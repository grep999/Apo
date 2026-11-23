package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LyriaSoundtrackRepository
import com.example.audio.MinimalSfx
import com.example.audio.MinimalTechnoAudioEngine
import com.example.audio.MusicMode
import com.example.audio.SoundtrackModelTier
import com.example.audio.SoundtrackPreset
import com.example.audio.SoundtrackSlot
import com.example.audio.SoundtrackSlotStatus
import com.example.audio.SoundtrackUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import com.example.data.ActiveRunEntity
import com.example.data.ActiveRunLoadResult
import com.example.data.EmberfallDatabase
import com.example.data.EmberfallRepository
import com.example.data.PlayerSettings
import com.example.data.RunHistoryEntity
import com.example.data.SettingsDataStore
import com.example.engine.*
import com.example.ui.components.BoardJuiceFx
import com.example.ui.components.CardCastFx
import com.example.ui.components.FxCell
import com.example.ui.components.FxWave
import com.example.ui.components.InvalidSwapFx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AppDestination(val route: String) {
  data object Title : AppDestination("title")
  data object ClassSelect : AppDestination("class_select")
  data object ActMap : AppDestination("act_map")
  data object CombatArena : AppDestination("combat_arena")
  data object Rewards : AppDestination("rewards")
  data object Campfire : AppDestination("campfire")
  data object Merchant : AppDestination("merchant")
  data object Event : AppDestination("event")
  data object RunSummary : AppDestination("run_summary")
  data object Compendium : AppDestination("compendium")
  data object Settings : AppDestination("settings")
}

class EmberfallViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: EmberfallRepository by lazy {
    val db = EmberfallDatabase.getInstance(application)
    EmberfallRepository(db.emberfallDao(), SettingsDataStore(application))
  }

  private val audioEngine = MinimalTechnoAudioEngine(application)
  private val soundtrackRepository = LyriaSoundtrackRepository(application)
  private val isRobolectricTest = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

  val activeRunSummary: StateFlow<ActiveRunEntity?> = repository.activeRunSummary
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val runHistory: StateFlow<List<RunHistoryEntity>> = repository.runHistory
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val settings: StateFlow<PlayerSettings> = repository.settings
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerSettings())

  private val _soundtrackUiState = MutableStateFlow(SoundtrackUiState())
  val soundtrackUiState: StateFlow<SoundtrackUiState> = _soundtrackUiState.asStateFlow()

  private val slotComposeJobs = mutableMapOf<SoundtrackSlot, Job>()
  private var autoComposeJob: Job? = null
  private var studioAuditionModeOverride: MusicMode? = null

  private val _runState = MutableStateFlow<RunState?>(null)
  val runState: StateFlow<RunState?> = _runState.asStateFlow()

  private val _selectedGroup = MutableStateFlow<GroupResolution?>(null)
  val selectedGroup: StateFlow<GroupResolution?> = _selectedGroup.asStateFlow()

  private val _selectedSwapCoord = MutableStateFlow<BoardCoord?>(null)
  val selectedSwapCoord: StateFlow<BoardCoord?> = _selectedSwapCoord.asStateFlow()

  private val _boardFeedback = MutableStateFlow<String?>(null)
  val boardFeedback: StateFlow<String?> = _boardFeedback.asStateFlow()

  private var fxSequence = 0L

  private val _boardJuiceFx = MutableStateFlow<BoardJuiceFx?>(null)
  val boardJuiceFx: StateFlow<BoardJuiceFx?> = _boardJuiceFx.asStateFlow()

  private val _invalidSwapFx = MutableStateFlow<InvalidSwapFx?>(null)
  val invalidSwapFx: StateFlow<InvalidSwapFx?> = _invalidSwapFx.asStateFlow()

  private val _cardCastFx = MutableStateFlow<CardCastFx?>(null)
  val cardCastFx: StateFlow<CardCastFx?> = _cardCastFx.asStateFlow()

  private val _corruptSaveError = MutableStateFlow<String?>(null)
  val corruptSaveError: StateFlow<String?> = _corruptSaveError.asStateFlow()

  private sealed interface PersistRequest {
    data class SaveActive(val run: RunState) : PersistRequest
    data class RecordFinished(val run: RunState) : PersistRequest
    data object ClearActive : PersistRequest
    data class Flush(val ack: CompletableDeferred<Unit>) : PersistRequest
  }

  private val persistChannel = Channel<PersistRequest>(Channel.UNLIMITED)
  // Persistence outlives the ViewModel so onCleared() can close the queue and let the worker
  // drain already-accepted writes. This improves lifecycle reliability but cannot survive OS
  // process death before Room commits; callers requiring that guarantee must await flush.
  private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile private var persistenceFailure: Throwable? = null

  private val _destination = MutableStateFlow<AppDestination>(AppDestination.Title)
  val destination: StateFlow<AppDestination> = _destination.asStateFlow()

  init {
    hydrateCachedSoundtracks()
    // Strictly FIFO-ordered persistence worker prevents rapid player actions from racing out-of-order
    // and shields in-flight Room commits with NonCancellable so terminal saves are never dropped.
    persistenceScope.launch {
      for (request in persistChannel) {
        try {
          withContext(NonCancellable) {
            when (request) {
              is PersistRequest.SaveActive -> repository.saveActiveRun(request.run)
              is PersistRequest.RecordFinished -> repository.recordFinishedRun(request.run)
              PersistRequest.ClearActive -> repository.clearActiveRun()
              is PersistRequest.Flush -> {
                val priorFailure = persistenceFailure
                if (priorFailure == null) {
                  request.ack.complete(Unit)
                } else {
                  request.ack.completeExceptionally(
                    IllegalStateException("A persistence operation failed before the flush barrier.", priorFailure),
                  )
                }
              }
            }
          }
        } catch (c: CancellationException) {
          if (request is PersistRequest.Flush) request.ack.completeExceptionally(c)
          throw c
        } catch (e: Exception) {
          if (request is PersistRequest.Flush) {
            request.ack.completeExceptionally(e)
          } else {
            // Keep the failure sticky until process recreation: a later flush must not report
            // success merely because the worker continued after a failed save/terminal record.
            persistenceFailure = e
            Log.e("EmberfallViewModel", "Persistence request failed: ${request::class.simpleName}", e)
          }
        }
      }
    }
    viewModelScope.launch {
      settings.collect { prefs ->
        audioEngine.updateSettings(
          musicEnabled = prefs.musicEnabled,
          sfxEnabled = prefs.sfxEnabled,
          masterMuted = prefs.masterAudioMuted,
        )
        if (!isRobolectricTest && prefs.musicEnabled && !prefs.masterAudioMuted &&
          !_soundtrackUiState.value.autoGenerationAttempted
        ) {
          triggerAutoGenerateMissingSoundtracks()
        }
      }
    }
    viewModelScope.launch {
      _destination.collect { dest ->
        if (dest != AppDestination.Settings) {
          studioAuditionModeOverride = null
        }
        val mode = studioAuditionModeOverride
          ?: if (dest == AppDestination.CombatArena) MusicMode.COMBAT else MusicMode.EXPLORATION
        audioEngine.setMusicMode(mode)
        _soundtrackUiState.value = _soundtrackUiState.value.copy(activeMusicMode = mode)
      }
    }
  }

  fun onAppForeground() {
    audioEngine.onAppForeground()
  }

  fun onAppBackground() {
    audioEngine.onAppBackground()
  }

  override fun onCleared() {
    // Closing (rather than cancelling) lets the independent worker drain all accepted writes.
    persistChannel.close()
    audioEngine.release()
    super.onCleared()
  }

  fun navigateTo(dest: AppDestination) {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.65f)
    _destination.value = dest
  }

  fun startNewRun(
    heroClass: HeroClass,
    seedText: String,
    unlitCovenantActive: Boolean = false,
    dreadLevel: Int = 0,
  ) {
    audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.85f)
    viewModelScope.launch {
      val shadeDeck = repository.loadShadeDeckIds()
      val effectiveSeed = seedText.trim().ifEmpty {
        "EF-${(System.currentTimeMillis() % 899999 + 100000)}"
      }
      val newRun = GameEngine.startNewRun(
        heroClass = heroClass,
        seedText = effectiveSeed,
        previousRunDeckIds = shadeDeck,
        unlitCovenantActive = unlitCovenantActive && heroClass == HeroClass.THE_UNLIT,
        dreadLevel = dreadLevel,
      )
      _runState.value = newRun
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      enqueuePersistence(PersistRequest.SaveActive(newRun))
      _destination.value = AppDestination.ActMap
    }
  }

  /**
   * Zero-friction 1-tap Quick Dive from Title straight into Row 1 combat!
   */
  fun quickDive(heroClass: HeroClass = HeroClass.ASHEN_MONK) {
    audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.88f)
    viewModelScope.launch {
      val shadeDeck = repository.loadShadeDeckIds()
      val effectiveSeed = "QD-${(System.currentTimeMillis() % 899999 + 100000)}"
      val newRun = GameEngine.startNewRun(
        heroClass = heroClass,
        seedText = effectiveSeed,
        previousRunDeckIds = shadeDeck,
      )
      val firstNodeId = GameEngine.availableNodeIds(newRun).firstOrNull()
      val entered = if (firstNodeId != null) {
        val res = GameEngine.execute(newRun, GameCommand.ChooseNode(firstNodeId))
        if (res.accepted) res.run else newRun
      } else {
        newRun
      }
      _runState.value = entered
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      _boardJuiceFx.value = null
      _invalidSwapFx.value = null
      _cardCastFx.value = null
      enqueuePersistence(PersistRequest.SaveActive(entered))
      _destination.value = resolveDestinationForRun(entered)
    }
  }

  fun continueSavedRun() {
    viewModelScope.launch {
      when (val loadResult = repository.loadActiveRunResult()) {
        is ActiveRunLoadResult.Loaded -> {
          audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.80f)
          _corruptSaveError.value = null
          _runState.value = loadResult.run
          _selectedGroup.value = null
          _selectedSwapCoord.value = null
          _boardFeedback.value = null
          _destination.value = resolveDestinationForRun(loadResult.run)
        }
        ActiveRunLoadResult.Missing -> {
          audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
        }
        is ActiveRunLoadResult.Corrupt -> {
          audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.78f)
          _corruptSaveError.value = loadResult.reason
        }
      }
    }
  }

  fun dismissCorruptSaveError() {
    _corruptSaveError.value = null
  }

  fun discardCorruptActiveRun() {
    audioEngine.playSfx(MinimalSfx.ASH, volume = 0.72f)
    _corruptSaveError.value = null
    enqueuePersistence(PersistRequest.ClearActive)
  }

  fun enterMapNode(nodeId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ChooseNode(nodeId))
    if (!result.accepted) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      return
    }
    audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.80f)
    commitRunUpdate(result.run)
    _selectedGroup.value = null
    _selectedSwapCoord.value = null
    _boardFeedback.value = null
    _boardJuiceFx.value = null
    _invalidSwapFx.value = null
    _cardCastFx.value = null
    _destination.value = resolveDestinationForRun(result.run)
  }

  fun onBoardCellTapped(row: Int, col: Int) {
    val current = _runState.value ?: return
    val combat = current.combat ?: return
    if (combat.phase == TurnPhase.CAST) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _selectedSwapCoord.value = null
      _boardFeedback.value = "CAST phase: tap Ink cards below or press End Turn."
      return
    }
    if (combat.phase != TurnPhase.WEAVE) return
    if (combat.tapsRemaining <= 0) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _selectedSwapCoord.value = null
      _boardFeedback.value = "0 Moves left: tap 'Go to CAST' to spend Ink on cards."
      return
    }
    if (combat.board.isColumnSealed(col)) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.72f)
      _selectedSwapCoord.value = null
      _boardFeedback.value = "Column ${col + 1} is Sealed (${combat.board.sealedColumns[col]} turn(s) left)."
      return
    }

    val tile = combat.board.tileAt(row, col)
    when (tile?.kind) {
      TileKind.Empty -> {
        audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.55f)
        _selectedSwapCoord.value = null
        _boardFeedback.value = "Empty headroom socket — drag or swap tiles below to align 3+ matching colors."
        return
      }
      TileKind.Stone -> {
        audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.70f)
        _selectedSwapCoord.value = null
        _boardFeedback.value = "Stone (■) is immovable — shatter it beside a 4+ match."
        return
      }
      else -> Unit
    }

    val tappedCoord = BoardCoord(row, col)
    val prev = _selectedSwapCoord.value
    when {
      prev == null -> {
        // Functional One-Tap Quick-Swap setting: when enabled, tapping any tile that participates
        // in a legal Match-3 swap immediately executes its highest-ranked swap.
        if (settings.value.oneTapBoardFire) {
          val quickSwap = BoardEngine.findValidSwaps(combat.board, combat.spectrum)
            .firstOrNull { it.from == tappedCoord || it.to == tappedCoord }
          if (quickSwap != null) {
            onBoardSwap(quickSwap.from.row, quickSwap.from.col, quickSwap.to.row, quickSwap.to.col)
            return
          }
        }
        audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.72f)
        _selectedSwapCoord.value = tappedCoord
        _boardFeedback.value = null
      }
      prev == tappedCoord -> {
        audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.55f)
        _selectedSwapCoord.value = null
        _boardFeedback.value = null
      }
      kotlin.math.abs(prev.row - tappedCoord.row) + kotlin.math.abs(prev.col - tappedCoord.col) == 1 -> {
        onBoardSwap(prev.row, prev.col, tappedCoord.row, tappedCoord.col)
      }
      else -> {
        audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.72f)
        _selectedSwapCoord.value = tappedCoord
        _boardFeedback.value = null
      }
    }
  }

  fun onBoardSwap(r1: Int, c1: Int, r2: Int, c2: Int) {
    val current = _runState.value ?: return
    val combat = current.combat ?: return
    if (combat.phase == TurnPhase.CAST) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _selectedSwapCoord.value = null
      _boardFeedback.value = "CAST phase: tap Ink cards below or press End Turn."
      return
    }
    if (combat.phase != TurnPhase.WEAVE) return
    if (combat.tapsRemaining <= 0) {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _selectedSwapCoord.value = null
      _boardFeedback.value = "0 Moves left: tap 'Go to CAST' to spend Ink on cards."
      return
    }

    val tracedWaves = traceSwapWaves(combat, current.rng.boardPaint, r1, c1, r2, c2)
    val preWave = BoardEngine.previewSwap(combat.board, r1, c1, r2, c2, combat.spectrum.currentHue)
    val result = GameEngine.execute(current, GameCommand.SwapCells(r1, c1, r2, c2))
    if (result.accepted) {
      val totalDmgDealt = (combat.enemy.hp - (result.run.combat?.enemy?.hp ?: 0)).coerceAtLeast(0)
      val earnedBonusMove = result.events.any { it.kind == CombatEventKind.BONUS_MOVE }
      val forgedPrism = result.events.any { it.kind == CombatEventKind.PRISM_FORGED }
      emitBoardJuiceFx(tracedWaves, totalDmgDealt, preWave, earnedBonusMove, forgedPrism)
      triggerSwapAudio(tracedWaves, preWave, result.events, current, result.run)
      _selectedSwapCoord.value = null
      _selectedGroup.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
      _invalidSwapFx.value = InvalidSwapFx(
        id = ++fxSequence,
        from = BoardCoord(r1, c1),
        to = BoardCoord(r2, c2),
      )
      _selectedSwapCoord.value = null
      _boardFeedback.value = result.rejectionReason?.message
        ?: "Swap must align 3+ matching tiles in a row or column."
    }
  }

  private fun traceSwapWaves(
    combat: CombatState,
    initialBoardRng: SeededRng,
    r1: Int,
    c1: Int,
    r2: Int,
    c2: Int,
  ): List<FxWave> {
    val firstWave = BoardEngine.previewSwap(combat.board, r1, c1, r2, c2, combat.spectrum.currentHue)
    if (!firstWave.isValid) return emptyList()
    val waves = mutableListOf<FxWave>()
    var simBoard = BoardEngine.swapTilesInBoard(combat.board, r1, c1, r2, c2)
    var simDeck = combat.paintDeck
    var simRng = initialBoardRng
    var simSpec = combat.spectrum
    var active: SwapWaveResolution? = firstWave
    var waveIdx = 1

    while (active != null && active.isValid && waveIdx <= 8) {
      val (effHalf, nextSpec) = BoardEngine.previewSpectrumForPop(
        current = simSpec,
        resolvedHue = active.primaryHue,
        usedPrism = active.prismCount > 0,
        isDirectAshGroup = active.isDirectAshOnly,
      )
      simSpec = nextSpec
      val waveDmg = if (active.isDirectAshOnly) {
        active.ashChipDamage
      } else {
        val base = (active.basePower * effHalf) / 2
        val cascadeBonus = if (waveIdx > 1) (base * (waveIdx - 1)) / 4 else 0
        base + cascadeBonus
      }

      val matchedCells = active.coords.mapNotNull { coord ->
        simBoard.tileAt(coord.row, coord.col)?.let { FxCell(coord.row, coord.col, it.kind) }
      }
      val ashCells = active.clearedAsh.mapNotNull { coord ->
        simBoard.tileAt(coord.row, coord.col)?.let { FxCell(coord.row, coord.col, it.kind) }
      }
      val stoneCells = active.shatteredStone.mapNotNull { coord ->
        simBoard.tileAt(coord.row, coord.col)?.let { FxCell(coord.row, coord.col, it.kind) }
      }
      waves.add(
        FxWave(
          waveIndex = waveIdx,
          matchedCells = matchedCells,
          ashCells = ashCells,
          stoneCells = stoneCells,
          primaryHue = active.primaryHue,
          isPrism = active.prismCount > 0,
          waveDamage = waveDmg,
        )
      )

      val stepped = BoardEngine.stepConsumeWithoutEnsure(
        board = simBoard,
        consumedCoords = active.coords + active.clearedAsh + active.shatteredStone,
        paintDeck = simDeck,
        rng = simRng,
      )
      simBoard = stepped.board
      simDeck = stepped.paintDeck
      simRng = stepped.rng
      waveIdx++
      active = BoardEngine.resolveBoardMatches(simBoard, simSpec.currentHue).takeIf { it.isValid }
    }
    return waves
  }

  private fun emitBoardJuiceFx(
    waves: List<FxWave>,
    totalDamage: Int,
    firstWave: SwapWaveResolution,
    earnedBonusMove: Boolean = false,
    forgedPrism: Boolean = false,
  ) {
    if (waves.isEmpty()) return
    val cascadeCount = waves.size
    val totalTiles = waves.sumOf { it.allCells.size }
    val hasPrism = waves.any { it.isPrism } || forgedPrism
    val hasStone = waves.any { it.stoneCells.isNotEmpty() }
    val headline = when {
      earnedBonusMove && forgedPrism -> "EMBER SURGE! +1 MOVE"
      cascadeCount >= 3 -> "MEGA CHAIN ×$cascadeCount!"
      earnedBonusMove && cascadeCount == 2 -> "CASCADE ×2 • +1 MOVE!"
      forgedPrism -> "PRISM FORGED!"
      cascadeCount == 2 -> "CASCADE ×2!"
      hasPrism -> "PRISM BLAST!"
      hasStone -> "STONE SHATTER!"
      firstWave.coords.size >= 5 -> "${firstWave.coords.size}-MATCH SURGE!"
      firstWave.coords.size == 4 -> "4-MATCH CRUSH!"
      totalDamage > 0 -> "-$totalDamage DMG"
      else -> "$totalTiles CLEARED!"
    }
    val subline = buildString {
      if (forgedPrism) append("★ PRISM FORGED")
      if (totalDamage > 0 && !headline.startsWith("-")) {
        if (isNotEmpty()) append(" • ")
        append("-$totalDamage DMG")
      }
      if (firstWave.totalInkAmount > 0 && firstWave.primaryHue != null) {
        if (isNotEmpty()) append(" • ")
        append("+${firstWave.totalInkAmount} ${firstWave.primaryHue.shortTag}")
      } else if (totalTiles > 0 && isEmpty()) {
        append("$totalTiles tiles cleared")
      }
    }.ifBlank { null }

    _boardJuiceFx.value = BoardJuiceFx(
      id = ++fxSequence,
      waves = waves,
      totalDamage = totalDamage,
      cascadeCount = cascadeCount,
      primaryHue = firstWave.primaryHue ?: waves.firstNotNullOfOrNull { it.primaryHue },
      isPrismBlast = hasPrism,
      headline = headline,
      subline = subline,
    )
  }

  fun confirmSelectedGroup() {
    val current = _runState.value ?: return
    val preGroup = _selectedGroup.value ?: return
    val anchor = preGroup.coords.firstOrNull() ?: return
    val result = GameEngine.execute(current, GameCommand.TapCell(anchor.row, anchor.col))
    if (result.accepted) {
      triggerPopAudio(preGroup, result.events, current, result.run)
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
    }
  }

  fun onBoardSwapDragAttempted() {
    audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
    _selectedSwapCoord.value = null
    _boardFeedback.value = "Swap must align 3+ matching tiles horizontally or vertically!"
  }

  private fun triggerSwapAudio(
    tracedWaves: List<FxWave>,
    preWave: SwapWaveResolution,
    events: List<CombatEvent>,
    before: RunState,
    after: RunState,
  ) {
    val popEvents = events.filter { it.kind == CombatEventKind.BOARD_POP }
    val effectiveSpectrumHalfSteps = popEvents.firstOrNull()?.effectiveSpectrumHalfSteps
      ?: after.combat?.spectrum?.halfSteps
      ?: 2
    val waveHues = if (tracedWaves.isNotEmpty()) {
      tracedWaves.map { it.primaryHue }
    } else {
      listOf(preWave.primaryHue)
    }
    audioEngine.playCascadeSequence(
      waveHues = waveHues,
      baseSpectrumHalfSteps = effectiveSpectrumHalfSteps,
      usedPrism = preWave.prismCount > 0 || tracedWaves.any { it.isPrism },
      clearedAsh = preWave.clearedAsh.isNotEmpty() || tracedWaves.any { it.ashCells.isNotEmpty() },
      shatteredStone = preWave.shatteredStone.isNotEmpty() || tracedWaves.any { it.stoneCells.isNotEmpty() },
    )
    if (events.any { it.kind == CombatEventKind.BRAND_DRAIN && it.isAlert } || after.hp < before.hp) {
      audioEngine.playSfx(MinimalSfx.ALARM, volume = 0.75f)
    }
    if (events.any { it.kind == CombatEventKind.VICTORY } || after.combat?.phase == TurnPhase.COMBAT_WON) {
      audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.92f)
    }
  }

  private fun triggerPopAudio(
    preGroup: GroupResolution,
    events: List<CombatEvent>,
    before: RunState,
    after: RunState,
  ) {
    // EFB-019: Use the exact effective Spectrum multiplier reported for THIS pop (pre-pop state),
    // not the post-pop mutated state (which resets to 1.0x after Prism or may be cleared on combat end).
    val effectiveSpectrumHalfSteps = events
      .firstOrNull { it.kind == CombatEventKind.BOARD_POP }
      ?.effectiveSpectrumHalfSteps
      ?: after.combat?.spectrum?.halfSteps
      ?: 2
    audioEngine.playHuePop(
      hue = preGroup.resolvedHue,
      spectrumHalfSteps = effectiveSpectrumHalfSteps,
      usedPrism = preGroup.prismCount > 0,
      clearedAsh = preGroup.clearedAsh.isNotEmpty(),
      shatteredStone = preGroup.shatteredStone.isNotEmpty(),
    )
    if (events.any { it.kind == CombatEventKind.BRAND_DRAIN && it.isAlert } || after.hp < before.hp) {
      audioEngine.playSfx(MinimalSfx.ALARM, volume = 0.75f)
    }
    if (events.any { it.kind == CombatEventKind.VICTORY } || after.combat?.phase == TurnPhase.COMBAT_WON) {
      audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.92f)
    }
  }

  fun useChiselWand(row: Int, col: Int, targetHue: Hue) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.UseChiselWand(row, col, targetHue))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.85f)
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun triggerOverdrive() {
    val current = _runState.value ?: return
    val combat = current.combat ?: return
    val preCells = buildList {
      for (r in 0 until combat.board.rows) {
        for (c in 0 until combat.board.cols) {
          if (combat.board.isColumnSealed(c)) continue
          val k = combat.board.tileAt(r, c)?.kind ?: continue
          val matchesHero = when (current.heroClass) {
            HeroClass.ASHEN_MONK -> k == TileKind.Playable(Hue.EMBER) || k == TileKind.Playable(Hue.GOLD)
            HeroClass.QUARRY_WITCH -> k is TileKind.Stone || k is TileKind.Ash
            HeroClass.THE_UNLIT -> k is TileKind.Ash
          }
          if (matchesHero) add(FxCell(r, c, k))
        }
      }
    }.ifEmpty {
      listOf(FxCell(3, 3, TileKind.Prism), FxCell(3, 2, TileKind.Prism), FxCell(3, 4, TileKind.Prism))
    }

    val result = GameEngine.execute(current, GameCommand.TriggerOverdrive)
    if (result.accepted) {
      val dmgDealt = (combat.enemy.hp - (result.run.combat?.enemy?.hp ?: 0)).coerceAtLeast(0)
      val skillDef = ProgressionCatalog.signatureSkillFor(current.heroClass)
      val links = current.growth.socketedSupportIds.size
      val breakdown = result.run.combat?.lastScoringBreakdown
      val ultHeadline = if (links > 0) "${skillDef.name.uppercase()} (${links}-LINK)!" else "${skillDef.name.uppercase()}!"
      val multTxt = breakdown?.let { "${it.finalMultTenths / 10}.${it.finalMultTenths % 10}×" } ?: "1.0×"
      val ultSub = when (current.heroClass) {
        HeroClass.ASHEN_MONK -> "-$dmgDealt DMG ($multTxt) • +2 VIGIL • +1 EMB"
        HeroClass.QUARRY_WITCH -> "-$dmgDealt DMG ($multTxt) • +4 GUARD • +1 PRISM"
        HeroClass.THE_UNLIT -> "-$dmgDealt DMG ($multTxt) • +1 SPARK • +1 PRISM"
      }
      _boardJuiceFx.value = BoardJuiceFx(
        id = ++fxSequence,
        waves = listOf(
          FxWave(
            waveIndex = 1,
            matchedCells = preCells,
            primaryHue = current.heroClass.primaryHue,
            isPrism = links >= 2,
            waveDamage = dmgDealt,
          )
        ),
        totalDamage = dmgDealt,
        cascadeCount = (links + 1).coerceAtLeast(1),
        primaryHue = current.heroClass.primaryHue,
        isPrismBlast = links >= 2,
        headline = ultHeadline,
        subline = ultSub,
      )
      _cardCastFx.value = CardCastFx(
        id = ++fxSequence,
        hue = current.heroClass.primaryHue,
        damageDealt = dmgDealt,
        guardGained = if (current.heroClass == HeroClass.QUARRY_WITCH) 4 else 0,
        isEcho = links > 0,
        cardName = ultHeadline,
      )
      audioEngine.playCascadeSequence(
        waveHues = listOf(current.heroClass.primaryHue, Hue.GOLD, Hue.VIOLET),
        baseSpectrumHalfSteps = maxOf(2 + links, combat.spectrum.halfSteps),
        usedPrism = links > 0,
        clearedAsh = current.heroClass != HeroClass.ASHEN_MONK,
        shatteredStone = current.heroClass == HeroClass.QUARRY_WITCH,
      )
      if (result.run.combat?.phase == TurnPhase.COMBAT_WON) {
        audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.94f)
      }
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun witnessIllusion() {
    val current = _runState.value ?: return
    val combat = current.combat ?: return
    val enemyHpBefore = combat.enemy.hp
    val result = GameEngine.execute(current, GameCommand.WitnessIllusion)
    if (result.accepted) {
      val shatteredNow = result.events.any { it.kind == CombatEventKind.ILLUSION_SHATTERED }
      val dmgDealt = (enemyHpBefore - (result.run.combat?.enemy?.hp ?: 0)).coerceAtLeast(0)
      if (shatteredNow) {
        audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.94f)
        _cardCastFx.value = CardCastFx(
          id = ++fxSequence,
          hue = combat.mindIllusion.illusion.hue,
          damageDealt = dmgDealt,
          guardGained = 4,
          isEcho = true,
          cardName = "PRATYABHIJÑĀ!",
        )
      } else {
        audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.85f)
      }
      if (result.run.combat?.phase == TurnPhase.COMBAT_WON) {
        audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.94f)
      }
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun proceedToCast() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ProceedToCast)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.80f)
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
    }
  }

  fun playCard(cardUid: Long, genericPayHue: Hue? = null) {
    val current = _runState.value ?: return
    val currentCombat = current.combat ?: return
    // Zero-friction 1-tap casting: if an affordable card is tapped during WEAVE,
    // cleanly advance WEAVE -> CAST via canonical ProceedToCast before CastCard (ADR-001 intact).
    val castBase = if (currentCombat.phase == TurnPhase.WEAVE) {
      val toCast = GameEngine.execute(current, GameCommand.ProceedToCast)
      if (!toCast.accepted) {
        audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
        return
      }
      toCast.run
    } else {
      current
    }
    val cardDef = castBase.combat?.combatDeck?.hand?.firstOrNull { it.uid == cardUid }?.let {
      GameCatalog.cardById(it.defId)
    }
    val enemyHpBefore = castBase.combat?.enemy?.hp ?: 0
    val guardBefore = castBase.combat?.player?.guardBlock ?: 0

    val result = GameEngine.execute(castBase, GameCommand.CastCard(cardUid, genericPayHue))
    if (result.accepted) {
      val enemyHpAfter = result.run.combat?.enemy?.hp ?: 0
      val guardAfter = result.run.combat?.player?.guardBlock ?: guardBefore
      val isEcho = result.events.any { it.isEcho }
      audioEngine.playSfx(MinimalSfx.CARD_CAST, volume = 0.88f)
      if (isEcho) {
        audioEngine.playSfx(MinimalSfx.ECHO, volume = 0.85f)
      }
      if (result.run.combat?.phase == TurnPhase.COMBAT_WON) {
        audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.92f)
      }
      _cardCastFx.value = CardCastFx(
        id = ++fxSequence,
        hue = cardDef?.frameHue,
        damageDealt = (enemyHpBefore - enemyHpAfter).coerceAtLeast(0),
        guardGained = (guardAfter - guardBefore).coerceAtLeast(0),
        isEcho = isEcho,
        cardName = cardDef?.name ?: "Ink Card",
      )
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun useFlask() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.UseFlask)
    if (result.accepted) {
      if (result.run.gameOverReason == GameOverReason.ZERO_FLASKS) {
        audioEngine.playSfx(MinimalSfx.ALARM, volume = 0.95f)
      } else {
        audioEngine.playSfx(MinimalSfx.FLASK, volume = 0.86f)
      }
      _selectedGroup.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun endTurn() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.EndTurn)
    if (result.accepted) {
      // EFB-020: Map audio strictly from typed CombatEventKind rather than message string matching
      // or raw HP delta (so Wall Overflow HP loss does not falsely fire ENEMY_HIT).
      val hasVictory = result.events.any { it.kind == CombatEventKind.VICTORY } ||
        result.run.combat?.phase == TurnPhase.COMBAT_WON
      val hasOverflow = result.events.any { it.kind == CombatEventKind.WALL_OVERFLOW }
      val hasWallRise = result.events.any { it.kind == CombatEventKind.WALL_RISE }
      val hasEnemyDamage = result.events.any { it.kind == CombatEventKind.ENEMY_DAMAGE }
      when {
        hasVictory -> audioEngine.playSfx(MinimalSfx.VICTORY, volume = 0.92f)
        hasOverflow || result.run.gameOverReason != null ->
          audioEngine.playSfx(MinimalSfx.ALARM, volume = 0.92f)
        hasWallRise -> audioEngine.playSfx(MinimalSfx.WALL_RISE, volume = 0.82f)
      }
      if (hasEnemyDamage) {
        audioEngine.playSfx(MinimalSfx.ENEMY_HIT, volume = 0.88f)
      }
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    }
  }

  fun proceedFromCombatVictory() {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.72f)
    val current = _runState.value ?: return
    if (current.runWon) {
      _destination.value = AppDestination.RunSummary
      return
    }
    val result = GameEngine.execute(current, GameCommand.OpenRewards)
    if (result.accepted) {
      commitRunUpdate(result.run, flushAfterWrite = true)
      _destination.value = AppDestination.Rewards
    }
  }

  fun pickRewardCard(cardDefId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.PickRewardCard(cardDefId))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.82f)
      completeRewardsIfResolved(result.run)
    }
  }

  fun skipRewardCard() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.SkipRewardCard)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.65f)
      completeRewardsIfResolved(result.run)
    }
  }

  fun rerollRewardCards() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.RerollRewardCards)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.84f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun claimRewardRelic() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ClaimRewardRelic)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.82f)
      completeRewardsIfResolved(result.run)
    }
  }

  fun skipRewardRelic() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.SkipRewardRelic)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.65f)
      completeRewardsIfResolved(result.run)
    }
  }

  private fun completeRewardsIfResolved(updated: RunState) {
    // When resolving spoils inside the inline CombatArena overlay, stay on the overlay so the
    // player can allocate Upāya passives, Dhāraṇā supports, Tantric artifacts, and single-tap next room!
    if (updated.combat?.phase == TurnPhase.COMBAT_WON) {
      commitRunUpdate(updated, flushAfterWrite = true)
      return
    }
    val reward = updated.pendingReward
    val allResolved = reward?.allChoicesResolved == true
    if (allResolved) {
      val finished = GameEngine.execute(updated, GameCommand.FinishRewards)
      if (finished.accepted) {
        commitRunUpdate(finished.run, flushAfterWrite = true)
        _destination.value = AppDestination.ActMap
        return
      }
    }
    commitRunUpdate(updated, flushAfterWrite = true)
  }

  fun unlockPassiveNode(nodeId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.UnlockPassiveNode(nodeId))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.88f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun socketSupport(supportId: String, replaceSlot: Int = -1) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.SocketSupport(supportId, replaceSlot))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.90f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun skipSupportChoice() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.SkipSupportChoice)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.68f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    }
  }

  fun bindArtifact(artifactId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.BindArtifact(artifactId))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.90f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = result.rejectionReason?.message
    }
  }

  fun skipArtifactChoice() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.SkipArtifactChoice)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.68f)
      commitRunUpdate(result.run, flushAfterWrite = true)
    }
  }

  /**
   * Single-tap descent from the inline spoils overlay straight into the chosen next room!
   * Resolves FinishRewards and immediately enters ChooseNode(nodeId) without Map ping-pong.
   */
  fun advanceInlineToNode(nodeId: String) {
    val current = _runState.value ?: return
    if (current.runWon) {
      _destination.value = AppDestination.RunSummary
      return
    }
    var state = current
    if (state.pendingReward != null) {
      val finished = GameEngine.execute(state, GameCommand.FinishRewards)
      if (!finished.accepted) {
        audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
        _boardFeedback.value = finished.rejectionReason?.message
        return
      }
      state = finished.run
    }
    val entered = GameEngine.execute(state, GameCommand.ChooseNode(nodeId))
    if (entered.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.85f)
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(entered.run, flushAfterWrite = true)
      _destination.value = resolveDestinationForRun(entered.run)
    } else {
      commitRunUpdate(state)
      _destination.value = AppDestination.ActMap
    }
  }

  fun openFullMapFromInlineSpoils() {
    val current = _runState.value ?: return
    if (current.runWon) {
      _destination.value = AppDestination.RunSummary
      return
    }
    val finished = GameEngine.execute(current, GameCommand.FinishRewards)
    if (finished.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.78f)
      _selectedGroup.value = null
      _selectedSwapCoord.value = null
      _boardFeedback.value = null
      commitRunUpdate(finished.run, flushAfterWrite = true)
      _destination.value = AppDestination.ActMap
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
      _boardFeedback.value = finished.rejectionReason?.message
    }
  }

  fun finishRewards() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.FinishRewards)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.78f)
      commitRunUpdate(result.run, flushAfterWrite = true)
      _destination.value = AppDestination.ActMap
    }
  }

  fun campfireRest() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.CampfireRest)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.FLASK, volume = 0.84f)
      commitRunUpdate(result.run)
    }
  }

  fun campfireUpgradeCard(cardUid: Long) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.CampfireUpgradeCard(cardUid))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.84f)
      commitRunUpdate(result.run)
    }
  }

  fun campfireGamble() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.CampfireGamble)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.88f)
      commitRunUpdate(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.68f)
    }
  }

  fun chooseEventOption(choiceId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ChooseEventOption(choiceId))
    if (result.accepted) {
      audioEngine.playSfx(if (result.run.hp < current.hp) MinimalSfx.ALARM else MinimalSfx.SELECT, volume = 0.82f)
      commitRunUpdate(result.run)
      checkRunEndTransition(result.run)
    }
  }

  fun shopBuyCard(cardId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ShopBuyCard(cardId))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.80f)
      commitRunUpdate(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun shopBuyRelic(relicId: String) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ShopBuyRelic(relicId))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.PRISM, volume = 0.84f)
      commitRunUpdate(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun shopBuyFlask() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ShopBuyFlask)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.FLASK, volume = 0.84f)
      commitRunUpdate(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun shopRemoveCard(cardUid: Long) {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.ShopRemoveCard(cardUid))
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.ASH, volume = 0.78f)
      commitRunUpdate(result.run)
    } else {
      audioEngine.playSfx(MinimalSfx.REJECT, volume = 0.65f)
    }
  }

  fun leaveNodeToMap() {
    val current = _runState.value ?: return
    val result = GameEngine.execute(current, GameCommand.LeaveNode)
    if (result.accepted) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.70f)
      commitRunUpdate(result.run)
      _destination.value = AppDestination.ActMap
    }
  }

  fun viewRunSummaryOnDefeat() {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.70f)
    _destination.value = AppDestination.RunSummary
  }

  fun finishSummaryToTitle() {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.70f)
    _runState.value = null
    _selectedGroup.value = null
    _boardFeedback.value = null
    _destination.value = AppDestination.Title
  }

  fun toggleMusic(enabled: Boolean) {
    viewModelScope.launch { repository.updateMusicEnabled(enabled) }
  }

  fun toggleSfx(enabled: Boolean) {
    if (enabled) audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.75f)
    viewModelScope.launch { repository.updateSfxEnabled(enabled) }
  }

  fun toggleAudioMute() {
    val current = settings.value
    // EFB-013: Toggle independent master mute without clobbering individual Music/SFX preferences.
    // If both channels were individually disabled in Settings and the user taps unmute, restore both.
    val nextMasterMuted = current.effectiveAudioOn
    val restoreChannels = !nextMasterMuted && !current.musicEnabled && !current.sfxEnabled
    val effectiveMusic = if (restoreChannels) true else current.musicEnabled
    val effectiveSfx = if (restoreChannels) true else current.sfxEnabled
    audioEngine.updateSettings(
      musicEnabled = effectiveMusic,
      sfxEnabled = effectiveSfx,
      masterMuted = nextMasterMuted,
    )
    if (!nextMasterMuted && effectiveSfx) {
      audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.75f)
    }
    viewModelScope.launch {
      repository.updateMasterAudioMuted(nextMasterMuted)
      if (restoreChannels) {
        repository.updateMusicEnabled(true)
        repository.updateSfxEnabled(true)
      }
    }
  }

  fun toggleHaptics(enabled: Boolean) {
    viewModelScope.launch { repository.updateHaptics(enabled) }
  }

  fun toggleFastAnimations(enabled: Boolean) {
    viewModelScope.launch { repository.updateFastAnimations(enabled) }
  }

  fun toggleColorblindSymbols(enabled: Boolean) {
    viewModelScope.launch { repository.updateColorblindSymbols(enabled) }
  }

  fun toggleTacticalHints(enabled: Boolean) {
    viewModelScope.launch { repository.updateTacticalHints(enabled) }
  }

  fun toggleOneTapBoardFire(enabled: Boolean) {
    viewModelScope.launch { repository.updateOneTapBoardFire(enabled) }
  }

  // --- Lyria 3 Soundtrack Studio & Cache Orchestration ---

  private fun hydrateCachedSoundtracks() {
    val cachedExp = soundtrackRepository.loadCached(SoundtrackSlot.EXPLORATION)
    val cachedCbt = soundtrackRepository.loadCached(SoundtrackSlot.COMBAT)
    cachedExp?.let { (_, file) -> audioEngine.setLyriaTrackFile(MusicMode.EXPLORATION, file) }
    cachedCbt?.let { (_, file) -> audioEngine.setLyriaTrackFile(MusicMode.COMBAT, file) }
    _soundtrackUiState.value = _soundtrackUiState.value.copy(
      exploration = cachedExp?.let { (meta, file) -> SoundtrackSlotStatus.Ready(meta, file.absolutePath) }
        ?: SoundtrackSlotStatus.Idle,
      combat = cachedCbt?.let { (meta, file) -> SoundtrackSlotStatus.Ready(meta, file.absolutePath) }
        ?: SoundtrackSlotStatus.Idle,
    )
  }

  private fun triggerAutoGenerateMissingSoundtracks() {
    val cur = _soundtrackUiState.value
    if (cur.autoGenerationAttempted) return
    _soundtrackUiState.value = cur.copy(autoGenerationAttempted = true)
    val missingSlots = SoundtrackSlot.entries.filter { cur.statusFor(it) !is SoundtrackSlotStatus.Ready }
    if (missingSlots.isEmpty()) return

    autoComposeJob?.cancel()
    autoComposeJob = viewModelScope.launch {
      for (slot in missingSlots) {
        composeSlotInternal(
          slot = slot,
          tier = SoundtrackModelTier.PRO_FULL_LOOP,
          preset = _soundtrackUiState.value.selectedPreset,
          customDirection = _soundtrackUiState.value.customDirection,
        )
      }
    }
  }

  fun selectStudioModelTier(tier: SoundtrackModelTier) {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.6f)
    _soundtrackUiState.value = _soundtrackUiState.value.copy(selectedTier = tier)
  }

  fun selectStudioPreset(preset: SoundtrackPreset) {
    audioEngine.playSfx(MinimalSfx.UI_CLICK, volume = 0.6f)
    _soundtrackUiState.value = _soundtrackUiState.value.copy(selectedPreset = preset)
  }

  fun updateStudioCustomDirection(direction: String) {
    _soundtrackUiState.value = _soundtrackUiState.value.copy(customDirection = direction.take(160))
  }

  fun auditionSoundtrackMode(mode: MusicMode) {
    audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.65f)
    studioAuditionModeOverride = mode
    audioEngine.setMusicMode(mode)
    _soundtrackUiState.value = _soundtrackUiState.value.copy(activeMusicMode = mode)
  }

  fun composeSoundtrackSlot(slot: SoundtrackSlot) {
    audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.72f)
    val snap = _soundtrackUiState.value
    slotComposeJobs[slot]?.cancel()
    slotComposeJobs[slot] = viewModelScope.launch {
      composeSlotInternal(
        slot = slot,
        tier = snap.selectedTier,
        preset = snap.selectedPreset,
        customDirection = snap.customDirection,
      )
    }
  }

  fun composeMissingOrRegenerateSoundtracks() {
    audioEngine.playSfx(MinimalSfx.SELECT, volume = 0.75f)
    val snap = _soundtrackUiState.value
    val targets = SoundtrackSlot.entries.filter { snap.statusFor(it) !is SoundtrackSlotStatus.Ready }
      .ifEmpty { SoundtrackSlot.entries }
    autoComposeJob?.cancel()
    autoComposeJob = viewModelScope.launch {
      for (slot in targets) {
        composeSlotInternal(
          slot = slot,
          tier = snap.selectedTier,
          preset = snap.selectedPreset,
          customDirection = snap.customDirection,
        )
      }
    }
  }

  fun clearSoundtrackCache() {
    audioEngine.playSfx(MinimalSfx.ASH, volume = 0.7f)
    autoComposeJob?.cancel()
    slotComposeJobs.values.forEach { it.cancel() }
    slotComposeJobs.clear()
    viewModelScope.launch {
      soundtrackRepository.clearCache()
      audioEngine.setLyriaTrackFile(MusicMode.EXPLORATION, null)
      audioEngine.setLyriaTrackFile(MusicMode.COMBAT, null)
      _soundtrackUiState.value = _soundtrackUiState.value.copy(
        exploration = SoundtrackSlotStatus.Idle,
        combat = SoundtrackSlotStatus.Idle,
      )
    }
  }

  private suspend fun composeSlotInternal(
    slot: SoundtrackSlot,
    tier: SoundtrackModelTier,
    preset: SoundtrackPreset,
    customDirection: String,
  ) {
    updateSlotStatus(slot, SoundtrackSlotStatus.Generating(tier.modelId, System.currentTimeMillis()))
    try {
      val (meta, file) = soundtrackRepository.generateAndCache(
        slot = slot,
        tier = tier,
        preset = preset,
        customDirection = customDirection,
      )
      audioEngine.setLyriaTrackFile(slot.musicMode, file)
      updateSlotStatus(slot, SoundtrackSlotStatus.Ready(meta, file.absolutePath))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e("EmberfallViewModel", "Failed to compose ${slot.id} soundtrack with ${tier.modelId}", e)
      updateSlotStatus(
        slot,
        SoundtrackSlotStatus.Error(
          e.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Unable to generate ${slot.displayName} soundtrack (${tier.modelId}).",
        ),
      )
    }
  }

  private fun updateSlotStatus(slot: SoundtrackSlot, status: SoundtrackSlotStatus) {
    val cur = _soundtrackUiState.value
    _soundtrackUiState.value = when (slot) {
      SoundtrackSlot.EXPLORATION -> cur.copy(exploration = status)
      SoundtrackSlot.COMBAT -> cur.copy(combat = status)
    }
  }

  private fun commitRunUpdate(updated: RunState, flushAfterWrite: Boolean = false) {
    _runState.value = updated
    if (updated.runWon || updated.gameOverReason != null) {
      enqueuePersistence(PersistRequest.RecordFinished(updated))
    } else {
      enqueuePersistence(PersistRequest.SaveActive(updated))
    }
    if (flushAfterWrite) {
      // Reward choices can transition directly into the next encounter. Queue a FIFO barrier after
      // each accepted choice so the snapshot is committed before a rapid lifecycle interruption.
      persistenceScope.launch {
        try {
          flushPendingPersistence()
        } catch (e: Exception) {
          persistenceFailure = e
          Log.e("EmberfallViewModel", "Reward snapshot flush failed.", e)
        }
      }
    }
  }

  private fun enqueuePersistence(request: PersistRequest) {
    val result = persistChannel.trySend(request)
    if (result.isFailure) {
      val failure = result.exceptionOrNull() ?: IllegalStateException("Persistence queue rejected a request.")
      persistenceFailure = failure
      Log.e("EmberfallViewModel", "Persistence request could not be queued: ${request::class.simpleName}", failure)
    }
  }

  internal suspend fun flushPendingPersistence() {
    val ack = CompletableDeferred<Unit>()
    persistChannel.send(PersistRequest.Flush(ack))
    withTimeout(10_000L) { ack.await() }
  }

  private fun checkRunEndTransition(updated: RunState) {
    if (updated.runWon || updated.gameOverReason != null) {
      _destination.value = AppDestination.RunSummary
    }
  }

  internal fun resolveDestinationForRun(run: RunState): AppDestination = when {
    run.runWon || run.gameOverReason != null -> AppDestination.RunSummary
    run.combat != null -> AppDestination.CombatArena
    run.pendingReward != null -> AppDestination.Rewards
    run.shopStock != null -> AppDestination.Merchant
    run.activeEvent != null -> AppDestination.Event
    // EFB-005: Keep Campfire destination whenever inNonCombatNode=true (including after Rest/Upgrade)
    // so the player can cleanly invoke LeaveNode instead of soft-locking on ActMap.
    run.inNonCombatNode &&
      run.currentNodeId != null &&
      run.actMap.nodes.firstOrNull { it.id == run.currentNodeId }?.type == MapNodeType.CAMPFIRE -> AppDestination.Campfire
    else -> AppDestination.ActMap
  }
}
