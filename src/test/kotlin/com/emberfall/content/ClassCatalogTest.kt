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
        assertEquals(listOf("ember_strike"), first.hand.map { it.definitionId })
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
}
