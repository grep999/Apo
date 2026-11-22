package com.example.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class MinimalSfx(val fileName: String) {
  POP_EMBER("sfx_pop_ember.wav"),
  POP_GOLD("sfx_pop_gold.wav"),
  POP_MOSS("sfx_pop_moss.wav"),
  POP_TIDE("sfx_pop_tide.wav"),
  POP_VIOLET("sfx_pop_violet.wav"),
  PRISM("sfx_prism.wav"),
  ASH("sfx_ash.wav"),
  STONE("sfx_stone.wav"),
  SELECT("sfx_select.wav"),
  REJECT("sfx_reject.wav"),
  CARD_CAST("sfx_card_cast.wav"),
  ECHO("sfx_echo.wav"),
  FLASK("sfx_flask.wav"),
  WALL_RISE("sfx_wall_rise.wav"),
  ALARM("sfx_alarm.wav"),
  ENEMY_HIT("sfx_enemy_hit.wav"),
  VICTORY("sfx_victory.wav"),
  UI_CLICK("sfx_ui_click.wav"),
}

/**
 * Boutique procedural DSP sound engine for EMBERFALL (Berlin Dub-Techno / Crystalline Basalt identity):
 * - Key: D Dorian / D Minor-9th (warm, lush, hypnotic nocturnal acoustics — zero harsh raw sine/saw beeps)
 * - Tempo: 122 BPM (16th-note swing grid, seamless 4-bar loops with sidechain pumping)
 * - DSP blocks: 2-pole resonant Chamberlin State-Variable Filter, additive band-limited oscillators,
 *   Karplus-Strong plucked marimba/glass waveguides, Schroeder 4-comb + 2-allpass cathedral reverb,
 *   and analog tape soft-saturation.
 */
object MinimalTechnoSynth {

  const val SAMPLE_RATE = 44100
  private const val TWO_PI = 2.0 * PI
  private const val BPM = 122.0

  // Deterministic LCG PRNG for organic micro-percussion, vinyl air, and stone fracture bursts
  private class FastNoise(private var state: Long = 0x5DEECE66DL) {
    fun next(): Double {
      state = (state * 6364136223846793005L + 1442695040888963407L)
      val bits = (state ushr 33).toInt()
      return (bits.toDouble() / Int.MAX_VALUE.toDouble()) * 2.0 - 1.0
    }
  }

  /**
   * 2-pole resonant Chamberlin State-Variable Filter (LP / BP / HP outputs).
   * Eliminates harsh aliasing buzz and gives dub chords & sub-basses genuine analog warmth.
   */
  private class SvfFilter {
    var low = 0.0
      private set
    var band = 0.0
      private set
    var high = 0.0
      private set

    fun process(input: Double, cutoffHz: Double, resonance: Double): Double {
      val clampedHz = cutoffHz.coerceIn(28.0, SAMPLE_RATE * 0.24)
      val f = 2.0 * sin(PI * clampedHz / SAMPLE_RATE)
      val q = (1.0 - resonance.coerceIn(0.0, 0.90)).coerceAtLeast(0.08)
      low += f * band
      high = input - low - q * band
      band += f * high
      return low
    }

    fun processBand(input: Double, cutoffHz: Double, resonance: Double): Double {
      process(input, cutoffHz, resonance)
      return band
    }

    fun processHigh(input: Double, cutoffHz: Double, resonance: Double): Double {
      process(input, cutoffHz, resonance)
      return high
    }
  }

  /**
   * 4-bar seamless 122 BPM Exploration Dub-Techno bed (Basic Channel / Monolake cathedral atmosphere):
   * - Warm breathing detuned D-minor-9th pad swept through a slow resonant low-pass filter
   * - Soft felt-headed deep heartbeat sub-kick + subtle swung organic shaker
   * - Velvety filtered dub-techno chord stabs into circular dotted-16th tape delay + cathedral reverb
   */
  fun synthesizeExplorationLoop(): ShortArray {
    val stepSec = 60.0 / BPM / 4.0
    val totalSteps = 64 // 4 bars
    val totalSamples = (totalSteps * stepSec * SAMPLE_RATE).toInt()
    val loopDur = totalSamples.toDouble() / SAMPLE_RATE
    val padTrack = DoubleArray(totalSamples)
    val dubTrack = DoubleArray(totalSamples)
    val percTrack = DoubleArray(totalSamples)
    val noise = FastNoise(0xE8B3F411L)

    // 1. Lush warm evolving D-minor-9th pad (D2, A2, F3, C4, E4) with gentle detuning & SVF sweep
    val padNotes = doubleArrayOf(73.42, 110.00, 174.61, 261.63, 329.63)
    val padFilter = SvfFilter()
    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val barLfo = 0.5 + 0.5 * sin(TWO_PI * (t / loopDur) - PI / 2.0)
      var chordSum = 0.0
      for (nIdx in padNotes.indices) {
        val f = padNotes[nIdx]
        val detune = 1.0 + (nIdx - 2) * 0.0018
        // Seamless integer-cycle warm harmonic voice
        val cycles = kotlin.math.round(f * loopDur).coerceAtLeast(1.0)
        val exactF = cycles / loopDur
        chordSum += sin(TWO_PI * exactF * t) * 0.65 +
          sin(TWO_PI * exactF * 2.0 * t + 0.4 * nIdx) * 0.22 +
          sin(TWO_PI * exactF * detune * t) * 0.13
      }
      val cutoff = 260.0 + 380.0 * barLfo
      val warmPad = padFilter.process(chordSum / padNotes.size, cutoff, resonance = 0.25)
      // Gentle deep sub anchor (D1 = 36.71 Hz snapped to loop cycle for zero seam click)
      val d1Freq = kotlin.math.round(36.71 * loopDur) / loopDur
      val sub = sin(TWO_PI * d1Freq * t) * 0.14
      padTrack[i] = warmPad * 0.26 + sub
    }

    // 2. Soft felt heartbeat kick, organic swung micro-shaker, and sparse warm dub chords
    for (step in 0 until totalSteps) {
      val swingOffset = if (step % 2 == 1) stepSec * 0.12 else 0.0
      val start = ((step * stepSec + swingOffset) * SAMPLE_RATE).toInt().coerceIn(0, totalSamples - 1)
      val inBar = step % 16

      if (inBar == 0 || inBar == 8) {
        addWarmAnalogKick(percTrack, start, gain = if (inBar == 0) 0.27 else 0.21, punchHz = 86.0, subHz = 41.0, decaySec = 0.22)
      }
      when (inBar) {
        4, 12 -> addBrushedShaker(percTrack, start, noise, gain = 0.045, decaySec = 0.042, centerHz = 6200.0)
        6, 14 -> addBrushedShaker(percTrack, start, noise, gain = 0.032, decaySec = 0.025, centerHz = 7800.0)
        11 -> addWoodyClave(percTrack, start, gain = 0.045, freq = 640.0)
      }
      // Lush filtered dub-techno chord stabs on bar 1 beat 2.5 and bar 3 beat 3.5
      if (step == 6 || step == 38) {
        val chord = if (step == 6) {
          doubleArrayOf(146.83, 174.61, 220.00, 261.63, 329.63) // Dm9
        } else {
          doubleArrayOf(130.81, 174.61, 220.00, 293.66, 329.63) // Cmaj13 / Am11 color
        }
        addResonantDubStab(dubTrack, start, freqs = chord, gain = 0.16, decaySec = 0.44, startCutoff = 1650.0, endCutoff = 260.0)
      }
    }

    // 3. Circular dotted-16th warm tape delay + soft cathedral diffusion on the dub chords
    applyCircularTapeDelay(dubTrack, delaySamples = (stepSec * 3.0 * SAMPLE_RATE).toInt(), feedback = 0.44, wet = 0.52)
    applySchroederReverb(dubTrack, wet = 0.34, roomDecay = 0.72)

    val mix = DoubleArray(totalSamples) { i ->
      padTrack[i] + percTrack[i] + dubTrack[i]
    }
    return floatToPcm16(mix, masterGain = 0.72)
  }

  /**
   * 4-bar seamless 122 BPM Combat Berlin Minimal Techno bed (Richie Hawtin / Rrose / Tresor groove):
   * - Tight, punchy 909-inspired kick with warm analog body & micro-woody click
   * - Sidechain-pumped rolling D-Dorian/Phrygian sub-bass through 2-pole resonant SVF
   * - Swung 16th-note velocity-grooved closed/open hats, rimshots, & muted sub-tom ghosts
   * - Sweeping resonant dub-techno chord stabs with dark dotted-16th tape delay & cathedral tail
   */
  fun synthesizeCombatLoop(): ShortArray {
    val stepSec = 60.0 / BPM / 4.0
    val totalSteps = 64 // 4 bars
    val totalSamples = (totalSteps * stepSec * SAMPLE_RATE).toInt()
    val drumTrack = DoubleArray(totalSamples)
    val bassTrack = DoubleArray(totalSamples)
    val dubTrack = DoubleArray(totalSamples)
    val noise = FastNoise(0xC08BA711L)

    // Hypnotic rolling minimal techno bassline (D1/D2/F2/Eb2/C2)
    val bassNotes = doubleArrayOf(
      0.0, 0.0, 73.42, 0.0,   // beat 1: offbeat D2 push
      0.0, 73.42, 0.0, 77.78, // beat 2: D2 -> Eb2 dark tension
      0.0, 0.0, 73.42, 65.41, // beat 3: D2 -> C2 roll
      0.0, 87.31, 0.0, 73.42, // beat 4: F2 -> D2 turnaround
    )

    for (step in 0 until totalSteps) {
      val swingOffset = if (step % 2 == 1) stepSec * 0.14 else 0.0
      val start = ((step * stepSec + swingOffset) * SAMPLE_RATE).toInt().coerceIn(0, totalSamples - 1)
      val bar = step / 16
      val inBar = step % 16

      // 1. Tight 4-on-the-floor sculpted club kick
      if (inBar % 4 == 0) {
        val accent = if (inBar == 0) 0.62 else 0.54
        addWarmAnalogKick(drumTrack, start, gain = accent, punchHz = 138.0, subHz = 44.0, decaySec = 0.165)
      }

      // 2. Grooving swung hi-hats, subtle open-hat lift on offbeats, & organic wood/rim syncopation
      when (inBar) {
        2, 6, 10, 14 -> {
          val openLift = (inBar == 14 && (bar == 1 || bar == 3))
          addBrushedShaker(
            drumTrack,
            start,
            noise,
            gain = if (openLift) 0.135 else 0.105,
            decaySec = if (openLift) 0.095 else 0.032,
            centerHz = if (openLift) 7400.0 else 8600.0,
          )
        }
        3, 11 -> addBrushedShaker(drumTrack, start, noise, gain = 0.055, decaySec = 0.018, centerHz = 9200.0)
        7 -> addWoodyClave(drumTrack, start, gain = 0.085, freq = if (bar >= 2) 880.0 else 740.0)
        15 -> addWoodyClave(drumTrack, start, gain = 0.065, freq = 620.0)
      }
      // Subtle muted clap/snare micro-snap on beats 2 & 4
      if (inBar == 4 || inBar == 12) {
        addAnalogSnareGhost(drumTrack, start, noise, gain = 0.085)
      }

      // 3. Rolling warm band-limited sub-bassline
      val bFreq = bassNotes[inBar]
      if (bFreq > 0.0) {
        val freqMod = if (bar == 3 && inBar == 13) bFreq * 1.12246 else bFreq // G2 lift on bar 4
        val cutoffLift = 240.0 + bar * 70.0
        addWarmResonantBass(bassTrack, start, freq = freqMod, gain = 0.28, decaySec = 0.135, peakCutoff = cutoffLift)
      }

      // 4. Hypnotic filtered dub-techno chord stabs (evolving filter across bars)
      if (step == 10 || step == 28 || step == 46) {
        val chord = when (step) {
          10 -> doubleArrayOf(146.83, 174.61, 220.00, 261.63) // Dm7
          28 -> doubleArrayOf(146.83, 220.00, 261.63, 329.63) // Dm9
          else -> doubleArrayOf(155.56, 196.00, 233.08, 293.66) // EbMaj7 Phrygian shimmer
        }
        addResonantDubStab(
          dubTrack,
          start,
          freqs = chord,
          gain = 0.16,
          decaySec = 0.24,
          startCutoff = 1900.0 + bar * 180.0,
          endCutoff = 320.0,
        )
      }
    }

    // 5. Rhythmic sidechain ducking on bass & dub stabs locked to every quarter-note kick!
    val quarterSamples = (stepSec * 4.0 * SAMPLE_RATE).toInt().coerceAtLeast(1)
    for (i in 0 until totalSamples) {
      val posInBeat = (i % quarterSamples).toDouble() / SAMPLE_RATE
      val duck = 1.0 - 0.58 * exp(-posInBeat / 0.062)
      bassTrack[i] *= duck
      dubTrack[i] *= (0.45 + 0.55 * duck)
    }

    // 6. Circular dotted-16th tape delay + lush dark club reverb on dub stabs
    applyCircularTapeDelay(dubTrack, delaySamples = (stepSec * 3.0 * SAMPLE_RATE).toInt(), feedback = 0.42, wet = 0.45)
    applySchroederReverb(dubTrack, wet = 0.26, roomDecay = 0.65)

    val mix = DoubleArray(totalSamples) { i ->
      drumTrack[i] + bassTrack[i] + dubTrack[i]
    }
    return floatToPcm16(mix, masterGain = 0.78)
  }

  fun synthesizeSfxWav(sfx: MinimalSfx): ByteArray {
    val samples = synthesizeSfxPcm(sfx)
    return encodeWavPcm16(samples, SAMPLE_RATE)
  }

  fun synthesizeSfxPcm(sfx: MinimalSfx): ShortArray = when (sfx) {
    // Each Hue has a distinct, lush, musical crystalline/marimba chordal match pop!
    MinimalSfx.POP_EMBER -> synthMusicalHueMatch(
      chordHz = doubleArrayOf(293.66, 349.23, 440.00), // D4-F4-A4 warm Dm triad
      subHz = 73.42,
      brightness = 2400.0,
      decaySec = 0.28,
      shimmer = 0.28,
    )
    MinimalSfx.POP_GOLD -> synthMusicalHueMatch(
      chordHz = doubleArrayOf(349.23, 440.00, 523.25), // F4-A4-C5 bright gilded Fmaj
      subHz = 87.31,
      brightness = 3400.0,
      decaySec = 0.30,
      shimmer = 0.42,
    )
    MinimalSfx.POP_MOSS -> synthMusicalHueMatch(
      chordHz = doubleArrayOf(329.63, 392.00, 493.88), // E4-G4-B4 woody Dorian Em
      subHz = 82.41,
      brightness = 2100.0,
      decaySec = 0.26,
      shimmer = 0.24,
    )
    MinimalSfx.POP_TIDE -> synthMusicalHueMatch(
      chordHz = doubleArrayOf(392.00, 440.00, 587.33), // G4-A4-D5 glassy suspended crystal
      subHz = 98.00,
      brightness = 3900.0,
      decaySec = 0.32,
      shimmer = 0.48,
    )
    MinimalSfx.POP_VIOLET -> synthMusicalHueMatch(
      chordHz = doubleArrayOf(261.63, 329.63, 392.00, 493.88), // C4-E4-G4-B4 hypnotic Cmaj7 dub
      subHz = 65.41,
      brightness = 2850.0,
      decaySec = 0.34,
      shimmer = 0.40,
      withDubEcho = true,
    )
    MinimalSfx.PRISM -> synthPrismCrystalBurst()
    MinimalSfx.ASH -> synthVelvetAshSwipe()
    MinimalSfx.STONE -> synthBasaltStoneShatter()
    MinimalSfx.SELECT -> synthTactileStoneClick(pitchHz = 780.0, bodyHz = 210.0, durationSec = 0.042)
    MinimalSfx.REJECT -> synthPoliteWoodReject()
    MinimalSfx.CARD_CAST -> synthCardCastSwell()
    MinimalSfx.ECHO -> synthCascadeEchoChime()
    MinimalSfx.FLASK -> synthAlchemicalChalice()
    MinimalSfx.WALL_RISE -> synthHydraulicWallRise()
    MinimalSfx.ALARM -> synthDarkBrassWarning()
    MinimalSfx.ENEMY_HIT -> synthHeavyEnemySlash()
    MinimalSfx.VICTORY -> synthVictoryCathedralChord()
    MinimalSfx.UI_CLICK -> synthTactileStoneClick(pitchHz = 620.0, bodyHz = 170.0, durationSec = 0.032)
  }

  /**
   * Euphoric crystalline marimba + warm Rhodes chord + tight sub-punch match pop.
   * Sounds genuinely tactile and rewarding, and climbs musically when pitched up by Spectrum/Cascade!
   */
  private fun synthMusicalHueMatch(
    chordHz: DoubleArray,
    subHz: Double,
    brightness: Double,
    decaySec: Double,
    shimmer: Double,
    withDubEcho: Boolean = false,
  ): ShortArray {
    val totalDur = if (withDubEcho) decaySec + 0.34 else decaySec + 0.22
    val n = (totalDur * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0x5A715F1EL + (subHz * 100).toLong())

    // 1. Crisp crystalline mallet transient + warm punchy sub thump
    addWarmAnalogKick(buf, 0, gain = 0.34, punchHz = subHz * 2.2, subHz = subHz, decaySec = 0.095)
    addBrushedShaker(buf, 0, noise, gain = 0.14, decaySec = 0.016, centerHz = brightness * 1.4)

    // 2. Staggered 18ms micro-strummed Karplus/Bell harmonic chord for sparkling jewel-shatter feel!
    val svf = SvfFilter()
    val chordBuf = DoubleArray(n)
    chordHz.forEachIndexed { idx, freq ->
      val start = (idx * 0.018 * SAMPLE_RATE).toInt()
      val voiceGain = (0.42 / sqrt(chordHz.size.toDouble())) * (1.0 - idx * 0.06)
      addCrystalMalletVoice(chordBuf, start, freq = freq, gain = voiceGain, decaySec = decaySec, shimmer = shimmer)
    }

    // Pass chord shimmer through an opening-then-warming SVF filter
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val cutoff = 420.0 + brightness * exp(-t / (decaySec * 0.55))
      buf[i] += svf.process(chordBuf[i], cutoff, resonance = 0.32) * 1.15
    }

    if (withDubEcho) {
      val delay = (0.123 * SAMPLE_RATE).toInt()
      var lp = 0.0
      for (i in delay until n) {
        lp += 0.28 * (buf[i - delay] - lp)
        buf[i] += lp * 0.36
      }
    }

    applySchroederReverb(buf, wet = 0.22, roomDecay = 0.62)
    return floatToPcm16(buf, masterGain = 0.90)
  }

  /**
   * 6-note shimmering ascending glass-harp chord + warm sub glow for Prism detonations.
   */
  private fun synthPrismCrystalBurst(): ShortArray {
    val n = (0.62 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val notes = doubleArrayOf(293.66, 349.23, 440.00, 523.25, 659.25, 880.00) // Dm9 -> A5 glass cascade
    addWarmAnalogKick(buf, 0, gain = 0.38, punchHz = 150.0, subHz = 55.0, decaySec = 0.16)
    notes.forEachIndexed { idx, freq ->
      val start = (idx * 0.036 * SAMPLE_RATE).toInt()
      addCrystalMalletVoice(buf, start, freq = freq, gain = 0.24, decaySec = 0.28, shimmer = 0.55)
    }
    applySchroederReverb(buf, wet = 0.38, roomDecay = 0.74)
    return floatToPcm16(buf, masterGain = 0.88)
  }

  /**
   * Silky velvet brushed ash sweep + warm muted low tap.
   */
  private fun synthVelvetAshSwipe(): ShortArray {
    val n = (0.24 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0xA54112L)
    val bp = SvfFilter()
    addWoodyClave(buf, 0, gain = 0.24, freq = 310.0)
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.055) * (1.0 - exp(-t / 0.005))
      val sweepHz = 1800.0 + 2600.0 * exp(-t / 0.045)
      val silky = bp.processBand(noise.next(), sweepHz, resonance = 0.45)
      buf[i] += silky * env * 0.65
    }
    applySchroederReverb(buf, wet = 0.16, roomDecay = 0.52)
    return floatToPcm16(buf, masterGain = 0.86)
  }

  /**
   * Heavy seismic basalt crack + sub-drop + cascading multi-grain stone debris rattle!
   */
  private fun synthBasaltStoneShatter(): ShortArray {
    val n = (0.44 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0x570AE9L)
    val crackFilter = SvfFilter()

    // Heavy seismic sub drop
    addWarmAnalogKick(buf, 0, gain = 0.72, punchHz = 132.0, subHz = 36.0, decaySec = 0.25)

    // Sharp rock fracture burst
    for (i in 0 until (0.14 * SAMPLE_RATE).toInt()) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.032) * (1.0 - exp(-t / 0.0012))
      val crunch = crackFilter.processBand(noise.next(), 1600.0 - 900.0 * (t / 0.14), resonance = 0.55)
      buf[i] += tanh(crunch * 2.2) * env * 0.58
    }

    // 5 tumbling rock shard clacks
    val shardOffsets = doubleArrayOf(0.025, 0.062, 0.105, 0.148, 0.195)
    val shardFreqs = doubleArrayOf(920.0, 1180.0, 760.0, 1340.0, 840.0)
    for (k in shardOffsets.indices) {
      addWoodyClave(buf, (shardOffsets[k] * SAMPLE_RATE).toInt(), gain = 0.24 - k * 0.035, freq = shardFreqs[k])
    }

    applySchroederReverb(buf, wet = 0.24, roomDecay = 0.64)
    return floatToPcm16(buf, masterGain = 0.90)
  }

  /**
   * Tactile organic stone/wood micro-click — zero harsh microwave sine beeps!
   */
  private fun synthTactileStoneClick(pitchHz: Double, bodyHz: Double, durationSec: Double): ShortArray {
    val n = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1024)
    val buf = DoubleArray(n)
    val noise = FastNoise(0x71C044L + pitchHz.toLong())
    val bp = SvfFilter()
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val knock = sin(TWO_PI * bodyHz * t) * exp(-t / 0.0075) * 0.52
      val woodTone = sin(TWO_PI * pitchHz * t) * exp(-t / 0.0042) * 0.32
      val crispTick = bp.processBand(noise.next(), pitchHz * 2.6, resonance = 0.42) * exp(-t / 0.0030) * 0.55
      buf[i] = (knock + woodTone + crispTick) * (1.0 - exp(-t / 0.0006))
    }
    return floatToPcm16(buf, masterGain = 0.85)
  }

  /**
   * Soft polite low double-wood knock for invalid swaps (never jarring).
   */
  private fun synthPoliteWoodReject(): ShortArray {
    val n = (0.18 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    addWoodyClave(buf, 0, gain = 0.42, freq = 260.0)
    addWoodyClave(buf, (0.065 * SAMPLE_RATE).toInt(), gain = 0.34, freq = 215.0)
    return floatToPcm16(buf, masterGain = 0.82)
  }

  /**
   * Snappy card-whip transient + warm filtered dub-techno chord + sub impact!
   */
  private fun synthCardCastSwell(): ShortArray {
    val n = (0.44 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0xCA8D01L)
    val bp = SvfFilter()

    // Crisp parchment whip transient
    for (i in 0 until (0.11 * SAMPLE_RATE).toInt()) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.032) * (1.0 - exp(-t / 0.006))
      val whip = bp.processBand(noise.next(), 2400.0 + 1800.0 * exp(-t / 0.025), resonance = 0.38)
      buf[i] += whip * env * 0.44
    }

    // Warm sub thump + resonant Dm9 dub chord impact
    addWarmAnalogKick(buf, (0.015 * SAMPLE_RATE).toInt(), gain = 0.44, punchHz = 148.0, subHz = 52.0, decaySec = 0.13)
    addResonantDubStab(
      buf,
      startSample = (0.015 * SAMPLE_RATE).toInt(),
      freqs = doubleArrayOf(146.83, 220.00, 293.66, 349.23, 440.00),
      gain = 0.40,
      decaySec = 0.28,
      startCutoff = 2800.0,
      endCutoff = 380.0,
    )
    applySchroederReverb(buf, wet = 0.28, roomDecay = 0.66)
    return floatToPcm16(buf, masterGain = 0.88)
  }

  /**
   * Euphoric octave-rising crystalline chime + dark dub echo for multi-wave cascades & Echo cards!
   */
  private fun synthCascadeEchoChime(): ShortArray {
    val n = (0.58 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val notes = doubleArrayOf(440.00, 587.33, 880.00) // A4 -> D5 -> A5 euphoric cascade lift
    notes.forEachIndexed { idx, freq ->
      val start = (idx * 0.055 * SAMPLE_RATE).toInt()
      addCrystalMalletVoice(buf, start, freq = freq, gain = 0.34 - idx * 0.04, decaySec = 0.24, shimmer = 0.50)
    }
    val delay = (0.123 * SAMPLE_RATE).toInt()
    var lp = 0.0
    for (i in delay until n) {
      lp += 0.30 * (buf[i - delay] - lp)
      buf[i] += lp * 0.42
    }
    applySchroederReverb(buf, wet = 0.34, roomDecay = 0.72)
    return floatToPcm16(buf, masterGain = 0.86)
  }

  /**
   * Warm crystalline chalice chime + ascending harmonic swell (replaces cartoonish bubble sweep!).
   */
  private fun synthAlchemicalChalice(): ShortArray {
    val n = (0.54 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val notes = doubleArrayOf(293.66, 392.00, 440.00, 587.33) // D4-G4-A4-D5 warm healing sus4
    notes.forEachIndexed { idx, freq ->
      val start = (idx * 0.045 * SAMPLE_RATE).toInt()
      addCrystalMalletVoice(buf, start, freq = freq, gain = 0.28, decaySec = 0.30, shimmer = 0.44)
    }
    applySchroederReverb(buf, wet = 0.36, roomDecay = 0.74)
    return floatToPcm16(buf, masterGain = 0.86)
  }

  /**
   * Deep hydraulic basalt grind + sub pressure swell + crisp stone lock clunk.
   */
  private fun synthHydraulicWallRise(): ShortArray {
    val n = (0.46 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0x9A115EL)
    val lp = SvfFilter()
    var phase = 0.0
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val normT = t / 0.46
      val freq = 38.0 + 34.0 * normT
      phase += TWO_PI * freq / SAMPLE_RATE
      val env = sin(PI * normT).coerceAtLeast(0.0)
      val rumble = tanh((sin(phase) + 0.35 * sin(phase * 2.0)) * 1.6) * 0.46 * env
      val stoneGrind = lp.process(noise.next(), 240.0 + 520.0 * normT, resonance = 0.45) * 0.36 * env
      buf[i] = rumble + stoneGrind
    }
    addWoodyClave(buf, (0.35 * SAMPLE_RATE).toInt(), gain = 0.42, freq = 420.0)
    applySchroederReverb(buf, wet = 0.18, roomDecay = 0.58)
    return floatToPcm16(buf, masterGain = 0.86)
  }

  /**
   * Deep ominous industrial brass horn / dark sub-bass warning throb (not a cheap harsh bell!).
   */
  private fun synthDarkBrassWarning(): ShortArray {
    val n = (0.54 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val svf = SvfFilter()
    addWarmAnalogKick(buf, 0, gain = 0.60, punchHz = 105.0, subHz = 36.71, decaySec = 0.32)
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.26) * (1.0 - exp(-t / 0.015))
      // Band-limited dark minor-second horn (D2 + Eb2)
      val brass = bandLimitedSaw(73.42, t, harmonics = 6) * 0.55 +
        bandLimitedSaw(77.78, t, harmonics = 6) * 0.45
      val filtered = svf.process(brass, 220.0 + 620.0 * exp(-t / 0.14), resonance = 0.38)
      buf[i] += tanh(filtered * 1.5) * env * 0.46
    }
    applySchroederReverb(buf, wet = 0.28, roomDecay = 0.68)
    return floatToPcm16(buf, masterGain = 0.90)
  }

  /**
   * Punchy analog impact kick + dark metallic slash transient for enemy strikes.
   */
  private fun synthHeavyEnemySlash(): ShortArray {
    val n = (0.28 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val noise = FastNoise(0xE4E817L)
    val bp = SvfFilter()
    addWarmAnalogKick(buf, 0, gain = 0.64, punchHz = 152.0, subHz = 46.0, decaySec = 0.14)
    for (i in 0 until n) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.042) * (1.0 - exp(-t / 0.0015))
      val snap = bp.processBand(noise.next(), 2100.0 - 1100.0 * (t / 0.28), resonance = 0.42)
      buf[i] += snap * env * 0.56
    }
    applySchroederReverb(buf, wet = 0.18, roomDecay = 0.55)
    return floatToPcm16(buf, masterGain = 0.90)
  }

  /**
   * Lush warm dub-techno Rhodes Dm11 swell + sparkling glass arpeggio + deep cathedral reverb!
   */
  private fun synthVictoryCathedralChord(): ShortArray {
    val n = (1.15 * SAMPLE_RATE).toInt()
    val buf = DoubleArray(n)
    val chord = doubleArrayOf(146.83, 220.00, 261.63, 329.63, 392.00, 440.00) // Lush Dm11
    addWarmAnalogKick(buf, 0, gain = 0.44, punchHz = 112.0, subHz = 36.71, decaySec = 0.34)
    addResonantDubStab(
      buf,
      startSample = 0,
      freqs = chord,
      gain = 0.42,
      decaySec = 0.72,
      startCutoff = 2600.0,
      endCutoff = 420.0,
    )
    val arp = doubleArrayOf(293.66, 392.00, 440.00, 587.33, 659.25)
    arp.forEachIndexed { idx, freq ->
      val start = ((0.06 + idx * 0.075) * SAMPLE_RATE).toInt()
      addCrystalMalletVoice(buf, start, freq = freq, gain = 0.20, decaySec = 0.36, shimmer = 0.48)
    }
    applySchroederReverb(buf, wet = 0.40, roomDecay = 0.78)
    return floatToPcm16(buf, masterGain = 0.88)
  }

  // ============================================================================================
  // Boutique DSP Primitives (Band-Limited Oscillators, Crystal Mallet, SVF, Tape Delay & Reverb)
  // ============================================================================================

  private fun bandLimitedSaw(freq: Double, t: Double, harmonics: Int = 7): Double {
    var sum = 0.0
    for (h in 1..harmonics) {
      if (freq * h >= SAMPLE_RATE * 0.42) break
      val sign = if (h % 2 == 0) -1.0 else 1.0
      sum += sign * sin(TWO_PI * freq * h * t) / h
    }
    return sum * 0.62
  }

  /**
   * Glass-marimba / electric Rhodes mallet voice with warm fundamental, harmonic bell overtone,
   * and organic wood-bar transient. Zero harsh aliasing.
   */
  private fun addCrystalMalletVoice(
    mix: DoubleArray,
    startSample: Int,
    freq: Double,
    gain: Double,
    decaySec: Double,
    shimmer: Double,
  ) {
    val len = (decaySec * 3.2 * SAMPLE_RATE).toInt()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val attack = 1.0 - exp(-t / 0.0022)
      val bodyEnv = exp(-t / decaySec)
      val tineEnv = exp(-t / (decaySec * 0.24))
      val f1 = sin(TWO_PI * freq * t)
      val f2 = sin(TWO_PI * freq * 2.0 * t + 0.15) * 0.32 * exp(-t / (decaySec * 0.55))
      val bell = sin(TWO_PI * freq * 3.004 * t) * shimmer * 0.36 * tineEnv
      val glass = sin(TWO_PI * freq * 4.012 * t) * shimmer * 0.18 * exp(-t / 0.032)
      mix[idx] += (f1 + f2 + bell + glass) * attack * bodyEnv * gain
    }
  }

  private fun addWarmAnalogKick(
    mix: DoubleArray,
    startSample: Int,
    gain: Double,
    punchHz: Double,
    subHz: Double,
    decaySec: Double,
  ) {
    val len = (decaySec * 2.6 * SAMPLE_RATE).toInt()
    var phase = 0.0
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val freq = subHz + (punchHz - subHz) * exp(-t / 0.019)
      phase += TWO_PI * freq / SAMPLE_RATE
      val env = exp(-t / decaySec) * (1.0 - exp(-t / 0.0014))
      val click = sin(TWO_PI * 1150.0 * t) * exp(-t / 0.0028) * 0.16
      val warmBody = tanh((sin(phase) + 0.14 * sin(phase * 2.0)) * 1.32)
      mix[idx] += (warmBody * env + click) * gain
    }
  }

  private fun addBrushedShaker(
    mix: DoubleArray,
    startSample: Int,
    noise: FastNoise,
    gain: Double,
    decaySec: Double,
    centerHz: Double,
  ) {
    val len = (decaySec * 4.2 * SAMPLE_RATE).toInt()
    val bp = SvfFilter()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val env = exp(-t / decaySec) * (1.0 - exp(-t / 0.0018))
      val shaped = bp.processBand(noise.next(), centerHz, resonance = 0.35)
      mix[idx] += shaped * env * gain * 1.4
    }
  }

  private fun addAnalogSnareGhost(
    mix: DoubleArray,
    startSample: Int,
    noise: FastNoise,
    gain: Double,
  ) {
    val len = (0.085 * SAMPLE_RATE).toInt()
    val bp = SvfFilter()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val snap = bp.processBand(noise.next(), 2400.0, resonance = 0.35) * exp(-t / 0.024)
      val body = sin(TWO_PI * 195.0 * t) * exp(-t / 0.018) * 0.45
      mix[idx] += (snap + body) * gain
    }
  }

  private fun addWoodyClave(
    mix: DoubleArray,
    startSample: Int,
    gain: Double,
    freq: Double,
  ) {
    val len = (0.055 * SAMPLE_RATE).toInt()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val env = exp(-t / 0.0095) * (1.0 - exp(-t / 0.0008))
      val sig = sin(TWO_PI * freq * t) + 0.38 * sin(TWO_PI * freq * 2.756 * t) * exp(-t / 0.004)
      mix[idx] += sig * env * gain
    }
  }

  private fun addWarmResonantBass(
    mix: DoubleArray,
    startSample: Int,
    freq: Double,
    gain: Double,
    decaySec: Double,
    peakCutoff: Double,
  ) {
    val len = (decaySec * 2.5 * SAMPLE_RATE).toInt()
    val svf = SvfFilter()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val amp = exp(-t / decaySec) * (1.0 - exp(-t / 0.0035))
      val osc = bandLimitedSaw(freq, t, harmonics = 6) * 0.65 + sin(TWO_PI * freq * t) * 0.55
      val cutoff = 110.0 + peakCutoff * exp(-t / 0.048)
      val filtered = svf.process(osc, cutoff, resonance = 0.44)
      mix[idx] += tanh(filtered * 1.45) * amp * gain
    }
  }

  private fun addResonantDubStab(
    mix: DoubleArray,
    startSample: Int,
    freqs: DoubleArray,
    gain: Double,
    decaySec: Double,
    startCutoff: Double,
    endCutoff: Double,
  ) {
    val len = (decaySec * 3.0 * SAMPLE_RATE).toInt()
    val norm = 1.0 / freqs.size
    val svf = SvfFilter()
    for (k in 0 until len) {
      val idx = startSample + k
      if (idx >= mix.size) break
      val t = k.toDouble() / SAMPLE_RATE
      val amp = exp(-t / decaySec) * (1.0 - exp(-t / 0.007))
      var chord = 0.0
      for (fIdx in freqs.indices) {
        val f = freqs[fIdx]
        val detune = 1.0 + (fIdx - freqs.size / 2) * 0.0022
        chord += bandLimitedSaw(f * detune, t, harmonics = 5) * 0.55 +
          sin(TWO_PI * f * t) * 0.45
      }
      val cutoff = endCutoff + (startCutoff - endCutoff) * exp(-t / (decaySec * 0.36))
      val filtered = svf.process(chord * norm, cutoff, resonance = 0.46)
      mix[idx] += tanh(filtered * 1.35) * amp * gain
    }
  }

  private fun applyCircularTapeDelay(
    mix: DoubleArray,
    delaySamples: Int,
    feedback: Double,
    wet: Double,
  ) {
    val n = mix.size
    if (delaySamples <= 0 || delaySamples >= n) return
    val delayBuf = DoubleArray(n)
    val svf = SvfFilter()
    repeat(2) {
      for (i in 0 until n) {
        val src = (i - delaySamples + n) % n
        val tapped = mix[src] + delayBuf[src] * feedback
        // Warm analog tape echo rolloff around 1.4 kHz
        delayBuf[i] = tanh(svf.process(tapped, cutoffHz = 1450.0, resonance = 0.18) * 1.1)
      }
    }
    for (i in 0 until n) {
      mix[i] += delayBuf[i] * wet
    }
  }

  /**
   * Compact 4-comb + 2-allpass Schroeder diffusion reverb giving chords and pops lush studio depth.
   */
  private fun applySchroederReverb(
    mix: DoubleArray,
    wet: Double,
    roomDecay: Double,
  ) {
    val n = mix.size
    if (n < 2048 || wet <= 0.0) return
    val combDelays = intArrayOf(1117, 1303, 1487, 1699)
    val combOut = DoubleArray(n)
    val g = roomDecay.coerceIn(0.35, 0.82)

    for (d in combDelays) {
      val buf = DoubleArray(d)
      var ptr = 0
      var lp = 0.0
      for (i in 0 until n) {
        val delayed = buf[ptr]
        lp += 0.36 * (delayed - lp) // warm high-frequency absorption
        buf[ptr] = mix[i] + lp * g
        combOut[i] += delayed * 0.25
        ptr++
        if (ptr == d) ptr = 0
      }
    }

    // Two short allpass diffusers for smooth, non-metallic tail
    val apDelays = intArrayOf(225, 341)
    var diffused = combOut
    for (d in apDelays) {
      val next = DoubleArray(n)
      val buf = DoubleArray(d)
      var ptr = 0
      for (i in 0 until n) {
        val bufOut = buf[ptr]
        val input = diffused[i]
        next[i] = -input * 0.5 + bufOut
        buf[ptr] = input + bufOut * 0.5
        ptr++
        if (ptr == d) ptr = 0
      }
      diffused = next
    }

    val dry = 1.0 - wet * 0.35
    for (i in 0 until n) {
      mix[i] = mix[i] * dry + diffused[i] * wet
    }
  }

  private fun floatToPcm16(mix: DoubleArray, masterGain: Double): ShortArray {
    val out = ShortArray(mix.size)
    for (i in mix.indices) {
      val limited = tanh(mix[i] * masterGain)
      out[i] = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
  }

  /**
   * Maps ADR-005 Spectrum Wheel half-steps (2 = 1.0x ... 8 = 4.0x) to D-Phrygian/Dorian pitch multipliers.
   */
  fun spectrumPitchRate(halfSteps: Int): Float {
    val semitones = when (halfSteps.coerceIn(2, 8)) {
      2 -> 0.0   // D
      3 -> 1.0   // Eb
      4 -> 3.0   // F
      5 -> 5.0   // G
      6 -> 7.0   // A
      7 -> 10.0  // C
      else -> 12.0 // D octave
    }
    return 2.0.pow(semitones / 12.0).toFloat()
  }

  /**
   * Musical D-Minor pentatonic pitch climb for Match-3 multi-wave cascades (Wave 1 -> Wave 5+).
   */
  fun cascadePitchRate(baseHalfSteps: Int, waveIndex: Int): Float {
    val baseRate = spectrumPitchRate(baseHalfSteps)
    val extraSemitones = when (waveIndex.coerceAtLeast(1)) {
      1 -> 0.0
      2 -> 3.0  // +m3
      3 -> 5.0  // +P4
      4 -> 7.0  // +P5
      else -> 12.0 // +Octave
    }
    return (baseRate * 2.0.pow(extraSemitones / 12.0)).toFloat().coerceIn(0.6f, 2.0f)
  }

  fun encodeWavPcm16(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
    val dataSize = samples.size * 2
    val out = ByteArrayOutputStream(44 + dataSize)

    fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
    fun writeIntLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v ushr 8) and 0xFF)
      out.write((v ushr 16) and 0xFF)
      out.write((v ushr 24) and 0xFF)
    }
    fun writeShortLE(v: Int) {
      out.write(v and 0xFF)
      out.write((v ushr 8) and 0xFF)
    }

    writeAscii("RIFF")
    writeIntLE(36 + dataSize)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeIntLE(16) // PCM chunk size
    writeShortLE(1) // PCM format
    writeShortLE(1) // Mono
    writeIntLE(sampleRate)
    writeIntLE(sampleRate * 2) // Byte rate
    writeShortLE(2) // Block align
    writeShortLE(16) // Bits per sample
    writeAscii("data")
    writeIntLE(dataSize)
    for (s in samples) {
      writeShortLE(s.toInt())
    }
    return out.toByteArray()
  }
}
