package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import com.example.engine.Hue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MusicMode {
  SILENT,
  EXPLORATION,
  COMBAT,
}

/**
 * Low-latency Android audio controller for EMBERFALL:
 * - Dual-deck MediaPlayer engine for seamless stereo Lyria 3 soundtrack playback with equal-power
 *   Exploration <-> Combat crossfading (~1.4s) and subtle momentary SFX ducking (~-4 dB).
 * - Automatic procedural AudioTrack fallback bridge while initial Lyria tracks compose on first launch.
 * - Polyphonic 8-voice SoundPool loaded from procedural cached WAVs (with Spectrum pitch scaling).
 * - Thread-safe lifecycle state machine (EFB-015/016), validated AudioTrack setup (EFB-017),
 *   and full Android AudioManager focus + ducking lifecycle (EFB-018).
 */
class MinimalTechnoAudioEngine(context: Context) {

  private enum class EngineState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    FAILED,
    RELEASED,
  }

  private val appContext = context.applicationContext
  private val isTestEnvironment = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val audioManager: AudioManager? =
    if (isTestEnvironment) null else appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

  private val engineLock = Any()
  private var state: EngineState = EngineState.UNINITIALIZED

  @Volatile private var musicEnabled: Boolean = true
  @Volatile private var sfxEnabled: Boolean = true
  @Volatile private var masterMuted: Boolean = false
  @Volatile private var isForeground: Boolean = true
  @Volatile private var desiredMode: MusicMode = MusicMode.EXPLORATION
  @Volatile private var activeMode: MusicMode = MusicMode.SILENT

  @Volatile private var hasAudioFocus: Boolean = false
  @Volatile private var ducked: Boolean = false
  @Volatile private var transientFocusLoss: Boolean = false

  // Momentary SFX ducking envelope (1.0f = full, ~0.64f = -4 dB subtle duck during puzzle impacts)
  @Volatile private var sfxDuckFactor: Float = 1.0f
  private var sfxDuckJob: Job? = null

  private var focusRequest: AudioFocusRequest? = null

  private var soundPool: SoundPool? = null
  private val soundIds = ConcurrentHashMap<MinimalSfx, Int>()
  private val loadedIds = ConcurrentHashMap<Int, Boolean>()

  // Fallback procedural beds (bridge until Lyria tracks finish caching on first launch)
  private var explorationPcm: ShortArray? = null
  private var combatPcm: ShortArray? = null
  private var proceduralTrack: AudioTrack? = null

  // Cached Lyria 3 stereo audio files per mode + dual-deck crossfade MediaPlayers
  private val lyriaFiles = ConcurrentHashMap<MusicMode, File>()
  private var activeLyriaPlayer: MediaPlayer? = null
  private var activeLyriaFilePath: String? = null
  private var fadingLyriaPlayer: MediaPlayer? = null
  private var activeDeckGain: Float = 1.0f
  private var fadingDeckGain: Float = 0.0f
  private var crossfadeJob: Job? = null

  private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        hasAudioFocus = false
        ducked = false
        transientFocusLoss = false
        scope.launch {
          synchronized(engineLock) {
            pauseAllMusicLocked()
          }
        }
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        transientFocusLoss = true
        ducked = false
        scope.launch {
          synchronized(engineLock) {
            pauseAllMusicLocked()
          }
        }
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        ducked = true
        scope.launch {
          synchronized(engineLock) {
            applyCurrentMusicVolumeLocked()
          }
        }
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        hasAudioFocus = true
        ducked = false
        transientFocusLoss = false
        scope.launch {
          syncMusicPlayback()
        }
      }
    }
  }

  init {
    if (!isTestEnvironment) {
      ensureInitializedAsync()
    }
  }

  private fun ensureInitializedAsync() {
    if (isTestEnvironment) return
    val shouldStart = synchronized(engineLock) {
      if (state == EngineState.UNINITIALIZED || state == EngineState.FAILED) {
        state = EngineState.INITIALIZING
        true
      } else {
        false
      }
    }
    if (shouldStart) {
      scope.launch { initializeAudioBank() }
    }
  }

  private fun initializeAudioBank() {
    var localPool: SoundPool? = null
    try {
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

      val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(attrs)
        .build()
      localPool = pool

      val localLoadedIds = ConcurrentHashMap<Int, Boolean>()
      pool.setOnLoadCompleteListener { _, sampleId, status ->
        if (status == 0) {
          localLoadedIds[sampleId] = true
          loadedIds[sampleId] = true
        }
      }

      val localSoundIds = HashMap<MinimalSfx, Int>(MinimalSfx.entries.size)
      val cacheDir = File(appContext.cacheDir, "emberfall_minimal_sfx_v2").apply { mkdirs() }
      for (sfx in MinimalSfx.entries) {
        if (isReleased()) {
          runCatching { pool.release() }
          return
        }
        val wavFile = File(cacheDir, sfx.fileName)
        if (!wavFile.exists() || wavFile.length() < 64L) {
          wavFile.writeBytes(MinimalTechnoSynth.synthesizeSfxWav(sfx))
        }
        val id = pool.load(wavFile.absolutePath, 1)
        if (id > 0) {
          localSoundIds[sfx] = id
        }
      }

      if (isReleased()) {
        runCatching { pool.release() }
        return
      }

      // Synthesize lightweight procedural bridge beds used only while Lyria tracks are absent/composing
      val localExploration = MinimalTechnoSynth.synthesizeExplorationLoop()
      val localCombat = MinimalTechnoSynth.synthesizeCombatLoop()

      val committed = synchronized(engineLock) {
        if (state == EngineState.RELEASED) {
          false
        } else {
          soundPool = pool
          soundIds.clear()
          soundIds.putAll(localSoundIds)
          loadedIds.putAll(localLoadedIds)
          explorationPcm = localExploration
          combatPcm = localCombat
          state = EngineState.READY
          true
        }
      }

      if (!committed) {
        runCatching { pool.release() }
        return
      }

      syncMusicPlayback()
    } catch (_: Throwable) {
      runCatching { localPool?.release() }
      synchronized(engineLock) {
        if (state != EngineState.RELEASED) {
          state = EngineState.FAILED
        }
      }
    }
  }

  private fun isReleased(): Boolean = synchronized(engineLock) { state == EngineState.RELEASED }

  /**
   * Registers (or clears) a cached Lyria 3 audio file for Exploration or Combat.
   * If that mode is currently active, crossfades seamlessly into the new track.
   */
  fun setLyriaTrackFile(mode: MusicMode, file: File?) {
    if (mode == MusicMode.SILENT) return
    val validFile = file?.takeIf { it.exists() && it.length() >= 64L }
    if (validFile != null) {
      lyriaFiles[mode] = validFile
    } else {
      lyriaFiles.remove(mode)
    }
    if (!isTestEnvironment) {
      ensureInitializedAsync()
      scope.launch { syncMusicPlayback(forceReloadIfPathChanged = true) }
    }
  }

  fun updateSettings(musicEnabled: Boolean, sfxEnabled: Boolean, masterMuted: Boolean = false) {
    val effectiveChanged = (this.musicEnabled != musicEnabled) || (this.masterMuted != masterMuted)
    this.musicEnabled = musicEnabled
    this.sfxEnabled = sfxEnabled
    this.masterMuted = masterMuted
    if (!isTestEnvironment) {
      if (!masterMuted && (musicEnabled || sfxEnabled)) {
        ensureInitializedAsync()
      }
      if (effectiveChanged) {
        scope.launch { syncMusicPlayback() }
      }
    }
  }

  fun setMusicMode(mode: MusicMode) {
    if (desiredMode == mode) return
    desiredMode = mode
    if (!isTestEnvironment) {
      ensureInitializedAsync()
      scope.launch { syncMusicPlayback() }
    }
  }

  fun onAppForeground() {
    isForeground = true
    if (!isTestEnvironment) {
      ensureInitializedAsync()
      scope.launch { syncMusicPlayback() }
    }
  }

  fun onAppBackground() {
    isForeground = false
    if (!isTestEnvironment) {
      scope.launch {
        synchronized(engineLock) {
          pauseAllMusicLocked()
          abandonAudioFocusLocked()
        }
      }
    }
  }

  fun playSfx(sfx: MinimalSfx, volume: Float = 0.85f, pitchRate: Float = 1.0f) {
    if (isTestEnvironment || masterMuted || !sfxEnabled || !isForeground) return
    val pool = synchronized(engineLock) {
      if (state != EngineState.READY) null else soundPool
    } ?: return
    val id = soundIds[sfx] ?: return
    if (loadedIds[id] != true) return
    val duckScale = if (ducked) 0.45f else 1.0f
    val clampedVol = (volume * duckScale).coerceIn(0.05f, 1.0f)
    val clampedRate = pitchRate.coerceIn(0.5f, 2.0f)
    runCatching {
      pool.play(id, clampedVol, clampedVol, 1, 0, clampedRate)
    }
    if (shouldDuckForSfx(sfx)) {
      triggerSubtleSfxDuck(depth = if (sfx == MinimalSfx.PRISM || sfx == MinimalSfx.VICTORY) 0.60f else 0.68f)
    }
  }

  private fun shouldDuckForSfx(sfx: MinimalSfx): Boolean = when (sfx) {
    MinimalSfx.UI_CLICK, MinimalSfx.SELECT, MinimalSfx.REJECT -> false
    else -> true
  }

  /**
   * Gently ducks active music by ~-4 dB on puzzle impacts and glides smoothly back over ~280ms
   * so acoustic timbres stay clear and unobtrusive on phone speakers.
   */
  private fun triggerSubtleSfxDuck(depth: Float = 0.66f, holdMs: Long = 160L) {
    if (isTestEnvironment || masterMuted || !musicEnabled) return
    synchronized(engineLock) {
      sfxDuckFactor = minOf(sfxDuckFactor, depth.coerceIn(0.50f, 1.0f))
      applyCurrentMusicVolumeLocked()
      sfxDuckJob?.cancel()
      sfxDuckJob = scope.launch {
        delay(holdMs)
        val steps = 10
        val startFactor = sfxDuckFactor
        for (i in 1..steps) {
          delay(28L)
          val t = i / steps.toFloat()
          synchronized(engineLock) {
            if (state == EngineState.RELEASED) return@launch
            sfxDuckFactor = startFactor + (1.0f - startFactor) * t
            applyCurrentMusicVolumeLocked()
          }
        }
      }
    }
  }

  /**
   * Plays a layered percussion + harmonic pluck tuned to the popped Hue & Spectrum Wheel step.
   */
  fun playHuePop(
    hue: Hue?,
    spectrumHalfSteps: Int,
    usedPrism: Boolean,
    clearedAsh: Boolean,
    shatteredStone: Boolean,
  ) {
    val rate = MinimalTechnoSynth.spectrumPitchRate(spectrumHalfSteps)
    val baseSfx = when (hue) {
      Hue.EMBER -> MinimalSfx.POP_EMBER
      Hue.GOLD -> MinimalSfx.POP_GOLD
      Hue.MOSS -> MinimalSfx.POP_MOSS
      Hue.TIDE -> MinimalSfx.POP_TIDE
      Hue.VIOLET -> MinimalSfx.POP_VIOLET
      null -> if (usedPrism) MinimalSfx.PRISM else MinimalSfx.ASH
    }
    playSfx(baseSfx, volume = 0.90f, pitchRate = rate)
    if (usedPrism && hue != null) {
      playSfx(MinimalSfx.PRISM, volume = 0.72f, pitchRate = (rate * 0.95f).coerceIn(0.8f, 1.8f))
    }
    if (clearedAsh && hue != null) {
      playSfx(MinimalSfx.ASH, volume = 0.62f, pitchRate = 1.0f)
    }
    if (shatteredStone) {
      playSfx(MinimalSfx.STONE, volume = 0.86f, pitchRate = 1.0f)
    }
  }

  /**
   * Plays a staggered ascending harmonic Match-3 cascade phrase (Wave 1 -> Wave N).
   */
  fun playCascadeSequence(
    waveHues: List<Hue?>,
    baseSpectrumHalfSteps: Int,
    usedPrism: Boolean,
    clearedAsh: Boolean,
    shatteredStone: Boolean,
  ) {
    val waves = if (waveHues.isEmpty()) listOf(null) else waveHues
    playHuePop(
      hue = waves.first(),
      spectrumHalfSteps = baseSpectrumHalfSteps,
      usedPrism = usedPrism,
      clearedAsh = clearedAsh,
      shatteredStone = shatteredStone,
    )
    if (waves.size <= 1 || isTestEnvironment || masterMuted || !sfxEnabled) return

    scope.launch {
      for (idx in 1 until minOf(waves.size, 6)) {
        delay(115L)
        val waveHue = waves[idx]
        val climbRate = MinimalTechnoSynth.cascadePitchRate(baseSpectrumHalfSteps, idx + 1)
        val sfx = when (waveHue) {
          Hue.EMBER -> MinimalSfx.POP_EMBER
          Hue.GOLD -> MinimalSfx.POP_GOLD
          Hue.MOSS -> MinimalSfx.POP_MOSS
          Hue.TIDE -> MinimalSfx.POP_TIDE
          Hue.VIOLET -> MinimalSfx.POP_VIOLET
          null -> MinimalSfx.PRISM
        }
        playSfx(sfx, volume = (0.88f + idx * 0.03f).coerceAtMost(1.0f), pitchRate = climbRate)
        if (idx == 1) {
          playSfx(MinimalSfx.ECHO, volume = 0.78f, pitchRate = climbRate)
        }
      }
    }
  }

  private fun syncMusicPlayback(forceReloadIfPathChanged: Boolean = false) {
    if (isTestEnvironment) return
    synchronized(engineLock) {
      if (state != EngineState.READY) return
      val effectiveMusic = musicEnabled && !masterMuted && isForeground
      val target = if (effectiveMusic && !transientFocusLoss) desiredMode else MusicMode.SILENT
      if (target == MusicMode.SILENT) {
        pauseAllMusicLocked()
        if (!effectiveMusic) {
          abandonAudioFocusLocked()
        }
        return
      }
      if (!requestAudioFocusLocked()) {
        pauseAllMusicLocked()
        return
      }

      val targetLyriaFile = lyriaFiles[target]?.takeIf { it.exists() && it.length() >= 64L }
      if (targetLyriaFile != null) {
        val sameLyriaActive = target == activeMode &&
          activeLyriaPlayer != null &&
          activeLyriaFilePath == targetLyriaFile.absolutePath
        if (sameLyriaActive && !forceReloadIfPathChanged) {
          runCatching {
            applyCurrentMusicVolumeLocked()
            if (activeLyriaPlayer?.isPlaying != true) {
              activeLyriaPlayer?.start()
            }
          }.onFailure {
            releaseLyriaDecksLocked()
          }
          return
        }
        if (sameLyriaActive) {
          applyCurrentMusicVolumeLocked()
          if (activeLyriaPlayer?.isPlaying != true) {
            runCatching { activeLyriaPlayer?.start() }
          }
          return
        }

        // Crossfade into the Lyria stereo track using dual MediaPlayer decks!
        if (startLyriaDeckCrossfadeLocked(target, targetLyriaFile)) {
          return
        }
        // If MediaPlayer fails to decode a corrupt file, fall through cleanly to procedural bridge.
      }

      // Fallback procedural bridge bed when Lyria track is not yet cached for this mode
      if (target == activeMode && proceduralTrack != null && activeLyriaPlayer == null) {
        runCatching {
          applyCurrentMusicVolumeLocked()
          if (proceduralTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
            proceduralTrack?.play()
          }
        }.onFailure {
          releaseProceduralTrackLocked()
        }
        return
      }

      startProceduralBridgeLocked(target)
    }
  }

  private fun startLyriaDeckCrossfadeLocked(targetMode: MusicMode, file: File): Boolean {
    var incoming: MediaPlayer? = null
    return try {
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
      val player = MediaPlayer().apply {
        setAudioAttributes(attrs)
        setDataSource(file.absolutePath)
        isLooping = true
        prepare()
      }
      incoming = player

      // Cancel any in-flight crossfade and clean previous fading deck
      crossfadeJob?.cancel()
      releaseFadingPlayerLocked()

      // Demote current active Lyria deck to outgoing fading deck
      val outgoingLyria = activeLyriaPlayer
      val outgoingProcedural = proceduralTrack
      activeLyriaPlayer = player
      activeLyriaFilePath = file.absolutePath
      fadingLyriaPlayer = outgoingLyria
      activeMode = targetMode

      if (outgoingLyria != null || outgoingProcedural != null) {
        activeDeckGain = 0.0f
        fadingDeckGain = 1.0f
        player.setVolume(0f, 0f)
        player.start()
        startCrossfadeAnimationLocked(fadeOutProcedural = outgoingProcedural != null)
      } else {
        activeDeckGain = 1.0f
        fadingDeckGain = 0.0f
        applyCurrentMusicVolumeLocked()
        player.start()
      }
      true
    } catch (_: Throwable) {
      runCatching { incoming?.release() }
      false
    }
  }

  private fun startCrossfadeAnimationLocked(fadeOutProcedural: Boolean) {
    crossfadeJob?.cancel()
    crossfadeJob = scope.launch {
      val steps = 28
      val stepDelayMs = 50L // ~1.4s smooth equal-power crossfade
      for (i in 1..steps) {
        delay(stepDelayMs)
        val t = i / steps.toFloat()
        val inGain = sin(t * PI * 0.5).toFloat()
        val outGain = cos(t * PI * 0.5).toFloat()
        synchronized(engineLock) {
          if (state == EngineState.RELEASED) return@launch
          activeDeckGain = inGain
          fadingDeckGain = outGain
          applyCurrentMusicVolumeLocked()
          if (fadeOutProcedural) {
            val baseProc = baseVolumeForMode(MusicMode.EXPLORATION, isLyria = false) * focusAndDuckMultiplier()
            runCatching { proceduralTrack?.setVolume((baseProc * outGain).coerceIn(0f, 1f)) }
          }
        }
      }
      synchronized(engineLock) {
        if (state == EngineState.RELEASED) return@launch
        activeDeckGain = 1.0f
        fadingDeckGain = 0.0f
        releaseFadingPlayerLocked()
        if (fadeOutProcedural) {
          releaseProceduralTrackLocked()
        }
        applyCurrentMusicVolumeLocked()
      }
    }
  }

  private fun startProceduralBridgeLocked(target: MusicMode) {
    val pcm = when (target) {
      MusicMode.EXPLORATION -> explorationPcm
      MusicMode.COMBAT -> combatPcm
      MusicMode.SILENT -> null
    } ?: return

    crossfadeJob?.cancel()
    releaseLyriaDecksLocked()
    releaseProceduralTrackLocked()
    var candidateTrack: AudioTrack? = null
    try {
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
      val format = AudioFormat.Builder()
        .setSampleRate(MinimalTechnoSynth.SAMPLE_RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()
      val byteLen = pcm.size * 2
      val track = AudioTrack.Builder()
        .setAudioAttributes(attrs)
        .setAudioFormat(format)
        .setBufferSizeInBytes(byteLen)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
      candidateTrack = track

      // EFB-017: Validate initialization, write completeness, and loop setup; release on any failure.
      if (track.state != AudioTrack.STATE_INITIALIZED) {
        throw IllegalStateException("AudioTrack failed to initialize (state=${track.state})")
      }
      val written = track.write(pcm, 0, pcm.size)
      if (written != pcm.size) {
        throw IllegalStateException("AudioTrack short write: wrote=$written expected=${pcm.size}")
      }
      val loopStatus = track.setLoopPoints(0, pcm.size, -1)
      if (loopStatus != AudioTrack.SUCCESS) {
        throw IllegalStateException("AudioTrack.setLoopPoints failed with code=$loopStatus")
      }

      proceduralTrack = track
      activeMode = target
      applyCurrentMusicVolumeLocked()
      track.play()
    } catch (_: Throwable) {
      runCatching { candidateTrack?.stop() }
      runCatching { candidateTrack?.release() }
      proceduralTrack = null
      activeMode = MusicMode.SILENT
    }
  }

  private fun baseVolumeForMode(mode: MusicMode, isLyria: Boolean): Float = if (isLyria) {
    if (mode == MusicMode.COMBAT) 0.72f else 0.62f
  } else {
    if (mode == MusicMode.COMBAT) 0.36f else 0.28f
  }

  private fun focusAndDuckMultiplier(): Float {
    val systemDuck = if (ducked) 0.30f else 1.0f
    return (systemDuck * sfxDuckFactor).coerceIn(0.05f, 1.0f)
  }

  private fun applyCurrentMusicVolumeLocked() {
    val mult = focusAndDuckMultiplier()
    activeLyriaPlayer?.let { player ->
      val vol = (baseVolumeForMode(activeMode, isLyria = true) * activeDeckGain * mult).coerceIn(0f, 1f)
      runCatching { player.setVolume(vol, vol) }
    }
    fadingLyriaPlayer?.let { player ->
      val vol = (baseVolumeForMode(activeMode, isLyria = true) * fadingDeckGain * mult).coerceIn(0f, 1f)
      runCatching { player.setVolume(vol, vol) }
    }
    if (activeLyriaPlayer == null) {
      proceduralTrack?.let { track ->
        val vol = (baseVolumeForMode(activeMode, isLyria = false) * mult).coerceIn(0f, 1f)
        runCatching { track.setVolume(vol) }
      }
    }
  }

  private fun requestAudioFocusLocked(): Boolean {
    if (hasAudioFocus) return true
    val manager = audioManager ?: return true
    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
      val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()
      focusRequest = req
      manager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
      @Suppress("DEPRECATION")
      manager.requestAudioFocus(
        focusChangeListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    hasAudioFocus = granted
    if (granted) {
      ducked = false
      transientFocusLoss = false
    }
    return granted
  }

  private fun abandonAudioFocusLocked() {
    val manager = audioManager ?: return
    if (!hasAudioFocus && focusRequest == null) return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
      } else {
        @Suppress("DEPRECATION")
        manager.abandonAudioFocus(focusChangeListener)
      }
    }
    focusRequest = null
    hasAudioFocus = false
    ducked = false
    transientFocusLoss = false
  }

  private fun pauseAllMusicLocked() {
    crossfadeJob?.cancel()
    releaseFadingPlayerLocked()
    activeDeckGain = 1.0f
    runCatching {
      activeLyriaPlayer?.let { if (it.isPlaying) it.pause() }
    }
    runCatching {
      proceduralTrack?.let {
        if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
          it.pause()
        }
      }
    }
  }

  private fun releaseFadingPlayerLocked() {
    runCatching {
      fadingLyriaPlayer?.let {
        if (it.isPlaying) it.stop()
        it.release()
      }
    }
    fadingLyriaPlayer = null
    fadingDeckGain = 0.0f
  }

  private fun releaseLyriaDecksLocked() {
    crossfadeJob?.cancel()
    releaseFadingPlayerLocked()
    runCatching {
      activeLyriaPlayer?.let {
        if (it.isPlaying) it.stop()
        it.release()
      }
    }
    activeLyriaPlayer = null
    activeLyriaFilePath = null
    activeDeckGain = 1.0f
  }

  private fun releaseProceduralTrackLocked() {
    runCatching {
      proceduralTrack?.stop()
      proceduralTrack?.release()
    }
    proceduralTrack = null
  }

  fun release() {
    val poolToRelease: SoundPool?
    synchronized(engineLock) {
      if (state == EngineState.RELEASED) return
      state = EngineState.RELEASED
      sfxDuckJob?.cancel()
      releaseLyriaDecksLocked()
      releaseProceduralTrackLocked()
      activeMode = MusicMode.SILENT
      abandonAudioFocusLocked()
      poolToRelease = soundPool
      soundPool = null
      soundIds.clear()
      loadedIds.clear()
      explorationPcm = null
      combatPcm = null
      lyriaFiles.clear()
    }
    scope.cancel()
    runCatching { poolToRelease?.release() }
  }
}
