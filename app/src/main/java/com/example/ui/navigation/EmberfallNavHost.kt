package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.ObsidianVoid
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.EmberfallViewModel

@Composable
fun EmberfallRoot(viewModel: EmberfallViewModel) {
  val navController = rememberNavController()
  val lifecycleOwner = LocalLifecycleOwner.current
  val destination by viewModel.destination.collectAsStateWithLifecycle()
  val activeRunSummary by viewModel.activeRunSummary.collectAsStateWithLifecycle()
  val runHistory by viewModel.runHistory.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val runState by viewModel.runState.collectAsStateWithLifecycle()
  val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
  val selectedSwapCoord by viewModel.selectedSwapCoord.collectAsStateWithLifecycle()
  val boardFeedback by viewModel.boardFeedback.collectAsStateWithLifecycle()
  val boardJuiceFx by viewModel.boardJuiceFx.collectAsStateWithLifecycle()
  val invalidSwapFx by viewModel.invalidSwapFx.collectAsStateWithLifecycle()
  val cardCastFx by viewModel.cardCastFx.collectAsStateWithLifecycle()
  val corruptSaveError by viewModel.corruptSaveError.collectAsStateWithLifecycle()
  val soundtrackState by viewModel.soundtrackUiState.collectAsStateWithLifecycle()

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.onAppForeground()
        Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackground()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(destination) {
    val currentRoute = navController.currentDestination?.route
    if (currentRoute != destination.route) {
      // EFB-021: Keep backstack bounded by popping intermediate screens back to Title
      // rather than pushing duplicate entries on every state transition.
      navController.navigate(destination.route) {
        popUpTo(navController.graph.startDestinationId) {
          inclusive = false
        }
        launchSingleTop = true
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 520.dp),
    ) {
      NavHost(
        navController = navController,
        startDestination = AppDestination.Title.route,
      ) {
        composable(AppDestination.Title.route) {
          TitleScreen(
            activeRun = activeRunSummary,
            audioEnabled = settings.effectiveAudioOn,
            soundtrackState = soundtrackState,
            corruptSaveError = corruptSaveError,
            onToggleAudioMute = { viewModel.toggleAudioMute() },
            onOpenSoundtrackStudio = { viewModel.navigateTo(AppDestination.Settings) },
            onDismissCorruptSave = { viewModel.dismissCorruptSaveError() },
            onDiscardCorruptSave = { viewModel.discardCorruptActiveRun() },
            onNewExpedition = { viewModel.navigateTo(AppDestination.ClassSelect) },
            onQuickDive = { viewModel.quickDive() },
            onContinueRun = { viewModel.continueSavedRun() },
            onOpenCompendium = { viewModel.navigateTo(AppDestination.Compendium) },
            onOpenSettings = { viewModel.navigateTo(AppDestination.Settings) },
          )
        }

        composable(AppDestination.ClassSelect.route) {
          BackHandler { viewModel.navigateTo(AppDestination.Title) }
          ClassSelectScreen(
            onBack = { viewModel.navigateTo(AppDestination.Title) },
            onStartRun = { hero, seed, unlitCovenant, dreadLevel ->
              viewModel.startNewRun(hero, seed, unlitCovenant, dreadLevel)
            },
          )
        }

        composable(AppDestination.ActMap.route) {
          BackHandler { viewModel.navigateTo(AppDestination.Title) }
          runState?.let { run ->
            ActMapScreen(
              run = run,
              onSelectNode = { viewModel.enterMapNode(it) },
              onExitToTitle = { viewModel.navigateTo(AppDestination.Title) },
            )
          }
        }

        composable(AppDestination.CombatArena.route) {
          BackHandler { viewModel.navigateTo(AppDestination.Title) }
          runState?.let { run ->
            CombatArenaScreen(
              run = run,
              selectedGroup = selectedGroup,
              selectedSwapCoord = selectedSwapCoord,
              boardFeedback = boardFeedback,
              settings = settings,
              onTapBoardCell = { r, c -> viewModel.onBoardCellTapped(r, c) },
              onSwapBoardCells = { r1, c1, r2, c2 -> viewModel.onBoardSwap(r1, c1, r2, c2) },
              onConfirmSelectedGroup = { viewModel.confirmSelectedGroup() },
              onUseChiselWand = { r, c, hue -> viewModel.useChiselWand(r, c, hue) },
              onTriggerOverdrive = { viewModel.triggerOverdrive() },
              onWitnessIllusion = { viewModel.witnessIllusion() },
              onProceedToCast = { viewModel.proceedToCast() },
              onPlayCard = { uid, hue -> viewModel.playCard(uid, hue) },
              onUseFlask = { viewModel.useFlask() },
              onEndTurn = { viewModel.endTurn() },
              onProceedVictory = { viewModel.proceedFromCombatVictory() },
              onProceedDefeat = { viewModel.viewRunSummaryOnDefeat() },
              onToggleAudioMute = { viewModel.toggleAudioMute() },
              onInvalidSwapDrag = { viewModel.onBoardSwapDragAttempted() },
              onUnlockPassive = { viewModel.unlockPassiveNode(it) },
              onSocketSupport = { id, slot -> viewModel.socketSupport(id, slot) },
              onSkipSupport = { viewModel.skipSupportChoice() },
              onBindArtifact = { viewModel.bindArtifact(it) },
              onSkipArtifact = { viewModel.skipArtifactChoice() },
              onPickRewardCard = { viewModel.pickRewardCard(it) },
              onSkipRewardCard = { viewModel.skipRewardCard() },
              onRerollRewardCards = { viewModel.rerollRewardCards() },
              onClaimRewardRelic = { viewModel.claimRewardRelic() },
              onSkipRewardRelic = { viewModel.skipRewardRelic() },
              onAdvanceInlineToNode = { viewModel.advanceInlineToNode(it) },
              onOpenFullMapFromSpoils = { viewModel.openFullMapFromInlineSpoils() },
              boardJuiceFx = boardJuiceFx,
              invalidSwapFx = invalidSwapFx,
              cardCastFx = cardCastFx,
            )
          }
        }

        composable(AppDestination.Rewards.route) {
          BackHandler { /* Must claim or skip rewards */ }
          runState?.let { run ->
            RewardsScreen(
              run = run,
              onPickCard = { viewModel.pickRewardCard(it) },
              onSkipCard = { viewModel.skipRewardCard() },
              onRerollCards = { viewModel.rerollRewardCards() },
              onClaimRelic = { viewModel.claimRewardRelic() },
              onSkipRelic = { viewModel.skipRewardRelic() },
              onFinishRewards = { viewModel.finishRewards() },
              onSocketSupport = { id, slot -> viewModel.socketSupport(id, slot) },
              onSkipSupport = { viewModel.skipSupportChoice() },
              onBindArtifact = { viewModel.bindArtifact(it) },
              onSkipArtifact = { viewModel.skipArtifactChoice() },
            )
          }
        }

        composable(AppDestination.Event.route) {
          BackHandler { /* Must resolve event before leaving */ }
          runState?.let { run ->
            EventScreen(
              run = run,
              onChooseOption = { viewModel.chooseEventOption(it) },
              onLeave = { viewModel.leaveNodeToMap() },
            )
          }
        }

        composable(AppDestination.Campfire.route) {
          BackHandler { viewModel.leaveNodeToMap() }
          runState?.let { run ->
            CampfireScreen(
              run = run,
              onRest = { viewModel.campfireRest() },
              onUpgradeCard = { viewModel.campfireUpgradeCard(it) },
              onGamble = { viewModel.campfireGamble() },
              onLeave = { viewModel.leaveNodeToMap() },
            )
          }
        }

        composable(AppDestination.Merchant.route) {
          BackHandler { viewModel.leaveNodeToMap() }
          runState?.let { run ->
            MerchantScreen(
              run = run,
              onBuyCard = { viewModel.shopBuyCard(it) },
              onBuyRelic = { viewModel.shopBuyRelic(it) },
              onBuyFlask = { viewModel.shopBuyFlask() },
              onRemoveCard = { viewModel.shopRemoveCard(it) },
              onLeave = { viewModel.leaveNodeToMap() },
            )
          }
        }

        composable(AppDestination.RunSummary.route) {
          BackHandler { viewModel.finishSummaryToTitle() }
          runState?.let { run ->
            RunSummaryScreen(
              run = run,
              onReturnToTitle = { viewModel.finishSummaryToTitle() },
            )
          }
        }

        composable(AppDestination.Compendium.route) {
          BackHandler { viewModel.navigateTo(AppDestination.Title) }
          CompendiumScreen(
            history = runHistory,
            onBack = { viewModel.navigateTo(AppDestination.Title) },
          )
        }

        composable(AppDestination.Settings.route) {
          BackHandler { viewModel.navigateTo(AppDestination.Title) }
          SettingsScreen(
            settings = settings,
            soundtrackState = soundtrackState,
            onToggleMusic = { viewModel.toggleMusic(it) },
            onToggleSfx = { viewModel.toggleSfx(it) },
            onSelectSoundtrackTier = { viewModel.selectStudioModelTier(it) },
            onSelectSoundtrackPreset = { viewModel.selectStudioPreset(it) },
            onUpdateSoundtrackDirection = { viewModel.updateStudioCustomDirection(it) },
            onAuditionSoundtrackMode = { viewModel.auditionSoundtrackMode(it) },
            onComposeSoundtrackSlot = { viewModel.composeSoundtrackSlot(it) },
            onRetryMissingSoundtracks = { viewModel.composeMissingOrRegenerateSoundtracks() },
            onClearSoundtrackCache = { viewModel.clearSoundtrackCache() },
            onToggleColorblind = { viewModel.toggleColorblindSymbols(it) },
            onToggleHints = { viewModel.toggleTacticalHints(it) },
            onToggleFastAnimations = { viewModel.toggleFastAnimations(it) },
            onToggleHaptics = { viewModel.toggleHaptics(it) },
            onToggleOneTap = { viewModel.toggleOneTapBoardFire(it) },
            onBack = { viewModel.navigateTo(AppDestination.Title) },
          )
        }
      }
    }
  }
}
