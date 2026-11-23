package com.emberfall.cards

import com.emberfall.board.*
import com.emberfall.combat.*
import kotlin.test.*

class CardCoreTest {
    private val catalog = StarterCardCatalog.cards
    private val engine = CardEngine(catalog)

    private fun combat(board: BoardState = BoardState.empty(), ink: Map<Hue, Int> = Hue.entries.associateWith { 8 }, spark: Int = 8) =
        CombatState.start(board, EnemyDefinition("dummy", 100, listOf(EnemyIntent.Strike(1), EnemyIntent.AshRow)), runSeed = 77)
            .copy(player = PlayerState(ink = ink, spark = spark))

    private fun battle(vararg ids: String, board: BoardState = BoardState.empty()): CardBattleState {
        val started = CardBattleState.start(combat(board), ids.toList(), catalog, 77)
        return started.copy(drawPile = emptyList(), hand = started.drawPile)
    }

    private fun cast(state: CardBattleState): CardBattleState {
        var next = (engine.transition(state, CardBattleCommand.BeginTurn) as CardBattleResult.Accepted).state
        next = (engine.transition(next, CardBattleCommand.EndWeave) as CardBattleResult.Accepted).state
        return next
    }

    private fun accepted(result: CardBattleResult) = assertIs<CardBattleResult.Accepted>(result)

    @Test fun starterCatalogUsesTypedDataAndUniqueStableIds() {
        assertEquals(catalog.size, catalog.keys.distinct().size)
        assertEquals(Hue.EMBER, catalog.getValue("ember_strike").frameHue)
        assertEquals(Hue.GOLD, catalog.getValue("ash_ward").frameHue)
        assertEquals(4, CardType.entries.size)
    }

    @Test fun drawFiveIsDeterministicAndEntersWeave() {
        val deck = List(12) { if (it % 2 == 0) "ember_strike" else "moss_ward" }
        val a = CardBattleState.start(combat(), deck, catalog, 1234)
        val b = CardBattleState.start(combat(), deck, catalog, 1234)
        assertEquals(a.drawPile, b.drawPile)
        val result = accepted(engine.transition(a, CardBattleCommand.BeginTurn))
        assertEquals(5, result.state.hand.size)
        assertEquals(7, result.state.drawPile.size)
        assertEquals(CombatPhase.WEAVE, result.state.combat.phase)
        assertEquals(5, result.events.filterIsInstance<CardEvent.Drew>().size)
    }

    @Test fun paymentUsesColoredInkForColoredPipsAndSparkFirstForGeneric() {
        val player = PlayerState(ink = mapOf(Hue.EMBER to 2, Hue.GOLD to 1, Hue.MOSS to 4), spark = 3)
        val plan = CardPayment.automatic(CardCost(mapOf(Hue.EMBER to 1), generic = 3), player)!!
        assertEquals(1, plan.coloredInk.getValue(Hue.EMBER))
        assertEquals(3, plan.sparkForGeneric)
        assertTrue(plan.genericInk.isEmpty())
        val paid = CardPayment.apply(player, plan)
        assertEquals(1, paid.immutableInk.getValue(Hue.EMBER))
        assertEquals(0, paid.spark)
    }

    @Test fun genericPaymentUsesLeastAbundantInkAndWheelTieBreak() {
        val player = PlayerState(ink = mapOf(Hue.EMBER to 2, Hue.GOLD to 2, Hue.MOSS to 4, Hue.TIDE to 4, Hue.VIOLET to 4), spark = 0)
        val plan = CardPayment.automatic(CardCost(generic = 3), player)!!
        assertEquals(2, plan.genericInk.getValue(Hue.EMBER))
        assertEquals(1, plan.genericInk.getValue(Hue.GOLD))
    }

    @Test fun coloredCostCannotBePaidBySpark() {
        val player = PlayerState(ink = emptyMap(), spark = 99)
        assertNull(CardPayment.automatic(CardCost(mapOf(Hue.TIDE to 1)), player))
    }

    @Test fun unaffordablePlayDoesNotChangeCombatOrCardZones() {
        val state = battle("ember_strike").copy(combat = combat(ink = emptyMap(), spark = 0))
            .copy(combat = combat(ink = emptyMap(), spark = 0), hand = battle("ember_strike").hand)
        val ready = cast(state)
        val before = ready.combat.player.immutableInk
        val id = ready.hand.single().instanceId
        val result = engine.transition(ready, CardBattleCommand.Play(id))
        assertEquals(CardRejection.UNAFFORDABLE, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(before, ready.combat.player.immutableInk)
        assertEquals(1, ready.hand.size)
    }

    @Test fun emberStrikePaysAndDealsSixDamage() {
        val ready = cast(battle("ember_strike"))
        val id = ready.hand.single().instanceId
        val result = accepted(engine.transition(ready, CardBattleCommand.Play(id)))
        assertEquals(94, result.state.combat.enemy.vigor)
        assertEquals(7, result.state.combat.player.immutableInk.getValue(Hue.EMBER))
        assertTrue(result.events.any { it is CardEvent.Played })
    }

    @Test fun popShotUsesLargestLegalGroupWithoutSpendingWeaveTap() {
        val board = BoardState.empty()
            .withCell(Coord(0, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(1, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(2, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(3, 6), Cell.hue(Hue.GOLD))
            .withCell(Coord(4, 6), Cell.hue(Hue.GOLD))
        val ready = cast(battle("pop_shot", board = board))
        val beforeTaps = ready.combat.tapsRemaining
        val result = accepted(engine.transition(ready, CardBattleCommand.Play(ready.hand.single().instanceId)))
        assertEquals(beforeTaps, result.state.combat.tapsRemaining)
        assertNull(result.state.combat.board[Coord(0, 6)])
        assertEquals(95, result.state.combat.enemy.vigor) // 2 chip + 3 card damage
    }

    @Test fun echoRepeatsOnlyPreviousTopLevelCardOnceAndExhaustsEchoChime() {
        val ready = cast(battle("ember_strike", "echo_chime"))
        val strike = ready.hand.single { it.definitionId == "ember_strike" }
        val echo = ready.hand.single { it.definitionId == "echo_chime" }
        val afterStrike = accepted(engine.transition(ready, CardBattleCommand.Play(strike.instanceId))).state
        assertEquals(94, afterStrike.combat.enemy.vigor)
        val afterEcho = accepted(engine.transition(afterStrike, CardBattleCommand.Play(echo.instanceId))).state
        assertEquals(88, afterEcho.combat.enemy.vigor)
        assertTrue(afterEcho.exhaustPile.any { it.instanceId == echo.instanceId })
        assertTrue(afterEcho.lastCardEchoed)
    }

    @Test fun echoWithoutPriorTopLevelCardRollsBackPaymentAndZones() {
        val ready = cast(battle("echo_chime"))
        val card = ready.hand.single()
        val result = engine.transition(ready, CardBattleCommand.Play(card.instanceId))
        assertEquals(CardRejection.EFFECT_FAILED, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(ready.combat.player.immutableInk, (ready.combat.player.copy()).immutableInk)
        assertEquals(8, ready.combat.player.spark)
        assertEquals(1, ready.hand.size)
        assertEquals(0, ready.exhaustPile.size)
    }

    @Test fun effectQueueOverflowRejectsWholeCardAction() {
        val huge = CardDefinition("huge", "Huge", Hue.EMBER, CardType.SKILL, CardRarity.RARE,
            CardCost(), listOf(CardEffect.Nested(List(129) { CardEffect.GainSpark(1) })))
        val local = CardEngine(mapOf(huge.id to huge))
        val state = CardBattleState.start(combat(), listOf("huge"), mapOf("huge" to huge))
        val ready = state.copy(drawPile = emptyList(), hand = state.drawPile, combat = state.combat.copy(phase = CombatPhase.CAST))
        val result = local.transition(ready, CardBattleCommand.Play(ready.hand.single().instanceId))
        assertEquals(CardRejection.EFFECT_LIMIT, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(8, ready.combat.player.spark)
        assertEquals(1, ready.hand.size)
    }

    @Test fun effectDepthLimitRejectsNestedRecursion() {
        var nested: CardEffect = CardEffect.GainSpark(1)
        repeat(17) { nested = CardEffect.Nested(listOf(nested)) }
        val deep = CardDefinition("deep", "Deep", Hue.VIOLET, CardType.SKILL, CardRarity.RARE, CardCost(), listOf(nested))
        val local = CardEngine(mapOf(deep.id to deep))
        val state = CardBattleState.start(combat(), listOf("deep"), mapOf("deep" to deep))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "deep")), combat = combat().copy(phase = CombatPhase.CAST))
        val result = local.transition(state, CardBattleCommand.Play(0))
        assertEquals(CardRejection.EFFECT_LIMIT, assertIs<CardBattleResult.Rejected>(result).reason)
    }

    @Test fun recolorRespectsSealedColumns() {
        val board = BoardState.empty()
            .withCell(Coord(2, 3), Cell.hue(Hue.EMBER))
            .withCell(Coord(3, 3), Cell.hue(Hue.EMBER))
            .sealColumn(3, 3)
        val definition = CardDefinition("recolor", "Recolor", Hue.VIOLET, CardType.SKILL, CardRarity.COMMON,
            CardCost(), listOf(CardEffect.Recolor3x3(Hue.TIDE)))
        val local = CardEngine(mapOf(definition.id to definition))
        val state = CardBattleState.start(combat(board), listOf("recolor"), mapOf("recolor" to definition))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "recolor")), combat = combat(board).copy(phase = CombatPhase.CAST))
        val result = accepted(local.transition(state, CardBattleCommand.Play(0, target = Coord(2, 3))))
        assertEquals(Hue.TIDE, result.state.combat.board[Coord(2, 3)]?.hue)
        assertEquals(Hue.EMBER, result.state.combat.board[Coord(3, 3)]?.hue)
    }

    @Test fun shatterColumnLeavesSealedColumnsUntouchedAndSettlesGravity() {
        val board = BoardState.empty()
            .withCell(Coord(2, 2), Cell.hue(Hue.EMBER))
            .withCell(Coord(2, 6), Cell.hue(Hue.GOLD))
            .withCell(Coord(3, 6), Cell.hue(Hue.MOSS))
            .sealColumn(3, 3)
        val card = CardDefinition("shatter", "Shatter", Hue.EMBER, CardType.ATTACK, CardRarity.COMMON,
            CardCost(), listOf(CardEffect.ShatterColumn(2)))
        val local = CardEngine(mapOf(card.id to card))
        val state = CardBattleState.start(combat(board), listOf("shatter"), mapOf("shatter" to card))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "shatter")), combat = combat(board).copy(phase = CombatPhase.CAST))
        val result = accepted(local.transition(state, CardBattleCommand.Play(0)))
        assertTrue((0..6).all { y -> result.state.combat.board[Coord(2, y)] == null })
        assertEquals(Hue.MOSS, result.state.combat.board[Coord(3, 6)]?.hue)
    }

    @Test fun hearthlightAddsTapBudgetToNextTurnWithoutReopeningWeave() {
        val ready = cast(battle("hearthlight"))
        val after = accepted(engine.transition(ready, CardBattleCommand.Play(ready.hand.single().instanceId))).state
        assertEquals(1, after.bonusTapsNextTurn)
        assertEquals(CombatPhase.CAST, after.combat.phase)
    }

    @Test fun cleanupRetainsOnlyUnplayedRetainCardsAndMovesOtherHandToDiscard() {
        val retain = CardDefinition("retain", "Retain", Hue.TIDE, CardType.SKILL, CardRarity.COMMON,
            CardCost(), listOf(CardEffect.Block(1)), retain = true)
        val normal = CardDefinition("normal", "Normal", Hue.EMBER, CardType.SKILL, CardRarity.COMMON,
            CardCost(), listOf(CardEffect.Block(1)))
        val local = CardEngine(mapOf(retain.id to retain, normal.id to normal))
        val combatState = combat().copy(phase = CombatPhase.CLEANUP)
        val state = CardBattleState.start(combatState, listOf("retain", "normal"), mapOf(retain.id to retain, normal.id to normal))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "retain"), CardInstance(1, "normal")))
        val result = accepted(local.transition(state, CardBattleCommand.FinishCleanup))
        assertEquals(listOf("retain"), result.state.hand.map { it.definitionId })
        assertEquals(listOf("normal"), result.state.discardPile.map { it.definitionId })
        assertEquals(CombatPhase.DRAW, result.state.combat.phase)
    }
    @Test fun spawnBesideUsesStableLowestIndexEmptyNeighbor() {
        val board = BoardState.empty().withCell(Coord(3, 3), Cell.hue(Hue.MOSS))
        val card = CardDefinition("spawn", "Spawn", Hue.MOSS, CardType.SKILL, CardRarity.COMMON,
            CardCost(), listOf(CardEffect.SpawnBeside(Cell.hue(Hue.MOSS))))
        val local = CardEngine(mapOf(card.id to card))
        val state = CardBattleState.start(combat(board), listOf("spawn"), mapOf(card.id to card))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, card.id)), combat = combat(board).copy(phase = CombatPhase.CAST))
        val result = accepted(local.transition(state, CardBattleCommand.Play(0, target = Coord(3, 3))))
        assertEquals(Hue.MOSS, result.state.combat.board[Coord(3, 2)]?.hue)
    }

    @Test fun fuseRecolorsTwoTouchingHueGroupsIntoOneSelectedHue() {
        val board = BoardState.empty()
            .withCell(Coord(2, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(3, 6), Cell.hue(Hue.GOLD))
        val card = CardDefinition("fuse", "Fuse", Hue.VIOLET, CardType.SKILL, CardRarity.UNCOMMON,
            CardCost(), listOf(CardEffect.FuseGroups(Hue.TIDE)))
        val local = CardEngine(mapOf(card.id to card))
        val state = CardBattleState.start(combat(board), listOf("fuse"), mapOf(card.id to card))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, card.id)), combat = combat(board).copy(phase = CombatPhase.CAST))
        val result = accepted(local.transition(state, CardBattleCommand.Play(0, target = Coord(2, 6), secondTarget = Coord(3, 6))))
        assertEquals(Hue.TIDE, result.state.combat.board[Coord(2, 6)]?.hue)
        assertEquals(Hue.TIDE, result.state.combat.board[Coord(3, 6)]?.hue)
    }

    @Test fun invalidFuseTargetRollsBackCardAndPayment() {
        val board = BoardState.empty()
            .withCell(Coord(0, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(6, 0), Cell.hue(Hue.GOLD))
        val card = CardDefinition("fuse", "Fuse", Hue.VIOLET, CardType.SKILL, CardRarity.UNCOMMON,
            CardCost(mapOf(Hue.VIOLET to 1)), listOf(CardEffect.FuseGroups(Hue.TIDE)))
        val local = CardEngine(mapOf(card.id to card))
        val before = combat(board)
        val state = CardBattleState.start(before, listOf("fuse"), mapOf(card.id to card))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, card.id)), combat = before.copy(phase = CombatPhase.CAST))
        val result = local.transition(state, CardBattleCommand.Play(0, target = Coord(0, 6), secondTarget = Coord(6, 0)))
        assertEquals(CardRejection.INVALID_TARGET, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(before.player.immutableInk, state.combat.player.immutableInk)
        assertEquals(1, state.hand.size)
    }

    @Test fun negativeColoredCostIsRejectedAtDefinitionBoundary() {
        assertFailsWith<IllegalArgumentException> {
            CardCost(mapOf(Hue.EMBER to -1))
        }
    }

    @Test fun blockOverflowRejectsCardTransactionWithoutWrapping() {
        val card = CardDefinition("overflow_block", "Overflow Block", Hue.MOSS, CardType.SKILL, CardRarity.RARE,
            CardCost(), listOf(CardEffect.Block(1)))
        val local = CardEngine(mapOf(card.id to card))
        val base = combat().copy(player = PlayerState(block = Int.MAX_VALUE))
        val state = CardBattleState.start(base, listOf(card.id), mapOf(card.id to card))
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, card.id)), combat = base.copy(phase = CombatPhase.CAST))
        val result = local.transition(state, CardBattleCommand.Play(0))
        assertEquals(CardRejection.EFFECT_FAILED, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(Int.MAX_VALUE, state.combat.player.block)
        assertEquals(1, state.hand.size)
    }

}
