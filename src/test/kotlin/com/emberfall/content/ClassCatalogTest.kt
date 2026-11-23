package com.emberfall.content

import com.emberfall.board.*
import com.emberfall.cards.*
import com.emberfall.combat.*
import kotlin.test.*

class ClassCatalogTest {
    @Test fun everyClassHasExactlyTwelveResolvableStarterCards() {
        assertEquals(3, PlayerClassCatalog.classes.size)
        PlayerClassCatalog.classes.values.forEach { definition ->
            assertEquals(12, definition.starterDeck.size)
            if (definition.id == PlayerClassId.THE_UNLIT) {
                assertTrue(definition.starterDeck.all { ClassCardCatalog.cards.getValue(it).frameHue == Hue.GOLD })
            } else {
                assertEquals(4, definition.starterDeck.count { ClassCardCatalog.cards.getValue(it).frameHue == definition.firstHue })
                assertEquals(4, definition.starterDeck.count { ClassCardCatalog.cards.getValue(it).frameHue == definition.secondHue })
            }
            assertEquals(2, definition.starterDeck.count { it.endsWith("pop_shot") })
            assertTrue(definition.starterDeck.all { it in ClassCardCatalog.cards })
            val battle = CardBattleState.start(
                CombatState.start(BoardState.empty(), EnemyDefinition("test", 50, listOf(EnemyIntent.Strike(1)))),
                definition.starterDeck, ClassCardCatalog.cards, 99L
            )
            assertEquals(12, battle.drawPile.size)
        }
    }

    @Test fun unlitDeckMatchesLockedAdr014MultisetAndKeepsCardRelicDistinct() {
        val deck = PlayerClassCatalog.starterDeck(PlayerClassId.THE_UNLIT)
        assertEquals(4, deck.count { it == "unlit_gold_strike" })
        assertEquals(4, deck.count { it == "unlit_ash_ward" })
        assertEquals(2, deck.count { it == "unlit_pop_shot" })
        assertEquals(1, deck.count { it == "unlit_sift" })
        assertEquals(1, deck.count { it == "unlit_hollow_crown" })
        assertEquals(Hue.GOLD, deck.map { ClassCardCatalog.cards.getValue(it).frameHue }.distinct().single())
        val relic = PlayerClassCatalog.classes.getValue(PlayerClassId.THE_UNLIT).signatureRelic
        assertEquals("relic_hollow_crown", relic.id)
        assertNotEquals("unlit_hollow_crown", relic.id)
        assertEquals(SignatureRelicRule.SPARK_PER_ASH_BLOCK, relic.rule)
    }

    @Test fun classSignaturesPreserveTheirDistinctMechanicalIdentities() {
        val monk = PlayerClassCatalog.classes.getValue(PlayerClassId.ASHEN_MONK)
        val witch = PlayerClassCatalog.classes.getValue(PlayerClassId.QUARRY_WITCH)
        val unlit = PlayerClassCatalog.classes.getValue(PlayerClassId.THE_UNLIT)
        assertEquals(SignatureRelicRule.FIRST_TAP_BONUS_INK, monk.signatureRelic.rule)
        assertEquals(2, monk.signatureRelic.magnitude)
        assertEquals(SignatureRelicRule.FREE_RECOLOR_ONCE_PER_FIGHT, witch.signatureRelic.rule)
        assertEquals(SignatureRelicRule.SPARK_PER_ASH_BLOCK, unlit.signatureRelic.rule)
        assertEquals(Hue.EMBER to Hue.MOSS, monk.firstHue to monk.secondHue)
        assertEquals(Hue.TIDE to Hue.VIOLET, witch.firstHue to witch.secondHue)
        assertEquals(Hue.GOLD, unlit.firstHue)
    }

    @Test fun siftIsDeterministicPreservesSpectrumAndDrawsOneCard() {
        val rows = List(7) { y -> List(7) { x -> Cell.hue(Hue.entries[(x + y) % Hue.entries.size]) } }
        val board = BoardState.fromRows(rows, List(7) { Cell.ash() })
        val enemy = EnemyDefinition("sifter_test", 80, listOf(EnemyIntent.Strike(1)))
        val combat = CombatState.start(board, enemy, SpectrumState(3, Hue.MOSS), 77L)
            .copy(phase = CombatPhase.CAST, player = PlayerState(spark = 1))
        val initial = CardBattleState.start(combat, listOf("monk_sift", "ember_strike"), ClassCardCatalog.cards, 123L)
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "monk_sift")),
                discardPile = listOf(CardInstance(1, "ember_strike")), nextCardInstanceId = 2)
        val engine = CardEngine(ClassCardCatalog.cards)
        val first = assertIs<CardBattleResult.Accepted>(engine.transition(initial, CardBattleCommand.Play(0))).state
        val second = assertIs<CardBattleResult.Accepted>(engine.transition(initial, CardBattleCommand.Play(0))).state
        assertEquals(first.combat.board, second.combat.board)
        assertNotEquals(initial.combat.board, first.combat.board)
        assertEquals(initial.combat.spectrum, first.combat.spectrum)
        assertEquals(1, first.hand.size)
        assertEquals(1, first.drawPile.size)
        assertTrue(first.hand.single().instanceId in setOf(0L, 1L))
        assertEquals(0, first.combat.player.spark)
    }

    @Test fun sacrificeRiteCannotKillItsCasterAndRollsBackTheWholePlay() {
        val enemy = EnemyDefinition("rite_test", 80, listOf(EnemyIntent.Strike(1)))
        val combat = CombatState.start(BoardState.empty(), enemy, runSeed = 12L)
            .copy(phase = CombatPhase.CAST, player = PlayerState(vigil = 6, ink = mapOf(Hue.MOSS to 2)))
        val initial = CardBattleState.start(combat, listOf("monk_sacrifice_rite"), ClassCardCatalog.cards, 12L)
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "monk_sacrifice_rite")))
        val result = CardEngine(ClassCardCatalog.cards).transition(initial, CardBattleCommand.Play(0))
        assertEquals(CardRejection.EFFECT_FAILED, assertIs<CardBattleResult.Rejected>(result).reason)
        assertEquals(6, initial.combat.player.vigil)
        assertEquals(2, initial.combat.player.immutableInk.getValue(Hue.MOSS))
        assertEquals(1, initial.hand.size)
    }
    @Test fun prayerBeadsAddsTwoInkToOnlyTheFirstSuccessfulTapEachTurn() {
        val board = BoardState.empty()
            .withCell(Coord(0, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(1, 6), Cell.hue(Hue.EMBER))
            .withCell(Coord(3, 6), Cell.hue(Hue.GOLD))
            .withCell(Coord(4, 6), Cell.hue(Hue.GOLD))
        val combat = CombatState.start(board, EnemyDefinition("beads_test", 80, listOf(EnemyIntent.Strike(1))))
            .copy(phase = CombatPhase.WEAVE)
        val state = PlayerClassCatalog.startBattle(combat, PlayerClassId.ASHEN_MONK, 5L)
        val engine = CardEngine(ClassCardCatalog.cards)
        val first = assertIs<CardBattleResult.Accepted>(engine.transition(state, CardBattleCommand.Tap(Coord(0, 6)))).state
        assertEquals(4, first.combat.player.immutableInk.getValue(Hue.EMBER))
        assertTrue(first.prayerBeadsUsedThisTurn)
        val second = assertIs<CardBattleResult.Accepted>(engine.transition(first, CardBattleCommand.Tap(Coord(3, 6)))).state
        assertEquals(3, second.combat.player.immutableInk.getValue(Hue.GOLD))
        val nextTurn = assertIs<CardBattleResult.Accepted>(engine.transition(
            second.copy(combat = second.combat.copy(phase = CombatPhase.DRAW)),
            CardBattleCommand.BeginTurn
        )).state
        assertFalse(nextTurn.prayerBeadsUsedThisTurn)
    }

    @Test fun chiselWandRecolorsOneOpenHueBlockOncePerFightWithoutPayment() {
        val board = BoardState.empty().withCell(Coord(2, 3), Cell.hue(Hue.EMBER, branded = true))
        val combat = CombatState.start(board, EnemyDefinition("chisel_test", 80, listOf(EnemyIntent.Strike(1))))
            .copy(phase = CombatPhase.WEAVE)
        val state = PlayerClassCatalog.startBattle(combat, PlayerClassId.QUARRY_WITCH, 6L)
        val engine = CardEngine(ClassCardCatalog.cards)
        val result = assertIs<CardBattleResult.Accepted>(engine.transition(
            state, CardBattleCommand.UseSignatureRecolor(Coord(2, 3), Hue.TIDE)
        )).state
        assertEquals(Hue.TIDE, result.combat.board[Coord(2, 3)]?.hue)
        assertTrue(result.combat.board[Coord(2, 3)]?.branded == true)
        assertTrue("relic_chisel_wand" in result.usedRelicIds)
        assertEquals(CardRejection.WRONG_PHASE, assertIs<CardBattleResult.Rejected>(
            engine.transition(result, CardBattleCommand.UseSignatureRecolor(Coord(2, 3), Hue.VIOLET))
        ).reason)
    }

    @Test fun hollowCrownPaysSparkForEveryAshCellPoppedByACard() {
        val board = BoardState.empty()
            .withCell(Coord(0, 6), Cell.ash())
            .withCell(Coord(1, 6), Cell.ash())
        val combat = CombatState.start(board, EnemyDefinition("crown_test", 80, listOf(EnemyIntent.Strike(1))))
            .copy(phase = CombatPhase.CAST, player = PlayerState(spark = 1))
        val state = PlayerClassCatalog.startBattle(combat, PlayerClassId.THE_UNLIT, 7L)
            .copy(drawPile = emptyList(), hand = listOf(CardInstance(0, "unlit_pop_shot")))
        val result = assertIs<CardBattleResult.Accepted>(
            CardEngine(ClassCardCatalog.cards).transition(state, CardBattleCommand.Play(0))
        ).state
        assertEquals(2, result.combat.player.spark)
        assertNull(result.combat.board[Coord(0, 6)])
        assertNull(result.combat.board[Coord(1, 6)])
    }

}
