package com.example.data

import com.example.engine.RunState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class ActiveRunLoadResult {
  data class Loaded(val run: RunState) : ActiveRunLoadResult()
  data object Missing : ActiveRunLoadResult()
  data class Corrupt(val reason: String, val rawPayloadJson: String) : ActiveRunLoadResult()
}

class EmberfallRepository(
  private val dao: EmberfallDao,
  private val settingsDataStore: SettingsDataStore,
) {
  private val writeMutex = Mutex()

  val activeRunSummary: Flow<ActiveRunEntity?> = dao.observeActiveRun()
  val runHistory: Flow<List<RunHistoryEntity>> = dao.observeRunHistory()
  val settings: Flow<PlayerSettings> = settingsDataStore.settingsFlow

  suspend fun saveActiveRun(run: RunState) {
    writeMutex.withLock {
      val entity = ActiveRunEntity(
        id = 1,
        seedLabel = run.seedLabel,
        heroClassName = run.heroClass.displayName,
        act = run.currentAct,
        hp = run.hp,
        maxHp = run.maxHp,
        flasks = run.flasks,
        payloadJson = RunSnapshotCodec.encode(run),
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.saveActiveRun(entity)
    }
  }

  /**
   * EFB-010: Distinguishes missing active save from corrupt/incompatible snapshot payloads
   * so the UI can surface a recovery dialog instead of silently no-oping.
   */
  suspend fun loadActiveRunResult(): ActiveRunLoadResult {
    val entity = dao.getActiveRun() ?: return ActiveRunLoadResult.Missing
    return try {
      ActiveRunLoadResult.Loaded(RunSnapshotCodec.decode(entity.payloadJson))
    } catch (c: CancellationException) {
      throw c
    } catch (e: Exception) {
      ActiveRunLoadResult.Corrupt(
        reason = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName,
        rawPayloadJson = entity.payloadJson,
      )
    }
  }

  suspend fun loadActiveRun(): RunState? = when (val res = loadActiveRunResult()) {
    is ActiveRunLoadResult.Loaded -> res.run
    ActiveRunLoadResult.Missing, is ActiveRunLoadResult.Corrupt -> null
  }

  suspend fun loadShadeDeckIds(): List<String> {
    val entity = dao.getShadeDeck() ?: return emptyList()
    return entity.cardIdsCsv.split(",").filter { it.isNotBlank() }
  }

  suspend fun clearActiveRun() {
    writeMutex.withLock {
      dao.clearActiveRun()
    }
  }

  /**
   * EFB-006 + EFB-009:
   * - Only DEFEATED runs overwrite the dead Shade deck (per ADR-012 / Shade memory rule).
   * - History insert, optional Shade deck update, and active_run deletion execute atomically in one Room transaction.
   */
  suspend fun recordFinishedRun(run: RunState) {
    writeMutex.withLock {
      val now = System.currentTimeMillis()
      val reason = when {
        run.runWon && run.dreadEndingAchieved -> "Dread Sovereign Extinguished"
        run.runWon -> "Ember Throne Reclaimed"
        run.gameOverReason != null -> run.gameOverReason.label
        else -> "Abandoned"
      }
      val history = RunHistoryEntity(
        seedLabel = run.seedLabel,
        heroClassName = run.heroClass.displayName,
        actReached = run.currentAct,
        victory = run.runWon,
        dreadEndingAchieved = run.dreadEndingAchieved,
        terminationReason = reason,
        enemiesSlain = run.stats.enemiesSlain,
        turnsTaken = run.stats.turnsTaken,
        highestSpectrumHalfSteps = run.stats.highestSpectrumHalfSteps,
        finishedAtMillis = now,
      )
      val shadeDeck = if (!run.runWon) {
        ShadeDeckEntity(
          id = 1,
          heroClassName = run.heroClass.displayName,
          seedLabel = run.seedLabel,
          cardIdsCsv = run.masterDeck.joinToString(",") { it.defId },
          recordedAtMillis = now,
        )
      } else {
        null
      }
      dao.finishRunAtomic(history = history, shadeDeck = shadeDeck)
    }
  }

  suspend fun updateMusicEnabled(enabled: Boolean) = settingsDataStore.setMusicEnabled(enabled)
  suspend fun updateSfxEnabled(enabled: Boolean) = settingsDataStore.setSfxEnabled(enabled)
  suspend fun updateMasterAudioMuted(muted: Boolean) = settingsDataStore.setMasterAudioMuted(muted)
  suspend fun updateHaptics(enabled: Boolean) = settingsDataStore.setHapticsEnabled(enabled)
  suspend fun updateFastAnimations(enabled: Boolean) = settingsDataStore.setFastAnimations(enabled)
  suspend fun updateColorblindSymbols(enabled: Boolean) = settingsDataStore.setColorblindSymbols(enabled)
  suspend fun updateTacticalHints(enabled: Boolean) = settingsDataStore.setShowTacticalHints(enabled)
  suspend fun updateOneTapBoardFire(enabled: Boolean) = settingsDataStore.setOneTapBoardFire(enabled)
}
