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

enum class RouteRewardPolicy { BALANCED, SURVIVAL, ELITE_HUNTER, TREASURE_SEEKER, DECK_CURATOR }
enum class CombatPolicy { BALANCED, DEFENSIVE, AGGRESSIVE }

data class ClassProgressionMetrics(
  val avgLevel: Double,
  val avgXpRemainder: Double,
  val avgUnspentPassivePoints: Double,
  val avgUnlockedPassives: Double,
  val avgSupportSocketsFilled: Double,
  val avgArtifactsOwned: Double,
  val avgCinders: Double,
  val avgDeckSize: Double,
  val avgHp: Double,
  val avgFlasks: Double,
)

data class PolicyMatrixCell(
  val routeRewardPolicy: RouteRewardPolicy,
  val combatPolicy: CombatPolicy,
  val report: SimulationReport,
  val progressionByClass: Map<HeroClass, ClassProgressionMetrics>,
)

data class PolicyMatrixReport(
  val seedsPerClass: Int,
  val dreadLevel: Int,
  val cells: List<PolicyMatrixCell>,
)

object SimulationRunner {

  const val MAX_NODES_PER_RUN = 60
  const val MAX_TURNS_PER_COMBAT = 45

  private fun routeNodeScore(run: RunState, node: MapNode, policy: RouteRewardPolicy): Int = when (policy) {
    RouteRewardPolicy.BALANCED -> when (node.type) {
      MapNodeType.CAMPFIRE -> if (run.hp < run.maxHp * 0.65f || run.flasks <= 1) 0 else 4
      MapNodeType.TREASURE -> 1
      MapNodeType.EVENT -> 2
      MapNodeType.MERCHANT -> if (run.cinders >= 70) 2 else 5
      MapNodeType.COMBAT -> 3
      MapNodeType.SHADE -> 4
      MapNodeType.ELITE -> if (run.hp > run.maxHp * 0.72f) 4 else 6
      MapNodeType.BOSS -> 7
    }
    RouteRewardPolicy.SURVIVAL -> when (node.type) {
      MapNodeType.CAMPFIRE -> if (run.hp < run.maxHp * 0.78f || run.flasks <= 2) 0 else 5
      MapNodeType.TREASURE -> 1
      MapNodeType.EVENT -> 2
      MapNodeType.MERCHANT -> if (run.cinders >= 45) 3 else 6
      MapNodeType.COMBAT -> 4
      MapNodeType.SHADE -> 6
      MapNodeType.ELITE -> 8
      MapNodeType.BOSS -> 9
    }
    RouteRewardPolicy.ELITE_HUNTER -> when (node.type) {
      MapNodeType.ELITE -> 0
      MapNodeType.SHADE -> 1
      MapNodeType.COMBAT -> 2
      MapNodeType.TREASURE -> 3
      MapNodeType.CAMPFIRE -> if (run.hp < run.maxHp * 0.5f) 1 else 4
      MapNodeType.EVENT -> 5
      MapNodeType.MERCHANT -> 6
      MapNodeType.BOSS -> 7
    }
    RouteRewardPolicy.TREASURE_SEEKER -> when (node.type) {
      MapNodeType.TREASURE -> 0
      MapNodeType.CAMPFIRE -> if (run.hp < run.maxHp * 0.65f || run.flasks <= 1) 1 else 4
      MapNodeType.EVENT -> 2
      MapNodeType.MERCHANT -> 3
      MapNodeType.COMBAT -> 4
      MapNodeType.SHADE -> 5
      MapNodeType.ELITE -> 6
      MapNodeType.BOSS -> 7
    }
    RouteRewardPolicy.DECK_CURATOR -> when (node.type) {
      MapNodeType.MERCHANT -> if (run.cinders >= 60) 0 else 5
      MapNodeType.EVENT -> 1
      MapNodeType.TREASURE -> 2
      MapNodeType.CAMPFIRE -> 3
      MapNodeType.COMBAT -> 4
      MapNodeType.ELITE -> 5
      MapNodeType.SHADE -> 6
      MapNodeType.BOSS -> 7
    }
  }

  /** Deterministic event policy: never select an option the player cannot currently afford. */
  fun firstAffordableEventChoice(run: RunState): EventChoiceDef? =
    run.activeEvent?.choices?.firstOrNull { choice ->
      run.cinders >= choice.minCindersRequired &&
        run.hp >= choice.minHpRequired &&
        run.flasks >= choice.minFlasksRequired
    }

  fun simulateFullRunWithOutcome(
    heroClass: HeroClass,
    seedText: String,
    previousRunDeckIds: List<String> = emptyList(),
    dreadLevel: Int = 0,
    routeRewardPolicy: RouteRewardPolicy = RouteRewardPolicy.BALANCED,
    combatPolicy: CombatPolicy = CombatPolicy.BALANCED,
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
        .minByOrNull { node -> routeNodeScore(run, node, routeRewardPolicy) } ?: break

      run = GameEngine.enterNode(run, preferredNode.id)

      when {
        run.combat != null -> {
          val (afterCombat, combatHitTurnCap) = playCombatBotWithStatus(run, combatPolicy)
          run = afterCombat
          if (combatHitTurnCap) {
            hitTurnCap = true
            break
          }
          if (run.runWon || run.gameOverReason != null) break
          if (run.combat?.phase == TurnPhase.COMBAT_WON) {
            run = resolveRewardsBot(run, routeRewardPolicy)
          } else if (run.combat != null) {
            // Combat exited neither won nor terminated — stop cleanly so we never misreport completion.
            break
          }
        }
        run.pendingReward != null -> {
          run = resolveRewardsBot(run, routeRewardPolicy)
        }
        run.activeEvent != null -> {
          val choice = firstAffordableEventChoice(run)
          if (choice == null) break
          val chosen = GameEngine.execute(run, GameCommand.ChooseEventOption(choice.id))
          if (!chosen.accepted) break
          run = chosen.run
          val left = GameEngine.execute(run, GameCommand.LeaveNode)
          if (!left.accepted) break
          run = left.run
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
    routeRewardPolicy: RouteRewardPolicy = RouteRewardPolicy.BALANCED,
    combatPolicy: CombatPolicy = CombatPolicy.BALANCED,
  ): RunState = simulateFullRunWithOutcome(
    heroClass = heroClass,
    seedText = seedText,
    previousRunDeckIds = previousRunDeckIds,
    dreadLevel = dreadLevel,
    routeRewardPolicy = routeRewardPolicy,
    combatPolicy = combatPolicy,
  ).finalRun

  private fun resolveRewardsBot(
    initialRun: RunState,
    policy: RouteRewardPolicy = RouteRewardPolicy.BALANCED,
  ): RunState {
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
      val pick = if (policy == RouteRewardPolicy.ELITE_HUNTER) afterRelic.supportChoices.last() else afterRelic.supportChoices.first()
      val replaceSlot = if (run.growth.socketedSupportIds.size >= run.growth.maxSockets) 0 else -1
      val socketRes = GameEngine.execute(run, GameCommand.SocketSupport(pick, replaceSlot))
      run = if (socketRes.accepted) socketRes.run else GameEngine.execute(run, GameCommand.SkipSupportChoice).run
    }

    val afterSupport = run.pendingReward
    if (afterSupport != null && afterSupport.artifactChoices.isNotEmpty() && !afterSupport.artifactPicked) {
      val pick = if (policy == RouteRewardPolicy.TREASURE_SEEKER) afterSupport.artifactChoices.last() else afterSupport.artifactChoices.first()
      val bindRes = GameEngine.execute(run, GameCommand.BindArtifact(pick))
      run = if (bindRes.accepted) bindRes.run else GameEngine.execute(run, GameCommand.SkipArtifactChoice).run
    }

    val afterArtifact = run.pendingReward
    if (afterArtifact != null && afterArtifact.cardChoices.isNotEmpty() && !afterArtifact.cardPicked) {
      val cardId = when (policy) {
        RouteRewardPolicy.SURVIVAL -> afterArtifact.cardChoices.maxByOrNull { id ->
          val card = GameCatalog.cardById(id)
          card.blockGain * 3 + card.gainFlasks * 8 + (if (card.skipWallRise) 10 else 0) + card.damage
        }
        RouteRewardPolicy.ELITE_HUNTER -> afterArtifact.cardChoices.maxByOrNull { id ->
          val card = GameCatalog.cardById(id)
          card.damage * 3 + card.spawnPrisms * 8 + card.shatterStoneCount * 4 + card.blockGain
        }
        RouteRewardPolicy.TREASURE_SEEKER -> afterArtifact.cardChoices.last()
        RouteRewardPolicy.DECK_CURATOR -> afterArtifact.cardChoices.firstOrNull { id ->
          GameCatalog.cardById(id).rarity != CardRarity.STARTER
        } ?: afterArtifact.cardChoices.first()
        RouteRewardPolicy.BALANCED -> afterArtifact.cardChoices.first()
      } ?: afterArtifact.cardChoices.first()
      run = if (run.masterDeck.size < 16) {
        GameEngine.pickRewardCard(run, cardId)
      } else {
        GameEngine.skipRewardCard(run)
      }
    }

    return GameEngine.finishRewardsAndAdvanceIfBoss(run)
  }

  private fun playCombatBotWithStatus(
    initialRun: RunState,
    policy: CombatPolicy = CombatPolicy.BALANCED,
  ): Pair<RunState, Boolean> {
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

      // Flask thresholds vary by policy to expose survival/aggression tradeoffs.
      val flaskThreshold = when (policy) {
        CombatPolicy.DEFENSIVE -> 0.56f
        CombatPolicy.BALANCED -> 0.42f
        CombatPolicy.AGGRESSIVE -> 0.28f
      }
      if (run.hp < run.maxHp * flaskThreshold && run.flasks > 1) {
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
        val bestSwap = bestBoardSwap(combat.board, combat.spectrum, policy) ?: break
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
        val defenseBuffer = when (policy) {
          CombatPolicy.DEFENSIVE -> 0
          CombatPolicy.BALANCED -> 6
          CombatPolicy.AGGRESSIVE -> 14
        }
        val needDefense = incomingAtk > combat.player.guardBlock + defenseBuffer
        val playable = combat.combatDeck.hand.filter { inst ->
          val def = GameCatalog.cardById(inst.defId)
          combat.player.vigil >= def.vigilCost &&
            GameEngine.tryPayCardCost(combat.resources, def, inst.upgraded) != null
        }.filter { inst ->
          val def = GameCatalog.cardById(inst.defId)
          !needDefense || def.blockGain > 0 || def.skipWallRise || def.damage >= 8
        }.maxByOrNull { inst ->
          val def = GameCatalog.cardById(inst.defId)
          when (policy) {
            CombatPolicy.DEFENSIVE -> def.blockGain * 4 + def.gainFlasks * 12 + (if (def.skipWallRise) 12 else 0) + def.damage
            CombatPolicy.BALANCED -> def.damage + def.blockGain * (if (needDefense) 2 else 1) + (if (def.skipWallRise) 4 else 0)
            CombatPolicy.AGGRESSIVE -> def.damage * 3 + def.spawnPrisms * 7 + def.shatterStoneCount * 3 + def.blockGain
          }
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

  private fun bestBoardSwap(board: BoardState, spectrum: SpectrumState, policy: CombatPolicy): ValidSwapOption? {
    val options = BoardEngine.findValidSwaps(board, spectrum)
    return when (policy) {
      CombatPolicy.BALANCED -> options.firstOrNull()
      CombatPolicy.DEFENSIVE -> options.maxByOrNull { it.preview.totalInkAmount * 3 + it.effectiveHalfSteps + if (it.advancesWheel) 4 else 0 }
      CombatPolicy.AGGRESSIVE -> options.maxByOrNull { it.previewDamage * 3 + it.effectiveHalfSteps + if (it.advancesWheel) 3 else 0 }
    }
  }

  /**
   * Matched-seed 5x3 policy sweep. Every policy cell sees identical seed labels per class,
   * so differences are attributable to policy choices rather than seed drift.
   */
  fun runPolicyMatrix(seedsPerClass: Int = 100, dreadLevel: Int = 0): PolicyMatrixReport {
    require(seedsPerClass >= 0) { "seedsPerClass must be non-negative" }
    require(dreadLevel in 0..3) { "dreadLevel must be between 0 and 3" }
    val cells = mutableListOf<PolicyMatrixCell>()
    for (routePolicy in RouteRewardPolicy.entries) {
      for (combatPolicy in CombatPolicy.entries) {
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
        val perClass = LinkedHashMap<HeroClass, ClassSimulationStats>()
        val progression = LinkedHashMap<HeroClass, ClassProgressionMetrics>()
        for (hero in HeroClass.entries) {
          var cAttempted = 0
          var cCompleted = 0
          var cIncomplete = 0
          var cWins = 0
          var cHpDefeats = 0
          var cFlaskDefeats = 0
          var cTurns = 0
          var cAct = 0
          val finalRuns = mutableListOf<RunState>()
          for (seedIndex in 1..seedsPerClass) {
            val outcome = simulateFullRunWithOutcome(
              heroClass = hero,
              seedText = "MATRIX-${hero.name}-$seedIndex-D$dreadLevel",
              dreadLevel = dreadLevel,
              routeRewardPolicy = routePolicy,
              combatPolicy = combatPolicy,
            )
            val finalRun = outcome.finalRun
            finalRuns += finalRun
            attempted++; cAttempted++
            if (outcome.hitTurnCap) turnCapHits++
            if (outcome.hitNodeCap) nodeCapHits++
            if (outcome.completed) {
              completed++; cCompleted++
              when {
                finalRun.runWon -> { wins++; cWins++ }
                finalRun.gameOverReason == GameOverReason.ZERO_FLASKS -> { flaskDefeats++; cFlaskDefeats++ }
                else -> { hpDefeats++; cHpDefeats++ }
              }
            } else { incomplete++; cIncomplete++ }
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
          progression[hero] = ClassProgressionMetrics(
            avgLevel = average(finalRuns) { it.growth.level.toDouble() },
            avgXpRemainder = average(finalRuns) { it.growth.xp.toDouble() },
            avgUnspentPassivePoints = average(finalRuns) { it.growth.unspentPassivePoints.toDouble() },
            avgUnlockedPassives = average(finalRuns) { it.growth.unlockedPassiveIds.size.toDouble() },
            avgSupportSocketsFilled = average(finalRuns) { it.growth.socketedSupportIds.size.toDouble() },
            avgArtifactsOwned = average(finalRuns) { it.growth.tantricArtifactIds.size.toDouble() },
            avgCinders = average(finalRuns) { it.cinders.toDouble() },
            avgDeckSize = average(finalRuns) { it.masterDeck.size.toDouble() },
            avgHp = average(finalRuns) { it.hp.toDouble() },
            avgFlasks = average(finalRuns) { it.flasks.toDouble() },
          )
        }
        check(attempted == completed + incomplete)
        check(turnCapHits <= incomplete && nodeCapHits <= incomplete)
        cells += PolicyMatrixCell(
          routeRewardPolicy = routePolicy,
          combatPolicy = combatPolicy,
          report = SimulationReport(
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
          ),
          progressionByClass = progression,
        )
      }
    }
    return PolicyMatrixReport(seedsPerClass = seedsPerClass, dreadLevel = dreadLevel, cells = cells)
  }

  private inline fun average(runs: List<RunState>, value: (RunState) -> Double): Double =
    if (runs.isEmpty()) 0.0 else runs.sumOf(value) / runs.size
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
