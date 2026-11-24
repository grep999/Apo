package com.emberfall.content

import com.emberfall.board.*
import com.emberfall.combat.*
import kotlin.test.*

class EncounterCatalogTest {
    @Test fun tutorialEncounterIsBoardOnlyAndTeachesTheOpeningLoop() {
        val encounter = EncounterCatalog.get("glassling")
        assertEquals(EncounterTier.TUTORIAL, encounter.tier)
        assertFalse(encounter.cardsEnabled)
        assertFalse(encounter.inkEnabled)
        assertEquals(setOf("group_pop", "chip_damage", "vertical_gravity", "wall_pressure"), encounter.teaches)
        assertTrue(encounter.enemy.intentSequence.none { it is EnemyIntent.Strike })
        assertEquals(listOf(EnemyIntent.AshRow, EnemyIntent.StoneSeed(3)), encounter.forecast())
    }

    @Test fun everyEncounterHasStableIdentityAndTwoIntentForecast() {
        assertEquals(5, EncounterCatalog.encounters.size)
        EncounterCatalog.encounters.values.forEach { encounter ->
            assertEquals(encounter.id, encounter.enemy.id)
            assertEquals(2, encounter.forecast().size)
            val combat = CombatState.start(BoardState.empty(), encounter.enemy, runSeed = 2026L)
            assertEquals(encounter.forecast(), combat.nextIntents)
        }
    }

    @Test fun encounterIntentSequencesLoopDeterministically() {
        val encounter = EncounterCatalog.get("cinderling")
        val sequence = encounter.enemy.intentSequence
        sequence.indices.forEach { index ->
            assertEquals(sequence[index], encounter.enemy.intentAt(index.toLong()))
            assertEquals(sequence[index], encounter.enemy.intentAt((index + sequence.size).toLong()))
        }
    }

    @Test fun actOneBossUsesStonePressureAndTelegraphedStrikes() {
        val boss = EncounterCatalog.get("hollow_warden")
        assertEquals(EncounterTier.BOSS, boss.tier)
        assertEquals(180, boss.enemy.maxVigor)
        assertTrue(boss.enemy.intentSequence.any { it is EnemyIntent.StoneSeed })
        assertTrue(boss.enemy.intentSequence.any { it is EnemyIntent.Strike })
        assertEquals(
            listOf(EnemyIntent.StoneSeed(0), EnemyIntent.Strike(8)),
            boss.forecast()
        )
    }
}
