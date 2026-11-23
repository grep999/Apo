package com.example.engine

/**
 * Pure deterministic rules core for EMBERFALL.
 * Enforces:
 * - Strict transactional state guards (validateTransition)
 * - ADR-001 Turn Phase FSM: DRAW -> WEAVE (<=3 Taps) -> CAST -> WALL -> ENEMY -> CLEANUP
 * - ADR-003 Deck-frame board paint weights
 * - ADR-004 Prism single contacting hue wheel tie-break (no multi-hue bridge)
 * - ADR-005 Ordered Spectrum wheel (Ember -> Gold -> Moss -> Tide -> Violet) with Run 0 & reset
 * - ADR-006 Ash yields 0 Ink and deals n - 1 chip after Enemy Block (direct 2+ or adjacent)
 * - ADR-007 Stone breaks only on 4+ groups via bounded orthogonal chain
 * - ADR-008 Incoming row preview, rise, skip rise, and pre-rise Overflow (-8 HP, burn 2 rows, -1 Flask, skip rise)
 * - ADR-009 5 Colored Ink pools + Spark wild resource
 * - ADR-010 Brand (2 Vigil per marked block popped; shortfall from HP)
 * - ADR-011 Fog (hides colors & Ink for 3 Taps; cleared by Gold Eye)
 * - ADR-012 Seal (locks 1 board column for 3 turns; cleansed by Quarry Purge)
 * - ADR-013 Exponential Enemy Vigor scaling
 * - ADR-015 Bounded event queue (100-event budget, Echo repeats single PrimaryEffect once only)
 * - ADR-016 Act I Row 5 Shade with full-deck intents, Cinders + Remnant reward, zero relic inheritance
 * - ADR-017 Unlit Covenant & 3 consecutive Ash-only no-card turn Dread true ending vs Act III Boss
 */
object GameEngine {

  const val MAX_EVENTS_PER_ACTION = 100
  const val SKIP_CARD_CINDER_BONUS = 12
  const val REROLL_CARDS_COST = 15
  const val CAMPFIRE_GAMBLE_HP_COST = 8
  const val CAMPFIRE_GAMBLE_CINDERS = 35

  private class BoundedEventQueue {
    private var nextSeq = 1L
    private val items = mutableListOf<CombatEvent>()
    private var echoEmittedCount = 0

    fun enqueue(
      priority: EventPriority,
      message: String,
      isAlert: Boolean = false,
      isEcho: Boolean = false,
      kind: CombatEventKind = CombatEventKind.INFO,
      effectiveSpectrumHalfSteps: Int? = null,
    ): Boolean {
      if (items.size >= MAX_EVENTS_PER_ACTION) return false
      if (isEcho) {
        if (echoEmittedCount >= 8) return false
        echoEmittedCount++
      }
      items.add(
        CombatEvent(
          seqId = nextSeq++,
          priority = priority,
          message = message,
          isAlert = isAlert,
          isEcho = isEcho,
          kind = kind,
          effectiveSpectrumHalfSteps = effectiveSpectrumHalfSteps,
        )
      )
      return true
    }

    fun drainSorted(): List<CombatEvent> =
      items.sortedWith(compareBy({ it.priority.order }, { it.seqId }))
  }

  fun startNewRun(
    heroClass: HeroClass,
    seedText: String,
    previousRunDeckIds: List<String> = emptyList(),
    unlitCovenantActive: Boolean = false,
    dreadLevel: Int = 0,
  ): RunState {
    val seed = SeededRng.fromSeedString(seedText)
    val initialStreams = RngStreams.fromRunSeed(seed)
    val (actMap, nextMapRng) = CampaignGenerator.generateActMap(1, initialStreams.map)

    var cardUid = 1L
    val starterCards = GameCatalog.starterDeckIds(heroClass).map { defId ->
      CardInstance(uid = cardUid++, defId = defId, upgraded = false)
    }

    // ADR-017: Unlit Covenant is exclusively binding for The Unlit class
    val effectiveCovenant = unlitCovenantActive && heroClass == HeroClass.THE_UNLIT

    return RunState(
      runSeed = seed,
      seedLabel = seedText.trim().ifEmpty { seed.toString() },
      rng = initialStreams.copy(map = nextMapRng),
      heroClass = heroClass,
      currentAct = 1,
      hp = heroClass.maxHp,
      maxHp = heroClass.maxHp,
      flasks = heroClass.startingFlasks,
      maxFlasks = heroClass.maxFlasks,
      cinders = heroClass.startingCinders,
      masterDeck = starterCards,
      nextCardUid = cardUid,
      relics = listOf(heroClass.signatureRelicId), // Signature relic only; zero relic inheritance
      remnants = emptyList(),
      actMap = actMap,
      currentNodeId = null,
      shadeDeckSnapshot = previousRunDeckIds,
      unlitCovenantActive = effectiveCovenant,
      dreadLevel = dreadLevel.coerceIn(0, 3),
    )
  }

  fun availableNodeIds(run: RunState): Set<String> {
    if (run.gameOverReason != null || run.runWon) return emptySet()
    if (run.combat != null || run.pendingReward != null || run.shopStock != null || run.inNonCombatNode || run.activeEvent != null) {
      return emptySet()
    }
    val currentId = run.currentNodeId ?: return run.actMap.nodes.filter { it.row == 1 }.map { it.id }.toSet()
    val current = run.actMap.nodes.firstOrNull { it.id == currentId } ?: return emptySet()
    return current.nextNodeIds.toSet()
  }

  /**
   * Central transactional FSM guard rejecting any out-of-order command before mutation.
   */
  fun validateTransition(run: RunState, command: GameCommand): CommandRejectionReason? {
    if (run.gameOverReason != null || run.runWon) {
      return CommandRejectionReason.RUN_TERMINATED
    }

    return when (command) {
      is GameCommand.ChooseNode -> {
        if (run.combat != null || run.pendingReward != null || run.shopStock != null || run.inNonCombatNode || run.activeEvent != null) {
          CommandRejectionReason.WRONG_PHASE
        } else if (command.nodeId !in availableNodeIds(run)) {
          CommandRejectionReason.NODE_UNAVAILABLE
        } else {
          null
        }
      }
      is GameCommand.TapCell -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        when {
          combat.phase != TurnPhase.WEAVE -> CommandRejectionReason.WRONG_PHASE
          combat.tapsRemaining <= 0 -> CommandRejectionReason.NO_TAPS_REMAINING
          command.col !in 0 until combat.board.cols || command.row !in 0 until combat.board.rows -> CommandRejectionReason.INVALID_BOARD_GROUP
          combat.board.isColumnSealed(command.col) -> CommandRejectionReason.COLUMN_SEALED
          combat.board.tileAt(command.row, command.col)?.kind is TileKind.Empty -> CommandRejectionReason.EMPTY_TILE
          else -> null
        }
      }
      is GameCommand.SwapCells -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        val r1 = command.r1
        val c1 = command.c1
        val r2 = command.r2
        val c2 = command.c2
        when {
          combat.phase != TurnPhase.WEAVE -> CommandRejectionReason.WRONG_PHASE
          combat.tapsRemaining <= 0 -> CommandRejectionReason.NO_TAPS_REMAINING
          r1 !in 0 until combat.board.rows || c1 !in 0 until combat.board.cols ||
            r2 !in 0 until combat.board.rows || c2 !in 0 until combat.board.cols -> CommandRejectionReason.NON_ADJACENT_SWAP
          kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2) != 1 -> CommandRejectionReason.NON_ADJACENT_SWAP
          combat.board.isColumnSealed(c1) || combat.board.isColumnSealed(c2) -> CommandRejectionReason.COLUMN_SEALED
          combat.board.tileAt(r1, c1)?.kind is TileKind.Empty ||
            combat.board.tileAt(r2, c2)?.kind is TileKind.Empty -> CommandRejectionReason.EMPTY_TILE
          combat.board.tileAt(r1, c1)?.kind is TileKind.Stone ||
            combat.board.tileAt(r2, c2)?.kind is TileKind.Stone -> CommandRejectionReason.NO_MATCH_FOR_SWAP
          else -> null
        }
      }
      is GameCommand.UseChiselWand -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        when {
          "relic_chisel_wand" !in run.relics -> CommandRejectionReason.RELIC_NOT_OWNED
          combat.player.chiselWandUsedThisCombat -> CommandRejectionReason.CHISEL_ALREADY_USED
          combat.phase != TurnPhase.WEAVE && combat.phase != TurnPhase.CAST -> CommandRejectionReason.WRONG_PHASE
          command.col !in 0 until combat.board.cols || command.row !in 0 until combat.board.rows -> CommandRejectionReason.INVALID_BOARD_GROUP
          combat.board.isColumnSealed(command.col) -> CommandRejectionReason.COLUMN_SEALED
          combat.board.tileAt(command.row, command.col)?.kind is TileKind.Empty -> CommandRejectionReason.EMPTY_TILE
          else -> null
        }
      }
      GameCommand.TriggerOverdrive -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        when {
          combat.phase != TurnPhase.WEAVE && combat.phase != TurnPhase.CAST -> CommandRejectionReason.WRONG_PHASE
          combat.player.overdriveCharge < PlayerCombatState.MAX_OVERDRIVE -> CommandRejectionReason.OVERDRIVE_NOT_READY
          else -> null
        }
      }
      GameCommand.ProceedToCast -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        if (combat.phase != TurnPhase.WEAVE) CommandRejectionReason.WRONG_PHASE else null
      }
      is GameCommand.CastCard -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        if (combat.phase != TurnPhase.CAST) return CommandRejectionReason.WRONG_PHASE
        val inst = combat.combatDeck.hand.firstOrNull { it.uid == command.cardUid }
          ?: return CommandRejectionReason.CARD_NOT_IN_HAND
        val def = GameCatalog.cardById(inst.defId)
        if (combat.player.vigil < def.vigilCost) return CommandRejectionReason.INSUFFICIENT_VIGIL
        if (tryPayCardCost(combat.resources, def, inst.upgraded, command.genericPayHue) == null) {
          return CommandRejectionReason.INSUFFICIENT_INK_OR_SPARK
        }
        null
      }
      GameCommand.UseFlask -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        when {
          combat.phase != TurnPhase.WEAVE && combat.phase != TurnPhase.CAST -> CommandRejectionReason.WRONG_PHASE
          run.flasks <= 0 -> CommandRejectionReason.NO_FLASKS
          else -> null
        }
      }
      GameCommand.WitnessIllusion -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        when {
          combat.phase != TurnPhase.WEAVE && combat.phase != TurnPhase.CAST -> CommandRejectionReason.WRONG_PHASE
          combat.mindIllusion.witnessUsedThisTurn -> CommandRejectionReason.WITNESS_ALREADY_USED
          else -> null
        }
      }
      GameCommand.EndTurn -> {
        val combat = run.combat ?: return CommandRejectionReason.NO_ACTIVE_COMBAT
        if (combat.phase != TurnPhase.WEAVE && combat.phase != TurnPhase.CAST) {
          CommandRejectionReason.WRONG_PHASE
        } else null
      }
      GameCommand.OpenRewards -> {
        val combat = run.combat ?: return CommandRejectionReason.WRONG_PHASE
        if (combat.phase != TurnPhase.COMBAT_WON || run.pendingReward == null) {
          CommandRejectionReason.WRONG_PHASE
        } else null
      }
      GameCommand.ClaimRewardRelic, GameCommand.SkipRewardRelic -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        if (!rewardOpen) {
          CommandRejectionReason.WRONG_PHASE
        } else if (reward.relicRewardId == null || reward.relicPicked) {
          CommandRejectionReason.REWARD_ALREADY_CLAIMED
        } else null
      }
      is GameCommand.PickRewardCard, GameCommand.SkipRewardCard -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        if (!rewardOpen) {
          CommandRejectionReason.WRONG_PHASE
        } else if (reward.cardPicked) {
          CommandRejectionReason.REWARD_ALREADY_CLAIMED
        } else null
      }
      GameCommand.RerollRewardCards -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        when {
          !rewardOpen -> CommandRejectionReason.WRONG_PHASE
          reward.cardPicked || reward.cardChoices.isEmpty() -> CommandRejectionReason.REWARD_ALREADY_CLAIMED
          reward.rerollUsed -> CommandRejectionReason.REROLL_ALREADY_USED
          run.cinders < REROLL_CARDS_COST -> CommandRejectionReason.INSUFFICIENT_CINDERS
          else -> null
        }
      }
      is GameCommand.UnlockPassiveNode -> {
        when {
          run.growth.unspentPassivePoints <= 0 -> CommandRejectionReason.NO_PASSIVE_POINTS
          !run.growth.canUnlockPassive(command.nodeId) -> CommandRejectionReason.PASSIVE_NODE_LOCKED
          else -> null
        }
      }
      is GameCommand.SocketSupport -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        when {
          !rewardOpen -> CommandRejectionReason.WRONG_PHASE
          reward.supportPicked -> CommandRejectionReason.REWARD_ALREADY_CLAIMED
          command.supportId !in reward.supportChoices -> CommandRejectionReason.SUPPORT_UNAVAILABLE
          ProgressionCatalog.supportByIdOrNull(command.supportId) == null -> CommandRejectionReason.SUPPORT_UNAVAILABLE
          command.supportId in run.growth.socketedSupportIds -> CommandRejectionReason.SUPPORT_UNAVAILABLE
          run.growth.socketedSupportIds.size >= run.growth.maxSockets && command.replaceSlot !in run.growth.socketedSupportIds.indices -> CommandRejectionReason.SUPPORT_UNAVAILABLE
          run.growth.socketedSupportIds.size < run.growth.maxSockets && command.replaceSlot != -1 -> CommandRejectionReason.WRONG_PHASE
          else -> null
        }
      }
      GameCommand.SkipSupportChoice -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        when {
          !rewardOpen -> CommandRejectionReason.WRONG_PHASE
          reward.supportPicked || reward.supportChoices.isEmpty() -> CommandRejectionReason.REWARD_ALREADY_CLAIMED
          else -> null
        }
      }
      is GameCommand.BindArtifact -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        when {
          !rewardOpen -> CommandRejectionReason.WRONG_PHASE
          reward.artifactPicked -> CommandRejectionReason.REWARD_ALREADY_CLAIMED
          command.artifactId !in reward.artifactChoices -> CommandRejectionReason.ARTIFACT_UNAVAILABLE
          ProgressionCatalog.artifactByIdOrNull(command.artifactId) == null -> CommandRejectionReason.ARTIFACT_UNAVAILABLE
          command.artifactId in run.growth.tantricArtifactIds -> CommandRejectionReason.ARTIFACT_UNAVAILABLE
          else -> null
        }
      }
      GameCommand.SkipArtifactChoice -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        when {
          !rewardOpen -> CommandRejectionReason.WRONG_PHASE
          reward.artifactPicked || reward.artifactChoices.isEmpty() -> CommandRejectionReason.REWARD_ALREADY_CLAIMED
          else -> null
        }
      }
      GameCommand.FinishRewards -> {
        val reward = run.pendingReward
        val rewardOpen = reward != null && (run.combat == null || run.combat.phase == TurnPhase.COMBAT_WON)
        if (!rewardOpen) {
          CommandRejectionReason.WRONG_PHASE
        } else if (!reward.allChoicesResolved) {
          CommandRejectionReason.WRONG_PHASE
        } else null
      }
      GameCommand.CampfireRest, is GameCommand.CampfireUpgradeCard -> {
        val node = run.actMap.nodes.firstOrNull { it.id == run.currentNodeId }
        if (!run.inNonCombatNode || node?.type != MapNodeType.CAMPFIRE) {
          CommandRejectionReason.WRONG_PHASE
        } else if (run.campfireUsed) {
          CommandRejectionReason.CAMPFIRE_USED
        } else null
      }
      GameCommand.CampfireGamble -> {
        val node = run.actMap.nodes.firstOrNull { it.id == run.currentNodeId }
        when {
          !run.inNonCombatNode || node?.type != MapNodeType.CAMPFIRE -> CommandRejectionReason.WRONG_PHASE
          run.campfireUsed -> CommandRejectionReason.CAMPFIRE_USED
          run.hp <= CAMPFIRE_GAMBLE_HP_COST -> CommandRejectionReason.INSUFFICIENT_HP
          else -> null
        }
      }
      is GameCommand.ChooseEventOption -> {
        val evt = run.activeEvent
        val choice = evt?.choices?.firstOrNull { it.id == command.choiceId }
        when {
          !run.inNonCombatNode || evt == null || evt.resolvedSummary != null -> CommandRejectionReason.EVENT_UNAVAILABLE
          choice == null -> CommandRejectionReason.EVENT_UNAVAILABLE
          run.cinders < choice.minCindersRequired || run.hp < choice.minHpRequired || run.flasks < choice.minFlasksRequired -> CommandRejectionReason.EVENT_UNAVAILABLE
          else -> null
        }
      }
      GameCommand.LeaveNode -> {
        if (!run.inNonCombatNode) {
          CommandRejectionReason.WRONG_PHASE
        } else if (run.activeEvent != null && run.activeEvent.resolvedSummary == null) {
          CommandRejectionReason.WRONG_PHASE
        } else null
      }
      is GameCommand.ShopBuyCard, is GameCommand.ShopBuyRelic, GameCommand.ShopBuyFlask, is GameCommand.ShopRemoveCard -> {
        if (!run.inNonCombatNode || run.shopStock == null) {
          CommandRejectionReason.SHOP_UNAVAILABLE
        } else null
      }
    }
  }

  /**
   * Single deterministic typed-command entry point with transactional guarantees.
   */
  fun execute(run: RunState, command: GameCommand): CommandResult {
    val guardRejection = validateTransition(run, command)
    if (guardRejection != null) {
      return CommandResult(run, accepted = false, rejectionReason = guardRejection)
    }

    val rawResult = when (command) {
      is GameCommand.ChooseNode -> handleChooseNode(run, command.nodeId)
      is GameCommand.TapCell -> handleTapCell(run, command.row, command.col)
      is GameCommand.SwapCells -> handleSwapCells(run, command.r1, command.c1, command.r2, command.c2)
      is GameCommand.UseChiselWand -> handleUseChiselWand(run, command.row, command.col, command.targetHue)
      GameCommand.TriggerOverdrive -> handleTriggerOverdrive(run)
      GameCommand.WitnessIllusion -> handleWitnessIllusion(run)
      GameCommand.ProceedToCast -> handleProceedToCast(run)
      is GameCommand.CastCard -> handleCastCard(run, command.cardUid, command.genericPayHue)
      GameCommand.UseFlask -> handleUseFlask(run)
      GameCommand.EndTurn -> handleEndTurn(run)
      GameCommand.OpenRewards -> {
        val next = run.copy(combat = null)
        CommandResult(next, accepted = true)
      }
      GameCommand.ClaimRewardRelic -> handleClaimRewardRelic(run)
      GameCommand.SkipRewardRelic -> handleSkipRewardRelic(run)
      is GameCommand.PickRewardCard -> handlePickRewardCard(run, command.cardDefId)
      GameCommand.SkipRewardCard -> handleSkipRewardCard(run)
      GameCommand.RerollRewardCards -> handleRerollRewardCards(run)
      is GameCommand.UnlockPassiveNode -> handleUnlockPassiveNode(run, command.nodeId)
      is GameCommand.SocketSupport -> handleSocketSupport(run, command.supportId, command.replaceSlot)
      GameCommand.SkipSupportChoice -> handleSkipSupportChoice(run)
      is GameCommand.BindArtifact -> handleBindArtifact(run, command.artifactId)
      GameCommand.SkipArtifactChoice -> handleSkipArtifactChoice(run)
      GameCommand.FinishRewards -> handleFinishRewards(run)
      GameCommand.CampfireRest -> handleCampfireRest(run)
      is GameCommand.CampfireUpgradeCard -> handleCampfireUpgradeCard(run, command.cardUid)
      GameCommand.CampfireGamble -> handleCampfireGamble(run)
      is GameCommand.ChooseEventOption -> handleChooseEventOption(run, command.choiceId)
      GameCommand.LeaveNode -> {
        val next = run.copy(
          shopStock = null,
          activeEvent = null,
          inNonCombatNode = false,
          campfireUsed = false,
          pendingReward = null,
        )
        CommandResult(next, accepted = true)
      }
      is GameCommand.ShopBuyCard -> handleShopBuyCard(run, command.cardId)
      is GameCommand.ShopBuyRelic -> handleShopBuyRelic(run, command.relicId)
      GameCommand.ShopBuyFlask -> handleShopBuyFlask(run)
      is GameCommand.ShopRemoveCard -> handleShopRemoveCard(run, command.cardUid)
    }

    return if (rawResult.accepted) {
      rawResult.copy(run = rawResult.run.copy(commandLog = rawResult.run.commandLog + command.serialize()))
    } else {
      CommandResult(run, accepted = false, rejectionReason = rawResult.rejectionReason)
    }
  }

  /**
   * Replays a run cleanly from scratch using seed + command sequence to verify strict determinism.
   */
  fun replay(
    heroClass: HeroClass,
    seedText: String,
    previousRunDeckIds: List<String>,
    commands: List<GameCommand>,
    unlitCovenantActive: Boolean = false,
    dreadLevel: Int = 0,
  ): RunState {
    var current = startNewRun(heroClass, seedText, previousRunDeckIds, unlitCovenantActive, dreadLevel)
    for (cmd in commands) {
      val result = execute(current, cmd)
      current = result.run
    }
    return current
  }

  // Convenience direct helpers delegating to execute(...)
  fun enterNode(run: RunState, nodeId: String): RunState = execute(run, GameCommand.ChooseNode(nodeId)).run
  fun tapBoard(run: RunState, row: Int, col: Int): RunState = execute(run, GameCommand.TapCell(row, col)).run
  fun swapBoard(run: RunState, r1: Int, c1: Int, r2: Int, c2: Int): RunState =
    execute(run, GameCommand.SwapCells(r1, c1, r2, c2)).run
  fun useChiselWand(run: RunState, row: Int, col: Int, targetHue: Hue): RunState =
    execute(run, GameCommand.UseChiselWand(row, col, targetHue)).run
  fun triggerOverdrive(run: RunState): RunState = execute(run, GameCommand.TriggerOverdrive).run
  fun proceedToCast(run: RunState): RunState = execute(run, GameCommand.ProceedToCast).run
  fun playCard(run: RunState, cardUid: Long, genericPayHue: Hue? = null): RunState =
    execute(run, GameCommand.CastCard(cardUid, genericPayHue)).run
  fun useFlask(run: RunState): RunState = execute(run, GameCommand.UseFlask).run
  fun endTurn(run: RunState): RunState = execute(run, GameCommand.EndTurn).run
  fun openRewardsScreen(run: RunState): RunState = execute(run, GameCommand.OpenRewards).run
  fun pickRewardCard(run: RunState, cardDefId: String): RunState = execute(run, GameCommand.PickRewardCard(cardDefId)).run
  fun skipRewardCard(run: RunState): RunState = execute(run, GameCommand.SkipRewardCard).run
  fun rerollRewardCards(run: RunState): RunState = execute(run, GameCommand.RerollRewardCards).run
  fun claimRewardRelic(run: RunState): RunState = execute(run, GameCommand.ClaimRewardRelic).run
  fun skipRewardRelic(run: RunState): RunState = execute(run, GameCommand.SkipRewardRelic).run
  fun finishRewardsAndAdvanceIfBoss(run: RunState): RunState = execute(run, GameCommand.FinishRewards).run
  fun campfireRest(run: RunState): RunState = execute(run, GameCommand.CampfireRest).run
  fun campfireUpgradeCard(run: RunState, cardUid: Long): RunState =
    execute(run, GameCommand.CampfireUpgradeCard(cardUid)).run
  fun campfireGamble(run: RunState): RunState = execute(run, GameCommand.CampfireGamble).run
  fun chooseEventOption(run: RunState, choiceId: String): RunState =
    execute(run, GameCommand.ChooseEventOption(choiceId)).run
  fun shopBuyCard(run: RunState, cardId: String): RunState = execute(run, GameCommand.ShopBuyCard(cardId)).run
  fun shopBuyRelic(run: RunState, relicId: String): RunState = execute(run, GameCommand.ShopBuyRelic(relicId)).run
  fun shopBuyFlask(run: RunState): RunState = execute(run, GameCommand.ShopBuyFlask).run
  fun shopRemoveCard(run: RunState, cardUid: Long): RunState = execute(run, GameCommand.ShopRemoveCard(cardUid)).run
  fun leaveNonCombatNode(run: RunState): RunState = execute(run, GameCommand.LeaveNode).run

  private fun handleChooseNode(run: RunState, nodeId: String): CommandResult {
    val node = run.actMap.nodes.firstOrNull { it.id == nodeId }
      ?: return CommandResult(run, accepted = false, rejectionReason = CommandRejectionReason.NODE_UNAVAILABLE)

    val updatedNodes = run.actMap.nodes.map { if (it.id == nodeId) it.copy(visited = true) else it }
    val baseRun = run.copy(
      actMap = run.actMap.copy(nodes = updatedNodes),
      currentNodeId = nodeId,
      pendingReward = null,
      shopStock = null,
      activeEvent = null,
      inNonCombatNode = false,
      campfireUsed = false,
    )

    val next = when (node.type) {
      MapNodeType.COMBAT, MapNodeType.ELITE, MapNodeType.SHADE, MapNodeType.BOSS -> startCombat(baseRun, node)
      MapNodeType.CAMPFIRE -> baseRun.copy(inNonCombatNode = true)
      MapNodeType.MERCHANT -> openMerchant(baseRun).copy(inNonCombatNode = true)
      MapNodeType.EVENT -> openEvent(baseRun).copy(inNonCombatNode = true)
      MapNodeType.TREASURE -> openTreasure(baseRun)
    }
    return CommandResult(next, accepted = true, events = next.combat?.lastEvents ?: emptyList())
  }

  private fun startCombat(run: RunState, node: MapNode): RunState {
    val queue = BoundedEventQueue()
    val arenaTwist = ArenaTwist.forNode(run.runSeed, node.id, node.type)
    val (enemyDef, nextEnemyRng) = CampaignGenerator.pickEnemyForNode(run.currentAct, node.type, run.rng.enemy)
    val (paintDeckInit, rngPaint1) = BoardPaintDeck.createInitial(run.masterDeck, run.rng.boardPaint)
    val startPrisms = 1 +
      (if ("relic_ashen_hourglass" in run.relics) 1 else 0) +
      (if ("remnant_hollow_ward" in run.remnants) 1 else 0) +
      (if (arenaTwist == ArenaTwist.VOLATILE_PRISMS) 1 else 0)
    val (seededBoardInit, paintDeckInit2, rngPaintInit) = BoardEngine.seedBoard(paintDeckInit, rngPaint1, startPrisms)
    var board = seededBoardInit
    var paintDeck = paintDeckInit2
    var rngPaint2 = rngPaintInit

    // Dread 2-3 opening board hazards (preserving guaranteed legal opening move)
    if (run.dreadLevel >= 2) {
      val (ashBoard, rAsh) = BoardEngine.paintTiles(board, TileKind.Ash, 2, rngPaint2)
      board = ashBoard
      rngPaint2 = rAsh
      if (run.dreadLevel >= 3) {
        val (stoneBoard, rStone) = BoardEngine.paintTiles(board, TileKind.Stone, 1, rngPaint2)
        board = stoneBoard
        rngPaint2 = rStone
      }
      val (safeBoard, safeDeck, rSafe, _) = BoardEngine.ensurePlayableSwap(board, paintDeck, rngPaint2, currentRunHue = null)
      board = safeBoard
      paintDeck = safeDeck
      rngPaint2 = rSafe
      val hazardDesc = if (run.dreadLevel >= 3) "+2 Ash & +1 Stone" else "+2 Ash"
      queue.enqueue(EventPriority.SYSTEM, "Dread ${run.dreadLevel} hazard: $hazardDesc seeded on board.", isAlert = true)
    }

    var (combatDeck, rngDeck1) = CombatDeck.startCombat(run.masterDeck, run.rng.combatDeck)
    if ("relic_vial_quicksilver" in run.relics) {
      val (extraDeck, rExtra) = CombatDeck.drawCards(combatDeck, 1, rngDeck1)
      combatDeck = extraDeck
      rngDeck1 = rExtra
      queue.enqueue(EventPriority.RELIC, "Vial of Quicksilver: +1 bonus card drawn on Turn 1.")
    }

    val shadeVigor = if (enemyDef.isShade) {
      CampaignGenerator.computeShadeVigorBonus(run.shadeDeckSnapshot, run.dreadLevel)
    } else 0
    val (slot1Pat, slot2Pat) = if (enemyDef.isShade) {
      CampaignGenerator.buildShadeIntentsFromDeck(run.shadeDeckSnapshot, run.currentAct, shadeVigor)
    } else {
      enemyDef.slot1Pattern to enemyDef.slot2Pattern
    }

    val scaledHp = CampaignGenerator.scaledEnemyHp(
      nodeIndex = node.row,
      act = run.currentAct,
      isElite = enemyDef.isElite,
      isBoss = enemyDef.isBoss,
      dreadLevel = run.dreadLevel,
      isShade = enemyDef.isShade,
      shadeDeckSize = run.shadeDeckSnapshot.size,
    )

    val enemy = EnemyState(
      defId = enemyDef.id,
      name = if (enemyDef.isShade) "The Shade (Row 5 Echo)" else enemyDef.name,
      act = run.currentAct,
      hp = scaledHp,
      maxHp = scaledHp,
      block = 0,
      isElite = enemyDef.isElite,
      isBoss = enemyDef.isBoss,
      isShade = enemyDef.isShade,
      patternStep = 0,
      slot1Intent = slot1Pat.first(),
      slot2Intent = slot2Pat.first(),
      shadeCardIds = if (enemyDef.isShade) run.shadeDeckSnapshot else emptyList(),
      shadeVigorBonus = shadeVigor,
    )

    val initialGuard = (if ("remnant_stone_memory" in run.remnants) 6 else 0) +
      (if ("relic_kiln_fired_bell" in run.relics) 4 else 0) +
      (if (run.growth.hasArtifact("art_bhasma_casket")) 3 else 0)
    val initialVigil = (if ("remnant_violet_veil" in run.remnants) 3 else 0) +
      (if ("relic_kiln_fired_bell" in run.relics) 3 else 0) +
      (if (run.growth.hasPassive("pass_iron_3")) 2 else 0) +
      (if (run.growth.hasArtifact("art_bhasma_casket")) 1 else 0)
    val initialMultTenths = TurnState.BASE_MULT_TENTHS +
      (if (run.growth.hasArtifact("art_tantraloka_folio")) 2 else 0)
    val initialSpark = if ("remnant_ember_echo" in run.remnants) 1 else 0
    var initialResources = ResourceState(spark = initialSpark)
    if ("remnant_tide_memory" in run.remnants) {
      initialResources = initialResources.addInk(Hue.TIDE, 1)
      queue.enqueue(EventPriority.RELIC, "Remnant of the Cistern: +1 starting Tide Ink.")
    }
    if ("relic_covenant_ring" in run.relics) {
      initialResources = initialResources.addInk(Hue.GOLD, 1).addInk(Hue.EMBER, 1)
      queue.enqueue(EventPriority.RELIC, "Covenant Ring: +1 Gold & +1 Ember Ink.")
    }
    val initialSpectrum = if ("relic_refracting_lens" in run.relics) {
      SpectrumState(currentHue = Hue.EMBER, runLength = 1, halfSteps = 3)
    } else {
      SpectrumState(currentHue = null, runLength = 0, halfSteps = 2)
    }

    if (enemy.isShade && run.shadeDeckSnapshot.isNotEmpty()) {
      val vigorNote = if (shadeVigor > 0) " [+$shadeVigor Vigor]" else ""
      queue.enqueue(
        EventPriority.SYSTEM,
        "The Shade manifests from your previous deck (${run.shadeDeckSnapshot.size} cards)$vigorNote!",
        isAlert = true,
      )
    } else {
      queue.enqueue(EventPriority.SYSTEM, "${enemy.name} engages — Phase: WEAVE (3 Swaps).")
    }
    if ("remnant_stone_memory" in run.remnants) queue.enqueue(EventPriority.RELIC, "Remnant of the Quarry: +6 starting Guard.")
    if ("remnant_violet_veil" in run.remnants) queue.enqueue(EventPriority.RELIC, "Remnant of the Choir: +3 starting Vigil.")
    if ("remnant_hollow_ward" in run.remnants) queue.enqueue(EventPriority.RELIC, "Remnant of the Lens: +1 extra opening Prism.")
    if ("relic_kiln_fired_bell" in run.relics) queue.enqueue(EventPriority.RELIC, "Kiln-Fired Bell: +4 Guard & +3 Vigil.")
    if (run.growth.hasPassive("pass_iron_3")) queue.enqueue(EventPriority.RELIC, "Unmeṣa-Vigil: +2 starting Vigil.")
    if (run.growth.hasArtifact("art_bhasma_casket")) queue.enqueue(EventPriority.RELIC, "Śmaśāna Bhasma Casket: +3 Guard & +1 Vigil.")
    if (initialSpark > 0) queue.enqueue(EventPriority.RELIC, "Remnant of the Ash: +$initialSpark starting Spark.")

    val encounterIllusion = MindIllusionState.forEncounter(run.runSeed, node.id)
    queue.enqueue(
      priority = EventPriority.SYSTEM,
      message = "Mind Illusion: ${encounterIllusion.veilTitle} (${encounterIllusion.sanskritName}) — Release attachment to ${encounterIllusion.hue.displayName} (${encounterIllusion.emotionName})!",
      kind = CombatEventKind.ILLUSION_SHIFT,
    )
    if (arenaTwist != ArenaTwist.NONE) {
      queue.enqueue(EventPriority.SYSTEM, "Arena Twist — ${arenaTwist.title}: ${arenaTwist.summary}")
    }

    val events = queue.drainSorted()
    val combat = CombatState(
      nodeId = node.id,
      board = board,
      paintDeck = paintDeck,
      combatDeck = combatDeck,
      enemy = enemy,
      turn = TurnState(
        turnNumber = 1,
        phase = TurnPhase.WEAVE,
        tapsRemaining = TurnState.TAPS_PER_TURN,
        firstTapUsedThisTurn = false,
        comboMultTenths = initialMultTenths,
      ),
      resources = initialResources,
      spectrum = initialSpectrum,
      mindIllusion = MindIllusionState(illusion = encounterIllusion),
      player = PlayerCombatState(
        guardBlock = initialGuard,
        vigil = initialVigil.coerceAtMost(PlayerCombatState.MAX_VIGIL),
      ),
      lastEvents = events,
      highestSpectrumThisCombat = initialSpectrum.halfSteps,
      arenaTwist = arenaTwist,
    )

    return checkGameOver(
      run.copy(
        rng = run.rng.copy(enemy = nextEnemyRng, boardPaint = rngPaint2, combatDeck = rngDeck1),
        combat = combat,
        dreadConsecutiveAshTurns = 0,
      )
    )
  }

  private fun handleTapCell(run: RunState, row: Int, col: Int): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val group = BoardEngine.groupAt(combat.board, row, col, combat.spectrum.currentHue)
    if (!group.isValid) {
      return CommandResult(run, false, CommandRejectionReason.INVALID_BOARD_GROUP)
    }

    val queue = BoundedEventQueue()
    val (effectiveHalfSteps, nextSpectrum) = BoardEngine.previewSpectrumForPop(
      current = combat.spectrum,
      resolvedHue = group.resolvedHue,
      usedPrism = group.prismCount > 0,
      isDirectAshGroup = group.isDirectAshGroup,
    )

    var nextEnemy = combat.enemy
    var nextHp = run.hp
    var nextPlayer = combat.player
    var nextResources = combat.resources
    var nextCombatDeck = combat.combatDeck
    var nextDeckRng = run.rng.combatDeck

    var spentVigilThisTap = false

    // ADR-010: Popping Branded blocks deducts 2 Vigil per block (shortfall from HP), unless Wickless Lantern negates first each turn
    if (group.brandVigilCost > 0) {
      if ("relic_wickless_lantern" in run.relics && !nextPlayer.firstBrandNegatedThisTurn) {
        nextPlayer = nextPlayer.copy(firstBrandNegatedThisTurn = true)
        queue.enqueue(EventPriority.RELIC, "Wickless Lantern: Negated Brand drain (-${group.brandVigilCost} Vigil).")
      } else {
        spentVigilThisTap = true
        val spentVigil = minOf(nextPlayer.vigil, group.brandVigilCost)
        val hpShortfall = group.brandVigilCost - spentVigil
        nextPlayer = nextPlayer.copy(vigil = nextPlayer.vigil - spentVigil)
        if (hpShortfall > 0) {
          nextHp = maxOf(0, nextHp - hpShortfall)
        }
        val msg = if (hpShortfall > 0) {
          "Branded blocks drained $spentVigil Vigil & $hpShortfall HP!"
        } else {
          "Branded blocks drained $spentVigil Vigil."
        }
        queue.enqueue(
          priority = EventPriority.STATUS,
          message = msg,
          isAlert = hpShortfall > 0,
          kind = CombatEventKind.BRAND_DRAIN,
        )
      }
    }

    // ADR-011: Fog decrements on each Tap
    if (nextPlayer.fogTapsRemaining > 0) {
      nextPlayer = nextPlayer.copy(fogTapsRemaining = nextPlayer.fogTapsRemaining - 1)
    }

    if (group.isDirectAshGroup) {
      val remnantBonus = if ("remnant_gilded_oath" in run.remnants) 1 else 0
      val rawAshChip = group.ashChipDamage + remnantBonus
      val (damagedEnemy, totalAshChip) = applyPlayerDamage(nextEnemy, rawAshChip, run.relics)
      nextEnemy = damagedEnemy
      queue.enqueue(
        priority = EventPriority.BOARD_POP,
        message = "Direct Ash tap (${group.clearedAsh.size}): $totalAshChip chip dmg (0 Ink).",
        kind = CombatEventKind.BOARD_POP,
        effectiveSpectrumHalfSteps = effectiveHalfSteps,
      )
    } else {
      val rawBoardDamage = (group.basePower * effectiveHalfSteps) / 2
      val (damagedEnemy, boardDamage) = applyPlayerDamage(nextEnemy, rawBoardDamage, run.relics)
      nextEnemy = damagedEnemy
      queue.enqueue(
        priority = EventPriority.BOARD_POP,
        message = "Tap ${group.coords.size} ${group.resolvedHue?.displayName ?: "Prism"} @ ${formatSpectrum(effectiveHalfSteps)} = $boardDamage dmg",
        kind = CombatEventKind.BOARD_POP,
        effectiveSpectrumHalfSteps = effectiveHalfSteps,
      )

      if (group.clearedAsh.isNotEmpty()) {
        val remnantBonus = if ("remnant_gilded_oath" in run.remnants) 1 else 0
        val rawAshChip = group.ashChipDamage + remnantBonus
        if (rawAshChip > 0) {
          val (ashEnemy, totalAshChip) = applyPlayerDamage(nextEnemy, rawAshChip, run.relics)
          nextEnemy = ashEnemy
          queue.enqueue(EventPriority.BOARD_POP, "Ash pop (${group.clearedAsh.size}): $totalAshChip chip dmg (0 Ink).")
        } else {
          queue.enqueue(EventPriority.BOARD_POP, "Ash pop (${group.clearedAsh.size}): 0 Ink, 0 chip (n-1).")
        }
      }

      if (group.inkHue != null && group.inkAmount > 0) {
        nextResources = nextResources.addInk(group.inkHue, group.inkAmount)
        queue.enqueue(EventPriority.BOARD_POP, "+${group.inkAmount} ${group.inkHue.displayName} Ink.")
      }

      // Ashen Monk Signature: Prayer Beads (+2 Ink on first Tap each turn)
      if ("relic_prayer_beads" in run.relics && !combat.turn.firstTapUsedThisTurn) {
        val beadHue = group.inkHue ?: Hue.EMBER
        nextResources = nextResources.addInk(beadHue, 2)
        queue.enqueue(EventPriority.RELIC, "Prayer Beads: First Tap grants +2 ${beadHue.displayName} Ink.")
      }
    }

    // Stained Loupe: First Prism popped each combat grants +2 bonus Ink of its resolved hue
    if (group.prismCount > 0 && "relic_stained_loupe" in run.relics && !nextPlayer.firstPrismInkUsedThisCombat) {
      val loupeHue = group.inkHue ?: group.resolvedHue ?: Hue.GOLD
      nextResources = nextResources.addInk(loupeHue, 2)
      nextPlayer = nextPlayer.copy(firstPrismInkUsedThisCombat = true)
      queue.enqueue(EventPriority.RELIC, "Stained Loupe: First Prism grants +2 ${loupeHue.displayName} Ink.")
    }

    // The Unlit Signature: Hollow Crown (+1 Spark per Ash block popped — applies on both direct & adjacent Ash!)
    if ("relic_hollow_crown" in run.relics && group.clearedAsh.isNotEmpty()) {
      val sparkGain = group.clearedAsh.size
      nextResources = nextResources.addSpark(sparkGain)
      queue.enqueue(EventPriority.RELIC, "Hollow Crown: +$sparkGain Spark from ${group.clearedAsh.size} Ash popped.")
    }

    // Ash-Glass Prism: Clearing 3+ Ash tiles in a single Tap grants +5 Guard
    if (group.clearedAsh.size >= 3 && "relic_ash_glass_prism" in run.relics) {
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 5)
      queue.enqueue(EventPriority.RELIC, "Ash-Glass Prism: +5 Guard from ${group.clearedAsh.size} Ash cleared.")
    }

    // ADR-007 Stone & Quarry Signet & Obsidian Trowel bonuses
    if (group.shatteredStone.isNotEmpty()) {
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + group.stoneDefenseBonus)
      queue.enqueue(EventPriority.BOARD_POP, "Shattered ${group.shatteredStone.size} Stone (+${group.stoneDefenseBonus} Guard).")
      if ("relic_obsidian_trowel" in run.relics) {
        val (trowelEnemy, trowelDmg) = applyPlayerDamage(nextEnemy, group.shatteredStone.size * 4, run.relics)
        nextEnemy = trowelEnemy
        queue.enqueue(EventPriority.RELIC, "Obsidian Trowel: Shattered Stone dealt $trowelDmg dmg.")
      }
    }
    if (!group.isDirectAshGroup && "relic_quarry_signet" in run.relics && group.coords.size >= 4) {
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 3)
      queue.enqueue(EventPriority.RELIC, "Quarry Signet: +3 Guard on 4+ group.")
    }

    // Spectrum Astrolabe: Once per turn, reaching 2.0x+ Spectrum grants +1 Spark and draws 1 card
    if (effectiveHalfSteps >= 4 && "relic_spectrum_astrolabe" in run.relics && !nextPlayer.spectrumAstrolabeTriggeredThisTurn) {
      nextResources = nextResources.addSpark(1)
      val (drawnDeck, rDraw) = CombatDeck.drawCards(nextCombatDeck, 1, nextDeckRng)
      nextCombatDeck = drawnDeck
      nextDeckRng = rDraw
      nextPlayer = nextPlayer.copy(spectrumAstrolabeTriggeredThisTurn = true)
      queue.enqueue(EventPriority.RELIC, "Spectrum Astrolabe: 2.0x+ Spectrum grants +1 Spark & draws 1 card!")
    }

    val allConsumed = group.coords + group.clearedAsh + group.shatteredStone
    val cascade = BoardEngine.consumeAndCascade(
      board = combat.board,
      consumedCoords = allConsumed,
      paintDeck = combat.paintDeck,
      rng = run.rng.boardPaint,
      currentRunHue = nextSpectrum.currentHue,
    )
    if (cascade.didReshuffle) {
      queue.enqueue(EventPriority.SYSTEM, "Board reshuffled via Paint Deck to restore a legal move.")
    }

    val remainingTaps = combat.tapsRemaining - 1
    val nextPhase = if (remainingTaps == 0) TurnPhase.CAST else TurnPhase.WEAVE
    if (nextPhase == TurnPhase.CAST) {
      queue.enqueue(EventPriority.SYSTEM, "3 Taps spent — entering CAST phase.")
    }

    val ashPopped = combat.ashPoppedThisTurn || group.isDirectAshGroup || group.clearedAsh.isNotEmpty()
    val nonAshPopped = combat.nonAshPoppedThisTurn || !group.isDirectAshGroup
    val vigilSpent = combat.vigilSpentThisTurn || spentVigilThisTap
    val nextDreadStreak = if (nonAshPopped || vigilSpent) 0 else run.dreadConsecutiveAshTurns

    val sortedEvents = queue.drainSorted()
    val updatedCombat = combat.copy(
      board = cascade.board,
      paintDeck = cascade.paintDeck,
      combatDeck = nextCombatDeck,
      enemy = nextEnemy,
      turn = combat.turn.copy(
        phase = nextPhase,
        tapsRemaining = remainingTaps,
        firstTapUsedThisTurn = true,
      ),
      resources = nextResources,
      spectrum = nextSpectrum,
      player = nextPlayer,
      lastEvents = sortedEvents,
      highestSpectrumThisCombat = maxOf(combat.highestSpectrumThisCombat, effectiveHalfSteps),
      ashPoppedThisTurn = ashPopped,
      nonAshPoppedThisTurn = nonAshPopped,
      vigilSpentThisTurn = vigilSpent,
    )

    val updatedStats = run.stats.copy(
      highestSpectrumHalfSteps = maxOf(run.stats.highestSpectrumHalfSteps, effectiveHalfSteps),
      prismsDetonated = run.stats.prismsDetonated + group.prismCount,
      ashBlocksPopped = run.stats.ashBlocksPopped + group.clearedAsh.size,
      stonesShattered = run.stats.stonesShattered + group.shatteredStone.size,
    )

    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = cascade.rng, combatDeck = nextDeckRng),
        hp = nextHp,
        combat = updatedCombat,
        dreadConsecutiveAshTurns = nextDreadStreak,
        stats = updatedStats,
      )
    )
    return CommandResult(finalRun, accepted = true, events = sortedEvents)
  }

  /**
   * True Match-3 Swap & Multi-Wave Auto-Cascade handler:
   * - Swaps two orthogonally adjacent tiles if they align 3+ matching tiles horizontally/vertically
   *   (or involve a Prism).
   * - Immediately clears matched tiles, drops tiles via gravity + PaintDeck refill, and automatically
   *   cascades any newly formed 3+ lines (with +25% bonus power per cascade wave) until ZERO 3+ lines remain.
   * - Guarantees a playable Match-3 swap remains after cascades settle.
   */
  private fun handleSwapCells(run: RunState, r1: Int, c1: Int, r2: Int, c2: Int): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val initialWave = BoardEngine.previewSwap(combat.board, r1, c1, r2, c2, combat.spectrum.currentHue)
    if (!initialWave.isValid) {
      return CommandResult(run, false, CommandRejectionReason.NO_MATCH_FOR_SWAP)
    }

    val queue = BoundedEventQueue()
    var currentBoard = BoardEngine.swapTilesInBoard(combat.board, r1, c1, r2, c2)
    var currentPaintDeck = combat.paintDeck
    var currentBoardRng = run.rng.boardPaint
    var nextEnemy = combat.enemy
    var nextHp = run.hp
    var nextPlayer = combat.player
    var nextResources = combat.resources
    var nextCombatDeck = combat.combatDeck
    var nextDeckRng = run.rng.combatDeck
    var nextSpectrum = combat.spectrum

    val twist = combat.arenaTwist
    val growth = run.growth
    var spentVigilThisMove = false
    var anyAshCleared = false
    var anyNonAshCleared = false
    var totalPrisms = 0
    var totalAsh = 0
    var totalStones = 0
    var maxHalfSteps = combat.highestSpectrumThisCombat
    var waveIndex = 1
    var activeWave: SwapWaveResolution? = initialWave
    val earnedFourMatch = !initialWave.isDirectAshOnly && initialWave.coords.size >= 4
    var forgedPrismThisSwap = false
    val nextMovesMade = combat.turn.movesMadeThisTurn + 1
    var currentMultTenths = combat.turn.comboMultTenths
    var currentLastHue = combat.turn.lastMatchedHue
    var cascadesCount = combat.turn.cascadesThisTurn
    val scoringSteps = mutableListOf<ScoringStep>()
    var totalLedgerHeat = 0
    var totalLedgerOutput = 0
    var nextMind = combat.mindIllusion
    var peeledVeilThisSwap = false
    val prevHueBeforeSwap = combat.turn.lastMatchedHue

    // Consume any Śmaśāna-Bhasma detached Spanda banked from conscious Witnessing
    var pendingDetachedSpanda = nextMind.detachedBonusSpanda
    if (pendingDetachedSpanda > 0) {
      nextMind = nextMind.copy(detachedBonusSpanda = 0)
    }

    // Tantric Artifact: Nāda-Ḍamaru beats on every 2nd move in a turn (+0.4× Vimarśa MULT & +8 Skill charge)
    if (growth.hasArtifact("art_nada_damaru") && nextMovesMade % 2 == 0) {
      currentMultTenths += 4
      nextPlayer = nextPlayer.copy(
        overdriveCharge = (nextPlayer.overdriveCharge + 8).coerceAtMost(PlayerCombatState.MAX_OVERDRIVE),
      )
      scoringSteps.add(ScoringStep("Nāda-Ḍamaru Beat", multAddTenths = 4))
      queue.enqueue(EventPriority.RELIC, "Nāda-Ḍamaru beats: +0.4× Vimarśa MULT & +8 Skill charge!")
    }

    while (activeWave != null && waveIndex <= 12) {
      val wave = activeWave
      val prevHalfSteps = nextSpectrum.halfSteps
      val (effectiveHalfSteps, updatedSpectrum) = BoardEngine.previewSpectrumForPop(
        current = nextSpectrum,
        resolvedHue = wave.primaryHue,
        usedPrism = wave.prismCount > 0,
        isDirectAshGroup = wave.isDirectAshOnly,
      )
      nextSpectrum = updatedSpectrum
      maxHalfSteps = maxOf(maxHalfSteps, effectiveHalfSteps)
      val spectrumAdvanced = effectiveHalfSteps > prevHalfSteps

      if (waveIndex >= 2) {
        cascadesCount++
        if (growth.hasPassive("pass_escape_1")) {
          currentMultTenths += 2
          scoringSteps.add(ScoringStep("Spanda-Tremor", multAddTenths = 2))
        }
      }
      if (spectrumAdvanced && growth.hasPassive("pass_escape_2")) {
        currentMultTenths += 3
        scoringSteps.add(ScoringStep("Pañcakṛtya Spur", multAddTenths = 3))
      }
      if (spectrumAdvanced && growth.hasArtifact("art_sphatika_mala") && wave.primaryHue != null) {
        if (nextHp > 0) nextHp = (nextHp + 1).coerceAtMost(run.maxHp)
        nextResources = nextResources.addInk(wave.primaryHue, 1)
        queue.enqueue(EventPriority.RELIC, "Sphaṭika Mālā: +1 HP & +1 ${wave.primaryHue.displayName} Ink.")
      }

      // Signature Skill (Prāṇa-Mudrā) charge gain on deliberate primary swap wave
      if (waveIndex == 1) {
        var overdriveGain = 12 +
          (if (wave.coords.size >= 4) 18 else 0) +
          (if (spectrumAdvanced) 8 else 0)
        if (wave.coords.size >= 4 && growth.hasPassive("pass_foundry_2")) {
          overdriveGain += 6
        }
        if (wave.coords.size >= 4 && growth.hasArtifact("art_tamra_yantra")) {
          val yantraCharge = growth.socketedSupportIds.size * 4
          overdriveGain += yantraCharge
          currentMultTenths += 5
          scoringSteps.add(ScoringStep("Tāmra Śrī-Yantra", multAddTenths = 5))
        }
        if (growth.hasSupport("sup_hue_resonance") && wave.primaryHue == run.heroClass.primaryHue) {
          overdriveGain = (overdriveGain * 5) / 4
        }
        if (twist == ArenaTwist.SPECTRUM_SURGE && spectrumAdvanced) {
          overdriveGain += 8
        }
        if (twist == ArenaTwist.EMBER_UPDRAFT && (wave.primaryHue == Hue.EMBER || wave.primaryHue == Hue.GOLD)) {
          overdriveGain += 6
        }
        nextPlayer = nextPlayer.copy(
          overdriveCharge = (nextPlayer.overdriveCharge + overdriveGain).coerceAtMost(PlayerCombatState.MAX_OVERDRIVE),
        )
      }

      // ADR-010: Branded tiles drain 2 Vigil (HP shortfall) unless Wickless Lantern negates first each turn
      if (wave.brandVigilCost > 0) {
        if ("relic_wickless_lantern" in run.relics && !nextPlayer.firstBrandNegatedThisTurn) {
          nextPlayer = nextPlayer.copy(firstBrandNegatedThisTurn = true)
          queue.enqueue(EventPriority.RELIC, "Wickless Lantern: Negated Brand drain (-${wave.brandVigilCost} Vigil).")
        } else {
          spentVigilThisMove = true
          val spentVigil = minOf(nextPlayer.vigil, wave.brandVigilCost)
          val hpShortfall = wave.brandVigilCost - spentVigil
          nextPlayer = nextPlayer.copy(vigil = nextPlayer.vigil - spentVigil)
          if (hpShortfall > 0) {
            nextHp = maxOf(0, nextHp - hpShortfall)
          }
          val msg = if (hpShortfall > 0) {
            "Branded blocks drained $spentVigil Vigil & $hpShortfall HP!"
          } else {
            "Branded blocks drained $spentVigil Vigil."
          }
          queue.enqueue(
            priority = EventPriority.STATUS,
            message = msg,
            isAlert = hpShortfall > 0,
            kind = CombatEventKind.BRAND_DRAIN,
          )
        }
      }

      val wavePrefix = if (waveIndex == 1) "Match" else "CASCADE ×$waveIndex!"
      if (wave.isDirectAshOnly) {
        anyAshCleared = true
        val remnantBonus = if ("remnant_gilded_oath" in run.remnants) 1 else 0
        val casketBonus = if (growth.hasArtifact("art_bhasma_casket")) 2 else 0
        val rawAshChip = wave.ashChipDamage + remnantBonus + casketBonus
        if (growth.hasPassive("pass_iron_key")) {
          if (nextHp > 0) nextHp = (nextHp + 2).coerceAtMost(run.maxHp)
          currentMultTenths += 3
          scoringSteps.add(ScoringStep("Śmaśāna-Bhasma", multAddTenths = 3))
        }
        val (damagedEnemy, totalAshChip) = applyPlayerDamage(nextEnemy, rawAshChip, run.relics)
        nextEnemy = damagedEnemy
        totalLedgerHeat += rawAshChip
        totalLedgerOutput += totalAshChip
        queue.enqueue(
          priority = EventPriority.BOARD_POP,
          message = "$wavePrefix ${wave.clearedAsh.size} Ash: $totalAshChip chip dmg (0 Ink).",
          kind = CombatEventKind.BOARD_POP,
          effectiveSpectrumHalfSteps = effectiveHalfSteps,
        )
      } else {
        anyNonAshCleared = true
        var waveSpanda = if (waveIndex == 1) {
          wave.basePower
        } else {
          maxOf(1, (wave.basePower + 1) / 2)
        }

        // Earned Upāya Passives & Tantric Items add Spanda / Vimarśa MULT on primary swap waves (0 bonus at humble Level 1!)
        if (waveIndex == 1 && growth.hasPassive("pass_foundry_1")) {
          waveSpanda += 2
          scoringSteps.add(ScoringStep("Prāṇa-Vāyu", heatDelta = 2))
        }
        if (waveIndex == 1 && wave.coords.size >= 4 && growth.hasPassive("pass_foundry_2")) {
          waveSpanda += 3
          scoringSteps.add(ScoringStep("Bhasma-Lepa", heatDelta = 3))
        }
        if (waveIndex == 1 && wave.coords.size >= 4 && growth.hasPassive("pass_iron_2")) {
          nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 2)
          queue.enqueue(EventPriority.RELIC, "Khecarī-Mudrā: 4+ match sealed +2 Guard.")
        }
        if (waveIndex == 1 && growth.hasArtifact("art_rudraksha_mala") && growth.unlockedPassiveIds.isNotEmpty()) {
          val rudraBonus = minOf(4, growth.unlockedPassiveIds.size)
          waveSpanda += rudraBonus
          scoringSteps.add(ScoringStep("Rudrākṣa Mālā", heatDelta = rudraBonus))
        }
        // Keystone: Prakāśa–Vimarśa (alternating Hues on consecutive moves!)
        if (waveIndex == 1 && wave.primaryHue != null && currentLastHue != null &&
          wave.primaryHue != currentLastHue && growth.hasPassive("pass_escape_key")
        ) {
          waveSpanda += 4
          currentMultTenths += 5
          scoringSteps.add(ScoringStep("Prakāśa–Vimarśa", heatDelta = 4, multAddTenths = 5))
          queue.enqueue(EventPriority.RELIC, "Prakāśa–Vimarśa Mirror: +4 Spanda & +0.5× Vimarśa MULT!")
        }
        if (waveIndex == 1 && pendingDetachedSpanda > 0) {
          waveSpanda += pendingDetachedSpanda
          scoringSteps.add(ScoringStep("Vairāgya Clarity", heatDelta = pendingDetachedSpanda))
          pendingDetachedSpanda = 0
        }
        // Emotional Attachment (Rāga) & Mind-Illusion dynamics on primary wave
        if (waveIndex == 1 && wave.primaryHue != null && !nextMind.shattered) {
          val hitIllusionHue = wave.primaryHue == nextMind.illusion.hue
          val repeatedSameHue = prevHueBeforeSwap != null && wave.primaryHue == prevHueBeforeSwap
          if (hitIllusionHue || repeatedSameHue) {
            val delta = if (hitIllusionHue && repeatedSameHue) 3 else if (hitIllusionHue) 2 else 1
            val rawAttachment = nextMind.attachment + delta
            if (rawAttachment >= MindIllusionState.MAX_ATTACHMENT) {
              // VIKALPA COLLAPSE: Attachment overwhelms the mind from within!
              nextHp = maxOf(0, nextHp - 4)
              val (ashBoard, rAsh) = BoardEngine.paintTiles(currentBoard, TileKind.Ash, 1, currentBoardRng)
              currentBoard = ashBoard
              currentBoardRng = rAsh
              nextMind = nextMind.copy(attachment = 4)
              queue.enqueue(
                priority = EventPriority.STATUS,
                message = "VIKALPA COLLAPSE! Attachment to ${nextMind.illusion.emotionName} recoiled for 4 HP & 1 Ash mist!",
                isAlert = true,
                kind = CombatEventKind.ILLUSION_SHIFT,
              )
            } else {
              nextMind = nextMind.copy(attachment = rawAttachment)
              if (rawAttachment >= MindIllusionState.ENTANGLED_THRESHOLD) {
                queue.enqueue(
                  priority = EventPriority.STATUS,
                  message = "MĀYĀ GRIP (${rawAttachment}/10): Clinging to ${nextMind.illusion.emotionName} amplifies incoming blows!",
                  isAlert = true,
                  kind = CombatEventKind.ILLUSION_SHIFT,
                )
              }
            }
          } else {
            // Non-Attached Flow: moving across a different emotion dissolves clinging!
            val release = if (spectrumAdvanced) 3 else 2
            val newAtt = (nextMind.attachment - release).coerceAtLeast(0)
            val newClarity = nextMind.clarity + 1
            nextMind = nextMind.copy(attachment = newAtt, clarity = newClarity)
            if (spectrumAdvanced && effectiveHalfSteps >= 3 && newAtt <= 4 && !peeledVeilThisSwap) {
              peeledVeilThisSwap = true
              val nextLayers = (nextMind.veilLayers - 1).coerceAtLeast(0)
              if (nextLayers == 0) {
                val mirrorHeld = growth.hasArtifact("art_kashmiri_darpana")
                val shatterBase = if (mirrorHeld) 24 else 12
                val shatterDmgRaw = (shatterBase * currentMultTenths) / 10
                nextEnemy = nextEnemy.copy(block = 0)
                val (shatteredEnemy, dealtShatter) = applyPlayerDamage(nextEnemy, shatterDmgRaw, run.relics)
                nextEnemy = shatteredEnemy
                if (mirrorHeld) nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 4)
                currentMultTenths += 10
                totalLedgerOutput += dealtShatter
                nextMind = nextMind.copy(veilLayers = 0, shattered = true, attachment = 0)
                scoringSteps.add(ScoringStep("Pratyabhijñā!", heatDelta = shatterBase, multAddTenths = 10))
                queue.enqueue(
                  priority = EventPriority.STATUS,
                  message = "PRATYABHIJÑĀ! ${nextMind.illusion.veilTitle} SHATTERED — armor broken, $dealtShatter clarity dmg & +1.0× Vimarśa!",
                  isAlert = true,
                  kind = CombatEventKind.ILLUSION_SHATTERED,
                )
              } else {
                nextMind = nextMind.copy(veilLayers = nextLayers)
                queue.enqueue(
                  priority = EventPriority.STATUS,
                  message = "Non-Attached Flow pierced ${nextMind.illusion.veilTitle} ($nextLayers/${MindIllusionState.MAX_VEIL_LAYERS} veils left).",
                  kind = CombatEventKind.ILLUSION_SHIFT,
                )
              }
            }
          }
        }
        if (waveIndex == 1 && wave.primaryHue != null) {
          currentLastHue = wave.primaryHue
        }

        var rawBoardDamage = (waveSpanda * effectiveHalfSteps) / 2
        if (twist == ArenaTwist.EMBER_UPDRAFT && (wave.primaryHue == Hue.EMBER || wave.primaryHue == Hue.GOLD)) {
          rawBoardDamage = (rawBoardDamage * 135) / 100
        }
        if (twist == ArenaTwist.VOLATILE_PRISMS && wave.prismCount > 0) {
          rawBoardDamage += wave.prismCount * 2
        }
        // Apply earned Vimarśa MULT (10 = 1.0x at humble Lv 1 -> zero drift on base math!)
        val finalBoardDamage = if (currentMultTenths > TurnState.BASE_MULT_TENTHS) {
          (rawBoardDamage * currentMultTenths) / 10
        } else {
          rawBoardDamage
        }
        val (damagedEnemy, boardDamage) = applyPlayerDamage(nextEnemy, finalBoardDamage, run.relics)
        nextEnemy = damagedEnemy
        totalLedgerHeat += rawBoardDamage
        totalLedgerOutput += boardDamage
        val multNote = if (currentMultTenths > TurnState.BASE_MULT_TENTHS) {
          " × ${currentMultTenths / 10}.${currentMultTenths % 10}×"
        } else ""
        queue.enqueue(
          priority = EventPriority.BOARD_POP,
          message = "$wavePrefix ${wave.coords.size} ${wave.primaryHue?.displayName ?: "Prism"} @ ${formatSpectrum(effectiveHalfSteps)}$multNote = $boardDamage dmg",
          kind = CombatEventKind.BOARD_POP,
          effectiveSpectrumHalfSteps = effectiveHalfSteps,
        )

        // Arena Twist: Blood Kiln (4+ match or cascade heals +3 HP & deals +4 bonus dmg)
        if (twist == ArenaTwist.BLOOD_KILN && (wave.coords.size >= 4 || waveIndex >= 2)) {
          val (kilnEnemy, kilnDmg) = applyPlayerDamage(nextEnemy, 4, run.relics)
          nextEnemy = kilnEnemy
          if (nextHp > 0) {
            nextHp = (nextHp + 3).coerceAtMost(run.maxHp)
          }
          queue.enqueue(EventPriority.STATUS, "Blood Kiln: +3 HP & +$kilnDmg bonus sear!")
        }

        if (wave.clearedAsh.isNotEmpty()) {
          anyAshCleared = true
          if (growth.hasPassive("pass_iron_key")) {
            if (nextHp > 0) nextHp = (nextHp + 2).coerceAtMost(run.maxHp)
            currentMultTenths += 3
            scoringSteps.add(ScoringStep("Śmaśāna-Bhasma", multAddTenths = 3))
          }
          val remnantBonus = if ("remnant_gilded_oath" in run.remnants) 1 else 0
          val casketBonus = if (growth.hasArtifact("art_bhasma_casket")) 2 else 0
          val rawAshChip = wave.ashChipDamage + remnantBonus + casketBonus
          if (rawAshChip > 0) {
            val (ashEnemy, totalAshChip) = applyPlayerDamage(nextEnemy, rawAshChip, run.relics)
            nextEnemy = ashEnemy
            queue.enqueue(EventPriority.BOARD_POP, "Ash cleared (${wave.clearedAsh.size}): $totalAshChip chip dmg (0 Ink).")
          } else {
            queue.enqueue(EventPriority.BOARD_POP, "Ash cleared (${wave.clearedAsh.size}): 0 Ink, 0 chip.")
          }
        }

        // Award colored Ink for every hue matched in this wave
        for (hue in Hue.WHEEL_ORDER) {
          val inkAmt = wave.inkForHue(hue)
          if (inkAmt > 0) {
            nextResources = nextResources.addInk(hue, inkAmt)
            queue.enqueue(EventPriority.BOARD_POP, "+$inkAmt ${hue.displayName} Ink.")
          }
        }

        // Arena Twist: Spectrum Surge (+1 bonus Ink on wheel advance)
        if (twist == ArenaTwist.SPECTRUM_SURGE && spectrumAdvanced && wave.primaryHue != null) {
          nextResources = nextResources.addInk(wave.primaryHue, 1)
          queue.enqueue(EventPriority.STATUS, "Spectrum Surge: +1 bonus ${wave.primaryHue.displayName} Ink!")
        }

        // Ashen Monk Signature: Prayer Beads (+2 Ink on first Move each turn)
        if (waveIndex == 1 && "relic_prayer_beads" in run.relics && !combat.turn.firstTapUsedThisTurn) {
          val beadHue = wave.primaryHue ?: Hue.EMBER
          nextResources = nextResources.addInk(beadHue, 2)
          queue.enqueue(EventPriority.RELIC, "Prayer Beads: First Move grants +2 ${beadHue.displayName} Ink.")
        }
      }

      // Stained Loupe: First Prism popped each combat grants +2 bonus Ink
      if (wave.prismCount > 0 && "relic_stained_loupe" in run.relics && !nextPlayer.firstPrismInkUsedThisCombat) {
        val loupeHue = wave.primaryHue ?: Hue.GOLD
        nextResources = nextResources.addInk(loupeHue, 2)
        nextPlayer = nextPlayer.copy(firstPrismInkUsedThisCombat = true)
        queue.enqueue(EventPriority.RELIC, "Stained Loupe: First Prism grants +2 ${loupeHue.displayName} Ink.")
      }

      // The Unlit Signature: Hollow Crown (+1 Spark per Ash block popped)
      if ("relic_hollow_crown" in run.relics && wave.clearedAsh.isNotEmpty()) {
        val sparkGain = wave.clearedAsh.size
        nextResources = nextResources.addSpark(sparkGain)
        queue.enqueue(EventPriority.RELIC, "Hollow Crown: +$sparkGain Spark from ${wave.clearedAsh.size} Ash popped.")
      }

      // Ash-Glass Prism: Clearing 3+ Ash tiles in a wave grants +5 Guard
      if (wave.clearedAsh.size >= 3 && "relic_ash_glass_prism" in run.relics) {
        nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 5)
        queue.enqueue(EventPriority.RELIC, "Ash-Glass Prism: +5 Guard from ${wave.clearedAsh.size} Ash cleared.")
      }

      // Arena Twist: Brittle Quarry lets even 3-matches crack orthogonally adjacent Stone!
      val brittleExtraStones = if (twist == ArenaTwist.BRITTLE_QUARRY && !wave.isDirectAshOnly) {
        findAdjacentStones(currentBoard, wave.coords) - wave.shatteredStone
      } else {
        emptySet()
      }
      val allShatteredStones = wave.shatteredStone + brittleExtraStones

      // ADR-007 Stone & Quarry Signet & Obsidian Trowel bonuses
      if (allShatteredStones.isNotEmpty()) {
        val stoneGuard = wave.stoneDefenseBonus + brittleExtraStones.size * 3
        nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + stoneGuard)
        queue.enqueue(EventPriority.BOARD_POP, "Shattered ${allShatteredStones.size} Stone (+$stoneGuard Guard).")
        if (twist == ArenaTwist.BRITTLE_QUARRY) {
          val (brittleEnemy, brittleDmg) = applyPlayerDamage(nextEnemy, allShatteredStones.size * 5, run.relics)
          nextEnemy = brittleEnemy
          queue.enqueue(EventPriority.STATUS, "Brittle Quarry: Cracked Stone dealt $brittleDmg dmg!")
        }
        if ("relic_obsidian_trowel" in run.relics) {
          val (trowelEnemy, trowelDmg) = applyPlayerDamage(nextEnemy, allShatteredStones.size * 4, run.relics)
          nextEnemy = trowelEnemy
          queue.enqueue(EventPriority.RELIC, "Obsidian Trowel: Shattered Stone dealt $trowelDmg dmg.")
        }
      }
      if (!wave.isDirectAshOnly && "relic_quarry_signet" in run.relics && wave.coords.size >= 4) {
        nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 3)
        queue.enqueue(EventPriority.RELIC, "Quarry Signet: +3 Guard on 4+ match.")
      }

      // Spectrum Astrolabe: Once per turn, reaching 2.0x+ Spectrum grants +1 Spark & draws 1 card
      if (effectiveHalfSteps >= 4 && "relic_spectrum_astrolabe" in run.relics && !nextPlayer.spectrumAstrolabeTriggeredThisTurn) {
        nextResources = nextResources.addSpark(1)
        val (drawnDeck, rDraw) = CombatDeck.drawCards(nextCombatDeck, 1, nextDeckRng)
        nextCombatDeck = drawnDeck
        nextDeckRng = rDraw
        nextPlayer = nextPlayer.copy(spectrumAstrolabeTriggeredThisTurn = true)
        queue.enqueue(EventPriority.RELIC, "Spectrum Astrolabe: 2.0x+ Spectrum grants +1 Spark & draws 1 card!")
      }

      totalPrisms += wave.prismCount
      totalAsh += wave.clearedAsh.size
      totalStones += allShatteredStones.size

      val allConsumed = wave.coords + wave.clearedAsh + allShatteredStones
      val stepped = BoardEngine.stepConsumeWithoutEnsure(
        board = currentBoard,
        consumedCoords = allConsumed,
        paintDeck = currentPaintDeck,
        rng = currentBoardRng,
      )
      currentBoard = stepped.board
      currentPaintDeck = stepped.paintDeck
      currentBoardRng = stepped.rng

      waveIndex++
      activeWave = BoardEngine.resolveBoardMatches(currentBoard, nextSpectrum.currentHue).takeIf { it.isValid }
    }

    // Skillful Match-3 Payoff #1: 4+ Match forges a glowing Prism right on the swap anchor!
    if (earnedFourMatch && !currentBoard.isColumnSealed(c2)) {
      val forged = forgePrismAt(currentBoard, r2, c2)
      if (forged != null) {
        currentBoard = forged
        forgedPrismThisSwap = true
        queue.enqueue(
          priority = EventPriority.BOARD_POP,
          message = "4+ MATCH FORGED A PRISM!",
          kind = CombatEventKind.PRISM_FORGED,
        )
      }
    }

    // ADR-011: Fog decrements on each Move
    if (nextPlayer.fogTapsRemaining > 0) {
      nextPlayer = nextPlayer.copy(fogTapsRemaining = nextPlayer.fogTapsRemaining - 1)
    }

    val ensured = BoardEngine.ensurePlayableSwap(
      board = currentBoard,
      paintDeck = currentPaintDeck,
      rng = currentBoardRng,
      currentRunHue = nextSpectrum.currentHue,
    )
    currentBoard = ensured.board
    currentPaintDeck = ensured.paintDeck
    currentBoardRng = ensured.rng
    if (ensured.didReshuffle) {
      queue.enqueue(EventPriority.SYSTEM, "Board reshuffled via Paint Deck to restore a legal swap.")
    }

    // Skillful Match-3 Payoff #2: 4+ Match OR 2+ Cascade earns +1 FREE MOVE ("EMBER SURGE", max 2/turn)!
    val totalWavesCleared = waveIndex - 1
    val earnedBonusMove = (earnedFourMatch || totalWavesCleared >= 2) &&
      !initialWave.isDirectAshOnly &&
      combat.turn.bonusMovesGrantedThisTurn < TurnState.MAX_BONUS_MOVES_PER_TURN
    val nextBonusMovesGranted = combat.turn.bonusMovesGrantedThisTurn + (if (earnedBonusMove) 1 else 0)
    val remainingTaps = if (earnedBonusMove) {
      queue.enqueue(
        priority = EventPriority.BOARD_POP,
        message = if (forgedPrismThisSwap) "EMBER SURGE! +1 Free Move & Prism Forged!" else "EMBER SURGE! +1 Free Move!",
        kind = CombatEventKind.BONUS_MOVE,
      )
      combat.tapsRemaining
    } else {
      combat.tapsRemaining - 1
    }

    val nextPhase = if (remainingTaps == 0) TurnPhase.CAST else TurnPhase.WEAVE
    if (nextPhase == TurnPhase.CAST) {
      queue.enqueue(EventPriority.SYSTEM, "Moves spent — entering CAST phase.")
    }

    val ashPopped = combat.ashPoppedThisTurn || anyAshCleared
    val nonAshPopped = combat.nonAshPoppedThisTurn || anyNonAshCleared
    val vigilSpent = combat.vigilSpentThisTurn || spentVigilThisMove
    val nextDreadStreak = if (nonAshPopped || vigilSpent) 0 else run.dreadConsecutiveAshTurns

    val swapBreakdown = ScoringBreakdown(
      sourceTitle = if (totalWavesCleared >= 2) "Cascade ×$totalWavesCleared" else (initialWave.primaryHue?.displayName ?: "Board Move"),
      totalHeat = totalLedgerHeat,
      finalMultTenths = currentMultTenths,
      finalOutput = totalLedgerOutput,
      steps = scoringSteps.take(6),
    )

    val sortedEvents = queue.drainSorted()
    val updatedCombat = combat.copy(
      board = currentBoard,
      paintDeck = currentPaintDeck,
      combatDeck = nextCombatDeck,
      enemy = nextEnemy,
      turn = combat.turn.copy(
        phase = nextPhase,
        tapsRemaining = remainingTaps,
        firstTapUsedThisTurn = true,
        bonusMovesGrantedThisTurn = nextBonusMovesGranted,
        comboMultTenths = currentMultTenths,
        lastMatchedHue = currentLastHue,
        cascadesThisTurn = cascadesCount,
        movesMadeThisTurn = nextMovesMade,
      ),
      resources = nextResources,
      spectrum = nextSpectrum,
      player = nextPlayer,
      lastEvents = sortedEvents,
      highestSpectrumThisCombat = maxHalfSteps,
      ashPoppedThisTurn = ashPopped,
      nonAshPoppedThisTurn = nonAshPopped,
      vigilSpentThisTurn = vigilSpent,
      lastScoringBreakdown = swapBreakdown,
      mindIllusion = nextMind,
    )

    val updatedStats = run.stats.copy(
      highestSpectrumHalfSteps = maxOf(run.stats.highestSpectrumHalfSteps, maxHalfSteps),
      prismsDetonated = run.stats.prismsDetonated + totalPrisms,
      ashBlocksPopped = run.stats.ashBlocksPopped + totalAsh,
      stonesShattered = run.stats.stonesShattered + totalStones,
    )

    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = currentBoardRng, combatDeck = nextDeckRng),
        hp = nextHp,
        combat = updatedCombat,
        dreadConsecutiveAshTurns = nextDreadStreak,
        stats = updatedStats,
      )
    )
    return CommandResult(finalRun, accepted = true, events = sortedEvents)
  }

  private fun findAdjacentStones(board: BoardState, coords: Set<BoardCoord>): Set<BoardCoord> {
    val result = mutableSetOf<BoardCoord>()
    val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    for (coord in coords) {
      for ((dr, dc) in dirs) {
        val nr = coord.row + dr
        val nc = coord.col + dc
        if (nr in 0 until board.rows && nc in 0 until board.cols && !board.isColumnSealed(nc)) {
          if (board.tileAt(nr, nc)?.kind is TileKind.Stone) {
            result.add(BoardCoord(nr, nc))
          }
        }
      }
    }
    return result
  }

  private fun forgePrismAt(board: BoardState, row: Int, col: Int): BoardState? {
    if (row !in 0 until board.rows || col !in 0 until board.cols || board.isColumnSealed(col)) return null
    val current = board.tileAt(row, col) ?: return null
    if (current.kind is TileKind.Empty || current.kind is TileKind.Prism || current.kind is TileKind.Stone) return null
    val nextGrid = board.grid.mapIndexed { r, rowTiles ->
      if (r != row) rowTiles
      else rowTiles.mapIndexed { c, tile ->
        if (c != col) tile else tile.copy(kind = TileKind.Prism, isBranded = false)
      }
    }
    return board.copy(grid = nextGrid)
  }

  /**
   * PoE2-inspired Evolving Signature Skill (100% charge):
   * Starts humble at Level 1 (0 sockets filled: Prāṇa-Mudrā 8 Spanda, Quarry Chip 7 Spanda,
   * Hollow Spark 7 Spanda) and transforms dramatically as the player sockets Dhāraṇā Support
   * Engravings and accumulates Upāya Passives & Shaiva Tantric Items!
   */
  private fun handleTriggerOverdrive(run: RunState): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val queue = BoundedEventQueue()
    val growth = run.growth
    val skillDef = ProgressionCatalog.signatureSkillFor(run.heroClass)
    var nextBoard = combat.board
    var nextPaintDeck = combat.paintDeck
    var nextBoardRng = run.rng.boardPaint
    var nextEnemy = combat.enemy
    var nextResources = combat.resources
    var nextPlayer = combat.player.copy(overdriveCharge = 0)
    var nextHp = run.hp
    var totalAsh = 0
    var totalStones = 0

    var spandaHeat = skillDef.baseHeat
    var multTenths = combat.turn.comboMultTenths
    val steps = mutableListOf(ScoringStep(skillDef.name, heatDelta = skillDef.baseHeat))

    // 1. Humble Class Base Resonance (modest at Level 1; earned growth replaces early godmode!)
    when (run.heroClass) {
      HeroClass.ASHEN_MONK -> {
        val solarCoords = buildSet {
          for (r in 0 until nextBoard.rows) {
            for (c in 0 until nextBoard.cols) {
              if (nextBoard.isColumnSealed(c)) continue
              val k = nextBoard.tileAt(r, c)?.kind
              if (k == TileKind.Playable(Hue.EMBER) || k == TileKind.Playable(Hue.GOLD)) {
                add(BoardCoord(r, c))
              }
            }
          }
        }
        val tileBonus = minOf(6, solarCoords.size)
        spandaHeat += tileBonus
        if (tileBonus > 0) steps.add(ScoringStep("Solar Tiles", heatDelta = tileBonus))
        nextPlayer = nextPlayer.copy(vigil = (nextPlayer.vigil + 2).coerceAtMost(PlayerCombatState.MAX_VIGIL))
        nextResources = nextResources.addInk(Hue.EMBER, 1)
        if (solarCoords.isNotEmpty()) {
          val consumeSet = solarCoords.take(4).toSet()
          val stepped = BoardEngine.stepConsumeWithoutEnsure(nextBoard, consumeSet, nextPaintDeck, nextBoardRng)
          nextBoard = stepped.board
          nextPaintDeck = stepped.paintDeck
          nextBoardRng = stepped.rng
        }
      }

      HeroClass.QUARRY_WITCH -> {
        val hazardCoords = buildSet {
          for (r in 0 until nextBoard.rows) {
            for (c in 0 until nextBoard.cols) {
              if (nextBoard.isColumnSealed(c)) continue
              val k = nextBoard.tileAt(r, c)?.kind
              if (k is TileKind.Stone || k is TileKind.Ash) {
                add(BoardCoord(r, c))
              }
            }
          }
        }
        val consumeSet = hazardCoords.take(3).toSet()
        consumeSet.forEach { coord ->
          if (nextBoard.tileAt(coord.row, coord.col)?.kind is TileKind.Stone) totalStones++ else totalAsh++
        }
        spandaHeat += consumeSet.size * 2
        if (consumeSet.isNotEmpty()) steps.add(ScoringStep("Quarry Chips", heatDelta = consumeSet.size * 2))
        nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 4)
        if (consumeSet.isNotEmpty()) {
          val stepped = BoardEngine.stepConsumeWithoutEnsure(nextBoard, consumeSet, nextPaintDeck, nextBoardRng)
          nextBoard = stepped.board
          nextPaintDeck = stepped.paintDeck
          nextBoardRng = stepped.rng
        }
      }

      HeroClass.THE_UNLIT -> {
        val ashCoords = buildSet {
          for (r in 0 until nextBoard.rows) {
            for (c in 0 until nextBoard.cols) {
              if (nextBoard.isColumnSealed(c)) continue
              if (nextBoard.tileAt(r, c)?.kind is TileKind.Ash) add(BoardCoord(r, c))
            }
          }
        }
        val consumeSet = ashCoords.take(3).toSet()
        totalAsh += consumeSet.size
        spandaHeat += consumeSet.size * 2
        if (consumeSet.isNotEmpty()) steps.add(ScoringStep("Ash Flicker", heatDelta = consumeSet.size * 2))
        nextResources = nextResources.addSpark(1)
        if (consumeSet.isNotEmpty()) {
          val stepped = BoardEngine.stepConsumeWithoutEnsure(nextBoard, consumeSet, nextPaintDeck, nextBoardRng)
          nextBoard = stepped.board
          nextPaintDeck = stepped.paintDeck
          nextBoardRng = stepped.rng
        }
      }
    }

    // 2. Apply Socketed PoE2 Dhāraṇā Support Engravings (transforms humble skill into a custom engine!)
    var hasEchoSupport = false
    for (supportId in growth.socketedSupportIds) {
      val sup = ProgressionCatalog.supportByIdOrNull(supportId) ?: continue
      when (sup.effect) {
        SupportEffect.DevourAsh -> {
          val ashTargets = buildList {
            for (r in 0 until nextBoard.rows) {
              for (c in 0 until nextBoard.cols) {
                if (!nextBoard.isColumnSealed(c) && nextBoard.tileAt(r, c)?.kind is TileKind.Ash) {
                  add(BoardCoord(r, c))
                }
              }
            }
          }.take(3).toSet()
          if (ashTargets.isNotEmpty()) {
            totalAsh += ashTargets.size
            val stepped = BoardEngine.stepConsumeWithoutEnsure(nextBoard, ashTargets, nextPaintDeck, nextBoardRng)
            nextBoard = stepped.board
            nextPaintDeck = stepped.paintDeck
            nextBoardRng = stepped.rng
            val heatAdd = ashTargets.size * 4
            val multAdd = ashTargets.size * 4
            spandaHeat += heatAdd
            multTenths += multAdd
            steps.add(ScoringStep("Bhasma-Sādhana", heatDelta = heatAdd, multAddTenths = multAdd))
          }
        }

        SupportEffect.ShatterStoneWave -> {
          val stoneTargets = buildList {
            for (r in 0 until nextBoard.rows) {
              for (c in 0 until nextBoard.cols) {
                if (!nextBoard.isColumnSealed(c) && nextBoard.tileAt(r, c)?.kind is TileKind.Stone) {
                  add(BoardCoord(r, c))
                }
              }
            }
          }.take(3).toSet()
          if (stoneTargets.isNotEmpty()) {
            totalStones += stoneTargets.size
            val stepped = BoardEngine.stepConsumeWithoutEnsure(nextBoard, stoneTargets, nextPaintDeck, nextBoardRng)
            nextBoard = stepped.board
            nextPaintDeck = stepped.paintDeck
            nextBoardRng = stepped.rng
            val heatAdd = stoneTargets.size * 5
            spandaHeat += heatAdd
            nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + stoneTargets.size * 3)
            steps.add(ScoringStep("Bhairava-Rava", heatDelta = heatAdd))
          }
        }

        SupportEffect.ChainCascadeMult -> {
          val multAdd = combat.turn.cascadesThisTurn * 5
          if (multAdd > 0) {
            multTenths += multAdd
            steps.add(ScoringStep("Śakti-Cakra", multAddTenths = multAdd))
          }
        }

        SupportEffect.HueResonance -> {
          multTenths += 6
          steps.add(ScoringStep("So'ham-Breath", multAddTenths = 6))
        }

        SupportEffect.TemperedGuard -> {
          val guardGain = maxOf(3, (spandaHeat * 60) / 100)
          nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + guardGain)
          steps.add(ScoringStep("Yoni-Mudrā +${guardGain}G"))
        }

        SupportEffect.ForgePrism -> {
          val (prismBoard, rPrism) = BoardEngine.paintTiles(nextBoard, TileKind.Prism, 1, nextBoardRng)
          nextBoard = prismBoard
          nextBoardRng = rPrism
          steps.add(ScoringStep("Bindu-Prism"))
        }

        SupportEffect.BloodKilnDouble -> {
          if (nextHp > 4) {
            nextHp -= 4
            multTenths = (multTenths * 15) / 10
            steps.add(ScoringStep("Rakta-Tarpaṇa", multFactorPercent = 150))
          }
        }

        SupportEffect.EchoStrike -> {
          hasEchoSupport = true
        }
      }
    }

    val spectrumScaled = (spandaHeat * combat.spectrum.halfSteps) / 2
    val primaryRaw = (spectrumScaled * multTenths) / 10
    val (damaged1, dealt1) = applyPlayerDamage(nextEnemy, primaryRaw, run.relics)
    nextEnemy = damaged1
    var totalDealt = dealt1

    if (hasEchoSupport && nextEnemy.hp > 0) {
      val echoRaw = (primaryRaw * 60) / 100
      val (damaged2, dealt2) = applyPlayerDamage(nextEnemy, echoRaw, run.relics)
      nextEnemy = damaged2
      totalDealt += dealt2
      steps.add(ScoringStep("Nāda-Echo", heatDelta = dealt2))
    }

    val socketCountNote = if (growth.socketedSupportIds.isEmpty()) "Unlinked Lv.${growth.level}" else "${growth.socketedSupportIds.size}-Link Lv.${growth.level}"
    queue.enqueue(
      priority = EventPriority.BOARD_POP,
      message = "${skillDef.name.uppercase()} ($socketCountNote): $spectrumScaled Spanda × ${multTenths / 10}.${multTenths % 10}× = $totalDealt dmg!",
      kind = CombatEventKind.OVERDRIVE,
      effectiveSpectrumHalfSteps = combat.spectrum.halfSteps,
    )

    val skillBreakdown = ScoringBreakdown(
      sourceTitle = skillDef.name,
      totalHeat = spectrumScaled,
      finalMultTenths = multTenths,
      finalOutput = totalDealt,
      steps = steps.take(6),
    )

    val ensured = BoardEngine.ensurePlayableSwap(nextBoard, nextPaintDeck, nextBoardRng, combat.spectrum.currentHue)
    nextBoard = ensured.board
    nextPaintDeck = ensured.paintDeck
    nextBoardRng = ensured.rng

    val sortedEvents = queue.drainSorted()
    val updatedCombat = combat.copy(
      board = nextBoard,
      paintDeck = nextPaintDeck,
      enemy = nextEnemy,
      turn = combat.turn,
      resources = nextResources,
      player = nextPlayer,
      lastEvents = sortedEvents,
      nonAshPoppedThisTurn = true,
      lastScoringBreakdown = skillBreakdown,
    )
    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = nextBoardRng),
        hp = nextHp,
        combat = updatedCombat,
        dreadConsecutiveAshTurns = 0,
        stats = run.stats.copy(
          ashBlocksPopped = run.stats.ashBlocksPopped + totalAsh,
          stonesShattered = run.stats.stonesShattered + totalStones,
        ),
      )
    )
    return CommandResult(finalRun, accepted = true, events = sortedEvents)
  }

  /**
   * Quarry Witch Signature: Chisel Wand (once per fight free recolour on unsealed occupied tile).
   * If recolouring creates a 3+ horizontal/vertical match, automatically resolves the cascade!
   */
  private fun handleUseChiselWand(run: RunState, row: Int, col: Int, targetHue: Hue): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val queue = BoundedEventQueue()
    var nextBoard = BoardEngine.recolourTile(combat.board, row, col, targetHue)
    var nextPaintDeck = combat.paintDeck
    var nextBoardRng = run.rng.boardPaint
    var nextEnemy = combat.enemy
    var nextResources = combat.resources
    var nextSpectrum = combat.spectrum
    var nextPlayer = combat.player.copy(chiselWandUsedThisCombat = true)
    queue.enqueue(EventPriority.RELIC, "Chisel Wand: Recoloured (${row + 1},${col + 1}) to ${targetHue.displayName}.")

    // If recolouring completes 3+ in a row, immediately clear & cascade!
    var waveIdx = 1
    var wave = BoardEngine.resolveBoardMatches(nextBoard, nextSpectrum.currentHue).takeIf { it.isValid }
    while (wave != null && waveIdx <= 8) {
      val (effHalf, updSpec) = BoardEngine.previewSpectrumForPop(
        current = nextSpectrum,
        resolvedHue = wave.primaryHue,
        usedPrism = wave.prismCount > 0,
        isDirectAshGroup = wave.isDirectAshOnly,
      )
      nextSpectrum = updSpec
      if (!wave.isDirectAshOnly) {
        val rawDmg = (wave.basePower * effHalf) / 2
        val (dmgEnemy, dealt) = applyPlayerDamage(nextEnemy, rawDmg, run.relics)
        nextEnemy = dmgEnemy
        queue.enqueue(
          priority = EventPriority.BOARD_POP,
          message = "Chisel Match ${wave.coords.size} ${wave.primaryHue?.displayName ?: "Prism"} = $dealt dmg",
          kind = CombatEventKind.BOARD_POP,
          effectiveSpectrumHalfSteps = effHalf,
        )
        for (hue in Hue.WHEEL_ORDER) {
          val inkAmt = wave.inkForHue(hue)
          if (inkAmt > 0) nextResources = nextResources.addInk(hue, inkAmt)
        }
      }
      if (wave.shatteredStone.isNotEmpty()) {
        nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + wave.stoneDefenseBonus)
      }
      val stepped = BoardEngine.stepConsumeWithoutEnsure(
        board = nextBoard,
        consumedCoords = wave.coords + wave.clearedAsh + wave.shatteredStone,
        paintDeck = nextPaintDeck,
        rng = nextBoardRng,
      )
      nextBoard = stepped.board
      nextPaintDeck = stepped.paintDeck
      nextBoardRng = stepped.rng
      waveIdx++
      wave = BoardEngine.resolveBoardMatches(nextBoard, nextSpectrum.currentHue).takeIf { it.isValid }
    }
    if (waveIdx > 1) {
      val ensured = BoardEngine.ensurePlayableSwap(nextBoard, nextPaintDeck, nextBoardRng, nextSpectrum.currentHue)
      nextBoard = ensured.board
      nextPaintDeck = ensured.paintDeck
      nextBoardRng = ensured.rng
    }

    val events = queue.drainSorted()
    val updatedCombat = combat.copy(
      board = nextBoard,
      paintDeck = nextPaintDeck,
      enemy = nextEnemy,
      resources = nextResources,
      spectrum = nextSpectrum,
      player = nextPlayer,
      lastEvents = events,
    )
    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = nextBoardRng),
        combat = updatedCombat,
      )
    )
    return CommandResult(finalRun, accepted = true, events = events)
  }

  private fun handleProceedToCast(run: RunState): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val queue = BoundedEventQueue()
    val unspent = combat.tapsRemaining
    val nextVigil = (combat.player.vigil + unspent).coerceAtMost(PlayerCombatState.MAX_VIGIL)
    val gained = nextVigil - combat.player.vigil
    // Keystone: Kumbhaka Void (banking unused moves into suspended breath adds +1.0× Vimarśa MULT per move!)
    val kumbhakaBonus = if (unspent > 0 && run.growth.hasPassive("pass_foundry_key")) unspent * 10 else 0
    if (unspent > 0) {
      val kNote = if (kumbhakaBonus > 0) " & Kumbhaka Void +${unspent}.0× MULT!" else ""
      queue.enqueue(EventPriority.SYSTEM, "Proceeded to CAST ($unspent banked tap(s) -> +$gained Vigil$kNote).")
    } else {
      queue.enqueue(EventPriority.SYSTEM, "Entered CAST phase.")
    }
    val events = queue.drainSorted()

    val updated = combat.copy(
      turn = combat.turn.copy(
        phase = TurnPhase.CAST,
        tapsRemaining = 0,
        overpressureMultTenths = combat.turn.overpressureMultTenths + kumbhakaBonus,
      ),
      player = combat.player.copy(vigil = nextVigil),
      lastEvents = events,
    )
    return CommandResult(run.copy(combat = updated), accepted = true, events = events)
  }

  /**
   * SĀKṢĪ WITNESSING (Vairāgya / Conscious Letting-Go):
   * Once per turn during WEAVE or CAST, step back as the silent Witness of the mind.
   * Dissolves emotional Attachment (-3 Rāga, or -5 with Sphaṭika Mālā), transmutes clinging
   * into Vimarśa MULT + Equanimity Guard + Prāṇa-Mudrā charge, and peels back 1 layer of
   * the encounter's Emotional Illusion Veil. Shattering all 3 layers triggers PRATYABHIJÑĀ!
   */
  private fun handleWitnessIllusion(run: RunState): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val mind = combat.mindIllusion
    val growth = run.growth
    val queue = BoundedEventQueue()
    val steps = mutableListOf<ScoringStep>()

    val hasMala = growth.hasArtifact("art_sphatika_mala")
    val releaseCap = if (hasMala) 5 else 3
    val dissolved = minOf(mind.attachment, releaseCap)
    var nextAttachment = (mind.attachment - dissolved).coerceAtLeast(0)
    var nextHp = run.hp
    var nextResources = combat.resources
    var nextPlayer = combat.player
    var nextEnemy = combat.enemy
    var nextMultTenths = combat.turn.comboMultTenths
    var nextDetachedSpanda = mind.detachedBonusSpanda
    var totalOutput = 0

    if (dissolved > 0) {
      val guardGain = if (dissolved >= 3) 4 else 2
      nextPlayer = nextPlayer.copy(
        guardBlock = nextPlayer.guardBlock + guardGain,
        overdriveCharge = (nextPlayer.overdriveCharge + 12).coerceAtMost(PlayerCombatState.MAX_OVERDRIVE),
      )
      nextMultTenths += 5
      steps.add(ScoringStep("Sākṣī Release (-$dissolved Rāga)", multAddTenths = 5))
      if (hasMala && nextHp > 0) {
        nextHp = (nextHp + 2).coerceAtMost(run.maxHp)
        queue.enqueue(EventPriority.RELIC, "Sphaṭika Mālā: Witness breath restored +2 HP.")
      }
      if (growth.hasPassive("pass_iron_key")) {
        nextDetachedSpanda += dissolved * 2
        steps.add(ScoringStep("Śmaśāna-Bhasma", heatDelta = dissolved * 2))
      }
      queue.enqueue(
        priority = EventPriority.STATUS,
        message = "Sākṣī Witness dissolved $dissolved ${mind.illusion.emotionName} Attachment → +0.5× Vimarśa, +$guardGain Guard, +12% Charge!",
        kind = CombatEventKind.ILLUSION_SHIFT,
      )
    } else {
      // Pure stillness (Śūnya): witnessing with 0 attachment yields Spark & quiet clarity
      nextResources = nextResources.addSpark(1)
      nextPlayer = nextPlayer.copy(
        guardBlock = nextPlayer.guardBlock + 2,
        overdriveCharge = (nextPlayer.overdriveCharge + 10).coerceAtMost(PlayerCombatState.MAX_OVERDRIVE),
      )
      nextMultTenths += 3
      steps.add(ScoringStep("Śūnya Stillness", multAddTenths = 3))
      queue.enqueue(
        priority = EventPriority.STATUS,
        message = "Sākṣī Stillness (0 Attachment): +1 Spark, +2 Guard & +0.3× Vimarśa!",
        kind = CombatEventKind.ILLUSION_SHIFT,
      )
    }

    var nextLayers = mind.veilLayers
    var nextShattered = mind.shattered
    if (!nextShattered && (dissolved >= 2 || mind.attachment == 0)) {
      nextLayers = (mind.veilLayers - 1).coerceAtLeast(0)
      if (nextLayers == 0) {
        nextShattered = true
        nextAttachment = 0
        val mirrorHeld = growth.hasArtifact("art_kashmiri_darpana")
        val shatterBase = if (mirrorHeld) 24 else 12
        val shatterDmgRaw = (shatterBase * nextMultTenths) / 10
        nextEnemy = nextEnemy.copy(block = 0)
        val (shatteredEnemy, dealtShatter) = applyPlayerDamage(nextEnemy, shatterDmgRaw, run.relics)
        nextEnemy = shatteredEnemy
        if (mirrorHeld) nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 4)
        nextMultTenths += 10
        totalOutput = dealtShatter
        steps.add(ScoringStep("Pratyabhijñā!", heatDelta = shatterBase, multAddTenths = 10))
        queue.enqueue(
          priority = EventPriority.STATUS,
          message = "PRATYABHIJÑĀ! ${mind.illusion.veilTitle} SHATTERED — armor broken, $dealtShatter clarity dmg & +1.0× Vimarśa!",
          isAlert = true,
          kind = CombatEventKind.ILLUSION_SHATTERED,
        )
      } else {
        queue.enqueue(
          priority = EventPriority.STATUS,
          message = "Witnessing pierced ${mind.illusion.veilTitle} ($nextLayers/${MindIllusionState.MAX_VEIL_LAYERS} veils remaining).",
          kind = CombatEventKind.ILLUSION_SHIFT,
        )
      }
    }

    val breakdown = ScoringBreakdown(
      sourceTitle = if (nextShattered && !mind.shattered) "Pratyabhijñā!" else "Sākṣī Witness",
      totalHeat = if (totalOutput > 0) totalOutput else nextDetachedSpanda,
      finalMultTenths = nextMultTenths,
      finalOutput = totalOutput,
      steps = steps,
    )
    val events = queue.drainSorted()
    val updatedCombat = combat.copy(
      enemy = nextEnemy,
      resources = nextResources,
      player = nextPlayer,
      turn = combat.turn.copy(comboMultTenths = nextMultTenths),
      mindIllusion = mind.copy(
        attachment = nextAttachment,
        clarity = mind.clarity + 1,
        veilLayers = nextLayers,
        shattered = nextShattered,
        witnessUsedThisTurn = true,
        detachedBonusSpanda = nextDetachedSpanda,
      ),
      lastScoringBreakdown = breakdown,
      lastEvents = events,
    )
    val finalRun = resolvePostAction(run.copy(hp = nextHp, combat = updatedCombat))
    return CommandResult(finalRun, accepted = true, events = events)
  }

  /**
   * Computes whether [resources] can cover [card] colored + generic pips (ADR-009),
   * and returns the exact updated ResourceState if affordable, or null if insufficient.
   */
  fun tryPayCardCost(
    resources: ResourceState,
    card: CardDef,
    upgraded: Boolean,
    preferredGenericHue: Hue? = null,
  ): ResourceState? {
    val pools = resources.inkPools.toMutableMap()
    var sparkLeft = resources.spark

    // 1. Pay exact colored pips first (Spark NEVER satisfies colored pips)
    for ((hue, required) in card.coloredCost) {
      val have = pools[hue] ?: 0
      if (have < required) return null
      pools[hue] = have - required
    }

    // 2. Pay generic pips with preferred Hue -> Spark -> Wheel order
    var genericNeeded = (card.genericCost - if (upgraded) card.upgradedGenericDiscount else 0).coerceAtLeast(0)
    if (genericNeeded > 0 && preferredGenericHue != null) {
      val havePref = pools[preferredGenericHue] ?: 0
      val take = minOf(havePref, genericNeeded)
      pools[preferredGenericHue] = havePref - take
      genericNeeded -= take
    }
    if (genericNeeded > 0 && sparkLeft > 0) {
      val take = minOf(sparkLeft, genericNeeded)
      sparkLeft -= take
      genericNeeded -= take
    }
    if (genericNeeded > 0) {
      for (hue in Hue.WHEEL_ORDER) {
        if (genericNeeded == 0) break
        val have = pools[hue] ?: 0
        if (have > 0) {
          val take = minOf(have, genericNeeded)
          pools[hue] = have - take
          genericNeeded -= take
        }
      }
    }

    return if (genericNeeded == 0) {
      ResourceState(inkPools = pools.toMap(), spark = sparkLeft)
    } else {
      null
    }
  }

  private fun handleCastCard(run: RunState, cardUid: Long, genericPayHue: Hue?): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val cardInst = combat.combatDeck.hand.firstOrNull { it.uid == cardUid }
      ?: return CommandResult(run, false, CommandRejectionReason.CARD_NOT_IN_HAND)
    val def = GameCatalog.cardById(cardInst.defId)
    val paidResources = tryPayCardCost(combat.resources, def, cardInst.upgraded, genericPayHue)
      ?: return CommandResult(run, false, CommandRejectionReason.INSUFFICIENT_INK_OR_SPARK)

    val queue = BoundedEventQueue()
    var nextResources = paidResources
    var nextEnemy = combat.enemy
    var nextPlayer = combat.player
    var nextBoard = combat.board
    var nextBoardRng = run.rng.boardPaint
    var nextDeckRng = run.rng.combatDeck
    var nextFlasks = run.flasks
    var nextSkipWall = combat.turn.skipWallRiseThisTurn
    var nextDeck = CombatDeck.playFromHand(combat.combatDeck, cardUid, def.exhaustOnPlay)

    // P0.1: Burn Vigil for Ashen Monk burst cards
    val burnedVigil = if (def.vigilBurnMax > 0) {
      minOf(nextPlayer.vigil, def.vigilBurnMax)
    } else 0
    if (burnedVigil > 0) {
      nextPlayer = nextPlayer.copy(vigil = nextPlayer.vigil - burnedVigil)
    }

    val growth = run.growth
    var currentMultTenths = combat.turn.comboMultTenths
    var remainingOverpressure = combat.turn.overpressureMultTenths
    val cardHue = def.frameHue
    val nextSameHueStreak = if (cardHue != null && cardHue == combat.turn.lastCardHue) {
      combat.turn.sameHueCardStreak + 1
    } else if (cardHue != null) {
      1
    } else {
      combat.turn.sameHueCardStreak
    }
    val cardSteps = mutableListOf<ScoringStep>()
    var lastCardSpanda = 0
    var lastCardDealt = 0

    // Passive: Ajapa-Japa (playing 2+ cards of the same Hue in one turn adds +0.5× Vimarśa MULT!)
    if (nextSameHueStreak >= 2 && growth.hasPassive("pass_escape_3")) {
      currentMultTenths += 5
      cardSteps.add(ScoringStep("Ajapa-Japa", multAddTenths = 5))
      queue.enqueue(EventPriority.RELIC, "Ajapa-Japa Chant: +0.5× Vimarśa MULT!")
    }

    fun dealCardDamage(baseDamage: Int, isEchoCopy: Boolean) {
      val upgradeBonus = if (cardInst.upgraded) def.upgradedDamageBonus else 0
      val relicBonus = if (def.category == CardCategory.ATTACK && "relic_ember_whetstone" in run.relics) 3 else 0
      val uccaraBonus = if (def.category == CardCategory.ATTACK && growth.hasPassive("pass_foundry_3")) 3 else 0
      val vigilBonus = if (!isEchoCopy) burnedVigil * def.damagePerBurnedVigil else 0
      if (uccaraBonus > 0 && !isEchoCopy) cardSteps.add(ScoringStep("Uccāra-Strike", heatDelta = uccaraBonus))
      val rawTotal = baseDamage + upgradeBonus + relicBonus + uccaraBonus + vigilBonus
      val spectrumScaled = (rawTotal * combat.spectrum.halfSteps) / 2
      var effectiveMult = currentMultTenths
      if (!isEchoCopy && remainingOverpressure > 0) {
        effectiveMult += remainingOverpressure
        cardSteps.add(ScoringStep("Kumbhaka Void", multAddTenths = remainingOverpressure))
        remainingOverpressure = 0
      }
      val multScaled = if (effectiveMult > TurnState.BASE_MULT_TENTHS) {
        (spectrumScaled * effectiveMult) / 10
      } else {
        spectrumScaled
      }
      val (damagedEnemy, scaledDmg) = applyPlayerDamage(nextEnemy, multScaled, run.relics)
      nextEnemy = damagedEnemy
      lastCardSpanda += spectrumScaled
      lastCardDealt += scaledDmg
      val echoTag = if (isEchoCopy) " (Echo)" else ""
      val burnNote = if (!isEchoCopy && burnedVigil > 0) " [Burned $burnedVigil Vigil]" else ""
      queue.enqueue(
        priority = if (isEchoCopy) EventPriority.ECHO else EventPriority.CARD,
        message = "${def.name}$echoTag: $scaledDmg dmg$burnNote.",
        isEcho = isEchoCopy,
        kind = if (isEchoCopy) CombatEventKind.ECHO else CombatEventKind.CARD_CAST,
      )
    }

    fun applyPrimaryGuard(isEchoCopy: Boolean) {
      val upgradeBonus = if (cardInst.upgraded) def.upgradedBlockBonus else 0
      val relicBonus = if (def.category == CardCategory.SKILL && "relic_moss_talisman" in run.relics) 3 else 0
      val gained = def.blockGain + upgradeBonus + relicBonus
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + gained)
      val echoTag = if (isEchoCopy) " (Echo)" else ""
      queue.enqueue(
        priority = if (isEchoCopy) EventPriority.ECHO else EventPriority.CARD,
        message = "${def.name}$echoTag: +$gained Guard.",
        isEcho = isEchoCopy,
        kind = if (isEchoCopy) CombatEventKind.ECHO else CombatEventKind.CARD_CAST,
      )
    }

    // 1. Primary execution (full card)
    if (def.damage > 0) {
      dealCardDamage(def.damage, isEchoCopy = false)
    }
    if (def.blockGain > 0) {
      applyPrimaryGuard(isEchoCopy = false)
    }
    if (def.category == CardCategory.SKILL && "relic_moss_talisman" in run.relics) {
      nextPlayer = nextPlayer.copy(vigil = (nextPlayer.vigil + 1).coerceAtMost(PlayerCombatState.MAX_VIGIL))
    }
    if (def.vigilGain > 0) {
      val updatedVigil = (nextPlayer.vigil + def.vigilGain).coerceAtMost(PlayerCombatState.MAX_VIGIL)
      nextPlayer = nextPlayer.copy(vigil = updatedVigil)
      queue.enqueue(EventPriority.CARD, "+${def.vigilGain} Vigil.")
    }
    if (def.clearsFog && nextPlayer.fogTapsRemaining > 0) {
      nextPlayer = nextPlayer.copy(fogTapsRemaining = 0)
      queue.enqueue(EventPriority.STATUS, "Gold Eye dispelled all board Fog!")
    } else if (def.clearsFog) {
      queue.enqueue(EventPriority.CARD, "Gold Eye pierced the chamber.")
    }
    if (def.unsealColumns && nextBoard.sealedColumns.isNotEmpty()) {
      nextBoard = nextBoard.copy(sealedColumns = emptyMap())
      queue.enqueue(EventPriority.STATUS, "Quarry Purge unsealed all locked columns!")
    }
    if (def.sparkGain > 0) {
      nextResources = nextResources.addSpark(def.sparkGain)
      queue.enqueue(EventPriority.CARD, "+${def.sparkGain} Spark.")
    }
    if (def.inkGainHue != null && def.inkGainAmount > 0) {
      nextResources = nextResources.addInk(def.inkGainHue, def.inkGainAmount)
      queue.enqueue(EventPriority.CARD, "+${def.inkGainAmount} ${def.inkGainHue.displayName} Ink.")
    }
    if (def.shatterStoneCount > 0) {
      val (shatteredBoard, count) = BoardEngine.shatterStones(nextBoard, def.shatterStoneCount)
      nextBoard = shatteredBoard
      if (count > 0) queue.enqueue(EventPriority.BOARD_POP, "Cracked $count Stone into Ash.")
    }
    if (def.paintKind != null && def.paintCount > 0) {
      val (painted, r1) = BoardEngine.paintTiles(nextBoard, def.paintKind, def.paintCount, nextBoardRng)
      nextBoard = painted
      nextBoardRng = r1
      val label = tileKindLabel(def.paintKind)
      queue.enqueue(EventPriority.BOARD_POP, "Painted ${def.paintCount} $label.")
    }
    if (def.spawnPrisms > 0) {
      val (painted, r1) = BoardEngine.paintTiles(nextBoard, TileKind.Prism, def.spawnPrisms, nextBoardRng)
      nextBoard = painted
      nextBoardRng = r1
      queue.enqueue(EventPriority.BOARD_POP, "Spawned ${def.spawnPrisms} Prism.")
    }
    if (def.skipWallRise) {
      nextSkipWall = true
      queue.enqueue(EventPriority.WALL, "Wall rise skipped this turn.")
    }
    if (def.gainFlasks > 0) {
      nextFlasks = (nextFlasks + def.gainFlasks).coerceAtMost(run.maxFlasks)
      queue.enqueue(EventPriority.CARD, "Restored +${def.gainFlasks} Flask.")
    }
    if (def.drawCards > 0) {
      val (drawn, r1) = CombatDeck.drawCards(nextDeck, def.drawCards, nextDeckRng)
      nextDeck = drawn
      nextDeckRng = r1
    }

    // 2. ADR-015 Echo: Repeats ONLY the single PrimaryEffect once (no secondary statuses, no Vigil burn, no exhaust/Flasks, no Echo-of-Echo)
    if (def.echo && nextEnemy.hp > 0) {
      when (def.primaryEffect) {
        PrimaryEffect.DAMAGE -> if (def.damage > 0) dealCardDamage(def.damage, isEchoCopy = true)
        PrimaryEffect.GUARD -> if (def.blockGain > 0) applyPrimaryGuard(isEchoCopy = true)
        PrimaryEffect.VIGIL -> if (def.vigilGain > 0) {
          nextPlayer = nextPlayer.copy(vigil = (nextPlayer.vigil + def.vigilGain).coerceAtMost(PlayerCombatState.MAX_VIGIL))
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): +${def.vigilGain} Vigil.", isEcho = true)
        }
        PrimaryEffect.DRAW -> if (def.drawCards > 0) {
          val (drawn, r1) = CombatDeck.drawCards(nextDeck, def.drawCards, nextDeckRng)
          nextDeck = drawn
          nextDeckRng = r1
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): Drew ${def.drawCards} card(s).", isEcho = true)
        }
        PrimaryEffect.PAINT_BOARD -> if (def.paintKind != null && def.paintCount > 0) {
          val (painted, r1) = BoardEngine.paintTiles(nextBoard, def.paintKind, def.paintCount, nextBoardRng)
          nextBoard = painted
          nextBoardRng = r1
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): Painted ${def.paintCount} ${tileKindLabel(def.paintKind)}.", isEcho = true)
        }
        PrimaryEffect.SPAWN_PRISM -> if (def.spawnPrisms > 0) {
          val (painted, r1) = BoardEngine.paintTiles(nextBoard, TileKind.Prism, def.spawnPrisms, nextBoardRng)
          nextBoard = painted
          nextBoardRng = r1
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): Spawned ${def.spawnPrisms} Prism.", isEcho = true)
        }
        PrimaryEffect.SHATTER_STONE -> if (def.shatterStoneCount > 0) {
          val (shatteredBoard, count) = BoardEngine.shatterStones(nextBoard, def.shatterStoneCount)
          nextBoard = shatteredBoard
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): Cracked $count Stone.", isEcho = true)
        }
        PrimaryEffect.CLEAR_FOG -> {
          nextPlayer = nextPlayer.copy(fogTapsRemaining = 0)
          queue.enqueue(EventPriority.ECHO, "${def.name} (Echo): Cleared Fog.", isEcho = true)
        }
      }
    }

    // 2.5 Arena Twist: Echo Chamber surges the first Ink Card cast each turn + Overdrive charge
    nextPlayer = nextPlayer.copy(
      overdriveCharge = (nextPlayer.overdriveCharge + 10).coerceAtMost(PlayerCombatState.MAX_OVERDRIVE),
    )
    if (combat.arenaTwist == ArenaTwist.ECHO_CHAMBER && combat.cardsPlayedThisTurn == 0 && nextEnemy.hp > 0) {
      val (echoEnemy, echoDmg) = applyPlayerDamage(nextEnemy, 5, run.relics)
      nextEnemy = echoEnemy
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + 4)
      queue.enqueue(
        priority = EventPriority.ECHO,
        message = "Echo Chamber: First card surges for +$echoDmg dmg & +4 Guard!",
        kind = CombatEventKind.ECHO,
      )
    }
    // Tantric Artifact: Kāśmīrī Darpaṇa mirrors first Ink Card cast each turn into Guard!
    if (growth.hasArtifact("art_kashmiri_darpana") && combat.cardsPlayedThisTurn == 0) {
      val mirrorGuard = maxOf(4, lastCardDealt / 4)
      nextPlayer = nextPlayer.copy(guardBlock = nextPlayer.guardBlock + mirrorGuard)
      queue.enqueue(EventPriority.RELIC, "Kāśmīrī Darpaṇa: Mirrored +$mirrorGuard Guard.")
    }

    val cardBreakdown = if (lastCardDealt > 0) {
      ScoringBreakdown(
        sourceTitle = def.name,
        totalHeat = lastCardSpanda,
        finalMultTenths = currentMultTenths,
        finalOutput = lastCardDealt,
        steps = cardSteps.take(6),
      )
    } else combat.lastScoringBreakdown

    val sortedEvents = queue.drainSorted()
    val updatedCombat = combat.copy(
      board = nextBoard,
      combatDeck = nextDeck,
      enemy = nextEnemy,
      turn = combat.turn.copy(
        skipWallRiseThisTurn = nextSkipWall,
        comboMultTenths = currentMultTenths,
        lastCardHue = cardHue ?: combat.turn.lastCardHue,
        sameHueCardStreak = nextSameHueStreak,
        overpressureMultTenths = remainingOverpressure,
      ),
      resources = nextResources,
      player = nextPlayer,
      lastEvents = sortedEvents,
      vigilSpentThisTurn = combat.vigilSpentThisTurn || burnedVigil > 0 || def.vigilCost > 0,
      cardsPlayedThisTurn = combat.cardsPlayedThisTurn + 1,
      lastScoringBreakdown = cardBreakdown,
    )

    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = nextBoardRng, combatDeck = nextDeckRng),
        flasks = nextFlasks,
        combat = updatedCombat,
        dreadConsecutiveAshTurns = 0, // Playing any card immediately disqualifies Ash-only Dread streak
        stats = run.stats.copy(cardsPlayed = run.stats.cardsPlayed + 1),
      )
    )
    return CommandResult(finalRun, accepted = true, events = sortedEvents)
  }

  private fun tileKindLabel(kind: TileKind): String = when (kind) {
    is TileKind.Playable -> kind.hue.displayName
    TileKind.Ash -> "Ash"
    TileKind.Stone -> "Stone"
    TileKind.Prism -> "Prism"
    TileKind.Empty -> "Empty"
  }

  private fun handleUseFlask(run: RunState): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val queue = BoundedEventQueue()
    val healAmount = if ("relic_alchemist_coil" in run.relics) 16 else 10
    val nextHp = (run.hp + healAmount).coerceAtMost(run.maxHp)
    val nextFlasks = run.flasks - 1
    var nextEnemy = combat.enemy
    if ("relic_alchemist_coil" in run.relics) {
      val (coilEnemy, coilDmg) = applyPlayerDamage(nextEnemy, 8, run.relics)
      nextEnemy = coilEnemy
      queue.enqueue(EventPriority.RELIC, "Alchemist's Coil: +$coilDmg dmg.")
    }

    val (paintedBoard, rngBoard) = BoardEngine.paintTiles(combat.board, TileKind.Prism, 1, run.rng.boardPaint)
    val (nextDeck, rngDeck) = CombatDeck.drawCards(combat.combatDeck, 1, run.rng.combatDeck)
    queue.enqueue(
      priority = EventPriority.SYSTEM,
      message = "Ember Flask: +$healAmount HP, +6 Guard, +1 Prism, +1 Card.",
      kind = CombatEventKind.FLASK_USE,
    )
    if (nextFlasks == 0) {
      queue.enqueue(
        priority = EventPriority.SYSTEM,
        message = "ZERO-FLASK COLLAPSE!",
        isAlert = true,
        kind = CombatEventKind.ZERO_FLASK_COLLAPSE,
      )
    }
    val events = queue.drainSorted()

    val updatedCombat = combat.copy(
      board = paintedBoard,
      combatDeck = nextDeck,
      enemy = nextEnemy,
      player = combat.player.copy(guardBlock = combat.player.guardBlock + 6),
      lastEvents = events,
      flaskUsedThisTurn = true,
    )

    val finalRun = resolvePostAction(
      run.copy(
        rng = run.rng.copy(boardPaint = rngBoard, combatDeck = rngDeck),
        hp = nextHp,
        flasks = nextFlasks,
        combat = updatedCombat,
        dreadConsecutiveAshTurns = 0, // ADR-017: Flask use resets Ash-only Dread streak
      )
    )
    return CommandResult(finalRun, accepted = true, events = events)
  }

  /**
   * Transitions WEAVE/CAST -> WALL -> ENEMY -> CLEANUP -> next turn DRAW/WEAVE.
   */
  private fun handleEndTurn(run: RunState): CommandResult {
    val combat = run.combat ?: return CommandResult(run, false, CommandRejectionReason.NO_ACTIVE_COMBAT)
    val queue = BoundedEventQueue()
    var nextBoard = combat.board
    var nextPaintDeck = combat.paintDeck
    var nextBoardRng = run.rng.boardPaint
    var nextEnemyRng = run.rng.enemy
    var nextEnemy = combat.enemy
    var nextHp = run.hp
    var nextFlasks = run.flasks
    var guardPool = combat.player.guardBlock
    val vigilPool = (combat.player.vigil + combat.tapsRemaining).coerceAtMost(PlayerCombatState.MAX_VIGIL)
    var fogTaps = combat.player.fogTapsRemaining
    var overflowCount = 0
    var overflowOccurred = combat.overflowOccurredThisTurn
    val newlySealedColumns = mutableSetOf<Int>()

    if (combat.tapsRemaining > 0) {
      queue.enqueue(EventPriority.SYSTEM, "Banked ${combat.tapsRemaining} tap(s) -> +${combat.tapsRemaining} Vigil.")
    }

    // 1. WALL PHASE (ADR-008 + EFB-012 active Spectrum hue forwarded into rescue)
    if (!combat.turn.skipWallRiseThisTurn) {
      val rise = BoardEngine.riseIncomingRow(
        board = nextBoard,
        paintDeck = nextPaintDeck,
        rng = nextBoardRng,
        currentRunHue = combat.spectrum.currentHue,
      )
      nextBoard = rise.board
      nextPaintDeck = rise.paintDeck
      nextBoardRng = rise.rng

      if (rise.overflowTriggered) {
        overflowCount++
        overflowOccurred = true
        nextHp = maxOf(0, nextHp - 8)
        nextFlasks = maxOf(0, nextFlasks - 1)
        queue.enqueue(
          priority = EventPriority.WALL,
          message = "WALL OVERFLOW! Lost 8 HP, 1 Flask, top 2 rows burned — Wall rise skipped!",
          isAlert = true,
          kind = CombatEventKind.WALL_OVERFLOW,
        )
      } else {
        queue.enqueue(
          priority = EventPriority.WALL,
          message = "Wall rose cleanly.",
          kind = CombatEventKind.WALL_RISE,
        )
        if ("relic_brazier_grate" in run.relics) {
          val (grateEnemy, grateDmg) = applyPlayerDamage(nextEnemy, 5, run.relics)
          nextEnemy = grateEnemy
          queue.enqueue(EventPriority.RELIC, "Brazier Grate: Clean Wall rise dealt $grateDmg dmg.")
        }
        // Auto-clear any 3+ horizontal/vertical lines formed by the rising Wall so 3-in-a-row never sit idle
        var wallWave = BoardEngine.resolveBoardMatches(nextBoard, combat.spectrum.currentHue).takeIf { it.isValid }
        var wallCascadeIdx = 1
        while (wallWave != null && wallCascadeIdx <= 8) {
          if (!wallWave.isDirectAshOnly) {
            val (dmgEnemy, dealt) = applyPlayerDamage(nextEnemy, wallWave.basePower, run.relics)
            nextEnemy = dmgEnemy
            queue.enqueue(
              priority = EventPriority.BOARD_POP,
              message = "Wall Cascade ×$wallCascadeIdx: ${wallWave.coords.size} tiles cleared ($dealt dmg).",
              kind = CombatEventKind.BOARD_POP,
            )
          }
          val stepped = BoardEngine.stepConsumeWithoutEnsure(
            board = nextBoard,
            consumedCoords = wallWave.coords + wallWave.clearedAsh + wallWave.shatteredStone,
            paintDeck = nextPaintDeck,
            rng = nextBoardRng,
          )
          nextBoard = stepped.board
          nextPaintDeck = stepped.paintDeck
          nextBoardRng = stepped.rng
          wallCascadeIdx++
          wallWave = BoardEngine.resolveBoardMatches(nextBoard, combat.spectrum.currentHue).takeIf { it.isValid }
        }
        if (wallCascadeIdx > 1 || !BoardEngine.hasAnyValidSwap(nextBoard, combat.spectrum.currentHue)) {
          val ensured = BoardEngine.ensurePlayableSwap(nextBoard, nextPaintDeck, nextBoardRng, combat.spectrum.currentHue)
          nextBoard = ensured.board
          nextPaintDeck = ensured.paintDeck
          nextBoardRng = ensured.rng
        }
      }
      if (rise.didReshuffle) {
        queue.enqueue(EventPriority.SYSTEM, "Board reshuffled via Paint Deck to preserve a legal move.")
      }
    } else {
      queue.enqueue(EventPriority.WALL, "Wall rise skipped.")
    }

    // EFB-001: Player death ALWAYS takes precedence over enemy death
    if (nextHp <= 0 || nextFlasks <= 0) {
      val collapsed = checkGameOver(
        run.copy(
          rng = run.rng.copy(boardPaint = nextBoardRng, enemy = nextEnemyRng),
          hp = nextHp,
          flasks = nextFlasks,
          dreadConsecutiveAshTurns = 0,
          combat = combat.copy(
            board = nextBoard,
            paintDeck = nextPaintDeck,
            enemy = nextEnemy,
            turn = combat.turn.copy(phase = TurnPhase.GAME_OVER),
            lastEvents = queue.drainSorted(),
            wallOverflowsTriggered = combat.wallOverflowsTriggered + overflowCount,
            overflowOccurredThisTurn = overflowOccurred,
          ),
          stats = run.stats.copy(
            turnsTaken = run.stats.turnsTaken + 1,
            wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
          ),
        )
      )
      return CommandResult(collapsed, accepted = true, events = collapsed.combat?.lastEvents ?: emptyList())
    }

    if (nextEnemy.hp <= 0) {
      val events = queue.drainSorted()
      val wonCombat = combat.copy(
        board = nextBoard,
        paintDeck = nextPaintDeck,
        enemy = nextEnemy,
        player = combat.player.copy(guardBlock = guardPool, vigil = vigilPool, fogTapsRemaining = fogTaps),
        lastEvents = events,
      )
      val wonRun = handleCombatVictory(
        run.copy(
          rng = run.rng.copy(boardPaint = nextBoardRng, enemy = nextEnemyRng),
          hp = nextHp,
          flasks = nextFlasks,
          combat = wonCombat,
          stats = run.stats.copy(
            turnsTaken = run.stats.turnsTaken + 1,
            wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
          ),
        ),
        wonCombat,
      )
      return CommandResult(wonRun, accepted = true, events = events)
    }

    // ADR-017 + EFB-004: Evaluate Unlit Covenant Ash-only no-card no-Vigil-burn turn qualification vs Act III Boss
    val qualifiesDreadAirTurn = run.currentAct == 3 &&
      nextEnemy.isBoss &&
      run.heroClass == HeroClass.THE_UNLIT &&
      run.unlitCovenantActive &&
      combat.ashPoppedThisTurn &&
      !combat.nonAshPoppedThisTurn &&
      !combat.vigilSpentThisTurn &&
      combat.cardsPlayedThisTurn == 0 &&
      !combat.flaskUsedThisTurn &&
      !overflowOccurred

    // 2. ENEMY PHASE (ADR-013: Enemy Block resets at start of Enemy turn; NO Vigil counterattack)
    nextEnemy = nextEnemy.copy(block = 0)
    var enemyNextBlock = 0
    val slotList = listOf("Slot I" to nextEnemy.slot1Intent, "Slot II" to nextEnemy.slot2Intent)

    for ((slotLabel, intent) in slotList) {
      if (qualifiesDreadAirTurn && intent !is EnemyIntent.Defend) {
        queue.enqueue(
          EventPriority.ENEMY,
          "$slotLabel: ${nextEnemy.name}'s strike passes through empty air! (Dread Covenant)",
          isAlert = true,
        )
        continue
      }

      val mayaGripActive = combat.mindIllusion.isEntangled
      when (intent) {
        is EnemyIntent.Attack -> {
          repeat(intent.hits) {
            val baseIncoming = intent.damage.coerceAtLeast(0)
            val incoming = if (mayaGripActive && baseIncoming > 0) {
              baseIncoming + maxOf(1, (baseIncoming + 3) / 4)
            } else {
              baseIncoming
            }
            val blocked = minOf(guardPool, incoming)
            guardPool -= blocked
            val unblocked = incoming - blocked
            nextHp = maxOf(0, nextHp - unblocked)
            val gripNote = if (mayaGripActive && baseIncoming > 0) " [Māyā Grip +25%]" else ""
            queue.enqueue(
              priority = EventPriority.ENEMY,
              message = "$slotLabel: ${nextEnemy.name} strikes $incoming$gripNote ($blocked Guard, $unblocked HP)",
              isAlert = unblocked > 0,
              kind = if (unblocked > 0) CombatEventKind.ENEMY_DAMAGE else CombatEventKind.INFO,
            )
          }
        }
        is EnemyIntent.Defend -> {
          enemyNextBlock += intent.block
          queue.enqueue(EventPriority.ENEMY, "$slotLabel: ${nextEnemy.name} gains +${intent.block} Block.")
        }
        is EnemyIntent.CorruptBoard -> {
          val (corrupted, r1) = BoardEngine.paintTiles(nextBoard, intent.kind, intent.count, nextBoardRng)
          nextBoard = corrupted
          nextBoardRng = r1
          val kindLabel = tileKindLabel(intent.kind)
          queue.enqueue(EventPriority.ENEMY, "$slotLabel: Corrupted ${intent.count} tile(s) to $kindLabel.", isAlert = true)
        }
        is EnemyIntent.SiphonFlask -> {
          val baseIncoming = intent.damage.coerceAtLeast(0)
          val incoming = if (mayaGripActive && baseIncoming > 0) {
            baseIncoming + maxOf(1, (baseIncoming + 3) / 4)
          } else {
            baseIncoming
          }
          val blocked = minOf(guardPool, incoming)
          guardPool -= blocked
          val unblocked = incoming - blocked
          nextHp = maxOf(0, nextHp - unblocked)
          if (unblocked > 0) {
            nextFlasks = maxOf(0, nextFlasks - intent.flaskDrain)
            queue.enqueue(
              priority = EventPriority.ENEMY,
              message = "$slotLabel: Siphoned ${intent.flaskDrain} Flask & $unblocked HP!",
              isAlert = true,
              kind = CombatEventKind.ENEMY_DAMAGE,
            )
          } else {
            queue.enqueue(EventPriority.ENEMY, "$slotLabel: Guard blocked Flask Siphon!")
          }
        }
        is EnemyIntent.InflictBrand -> {
          // EFB-008: persist exact post-shuffle RNG state returned by brandTiles
          val (brandedBoard, markedCount, rBrand) = BoardEngine.brandTiles(nextBoard, intent.count, nextBoardRng)
          nextBoard = brandedBoard
          nextBoardRng = rBrand
          var unblocked = 0
          if (intent.damage > 0) {
            val blocked = minOf(guardPool, intent.damage)
            guardPool -= blocked
            unblocked = intent.damage - blocked
            nextHp = maxOf(0, nextHp - unblocked)
          }
          queue.enqueue(
            priority = EventPriority.ENEMY,
            message = "$slotLabel: Branded $markedCount board block(s) (-2 Vigil each on pop)" +
              if (intent.damage > 0) " & dealt ${intent.damage} dmg." else ".",
            isAlert = true,
            kind = if (unblocked > 0) CombatEventKind.ENEMY_DAMAGE else CombatEventKind.INFO,
          )
        }
        is EnemyIntent.InflictFog -> {
          fogTaps = maxOf(fogTaps, intent.taps)
          var unblocked = 0
          if (intent.damage > 0) {
            val blocked = minOf(guardPool, intent.damage)
            guardPool -= blocked
            unblocked = intent.damage - blocked
            nextHp = maxOf(0, nextHp - unblocked)
          }
          queue.enqueue(
            priority = EventPriority.ENEMY,
            message = "$slotLabel: Shrouded board in Fog for $fogTaps Tap(s)!",
            isAlert = true,
            kind = if (unblocked > 0) CombatEventKind.ENEMY_DAMAGE else CombatEventKind.INFO,
          )
        }
        is EnemyIntent.SealColumn -> {
          // EFB-011: Enemy targeting uses enemy RNG stream (not boardPaint RNG)
          // EFB-002: Newly sealed columns are exempt from immediate same-turn Cleanup decrement
          val unsealedCols = (0 until nextBoard.cols).filter { !nextBoard.isColumnSealed(it) }
          if (unsealedCols.isNotEmpty()) {
            val (pickIdx, rNext) = nextEnemyRng.nextInt(unsealedCols.size)
            nextEnemyRng = rNext
            val chosenCol = unsealedCols[pickIdx]
            val updatedSeals = nextBoard.sealedColumns.toMutableMap()
            updatedSeals[chosenCol] = maxOf(updatedSeals[chosenCol] ?: 0, intent.turns)
            newlySealedColumns.add(chosenCol)
            nextBoard = nextBoard.copy(sealedColumns = updatedSeals.toMap())
            queue.enqueue(
              EventPriority.ENEMY,
              "$slotLabel: Sealed Column ${chosenCol + 1} for ${intent.turns} turns!",
              isAlert = true,
            )
          }
          if (intent.damage > 0) {
            val blocked = minOf(guardPool, intent.damage)
            guardPool -= blocked
            val unblocked = intent.damage - blocked
            nextHp = maxOf(0, nextHp - unblocked)
            if (unblocked > 0) {
              queue.enqueue(
                priority = EventPriority.ENEMY,
                message = "$slotLabel: Seal impact dealt $unblocked HP!",
                isAlert = true,
                kind = CombatEventKind.ENEMY_DAMAGE,
              )
            }
          }
        }
      }
    }

    if (nextHp <= 0 || nextFlasks <= 0) {
      val collapsed = checkGameOver(
        run.copy(
          rng = run.rng.copy(boardPaint = nextBoardRng, enemy = nextEnemyRng),
          hp = nextHp,
          flasks = nextFlasks,
          dreadConsecutiveAshTurns = 0,
          combat = combat.copy(
            board = nextBoard,
            paintDeck = nextPaintDeck,
            enemy = nextEnemy.copy(block = enemyNextBlock),
            turn = combat.turn.copy(phase = TurnPhase.GAME_OVER),
            lastEvents = queue.drainSorted(),
            wallOverflowsTriggered = combat.wallOverflowsTriggered + overflowCount,
          ),
          stats = run.stats.copy(
            turnsTaken = run.stats.turnsTaken + 1,
            wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
          ),
        )
      )
      return CommandResult(collapsed, accepted = true, events = collapsed.combat?.lastEvents ?: emptyList())
    }

    if (nextEnemy.hp <= 0) {
      val events = queue.drainSorted()
      val wonCombat = combat.copy(
        board = nextBoard,
        paintDeck = nextPaintDeck,
        enemy = nextEnemy,
        player = combat.player.copy(guardBlock = guardPool, vigil = vigilPool, fogTapsRemaining = fogTaps),
        lastEvents = events,
      )
      val wonRun = handleCombatVictory(
        run.copy(
          rng = run.rng.copy(boardPaint = nextBoardRng, enemy = nextEnemyRng),
          hp = nextHp,
          flasks = nextFlasks,
          combat = wonCombat,
          stats = run.stats.copy(
            turnsTaken = run.stats.turnsTaken + 1,
            wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
          ),
        ),
        wonCombat,
      )
      return CommandResult(wonRun, accepted = true, events = events)
    }

    // ADR-017: Update Dread 3-turn streak & check instant Dread Sovereign true victory
    val nextDreadStreak = if (run.currentAct == 3 && nextEnemy.isBoss && run.heroClass == HeroClass.THE_UNLIT && run.unlitCovenantActive) {
      if (qualifiesDreadAirTurn) {
        val incremented = run.dreadConsecutiveAshTurns + 1
        queue.enqueue(EventPriority.SYSTEM, "Dread Covenant: $incremented / 3 Ash-only turns completed!", isAlert = true)
        incremented
      } else {
        0
      }
    } else {
      0
    }

    if (nextDreadStreak >= 3) {
      queue.enqueue(
        priority = EventPriority.SYSTEM,
        message = "THE HEARTHKEEPER EXTINGUISHED — DREAD SOVEREIGN TRUE ENDING!",
        isAlert = true,
        kind = CombatEventKind.VICTORY,
      )
      val events = queue.drainSorted()
      val wonDread = run.copy(
        rng = run.rng.copy(boardPaint = nextBoardRng, enemy = nextEnemyRng),
        hp = nextHp,
        flasks = nextFlasks,
        combat = null,
        dreadConsecutiveAshTurns = 3,
        runWon = true,
        dreadEndingAchieved = true,
        stats = run.stats.copy(
          turnsTaken = run.stats.turnsTaken + 1,
          enemiesSlain = run.stats.enemiesSlain + 1,
          wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
        ),
      )
      return CommandResult(wonDread, accepted = true, events = events)
    }

    // 3. CLEANUP & DRAW -> WEAVE (ADR-001 + EFB-002 skip decrementing seals applied this Enemy phase)
    val decrementedSeals = nextBoard.sealedColumns
      .mapValues { (col, turns) -> if (col in newlySealedColumns) turns else turns - 1 }
      .filterValues { it > 0 }
    nextBoard = nextBoard.copy(sealedColumns = decrementedSeals)

    val enemyDef = GameCatalog.enemyById(nextEnemy.defId)
    val (slot1Pat, slot2Pat) = if (nextEnemy.isShade) {
      CampaignGenerator.buildShadeIntentsFromDeck(nextEnemy.shadeCardIds, nextEnemy.act, nextEnemy.shadeVigorBonus)
    } else {
      enemyDef.slot1Pattern to enemyDef.slot2Pattern
    }
    val nextStep = nextEnemy.patternStep + 1
    val updatedEnemy = nextEnemy.copy(
      block = enemyNextBlock,
      patternStep = nextStep,
      slot1Intent = slot1Pat[nextStep % slot1Pat.size],
      slot2Intent = slot2Pat[nextStep % slot2Pat.size],
    )

    val discarded = CombatDeck.discardHand(combat.combatDeck)
    val (nextDeck, nextDeckRng) = CombatDeck.drawCards(discarded, CombatDeck.BASE_DRAW_COUNT, run.rng.combatDeck)
    val baseSpectrum = if ("relic_refracting_lens" in run.relics) {
      SpectrumState(currentHue = Hue.EMBER, runLength = 1, halfSteps = 3)
    } else {
      SpectrumState(currentHue = null, runLength = 0, halfSteps = 2)
    }
    val events = queue.drainSorted()

    val calmKumbhakaCarry = if (run.growth.hasPassive("pass_foundry_key") && combat.mindIllusion.attachment <= 2) 5 else 0
    val nextTurnBaseMult = TurnState.BASE_MULT_TENTHS +
      (if (run.growth.hasArtifact("art_tantraloka_folio")) 2 else 0) +
      (if (combat.mindIllusion.shattered) 10 else 0)

    val nextTurnCombat = combat.copy(
      board = nextBoard,
      paintDeck = nextPaintDeck,
      combatDeck = nextDeck,
      enemy = updatedEnemy,
      turn = TurnState(
        turnNumber = combat.turnNumber + 1,
        phase = TurnPhase.WEAVE,
        tapsRemaining = TurnState.TAPS_PER_TURN,
        firstTapUsedThisTurn = false,
        skipWallRiseThisTurn = false,
        bonusMovesGrantedThisTurn = 0,
        comboMultTenths = nextTurnBaseMult,
        lastMatchedHue = null,
        lastCardHue = null,
        sameHueCardStreak = 0,
        cascadesThisTurn = 0,
        overpressureMultTenths = calmKumbhakaCarry,
        movesMadeThisTurn = 0,
      ),
      spectrum = baseSpectrum,
      player = combat.player.copy(
        guardBlock = guardPool / 2,
        vigil = vigilPool, // P0.1: Vigil persists across turns in combat
        fogTapsRemaining = fogTaps,
        firstBrandNegatedThisTurn = false,
        spectrumAstrolabeTriggeredThisTurn = false,
      ),
      mindIllusion = combat.mindIllusion.copy(witnessUsedThisTurn = false),
      lastEvents = events,
      wallOverflowsTriggered = combat.wallOverflowsTriggered + overflowCount,
      ashPoppedThisTurn = false,
      nonAshPoppedThisTurn = false,
      vigilSpentThisTurn = false,
      cardsPlayedThisTurn = 0,
      flaskUsedThisTurn = false,
      overflowOccurredThisTurn = false,
    )

    val nextRun = run.copy(
      rng = run.rng.copy(boardPaint = nextBoardRng, combatDeck = nextDeckRng, enemy = nextEnemyRng),
      hp = nextHp,
      flasks = nextFlasks,
      dreadConsecutiveAshTurns = nextDreadStreak,
      combat = nextTurnCombat,
      stats = run.stats.copy(
        turnsTaken = run.stats.turnsTaken + 1,
        wallOverflowsTriggered = run.stats.wallOverflowsTriggered + overflowCount,
      ),
    )
    return CommandResult(nextRun, accepted = true, events = events)
  }

  /**
   * Applies outgoing player damage to [enemy], enforcing Gravedigger's Spade (1.5x vs Elite/Boss/Shade).
   */
  fun applyPlayerDamage(enemy: EnemyState, rawDamage: Int, relics: List<String>): Pair<EnemyState, Int> {
    if (rawDamage <= 0) return enemy to 0
    val boosted = if ("relic_gravediggers_spade" in relics && (enemy.isElite || enemy.isBoss || enemy.isShade)) {
      (rawDamage * 3) / 2
    } else {
      rawDamage
    }
    return applyDamageToEnemy(enemy, boosted) to boosted
  }

  fun applyDamageToEnemy(enemy: EnemyState, rawDamage: Int): EnemyState {
    if (rawDamage <= 0) return enemy
    val absorbed = minOf(enemy.block, rawDamage)
    val remainingBlock = enemy.block - absorbed
    val overflow = rawDamage - absorbed
    return enemy.copy(
      block = remainingBlock,
      hp = maxOf(0, enemy.hp - overflow),
    )
  }

  fun checkGameOver(run: RunState): RunState {
    if (run.gameOverReason != null) return run
    val reason = when {
      run.hp <= 0 -> GameOverReason.HP_DEPLETED
      run.flasks <= 0 -> GameOverReason.ZERO_FLASKS
      else -> null
    } ?: return run

    return run.copy(
      gameOverReason = reason,
      combat = run.combat?.let { it.copy(turn = it.turn.copy(phase = TurnPhase.GAME_OVER)) },
    )
  }

  /**
   * EFB-001: Centralized terminal arbitration — player death / Zero-Flask collapse ALWAYS
   * takes precedence over simultaneous enemy death.
   */
  private fun resolvePostAction(run: RunState): RunState {
    val checkedDefeat = checkGameOver(run)
    if (checkedDefeat.gameOverReason != null) {
      return checkedDefeat
    }
    val combat = checkedDefeat.combat ?: return checkedDefeat
    if (combat.enemy.hp <= 0) {
      val withWinningTurn = checkedDefeat.copy(
        stats = checkedDefeat.stats.copy(turnsTaken = checkedDefeat.stats.turnsTaken + 1),
      )
      return handleCombatVictory(withWinningTurn, combat)
    }
    return checkedDefeat
  }

  private fun handleCombatVictory(run: RunState, combat: CombatState): RunState {
    val wonCombat = combat.copy(turn = combat.turn.copy(phase = TurnPhase.COMBAT_WON))
    val isBoss = combat.enemy.isBoss
    val isShade = combat.enemy.isShade
    val isElite = combat.enemy.isElite && !isShade

    if (isBoss && run.currentAct >= 3) {
      // Standard HP victory does NOT achieve the ADR-017 Dread ending
      return run.copy(
        combat = null,
        runWon = true,
        dreadEndingAchieved = false,
        stats = run.stats.copy(enemiesSlain = run.stats.enemiesSlain + 1),
      )
    }

    val kapalaBonus = run.growth.hasArtifact("art_kapala_bowl") && combat.turn.comboMultTenths >= 15
    val baseCinders = when {
      isBoss -> 95
      isShade -> 65
      isElite -> 55
      else -> 30
    } + (run.currentAct * 8)
    val bonusCinders = (if ("relic_cinder_tongs" in run.relics) 15 else 0) + (if (kapalaBonus) 10 else 0)
    val totalCinders = baseCinders + bonusCinders
    val healAfter = if ("relic_sovereign_seal" in run.relics) 6 else 0

    // Encounter-by-encounter PoE2 Sādhana XP -> guaranteed Level 2 after Encounter 1!
    // Shattering the encounter's Mind Illusion (Pratyabhijñā) awards +25 bonus Sādhana XP!
    val pratyabhijnaBonusXp = if (combat.mindIllusion.shattered) 25 else 0
    val xpGained = when {
      isBoss -> 50
      isShade -> 36
      isElite -> 32
      else -> 20
    } + (if (kapalaBonus) 8 else 0) + pratyabhijnaBonusXp
    val xpResult = run.growth.gainXp(xpGained)

    var currentRewardRng = run.rng.rewards
    val pool = GameCatalog.rewardPoolFor(run.heroClass)
    val (shuffledCards, r1) = currentRewardRng.shuffled(pool)
    currentRewardRng = r1
    val cardChoices = shuffledCards.take(3).map { it.id }

    // Draft 2 fresh Dhāraṇā Support Engravings so Signature Skill evolves encounter-by-encounter
    val unownedSupports = ProgressionCatalog.supportEngravings.filter { it.id !in xpResult.growth.socketedSupportIds }
    val supportChoices = if (unownedSupports.isNotEmpty()) {
      val (shuffledSupports, rSup) = currentRewardRng.shuffled(unownedSupports)
      currentRewardRng = rSup
      shuffledSupports.take(2).map { it.id }
    } else emptyList()

    // Draft 2 fresh Shaiva Tantric Ritual Artifacts so the Monk accumulates sacred items
    val unownedArtifacts = ProgressionCatalog.tantricArtifacts.filter { it.id !in xpResult.growth.tantricArtifactIds }
    val artifactChoices = if (unownedArtifacts.isNotEmpty()) {
      val (shuffledArtifacts, rArt) = currentRewardRng.shuffled(unownedArtifacts)
      currentRewardRng = rArt
      shuffledArtifacts.take(2).map { it.id }
    } else emptyList()

    var relicDrop: String? = null
    if (isElite || isBoss) {
      val unowned = GameCatalog.rewardRelics().filter { it.id !in run.relics }
      if (unowned.isNotEmpty()) {
        val (idx, r2) = currentRewardRng.nextInt(unowned.size)
        relicDrop = unowned[idx].id
        currentRewardRng = r2
      }
    }

    var remnantDrop: String? = null
    var nextRemnants = run.remnants
    if (isShade) {
      val unownedRemnants = GameCatalog.remnants.filter { it.id !in run.remnants }
      if (unownedRemnants.isNotEmpty()) {
        val (idx, r3) = currentRewardRng.nextInt(unownedRemnants.size)
        remnantDrop = unownedRemnants[idx].id
        nextRemnants = nextRemnants + remnantDrop
        currentRewardRng = r3
      }
    }

    val flaskDrop = if (isBoss || isElite || isShade) 1 else 0
    val reward = RewardBundle(
      cindersEarned = totalCinders,
      cardChoices = cardChoices,
      relicRewardId = relicDrop,
      remnantRewardId = remnantDrop,
      flaskRefill = flaskDrop,
      xpEarned = xpGained,
      leveledUp = xpResult.levelsGained > 0,
      unlockedNewSocket = xpResult.unlockedNewSocket,
      supportChoices = supportChoices,
      supportPicked = supportChoices.isEmpty(),
      artifactChoices = artifactChoices,
      artifactPicked = artifactChoices.isEmpty(),
    )

    return run.copy(
      rng = run.rng.copy(rewards = currentRewardRng),
      hp = (run.hp + healAfter).coerceAtMost(run.maxHp),
      cinders = run.cinders + totalCinders,
      flasks = (run.flasks + flaskDrop).coerceAtMost(run.maxFlasks),
      remnants = nextRemnants,
      combat = wonCombat,
      pendingReward = reward,
      growth = xpResult.growth,
      stats = run.stats.copy(enemiesSlain = run.stats.enemiesSlain + 1),
    )
  }

  private fun handlePickRewardCard(run: RunState, cardDefId: String): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    if (reward.cardPicked || cardDefId !in reward.cardChoices) {
      return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    }
    val newCard = CardInstance(uid = run.nextCardUid, defId = cardDefId, upgraded = false)
    val next = run.copy(
      masterDeck = run.masterDeck + newCard,
      nextCardUid = run.nextCardUid + 1,
      pendingReward = reward.copy(cardPicked = true),
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleSkipRewardCard(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    if (reward.cardPicked) return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    return CommandResult(
      run.copy(
        cinders = run.cinders + SKIP_CARD_CINDER_BONUS,
        pendingReward = reward.copy(cardPicked = true),
      ),
      accepted = true,
    )
  }

  private fun handleRerollRewardCards(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.WRONG_PHASE)
    val pool = GameCatalog.rewardPoolFor(run.heroClass)
    val preferredPool = pool.filter { it.id !in reward.cardChoices }.ifEmpty { pool }
    val (shuffled, nextRng) = run.rng.rewards.shuffled(preferredPool)
    val freshChoices = shuffled.take(3).map { it.id }
    val next = run.copy(
      rng = run.rng.copy(rewards = nextRng),
      cinders = run.cinders - REROLL_CARDS_COST,
      pendingReward = reward.copy(
        cardChoices = freshChoices,
        rerollUsed = true,
      ),
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleUnlockPassiveNode(run: RunState, nodeId: String): CommandResult {
    val updatedGrowth = run.growth.unlockPassive(nodeId)
    val (nextMaxHp, nextHp) = if (nodeId == "pass_iron_1") {
      (run.maxHp + 6) to (run.hp + 6).coerceAtMost(run.maxHp + 6)
    } else {
      run.maxHp to run.hp
    }
    return CommandResult(
      run.copy(
        maxHp = nextMaxHp,
        hp = nextHp,
        growth = updatedGrowth,
      ),
      accepted = true,
    )
  }

  private fun handleSocketSupport(run: RunState, supportId: String, replaceSlot: Int): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.WRONG_PHASE)
    val updatedGrowth = run.growth.socketSupport(supportId, replaceSlot)
    return CommandResult(
      run.copy(
        growth = updatedGrowth,
        pendingReward = reward.copy(supportPicked = true),
      ),
      accepted = true,
    )
  }

  private fun handleSkipSupportChoice(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.WRONG_PHASE)
    return CommandResult(
      run.copy(
        cinders = run.cinders + 8,
        pendingReward = reward.copy(supportPicked = true),
      ),
      accepted = true,
    )
  }

  private fun handleBindArtifact(run: RunState, artifactId: String): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.WRONG_PHASE)
    val updatedGrowth = run.growth.bindArtifact(artifactId)
    return CommandResult(
      run.copy(
        growth = updatedGrowth,
        pendingReward = reward.copy(artifactPicked = true),
      ),
      accepted = true,
    )
  }

  private fun handleSkipArtifactChoice(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.WRONG_PHASE)
    return CommandResult(
      run.copy(
        cinders = run.cinders + 8,
        pendingReward = reward.copy(artifactPicked = true),
      ),
      accepted = true,
    )
  }

  private fun handleClaimRewardRelic(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    val relicId = reward.relicRewardId ?: return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    if (reward.relicPicked) return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    val updated = applyRelicPickup(run.copy(pendingReward = reward.copy(relicPicked = true)), relicId)
    return CommandResult(updated, accepted = true)
  }

  private fun handleSkipRewardRelic(run: RunState): CommandResult {
    val reward = run.pendingReward ?: return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    if (reward.relicRewardId == null || reward.relicPicked) {
      return CommandResult(run, false, CommandRejectionReason.REWARD_ALREADY_CLAIMED)
    }
    return CommandResult(run.copy(pendingReward = reward.copy(relicPicked = true)), accepted = true)
  }

  private fun handleFinishRewards(run: RunState): CommandResult {
    val node = run.actMap.nodes.firstOrNull { it.id == run.currentNodeId }
    val cleared = run.copy(pendingReward = null, combat = null)
    val next = if (node?.type == MapNodeType.BOSS && cleared.currentAct < 3) {
      val nextAct = cleared.currentAct + 1
      val (nextMap, nextMapRng) = CampaignGenerator.generateActMap(nextAct, cleared.rng.map)
      cleared.copy(
        rng = cleared.rng.copy(map = nextMapRng),
        currentAct = nextAct,
        actMap = nextMap,
        currentNodeId = null,
        hp = (cleared.hp + 15).coerceAtMost(cleared.maxHp),
      )
    } else {
      cleared
    }
    return CommandResult(next, accepted = true)
  }

  private fun handleCampfireRest(run: RunState): CommandResult {
    val healed = maxOf(14, (run.maxHp * 35) / 100)
    val next = run.copy(
      hp = (run.hp + healed).coerceAtMost(run.maxHp),
      flasks = (run.flasks + 1).coerceAtMost(run.maxFlasks),
      campfireUsed = true,
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleCampfireUpgradeCard(run: RunState, cardUid: Long): CommandResult {
    if (run.masterDeck.none { it.uid == cardUid && !it.upgraded }) {
      return CommandResult(run, false, CommandRejectionReason.CARD_NOT_IN_HAND)
    }
    val updatedDeck = run.masterDeck.map { if (it.uid == cardUid) it.copy(upgraded = true) else it }
    return CommandResult(run.copy(masterDeck = updatedDeck, campfireUsed = true), accepted = true)
  }

  /**
   * Creative Risk-Reward Campfire choice ("Stoke the Kiln"):
   * Sacrifice 8 HP to scavenge +35 Cinders and temper a random un-upgraded card!
   */
  private fun handleCampfireGamble(run: RunState): CommandResult {
    var nextDeck = run.masterDeck
    var nextRng = run.rng.rewards
    val candidates = nextDeck.filter { !it.upgraded }
    if (candidates.isNotEmpty()) {
      val (idx, r1) = nextRng.nextInt(candidates.size)
      nextRng = r1
      val chosenUid = candidates[idx].uid
      nextDeck = nextDeck.map { if (it.uid == chosenUid) it.copy(upgraded = true) else it }
    }
    val next = run.copy(
      rng = run.rng.copy(rewards = nextRng),
      hp = (run.hp - CAMPFIRE_GAMBLE_HP_COST).coerceAtLeast(1),
      cinders = run.cinders + CAMPFIRE_GAMBLE_CINDERS,
      masterDeck = nextDeck,
      campfireUsed = true,
    )
    return CommandResult(next, accepted = true)
  }

  private fun openEvent(run: RunState): RunState {
    val eligible = GameCatalog.events.filter { run.currentAct in it.actMin..it.actMax }
    val (idx, nextRng) = run.rng.rewards.nextInt(eligible.size)
    val def = eligible[idx]
    return run.copy(
      rng = run.rng.copy(rewards = nextRng),
      activeEvent = EventState(
        eventId = def.id,
        title = def.title,
        body = def.body,
        choices = def.choices,
      ),
    )
  }

  private fun handleChooseEventOption(run: RunState, choiceId: String): CommandResult {
    val evt = run.activeEvent ?: return CommandResult(run, false, CommandRejectionReason.EVENT_UNAVAILABLE)
    val choice = evt.choices.firstOrNull { it.id == choiceId }
      ?: return CommandResult(run, false, CommandRejectionReason.EVENT_UNAVAILABLE)
    if (run.cinders < choice.minCindersRequired || run.hp < choice.minHpRequired || run.flasks < choice.minFlasksRequired) {
      return CommandResult(run, false, CommandRejectionReason.EVENT_UNAVAILABLE)
    }

    var nextMaxHp = (run.maxHp + choice.maxHpDelta).coerceAtLeast(10)
    var nextHp = (run.hp + choice.hpDelta).coerceIn(1, nextMaxHp)
    var nextCinders = (run.cinders + choice.cindersDelta).coerceAtLeast(0)
    var nextFlasks = (run.flasks + choice.flasksDelta).coerceIn(1, run.maxFlasks)
    var nextDeck = run.masterDeck
    var nextUid = run.nextCardUid
    var nextRewardRng = run.rng.rewards
    var updatedRun = run

    if (choice.upgradeRandomCard) {
      val candidates = nextDeck.filter { !it.upgraded }
      if (candidates.isNotEmpty()) {
        val (uIdx, r1) = nextRewardRng.nextInt(candidates.size)
        nextRewardRng = r1
        val targetUid = candidates[uIdx].uid
        nextDeck = nextDeck.map { if (it.uid == targetUid) it.copy(upgraded = true) else it }
      }
    }

    if (choice.removeRandomCard && nextDeck.size > 4) {
      val (remIdx, rRem) = nextRewardRng.nextInt(nextDeck.size)
      nextRewardRng = rRem
      val removedUid = nextDeck[remIdx].uid
      nextDeck = nextDeck.filterNot { it.uid == removedUid }
    }

    if (choice.addCardDefId != null) {
      nextDeck = nextDeck + CardInstance(uid = nextUid++, defId = choice.addCardDefId, upgraded = false)
    }

    if (choice.grantRandomRelic) {
      val unowned = GameCatalog.rewardRelics().filter { it.id !in run.relics }
      if (unowned.isNotEmpty()) {
        val (rIdx, r2) = nextRewardRng.nextInt(unowned.size)
        nextRewardRng = r2
        updatedRun = applyRelicPickup(updatedRun, unowned[rIdx].id)
        nextMaxHp = updatedRun.maxHp
        nextFlasks = maxOf(nextFlasks, updatedRun.flasks).coerceAtMost(updatedRun.maxFlasks)
      }
    }

    val resolvedRun = checkGameOver(
      updatedRun.copy(
        rng = updatedRun.rng.copy(rewards = nextRewardRng),
        hp = nextHp,
        maxHp = nextMaxHp,
        cinders = nextCinders,
        flasks = nextFlasks,
        masterDeck = nextDeck,
        nextCardUid = nextUid,
        activeEvent = evt.copy(resolvedSummary = choice.summary),
      )
    )
    return CommandResult(resolvedRun, accepted = true)
  }

  private fun openMerchant(run: RunState): RunState {
    val (shuffledCards, r1) = run.rng.rewards.shuffled(GameCatalog.rewardPoolFor(run.heroClass))
    val unownedRelics = GameCatalog.rewardRelics().filter { it.id !in run.relics }
    val (shuffledRelics, r2) = r1.shuffled(unownedRelics)
    return run.copy(
      rng = run.rng.copy(rewards = r2),
      shopStock = ShopStock(
        cardIds = shuffledCards.take(3).map { it.id },
        relicIds = shuffledRelics.take(2).map { it.id },
      ),
    )
  }

  private fun openTreasure(run: RunState): RunState {
    val unowned = GameCatalog.rewardRelics().filter { it.id !in run.relics }
    val (shuffledRelics, r1) = run.rng.rewards.shuffled(unowned)
    val relicId = shuffledRelics.firstOrNull()?.id
    return run.copy(
      rng = run.rng.copy(rewards = r1),
      cinders = run.cinders + 40,
      pendingReward = RewardBundle(
        cindersEarned = 40,
        cardChoices = emptyList(),
        relicRewardId = relicId,
        flaskRefill = 1,
        cardPicked = true,
      ),
      flasks = (run.flasks + 1).coerceAtMost(run.maxFlasks),
    )
  }

  fun cardPrice(def: CardDef): Int = when (def.rarity) {
    CardRarity.STARTER -> 35
    CardRarity.COMMON -> 50
    CardRarity.UNCOMMON -> 75
    CardRarity.RARE -> 115
  }

  private fun handleShopBuyCard(run: RunState, cardId: String): CommandResult {
    val shop = run.shopStock ?: return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    if (cardId !in shop.cardIds) return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    val def = GameCatalog.cardById(cardId)
    val price = cardPrice(def)
    if (run.cinders < price) return CommandResult(run, false, CommandRejectionReason.INSUFFICIENT_CINDERS)
    val newCard = CardInstance(uid = run.nextCardUid, defId = cardId)
    val next = run.copy(
      cinders = run.cinders - price,
      masterDeck = run.masterDeck + newCard,
      nextCardUid = run.nextCardUid + 1,
      shopStock = shop.copy(cardIds = shop.cardIds - cardId),
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleShopBuyRelic(run: RunState, relicId: String): CommandResult {
    val shop = run.shopStock ?: return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    if (relicId !in shop.relicIds) return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    val relic = GameCatalog.relicById(relicId)
    if (run.cinders < relic.price) return CommandResult(run, false, CommandRejectionReason.INSUFFICIENT_CINDERS)
    val next = applyRelicPickup(
      run.copy(
        cinders = run.cinders - relic.price,
        shopStock = shop.copy(relicIds = shop.relicIds - relicId),
      ),
      relicId,
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleShopBuyFlask(run: RunState): CommandResult {
    val shop = run.shopStock ?: return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    if (shop.flaskPurchased || run.flasks >= run.maxFlasks) {
      return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    }
    if (run.cinders < shop.flaskPrice) return CommandResult(run, false, CommandRejectionReason.INSUFFICIENT_CINDERS)
    val next = run.copy(
      cinders = run.cinders - shop.flaskPrice,
      flasks = (run.flasks + 1).coerceAtMost(run.maxFlasks),
      shopStock = shop.copy(flaskPurchased = true),
    )
    return CommandResult(next, accepted = true)
  }

  private fun handleShopRemoveCard(run: RunState, cardUid: Long): CommandResult {
    val shop = run.shopStock ?: return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    if (shop.removeUsed) return CommandResult(run, false, CommandRejectionReason.SHOP_UNAVAILABLE)
    if (run.cinders < shop.removePrice) return CommandResult(run, false, CommandRejectionReason.INSUFFICIENT_CINDERS)
    if (run.masterDeck.size <= 4) return CommandResult(run, false, CommandRejectionReason.DECK_TOO_SMALL)
    if (run.masterDeck.none { it.uid == cardUid }) return CommandResult(run, false, CommandRejectionReason.CARD_NOT_IN_HAND)
    val next = run.copy(
      cinders = run.cinders - shop.removePrice,
      masterDeck = run.masterDeck.filterNot { it.uid == cardUid },
      shopStock = shop.copy(removeUsed = true),
    )
    return CommandResult(next, accepted = true)
  }

  private fun applyRelicPickup(run: RunState, relicId: String): RunState {
    if (relicId in run.relics) return run
    var updated = run.copy(relics = run.relics + relicId)
    if (relicId == "relic_hermetic_stopper") {
      val nextMax = updated.maxFlasks + 1
      updated = updated.copy(
        maxFlasks = nextMax,
        flasks = (updated.flasks + 1).coerceAtMost(nextMax),
      )
    }
    return updated
  }

  fun formatSpectrum(halfSteps: Int): String {
    val whole = halfSteps / 2
    val frac = if (halfSteps % 2 == 1) 5 else 0
    return "$whole.${frac}x"
  }
}
