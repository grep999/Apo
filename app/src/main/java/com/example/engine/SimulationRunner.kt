package com.example.engine

/**
 * Deterministic automated campaign simulator for playtesting balance, progression, and invariants.
 * Strictly exercises the authoritative Match-3 swap combat contract (`SwapCells`) and reports
 * honest terminal completion counts versus stalled/incomplete runs.
 */
data class ClassSimulationStats(
  val runsAttempted: Int,
  val runsCompleted: Int,
  val incompleteRuns: Int,
  val wins: Int,
  val hpDefeats: Int,
  val zeroFlaskCollapses: Int,
  val avgTurnsPerRun: Double,
  val avgActReached: Double,
)

data class SimulationReport(
  val runsAttempted: Int,
  val runsCompleted: Int,
  val incompleteRuns: Int,
  val turnCapHits: Int,
  val nodeCapHits: Int,
  val wins: Int,
  val hpDefeats: Int,
  val zeroFlaskCollapses: Int,
  val avgTurnsPerRun: Double,
  val avgActReached: Double,
  val perClassBreakdown: Map<HeroClass, ClassSimulationStats> = emptyMap(),
)

data class SingleRunSimulationOutcome(
  val finalRun: RunState,
  val completed: Boolean,
  val hitTurnCap: Boolean,
  val hitNodeCap: Boolean,
)

object SimulationRunner {

  const val MAX_NODES_PER_RUN = 60
  const val MAX_TURNS_PER_COMBAT = 45

  fun simulateFullRunWithOutcome(
    heroClass: HeroClass,
    seedText: String,
    previousRunDeckIds: List<String> = emptyList(),
    dreadLevel: Int = 0,
  ): SingleRunSimulationOutcome {
    var run = GameEngine.startNewRun(
      heroClass = heroClass,
      seedText = seedText,
      previousRunDeckIds = previousRunDeckIds,
      dreadLevel = dreadLevel,
    )
    var nodeSafety = 0
    var hitTurnCap = false
    var hitNodeCap = false

    while (run.gameOverReason == null && !run.runWon && nodeSafety < MAX_NODES_PER_RUN) {
      nodeSafety++
      val available = GameEngine.availableNodeIds(run)
      if (available.isEmpty()) break

      val preferredNode = available
        .mapNotNull { id -> run.actMap.nodes.firstOrNull { it.id == id } }
        .minByOrNull { node ->
          when (node.type) {
            MapNodeType.CAMPFIRE -> if (run.hp < run.maxHp * 0.65f || run.flasks <= 1) 0 else 4
            MapNodeType.TREASURE -> 1
            MapNodeType.EVENT -> 2
            MapNodeType.MERCHANT -> if (run.cinders >= 70) 2 else 5
            MapNodeType.COMBAT -> 3
            MapNodeType.SHADE -> 4
            MapNodeType.ELITE -> if (run.hp > run.maxHp * 0.72f) 4 else 6
            MapNodeType.BOSS -> 7
          }
        } ?: break

      run = GameEngine.enterNode(run, preferredNode.id)

      when {
        run.combat != null -> {
          val (afterCombat, combatHitTurnCap) = playCombatBotWithStatus(run)
          run = afterCombat
          if (combatHitTurnCap) {
            hitTurnCap = true
            break
          }
          if (run.runWon || run.gameOverReason != null) break
          if (run.combat?.phase == TurnPhase.COMBAT_WON) {
            run = resolveRewardsBot(run)
          } else if (run.combat != null) {
            // Combat exited neither won nor terminated — stop cleanly so we never misreport completion.
            break
          }
        }
        run.pendingReward != null -> {
          run = resolveRewardsBot(run)
        }
        run.activeEvent != null -> {
          val choice = run.activeEvent.choices.firstOrNull()
          if (choice != null) {
            run = GameEngine.chooseEventOption(run, choice.id)
          }
          if (run.gameOverReason == null && !run.runWon) {
            run = GameEngine.leaveNonCombatNode(run)
          }
        }
        run.shopStock != null -> {
          val stock = run.shopStock
          if (run.flasks < run.maxFlasks && !stock.flaskPurchased && run.cinders >= stock.flaskPrice) {
            run = GameEngine.shopBuyFlask(run)
          }
          val buyRelicId = run.shopStock?.relicIds?.firstOrNull { id ->
            GameCatalog.relicById(id).price <= run.cinders
          }
          if (buyRelicId != null) {
            val res = GameEngine.execute(run, GameCommand.ShopBuyRelic(buyRelicId))
            if (res.accepted) run = res.run
          }
          val buyCardId = run.shopStock?.cardIds?.firstOrNull()
          if (buyCardId != null && run.masterDeck.size < 18) {
            val res = GameEngine.execute(run, GameCommand.ShopBuyCard(buyCardId))
            if (res.accepted) run = res.run
          }
          run = GameEngine.leaveNonCombatNode(run)
        }
        run.inNonCombatNode -> {
          if (run.hp < run.maxHp * 0.75f || run.flasks < run.maxFlasks) {
            run = GameEngine.campfireRest(run)
          } else {
            val unupgraded = run.masterDeck.firstOrNull { !it.upgraded }
            run = if (unupgraded != null) {
              GameEngine.campfireUpgradeCard(run, unupgraded.uid)
            } else {
              GameEngine.campfireRest(run)
            }
          }
          run = GameEngine.leaveNonCombatNode(run)
        }
      }
    }

    val isTerminal = run.runWon || run.gameOverReason != null
    if (!isTerminal && nodeSafety >= MAX_NODES_PER_RUN) {
      hitNodeCap = true
    }

    return SingleRunSimulationOutcome(
      finalRun = run,
      completed = isTerminal,
      hitTurnCap = hitTurnCap,
      hitNodeCap = hitNodeCap,
    )
  }

  fun simulateFullRun(
    heroClass: HeroClass,
    seedText: String,
    previousRunDeckIds: List<String> = emptyList(),
    dreadLevel: Int = 0,
  ): RunState = simulateFullRunWithOutcome(
    heroClass = heroClass,
    seedText = seedText,
    previousRunDeckIds = previousRunDeckIds,
    dreadLevel = dreadLevel,
  ).finalRun

  private fun resolveRewardsBot(initialRun: RunState): RunState {
    var run = initialRun
    if (run.combat?.phase == TurnPhase.COMBAT_WON) {
      run = GameEngine.openRewardsScreen(run)
    }

    // Allocate available Upaya passive points along unlocked branches
    var passiveGuard = 0
    while (run.growth.unspentPassivePoints > 0 && passiveGuard < 6) {
      passiveGuard++
      val nextNode = ProgressionCatalog.passiveNodes
        .firstOrNull { run.growth.canUnlockPassive(it.id) } ?: break
      val res = GameEngine.execute(run, GameCommand.UnlockPassiveNode(nextNode.id))
      if (!res.accepted) break
      run = res.run
    }

    val reward = run.pendingReward ?: return run
    if (reward.relicRewardId != null && !reward.relicPicked) {
      run = GameEngine.claimRewardRelic(run)
    }

    val afterRelic = run.pendingReward
    if (afterRelic != null && afterRelic.supportChoices.isNotEmpty() && !afterRelic.supportPicked) {
      val pick = afterRelic.supportChoices.first()
      val socketRes = GameEngine.execute(run, GameCommand.SocketSupport(pick))
      run = if (socketRes.accepted) socketRes.run else GameEngine.execute(run, GameCommand.SkipSupportChoice).run
    }

    val afterSupport = run.pendingReward
    if (afterSupport != null && afterSupport.artifactChoices.isNotEmpty() && !afterSupport.artifactPicked) {
      val pick = afterSupport.artifactChoices.first()
      val bindRes = GameEngine.execute(run, GameCommand.BindArtifact(pick))
      run = if (bindRes.accepted) bindRes.run else GameEngine.execute(run, GameCommand.SkipArtifactChoice).run
    }

    val afterArtifact = run.pendingReward
    if (afterArtifact != null && afterArtifact.cardChoices.isNotEmpty() && !afterArtifact.cardPicked) {
      run = if (run.masterDeck.size < 16) {
        GameEngine.pickRewardCard(run, afterArtifact.cardChoices.first())
      } else {
        GameEngine.skipRewardCard(run)
      }
    }

    return GameEngine.finishRewardsAndAdvanceIfBoss(run)
  }

  private fun playCombatBotWithStatus(initialRun: RunState): Pair<RunState, Boolean> {
    var run = initialRun
    var turnGuard = 0

    while (
      run.combat != null &&
      run.combat.phase != TurnPhase.COMBAT_WON &&
      run.combat.phase != TurnPhase.GAME_OVER &&
      run.gameOverReason == null &&
      turnGuard < MAX_TURNS_PER_COMBAT
    ) {
      turnGuard++

      // Emergency flask if low HP and >1 flask remaining
      if (run.hp < run.maxHp * 0.42f && run.flasks > 1) {
        run = GameEngine.useFlask(run)
        if (run.gameOverReason != null) break
      }

      // Witness Mind Illusion once per turn while illusion veil remains active
      val preWeaveCombat = run.combat
      if (preWeaveCombat != null && !preWeaveCombat.mindIllusion.shattered &&
        !preWeaveCombat.mindIllusion.witnessUsedThisTurn
      ) {
        val wit = GameEngine.execute(run, GameCommand.WitnessIllusion)
        if (wit.accepted) run = wit.run
        if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
      }

      // WEAVE phase: strictly authoritative Match-3 SwapCells (no legacy TapCell fallback)
      var moveGuard = 0
      while (run.combat?.phase == TurnPhase.WEAVE && run.combat.tapsRemaining > 0 && moveGuard < 8) {
        moveGuard++
        val combat = run.combat
        if (combat.player.overdriveCharge >= PlayerCombatState.MAX_OVERDRIVE) {
          val ult = GameEngine.execute(run, GameCommand.TriggerOverdrive)
          if (ult.accepted) {
            run = ult.run
            if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
            continue
          }
        }
        val bestSwap = bestBoardSwap(combat.board, combat.spectrum) ?: break
        val swapRes = GameEngine.execute(
          run,
          GameCommand.SwapCells(bestSwap.from.row, bestSwap.from.col, bestSwap.to.row, bestSwap.to.col),
        )
        if (!swapRes.accepted) break
        run = swapRes.run
        if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
      }
      if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break

      // Move to CAST phase
      if (run.combat?.phase == TurnPhase.WEAVE) {
        run = GameEngine.proceedToCast(run)
      }

      // Fire Signature Overdrive if charged during WEAVE
      if (run.combat?.phase == TurnPhase.CAST &&
        run.combat.player.overdriveCharge >= PlayerCombatState.MAX_OVERDRIVE
      ) {
        val ult = GameEngine.execute(run, GameCommand.TriggerOverdrive)
        if (ult.accepted) run = ult.run
        if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
      }

      // CAST phase: greedily play affordable cards, prioritizing defense if enemy attacks hard
      var castSafety = 0
      while (run.combat?.phase == TurnPhase.CAST && castSafety < 8) {
        castSafety++
        val combat = run.combat
        val incomingAtk = intentDamage(combat.enemy.slot1Intent) + intentDamage(combat.enemy.slot2Intent)
        val needDefense = incomingAtk > combat.player.guardBlock + 6
        val playable = combat.combatDeck.hand.firstOrNull { inst ->
          val def = GameCatalog.cardById(inst.defId)
          combat.player.vigil >= def.vigilCost &&
            GameEngine.tryPayCardCost(combat.resources, def, inst.upgraded) != null &&
            (!needDefense || def.blockGain > 0 || def.skipWallRise || def.damage >= 8)
        } ?: combat.combatDeck.hand.firstOrNull { inst ->
          val def = GameCatalog.cardById(inst.defId)
          combat.player.vigil >= def.vigilCost &&
            GameEngine.tryPayCardCost(combat.resources, def, inst.upgraded) != null
        } ?: break

        val castResult = GameEngine.execute(run, GameCommand.CastCard(playable.uid))
        if (!castResult.accepted) break
        run = castResult.run
        if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
      }

      if (run.combat?.phase == TurnPhase.COMBAT_WON || run.gameOverReason != null) break
      run = GameEngine.endTurn(run)
    }

    val finalCombat = run.combat
    val stillUnfinished = finalCombat != null &&
      finalCombat.phase != TurnPhase.COMBAT_WON &&
      finalCombat.phase != TurnPhase.GAME_OVER &&
      run.gameOverReason == null &&
      !run.runWon

    return run to (stillUnfinished && turnGuard >= MAX_TURNS_PER_COMBAT)
  }

  private fun intentDamage(intent: EnemyIntent): Int = when (intent) {
    is EnemyIntent.Attack -> intent.damage * intent.hits
    is EnemyIntent.SiphonFlask -> intent.damage
    is EnemyIntent.InflictBrand -> intent.damage
    is EnemyIntent.InflictFog -> intent.damage
    is EnemyIntent.SealColumn -> intent.damage
    else -> 0
  }

  private fun bestBoardSwap(board: BoardState, spectrum: SpectrumState): ValidSwapOption? =
    BoardEngine.findValidSwaps(board, spectrum).firstOrNull()

  fun runBatchSimulation(
    seedsPerClass: Int = 3,
    dreadLevel: Int = 0,
  ): SimulationReport {
    require(seedsPerClass >= 0) { "seedsPerClass must be non-negative" }
    require(dreadLevel in 0..3) { "dreadLevel must be between 0 and 3" }
    var attempted = 0
    var completed = 0
    var incomplete = 0
    var turnCapHits = 0
    var nodeCapHits = 0
    var wins = 0
    var hpDefeats = 0
    var flaskDefeats = 0
    var totalTurns = 0
    var totalAct = 0
    var shadePriorDeck: List<String> = emptyList()
    val perClass = LinkedHashMap<HeroClass, ClassSimulationStats>()

    for (hero in HeroClass.entries) {
      var cAttempted = 0
      var cCompleted = 0
      var cIncomplete = 0
      var cWins = 0
      var cHpDefeats = 0
      var cFlaskDefeats = 0
      var cTurns = 0
      var cAct = 0

      for (i in 1..seedsPerClass) {
        val outcome = simulateFullRunWithOutcome(
          heroClass = hero,
          seedText = "SIM-${hero.name}-$i-D$dreadLevel",
          previousRunDeckIds = shadePriorDeck,
          dreadLevel = dreadLevel,
        )
        val finalRun = outcome.finalRun
        // EFB-006: Only defeated runs overwrite the persistent Shade deck snapshot
        if (finalRun.gameOverReason != null) {
          shadePriorDeck = finalRun.masterDeck.map { it.defId }
        }
        attempted++
        cAttempted++
        if (outcome.hitTurnCap) turnCapHits++
        if (outcome.hitNodeCap) nodeCapHits++

        if (outcome.completed) {
          completed++
          cCompleted++
          when {
            finalRun.runWon -> {
              wins++
              cWins++
            }
            finalRun.gameOverReason == GameOverReason.ZERO_FLASKS -> {
              flaskDefeats++
              cFlaskDefeats++
            }
            else -> {
              hpDefeats++
              cHpDefeats++
            }
          }
        } else {
          incomplete++
          cIncomplete++
        }
        totalTurns += finalRun.stats.turnsTaken
        totalAct += finalRun.currentAct
        cTurns += finalRun.stats.turnsTaken
        cAct += finalRun.currentAct
      }

      perClass[hero] = ClassSimulationStats(
        runsAttempted = cAttempted,
        runsCompleted = cCompleted,
        incompleteRuns = cIncomplete,
        wins = cWins,
        hpDefeats = cHpDefeats,
        zeroFlaskCollapses = cFlaskDefeats,
        avgTurnsPerRun = if (cAttempted == 0) 0.0 else cTurns.toDouble() / cAttempted,
        avgActReached = if (cAttempted == 0) 0.0 else cAct.toDouble() / cAttempted,
      )
    }

    // These are accounting invariants, not presentation estimates.
    check(attempted == completed + incomplete) {
      "Simulation accounting mismatch: attempted=$attempted completed=$completed incomplete=$incomplete"
    }
    check(turnCapHits <= incomplete) { "A turn-capped simulation cannot be counted as completed" }
    check(nodeCapHits <= incomplete) { "A node-capped simulation cannot be counted as completed" }
    check(perClass.values.sumOf { it.runsAttempted } == attempted)
    check(perClass.values.sumOf { it.runsCompleted } == completed)
    check(perClass.values.sumOf { it.incompleteRuns } == incomplete)

    return SimulationReport(
      runsAttempted = attempted,
      runsCompleted = completed,
      incompleteRuns = incomplete,
      turnCapHits = turnCapHits,
      nodeCapHits = nodeCapHits,
      wins = wins,
      hpDefeats = hpDefeats,
      zeroFlaskCollapses = flaskDefeats,
      avgTurnsPerRun = if (attempted == 0) 0.0 else totalTurns.toDouble() / attempted,
      avgActReached = if (attempted == 0) 0.0 else totalAct.toDouble() / attempted,
      perClassBreakdown = perClass,
    )
  }
}
