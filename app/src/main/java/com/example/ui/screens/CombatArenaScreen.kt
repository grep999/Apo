package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayerSettings
import com.example.engine.*
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CombatArenaScreen(
  run: RunState,
  selectedGroup: GroupResolution?,
  boardFeedback: String?,
  settings: PlayerSettings,
  onTapBoardCell: (Int, Int) -> Unit,
  onConfirmSelectedGroup: () -> Unit,
  onUseChiselWand: (Int, Int, Hue) -> Unit,
  onTriggerOverdrive: () -> Unit = {},
  onWitnessIllusion: () -> Unit = {},
  onProceedToCast: () -> Unit,
  onPlayCard: (Long, Hue?) -> Unit,
  onUseFlask: () -> Unit,
  onEndTurn: () -> Unit,
  onProceedVictory: () -> Unit,
  onProceedDefeat: () -> Unit,
  onToggleAudioMute: () -> Unit = {},
  onInvalidSwapDrag: () -> Unit = {},
  onUnlockPassive: (String) -> Unit = {},
  onSocketSupport: (String, Int) -> Unit = {},
  onSkipSupport: () -> Unit = {},
  onBindArtifact: (String) -> Unit = {},
  onSkipArtifact: () -> Unit = {},
  onPickRewardCard: (String) -> Unit = {},
  onSkipRewardCard: () -> Unit = {},
  onRerollRewardCards: () -> Unit = {},
  onClaimRewardRelic: () -> Unit = {},
  onSkipRewardRelic: () -> Unit = {},
  onAdvanceInlineToNode: (String) -> Unit = {},
  onOpenFullMapFromSpoils: () -> Unit = {},
  selectedSwapCoord: BoardCoord? = null,
  onSwapBoardCells: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> },
  boardJuiceFx: BoardJuiceFx? = null,
  invalidSwapFx: InvalidSwapFx? = null,
  cardCastFx: CardCastFx? = null,
) {
  val combat = run.combat ?: return
  val enemy = combat.enemy
  val haptics = LocalHapticFeedback.current
  var showPilesModal by remember { mutableStateOf(false) }
  var showTwistInfoDialog by remember { mutableStateOf(false) }
  var showIllusionInfoDialog by remember { mutableStateOf(false) }
  var showUpayaCircuitDialog by remember { mutableStateOf(false) }
  var showSkillDetailDialog by remember { mutableStateOf(false) }
  var showLedgerDialog by remember { mutableStateOf(false) }
  var inspectedArtifact by remember { mutableStateOf<TantricArtifactDef?>(null) }
  var inspectedIntent by remember { mutableStateOf<Pair<String, EnemyIntent>?>(null) }
  var chiselMode by remember { mutableStateOf(false) }
  var chiselTargetCoord by remember { mutableStateOf<BoardCoord?>(null) }
  var pendingGenericPayCard by remember { mutableStateOf<CardInstance?>(null) }

  val hasChiselWand = "relic_chisel_wand" in run.relics && !combat.player.chiselWandUsedThisCombat
  val isWeavePhase = combat.phase == TurnPhase.WEAVE
  val isCastPhase = combat.phase == TurnPhase.CAST
  val isFogActive = combat.player.fogTapsRemaining > 0
  val showDreadTracker = run.currentAct == 3 &&
    enemy.isBoss &&
    run.heroClass == HeroClass.THE_UNLIT &&
    run.unlitCovenantActive

  // Enemy hit-flash, recoil shake, floating damage counter, & animated HP fraction
  var previousEnemyHp by remember(combat.nodeId) { mutableIntStateOf(enemy.hp) }
  var enemyHitFlash by remember(combat.nodeId) { mutableStateOf(false) }
  var lastEnemyDamageTaken by remember(combat.nodeId) { mutableIntStateOf(0) }
  val enemyShakeX = remember(combat.nodeId) { Animatable(0f) }
  LaunchedEffect(enemy.hp) {
    val delta = previousEnemyHp - enemy.hp
    if (delta > 0) {
      lastEnemyDamageTaken = delta
      enemyHitFlash = true
      val kick = if (delta >= 18) 14f else 8f
      val stepMs = if (settings.fastAnimations) 28 else 42
      enemyShakeX.animateTo(kick, tween(stepMs))
      enemyShakeX.animateTo(-kick * 0.8f, tween(stepMs))
      enemyShakeX.animateTo(kick * 0.4f, tween(stepMs))
      enemyShakeX.animateTo(0f, tween(stepMs))
      delay(if (settings.fastAnimations) 80L else 170L)
      enemyHitFlash = false
    }
    previousEnemyHp = enemy.hp
  }
  val animatedEnemyHpFraction by animateFloatAsState(
    targetValue = (enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f),
    animationSpec = tween(durationMillis = if (settings.fastAnimations) 120 else 260),
    label = "enemy_hp_bar",
  )

  // Board impact micro-shake & hue rim flash on Match-3 pops & cascades
  val boardShakeX = remember(combat.nodeId) { Animatable(0f) }
  val boardRimGlow = remember(combat.nodeId) { Animatable(0f) }
  LaunchedEffect(boardJuiceFx?.id) {
    val fx = boardJuiceFx ?: return@LaunchedEffect
    val kick = when {
      fx.cascadeCount >= 2 || fx.isPrismBlast -> 11f
      fx.totalDamage >= 14 -> 7.5f
      else -> 4.5f
    }
    val stepMs = if (settings.fastAnimations) 22 else 36
    boardRimGlow.snapTo(1f)
    boardShakeX.animateTo(kick, tween(stepMs))
    boardShakeX.animateTo(-kick * 0.75f, tween(stepMs))
    boardShakeX.animateTo(kick * 0.35f, tween(stepMs))
    boardShakeX.animateTo(0f, tween(stepMs))
    boardRimGlow.animateTo(0f, tween(if (settings.fastAnimations) 160 else 320))
  }

  // Physical bump-and-snap-back animation when an invalid swap is attempted
  val invalidBumpProgress = remember(combat.nodeId) { Animatable(0f) }
  LaunchedEffect(invalidSwapFx?.id) {
    if (invalidSwapFx == null) return@LaunchedEffect
    invalidBumpProgress.snapTo(0f)
    invalidBumpProgress.animateTo(1f, tween(if (settings.fastAnimations) 55 else 85))
    invalidBumpProgress.animateTo(0f, tween(if (settings.fastAnimations) 75 else 115))
  }

  // ADR-008 Wall Overflow pre-rise danger telemetry
  val unsealedCols = remember(combat.board) {
    (0 until combat.board.cols).filter { !combat.board.isColumnSealed(it) }
  }
  val overflowImminent = !combat.turn.skipWallRiseThisTurn &&
    unsealedCols.isNotEmpty() &&
    unsealedCols.none { combat.board.grid[0][it].kind is TileKind.Empty }
  val overflowWarning = !combat.turn.skipWallRiseThisTurn &&
    !overflowImminent &&
    unsealedCols.isNotEmpty() &&
    unsealedCols.count { combat.board.grid[0][it].kind is TileKind.Empty } <= 2

  // Pure engine legal Match-3 swap computation: drives playable highlights, drag/click previews, and Valid Swap quick buttons.
  val validSwaps = remember(combat.board, combat.spectrum) {
    BoardEngine.findValidSwaps(combat.board, combat.spectrum)
  }
  val swappableCoords = remember(validSwaps) {
    validSwaps.flatMap { listOf(it.from, it.to) }.toSet()
  }
  val validPartnersForSelected = remember(selectedSwapCoord, combat.board, combat.spectrum.currentHue) {
    val sel = selectedSwapCoord ?: return@remember emptySet<BoardCoord>()
    listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).mapNotNull { (dr, dc) ->
      val nr = sel.row + dr
      val nc = sel.col + dc
      if (nr in 0 until combat.board.rows && nc in 0 until combat.board.cols &&
        BoardEngine.previewSwap(combat.board, sel.row, sel.col, nr, nc, combat.spectrum.currentHue).isValid
      ) {
        BoardCoord(nr, nc)
      } else {
        null
      }
    }.toSet()
  }

  // PC Mouse & Touch gesture state for live drag-to-swap sliding, hover preview, and invalid-swap hint beacon.
  var hoveredCoord by remember(combat.nodeId) { mutableStateOf<BoardCoord?>(null) }
  var dragStartCoord by remember(combat.nodeId) { mutableStateOf<BoardCoord?>(null) }
  var dragTargetCoord by remember(combat.nodeId) { mutableStateOf<BoardCoord?>(null) }
  var dragOffsetPx by remember(combat.nodeId) { mutableStateOf(Offset.Zero) }
  var beaconPulseActive by remember(combat.nodeId) { mutableStateOf(false) }
  var beaconPulseTrigger by remember(combat.nodeId) { mutableIntStateOf(0) }

  LaunchedEffect(beaconPulseTrigger) {
    if (beaconPulseTrigger > 0) {
      beaconPulseActive = true
      delay(1100L)
      beaconPulseActive = false
    }
  }

  // Gentle idle hint beacon if player pauses > 3.8s during WEAVE so pacing never stalls
  LaunchedEffect(combat.turnNumber, combat.tapsRemaining, isWeavePhase, selectedSwapCoord, dragStartCoord) {
    if (isWeavePhase && combat.tapsRemaining > 0 && selectedSwapCoord == null && dragStartCoord == null && validSwaps.isNotEmpty()) {
      delay(3800L)
      beaconPulseActive = true
      delay(1400L)
      beaconPulseActive = false
    }
  }

  val hoveredSwapPreview = remember(
    dragStartCoord,
    dragTargetCoord,
    selectedSwapCoord,
    hoveredCoord,
    combat.board,
    combat.spectrum.currentHue,
    isWeavePhase,
    combat.tapsRemaining,
    chiselMode,
  ) {
    if (!isWeavePhase || combat.tapsRemaining <= 0 || chiselMode) {
      null
    } else {
      val pair: Pair<BoardCoord, BoardCoord>? = when {
        dragStartCoord != null && dragTargetCoord != null -> dragStartCoord!! to dragTargetCoord!!
        selectedSwapCoord != null && hoveredCoord != null &&
          kotlin.math.abs(selectedSwapCoord.row - hoveredCoord!!.row) +
          kotlin.math.abs(selectedSwapCoord.col - hoveredCoord!!.col) == 1 ->
          selectedSwapCoord to hoveredCoord!!
        else -> null
      }
      pair?.let { (a, b) ->
        BoardEngine.previewSwap(combat.board, a.row, a.col, b.row, b.col, combat.spectrum.currentHue)
          .takeIf { it.isValid }
      }
    }
  }
  val beaconSwapCoords = remember(beaconPulseActive, validSwaps) {
    if (beaconPulseActive) {
      validSwaps.firstOrNull()?.let { setOf(it.from, it.to) }.orEmpty()
    } else {
      emptySet()
    }
  }

  val calloutTriggerKey = remember(combat.turnNumber, combat.tapsRemaining, combat.cardsPlayedThisTurn, combat.lastEvents) {
    "${combat.turnNumber}_${combat.tapsRemaining}_${combat.cardsPlayedThisTurn}_${combat.lastEvents.lastOrNull()?.message.orEmpty()}"
  }

  val actGlowColor = when (run.currentAct) {
    1 -> MoltenEmber
    2 -> GildedBrass
    else -> HueViolet
  }
  val overdriveReady = combat.player.overdriveCharge >= PlayerCombatState.MAX_OVERDRIVE &&
    (isWeavePhase || isCastPhase)
  val enemyBark = remember(enemy.defId, enemy.hp, enemy.maxHp, lastEnemyDamageTaken, combat.turnNumber) {
    val hpRatio = enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)
    when {
      enemy.hp <= 0 -> "The crucible claims another vessel..."
      lastEnemyDamageTaken >= 18 -> "\"Ugh—that cascade cracked my shell!\""
      hpRatio <= 0.35f -> if (enemy.isBoss) "\"My kiln will NOT go cold!\"" else "\"Enough! Burn with me!\""
      enemy.isShade -> "\"I remember every card you forged...\""
      enemy.isBoss -> "\"Step into the furnace and prove your hue.\""
      combat.turnNumber == 1 -> "\"Your flame flickers. Show me your best move.\""
      else -> null
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            actGlowColor.copy(alpha = 0.14f),
            ObsidianVoid,
            actGlowColor.copy(alpha = 0.08f),
          )
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Subtle living rising ember motes backdrop (non-intercepting)
    AmbientArenaEmbers(
      accentColor = actGlowColor,
      fastAnimations = settings.fastAnimations,
      modifier = Modifier.fillMaxSize(),
    )

    val totalMaxHeight = maxHeight
    val isCompactHeight = totalMaxHeight < 680.dp
    val sectionGap = if (isCompactHeight) 2.dp else 3.dp
    val handHeight = when {
      totalMaxHeight < 560.dp -> 64.dp
      isCompactHeight -> 70.dp
      else -> 76.dp
    }
    val quickStripHeight = 34.dp

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = if (isCompactHeight) 2.dp else 4.dp),
      verticalArrangement = Arrangement.spacedBy(sectionGap),
    ) {
      // 1. Slim Rival & Wax-Seal Intent Bar (~58-66dp max — Opponent present & crisp without crowding board!)
      val enemyCardBorder by animateColorAsState(
        targetValue = when {
          enemyHitFlash -> CrimsonRuin
          enemy.isShade -> HueViolet
          enemy.isBoss -> GildedBrass.copy(alpha = 0.78f)
          else -> SmokedBorder
        },
        animationSpec = tween(durationMillis = 150),
        label = "enemy_card_border",
      )
      Surface(
        color = CharredBasalt.copy(alpha = 0.96f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(if (enemyHitFlash) 2.dp else 1.dp, enemyCardBorder),
        modifier = Modifier
          .fillMaxWidth()
          .graphicsLayer { translationX = enemyShakeX.value }
          .testTag("combat_enemy_header"),
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (enemyPortraitRes(enemy) != null) {
              EnemyPortraitFrame(
                enemy = enemy,
                hitFlash = enemyHitFlash,
                size = 42.dp,
              )
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = enemy.name,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (enemy.isShade) HueViolet else if (enemy.isBoss) GildedBrass else WarmParchment,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  if (overflowImminent || overflowWarning) {
                    Surface(
                      color = (if (overflowImminent) CrimsonRuin else MoltenEmber).copy(alpha = 0.22f),
                      shape = RoundedCornerShape(5.dp),
                      border = BorderStroke(1.dp, if (overflowImminent) CrimsonRuin else MoltenEmber),
                      modifier = Modifier.testTag("wall_overflow_warning_badge"),
                    ) {
                      Text(
                        text = if (overflowImminent) "OVERFLOW!" else "WALL",
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = if (overflowImminent) CrimsonRuin else MoltenEmber,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                      )
                    }
                  }
                  if (showDreadTracker) {
                    Surface(
                      color = HueGold.copy(alpha = 0.18f),
                      shape = RoundedCornerShape(5.dp),
                      border = BorderStroke(1.dp, HueGold),
                      modifier = Modifier.testTag("dread_streak_badge"),
                    ) {
                      Text(
                        text = "DREAD ${run.dreadConsecutiveAshTurns}/3",
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = HueGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                      )
                    }
                  }
                  if (enemy.block > 0) {
                    Surface(
                      color = SteelShield.copy(alpha = 0.2f),
                      shape = RoundedCornerShape(5.dp),
                      border = BorderStroke(1.dp, SteelShield),
                    ) {
                      Text(
                        text = "B${enemy.block}",
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = SteelShield,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                      )
                    }
                  }
                  Text(
                    text = "${enemy.hp}/${enemy.maxHp}",
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontFamily = IbmPlexMonoFamily,
                    color = if (enemyHitFlash) WarmParchment else CrimsonRuin,
                    fontWeight = FontWeight.ExtraBold,
                  )
                }
              }

              LinearProgressIndicator(
                progress = { animatedEnemyHpFraction },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(4.dp)
                  .clip(RoundedCornerShape(2.dp)),
                color = if (enemyHitFlash) WarmParchment else if (enemy.isShade) HueViolet else CrimsonRuin,
                trackColor = SlateStone,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "T${combat.turnNumber} • ${combat.phase.name}" +
                    (if (enemy.shadeVigorBonus > 0) " • +${enemy.shadeVigorBonus}V" else "") +
                    if (combat.board.sealedColumns.isNotEmpty()) {
                      " • S:${combat.board.sealedColumns.keys.sorted().joinToString(",") { "C${it + 1}" }}"
                    } else "",
                  fontSize = 9.sp,
                  lineHeight = 10.sp,
                  fontFamily = IbmPlexMonoFamily,
                  color = if (isWeavePhase) MoltenEmber else GildedBrass,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (combat.arenaTwist != ArenaTwist.NONE) {
                  Surface(
                    onClick = { showTwistInfoDialog = true },
                    color = GildedBrass.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, GildedBrass.copy(alpha = 0.72f)),
                    modifier = Modifier.testTag("arena_twist_badge"),
                  ) {
                    Text(
                      text = "★ ${combat.arenaTwist.shortTag}",
                      fontSize = 8.sp,
                      lineHeight = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = GildedBrass,
                      maxLines = 1,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                  }
                }
              }
            }

            // Inline dual scalloped Wax-Seal Intent badges (tap either for full telegraph explanation)
            Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CompactWaxSealBadge(
                intent = enemy.slot1Intent,
                slotLabel = "SEAL I",
                onClick = { inspectedIntent = "SEAL I (Active Next)" to enemy.slot1Intent },
              )
              CompactWaxSealBadge(
                intent = enemy.slot2Intent,
                slotLabel = "SEAL II",
                onClick = { inspectedIntent = "SEAL II (On Deck)" to enemy.slot2Intent },
              )
            }
          }

          CardCastSlashOverlay(
            fx = cardCastFx,
            fastAnimations = settings.fastAnimations,
            modifier = Modifier.matchParentSize(),
          )
          if (enemyHitFlash && lastEnemyDamageTaken > 0) {
            Surface(
              color = CrimsonRuin,
              shape = RoundedCornerShape(999.dp),
              border = BorderStroke(1.5.dp, WarmParchment),
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 6.dp),
            ) {
              Text(
                text = "-$lastEnemyDamageTaken",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = WarmParchment,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
              )
            }
          }
        }
      }

      // 2. Compact Player Vitals, Spectrum Wheel Dial & 5 Colored Ink Pools (~48dp)
      Surface(
        color = CharredBasalt.copy(alpha = 0.94f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isFogActive) HueTide else SmokedBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SpectrumWheelDial(
            spectrum = combat.spectrum,
            isFogActive = isFogActive,
            dialSize = 38.dp,
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CompactStatChip("HP", "${run.hp}/${run.maxHp}", CrimsonRuin)
              CompactStatChip("GRD", "${combat.player.guardBlock}", SteelShield)
              CompactStatChip("VGL", "${combat.player.vigil}", GildedBrass)
              if (isFogActive) {
                CompactStatChip("FOG", "${combat.player.fogTapsRemaining}T", HueTide)
              }
              CompactStatChip("MV", "${combat.tapsRemaining}/3", MoltenEmber)
              CompactStatChip("SPK", "${combat.resources.spark}", WarmParchment)
              Surface(
                onClick = { showPilesModal = true },
                color = SlateStone,
                shape = RoundedCornerShape(5.dp),
                border = BorderStroke(1.dp, GildedBrass.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("combat_piles_button"),
              ) {
                Text(
                  text = "D:${combat.combatDeck.drawPile.size}•X:${combat.combatDeck.discardPile.size}",
                  fontSize = 8.sp,
                  lineHeight = 9.sp,
                  fontFamily = IbmPlexMonoFamily,
                  color = GildedBrass,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val nextExpected = combat.spectrum.currentHue?.let { Hue.nextInWheel(it) }
              Hue.WHEEL_ORDER.forEach { hue ->
                val inkCount = combat.resources.inkOf(hue)
                val isCurrent = combat.spectrum.currentHue == hue
                val isNextWheel = nextExpected == hue
                val chipAccent = if (isFogActive) MutedAsh else hueColor(hue)
                Surface(
                  color = if (isFogActive) FogVeilSlate else hueColor(hue).copy(alpha = if (inkCount > 0) 0.26f else 0.09f),
                  shape = RoundedCornerShape(5.dp),
                  border = BorderStroke(
                    width = if (!isFogActive && isNextWheel) 1.4.dp else 0.9.dp,
                    color = when {
                      isFogActive -> SmokedBorder
                      isNextWheel -> WarmParchment
                      isCurrent -> GildedBrass
                      else -> hueColor(hue).copy(alpha = 0.55f)
                    },
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("ink_pool_${hue.name.lowercase()}"),
                ) {
                  Box(
                    modifier = Modifier.padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = if (isFogActive) "${hue.shortTag} ?" else "${hue.shortTag} $inkCount",
                      fontSize = 9.sp,
                      lineHeight = 10.sp,
                      fontFamily = IbmPlexMonoFamily,
                      color = chipAccent,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 3. Unified Single-Row Interactive System Pill Ribbon (~38dp: Mind Illusion + Signature Skill + Upāya/Relics + Multiplier)
      CombatSystemPillRibbon(
        heroClass = run.heroClass,
        growth = run.growth,
        mind = combat.mindIllusion,
        charge = combat.player.overdriveCharge,
        turn = combat.turn,
        breakdown = combat.lastScoringBreakdown,
        canAct = isWeavePhase || isCastPhase,
        onWitness = {
          if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onWitnessIllusion()
        },
        onInspectIllusion = { showIllusionInfoDialog = true },
        onUnleashSkill = {
          if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onTriggerOverdrive()
        },
        onInspectSkill = { showSkillDetailDialog = true },
        onOpenCircuit = { showUpayaCircuitDialog = true },
        onInspectLedger = { showLedgerDialog = true },
      )

      // 4. Non-Scrolling 7x7 Elemental Board + Live Drag-to-Swap + ADR-008 Incoming Row Preview
      val latestCombat by rememberUpdatedState(combat)
      val latestChiselMode by rememberUpdatedState(chiselMode)
      val latestSettings by rememberUpdatedState(settings)
      val latestOnTapBoardCell by rememberUpdatedState(onTapBoardCell)
      val latestOnSwapBoardCells by rememberUpdatedState(onSwapBoardCells)
      val latestOnInvalidSwapDrag by rememberUpdatedState(onInvalidSwapDrag)

      fun fireTileAction(r: Int, c: Int) {
        val currentCombat = latestCombat
        val tile = currentCombat.board.tileAt(r, c) ?: return
        if (latestChiselMode) {
          if (!currentCombat.board.isColumnSealed(c) && tile.kind !is TileKind.Empty) {
            chiselTargetCoord = BoardCoord(r, c)
          }
        } else {
          if (latestSettings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          }
          latestOnTapBoardCell(r, c)
        }
      }

      fun fireSwapAction(from: BoardCoord, to: BoardCoord) {
        val currentCombat = latestCombat
        if (latestChiselMode) {
          fireTileAction(from.row, from.col)
          return
        }
        val preview = BoardEngine.previewSwap(
          currentCombat.board,
          from.row,
          from.col,
          to.row,
          to.col,
          currentCombat.spectrum.currentHue,
        )
        if (preview.isValid) {
          if (latestSettings.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          }
          latestOnSwapBoardCells(from.row, from.col, to.row, to.col)
        } else {
          beaconPulseTrigger++
          latestOnSwapBoardCells(from.row, from.col, to.row, to.col)
          latestOnInvalidSwapDrag()
        }
      }

      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val boardWidth = minOf(maxWidth, (maxHeight - 18.dp).coerceAtLeast(112.dp))
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          val fxRimColor = boardJuiceFx?.primaryHue?.let { hueColor(it) } ?: GildedBrass
          Surface(
            color = CharredBasalt,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
              width = if (overflowImminent || boardRimGlow.value > 0.1f) 2.2.dp else 1.5.dp,
              color = when {
                boardRimGlow.value > 0.1f -> fxRimColor
                overflowImminent -> CrimsonRuin
                chiselMode -> HueTide
                isFogActive -> HueTide
                overflowWarning -> MoltenEmber
                isWeavePhase && combat.tapsRemaining > 0 -> MoltenEmber.copy(alpha = 0.65f)
                else -> SmokedBorder
              },
            ),
            modifier = Modifier
              .size(boardWidth)
              .graphicsLayer { translationX = boardShakeX.value }
              .testTag("combat_board_grid"),
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(4.dp)
                  .pointerHoverIcon(
                    if (hoveredCoord != null && (chiselMode || (isWeavePhase && combat.tapsRemaining > 0))) {
                      PointerIcon.Hand
                    } else {
                      PointerIcon.Default
                    }
                  )
                .pointerInput(Unit) {
                  var activePressCoord: BoardCoord? = null
                  var activePressPos = Offset.Zero
                  var dragCommitted = false

                  awaitPointerEventScope {
                    while (true) {
                      val event = awaitPointerEvent()
                      val change = event.changes.firstOrNull() ?: continue
                      val pos = change.position
                      val rows = latestCombat.board.rows
                      val cols = latestCombat.board.cols
                      val boardCoord = mapOffsetToBoardCoord(pos, size, rows, cols)
                      val cellPx = (minOf(size.width, size.height) / cols.coerceAtLeast(1).toFloat())
                        .coerceAtLeast(1f)

                      when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> {
                          val start = activePressCoord
                          if (start != null && !dragCommitted && !latestChiselMode) {
                            val delta = pos - activePressPos
                            val absX = kotlin.math.abs(delta.x)
                            val absY = kotlin.math.abs(delta.y)
                            val maxAxis = maxOf(absX, absY)
                            if (maxAxis >= cellPx * 0.12f) {
                              val (dr, dc) = if (absX >= absY) {
                                0 to (if (delta.x >= 0f) 1 else -1)
                              } else {
                                (if (delta.y >= 0f) 1 else -1) to 0
                              }
                              val nr = start.row + dr
                              val nc = start.col + dc
                              if (nr in 0 until rows && nc in 0 until cols) {
                                val target = BoardCoord(nr, nc)
                                val clamped = cellPx * 0.85f
                                dragTargetCoord = target
                                dragOffsetPx = if (absX >= absY) {
                                  Offset(delta.x.coerceIn(-clamped, clamped), 0f)
                                } else {
                                  Offset(0f, delta.y.coerceIn(-clamped, clamped))
                                }
                                change.consume()
                                // Snappy commit at 28% across neighbor tile for ultra-responsive mouse & touch!
                                if (maxAxis >= cellPx * 0.28f) {
                                  dragCommitted = true
                                  activePressCoord = null
                                  dragStartCoord = null
                                  dragTargetCoord = null
                                  dragOffsetPx = Offset.Zero
                                  fireSwapAction(start, target)
                                }
                              }
                            } else {
                              dragTargetCoord = null
                              dragOffsetPx = Offset.Zero
                            }
                          } else {
                            hoveredCoord = boardCoord
                          }
                        }

                        PointerEventType.Exit -> {
                          hoveredCoord = null
                        }

                        PointerEventType.Press -> {
                          if (boardCoord != null) {
                            val tile = latestCombat.board.tileAt(boardCoord.row, boardCoord.col)
                            val canDrag = !latestChiselMode &&
                              latestCombat.phase == TurnPhase.WEAVE &&
                              latestCombat.tapsRemaining > 0 &&
                              !latestCombat.board.isColumnSealed(boardCoord.col) &&
                              tile?.kind !is TileKind.Empty &&
                              tile?.kind !is TileKind.Stone
                            activePressCoord = boardCoord
                            activePressPos = pos
                            dragCommitted = false
                            dragStartCoord = if (canDrag) boardCoord else null
                            dragTargetCoord = null
                            dragOffsetPx = Offset.Zero
                            hoveredCoord = boardCoord
                            change.consume()
                          }
                        }

                        PointerEventType.Release -> {
                          val start = activePressCoord
                          val startPos = activePressPos
                          val alreadyCommitted = dragCommitted
                          activePressCoord = null
                          dragCommitted = false
                          dragStartCoord = null
                          dragTargetCoord = null
                          dragOffsetPx = Offset.Zero

                          if (start != null && !alreadyCommitted) {
                            change.consume()
                            val delta = pos - startPos
                            val absX = kotlin.math.abs(delta.x)
                            val absY = kotlin.math.abs(delta.y)
                            val dragDist = delta.getDistance()

                            when {
                              boardCoord != null &&
                                !latestChiselMode &&
                                kotlin.math.abs(start.row - boardCoord.row) + kotlin.math.abs(start.col - boardCoord.col) == 1 -> {
                                fireSwapAction(start, boardCoord)
                              }
                              dragDist >= cellPx * 0.36f && !latestChiselMode -> {
                                val (dr, dc) = if (absX >= absY) {
                                  0 to (if (delta.x >= 0f) 1 else -1)
                                } else {
                                  (if (delta.y >= 0f) 1 else -1) to 0
                                }
                                val nr = start.row + dr
                                val nc = start.col + dc
                                if (nr in 0 until rows && nc in 0 until cols) {
                                  fireSwapAction(start, BoardCoord(nr, nc))
                                }
                              }
                              else -> {
                                // Crisp click/tap -> Two-Click Swap (or Chisel target)!
                                fireTileAction(start.row, start.col)
                              }
                            }
                          }
                        }

                        else -> Unit
                      }
                    }
                  }
                },
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              for (r in 0 until combat.board.rows) {
                Row(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  for (c in 0 until combat.board.cols) {
                    val tile = combat.board.grid[r][c]
                    val coord = BoardCoord(r, c)
                    val sealedTurns = combat.board.sealedColumns[c] ?: 0
                    val isSelected = selectedSwapCoord == coord || dragStartCoord == coord
                    val isPartnerHighlight = coord in validPartnersForSelected || dragTargetCoord == coord
                    val isHighlighted = isSelected || isPartnerHighlight ||
                      (selectedGroup != null && coord in selectedGroup.coords)
                    val isBeaconHint = coord in beaconSwapCoords
                    val isPlayableNow = (chiselMode && sealedTurns == 0 && tile.kind !is TileKind.Empty) ||
                      (isWeavePhase && combat.tapsRemaining > 0 && coord in swappableCoords)
                    val invalidBumpOffset = if (invalidSwapFx != null && invalidBumpProgress.value > 0.01f) {
                      val bumpDist = 18f * invalidBumpProgress.value
                      when (coord) {
                        invalidSwapFx.from -> Offset(
                          (invalidSwapFx.to.col - invalidSwapFx.from.col) * bumpDist,
                          (invalidSwapFx.to.row - invalidSwapFx.from.row) * bumpDist,
                        )
                        invalidSwapFx.to -> Offset(
                          (invalidSwapFx.from.col - invalidSwapFx.to.col) * bumpDist,
                          (invalidSwapFx.from.row - invalidSwapFx.to.row) * bumpDist,
                        )
                        else -> Offset.Zero
                      }
                    } else {
                      Offset.Zero
                    }
                    val tileDragTranslation = when (coord) {
                      dragStartCoord -> dragOffsetPx
                      dragTargetCoord -> Offset(-dragOffsetPx.x, -dragOffsetPx.y)
                      else -> invalidBumpOffset
                    }
                    val tileZ = when (coord) {
                      dragStartCoord -> 3f
                      dragTargetCoord -> 2f
                      invalidSwapFx?.from, invalidSwapFx?.to -> if (invalidBumpProgress.value > 0.01f) 2f else 0f
                      else -> 0f
                    }

                    BoardTileCell(
                      tile = tile,
                      isHighlighted = isHighlighted,
                      isSelected = isSelected,
                      isPartnerHint = isPartnerHighlight,
                      isBeaconHint = isBeaconHint,
                      isInvalidFlash = invalidBumpProgress.value > 0.05f &&
                        (coord == invalidSwapFx?.from || coord == invalidSwapFx?.to),
                      isPlayable = isPlayableNow,
                      isFogged = isFogActive,
                      fastAnimations = settings.fastAnimations,
                      sealedTurns = sealedTurns,
                      showSealBanner = r == 0 && sealedTurns > 0,
                      showGlyph = true,
                      strongGlyph = isFogActive || settings.colorblindSymbols || tile.kind.isNeutral,
                      dragTranslation = tileDragTranslation,
                      onAccessibilityClick = { fireTileAction(r, c) },
                      modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .zIndex(tileZ)
                        .testTag("board_cell_${r}_${c}"),
                    )
                  }
                }
              }
            }
              // 60fps Block Disappearance VFX Overlay: flashes, match beams, shatter shards, shockwaves & homing orbs!
              BoardJuiceFxOverlay(
                fx = boardJuiceFx,
                rows = combat.board.rows,
                cols = combat.board.cols,
                fastAnimations = settings.fastAnimations,
                modifier = Modifier
                  .matchParentSize()
                  .padding(4.dp),
              )
            }
          }

          // ADR-008 Incoming Row Preview Strip
          Row(
            modifier = Modifier
              .width(boardWidth)
              .height(14.dp)
              .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            combat.board.incomingRow.forEachIndexed { colIdx, tile ->
              val colSealed = combat.board.isColumnSealed(colIdx)
              val bg = when {
                isFogActive && tile.kind is TileKind.Playable -> FogVeilSlate
                else -> tileColor(tile.kind).copy(alpha = 0.55f)
              }
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(3.dp))
                  .background(bg)
                  .border(
                    1.dp,
                    if (colSealed) HueViolet else SmokedBorder,
                    RoundedCornerShape(3.dp),
                  ),
                contentAlignment = Alignment.Center,
              ) {
                if (isFogActive || tile.kind.isNeutral || settings.colorblindSymbols) {
                  Text(
                    text = tileGlyph(tile.kind),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = WarmParchment.copy(alpha = 0.8f),
                  )
                }
              }
            }
          }
        }

        // Non-intercepting floating combat feedback badges above the board
        FloatingCombatCallouts(
          events = combat.lastEvents,
          eventTriggerKey = calloutTriggerKey,
          fastAnimations = settings.fastAnimations,
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 6.dp),
        )

        // Zero-layout-cost floating status overlay (only renders for active swap preview, feedback, or chisel mode!)
        val hasTransientBanner = chiselMode ||
          boardFeedback != null ||
          (hoveredSwapPreview != null && hoveredSwapPreview.isValid && isWeavePhase) ||
          (selectedSwapCoord != null && isWeavePhase) ||
          (selectedGroup != null && selectedGroup.isValid && isWeavePhase)
        if (hasTransientBanner) {
          Box(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .zIndex(5f),
          ) {
            CombatStatusBanner(
              combat = combat,
              chiselMode = chiselMode,
              boardFeedback = boardFeedback,
              selectedSwapCoord = selectedSwapCoord,
              hoveredSwapPreview = hoveredSwapPreview,
              selectedGroup = selectedGroup,
              isWeavePhase = isWeavePhase,
              isCastPhase = isCastPhase,
              isFogActive = isFogActive,
              showTacticalHints = settings.showTacticalHints,
              onCancelChisel = { chiselMode = false },
              onConfirmGroup = {
                if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirmSelectedGroup()
              },
            )
          }
        }
      }

      // 5. Valid Match-3 Swaps Quick-Action Strip (100% engine-backed one-click swap alternative)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(quickStripHeight),
        contentAlignment = Alignment.CenterStart,
      ) {
        when {
          isWeavePhase && combat.tapsRemaining > 0 && validSwaps.isNotEmpty() -> {
            LazyRow(
              modifier = Modifier
                .fillMaxSize()
                .testTag("valid_swaps_strip"),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              itemsIndexed(validSwaps) { index, option ->
                val isSelectedPair = selectedSwapCoord == option.from || selectedSwapCoord == option.to
                ValidSwapChip(
                  option = option,
                  isFogActive = isFogActive,
                  isSelected = isSelectedPair,
                  onClick = {
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSwapBoardCells(option.from.row, option.from.col, option.to.row, option.to.col)
                  },
                  modifier = Modifier
                    .fillMaxHeight()
                    .testTag("valid_swap_chip_$index"),
                )
              }
            }
          }
          isWeavePhase && combat.tapsRemaining <= 0 -> {
            Button(
              onClick = onProceedToCast,
              modifier = Modifier
                .fillMaxSize()
                .testTag("zero_taps_cast_prompt"),
              colors = ButtonDefaults.buttonColors(
                containerColor = GildedBrass.copy(alpha = 0.22f),
                contentColor = GildedBrass,
              ),
              border = BorderStroke(1.dp, GildedBrass),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                text = "0 Moves Left — Tap Here to Enter CAST Phase & Play Cards",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          else -> {
            Surface(
              color = CharredBasalt,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, if (isCastPhase) GildedBrass.copy(alpha = 0.5f) else SmokedBorder),
              modifier = Modifier.fillMaxSize(),
            ) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
              ) {
                Text(
                  text = if (isCastPhase) {
                    "CAST Phase — Tap Ink cards below to cast, or press End Turn (Wall rises next)."
                  } else {
                    combat.lastEvents.lastOrNull()?.message ?: "Resolving turn..."
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isCastPhase) GildedBrass else MutedAsh,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // 6. Hand Cards — Instant 1-tap casting in CAST or directly from WEAVE when affordable!
      val hasAffordableHandCard = remember(combat.combatDeck.hand, combat.resources, combat.player.vigil) {
        combat.combatDeck.hand.any { inst ->
          val def = GameCatalog.cardById(inst.defId)
          GameEngine.tryPayCardCost(combat.resources, def, inst.upgraded) != null &&
            combat.player.vigil >= def.vigilCost
        }
      }
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .height(handHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(combat.combatDeck.hand, key = { it.uid }) { cardInst ->
          val def = GameCatalog.cardById(cardInst.defId)
          CardPillView(
            cardDef = def,
            upgraded = cardInst.upgraded,
            resources = combat.resources,
            vigilAvailable = combat.player.vigil,
            enabled = isCastPhase || isWeavePhase,
            onClick = {
              if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              val effectiveGeneric = (def.genericCost - if (cardInst.upgraded) def.upgradedGenericDiscount else 0).coerceAtLeast(0)
              if (effectiveGeneric > 0) {
                val sources = availableGenericPaySources(combat.resources, def)
                if (sources.size > 1) {
                  pendingGenericPayCard = cardInst
                } else {
                  onPlayCard(cardInst.uid, sources.firstOrNull())
                }
              } else {
                onPlayCard(cardInst.uid, null)
              }
            },
            modifier = Modifier
              .width(136.dp)
              .fillMaxHeight()
              .testTag("hand_card_${cardInst.uid}"),
          )
        }
      }

      // 7. Pinned Phase & Utility Action Row (Audio Mute, Flask, Chisel Wand, Go to CAST / End Turn)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val audioOn = settings.effectiveAudioOn
        OutlinedIconButton(
          onClick = onToggleAudioMute,
          modifier = Modifier
            .size(48.dp)
            .testTag("combat_audio_toggle"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, if (audioOn) GildedBrass.copy(alpha = 0.65f) else SmokedBorder),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (audioOn) CharredBasalt else ObsidianVoid,
            contentColor = if (audioOn) GildedBrass else MutedAsh,
          ),
        ) {
          Icon(
            imageVector = if (audioOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = if (audioOn) "Mute minimal techno audio" else "Unmute minimal techno audio",
            modifier = Modifier.size(20.dp),
          )
        }

        OutlinedButton(
          onClick = onUseFlask,
          enabled = (isWeavePhase || isCastPhase) && run.flasks > 0,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("use_flask_button"),
          border = BorderStroke(
            1.5.dp,
            if (run.flasks <= 1) CrimsonRuin else VerdantHeal,
          ),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
          Icon(
            Icons.Default.LocalDrink,
            contentDescription = null,
            tint = if (run.flasks <= 1) CrimsonRuin else VerdantHeal,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = if (run.flasks == 1) "Flask (1!)" else "Flask (${run.flasks}/${run.maxFlasks})",
            style = MaterialTheme.typography.labelMedium,
            color = if (run.flasks <= 1) CrimsonRuin else VerdantHeal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (hasChiselWand) {
          OutlinedButton(
            onClick = { chiselMode = !chiselMode },
            enabled = isWeavePhase || isCastPhase,
            modifier = Modifier
              .height(48.dp)
              .testTag("chisel_wand_button"),
            border = BorderStroke(1.5.dp, HueTide),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = "Chisel Wand", tint = HueTide, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text("Chisel", style = MaterialTheme.typography.labelMedium, color = HueTide, maxLines = 1)
          }
        }

        if (isWeavePhase) {
          Button(
            onClick = onProceedToCast,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("proceed_cast_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (combat.tapsRemaining == 0) MoltenEmber else GildedBrass,
              contentColor = ObsidianVoid,
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Text(
              text = if (combat.tapsRemaining == 0) "CAST Cards" else "Go to CAST",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        } else {
          Button(
            onClick = onEndTurn,
            enabled = isCastPhase,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("end_turn_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = if (hasAffordableHandCard) GildedBrass else MoltenEmber,
              contentColor = ObsidianVoid,
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Text(
              text = if (hasAffordableHandCard) "Skip Cards • End" else "End Turn →",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // Combat Resolution Overlay: Inline Spoils, Growth & Single-Tap Next-Room Flow!
    if (combat.phase == TurnPhase.COMBAT_WON && !run.runWon && run.pendingReward != null) {
      InlineSpoilsAndGrowthOverlay(
        run = run,
        onUnlockPassive = onUnlockPassive,
        onSocketSupport = onSocketSupport,
        onSkipSupport = onSkipSupport,
        onBindArtifact = onBindArtifact,
        onSkipArtifact = onSkipArtifact,
        onClaimRelic = onClaimRewardRelic,
        onSkipRelic = onSkipRewardRelic,
        onPickCard = onPickRewardCard,
        onSkipCard = onSkipRewardCard,
        onRerollCards = onRerollRewardCards,
        onAdvanceToNode = onAdvanceInlineToNode,
        onOpenFullMap = onOpenFullMapFromSpoils,
        onOpenLegacyRewards = onProceedVictory,
      )
    } else if (combat.phase == TurnPhase.COMBAT_WON || combat.phase == TurnPhase.GAME_OVER) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(ObsidianVoid.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          color = CharredBasalt,
          shape = SkillCardShape,
          border = BorderStroke(
            2.dp,
            if (combat.phase == TurnPhase.COMBAT_WON) GildedBrass else CrimsonRuin,
          ),
          modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .copperplateEngraving(accent = if (combat.phase == TurnPhase.COMBAT_WON) GildedBrass else CrimsonRuin),
        ) {
          Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = if (combat.phase == TurnPhase.COMBAT_WON) "CITADEL ASCENDED" else (run.gameOverReason?.label ?: "DEFEAT"),
              style = MaterialTheme.typography.headlineMedium,
              color = if (combat.phase == TurnPhase.COMBAT_WON) GildedBrass else CrimsonRuin,
            )
            Spacer(Modifier.height(8.dp))
            Text(
              text = if (combat.phase == TurnPhase.COMBAT_WON) {
                "${enemy.name} has dissolved into Prakāśa light! Your Sādhana reaches completion."
              } else if (run.gameOverReason == GameOverReason.ZERO_FLASKS) {
                "Your final Ember Flask shattered. Without alchemical fire, the expedition collapses."
              } else {
                "Your vessel succumbed to ${enemy.name}."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = WarmParchment,
            )
            Spacer(Modifier.height(18.dp))
            Button(
              onClick = {
                if (combat.phase == TurnPhase.COMBAT_WON) onProceedVictory() else onProceedDefeat()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("combat_outcome_continue"),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (combat.phase == TurnPhase.COMBAT_WON) VermilionWax else CrimsonRuin,
                contentColor = WarmParchment,
              ),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(
                text = if (combat.phase == TurnPhase.COMBAT_WON) "View Ascension Summary →" else "View Run Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }

  if (showIllusionInfoDialog) {
    val mind = combat.mindIllusion
    val ill = mind.illusion
    AlertDialog(
      onDismissRequest = { showIllusionInfoDialog = false },
      containerColor = CharredBasalt,
      title = {
        Text(
          text = "${ill.veilTitle} (${ill.sanskritName})",
          color = if (mind.shattered) GildedBrass else VermilionHot,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${ill.projectionLabel} • Fixated Emotion: ${ill.hue.displayName} (${ill.emotionName})",
            style = MaterialTheme.typography.labelMedium,
            color = GildedBrass,
          )
          Text(
            text = "Attachment Trap: ${ill.attachmentTrap}",
            style = MaterialTheme.typography.bodyMedium,
            color = WarmParchment,
          )
          Text(
            text = "Path of Non-Attachment: ${ill.liberationInsight}",
            style = MaterialTheme.typography.bodyMedium,
            color = WarmParchment,
          )
          HorizontalDivider(color = KilnBrass.copy(alpha = 0.45f))
          Text(
            text = "• At 6+ Attachment (Māyā Grip), enemy blows resonate for +25% damage.\n• At 10 Attachment (Vikalpa Collapse), clinging recoils for 4 HP & spawns 1 Ash mist.\n• Peel all 3 Illusion Seals via Sākṣī Witness or Non-Attached Wheel Flow to trigger PRATYABHIJÑĀ: shatter enemy Block, deal Clarity damage, gain +1.0× permanent Vimarśa, and earn +25 bonus Sādhana XP!",
            style = MaterialTheme.typography.bodySmall,
            color = MutedAsh,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showIllusionInfoDialog = false }) {
          Text("Return to Stillness", color = GildedBrass)
        }
      },
    )
  }

  if (showUpayaCircuitDialog) {
    UpayaCircuitDialog(
      growth = run.growth,
      onUnlockPassive = onUnlockPassive,
      onDismiss = { showUpayaCircuitDialog = false },
    )
  }

  if (showSkillDetailDialog) {
    val skillDef = ProgressionCatalog.signatureSkillFor(run.heroClass)
    AlertDialog(
      onDismissRequest = { showSkillDetailDialog = false },
      containerColor = CharredBasalt,
      title = {
        Text(
          text = "${skillDef.name} (${run.growth.socketedSupportIds.size}/${run.growth.maxSockets} Links)",
          color = GildedBrass,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = skillDef.sanskritName,
            style = MaterialTheme.typography.labelMedium,
            color = VermilionHot,
          )
          Text(
            text = "Humble Base: ${skillDef.baseHeat} Spanda • ${skillDef.baseFlavor}",
            style = MaterialTheme.typography.bodyMedium,
            color = WarmParchment,
          )
          HorizontalDivider(color = KilnBrass.copy(alpha = 0.4f))
          Text("Socketed Dhāraṇā Supports", style = MaterialTheme.typography.titleSmall, color = GildedBrass)
          if (run.growth.socketedSupportIds.isEmpty()) {
            Text(
              text = "No Dhāraṇā Supports socketed yet. Conquer chambers to draft and link Supports that transform this skill!",
              style = MaterialTheme.typography.bodySmall,
              color = MutedAsh,
            )
          } else {
            run.growth.socketedSupportIds.forEach { supId ->
              val sup = ProgressionCatalog.supportByIdOrNull(supId) ?: return@forEach
              Text(
                text = "• ${sup.name} (${sup.shortTag}): ${sup.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = WarmParchment,
              )
            }
          }
          Text(
            text = "Sockets unlock at Lv.1 (1), Lv.3 (2), and Lv.6 (3). Current Level: ${run.growth.level} (${run.growth.stageTitle()}).",
            style = MaterialTheme.typography.labelSmall,
            color = KilnBrass,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showSkillDetailDialog = false }) {
          Text("Close", color = GildedBrass)
        }
      },
    )
  }

  if (showLedgerDialog) {
    AlertDialog(
      onDismissRequest = { showLedgerDialog = false },
      containerColor = CharredBasalt,
      title = { Text("Prakāśa Kiln Ledger & Artifacts", color = GildedBrass) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SpandaVimarsaLedgerHud(
            turn = combat.turn,
            breakdown = combat.lastScoringBreakdown,
          )
          if (run.growth.tantricArtifactIds.isNotEmpty()) {
            HorizontalDivider(color = KilnBrass.copy(alpha = 0.4f))
            Text("Bound Shaiva Tantric Items", style = MaterialTheme.typography.titleSmall, color = GildedBrass)
            run.growth.tantricArtifactIds.forEach { artId ->
              val art = ProgressionCatalog.artifactByIdOrNull(artId) ?: return@forEach
              Text(
                text = "• ${art.glyph} ${art.name}: ${art.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = WarmParchment,
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showLedgerDialog = false }) {
          Text("Close", color = GildedBrass)
        }
      },
    )
  }

  inspectedArtifact?.let { art ->
    AlertDialog(
      onDismissRequest = { inspectedArtifact = null },
      containerColor = CharredBasalt,
      title = { Text("${art.glyph} ${art.name}", color = GildedBrass) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(art.sanskritTitle, style = MaterialTheme.typography.labelMedium, color = VermilionHot)
          Text(art.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
          Text("\"${art.loreQuote}\"", style = MaterialTheme.typography.bodySmall, color = MutedAsh)
        }
      },
      confirmButton = {
        TextButton(onClick = { inspectedArtifact = null }) {
          Text("Close", color = GildedBrass)
        }
      },
    )
  }

  inspectedIntent?.let { (slotLabel, intent) ->
    val detailText = when (intent) {
      is EnemyIntent.Attack -> if (intent.hits > 1) {
        "Deals ${intent.damage} damage ${intent.hits} times at end of turn."
      } else {
        "Deals ${intent.damage} damage at end of turn."
      }
      is EnemyIntent.Defend -> "Grants enemy +${intent.block} Block."
      is EnemyIntent.CorruptBoard -> "Corrupts ${intent.count} playable tiles into ${if (intent.kind is TileKind.Ash) "Ash" else "Stone"}."
      is EnemyIntent.SiphonFlask -> "Deals ${intent.damage} damage and drains ${intent.flaskDrain} Ember Flask!"
      is EnemyIntent.InflictBrand -> "Brands ${intent.count} tiles (-2 Vigil on pop)" + if (intent.damage > 0) " and deals ${intent.damage} damage." else "."
      is EnemyIntent.InflictFog -> "Shrouds board hues and ink pools in Fog for ${intent.taps} moves" + if (intent.damage > 0) " and deals ${intent.damage} damage." else "."
      is EnemyIntent.SealColumn -> "Seals 1 board column for ${intent.turns} turns" + if (intent.damage > 0) " and deals ${intent.damage} damage." else "."
    }
    AlertDialog(
      onDismissRequest = { inspectedIntent = null },
      containerColor = CharredBasalt,
      title = { Text("Wax Seal Telegraph • $slotLabel", color = VermilionHot) },
      text = { Text(detailText, style = MaterialTheme.typography.bodyLarge, color = WarmParchment) },
      confirmButton = {
        TextButton(onClick = { inspectedIntent = null }) {
          Text("Understood", color = GildedBrass)
        }
      },
    )
  }

  if (showTwistInfoDialog && combat.arenaTwist != ArenaTwist.NONE) {
    AlertDialog(
      onDismissRequest = { showTwistInfoDialog = false },
      containerColor = CharredBasalt,
      title = { Text("Arena Twist: ${combat.arenaTwist.title}", color = GildedBrass) },
      text = {
        Text(
          text = combat.arenaTwist.summary,
          style = MaterialTheme.typography.bodyLarge,
          color = WarmParchment,
        )
      },
      confirmButton = {
        TextButton(onClick = { showTwistInfoDialog = false }) {
          Text("Got It", color = MoltenEmber)
        }
      },
    )
  }

  // ADR-009 Explicit Generic Ink / Spark Payment Picker
  pendingGenericPayCard?.let { cardInst ->
    val def = GameCatalog.cardById(cardInst.defId)
    val sources = availableGenericPaySources(combat.resources, def)
    AlertDialog(
      onDismissRequest = { pendingGenericPayCard = null },
      containerColor = CharredBasalt,
      modifier = Modifier.testTag("generic_pay_dialog"),
      title = { Text("Pay Generic Pip — ${def.name}", color = WarmParchment) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Choose which Ink pool or Spark pays the colorless cost first:",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedAsh,
          )
          sources.forEach { payHue ->
            val label = if (payHue == null) {
              "Spend Spark (${combat.resources.spark} available)"
            } else {
              val avail = combat.resources.inkOf(payHue) - (def.coloredCost[payHue] ?: 0)
              "Spend ${payHue.glyph} ${payHue.displayName} Ink ($avail spare)"
            }
            val btnColor = payHue?.let { hueColor(it) } ?: GildedBrass
            Button(
              onClick = {
                onPlayCard(cardInst.uid, payHue)
                pendingGenericPayCard = null
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("generic_pay_${payHue?.name?.lowercase() ?: "spark"}"),
              colors = ButtonDefaults.buttonColors(
                containerColor = btnColor,
                contentColor = ObsidianVoid,
              ),
            ) {
              Text(label, fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { pendingGenericPayCard = null }) {
          Text("Cancel", color = MutedAsh)
        }
      },
    )
  }

  chiselTargetCoord?.let { coord ->
    AlertDialog(
      onDismissRequest = { chiselTargetCoord = null },
      containerColor = CharredBasalt,
      title = { Text("Chisel Wand — Choose Hue", color = WarmParchment) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Hue.WHEEL_ORDER.forEach { hue ->
            Button(
              onClick = {
                onUseChiselWand(coord.row, coord.col, hue)
                chiselTargetCoord = null
                chiselMode = false
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(
                containerColor = hueColor(hue),
                contentColor = ObsidianVoid,
              ),
            ) {
              Text("${hue.glyph} ${hue.displayName}", fontWeight = FontWeight.Bold)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { chiselTargetCoord = null }) {
          Text("Cancel", color = MutedAsh)
        }
      },
    )
  }

  if (showPilesModal) {
    AlertDialog(
      onDismissRequest = { showPilesModal = false },
      containerColor = CharredBasalt,
      title = { Text("Piles, Relics & Paint Weights", color = WarmParchment) },
      text = {
        LazyColumn(
          modifier = Modifier.heightIn(max = 380.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            Text("Board Paint Weights (ADR-003)", style = MaterialTheme.typography.titleMedium, color = MoltenEmber)
            Hue.WHEEL_ORDER.forEach { hue ->
              val pct = (combat.paintDeck.hueWeightsBp[hue] ?: 2000) / 100.0
              Text("• ${hue.displayName}: ${"%.1f".format(pct)}% weight", style = MaterialTheme.typography.bodyMedium, color = hueColor(hue))
            }
          }
          item {
            Spacer(Modifier.height(4.dp))
            Text("Shaiva Tantric Items (${run.growth.tantricArtifactIds.size})", style = MaterialTheme.typography.titleMedium, color = VermilionHot)
          }
          if (run.growth.tantricArtifactIds.isEmpty()) {
            item {
              Text("• None bound yet (earned via encounters)", style = MaterialTheme.typography.bodySmall, color = MutedAsh)
            }
          } else {
            items(run.growth.tantricArtifactIds) { artId ->
              val art = ProgressionCatalog.artifactByIdOrNull(artId)
              if (art != null) {
                Text("• ${art.glyph} ${art.name}: ${art.summary}", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
              }
            }
          }
          item {
            Spacer(Modifier.height(4.dp))
            Text("Active Relics (${run.relics.size})", style = MaterialTheme.typography.titleMedium, color = GildedBrass)
          }
          items(run.relics) { relicId ->
            val relic = GameCatalog.relicById(relicId)
            Text("• ${relic.name}: ${relic.summary}", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
          }
          item {
            Spacer(Modifier.height(4.dp))
            Text("Draw Pile (${combat.combatDeck.drawPile.size})", style = MaterialTheme.typography.titleMedium, color = HueTide)
          }
          items(combat.combatDeck.drawPile, key = { "draw_${it.uid}" }) { card ->
            val def = GameCatalog.cardById(card.defId)
            Text("• ${def.name} — ${def.description}", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPilesModal = false }) {
          Text("Close", color = MoltenEmber)
        }
      },
    )
  }
}

@Composable
private fun CombatStatusBanner(
  combat: CombatState,
  chiselMode: Boolean,
  boardFeedback: String?,
  selectedSwapCoord: BoardCoord?,
  hoveredSwapPreview: SwapWaveResolution?,
  selectedGroup: GroupResolution?,
  isWeavePhase: Boolean,
  isCastPhase: Boolean,
  isFogActive: Boolean,
  showTacticalHints: Boolean,
  onCancelChisel: () -> Unit,
  onConfirmGroup: () -> Unit,
) {
  when {
    chiselMode -> {
      Surface(
        color = HueTide.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HueTide),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Chisel Wand: Click any unsealed occupied tile to recolour it",
            style = MaterialTheme.typography.labelSmall,
            color = WarmParchment,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          TextButton(
            onClick = onCancelChisel,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("chisel_cancel_button"),
          ) {
            Text("Cancel", color = CrimsonRuin, style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    boardFeedback != null -> {
      Surface(
        color = CrimsonRuin.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MoltenEmber.copy(alpha = 0.85f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("board_feedback_banner"),
      ) {
        Text(
          text = boardFeedback,
          style = MaterialTheme.typography.labelSmall,
          color = WarmParchment,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
      }
    }

    hoveredSwapPreview != null && hoveredSwapPreview.isValid && isWeavePhase -> {
      val (effectiveHalfSteps, _) = BoardEngine.previewSpectrumForPop(
        current = combat.spectrum,
        resolvedHue = hoveredSwapPreview.primaryHue,
        usedPrism = hoveredSwapPreview.prismCount > 0,
        isDirectAshGroup = hoveredSwapPreview.isDirectAshOnly,
      )
      val dmg = if (hoveredSwapPreview.isDirectAshOnly) {
        hoveredSwapPreview.ashChipDamage
      } else {
        (hoveredSwapPreview.basePower * effectiveHalfSteps) / 2
      }
      val inkPart = if (isFogActive) {
        "? Ink"
      } else if (hoveredSwapPreview.totalInkAmount > 0) {
        "+${hoveredSwapPreview.totalInkAmount} ${hoveredSwapPreview.primaryHue?.shortTag ?: "Ink"}"
      } else {
        "0 Ink"
      }
      val brandPart = if (hoveredSwapPreview.brandVigilCost > 0) " • -${hoveredSwapPreview.brandVigilCost}V" else ""
      Surface(
        color = GildedBrass.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (hoveredSwapPreview.brandVigilCost > 0) CrimsonRuin else GildedBrass),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("hover_swap_preview"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Match ${hoveredSwapPreview.coords.size} • $dmg dmg @ ${GameEngine.formatSpectrum(effectiveHalfSteps)} • $inkPart$brandPart",
            style = MaterialTheme.typography.labelSmall,
            color = WarmParchment,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "RELEASE / CLICK",
            style = MaterialTheme.typography.labelSmall,
            color = GildedBrass,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    selectedSwapCoord != null && isWeavePhase -> {
      Surface(
        color = MoltenEmber.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GildedBrass),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("selected_swap_banner"),
      ) {
        Text(
          text = "Tile (${selectedSwapCoord.row + 1},${selectedSwapCoord.col + 1}) selected — click an adjacent tile to swap & line up 3+",
          style = MaterialTheme.typography.labelSmall,
          color = WarmParchment,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
      }
    }

    selectedGroup != null && selectedGroup.isValid && isWeavePhase -> {
      Surface(
        onClick = onConfirmGroup,
        color = MoltenEmber.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MoltenEmber),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("confirm_group_button"),
      ) {
        Text(
          text = "Tap again to confirm",
          style = MaterialTheme.typography.labelSmall,
          color = WarmParchment,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
      }
    }

    else -> {
      val lastEvent = combat.lastEvents.lastOrNull()
      val message = when {
        lastEvent != null && lastEvent.isAlert -> lastEvent.message
        isFogActive -> "FOG (${combat.player.fogTapsRemaining}M): Colors & Ink hidden — match glyphs or cast Gold Eye."
        showTacticalHints && isWeavePhase && combat.tapsRemaining > 0 ->
          "Drag or click 2 adjacent tiles to align 3+ matching colors • Next: ${combat.spectrum.currentHue?.let { Hue.nextInWheel(it).displayName } ?: "Any"}"
        lastEvent != null -> lastEvent.message
        isCastPhase -> "CAST: Spend colored Ink & Spark on cards, or End Turn."
        else -> "Ready"
      }
      Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = when {
          lastEvent?.isAlert == true -> MoltenEmber
          isFogActive -> HueTide
          else -> MutedAsh
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
      )
    }
  }
}

@Composable
private fun ValidSwapChip(
  option: ValidSwapOption,
  isFogActive: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val res = option.preview
  val isHorizontal = option.from.row == option.to.row
  val glyph = when {
    res.isDirectAshOnly -> tileGlyph(TileKind.Ash)
    res.primaryHue != null -> res.primaryHue.glyph
    else -> tileGlyph(TileKind.Prism)
  }
  val accentColor = when {
    isFogActive -> HueTide
    res.isDirectAshOnly -> tileColor(TileKind.Ash)
    res.primaryHue != null -> hueColor(res.primaryHue)
    else -> GildedBrass
  }
  val titleLabel = when {
    isFogActive -> "$glyph ×${res.coords.size}"
    res.isDirectAshOnly -> "$glyph ASH ×${res.coords.size}"
    res.primaryHue != null -> "$glyph ${res.primaryHue.shortTag} ×${res.coords.size}"
    else -> "$glyph PRISM ×${res.coords.size}"
  }
  val detailLabel = buildString {
    append("${option.previewDamage}dmg")
    if (isFogActive) {
      append(" · ? Ink")
    } else if (res.totalInkAmount > 0) {
      append(" · +${res.totalInkAmount}")
    } else if (res.isDirectAshOnly) {
      append(" · 0 Ink")
    }
    if (res.brandVigilCost > 0) {
      append(" · -${res.brandVigilCost}V")
    }
  }
  val borderColor = when {
    res.brandVigilCost > 0 -> CrimsonRuin
    isSelected -> WarmParchment
    !isFogActive && option.advancesWheel -> GildedBrass
    else -> accentColor.copy(alpha = 0.75f)
  }
  val containerColor = if (isFogActive) {
    FogVeilSlate
  } else {
    accentColor.copy(alpha = if (isSelected) 0.34f else 0.18f)
  }

  Surface(
    onClick = onClick,
    color = containerColor,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(if (isSelected || (!isFogActive && option.advancesWheel)) 1.5.dp else 1.dp, borderColor),
    modifier = modifier.semantics {
      contentDescription = "Swap $titleLabel, $detailLabel"
    },
  ) {
    Row(
      modifier = Modifier
        .fillMaxHeight()
        .padding(horizontal = 8.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = if (isHorizontal) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
        contentDescription = null,
        tint = if (!isFogActive && option.advancesWheel) GildedBrass else accentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = titleLabel,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        color = WarmParchment,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
      if (!isFogActive && option.advancesWheel) {
        Text(
          text = "★",
          fontSize = 10.sp,
          lineHeight = 11.sp,
          color = GildedBrass,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = detailLabel,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        color = if (res.brandVigilCost > 0) CrimsonRuin else WarmParchment.copy(alpha = 0.8f),
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun CompactStatChip(label: String, value: String, accent: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      fontSize = 8.sp,
      lineHeight = 9.sp,
      fontFamily = IbmPlexMonoFamily,
      color = MutedAsh,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = value,
      fontSize = 10.sp,
      lineHeight = 11.sp,
      fontFamily = IbmPlexMonoFamily,
      color = accent,
      fontWeight = FontWeight.ExtraBold,
    )
  }
}

/**
 * Returns selectable generic-pip payment options: null = Spark, Hue = eligible Ink hue with spare Ink after colored pips.
 */
private fun availableGenericPaySources(resources: ResourceState, def: CardDef): List<Hue?> {
  val list = mutableListOf<Hue?>()
  if (resources.spark > 0) list.add(null)
  Hue.WHEEL_ORDER.forEach { hue ->
    val spare = resources.inkOf(hue) - (def.coloredCost[hue] ?: 0)
    if (spare > 0) list.add(hue)
  }
  return list
}

@Composable
private fun TelegraphSlotChip(
  slotTitle: String,
  intent: EnemyIntent,
  modifier: Modifier = Modifier,
) {
  val (label, accent, icon) = when (intent) {
    is EnemyIntent.Attack -> Triple(
      if (intent.hits > 1) "ATK ${intent.damage}x${intent.hits}" else "ATK ${intent.damage}",
      CrimsonRuin,
      Icons.Default.Whatshot,
    )
    is EnemyIntent.Defend -> Triple("BLK +${intent.block}", SteelShield, Icons.Default.Shield)
    is EnemyIntent.CorruptBoard -> {
      val kindName = if (intent.kind is TileKind.Ash) "Ash" else "Stone"
      Triple("+${intent.count} $kindName", HueGold, Icons.Default.Whatshot)
    }
    is EnemyIntent.SiphonFlask -> Triple("ATK ${intent.damage} • -${intent.flaskDrain} Flask", HueViolet, Icons.Default.LocalDrink)
    is EnemyIntent.InflictBrand -> Triple(
      if (intent.damage > 0) "Brand ${intent.count} (${intent.damage} dmg)" else "Brand ${intent.count} blocks",
      MoltenEmber,
      Icons.Default.Whatshot,
    )
    is EnemyIntent.InflictFog -> Triple(
      if (intent.damage > 0) "Fog ${intent.taps}T (${intent.damage} dmg)" else "Fog ${intent.taps} Taps",
      HueTide,
      Icons.Default.Cloud,
    )
    is EnemyIntent.SealColumn -> Triple(
      if (intent.damage > 0) "Seal Col ${intent.turns}T (${intent.damage})" else "Seal Col (${intent.turns}T)",
      HueViolet,
      Icons.Default.Lock,
    )
  }

  Surface(
    modifier = modifier,
    color = SlateStone,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
      Column {
        Text(text = slotTitle, style = MaterialTheme.typography.labelSmall, color = MutedAsh)
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = accent,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun mapOffsetToBoardCoord(
  offset: Offset,
  boardSize: IntSize,
  rows: Int,
  cols: Int,
): BoardCoord? {
  if (boardSize.width <= 0 || boardSize.height <= 0 || rows <= 0 || cols <= 0) return null
  if (offset.x < 0f || offset.y < 0f || offset.x > boardSize.width || offset.y > boardSize.height) return null
  val col = ((offset.x / boardSize.width.toFloat()) * cols).toInt().coerceIn(0, cols - 1)
  val row = ((offset.y / boardSize.height.toFloat()) * rows).toInt().coerceIn(0, rows - 1)
  return BoardCoord(row, col)
}

@Composable
private fun BoardTileCell(
  tile: Tile,
  isHighlighted: Boolean,
  isSelected: Boolean,
  isPartnerHint: Boolean,
  isBeaconHint: Boolean,
  isInvalidFlash: Boolean = false,
  isPlayable: Boolean,
  isFogged: Boolean,
  fastAnimations: Boolean,
  sealedTurns: Int,
  showSealBanner: Boolean,
  showGlyph: Boolean,
  strongGlyph: Boolean = showGlyph,
  dragTranslation: Offset,
  onAccessibilityClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isEmpty = tile.kind is TileKind.Empty
  val isSealed = sealedTurns > 0
  val baseColor = when {
    isEmpty -> ObsidianVoid
    isFogged && tile.kind is TileKind.Playable -> FogVeilSlate
    else -> tileColor(tile.kind)
  }
  val targetAlpha = when {
    isEmpty -> 1f
    isSelected || isPartnerHint || isBeaconHint -> 1f
    isPlayable -> 0.96f
    else -> 0.78f
  }
  val animatedBg by animateColorAsState(
    targetValue = if (isEmpty) ObsidianVoid else baseColor.copy(alpha = targetAlpha),
    animationSpec = tween(durationMillis = if (fastAnimations) 75 else 130),
    label = "tile_bg",
  )
  val highlightScale by animateFloatAsState(
    targetValue = when {
      isSelected && !isEmpty -> 1.09f
      isPartnerHint && !isEmpty -> 1.05f
      isBeaconHint && !isEmpty -> 1.08f
      isHighlighted && !isEmpty -> 0.96f
      else -> 1f
    },
    animationSpec = spring(dampingRatio = 0.58f, stiffness = 460f),
    label = "tile_scale",
  )

  // Punchy bouncy gravity-drop & entrance glow whenever a new tile.id falls into this cell!
  var mounted by remember { mutableStateOf(false) }
  val cascadeDropPx = remember { Animatable(0f) }
  val cascadeScale = remember { Animatable(1f) }
  val cascadeFlash = remember { Animatable(0f) }
  LaunchedEffect(tile.id) {
    if (!mounted) {
      mounted = true
    } else if (!isEmpty) {
      cascadeDropPx.snapTo(if (fastAnimations) -26f else -48f)
      cascadeScale.snapTo(0.82f)
      cascadeFlash.snapTo(0.55f)
      if (fastAnimations) {
        cascadeDropPx.animateTo(0f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
        cascadeScale.animateTo(1f, tween(durationMillis = 90))
        cascadeFlash.animateTo(0f, tween(durationMillis = 90))
      } else {
        cascadeScale.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 520f))
        cascadeDropPx.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
        cascadeFlash.animateTo(0f, tween(durationMillis = 150))
      }
    }
  }

  val borderColor = when {
    isInvalidFlash -> CrimsonRuin
    isSealed -> HueViolet
    tile.isBranded -> CrimsonRuin
    isSelected -> WarmParchment
    isPartnerHint || isBeaconHint -> GildedBrass
    isEmpty -> SmokedBorder.copy(alpha = 0.28f)
    tile.kind is TileKind.Prism -> GildedBrass
    isPlayable -> WarmParchment.copy(alpha = 0.62f)
    tile.kind is TileKind.Stone -> Color(0xFFA099A8)
    else -> ObsidianVoid.copy(alpha = 0.65f)
  }
  val desc = buildString {
    append(tileContentDescription(tile.kind))
    if (isSelected) append(", Selected for swap")
    if (isPlayable && !isEmpty) append(", Swappable")
    if (tile.isBranded) append(", Branded -2 Vigil")
    if (isSealed) append(", Sealed column $sealedTurns turns")
  }
  val shape = RoundedCornerShape(8.dp)

  Box(
    modifier = modifier
      .graphicsLayer {
        translationX = dragTranslation.x
        translationY = cascadeDropPx.value + dragTranslation.y
        scaleX = highlightScale * cascadeScale.value
        scaleY = highlightScale * cascadeScale.value
      }
      .clip(shape)
      .background(
        if (isEmpty) {
          Brush.verticalGradient(colors = listOf(ObsidianVoid, CharredBasalt.copy(alpha = 0.42f)))
        } else {
          Brush.verticalGradient(
            colors = listOf(
              animatedBg,
              animatedBg.copy(alpha = 0.84f),
              animatedBg.copy(alpha = 0.58f),
            ),
          )
        }
      )
      .border(
        width = when {
          isInvalidFlash || isSelected || isPartnerHint || isBeaconHint || isSealed || tile.isBranded -> 2.2.dp
          isPlayable && !isEmpty -> 1.5.dp
          else -> 1.dp
        },
        color = borderColor,
        shape = shape,
      )
      .semantics {
        role = Role.Button
        contentDescription = desc
        onClick(label = "Select or swap tile") {
          onAccessibilityClick()
          true
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    // Glossy beveled jewel specular gleam at the top lip of every solid tile
    if (!isEmpty) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.36f)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.White.copy(alpha = if (tile.kind is TileKind.Prism) 0.44f else 0.24f),
                Color.Transparent,
              ),
            ),
          ),
      )
    }
    if (cascadeFlash.value > 0.01f && !isEmpty) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(WarmParchment.copy(alpha = cascadeFlash.value)),
      )
    }
    if (!isEmpty && showGlyph) {
      val glyphAlpha = when {
        tile.kind is TileKind.Prism && !isFogged -> 0.98f
        strongGlyph -> if (isPlayable) 1f else 0.80f
        else -> if (isPlayable) 0.78f else 0.50f
      }
      Text(
        text = tileGlyph(tile.kind),
        style = MaterialTheme.typography.titleMedium,
        color = if (tile.kind is TileKind.Prism && !isFogged) {
          ObsidianVoid.copy(alpha = glyphAlpha)
        } else {
          WarmParchment.copy(alpha = glyphAlpha)
        },
        fontWeight = FontWeight.Bold,
      )
    }
    if (isPlayable && !isEmpty && !isSealed && !tile.isBranded) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(2.dp)
          .size(if (isBeaconHint) 6.dp else 4.dp)
          .clip(CircleShape)
          .background(if (isBeaconHint) GildedBrass else WarmParchment.copy(alpha = 0.85f)),
      )
    }
    if (tile.isBranded && !isEmpty) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(1.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(CrimsonRuin),
      ) {
        Text(
          text = "-2V",
          fontSize = 7.sp,
          lineHeight = 8.sp,
          color = WarmParchment,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 2.dp),
        )
      }
    }
    if (showSealBanner) {
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(1.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(HueViolet),
      ) {
        Text(
          text = "S$sealedTurns",
          fontSize = 7.sp,
          lineHeight = 8.sp,
          color = WarmParchment,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 2.dp),
        )
      }
    }
  }
}
