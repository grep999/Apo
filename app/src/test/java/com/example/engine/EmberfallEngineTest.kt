package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class EmberfallEngineTest {

  private fun gridOf(
    sealedColumns: Map<Int, Int> = emptyMap(),
    branded: Set<BoardCoord> = emptySet(),
    builder: (Int, Int) -> TileKind,
  ): BoardState {
    var nextId = 1L
    val grid = List(7) { r ->
      List(7) { c ->
        Tile(
          id = nextId++,
          kind = builder(r, c),
          isBranded = BoardCoord(r, c) in branded,
        )
      }
    }
    val incoming = List(7) { c ->
      Tile(id = nextId++, kind = TileKind.Playable(Hue.WHEEL_ORDER[c % 5]))
    }
    return BoardState(
      rows = 7,
      cols = 7,
      grid = grid,
      incomingRow = incoming,
      sealedColumns = sealedColumns,
      nextTileId = nextId,
    )
  }

  @Test
  fun `canonical classes hues signature relics and Unlit 12-card Gold starter match baseline`() {
    assertEquals(listOf(Hue.EMBER, Hue.GOLD, Hue.MOSS, Hue.TIDE, Hue.VIOLET), Hue.WHEEL_ORDER)

    assertEquals("Ashen Monk", HeroClass.ASHEN_MONK.displayName)
    assertEquals(Hue.EMBER, HeroClass.ASHEN_MONK.primaryHue)
    assertEquals(Hue.MOSS, HeroClass.ASHEN_MONK.secondaryHue)
    assertEquals("Prayer Beads", GameCatalog.relicById(HeroClass.ASHEN_MONK.signatureRelicId).name)

    assertEquals("Quarry Witch", HeroClass.QUARRY_WITCH.displayName)
    assertEquals(Hue.TIDE, HeroClass.QUARRY_WITCH.primaryHue)
    assertEquals(Hue.VIOLET, HeroClass.QUARRY_WITCH.secondaryHue)
    assertEquals("Chisel Wand", GameCatalog.relicById(HeroClass.QUARRY_WITCH.signatureRelicId).name)

    assertEquals("The Unlit", HeroClass.THE_UNLIT.displayName)
    assertEquals(Hue.GOLD, HeroClass.THE_UNLIT.primaryHue)
    assertEquals("Hollow Crown", GameCatalog.relicById(HeroClass.THE_UNLIT.signatureRelicId).name)

    val unlitStarter = GameCatalog.starterDeckIds(HeroClass.THE_UNLIT)
    assertEquals(12, unlitStarter.size)
    assertEquals(4, unlitStarter.count { it == "unlit_gold_strike" })
    assertEquals(4, unlitStarter.count { it == "unlit_ash_ward" })
    assertEquals(2, unlitStarter.count { it == "unlit_pop_shot" })
    assertEquals(1, unlitStarter.count { it == "unlit_sift" })
    assertEquals(1, unlitStarter.count { it == "unlit_hollow_crown" })
    assertTrue(unlitStarter.all { GameCatalog.cardById(it).frameHue == Hue.GOLD })
  }

  @Test
  fun `ADR-003 board paint weights follow deck frame formula max 0_08 or count plus 1 over size plus 5`() {
    val unlitDeck = GameCatalog.starterDeckIds(HeroClass.THE_UNLIT).mapIndexed { i, id ->
      CardInstance(uid = i.toLong() + 1L, defId = id)
    }
    val weights = BoardPaintDeck.computeHueWeights(unlitDeck)
    assertEquals(13.0 / 17.0, weights.getValue(Hue.GOLD), 1e-6)
    assertEquals(0.08, weights.getValue(Hue.EMBER), 1e-6)
    assertEquals(0.08, weights.getValue(Hue.TIDE), 1e-6)
  }

  @Test
  fun `ADR-004 prism joins single contacting hue tie broken by wheel order and never bridges unrelated hue clusters`() {
    val board = gridOf { r, c ->
      when {
        r == 3 && c == 3 -> TileKind.Prism
        r == 3 && c == 2 -> TileKind.Playable(Hue.EMBER)
        r == 3 && c == 1 -> TileKind.Playable(Hue.EMBER)
        r == 3 && c == 4 -> TileKind.Playable(Hue.TIDE)
        r == 3 && c == 5 -> TileKind.Playable(Hue.TIDE)
        else -> TileKind.Playable(Hue.VIOLET)
      }
    }

    val resEmber = BoardEngine.groupAt(board, 3, 3, currentRunHue = null)
    assertTrue(resEmber.isValid)
    assertEquals(Hue.EMBER, resEmber.resolvedHue)
    assertTrue(BoardCoord(3, 2) in resEmber.coords)
    assertFalse("Prism must NOT bridge Tide when resolving Ember", BoardCoord(3, 4) in resEmber.coords)

    val resTide = BoardEngine.groupAt(board, 3, 3, currentRunHue = Hue.MOSS)
    assertEquals(Hue.TIDE, resTide.resolvedHue)
    assertTrue(BoardCoord(3, 4) in resTide.coords)
    assertFalse("Prism must NOT bridge Ember when resolving Tide", BoardCoord(3, 2) in resTide.coords)
  }

  @Test
  fun `ADR-005 ordered spectrum wheel starts at Run 0 advances in sequence wraps and evaluates out-of-order at Run 0 then resets`() {
    var s = SpectrumState(currentHue = null, runLength = 0, halfSteps = 2)
    // First colored pop anchors wheel at Run 0 (1.0x)
    val (eff0, s0) = BoardEngine.previewSpectrumForPop(s, Hue.EMBER, usedPrism = false)
    assertEquals(2, eff0)
    assertEquals(Hue.EMBER, s0.currentHue)
    assertEquals(0, s0.runLength)
    assertEquals(2, s0.halfSteps)

    // Next consecutive hue increments to Run 1 (1.5x)
    val (eff1, s1) = BoardEngine.previewSpectrumForPop(s0, Hue.GOLD, usedPrism = false)
    assertEquals(3, eff1)
    assertEquals(Hue.GOLD, s1.currentHue)
    assertEquals(1, s1.runLength)

    s = BoardEngine.advanceSpectrum(s1, Hue.MOSS, usedPrism = false)
    s = BoardEngine.advanceSpectrum(s, Hue.TIDE, usedPrism = false)
    s = BoardEngine.advanceSpectrum(s, Hue.VIOLET, usedPrism = false)
    assertEquals(4, s.runLength)
    assertEquals(6, s.halfSteps) // 3.0x

    // Wrap Violet -> Ember
    s = BoardEngine.advanceSpectrum(s, Hue.EMBER, usedPrism = false)
    assertEquals(Hue.EMBER, s.currentHue)
    assertEquals(5, s.runLength)
    assertEquals(7, s.halfSteps) // 3.5x

    // Out-of-order pop evaluates at Run 0 (1.0x) and resets sequence to null
    val (effBad, outOfOrder) = BoardEngine.previewSpectrumForPop(s, Hue.TIDE, usedPrism = false)
    assertEquals(2, effBad)
    assertNull(outOfOrder.currentHue)
    assertEquals(0, outOfOrder.runLength)
    assertEquals(2, outOfOrder.halfSteps)

    // Repeated same hue also evaluates at Run 0 (1.0x) and resets sequence
    val (effRep, repeated) = BoardEngine.previewSpectrumForPop(s, Hue.EMBER, usedPrism = false)
    assertEquals(2, effRep)
    assertNull(repeated.currentHue)
    assertEquals(2, repeated.halfSteps)
  }

  @Test
  fun `ADR-006 ash yields zero ink deals n minus 1 chip after enemy block and Hollow Crown awards spark`() {
    val board = gridOf { r, c ->
      when {
        r == 3 && c in 2..3 -> TileKind.Playable(Hue.GOLD)
        r == 2 && c in 2..4 -> TileKind.Ash // (2,2), (2,3) adjacent to Gold group
        else -> TileKind.Playable(Hue.TIDE)
      }
    }
    val group = BoardEngine.groupAt(board, 3, 2, null)
    assertEquals(2, group.clearedAsh.size)
    assertEquals(1, group.ashChipDamage)
    assertEquals(Hue.GOLD, group.inkHue)
    assertEquals(1, group.inkAmount) // Ash adds 0 Ink

    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "ASH-CROWN")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    run = run.copy(
      combat = c0.copy(
        board = board,
        enemy = c0.enemy.copy(hp = 100, maxHp = 100, block = 50),
      )
    )
    val tapped = GameEngine.execute(run, GameCommand.TapCell(3, 2))
    assertTrue(tapped.accepted)
    val c1 = requireNotNull(tapped.run.combat)
    assertTrue(c1.enemy.block < 50)
    assertEquals(100, c1.enemy.hp) // Chip applied after Enemy Block
    assertEquals(2, c1.resources.spark) // Hollow Crown: +1 Spark per Ash popped
  }

  @Test
  fun `ADR-007 stone rejects direct tap and breaks only on qualifying 4-plus groups with orthogonal chain`() {
    val board = gridOf { r, c ->
      when {
        r == 3 && c in 1..4 -> TileKind.Playable(Hue.MOSS)
        r == 2 && c == 2 -> TileKind.Stone
        r == 1 && c == 2 -> TileKind.Stone
        r == 5 && c in 1..2 -> TileKind.Playable(Hue.EMBER)
        r == 5 && c == 3 -> TileKind.Stone
        else -> TileKind.Playable(Hue.TIDE)
      }
    }

    assertFalse(BoardEngine.groupAt(board, 2, 2).isValid)

    val smallGroup = BoardEngine.groupAt(board, 5, 1)
    assertTrue(smallGroup.isValid)
    assertTrue(smallGroup.shatteredStone.isEmpty())

    val fourGroup = BoardEngine.groupAt(board, 3, 1)
    assertTrue(fourGroup.isValid)
    assertEquals(setOf(BoardCoord(2, 2), BoardCoord(1, 2)), fourGroup.shatteredStone)
  }

  @Test
  fun `signature relics Prayer Beads and Chisel Wand execute canonical mechanics`() {
    var monkRun = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "BEADS-1")
    monkRun = GameEngine.enterNode(monkRun, GameEngine.availableNodeIds(monkRun).first())
    val c0 = requireNotNull(monkRun.combat)
    val board = gridOf { r, c ->
      if (r < 2) TileKind.Empty
      else if (r == 3 && c in 2..3) TileKind.Playable(Hue.EMBER)
      else TileKind.Playable(Hue.MOSS)
    }
    monkRun = monkRun.copy(combat = c0.copy(board = board, enemy = c0.enemy.copy(hp = 200, maxHp = 200)))
    val tap1 = GameEngine.execute(monkRun, GameCommand.TapCell(3, 2))
    assertTrue(tap1.accepted)
    assertEquals(3, tap1.run.combat?.resources?.inkOf(Hue.EMBER)) // 1 base + 2 Prayer Beads

    var witchRun = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "CHISEL-1")
    witchRun = GameEngine.enterNode(witchRun, GameEngine.availableNodeIds(witchRun).first())
    // Chiseling an Empty headroom socket (row 0) is rejected cleanly
    val emptyChisel = GameEngine.execute(witchRun, GameCommand.UseChiselWand(0, 0, Hue.VIOLET))
    assertFalse(emptyChisel.accepted)
    assertEquals(CommandRejectionReason.EMPTY_TILE, emptyChisel.rejectionReason)

    // Chiseling occupied row 3 succeeds once per fight
    val firstChisel = GameEngine.execute(witchRun, GameCommand.UseChiselWand(3, 0, Hue.VIOLET))
    assertTrue(firstChisel.accepted)
    assertEquals(TileKind.Playable(Hue.VIOLET), firstChisel.run.combat?.board?.tileAt(3, 0)?.kind)
    val secondChisel = GameEngine.execute(firstChisel.run, GameCommand.UseChiselWand(3, 1, Hue.TIDE))
    assertFalse(secondChisel.accepted)
    assertEquals(CommandRejectionReason.CHISEL_ALREADY_USED, secondChisel.rejectionReason)
  }

  @Test
  fun `ADR-001 transactional phase FSM enforces Weave before Cast and rejects invalid commands cleanly`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "FSM-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val combat = requireNotNull(run.combat)
    assertEquals(TurnPhase.WEAVE, combat.phase)

    val cardUid = combat.combatDeck.hand.first().uid
    val rejectedCast = GameEngine.execute(run, GameCommand.CastCard(cardUid))
    assertFalse(rejectedCast.accepted)
    assertEquals(CommandRejectionReason.WRONG_PHASE, rejectedCast.rejectionReason)
    assertEquals(run, rejectedCast.run)

    val toCast = GameEngine.execute(run, GameCommand.ProceedToCast)
    assertTrue(toCast.accepted)
    assertEquals(TurnPhase.CAST, toCast.run.combat?.phase)
    val rejectedTap = GameEngine.execute(toCast.run, GameCommand.TapCell(3, 3))
    assertFalse(rejectedTap.accepted)
    assertEquals(CommandRejectionReason.WRONG_PHASE, rejectedTap.rejectionReason)
  }

  @Test
  fun `P0_6 out-of-order GameCommand transitions are rejected without mutating RunState`() {
    val startRun = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "GUARD-TEST")
    val nodeId = GameEngine.availableNodeIds(startRun).first()
    val inCombat = GameEngine.execute(startRun, GameCommand.ChooseNode(nodeId)).run

    // Cannot choose another node, open rewards, finish rewards, or use campfire/shop/event while in combat
    listOf(
      GameCommand.ChooseNode(nodeId),
      GameCommand.OpenRewards,
      GameCommand.FinishRewards,
      GameCommand.CampfireRest,
      GameCommand.ShopBuyFlask,
      GameCommand.ChooseEventOption("kiln_scavenge"),
      GameCommand.LeaveNode,
    ).forEach { illegal ->
      val res = GameEngine.execute(inCombat, illegal)
      assertFalse("Expected $illegal to be rejected during combat", res.accepted)
      assertEquals(inCombat, res.run)
    }

    // Cannot FinishRewards until both relic and card rewards are picked or skipped
    val rewardRun = startRun.copy(
      pendingReward = RewardBundle(
        cindersEarned = 50,
        cardChoices = listOf("neutral_gold_eye"),
        relicRewardId = "relic_cinder_tongs",
      ),
    )
    val prematureFinish = GameEngine.execute(rewardRun, GameCommand.FinishRewards)
    assertFalse(prematureFinish.accepted)
    assertEquals(rewardRun, prematureFinish.run)

    val afterSkipRelic = GameEngine.execute(rewardRun, GameCommand.SkipRewardRelic)
    assertTrue(afterSkipRelic.accepted)
    val afterSkipCard = GameEngine.execute(afterSkipRelic.run, GameCommand.SkipRewardCard)
    assertTrue(afterSkipCard.accepted)
    val finished = GameEngine.execute(afterSkipCard.run, GameCommand.FinishRewards)
    assertTrue(finished.accepted)
    assertNull(finished.run.pendingReward)
  }

  @Test
  fun `reward completion requires support and artifact resolution and support replacement is explicit`() {
    val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "REWARD-ALL-CHANNELS")
    val rewardRun = base.copy(
      growth = base.growth.copy(socketedSupportIds = listOf("sup_echo_strike"), maxSockets = 1),
      pendingReward = RewardBundle(
        cindersEarned = 0,
        cardChoices = emptyList(),
        supportChoices = listOf("sup_prism_forge"),
        artifactChoices = listOf("art_rudraksha_mala"),
      ),
    )

    val premature = GameEngine.execute(rewardRun, GameCommand.FinishRewards)
    assertFalse("FinishRewards must wait for support and artifact channels", premature.accepted)
    assertEquals(rewardRun, premature.run)

    val implicitReplacement = GameEngine.execute(rewardRun, GameCommand.SocketSupport("sup_prism_forge"))
    assertFalse("A full socket set must not silently replace its last slot", implicitReplacement.accepted)
    assertEquals(rewardRun, implicitReplacement.run)

    val invalidSlot = GameEngine.execute(rewardRun, GameCommand.SocketSupport("sup_prism_forge", 1))
    assertFalse("Out-of-range replacement slots must be rejected", invalidSlot.accepted)
    assertEquals(rewardRun, invalidSlot.run)

    val replaced = GameEngine.execute(rewardRun, GameCommand.SocketSupport("sup_prism_forge", 0))
    assertTrue(replaced.accepted)
    assertEquals(listOf("sup_prism_forge"), replaced.run.growth.socketedSupportIds)

    val bound = GameEngine.execute(replaced.run, GameCommand.BindArtifact("art_rudraksha_mala"))
    assertTrue(bound.accepted)
    assertTrue("Artifact must be owned after binding", "art_rudraksha_mala" in bound.run.growth.tantricArtifactIds)

    val finished = GameEngine.execute(bound.run, GameCommand.FinishRewards)
    assertTrue("All four reward channels are resolved; finishing must succeed", finished.accepted)
    assertNull(finished.run.pendingReward)
  }

  @Test
  fun `support reward rejects already socketed and unknown support without consuming the choice`() {
    val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "SUPPORT-REWARD-INTEGRITY")
    val duplicateSupportRun = base.copy(
      growth = base.growth.copy(
        socketedSupportIds = listOf("sup_prism_forge"),
        maxSockets = 1,
      ),
      pendingReward = RewardBundle(
        cindersEarned = 0,
        cardChoices = emptyList(),
        supportChoices = listOf("sup_prism_forge"),
      ),
    )

    val duplicate = GameEngine.execute(
      duplicateSupportRun,
      GameCommand.SocketSupport("sup_prism_forge", 0),
    )
    assertFalse("Already socketed support must not consume a reward choice", duplicate.accepted)
    assertEquals(duplicateSupportRun, duplicate.run)

    val unknownSupportRun = duplicateSupportRun.copy(
      growth = duplicateSupportRun.growth.copy(socketedSupportIds = emptyList()),
      pendingReward = duplicateSupportRun.pendingReward?.copy(
        supportChoices = listOf("missing_support_definition"),
      ),
    )
    val unknown = GameEngine.execute(
      unknownSupportRun,
      GameCommand.SocketSupport("missing_support_definition", -1),
    )
    assertFalse("Unknown support definition must not consume a reward choice", unknown.accepted)
    assertEquals(unknownSupportRun, unknown.run)
  }

  @Test
  fun `event selection rejects unaffordable options and simulator chooses first affordable option`() {
    val base = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "EVENT-AFFORDABILITY")
    val eventRun = base.copy(
      cinders = 5,
      inNonCombatNode = true,
      activeEvent = EventState(
        eventId = "test_affordability",
        title = "Test Event",
        body = "Affordability regression.",
        choices = listOf(
          EventChoiceDef("too_expensive", "Expensive", "Requires 10 Cinders", minCindersRequired = 10),
          EventChoiceDef("affordable", "Affordable", "No cost"),
        ),
      ),
    )

    val rejected = GameEngine.execute(eventRun, GameCommand.ChooseEventOption("too_expensive"))
    assertFalse(rejected.accepted)
    assertEquals(eventRun, rejected.run)
    assertEquals("affordable", SimulationRunner.firstAffordableEventChoice(eventRun)?.id)

    val accepted = GameEngine.execute(eventRun, GameCommand.ChooseEventOption("affordable"))
    assertTrue(accepted.accepted)
    assertNotNull("Accepted event choice must record a resolved outcome", accepted.run.activeEvent?.resolvedSummary)
  }
  @Test
  fun `ADR-009 colored pip cost rejects Spark while generic pip accepts Spark and preferred Hue`() {
    val emberOnlyCard = GameCatalog.cardById("monk_ember_palm")
    val resWithOnlySpark = ResourceState(spark = 3)
    assertNull(GameEngine.tryPayCardCost(resWithOnlySpark, emberOnlyCard, upgraded = false))

    val genericCard = GameCatalog.cardById("monk_bead_strike")
    val paidSpark = GameEngine.tryPayCardCost(resWithOnlySpark, genericCard, upgraded = false)
    assertNotNull(paidSpark)
    assertEquals(2, paidSpark?.spark)

    val mixedRes = ResourceState(inkPools = mapOf(Hue.EMBER to 2, Hue.MOSS to 2), spark = 1)
    val paidMoss = GameEngine.tryPayCardCost(mixedRes, genericCard, upgraded = false, preferredGenericHue = Hue.MOSS)
    assertNotNull(paidMoss)
    assertEquals(1, paidMoss?.inkOf(Hue.MOSS))
    assertEquals(2, paidMoss?.inkOf(Hue.EMBER))
    assertEquals(1, paidMoss?.spark)
  }

  @Test
  fun `P0_1 Ashen Monk burns Vigil for heavy single-hit burst with zero passive enemy-phase counterattack`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "MONK-VIGIL")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    val beadStrike = CardInstance(uid = 5001L, defId = "monk_bead_strike") // 5 + burn<=3 * 4
    val lotus = CardInstance(uid = 5002L, defId = "monk_lotus_combustion") // req 2 Vigil, burn all * 5 + 16

    // 1. Verify NO passive Vigil counterattack on enemy turn and Vigil persists across turns
    val endTurnSetup = run.copy(
      combat = c0.copy(
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        player = c0.player.copy(vigil = 6, guardBlock = 20),
        enemy = c0.enemy.copy(
          hp = 150,
          maxHp = 150,
          block = 0,
          slot1Intent = EnemyIntent.Attack(8),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val afterEnemyTurn = GameEngine.execute(endTurnSetup, GameCommand.EndTurn)
    assertTrue(afterEnemyTurn.accepted)
    assertEquals("Enemy must take 0 counterattack damage from Vigil", 150, afterEnemyTurn.run.combat?.enemy?.hp)
    assertEquals("Vigil must persist across turns in combat", 6, afterEnemyTurn.run.combat?.player?.vigil)

    // 2. Verify Bead Strike burns 3 Vigil for 5 + 12 = 17 dmg
    val castRun = afterEnemyTurn.run.copy(
      combat = requireNotNull(afterEnemyTurn.run.combat).copy(
        turn = TurnState(turnNumber = 2, phase = TurnPhase.CAST, tapsRemaining = 0),
        resources = ResourceState(inkPools = mapOf(Hue.EMBER to 3), spark = 2),
        combatDeck = DeckState(emptyList(), listOf(beadStrike, lotus), emptyList(), emptyList()),
        enemy = requireNotNull(afterEnemyTurn.run.combat).enemy.copy(hp = 150, block = 0),
      )
    )
    val afterBead = GameEngine.execute(castRun, GameCommand.CastCard(beadStrike.uid))
    assertTrue(afterBead.accepted)
    assertEquals(150 - 17, afterBead.run.combat?.enemy?.hp)
    assertEquals(3, afterBead.run.combat?.player?.vigil) // 6 - 3 = 3

    // 3. Verify Lotus Combustion burns all remaining 3 Vigil for 16 + 15 = 31 dmg
    val afterLotus = GameEngine.execute(afterBead.run, GameCommand.CastCard(lotus.uid))
    assertTrue(afterLotus.accepted)
    assertEquals(150 - 17 - 31, afterLotus.run.combat?.enemy?.hp)
    assertEquals(0, afterLotus.run.combat?.player?.vigil)

    // 4. Verify Lotus Combustion rejected when Vigil < 2
    val lowVigilRun = afterLotus.run.copy(
      combat = requireNotNull(afterLotus.run.combat).copy(
        resources = ResourceState(inkPools = mapOf(Hue.EMBER to 3), spark = 2),
        combatDeck = DeckState(emptyList(), listOf(lotus), emptyList(), emptyList()),
        player = requireNotNull(afterLotus.run.combat).player.copy(vigil = 1),
      )
    )
    val rejected = GameEngine.execute(lowVigilRun, GameCommand.CastCard(lotus.uid))
    assertFalse(rejected.accepted)
    assertEquals(CommandRejectionReason.INSUFFICIENT_VIGIL, rejected.rejectionReason)
  }

  @Test
  fun `P0_2 Brand marks board blocks preserves across gravity and costs 2 Vigil per popped block with HP shortfall`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "BRAND-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    val brandedBoard = gridOf(
      branded = setOf(BoardCoord(5, 2), BoardCoord(5, 3), BoardCoord(3, 4)),
    ) { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && c in 2..3 -> TileKind.Playable(Hue.EMBER)
        r == 3 && c == 4 -> TileKind.Playable(Hue.GOLD)
        else -> TileKind.Playable(Hue.MOSS)
      }
    }

    // Preview detects 2 branded tiles in (5,2)-(5,3) -> 4 Vigil cost
    val preview = BoardEngine.groupAt(brandedBoard, 5, 2, null)
    assertEquals(2, preview.brandedCoords.size)
    assertEquals(4, preview.brandVigilCost)

    // Player has only 2 Vigil -> popping 2 branded tiles spends 2 Vigil + 2 HP shortfall
    val seeded = run.copy(
      hp = 50,
      combat = c0.copy(
        board = brandedBoard,
        player = c0.player.copy(vigil = 2),
        enemy = c0.enemy.copy(hp = 120, maxHp = 120, block = 0),
      ),
    )
    val afterTap = GameEngine.execute(seeded, GameCommand.TapCell(5, 2))
    assertTrue(afterTap.accepted)
    assertEquals(0, afterTap.run.combat?.player?.vigil)
    assertEquals(48, afterTap.run.hp)
    // Unpopped branded block at (3,4) remains branded
    assertTrue(afterTap.run.combat?.board?.tileAt(3, 4)?.isBranded == true)
  }

  @Test
  fun `P0_2 Fog lasts 3 taps decrements on tap and is dispelled by Gold Eye`() {
    var run = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "FOG-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val nonTide = listOf(Hue.EMBER, Hue.GOLD, Hue.MOSS, Hue.VIOLET)
    val board = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && c in 1..2 -> TileKind.Playable(Hue.TIDE)
        else -> TileKind.Playable(nonTide[(r * 2 + c) % nonTide.size])
      }
    }

    // Enemy InflictFog sets fogTapsRemaining = 3
    val enemyFogRun = run.copy(
      combat = c0.copy(
        board = board,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        enemy = c0.enemy.copy(
          slot1Intent = EnemyIntent.InflictFog(taps = 3, damage = 0),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val afterEnemy = GameEngine.execute(enemyFogRun, GameCommand.EndTurn)
    assertTrue(afterEnemy.accepted)
    assertEquals(3, afterEnemy.run.combat?.player?.fogTapsRemaining)

    // Tap decrements Fog from 3 -> 2
    val afterTap = GameEngine.execute(afterEnemy.run, GameCommand.TapCell(4, 1))
    assertTrue(afterTap.accepted)
    assertEquals(2, afterTap.run.combat?.player?.fogTapsRemaining)

    // Cast Gold Eye dispels Fog immediately to 0
    val goldEye = CardInstance(uid = 7001L, defId = "neutral_gold_eye")
    val castPrep = afterTap.run.copy(
      combat = requireNotNull(afterTap.run.combat).copy(
        turn = TurnState(turnNumber = 2, phase = TurnPhase.CAST, tapsRemaining = 0),
        resources = ResourceState(spark = 1),
        combatDeck = DeckState(emptyList(), listOf(goldEye), emptyList(), emptyList()),
      ),
    )
    val afterGoldEye = GameEngine.execute(castPrep, GameCommand.CastCard(goldEye.uid))
    assertTrue(afterGoldEye.accepted)
    assertEquals(0, afterGoldEye.run.combat?.player?.fogTapsRemaining)
  }

  @Test
  fun `P0_2 Seal locks column for 3 turns blocking taps gravity wall rise and paint until Quarry Purge or decay`() {
    var run = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "SEAL-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val sealedCol = 2
    val board = gridOf(sealedColumns = mapOf(sealedCol to 3)) { r, c ->
      if (r < 2) TileKind.Empty
      else if (c == sealedCol) TileKind.Playable(Hue.TIDE)
      else TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
    }

    val seeded = run.copy(
      combat = c0.copy(
        board = board,
        enemy = c0.enemy.copy(
          hp = 150,
          maxHp = 150,
          slot1Intent = EnemyIntent.Defend(4),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )

    // 1. Direct tap on sealed column rejected
    val tapSealed = GameEngine.execute(seeded, GameCommand.TapCell(4, sealedCol))
    assertFalse(tapSealed.accepted)
    assertEquals(CommandRejectionReason.COLUMN_SEALED, tapSealed.rejectionReason)

    // 2. Chisel Wand on sealed column rejected
    val chiselSealed = GameEngine.execute(seeded, GameCommand.UseChiselWand(4, sealedCol, Hue.EMBER))
    assertFalse(chiselSealed.accepted)
    assertEquals(CommandRejectionReason.COLUMN_SEALED, chiselSealed.rejectionReason)

    // 3. Wall rise keeps sealed column frozen while unsealed column shifts up, and seal decays 3 -> 2
    val sealedSnapshot = (0 until 7).map { r -> board.grid[r][sealedCol].id }
    val unsealedIncId = board.incomingRow[0].id
    val toCast = GameEngine.execute(seeded, GameCommand.ProceedToCast).run
    val afterEnd = GameEngine.execute(toCast, GameCommand.EndTurn).run
    val nextBoard = requireNotNull(afterEnd.combat).board
    assertEquals(sealedSnapshot, (0 until 7).map { r -> nextBoard.grid[r][sealedCol].id })
    assertEquals(unsealedIncId, nextBoard.grid[6][0].id)
    assertEquals(2, nextBoard.sealedColumns[sealedCol])

    // 4. Casting Quarry Purge immediately clears all sealed columns
    val purge = CardInstance(uid = 8001L, defId = "witch_quarry_purge")
    val purgeReady = afterEnd.copy(
      combat = requireNotNull(afterEnd.combat).copy(
        turn = TurnState(turnNumber = 2, phase = TurnPhase.CAST, tapsRemaining = 0),
        resources = ResourceState(inkPools = mapOf(Hue.VIOLET to 2)),
        combatDeck = DeckState(emptyList(), listOf(purge), emptyList(), emptyList()),
      ),
    )
    val afterPurge = GameEngine.execute(purgeReady, GameCommand.CastCard(purge.uid))
    assertTrue(afterPurge.accepted)
    assertTrue(requireNotNull(afterPurge.run.combat).board.sealedColumns.isEmpty())
  }

  @Test
  fun `P0_3 ADR-008 pre-rise wall overflow burns top 2 rows deducts 8 HP and 1 Flask unmitigated by Guard and skips rise`() {
    var run = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "WALL-OVERFLOW")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // Full board (row 0 has NO Empty socket) with a valid pair in row 3 so post-burn no-move swap isn't needed
    val fullBoard = gridOf { r, c ->
      if (r == 3 && c in 0..1) TileKind.Playable(Hue.EMBER)
      else TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
    }
    val bottomRowBefore = fullBoard.grid[6].map { it.id }

    val seeded = run.copy(
      hp = 60,
      flasks = 3,
      combat = c0.copy(
        board = fullBoard,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        player = c0.player.copy(guardBlock = 40), // Guard MUST NOT mitigate overflow HP loss
        enemy = c0.enemy.copy(
          slot1Intent = EnemyIntent.Defend(4),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val afterTurn = GameEngine.execute(seeded, GameCommand.EndTurn)
    assertTrue(afterTurn.accepted)
    assertEquals("Overflow must deduct 8 HP unmitigated by Guard", 52, afterTurn.run.hp)
    assertEquals("Overflow must deduct 1 Flask", 2, afterTurn.run.flasks)

    val nextBoard = requireNotNull(afterTurn.run.combat).board
    assertTrue("Row 0 must be burned to Empty", nextBoard.grid[0].all { it.kind is TileKind.Empty })
    assertTrue("Row 1 must be burned to Empty", nextBoard.grid[1].all { it.kind is TileKind.Empty })
    assertEquals("Wall rise must be skipped on overflow (bottom row unchanged)", bottomRowBefore, nextBoard.grid[6].map { it.id })
  }

  @Test
  fun `P0_4 ADR-015 Echo repeats only primary damage once without repeating secondary Vigil or Echo-of-Echo`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "ECHO-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    val sutra = CardInstance(uid = 9001L, defId = "monk_cinder_sutra") // Primary: 12 dmg; Secondary: +2 Vigil; Echo=true
    val seeded = run.copy(
      combat = c0.copy(
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        resources = ResourceState(inkPools = mapOf(Hue.EMBER to 2, Hue.MOSS to 2)),
        combatDeck = DeckState(emptyList(), listOf(sutra), emptyList(), emptyList()),
        player = c0.player.copy(vigil = 0),
        enemy = c0.enemy.copy(hp = 100, maxHp = 100, block = 0),
      ),
    )

    val res = GameEngine.execute(seeded, GameCommand.CastCard(sutra.uid))
    assertTrue(res.accepted)
    val c1 = requireNotNull(res.run.combat)
    assertEquals("Primary 12 dmg executes twice = 24 dmg total", 76, c1.enemy.hp)
    assertEquals("Secondary +2 Vigil must NOT repeat on Echo", 2, c1.player.vigil)
    assertEquals("Exactly 1 Echo event emitted (no Echo-of-Echo)", 1, res.events.count { it.isEcho })
  }

  @Test
  fun `P0_5 direct 2-plus Ash tap yields 0 Ink n-1 chip and Hollow Crown Spark`() {
    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "DIRECT-ASH")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val board = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c in 2..4 -> TileKind.Ash // 3 connected Ash
        else -> TileKind.Playable(Hue.GOLD)
      }
    }
    val seeded = run.copy(
      combat = c0.copy(
        board = board,
        enemy = c0.enemy.copy(hp = 100, maxHp = 100, block = 0),
      ),
    )
    val res = GameEngine.execute(seeded, GameCommand.TapCell(4, 2))
    assertTrue(res.accepted)
    val c1 = requireNotNull(res.run.combat)
    assertEquals("Direct Ash pop yields 0 Ink", 0, c1.resources.totalInk())
    assertEquals("3 Ash deals n-1 = 2 chip damage", 98, c1.enemy.hp)
    assertEquals("Hollow Crown awards +3 Spark for 3 Ash", 3, c1.resources.spark)
  }

  @Test
  fun `P0_5 ADR-017 Unlit Covenant enforces class lock 3 Ash-only empty-air turns for Dread ending and resets on card or Flask`() {
    // Non-Unlit class cannot activate Unlit Covenant
    val monkAttempt = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "MONK-COV", unlitCovenantActive = true)
    assertFalse(monkAttempt.unlitCovenantActive)

    // Unlit vs Act III Hearthkeeper: 3 consecutive Ash-only no-card no-Flask turns trigger Dread true ending
    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "DREAD-3TURNS", unlitCovenantActive = true, dreadLevel = 1)
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    fun ashBoard(): BoardState = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c in 2..3 -> TileKind.Ash
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }

    var state = run.copy(
      currentAct = 3,
      hp = 60,
      flasks = 3,
      combat = c0.copy(
        board = ashBoard(),
        enemy = c0.enemy.copy(
          defId = "act3_boss_malakor",
          name = "The Hearthkeeper",
          act = 3,
          hp = 200,
          maxHp = 200,
          block = 0,
          isBoss = true,
          slot1Intent = EnemyIntent.Attack(25),
          slot2Intent = EnemyIntent.SiphonFlask(15, 1),
        ),
      ),
    )

    // Turn 1: Ash-only tap, no card -> Hearthkeeper attacks pass through empty air (0 HP / 0 Flask lost), streak = 1
    state = GameEngine.execute(state, GameCommand.TapCell(4, 2)).run
    val t1 = GameEngine.execute(state, GameCommand.EndTurn)
    assertTrue(t1.accepted)
    assertEquals(60, t1.run.hp)
    assertEquals(3, t1.run.flasks)
    assertEquals(1, t1.run.dreadConsecutiveAshTurns)
    assertTrue(t1.events.any { it.message.contains("empty air") })

    // Verify Flask use resets Dread streak to 0
    val withFlask = GameEngine.execute(t1.run, GameCommand.UseFlask)
    assertTrue(withFlask.accepted)
    assertEquals(0, withFlask.run.dreadConsecutiveAshTurns)

    // Complete 3 clean Ash-only turns from streak 0 -> Dread true ending triggers
    var cleanRun = t1.run.copy(dreadConsecutiveAshTurns = 0, hp = 60, flasks = 3)
    repeat(3) { turnIdx ->
      val curCombat = requireNotNull(cleanRun.combat)
      cleanRun = cleanRun.copy(
        combat = curCombat.copy(
          board = ashBoard(),
          enemy = curCombat.enemy.copy(slot1Intent = EnemyIntent.Attack(20), slot2Intent = EnemyIntent.Attack(15)),
        )
      )
      cleanRun = GameEngine.execute(cleanRun, GameCommand.TapCell(4, 2)).run
      val ended = GameEngine.execute(cleanRun, GameCommand.EndTurn)
      assertTrue(ended.accepted)
      cleanRun = ended.run
      if (turnIdx < 2) {
        assertFalse(cleanRun.runWon)
        assertEquals(turnIdx + 1, cleanRun.dreadConsecutiveAshTurns)
      }
    }
    assertTrue(cleanRun.runWon)
    assertTrue(cleanRun.dreadEndingAchieved)
    assertEquals(3, cleanRun.dreadConsecutiveAshTurns)

    // Standard HP kill of Act III Boss must NOT grant Dread true ending
    val hpKillBase = GameEngine.startNewRun(HeroClass.THE_UNLIT, "HP-KILL-BOSS", unlitCovenantActive = true)
    val entered = GameEngine.enterNode(hpKillBase, GameEngine.availableNodeIds(hpKillBase).first())
    val strike = CardInstance(uid = 9991L, defId = "unlit_gold_strike")
    val bossNearDead = entered.copy(
      currentAct = 3,
      combat = requireNotNull(entered.combat).copy(
        turn = TurnState(turnNumber = 1, phase = TurnPhase.CAST, tapsRemaining = 0),
        resources = ResourceState(inkPools = mapOf(Hue.GOLD to 1)),
        combatDeck = DeckState(emptyList(), listOf(strike), emptyList(), emptyList()),
        enemy = requireNotNull(entered.combat).enemy.copy(hp = 4, maxHp = 200, block = 0, isBoss = true),
      ),
    )
    val hpWin = GameEngine.execute(bossNearDead, GameCommand.CastCard(strike.uid))
    assertTrue(hpWin.accepted)
    assertTrue(hpWin.run.runWon)
    assertFalse("Standard HP kill must NOT award Dread ending", hpWin.run.dreadEndingAchieved)
  }

  @Test
  fun `P1 ADR-013 enemy HP scales exponentially by node Elite and Boss multipliers`() {
    val act1Normal = CampaignGenerator.scaledEnemyHp(1, act = 1, isElite = false, isBoss = false, dreadLevel = 0, isShade = false, shadeDeckSize = 0)
    val act1Elite = CampaignGenerator.scaledEnemyHp(10, act = 1, isElite = true, isBoss = false, dreadLevel = 0, isShade = false, shadeDeckSize = 0)
    val act3Boss = CampaignGenerator.scaledEnemyHp(15, act = 3, isElite = false, isBoss = true, dreadLevel = 0, isShade = false, shadeDeckSize = 0)
    assertEquals(40, act1Normal) // round(36 * 1.11^1) = 40
    assertTrue(act1Elite > act1Normal * 2)
    assertTrue(act3Boss > act1Elite * 3)
  }

  @Test
  fun `ADR-018 15-row map validates all routes and places Shade on Act I Row 5 with full-deck intents`() {
    val priorDeck = listOf(
      "unlit_gold_strike", "unlit_ash_ward", "unlit_pop_shot",
      "unlit_sift", "unlit_hollow_crown", "neutral_gold_eye",
    )
    val run = GameEngine.startNewRun(
      heroClass = HeroClass.THE_UNLIT,
      seedText = "CAMPAIGN-15",
      previousRunDeckIds = priorDeck,
      unlitCovenantActive = true,
    )
    assertTrue(CampaignGenerator.validateMap(run.actMap))
    assertEquals((1..15).toSet(), run.actMap.nodes.map { it.row }.toSet())
    assertTrue(run.actMap.nodes.any { it.row == 5 && it.type == MapNodeType.SHADE })
    assertTrue(run.actMap.nodes.any { it.type == MapNodeType.EVENT })
    assertEquals(listOf(HeroClass.THE_UNLIT.signatureRelicId), run.relics)

    // Full previous deck drives Shade intents (no 4-card truncation)
    val (s1, s2) = CampaignGenerator.buildShadeIntentsFromDeck(run.shadeDeckSnapshot, act = 1)
    assertEquals(priorDeck.size, s1.size)
    assertEquals(priorDeck.size, s2.size)
  }

  @Test
  fun `P1 15-row campaign map passes O(V+E) DP path validation across 20 seeds per Act`() {
    for (act in 1..3) {
      repeat(20) { i ->
        val rng = SeededRng(SeededRng.fromSeedString("MAP-ACT$act-$i"))
        val (map, _) = CampaignGenerator.generateActMap(act, rng)
        assertTrue("Map validation failed for Act $act seed $i", CampaignGenerator.validateMap(map))
      }
    }
  }

  @Test
  fun `P1 no-move rescue uses multiset swap or PaintDeck redraw without un-accounted hue injection`() {
    // Checkerboard with no adjacent duplicates and no Prisms
    val deadBoard = gridOf { r, c ->
      if (r < 2) TileKind.Empty
      else TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
    }
    assertFalse(BoardEngine.hasAnyValidMove(deadBoard))

    val initialCounts = deadBoard.grid.flatten()
      .mapNotNull { (it.kind as? TileKind.Playable)?.hue }
      .groupingBy { it }
      .eachCount()

    val deck = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).mapIndexed { idx, id ->
      CardInstance(idx.toLong() + 1L, id)
    }
    val (paintDeck, rng1) = BoardPaintDeck.createInitial(deck, SeededRng(4242L))
    val rescued = BoardEngine.ensurePlayableMove(deadBoard, paintDeck, rng1)

    assertTrue(rescued.didReshuffle)
    assertTrue(BoardEngine.hasAnyValidMove(rescued.board))
    // Multiset swap preserves exact hue multiset without injecting free Prisms
    val rescuedPrisms = rescued.board.grid.flatten().count { it.kind is TileKind.Prism }
    assertEquals(0, rescuedPrisms)
    val rescuedCounts = rescued.board.grid.flatten()
      .mapNotNull { (it.kind as? TileKind.Playable)?.hue }
      .groupingBy { it }
      .eachCount()
    assertEquals(initialCounts, rescuedCounts)
  }

  @Test
  fun `seed plus command log replay reproduces exact identical RunState`() {
    val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "REPLAY-SEED")
    val nodeId = GameEngine.availableNodeIds(base).first()
    val commands = mutableListOf<GameCommand>(GameCommand.ChooseNode(nodeId))
    var cur = GameEngine.execute(base, commands.last()).run

    repeat(3) {
      val b = requireNotNull(cur.combat).board
      val coord = (0 until 7).flatMap { r ->
        (0 until 7).mapNotNull { c ->
          if (BoardEngine.groupAt(b, r, c, cur.combat?.spectrum?.currentHue).isValid) BoardCoord(r, c) else null
        }
      }.first()
      val tapCmd = GameCommand.TapCell(coord.row, coord.col)
      commands.add(tapCmd)
      cur = GameEngine.execute(cur, tapCmd).run
    }
    if (cur.combat?.phase == TurnPhase.WEAVE) {
      commands.add(GameCommand.ProceedToCast)
      cur = GameEngine.execute(cur, GameCommand.ProceedToCast).run
    }
    commands.add(GameCommand.EndTurn)
    cur = GameEngine.execute(cur, GameCommand.EndTurn).run

    val replayed = GameEngine.replay(
      heroClass = HeroClass.ASHEN_MONK,
      seedText = "REPLAY-SEED",
      previousRunDeckIds = emptyList(),
      commands = commands,
    )
    assertEquals(cur, replayed)
  }

  @Test
  fun `opening board avoids 4-plus in a row and 5-plus clusters across Monk and Witch seeds while guaranteeing valid moves`() {
    listOf(HeroClass.ASHEN_MONK, HeroClass.QUARRY_WITCH).forEach { hero ->
      repeat(10) { idx ->
        val run = GameEngine.startNewRun(hero, "${hero.name}-OPEN-$idx")
        val inCombat = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
        val board = requireNotNull(inCombat.combat).board
        assertTrue(BoardEngine.hasAnyValidMove(board))
        assertTrue("Opening rows 0..1 must be Empty headroom", board.grid[0].all { it.kind is TileKind.Empty })
        assertTrue("Opening rows 0..1 must be Empty headroom", board.grid[1].all { it.kind is TileKind.Empty })

        for (r in 2 until board.rows) {
          var runLen = 1
          for (c in 1 until board.cols) {
            val prev = (board.grid[r][c - 1].kind as? TileKind.Playable)?.hue
            val cur = (board.grid[r][c].kind as? TileKind.Playable)?.hue
            runLen = if (prev != null && prev == cur) runLen + 1 else 1
            assertTrue("Row $r had $runLen in a row for $hero seed $idx", runLen < 4)
          }
        }
        for (c in 0 until board.cols) {
          var runLen = 1
          for (r in 3 until board.rows) {
            val prev = (board.grid[r - 1][c].kind as? TileKind.Playable)?.hue
            val cur = (board.grid[r][c].kind as? TileKind.Playable)?.hue
            runLen = if (prev != null && prev == cur) runLen + 1 else 1
            assertTrue("Col $c had $runLen in a row for $hero seed $idx", runLen < 4)
          }
        }
      }
    }
  }

  @Test
  fun `findValidGroups lists unique legal clusters whose anchors match groupAt and excludes sealed columns`() {
    val board = gridOf(sealedColumns = mapOf(6 to 2)) { r, c ->
      when {
        r < 2 -> TileKind.Empty
        c == 6 -> TileKind.Playable(Hue.EMBER) // Sealed column: must never appear
        r == 2 && c in 0..2 -> TileKind.Playable(Hue.GOLD) // 3 Gold
        r == 4 && c in 1..2 -> TileKind.Ash // 2 Ash
        r == 5 && c == 4 -> TileKind.Prism // Isolated Prism
        r == 6 && c in 0..1 -> TileKind.Stone // Stone: never tappable
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }
    val spectrum = SpectrumState(currentHue = Hue.EMBER, runLength = 0, halfSteps = 2)
    val groups = BoardEngine.findValidGroups(board, spectrum)
    assertTrue(groups.isNotEmpty())
    // Unique coordinate sets
    assertEquals(groups.size, groups.map { it.resolution.coords }.toSet().size)
    // Every anchor round-trips to the exact same GroupResolution and excludes sealed column 6
    groups.forEach { opt ->
      assertFalse("Sealed column must be excluded", opt.anchor.col == 6)
      assertTrue(opt.resolution.coords.none { it.col == 6 })
      assertEquals(opt.resolution, BoardEngine.groupAt(board, opt.anchor.row, opt.anchor.col, spectrum.currentHue))
    }
    // Gold follows Ember on wheel -> Gold cluster should be marked advancesWheel and prioritized first
    assertTrue(groups.first().advancesWheel)
    assertEquals(Hue.GOLD, groups.first().resolution.resolvedHue)
    // Direct 2+ Ash cluster is included
    assertTrue(groups.any { it.resolution.isDirectAshGroup && it.resolution.coords.size == 2 })
  }

  @Test
  fun `batch 3-act campaign simulation completes cleanly across all three canonical classes`() {
    val report = SimulationRunner.runBatchSimulation(seedsPerClass = 2)
    assertEquals(6, report.runsCompleted)
    assertTrue(report.avgTurnsPerRun > 0.0)
    assertTrue(report.avgActReached >= 1.0)
  }

  @Test
  fun `v1_0 catalog meets full depth targets with unique IDs and locked starter integrity`() {
    assertEquals(65, GameCatalog.cards.size)
    assertEquals(65, GameCatalog.cards.map { it.id }.toSet().size)
    assertEquals(18, GameCatalog.cards.count { it.heroClass == HeroClass.ASHEN_MONK })
    assertEquals(18, GameCatalog.cards.count { it.heroClass == HeroClass.QUARRY_WITCH })
    assertEquals(17, GameCatalog.cards.count { it.heroClass == HeroClass.THE_UNLIT })
    assertEquals(12, GameCatalog.cards.count { it.heroClass == null })

    assertEquals(22, GameCatalog.relics.size)
    assertEquals(22, GameCatalog.relics.map { it.id }.toSet().size)
    assertEquals(3, GameCatalog.relics.count { it.isSignature })
    assertEquals(19, GameCatalog.rewardRelics().size)
    assertTrue(GameCatalog.relics.any { it.id == "relic_gravediggers_spade" })

    assertEquals(6, GameCatalog.remnants.size)
    assertEquals(6, GameCatalog.remnants.map { it.id }.toSet().size)

    assertEquals(20, GameCatalog.enemies.size)
    assertEquals(20, GameCatalog.enemies.map { it.id }.toSet().size)
    assertTrue(GameCatalog.enemies.any { it.id == "special_shade" && it.isShade })

    assertEquals(12, GameCatalog.events.size)
    assertEquals(12, GameCatalog.events.map { it.id }.toSet().size)

    HeroClass.entries.forEach { hero ->
      val starterIds = GameCatalog.starterDeckIds(hero)
      starterIds.forEach { id -> assertNotNull(GameCatalog.cardById(id)) }
    }
    assertEquals(12, GameCatalog.starterDeckIds(HeroClass.THE_UNLIT).size)
  }

  @Test
  fun `ADR-016 Gravediggers Spade deals 1_5x damage to Elites Bosses and Shade while staying 1_0x on Skirmish`() {
    val relics = listOf("relic_prayer_beads", "relic_gravediggers_spade")
    val baseEnemy = EnemyState(
      defId = "act1_ash_hound",
      name = "Cinder Hound",
      act = 1,
      hp = 100,
      maxHp = 100,
      block = 0,
      isElite = false,
      isBoss = false,
      isShade = false,
      patternStep = 0,
      slot1Intent = EnemyIntent.Attack(6),
      slot2Intent = EnemyIntent.Defend(4),
    )

    val (skirmishAfter, skirmishDmg) = GameEngine.applyPlayerDamage(baseEnemy, 10, relics)
    assertEquals(10, skirmishDmg)
    assertEquals(90, skirmishAfter.hp)

    val (eliteAfter, eliteDmg) = GameEngine.applyPlayerDamage(baseEnemy.copy(isElite = true), 10, relics)
    assertEquals(15, eliteDmg)
    assertEquals(85, eliteAfter.hp)

    val (bossAfter, bossDmg) = GameEngine.applyPlayerDamage(baseEnemy.copy(isBoss = true), 12, relics)
    assertEquals(18, bossDmg)
    assertEquals(82, bossAfter.hp)

    val (shadeAfter, shadeDmg) = GameEngine.applyPlayerDamage(baseEnemy.copy(isShade = true), 14, relics)
    assertEquals(21, shadeDmg)
    assertEquals(79, shadeAfter.hp)
  }

  @Test
  fun `v1_0 relics and remnants execute live combat hooks across startCombat tap and clean Wall rise`() {
    val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "V1-RELICS").copy(
      relics = listOf(
        "relic_prayer_beads",
        "relic_kiln_fired_bell",
        "relic_vial_quicksilver",
        "relic_covenant_ring",
        "relic_wickless_lantern",
        "relic_stained_loupe",
        "relic_obsidian_trowel",
        "relic_ash_glass_prism",
        "relic_spectrum_astrolabe",
        "relic_brazier_grate",
      ),
      remnants = listOf(
        "remnant_violet_veil",
        "remnant_tide_memory",
        "remnant_hollow_ward",
      ),
    )
    val entered = GameEngine.enterNode(base, GameEngine.availableNodeIds(base).first())
    val c0 = requireNotNull(entered.combat)

    // Start-of-combat relic & remnant hooks
    assertEquals(4, c0.player.guardBlock) // Kiln-Fired Bell (+4)
    assertEquals(6, c0.player.vigil) // Kiln-Fired Bell (+3) + Remnant of Choir (+3)
    assertEquals(1, c0.resources.inkOf(Hue.GOLD)) // Covenant Ring
    assertEquals(1, c0.resources.inkOf(Hue.EMBER)) // Covenant Ring
    assertEquals(1, c0.resources.inkOf(Hue.TIDE)) // Remnant of Cistern
    assertEquals(6, c0.combatDeck.hand.size) // Base 5 + Vial of Quicksilver (+1)
    assertTrue(c0.board.grid.flatten().count { it.kind is TileKind.Prism } >= 2) // Base 1 + Remnant of Lens

    // Construct deterministic board with:
    // - 3 Gold + 1 Prism (branded) -> 4-group that shatters 1 adjacent Stone & clears 3 adjacent Ash
    // - Spectrum already at Ember (1.5x) -> popping Gold advances to 2.0x (triggers Spectrum Astrolabe!)
    val nonGold = listOf(Hue.EMBER, Hue.MOSS, Hue.TIDE, Hue.VIOLET)
    val testBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 3 && c in 1..3 -> TileKind.Playable(Hue.GOLD)
        r == 3 && c == 4 -> TileKind.Prism
        r == 2 && c == 1 -> TileKind.Stone
        r == 4 && c in 1..3 -> TileKind.Ash
        else -> TileKind.Playable(nonGold[(r * 2 + c) % nonGold.size])
      }
    }.let { b ->
      b.copy(
        grid = b.grid.mapIndexed { r, row ->
          row.mapIndexed { c, tile ->
            if (r == 3 && c == 1) tile.copy(isBranded = true) else tile
          }
        }
      )
    }

    val prepared = entered.copy(
      combat = c0.copy(
        board = testBoard,
        spectrum = SpectrumState(currentHue = Hue.EMBER, runLength = 1, halfSteps = 3),
        enemy = c0.enemy.copy(hp = 120, maxHp = 120, block = 0),
      )
    )

    val tapRes = GameEngine.execute(prepared, GameCommand.TapCell(3, 1))
    assertTrue(tapRes.accepted)
    val c1 = requireNotNull(tapRes.run.combat)

    // Wickless Lantern negated the Brand Vigil drain (stays at 6 Vigil, flag flipped)
    assertEquals(6, c1.player.vigil)
    assertTrue(c1.player.firstBrandNegatedThisTurn)
    // Stained Loupe granted +2 bonus Gold Ink on first Prism pop (flag flipped)
    assertTrue(c1.player.firstPrismInkUsedThisCombat)
    assertTrue(c1.resources.inkOf(Hue.GOLD) >= 6) // 1 start + 3 group/prism + 2 Prayer Beads + 2 Stained Loupe
    // Ash-Glass Prism (+5 Guard for 3 Ash) + Stone shatter Guard (+2) + starting 4 Guard = 11
    assertEquals(11, c1.player.guardBlock)
    // Spectrum Astrolabe triggered at 2.0x -> +1 Spark and drew +1 card (6 -> 7)
    assertTrue(c1.player.spectrumAstrolabeTriggeredThisTurn)
    assertEquals(1, c1.resources.spark)
    assertEquals(7, c1.combatDeck.hand.size)
    // Obsidian Trowel fired on Stone shatter
    assertTrue(tapRes.events.any { it.message.contains("Obsidian Trowel") })

    // End turn cleanly -> Brazier Grate deals 5 damage on clean Wall rise and per-turn flags reset
    val hpBeforeWall = c1.enemy.hp
    val endRes = GameEngine.execute(tapRes.run, GameCommand.EndTurn)
    assertTrue(endRes.accepted)
    val c2 = requireNotNull(endRes.run.combat)
    assertTrue(c2.enemy.hp <= hpBeforeWall - 5)
    assertTrue(endRes.events.any { it.message.contains("Brazier Grate: Clean Wall rise dealt 5 dmg.") })
    assertFalse(c2.player.firstBrandNegatedThisTurn)
    assertFalse(c2.player.spectrumAstrolabeTriggeredThisTurn)
    assertTrue("Once-per-combat Stained Loupe flag must persist across turns", c2.player.firstPrismInkUsedThisCombat)
  }

  @Test
  fun `Dread 2 and 3 seed opening hazards Shade Vigor scales intents and event removeRandomCard purges deck`() {
    // Dread 3 seeds Ash & Stone while preserving legal move
    val dread3 = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "DREAD-3-BOARD", dreadLevel = 3)
    val inDreadCombat = GameEngine.enterNode(dread3, GameEngine.availableNodeIds(dread3).first())
    val dBoard = requireNotNull(inDreadCombat.combat).board
    assertTrue(BoardEngine.hasAnyValidMove(dBoard))
    assertTrue(dBoard.grid.flatten().count { it.kind is TileKind.Ash } >= 2)
    assertTrue(dBoard.grid.flatten().count { it.kind is TileKind.Stone } >= 1)

    // Shade Vigor scales attack & defend intent values deterministically
    val rareHeavyDeck = listOf(
      "monk_lotus_combustion", "monk_incense_vow", "witch_abyssal_refract",
      "witch_amethyst_lode", "unlit_sovereign_ember", "neutral_prismatic_edict",
    )
    val vigorBonus = CampaignGenerator.computeShadeVigorBonus(rareHeavyDeck, dreadLevel = 2)
    assertEquals(6, vigorBonus)
    val (baseS1, _) = CampaignGenerator.buildShadeIntentsFromDeck(rareHeavyDeck, act = 1, shadeVigorBonus = 0)
    val (scaledS1, _) = CampaignGenerator.buildShadeIntentsFromDeck(rareHeavyDeck, act = 1, shadeVigorBonus = vigorBonus)
    val baseDmg = (baseS1.first() as? EnemyIntent.SiphonFlask)?.damage ?: (baseS1.first() as EnemyIntent.Attack).damage
    val scaledDmg = (scaledS1.first() as? EnemyIntent.SiphonFlask)?.damage ?: (scaledS1.first() as EnemyIntent.Attack).damage
    assertEquals(baseDmg + vigorBonus, scaledDmg)

    // Event with removeRandomCard removes 1 card from masterDeck
    val evtDef = GameCatalog.eventById("event_whispering_belfry")
    val eventRun = dread3.copy(
      cinders = 100,
      inNonCombatNode = true,
      activeEvent = EventState(evtDef.id, evtDef.title, evtDef.body, evtDef.choices),
    )
    val beforeDeckSize = eventRun.masterDeck.size
    val chosen = GameEngine.execute(eventRun, GameCommand.ChooseEventOption("belfry_purge"))
    assertTrue(chosen.accepted)
    assertEquals(beforeDeckSize - 1, chosen.run.masterDeck.size)
    assertEquals(eventRun.cinders - 35, chosen.run.cinders)
  }

  @Test
  fun `EFB-001 player defeat via Brand HP drain or zero-Flask collapse takes precedence over simultaneous enemy kill`() {
    // 1. Brand drains final HP on the exact same Tap that kills the enemy -> player DEFEAT, never Victory
    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "EFB001-BRAND")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val lethalBrandBoard = gridOf(
      branded = setOf(BoardCoord(4, 2), BoardCoord(4, 3)),
    ) { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c in 2..3 -> TileKind.Playable(Hue.GOLD)
        else -> TileKind.Playable(Hue.TIDE)
      }
    }
    val prepared = run.copy(
      hp = 2,
      combat = c0.copy(
        board = lethalBrandBoard,
        player = c0.player.copy(vigil = 0),
        enemy = c0.enemy.copy(hp = 2, maxHp = 50, block = 0),
      ),
    )
    val tapRes = GameEngine.execute(prepared, GameCommand.TapCell(4, 2))
    assertTrue(tapRes.accepted)
    assertEquals(0, tapRes.run.hp)
    assertEquals(GameOverReason.HP_DEPLETED, tapRes.run.gameOverReason)
    assertFalse(tapRes.run.runWon)
    assertEquals(TurnPhase.GAME_OVER, tapRes.run.combat?.phase)

    // 2. Last Flask + Alchemist's Coil kills enemy -> ZERO_FLASKS defeat takes precedence over enemy death
    val coilRun = run.copy(
      hp = 30,
      flasks = 1,
      relics = run.relics + "relic_alchemist_coil",
      combat = c0.copy(
        enemy = c0.enemy.copy(hp = 4, maxHp = 50, block = 0),
      ),
    )
    val flaskRes = GameEngine.execute(coilRun, GameCommand.UseFlask)
    assertTrue(flaskRes.accepted)
    assertEquals(0, flaskRes.run.flasks)
    assertEquals(GameOverReason.ZERO_FLASKS, flaskRes.run.gameOverReason)
    assertFalse(flaskRes.run.runWon)
    assertEquals(TurnPhase.GAME_OVER, flaskRes.run.combat?.phase)
  }

  @Test
  fun `EFB-002 enemy SealColumn 3 preserves 3 full player turns and decays across subsequent turns`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "EFB002-SEAL")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val board = gridOf { r, c ->
      if (r < 2) TileKind.Empty else TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
    }
    val turn1Setup = run.copy(
      combat = c0.copy(
        board = board,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0, skipWallRiseThisTurn = true),
        enemy = c0.enemy.copy(
          hp = 200,
          maxHp = 200,
          slot1Intent = EnemyIntent.SealColumn(turns = 3),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val afterTurn1 = GameEngine.execute(turn1Setup, GameCommand.EndTurn)
    assertTrue(afterTurn1.accepted)
    val sealedAfterApply = requireNotNull(afterTurn1.run.combat).board.sealedColumns
    assertEquals(1, sealedAfterApply.size)
    val sealedCol = sealedAfterApply.keys.first()
    assertEquals("Newly applied 3-turn Seal must leave 3 full turns for the player", 3, sealedAfterApply[sealedCol])

    // Subsequent non-sealing turns decay 3 -> 2 -> 1 -> cleared
    var cur = afterTurn1.run
    listOf(2, 1, null).forEach { expected ->
      val cc = requireNotNull(cur.combat)
      val prep = cur.copy(
        combat = cc.copy(
          turn = cc.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0, skipWallRiseThisTurn = true),
          enemy = cc.enemy.copy(
            slot1Intent = EnemyIntent.Defend(4),
            slot2Intent = EnemyIntent.Defend(4),
          ),
        ),
      )
      cur = GameEngine.execute(prep, GameCommand.EndTurn).run
      assertEquals(expected, requireNotNull(cur.combat).board.sealedColumns[sealedCol])
    }
  }

  @Test
  fun `EFB-003 ensurePlayableMove guarantees a legal move even on hostile sealed sparse boards`() {
    val deck = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).mapIndexed { idx, id ->
      CardInstance(idx.toLong() + 1L, id)
    }
    val (paintDeck, rng0) = BoardPaintDeck.createInitial(deck, SeededRng(9090L))

    // Case 1: Columns 0..5 sealed; column 6 has only 2 non-matching separated tiles
    val sparseSealed = gridOf(
      sealedColumns = mapOf(0 to 3, 1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3),
    ) { r, c ->
      when {
        c == 6 && r == 4 -> TileKind.Playable(Hue.EMBER)
        c == 6 && r == 6 -> TileKind.Playable(Hue.TIDE)
        c < 6 && r >= 2 -> TileKind.Playable(Hue.MOSS)
        else -> TileKind.Empty
      }
    }
    assertFalse(BoardEngine.hasAnyValidMove(sparseSealed))
    val rescued1 = BoardEngine.ensurePlayableMove(sparseSealed, paintDeck, rng0)
    assertTrue(rescued1.didReshuffle)
    assertTrue("Rescue must guarantee a legal move on sparse sealed board", BoardEngine.hasAnyValidMove(rescued1.board))

    // Case 2: All 7 columns sealed with a single occupied tile in column 3 (shortest seal = 1)
    val allSealed = gridOf(
      sealedColumns = mapOf(0 to 3, 1 to 3, 2 to 2, 3 to 1, 4 to 3, 5 to 2, 6 to 3),
    ) { r, c ->
      if (r == 6 && c == 3) TileKind.Playable(Hue.GOLD) else TileKind.Empty
    }
    assertFalse(BoardEngine.hasAnyValidMove(allSealed))
    val rescued2 = BoardEngine.ensurePlayableMove(allSealed, paintDeck, rescued1.rng)
    assertTrue(rescued2.didReshuffle)
    assertTrue("Rescue must guarantee a legal move even when all columns start sealed", BoardEngine.hasAnyValidMove(rescued2.board))
    assertFalse("Shortest-duration sealed column 3 should be unsealed for rescue", rescued2.board.isColumnSealed(3))
  }

  @Test
  fun `EFB-004 branded Ash pop spending Vigil resets Dread streak and blocks Dread true ending`() {
    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "EFB004-DREAD", unlitCovenantActive = true, dreadLevel = 1)
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    val brandedAshBoard = gridOf(
      branded = setOf(BoardCoord(4, 2), BoardCoord(4, 3)),
    ) { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c in 2..3 -> TileKind.Ash
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }

    val state = run.copy(
      currentAct = 3,
      hp = 60,
      flasks = 3,
      dreadConsecutiveAshTurns = 2,
      combat = c0.copy(
        board = brandedAshBoard,
        player = c0.player.copy(vigil = 4),
        enemy = c0.enemy.copy(
          defId = "act3_boss_malakor",
          name = "The Hearthkeeper",
          act = 3,
          hp = 200,
          maxHp = 200,
          block = 0,
          isBoss = true,
          slot1Intent = EnemyIntent.Attack(15),
          slot2Intent = EnemyIntent.Defend(6),
        ),
      ),
    )

    val afterTap = GameEngine.execute(state, GameCommand.TapCell(4, 2))
    assertTrue(afterTap.accepted)
    assertEquals("Branded Ash pop must spend 4 Vigil", 0, afterTap.run.combat?.player?.vigil)
    assertTrue(afterTap.run.combat?.vigilSpentThisTurn == true)
    assertEquals("Spending Vigil during Dread streak must reset streak to 0", 0, afterTap.run.dreadConsecutiveAshTurns)

    val afterEnd = GameEngine.execute(afterTap.run, GameCommand.EndTurn)
    assertTrue(afterEnd.accepted)
    assertEquals(0, afterEnd.run.dreadConsecutiveAshTurns)
    assertFalse("Spending Vigil must prevent Dread true ending", afterEnd.run.dreadEndingAchieved)
    assertFalse(afterEnd.run.runWon)
  }

  @Test
  fun `EFB-008 and EFB-011 Brand persists shuffled RNG and SealColumn uses enemy RNG without mutating boardPaint`() {
    var run = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "EFB008-011")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val board = gridOf { r, c ->
      if (r < 2) TileKind.Empty else TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
    }

    // Verify SealColumn uses rng.enemy and leaves rng.boardPaint untouched when Wall rise is skipped
    val sealSetup = run.copy(
      combat = c0.copy(
        board = board,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0, skipWallRiseThisTurn = true),
        enemy = c0.enemy.copy(
          slot1Intent = EnemyIntent.SealColumn(turns = 3),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val afterSeal = GameEngine.execute(sealSetup, GameCommand.EndTurn)
    assertTrue(afterSeal.accepted)
    assertNotEquals("SealColumn must advance rng.enemy", sealSetup.rng.enemy.state, afterSeal.run.rng.enemy.state)
    assertEquals("SealColumn must not perturb rng.boardPaint", sealSetup.rng.boardPaint.state, afterSeal.run.rng.boardPaint.state)

    // Verify InflictBrand persists post-shuffle boardPaint RNG matching BoardEngine.brandTiles
    val sealCombat = requireNotNull(sealSetup.combat)
    val brandSetup = sealSetup.copy(
      combat = sealCombat.copy(
        enemy = sealCombat.enemy.copy(
          slot1Intent = EnemyIntent.InflictBrand(count = 2, damage = 0),
          slot2Intent = EnemyIntent.Defend(4),
        ),
      ),
    )
    val (_, _, expectedBrandRng) = BoardEngine.brandTiles(board, 2, brandSetup.rng.boardPaint)
    val afterBrand = GameEngine.execute(brandSetup, GameCommand.EndTurn)
    assertTrue(afterBrand.accepted)
    assertEquals(expectedBrandRng.state, afterBrand.run.rng.boardPaint.state)
  }

  @Test
  fun `EFB-019 and EFB-020 combat events emit typed CombatEventKind and exact pre-pop effective Spectrum`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "EFB019-020")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // 1. Popping a Prism+Gold group when wheel is on EMBER (runLength=3, 2.5x) advances effective
    // multiplier to runLength=4 (3.0x = 6 halfSteps) and preserves it on lethal combat-ending pop.
    val prismBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c == 2 -> TileKind.Prism
        r == 4 && c == 3 -> TileKind.Playable(Hue.GOLD)
        else -> TileKind.Playable(Hue.TIDE)
      }
    }
    val prismSetup = run.copy(
      combat = c0.copy(
        board = prismBoard,
        spectrum = SpectrumState(currentHue = Hue.EMBER, runLength = 3, halfSteps = 5),
        enemy = c0.enemy.copy(hp = 150, maxHp = 150, block = 0),
      ),
    )
    val popRes = GameEngine.execute(prismSetup, GameCommand.TapCell(4, 2))
    assertTrue(popRes.accepted)
    val popEvent = popRes.events.firstOrNull { it.kind == CombatEventKind.BOARD_POP }
    assertNotNull("TapCell must emit typed BOARD_POP event", popEvent)
    assertEquals(6, popEvent?.effectiveSpectrumHalfSteps)
    assertEquals(6, popRes.run.combat?.spectrum?.halfSteps)

    // 2. Out-of-order Tide pop after Ember evaluates at 1.0x (2 halfSteps) and resets wheel to null
    val outOfOrderRes = GameEngine.execute(prismSetup, GameCommand.TapCell(5, 0))
    assertTrue(outOfOrderRes.accepted)
    val resetEvent = outOfOrderRes.events.firstOrNull { it.kind == CombatEventKind.BOARD_POP }
    assertEquals(2, resetEvent?.effectiveSpectrumHalfSteps)
    assertNull(outOfOrderRes.run.combat?.spectrum?.currentHue)

    // Wall Overflow with non-attacking enemy emits WALL_OVERFLOW and zero ENEMY_DAMAGE events
    val fullBoard = gridOf { r, c ->
      if (r == 3 && c in 0..1) TileKind.Playable(Hue.EMBER)
      else TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
    }
    val overflowSetup = run.copy(
      hp = 60,
      flasks = 3,
      combat = c0.copy(
        board = fullBoard,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        enemy = c0.enemy.copy(
          slot1Intent = EnemyIntent.Defend(5),
          slot2Intent = EnemyIntent.Defend(5),
        ),
      ),
    )
    val overflowRes = GameEngine.execute(overflowSetup, GameCommand.EndTurn)
    assertTrue(overflowRes.accepted)
    assertTrue(overflowRes.events.any { it.kind == CombatEventKind.WALL_OVERFLOW })
    assertFalse("Wall Overflow must NOT emit ENEMY_DAMAGE", overflowRes.events.any { it.kind == CombatEventKind.ENEMY_DAMAGE })
  }

  @Test
  fun `EFB-023 CampaignGenerator validateMap rejects malformed map graphs`() {
    val (validMap, _) = CampaignGenerator.generateActMap(1, SeededRng(12345L))
    assertTrue(CampaignGenerator.validateMap(validMap))

    // 1. Duplicate node ID
    val dupIdNodes = validMap.nodes.toMutableList()
    dupIdNodes[1] = dupIdNodes[1].copy(id = dupIdNodes[0].id)
    assertFalse(CampaignGenerator.validateMap(validMap.copy(nodes = dupIdNodes)))

    // 2. Dangling edge target
    val danglingNodes = validMap.nodes.mapIndexed { idx, n ->
      if (idx == 0) n.copy(nextNodeIds = n.nextNodeIds + "nonexistent_node") else n
    }
    assertFalse(CampaignGenerator.validateMap(validMap.copy(nodes = danglingNodes)))

    // 3. Row skip edge (row 1 jumping to row 3)
    val row3Id = validMap.nodes.first { it.row == 3 }.id
    val skipRowNodes = validMap.nodes.mapIndexed { idx, n ->
      if (idx == 0) n.copy(nextNodeIds = listOf(row3Id)) else n
    }
    assertFalse(CampaignGenerator.validateMap(validMap.copy(nodes = skipRowNodes)))

    // 4. Duplicate (row, col) coordinates
    val dupCoordNodes = validMap.nodes.mapIndexed { idx, n ->
      if (idx == 1) n.copy(row = validMap.nodes[0].row, col = validMap.nodes[0].col) else n
    }
    assertFalse(CampaignGenerator.validateMap(validMap.copy(nodes = dupCoordNodes)))

    // 5. Boss node with outgoing edge
    val bossOutgoing = validMap.nodes.map { n ->
      if (n.row == 15) n.copy(nextNodeIds = listOf(validMap.nodes.first().id)) else n
    }
    assertFalse(CampaignGenerator.validateMap(validMap.copy(nodes = bossOutgoing)))
  }

  @Test
  fun `match3 opening boards have ZERO 3-in-a-row horizontal or vertical lines and always have valid swaps across all classes`() {
    HeroClass.entries.forEach { hero ->
      repeat(10) { idx ->
        val run = GameEngine.startNewRun(hero, "M3-OPEN-${hero.name}-$idx")
        val inCombat = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
        val board = requireNotNull(inCombat.combat).board
        val idleMatches = BoardEngine.findLineMatches(board)
        assertTrue(
          "Opening board for $hero seed $idx must have ZERO pre-existing 3+ lines, found $idleMatches",
          idleMatches.isEmpty(),
        )
        assertTrue(
          "Opening board for $hero seed $idx must have at least one valid Match-3 swap",
          BoardEngine.hasAnyValidSwap(board),
        )
      }
    }
  }

  @Test
  fun `match3 SwapCells clears horizontal and vertical 3-in-a-row lines awards Ink damage and rejects non-matching swaps`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "M3-SWAP-HV")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // Bent-3 EMBER pattern: (5,1)=EMBER, (5,2)=EMBER, (5,3)=TIDE, (4,3)=EMBER
    // Swapping (5,3) <-> (4,3) forms horizontal 3-in-a-row at (5,1..3)!
    val hBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && c in 1..2 -> TileKind.Playable(Hue.EMBER)
        r == 5 && c == 3 -> TileKind.Playable(Hue.TIDE)
        r == 5 && c == 4 -> TileKind.Playable(Hue.VIOLET)
        r == 4 && c == 3 -> TileKind.Playable(Hue.EMBER)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 1) % 5])
      }
    }
    val seededH = run.copy(
      combat = c0.copy(
        board = hBoard,
        arenaTwist = ArenaTwist.NONE,
        enemy = c0.enemy.copy(hp = 120, maxHp = 120, block = 0),
      ),
    )

    // Non-matching swap rejected without mutating board or moves
    val badSwap = GameEngine.execute(seededH, GameCommand.SwapCells(2, 0, 2, 1))
    assertFalse(badSwap.accepted)
    assertEquals(CommandRejectionReason.NO_MATCH_FOR_SWAP, badSwap.rejectionReason)
    assertEquals(3, badSwap.run.combat?.tapsRemaining)

    // Valid swap aligns horizontal 3 EMBER -> clears, deals damage, grants Ink, decrements moves 3 -> 2
    val goodH = GameEngine.execute(seededH, GameCommand.SwapCells(5, 3, 4, 3))
    assertTrue(goodH.accepted)
    val cH = requireNotNull(goodH.run.combat)
    assertEquals(2, cH.tapsRemaining)
    assertTrue("Enemy must take damage from 3-match", cH.enemy.hp < 120)
    assertTrue("Ember Ink must be awarded", cH.resources.inkOf(Hue.EMBER) >= 2)
    assertTrue("Zero 3+ lines may remain after swap settles", BoardEngine.findLineMatches(cH.board).isEmpty())

    // Vertical 3-in-a-row test: (3,4)=GOLD, (4,4)=GOLD, (5,4)=MOSS, (5,5)=GOLD -> swap (5,4) <-> (5,5)
    val vBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r in 3..4 && c == 4 -> TileKind.Playable(Hue.GOLD)
        r == 5 && c == 4 -> TileKind.Playable(Hue.MOSS)
        r == 5 && c == 5 -> TileKind.Playable(Hue.GOLD)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 2) % 5])
      }
    }
    val seededV = seededH.copy(combat = seededH.combat!!.copy(board = vBoard))
    val goodV = GameEngine.execute(seededV, GameCommand.SwapCells(5, 4, 5, 5))
    assertTrue(goodV.accepted)
    assertTrue(requireNotNull(goodV.run.combat).resources.inkOf(Hue.GOLD) >= 2)
    assertTrue(BoardEngine.findLineMatches(requireNotNull(goodV.run.combat).board).isEmpty())
  }

  @Test
  fun `match3 SwapCells triggers automatic multi-wave gravity cascades until zero 3-in-a-row lines remain`() {
    var run = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "M3-CASCADE")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // Setup guaranteed 2-wave cascade:
    // Row 6: (6,1)=EMBER, (6,2)=EMBER, (6,3)=TIDE, (5,3)=EMBER -> swap (6,3) <-> (5,3) clears (6,1..3)
    // Row 5 above (6,1) and (6,2): (5,1)=GOLD, (5,2)=GOLD; (6,0)=GOLD!
    // When (6,1) and (6,2) clear, (5,1)=GOLD and (5,2)=GOLD drop onto (6,1) & (6,2) beside (6,0)=GOLD -> CASCADE x2!
    val cascadeBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 6 && c == 0 -> TileKind.Playable(Hue.GOLD)
        r == 6 && c in 1..2 -> TileKind.Playable(Hue.EMBER)
        r == 6 && c == 3 -> TileKind.Playable(Hue.TIDE)
        r == 5 && c == 3 -> TileKind.Playable(Hue.EMBER)
        r == 5 && c in 1..2 -> TileKind.Playable(Hue.GOLD)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 3) % 5])
      }
    }
    val seeded = run.copy(
      combat = c0.copy(
        board = cascadeBoard,
        enemy = c0.enemy.copy(hp = 150, maxHp = 150, block = 0),
      ),
    )
    val res = GameEngine.execute(seeded, GameCommand.SwapCells(6, 3, 5, 3))
    assertTrue(res.accepted)
    val popEvents = res.events.filter { it.kind == CombatEventKind.BOARD_POP }
    assertTrue("Expected at least 2 cascade BOARD_POP waves, got ${popEvents.size}", popEvents.size >= 2)
    assertTrue("Expected CASCADE x2 event", res.events.any { it.message.contains("CASCADE ×2") })
    val c1 = requireNotNull(res.run.combat)
    assertTrue("Both Ember and Gold Ink should be earned across cascade", c1.resources.inkOf(Hue.EMBER) >= 2 && c1.resources.inkOf(Hue.GOLD) >= 2)
    assertTrue("Zero 3+ lines may remain after cascade finishes", BoardEngine.findLineMatches(c1.board).isEmpty())
    assertTrue("A playable Match-3 swap must remain", BoardEngine.hasAnyValidSwap(c1.board))
  }

  @Test
  fun `match3 Prism swap detonates hue cross and 4-match shatters Stone while clearing adjacent Ash`() {
    var run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "M3-PRISM-STONE")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // 4-match MOSS setup: (5,1)=MOSS, (5,2)=MOSS, (5,4)=MOSS, (5,3)=TIDE, (4,3)=MOSS
    // Adjacent Stone at (4,2) and Ash at (4,1)
    val fourBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && (c == 1 || c == 2 || c == 4) -> TileKind.Playable(Hue.MOSS)
        r == 5 && c == 3 -> TileKind.Playable(Hue.TIDE)
        r == 4 && c == 3 -> TileKind.Playable(Hue.MOSS)
        r == 4 && c == 2 -> TileKind.Stone
        r == 4 && c == 1 -> TileKind.Ash
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }
    val seeded4 = run.copy(
      combat = c0.copy(
        board = fourBoard,
        enemy = c0.enemy.copy(hp = 140, maxHp = 140, block = 0),
      ),
    )
    val res4 = GameEngine.execute(seeded4, GameCommand.SwapCells(5, 3, 4, 3))
    assertTrue(res4.accepted)
    val c4 = requireNotNull(res4.run.combat)
    assertTrue("4-match must shatter adjacent Stone (+2 Guard)", c4.player.guardBlock >= 2)
    assertTrue("Hollow Crown must award Spark from cleared adjacent Ash", c4.resources.spark >= 1)

    // Prism <-> Hue swap always succeeds and detonates Prism + partner hue in row/col
    val prismBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 4 && c == 2 -> TileKind.Prism
        r == 4 && c == 3 -> TileKind.Playable(Hue.VIOLET)
        r == 4 && c == 5 -> TileKind.Playable(Hue.VIOLET)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }
    val seededP = run.copy(combat = c0.copy(board = prismBoard, enemy = c0.enemy.copy(hp = 140, maxHp = 140, block = 0)))
    val resP = GameEngine.execute(seededP, GameCommand.SwapCells(4, 2, 4, 3))
    assertTrue("Swapping Prism with adjacent Playable tile must always succeed", resP.accepted)
    assertTrue(requireNotNull(resP.run.combat).resources.inkOf(Hue.VIOLET) >= 2)
  }

  @Test
  fun `match3 SwapCells command serializes and replays deterministically`() {
    val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "M3-REPLAY")
    val nodeId = GameEngine.availableNodeIds(base).first()
    val commands = mutableListOf<GameCommand>(GameCommand.ChooseNode(nodeId))
    var cur = GameEngine.execute(base, commands.last()).run

    repeat(3) {
      val combat = requireNotNull(cur.combat)
      val swap = BoardEngine.findValidSwaps(combat.board, combat.spectrum).first()
      val cmd = GameCommand.SwapCells(swap.from.row, swap.from.col, swap.to.row, swap.to.col)
      val serialized = cmd.serialize()
      assertEquals(cmd, GameCommand.deserialize(serialized))
      commands.add(cmd)
      cur = GameEngine.execute(cur, cmd).run
    }

    val replayed = GameEngine.replay(
      heroClass = HeroClass.ASHEN_MONK,
      seedText = "M3-REPLAY",
      previousRunDeckIds = emptyList(),
      commands = commands,
    )
    assertEquals(cur, replayed)
  }

  @Test
  fun `4-match forges Prism at destination, triggers Ember Surge free move up to cap, and charges Overdrive`() {
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "SURGE-PRISM-TEST")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)

    // 4-match EMBER setup on row 5: (5,1)=EMBER, (5,2)=EMBER, (5,4)=EMBER, (5,3)=GOLD, (4,3)=EMBER
    // Swapping (4,3) -> (5,3) moves EMBER into (5,3) forming a 4-match and forging a Prism at (5,3)!
    val fourBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && (c == 1 || c == 2 || c == 4) -> TileKind.Playable(Hue.EMBER)
        r == 5 && c == 3 -> TileKind.Playable(Hue.GOLD)
        r == 4 && c == 3 -> TileKind.Playable(Hue.EMBER)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 1) % 5])
      }
    }
    val seeded = run.copy(
      combat = c0.copy(
        board = fourBoard,
        arenaTwist = ArenaTwist.NONE,
        enemy = c0.enemy.copy(hp = 180, maxHp = 180, block = 0),
        player = c0.player.copy(overdriveCharge = 0),
        turn = c0.turn.copy(tapsRemaining = 3, bonusMovesGrantedThisTurn = 0),
      ),
    )

    val res = GameEngine.execute(seeded, GameCommand.SwapCells(4, 3, 5, 3))
    assertTrue("4-match swap must be accepted", res.accepted)
    val c1 = requireNotNull(res.run.combat)

    assertTrue(
      "4-match must emit PRISM_FORGED event",
      res.events.any { it.kind == CombatEventKind.PRISM_FORGED },
    )
    assertTrue(
      "4-match must leave a forged Prism on the board",
      c1.board.grid.flatten().any { it.kind == TileKind.Prism },
    )
    assertTrue(
      "4-match must emit BONUS_MOVE Ember Surge event",
      res.events.any { it.kind == CombatEventKind.BONUS_MOVE },
    )
    assertEquals(
      "Ember Surge must refund the move so tapsRemaining stays 3",
      3,
      c1.turn.tapsRemaining,
    )
    assertEquals(1, c1.turn.bonusMovesGrantedThisTurn)
    assertTrue("4-match must significantly charge Overdrive", c1.player.overdriveCharge >= 30)

    // Verify cap: when bonusMovesGrantedThisTurn == MAX_BONUS_MOVES_PER_TURN, another 4-match spends a move normally
    val seededCombat = requireNotNull(seeded.combat)
    val cappedSeeded = seeded.copy(
      combat = seededCombat.copy(
        turn = seededCombat.turn.copy(
          tapsRemaining = 3,
          bonusMovesGrantedThisTurn = TurnState.MAX_BONUS_MOVES_PER_TURN,
        ),
      ),
    )
    val cappedRes = GameEngine.execute(cappedSeeded, GameCommand.SwapCells(4, 3, 5, 3))
    assertTrue(cappedRes.accepted)
    assertEquals(
      "Bonus moves must be capped at MAX_BONUS_MOVES_PER_TURN",
      2,
      requireNotNull(cappedRes.run.combat).turn.tapsRemaining,
    )
  }

  @Test
  fun `Kindle Overdrive rejects below 100, executes class-unique ultimates for all 3 classes, and serializes`() {
    assertEquals(GameCommand.TriggerOverdrive, GameCommand.deserialize(GameCommand.TriggerOverdrive.serialize()))

    for (hero in HeroClass.entries) {
      var run = GameEngine.startNewRun(hero, "OVERDRIVE-${hero.name}")
      run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
      val c0 = requireNotNull(run.combat)

      val board = gridOf { r, c ->
        when {
          r < 2 -> TileKind.Empty
          r == 4 && (c == 1 || c == 2) -> TileKind.Playable(Hue.EMBER)
          r == 4 && c == 3 -> TileKind.Playable(Hue.GOLD)
          r == 5 && (c == 1 || c == 2) -> TileKind.Stone
          r == 5 && (c == 3 || c == 4) -> TileKind.Ash
          else -> TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
        }
      }

      val notReady = run.copy(
        combat = c0.copy(
          board = board,
          player = c0.player.copy(overdriveCharge = 80),
        ),
      )
      val rejected = GameEngine.execute(notReady, GameCommand.TriggerOverdrive)
      assertFalse("Overdrive below 100 must be rejected", rejected.accepted)
      assertEquals(CommandRejectionReason.OVERDRIVE_NOT_READY, rejected.rejectionReason)

      val ready = run.copy(
        combat = c0.copy(
          board = board,
          enemy = c0.enemy.copy(hp = 200, maxHp = 200, block = 0),
          player = c0.player.copy(overdriveCharge = PlayerCombatState.MAX_OVERDRIVE, guardBlock = 0, vigil = 0),
          resources = c0.resources.copy(spark = 0),
        ),
      )
      val fired = GameEngine.execute(ready, GameCommand.TriggerOverdrive)
      assertTrue("Overdrive at 100 must succeed for $hero", fired.accepted)
      val after = requireNotNull(fired.run.combat)
      assertEquals("Overdrive charge must reset to 0 after firing", 0, after.player.overdriveCharge)
      assertTrue("Overdrive must damage enemy", after.enemy.hp < 200)
      assertTrue("Overdrive must emit OVERDRIVE event", fired.events.any { it.kind == CombatEventKind.OVERDRIVE })

      when (hero) {
        HeroClass.ASHEN_MONK -> assertTrue("Humble Lv.1 Prāṇa-Mudrā must grant +2 Vigil", after.player.vigil >= 2)
        HeroClass.QUARRY_WITCH -> assertTrue("Humble Lv.1 Quarry Chip must grant +4 Guard", after.player.guardBlock >= 4)
        HeroClass.THE_UNLIT -> assertTrue("Humble Lv.1 Hollow Spark must grant +1 Spark", after.resources.spark >= 1)
      }
    }
  }

  @Test
  fun `ArenaTwist assigns deterministically and fires Blood Kiln, Brittle Quarry, and Echo Chamber combat hooks`() {
    val t1 = ArenaTwist.forNode(12345L, "act1_r1_c0", MapNodeType.COMBAT)
    val t2 = ArenaTwist.forNode(12345L, "act1_r1_c0", MapNodeType.COMBAT)
    assertEquals("ArenaTwist.forNode must be deterministic", t1, t2)
    assertNotEquals("Combat nodes must receive an active ArenaTwist", ArenaTwist.NONE, t1)
    assertEquals(ArenaTwist.NONE, ArenaTwist.forNode(12345L, "act1_r5_c0", MapNodeType.CAMPFIRE))

    // 1. BLOOD_KILN heals +3 HP on 4+ match
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "TWIST-HOOKS")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val fourBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && (c == 1 || c == 2 || c == 4) -> TileKind.Playable(Hue.TIDE)
        r == 5 && c == 3 -> TileKind.Playable(Hue.VIOLET)
        r == 4 && c == 3 -> TileKind.Playable(Hue.TIDE)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 1) % 5])
      }
    }
    val bloodRun = run.copy(
      hp = 40,
      maxHp = 80,
      combat = c0.copy(
        board = fourBoard,
        arenaTwist = ArenaTwist.BLOOD_KILN,
        enemy = c0.enemy.copy(hp = 160, maxHp = 160, block = 0),
      ),
    )
    val bloodRes = GameEngine.execute(bloodRun, GameCommand.SwapCells(4, 3, 5, 3))
    assertTrue(bloodRes.accepted)
    assertTrue("Blood Kiln must heal +3 HP on 4+ match", bloodRes.run.hp >= 43)

    // 2. BRITTLE_QUARRY shatters adjacent Stone even on a 3-match
    val brittleBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && (c == 1 || c == 2) -> TileKind.Playable(Hue.MOSS)
        r == 5 && c == 3 -> TileKind.Playable(Hue.EMBER)
        r == 4 && c == 3 -> TileKind.Playable(Hue.MOSS)
        r == 4 && c == 2 -> TileKind.Stone
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
      }
    }
    val brittleRun = run.copy(
      combat = c0.copy(
        board = brittleBoard,
        arenaTwist = ArenaTwist.BRITTLE_QUARRY,
        player = c0.player.copy(guardBlock = 0),
        enemy = c0.enemy.copy(hp = 160, maxHp = 160, block = 0),
      ),
    )
    val brittleRes = GameEngine.execute(brittleRun, GameCommand.SwapCells(4, 3, 5, 3))
    assertTrue(brittleRes.accepted)
    assertTrue(
      "Brittle Quarry must shatter adjacent Stone on 3-match and grant Guard",
      requireNotNull(brittleRes.run.combat).player.guardBlock >= 3,
    )

    // 3. ECHO_CHAMBER boosts first card cast each turn (+5 dmg & +4 Guard)
    val cardUid = c0.combatDeck.hand.first().uid
    val echoRun = run.copy(
      combat = c0.copy(
        arenaTwist = ArenaTwist.ECHO_CHAMBER,
        turn = c0.turn.copy(phase = TurnPhase.CAST, tapsRemaining = 0),
        cardsPlayedThisTurn = 0,
        player = c0.player.copy(guardBlock = 0),
        resources = c0.resources.copy(inkPools = Hue.entries.associateWith { 5 }),
        enemy = c0.enemy.copy(hp = 160, maxHp = 160, block = 0),
      ),
    )
    val echoRes = GameEngine.execute(echoRun, GameCommand.CastCard(cardUid))
    assertTrue(echoRes.accepted)
    val echoCombat = requireNotNull(echoRes.run.combat)
    assertTrue("Echo Chamber must add +4 Guard on first card played", echoCombat.player.guardBlock >= 4)
    assertTrue("Echo Chamber must emit announcement", echoRes.events.any { it.message.contains("Echo Chamber") })
  }

  @Test
  fun `SkipRewardCard awards +12 Cinders, RerollRewardCards costs 15 once, and CampfireGamble trades HP for Cinders and upgrade`() {
    assertEquals(GameCommand.RerollRewardCards, GameCommand.deserialize(GameCommand.RerollRewardCards.serialize()))
    assertEquals(GameCommand.CampfireGamble, GameCommand.deserialize(GameCommand.CampfireGamble.serialize()))

    val base = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "REWARD-CAMP-TEST")
    val starterChoices = GameCatalog.starterDeckIds(HeroClass.QUARRY_WITCH).distinct().take(3)

    // 1. RerollRewardCards once for 15 Cinders, then rejects second attempt
    val rewardRun = base.copy(
      cinders = 30,
      pendingReward = RewardBundle(
        cindersEarned = 20,
        flaskRefill = 0,
        cardChoices = starterChoices,
        relicRewardId = null,
        cardPicked = false,
        relicPicked = false,
        rerollUsed = false,
      ),
    )
    val reroll1 = GameEngine.execute(rewardRun, GameCommand.RerollRewardCards)
    assertTrue("First card reroll must succeed", reroll1.accepted)
    assertEquals(30 - GameEngine.REROLL_CARDS_COST, reroll1.run.cinders)
    assertTrue(requireNotNull(reroll1.run.pendingReward).rerollUsed)
    assertEquals(3, requireNotNull(reroll1.run.pendingReward).cardChoices.size)

    val reroll2 = GameEngine.execute(reroll1.run, GameCommand.RerollRewardCards)
    assertFalse("Second card reroll must be rejected", reroll2.accepted)
    assertEquals(CommandRejectionReason.REROLL_ALREADY_USED, reroll2.rejectionReason)

    // 2. SkipRewardCard awards +12 Cinders
    val skipped = GameEngine.execute(reroll1.run, GameCommand.SkipRewardCard)
    assertTrue("SkipRewardCard must succeed", skipped.accepted)
    assertEquals(
      reroll1.run.cinders + GameEngine.SKIP_CARD_CINDER_BONUS,
      skipped.run.cinders,
    )
    assertTrue(requireNotNull(skipped.run.pendingReward).cardPicked)

    // 3. CampfireGamble rejects when HP <= 8, succeeds otherwise
    val campfireNode = base.actMap.nodes.first { it.type == MapNodeType.CAMPFIRE }
    val campBase = base.copy(currentNodeId = campfireNode.id, inNonCombatNode = true, campfireUsed = false)
    val lowHpCamp = campBase.copy(hp = 8, cinders = 10)
    val gambleRejected = GameEngine.execute(lowHpCamp, GameCommand.CampfireGamble)
    assertFalse("CampfireGamble must reject when HP <= 8", gambleRejected.accepted)
    assertEquals(CommandRejectionReason.INSUFFICIENT_HP, gambleRejected.rejectionReason)

    val healthyCamp = campBase.copy(hp = 40, cinders = 10)
    val upgradedBefore = healthyCamp.masterDeck.count { it.upgraded }
    val gambleOk = GameEngine.execute(healthyCamp, GameCommand.CampfireGamble)
    assertTrue("CampfireGamble must succeed when HP > 8", gambleOk.accepted)
    assertEquals(40 - GameEngine.CAMPFIRE_GAMBLE_HP_COST, gambleOk.run.hp)
    assertEquals(10 + GameEngine.CAMPFIRE_GAMBLE_CINDERS, gambleOk.run.cinders)
    assertTrue(gambleOk.run.campfireUsed)
    assertEquals(upgradedBefore + 1, gambleOk.run.masterDeck.count { it.upgraded })
  }

  @Test
  fun `Ashen Monk starts humble at Lv1 and scales massively via Upaya passives, Dharana sockets, and Tantric artifacts`() {
    // Verify command serialization roundtrips for all new progression & spiritual commands
    val cmds = listOf(
      GameCommand.UnlockPassiveNode("pass_foundry_1"),
      GameCommand.SocketSupport("sup_echo_strike", -1),
      GameCommand.SkipSupportChoice,
      GameCommand.BindArtifact("art_rudraksha_mala"),
      GameCommand.SkipArtifactChoice,
      GameCommand.WitnessIllusion,
    )
    cmds.forEach { cmd ->
      assertEquals(cmd, GameCommand.deserialize(cmd.serialize()))
    }

    // 1. Humble Lv.1 Āṇava Initiate baseline
    val fresh = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "TRIKA-GROWTH-1")
    assertEquals(1, fresh.growth.level)
    assertEquals(0, fresh.growth.unspentPassivePoints)
    assertTrue(fresh.growth.unlockedPassiveIds.isEmpty())
    assertTrue(fresh.growth.socketedSupportIds.isEmpty())
    assertTrue(fresh.growth.tantricArtifactIds.isEmpty())
    assertEquals(1, fresh.growth.maxSockets)
    assertEquals("Āṇava Initiate", fresh.growth.stageTitle())

    // 2. XP progression unlocks Lv2 (passive pt), Lv3 (2nd socket), and Lv6 (3rd socket)
    val lv2 = fresh.growth.gainXp(20)
    assertTrue(lv2.levelsGained > 0)
    assertEquals(2, lv2.growth.level)
    assertEquals(1, lv2.growth.unspentPassivePoints)
    assertEquals(1, lv2.growth.maxSockets)

    val lv3 = lv2.growth.gainXp(25)
    assertEquals(3, lv3.growth.level)
    assertTrue("Lv.3 must unlock 2nd Dhāraṇā socket", lv3.unlockedNewSocket)
    assertEquals(2, lv3.growth.maxSockets)

    val lv6 = lv3.growth.gainXp(120)
    assertTrue(lv6.growth.level >= 6)
    assertEquals(3, lv6.growth.maxSockets)

    // 3. Upāya passive prerequisites enforced: Tier 2 locked before Tier 1
    val withPoints = fresh.copy(growth = fresh.growth.copy(unspentPassivePoints = 2))
    val lockedAttempt = GameEngine.execute(withPoints, GameCommand.UnlockPassiveNode("pass_foundry_2"))
    assertFalse("Tier 2 Upāya node must require Tier 1 prerequisite", lockedAttempt.accepted)
    assertEquals(CommandRejectionReason.PASSIVE_NODE_LOCKED, lockedAttempt.rejectionReason)

    val unlock1 = GameEngine.execute(withPoints, GameCommand.UnlockPassiveNode("pass_foundry_1"))
    assertTrue(unlock1.accepted)
    assertTrue(unlock1.run.growth.hasPassive("pass_foundry_1"))
    assertEquals(1, unlock1.run.growth.unspentPassivePoints)

    // 4. Compare humble Lv1 Prāṇa-Mudrā vs fully linked 3-socket + Tantric artifact evolved Prāṇa-Mudrā!
    val combatBase = GameEngine.enterNode(fresh, GameEngine.availableNodeIds(fresh).first())
    val c0 = requireNotNull(combatBase.combat)
    val readyLv1 = combatBase.copy(
      combat = c0.copy(
        enemy = c0.enemy.copy(hp = 200, maxHp = 200, block = 0),
        player = c0.player.copy(overdriveCharge = PlayerCombatState.MAX_OVERDRIVE),
        turn = c0.turn.copy(comboMultTenths = TurnState.BASE_MULT_TENTHS),
      )
    )
    val resLv1 = GameEngine.execute(readyLv1, GameCommand.TriggerOverdrive)
    assertTrue(resLv1.accepted)
    val dmgLv1 = 200 - requireNotNull(resLv1.run.combat).enemy.hp
    assertTrue(
      "Humble unlinked Lv.1 Prāṇa-Mudrā deals modest 8..14 Spanda (was $dmgLv1)",
      dmgLv1 in 8..14,
    )

    val evolvedRun = readyLv1.copy(
      hp = 40,
      growth = CharacterGrowthState(
        level = 6,
        maxSockets = 3,
        unlockedPassiveIds = listOf("pass_foundry_1", "pass_foundry_2"),
        socketedSupportIds = listOf("sup_echo_strike", "sup_hue_resonance", "sup_blood_kiln"),
        tantricArtifactIds = listOf("art_rudraksha_mala", "art_tantraloka_folio"),
      ),
      combat = requireNotNull(readyLv1.combat).copy(
        turn = c0.turn.copy(comboMultTenths = 12),
      ),
    )
    val resEvolved = GameEngine.execute(evolvedRun, GameCommand.TriggerOverdrive)
    assertTrue(resEvolved.accepted)
    val dmgEvolved = 200 - requireNotNull(resEvolved.run.combat).enemy.hp
    assertTrue(
      "Linked 3-socket Prāṇa-Mudrā ($dmgEvolved) must massively dwarf humble Lv.1 ($dmgLv1)",
      dmgEvolved >= dmgLv1 * 3,
    )
    assertEquals("Rakta-Tarpaṇa blood offering must spend 4 HP", 36, resEvolved.run.hp)
  }

  @Test
  fun `Emotional Attachment triggers Maya Grip and Vikalpa Collapse while Saksi Witnessing shatters Illusion for Pratyabhijna`() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "MINDILLUSION-PATH")
    val entered = GameEngine.enterNode(started, GameEngine.availableNodeIds(started).first())
    val c0 = requireNotNull(entered.combat)

    // 1. Setup Veil of Wrath (Ember attachment) with an Ember match available
    val emberBoard = gridOf { r, c ->
      when {
        r < 2 -> TileKind.Empty
        r == 5 && (c == 1 || c == 2) -> TileKind.Playable(Hue.EMBER)
        r == 5 && c == 3 -> TileKind.Playable(Hue.GOLD)
        r == 4 && c == 3 -> TileKind.Playable(Hue.EMBER)
        else -> TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c + 2) % 5])
      }
    }
    val wrathRun = entered.copy(
      hp = 50,
      maxHp = 60,
      combat = c0.copy(
        board = emberBoard,
        arenaTwist = ArenaTwist.NONE,
        enemy = c0.enemy.copy(
          hp = 200,
          maxHp = 200,
          block = 10,
          slot1Intent = EnemyIntent.Attack(damage = 12, hits = 1),
          slot2Intent = EnemyIntent.Defend(block = 4),
        ),
        player = c0.player.copy(guardBlock = 0),
        mindIllusion = MindIllusionState(
          illusion = EmotionalIllusion.KRODHA_WRATH,
          attachment = 4,
          veilLayers = 3,
        ),
      ),
    )

    // Clinging to Ember pushes Attachment 4 -> 6 (MĀYĀ GRIP!)
    val afterCling = GameEngine.execute(wrathRun, GameCommand.SwapCells(4, 3, 5, 3))
    assertTrue(afterCling.accepted)
    val gripMind = requireNotNull(afterCling.run.combat).mindIllusion
    assertEquals(6, gripMind.attachment)
    assertTrue("Attachment >= 6 must activate Māyā Grip", gripMind.isEntangled)

    // EndTurn under Māyā Grip amplifies enemy 12 dmg by +25% -> 15 dmg!
    val hpBeforeHit = afterCling.run.hp
    val castPhaseRun = GameEngine.execute(afterCling.run, GameCommand.ProceedToCast).run
    val afterEnemyHit = GameEngine.execute(castPhaseRun, GameCommand.EndTurn)
    assertTrue(afterEnemyHit.accepted)
    assertEquals(
      "Māyā Grip (+25%) must amplify 12 base enemy damage to 15",
      hpBeforeHit - 15,
      afterEnemyHit.run.hp,
    )

    // 2. Sākṣī Witness dissolves Attachment (-3), grants Guard & Vimarśa, and peels Illusion Veil;
    //    peeling the final veil triggers PRATYABHIJÑĀ (breaks enemy Block, deals Clarity dmg, +1.0× Vimarśa)!
    val nearShatter = wrathRun.copy(
      combat = requireNotNull(wrathRun.combat).copy(
        enemy = c0.enemy.copy(hp = 120, maxHp = 120, block = 15),
        mindIllusion = MindIllusionState(
          illusion = EmotionalIllusion.KRODHA_WRATH,
          attachment = 5,
          veilLayers = 1,
          shattered = false,
          witnessUsedThisTurn = false,
        ),
      ),
    )
    val witnessed = GameEngine.execute(nearShatter, GameCommand.WitnessIllusion)
    assertTrue("Sākṣī Witness must succeed", witnessed.accepted)
    val postCombat = requireNotNull(witnessed.run.combat)
    assertTrue("Final veil peel must shatter the Mind Illusion (Pratyabhijñā!)", postCombat.mindIllusion.shattered)
    assertEquals(0, postCombat.mindIllusion.veilLayers)
    assertEquals(0, postCombat.mindIllusion.attachment)
    assertEquals("Pratyabhijñā must shatter enemy Block to 0", 0, postCombat.enemy.block)
    assertTrue("Pratyabhijñā must deal clarity damage to enemy", postCombat.enemy.hp < 120)
    assertTrue("Pratyabhijñā must boost Vimarśa MULT >= 2.5×", postCombat.turn.comboMultTenths >= 25)
    assertTrue(witnessed.events.any { it.kind == CombatEventKind.ILLUSION_SHATTERED })

    // Second Witness on same turn is rejected
    val secondWitness = GameEngine.execute(witnessed.run, GameCommand.WitnessIllusion)
    assertFalse(secondWitness.accepted)
    assertEquals(CommandRejectionReason.WITNESS_ALREADY_USED, secondWitness.rejectionReason)
  }

  @Test
  fun `P0_5 ensurePlayableSwap preserves all active Seal durations on 6-of-7 sealed boards while guaranteeing valid swap`() {
    val deck = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).mapIndexed { idx, id ->
      CardInstance(idx.toLong() + 1L, id)
    }
    val (paintDeck, rng0) = BoardPaintDeck.createInitial(deck, SeededRng(5150L))

    // Case 1: 6 of 7 columns sealed; column 6 unsealed with 5 non-matching occupied tiles -> zero valid swaps initially
    val seals6 = mapOf(0 to 3, 1 to 2, 2 to 3, 3 to 1, 4 to 2, 5 to 3)
    val sealed6Board = gridOf(sealedColumns = seals6) { r, c ->
      when {
        r < 2 -> TileKind.Empty
        c == 6 -> TileKind.Playable(Hue.WHEEL_ORDER[r % 5]) // 5 distinct hues in col 6 -> no swap
        else -> TileKind.Playable(Hue.MOSS)
      }
    }
    assertFalse("Board should start without any valid Match-3 swap", BoardEngine.hasAnyValidSwap(sealed6Board))

    val rescued6 = BoardEngine.ensurePlayableSwap(sealed6Board, paintDeck, rng0)
    assertTrue("Rescued 6-of-7 sealed board must have a valid Match-3 swap", BoardEngine.hasAnyValidSwap(rescued6.board))
    assertTrue("Rescued board must have zero idle 3+ lines", BoardEngine.findLineMatches(rescued6.board).isEmpty())
    assertEquals(
      "All 6 active Seal column durations MUST be preserved intact during vertical rescue",
      seals6,
      rescued6.board.sealedColumns,
    )

    // Case 2: 6 of 7 columns sealed with only 2 occupied tiles in column 6 -> vertical Prism+Playable rescue preserves all 6 seals
    val sparseSealed6 = gridOf(sealedColumns = seals6) { r, c ->
      when {
        c == 6 && r == 5 -> TileKind.Playable(Hue.EMBER)
        c == 6 && r == 6 -> TileKind.Playable(Hue.TIDE)
        c < 6 && r >= 2 -> TileKind.Playable(Hue.MOSS)
        else -> TileKind.Empty
      }
    }
    val rescuedSparse6 = BoardEngine.ensurePlayableSwap(sparseSealed6, paintDeck, rescued6.rng)
    assertTrue("Sparse 6-of-7 sealed board must have a valid Match-3 swap", BoardEngine.hasAnyValidSwap(rescuedSparse6.board))
    assertEquals(
      "All 6 active Seal durations MUST remain untouched even on sparse 1-unsealed-column board",
      seals6,
      rescuedSparse6.board.sealedColumns,
    )

    // Case 3: All 7 columns sealed -> unseal ONLY the shortest-duration column (col 3, turns=1), preserving the other 6
    val sealsAll7 = mapOf(0 to 3, 1 to 2, 2 to 3, 3 to 1, 4 to 2, 5 to 3, 6 to 2)
    val allSealedBoard = gridOf(sealedColumns = sealsAll7) { r, c ->
      if (r < 2) TileKind.Empty else TileKind.Playable(Hue.WHEEL_ORDER[(r + c) % 5])
    }
    val rescuedAll7 = BoardEngine.ensurePlayableSwap(allSealedBoard, paintDeck, rescuedSparse6.rng)
    assertTrue("All-sealed board rescue must guarantee a valid Match-3 swap", BoardEngine.hasAnyValidSwap(rescuedAll7.board))
    assertFalse("Only shortest-duration sealed column 3 should be unsealed", rescuedAll7.board.isColumnSealed(3))
    assertEquals(
      "The remaining 6 sealed columns must keep their exact durations",
      sealsAll7 - 3,
      rescuedAll7.board.sealedColumns,
    )
  }

  @Test
  fun `P0_1 riseIncomingRow enforces Match-3 swap invariant with zero idle lines and preserves Seal durations`() {
    val deck = GameCatalog.starterDeckIds(HeroClass.QUARRY_WITCH).mapIndexed { idx, id ->
      CardInstance(idx.toLong() + 1L, id)
    }
    val (paintDeck, rng0) = BoardPaintDeck.createInitial(deck, SeededRng(7711L))
    val seals = mapOf(1 to 3, 4 to 2)
    // Board with no Match-3 swap initially and an incoming row that could otherwise leave zero swaps or idle lines
    val board = gridOf(sealedColumns = seals) { r, c ->
      if (r < 2) TileKind.Empty else TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + c) % 5])
    }
    val rise = BoardEngine.riseIncomingRow(board, paintDeck, rng0, currentRunHue = Hue.TIDE)
    assertTrue("Post-Wall-Rise board MUST have a valid Match-3 swap", BoardEngine.hasAnyValidSwap(rise.board, Hue.TIDE))
    assertTrue("Post-Wall-Rise board MUST have zero idle 3+ lines", BoardEngine.findLineMatches(rise.board).isEmpty())
    assertEquals("Post-Wall-Rise recovery MUST preserve active Seal column durations", seals, rise.board.sealedColumns)
  }

  @Test
  fun `P0_4 SimulationRunner truthfully accounts attempted completed incomplete runs and mid-turn victory turnsTaken`() {
    val report = SimulationRunner.runBatchSimulation(seedsPerClass = 4, dreadLevel = 0)
    assertEquals(12, report.runsAttempted)
    assertEquals(report.runsAttempted, report.runsCompleted + report.incompleteRuns)
    assertEquals(0, report.incompleteRuns)
    assertEquals(
      "Completed runs must equal wins + hpDefeats + zeroFlaskCollapses",
      report.runsCompleted,
      report.wins + report.hpDefeats + report.zeroFlaskCollapses,
    )
    assertTrue("Average turns per run must be strictly positive (got ${report.avgTurnsPerRun})", report.avgTurnsPerRun >= 1.0)
    assertEquals(HeroClass.entries.toSet(), report.perClassBreakdown.keys)
    HeroClass.entries.forEach { hero ->
      val stats = requireNotNull(report.perClassBreakdown[hero])
      assertEquals(4, stats.runsAttempted)
      assertEquals(4, stats.runsCompleted)
      assertEquals(0, stats.incompleteRuns)
      assertTrue("Per-class avgTurnsPerRun must be positive for $hero", stats.avgTurnsPerRun >= 1.0)
    }

    // Verify mid-turn Turn-1 kill increments stats.turnsTaken to 1 (never 0)
    var run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "TURN1-COUNT")
    run = GameEngine.enterNode(run, GameEngine.availableNodeIds(run).first())
    val c0 = requireNotNull(run.combat)
    val swap = BoardEngine.findValidSwaps(c0.board, c0.spectrum).first()
    val oneHpRun = run.copy(combat = c0.copy(enemy = c0.enemy.copy(hp = 1, maxHp = 1, block = 0)))
    val killRes = GameEngine.execute(oneHpRun, GameCommand.SwapCells(swap.from.row, swap.from.col, swap.to.row, swap.to.col))
    assertTrue(killRes.accepted)
    assertEquals(TurnPhase.COMBAT_WON, killRes.run.combat?.phase)
    assertEquals("Turn 1 mid-turn victory must record turnsTaken = 1", 1, killRes.run.stats.turnsTaken)
  }

  @Test
  fun `P0_7 authoritative starter decks lock exact card IDs counts class ownership and frame hues for all three classes`() {
    val monkIds = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK)
    assertEquals(10, monkIds.size)
    assertEquals(
      mapOf(
        "monk_ember_palm" to 3,
        "monk_moss_stance" to 3,
        "monk_bead_strike" to 2,
        "monk_quiet_root" to 1,
        "monk_cinder_sutra" to 1,
      ),
      monkIds.groupingBy { it }.eachCount(),
    )
    monkIds.forEach { id ->
      val def = GameCatalog.cardById(id)
      assertEquals(HeroClass.ASHEN_MONK, def.heroClass)
      assertTrue(def.frameHue == Hue.EMBER || def.frameHue == Hue.MOSS)
    }

    val witchIds = GameCatalog.starterDeckIds(HeroClass.QUARRY_WITCH)
    assertEquals(10, witchIds.size)
    assertEquals(
      mapOf(
        "witch_tide_vein" to 3,
        "witch_violet_shroud" to 3,
        "witch_slate_bolt" to 2,
        "witch_wells_echo" to 1,
        "witch_quarry_purge" to 1,
      ),
      witchIds.groupingBy { it }.eachCount(),
    )
    witchIds.forEach { id ->
      val def = GameCatalog.cardById(id)
      assertEquals(HeroClass.QUARRY_WITCH, def.heroClass)
      assertTrue(def.frameHue == Hue.TIDE || def.frameHue == Hue.VIOLET)
    }

    val unlitIds = GameCatalog.starterDeckIds(HeroClass.THE_UNLIT)
    assertEquals(12, unlitIds.size)
    assertEquals(
      mapOf(
        "unlit_gold_strike" to 4,
        "unlit_ash_ward" to 4,
        "unlit_pop_shot" to 2,
        "unlit_sift" to 1,
        "unlit_hollow_crown" to 1,
      ),
      unlitIds.groupingBy { it }.eachCount(),
    )
    unlitIds.forEach { id ->
      val def = GameCatalog.cardById(id)
      assertEquals(HeroClass.THE_UNLIT, def.heroClass)
      assertEquals(Hue.GOLD, def.frameHue)
    }
  }

  @Test
  fun `simulation batch rejects invalid inputs and reports exact zero-run accounting`() {
    val empty = SimulationRunner.runBatchSimulation(seedsPerClass = 0, dreadLevel = 0)
    assertEquals(0, empty.runsAttempted)
    assertEquals(0, empty.runsCompleted)
    assertEquals(0, empty.incompleteRuns)
    assertEquals(0, empty.turnCapHits)
    assertEquals(0, empty.nodeCapHits)
    assertTrue(empty.perClassBreakdown.values.all { it.runsAttempted == 0 && it.runsCompleted == 0 })

    assertThrows(IllegalArgumentException::class.java) {
      SimulationRunner.runBatchSimulation(seedsPerClass = -1, dreadLevel = 0)
    }
    assertThrows(IllegalArgumentException::class.java) {
      SimulationRunner.runBatchSimulation(seedsPerClass = 0, dreadLevel = 4)
    }
  }

  @Test
  fun `Phase4 deterministic multi-seed campaign validation completes cleanly across all three classes and Dread 2`() {
    val d0First = SimulationRunner.runBatchSimulation(seedsPerClass = 8, dreadLevel = 0)
    val d0Second = SimulationRunner.runBatchSimulation(seedsPerClass = 8, dreadLevel = 0)
    assertEquals("Batch simulation must be strictly deterministic across identical seeds", d0First, d0Second)
    assertEquals(24, d0First.runsAttempted)
    assertEquals(24, d0First.runsCompleted)
    assertEquals(0, d0First.incompleteRuns)
    assertEquals(0, d0First.turnCapHits)
    assertEquals(0, d0First.nodeCapHits)

    val d2 = SimulationRunner.runBatchSimulation(seedsPerClass = 4, dreadLevel = 2)
    assertEquals(12, d2.runsAttempted)
    assertEquals(12, d2.runsCompleted)
    assertEquals(0, d2.incompleteRuns)

    println("SIM_REPORT_D0: attempted=${d0First.runsAttempted} completed=${d0First.runsCompleted} incomplete=${d0First.incompleteRuns} wins=${d0First.wins} hpDefeats=${d0First.hpDefeats} zeroFlask=${d0First.zeroFlaskCollapses} avgTurns=${"%.2f".format(d0First.avgTurnsPerRun)} avgAct=${"%.2f".format(d0First.avgActReached)}")
    d0First.perClassBreakdown.forEach { (hero, s) ->
      println("SIM_CLASS_D0[$hero]: completed=${s.runsCompleted}/${s.runsAttempted} wins=${s.wins} hpDefeats=${s.hpDefeats} zeroFlask=${s.zeroFlaskCollapses} avgTurns=${"%.2f".format(s.avgTurnsPerRun)} avgAct=${"%.2f".format(s.avgActReached)}")
    }
    println("SIM_REPORT_D2: attempted=${d2.runsAttempted} completed=${d2.runsCompleted} incomplete=${d2.incompleteRuns} wins=${d2.wins} hpDefeats=${d2.hpDefeats} zeroFlask=${d2.zeroFlaskCollapses} avgTurns=${"%.2f".format(d2.avgTurnsPerRun)} avgAct=${"%.2f".format(d2.avgActReached)}")
  }
}





