package com.emberfall.content

import com.emberfall.board.Coord
import com.emberfall.board.Hue
import com.emberfall.combat.*

enum class EncounterTier { TUTORIAL, REGULAR, ELITE, BOSS }

data class EncounterDefinition(
    val id: String,
    val displayName: String,
    val act: Int,
    val tier: EncounterTier,
    val enemy: EnemyDefinition,
    val counterplay: String,
    val teaches: Set<String> = emptySet(),
    val cardsEnabled: Boolean = true,
    val inkEnabled: Boolean = true
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank())
        require(act in 1..3)
        require(enemy.id == id)
        require(counterplay.isNotBlank())
        require(cardsEnabled || !inkEnabled) { "Ink cannot be enabled while cards are disabled" }
    }

    fun forecast(cursor: Long = 0): List<EnemyIntent> =
        listOf(enemy.intentAt(cursor), enemy.intentAt(cursor + 1))
}

/** Original first-slice encounters. Every enemy exposes two deterministic upcoming intents. */
object EncounterCatalog {
    val encounters: Map<String, EncounterDefinition> = listOf(
        EncounterDefinition(
            id = "glassling", displayName = "Glassling", act = 1, tier = EncounterTier.TUTORIAL,
            enemy = EnemyDefinition("glassling", 18, listOf(
                EnemyIntent.AshRow, EnemyIntent.StoneSeed(3), EnemyIntent.AshRow, EnemyIntent.Stain(Hue.EMBER)
            )),
            counterplay = "Pop paired groups to learn chip damage and vertical gravity; clear space before the Wall rises.",
            teaches = setOf("group_pop", "chip_damage", "vertical_gravity", "wall_pressure"),
            cardsEnabled = false, inkEnabled = false
        ),
        EncounterDefinition(
            id = "cinderling", displayName = "Cinderling", act = 1, tier = EncounterTier.REGULAR,
            enemy = EnemyDefinition("cinderling", 34, listOf(
                EnemyIntent.Strike(5), EnemyIntent.AshRow, EnemyIntent.Strike(7), EnemyIntent.Stain(Hue.EMBER)
            )),
            counterplay = "Block or kill before the second Strike; Ash groups can be cleared for chip damage but pay no Ink."
        ),
        EncounterDefinition(
            id = "glass_moth", displayName = "Glass Moth", act = 1, tier = EncounterTier.REGULAR,
            enemy = EnemyDefinition("glass_moth", 30, listOf(
                EnemyIntent.Fog, EnemyIntent.Stain(Hue.TIDE), EnemyIntent.Strike(4), EnemyIntent.Surge
            )),
            counterplay = "Read glyphs through Fog, clear Tide before Stain and reduce board density before Surge."
        ),
        EncounterDefinition(
            id = "quarry_husk", displayName = "Quarry Husk", act = 1, tier = EncounterTier.REGULAR,
            enemy = EnemyDefinition("quarry_husk", 42, listOf(
                EnemyIntent.StoneSeed(3), EnemyIntent.Strike(8), EnemyIntent.Seal(2), EnemyIntent.Strike(6)
            )),
            counterplay = "Build groups of four beside Stone, route around the telegraphed Seal and retain a safe column."
        ),
        EncounterDefinition(
            id = "hollow_warden", displayName = "The Hollow Warden", act = 1, tier = EncounterTier.BOSS,
            enemy = EnemyDefinition("hollow_warden", 180, listOf(
                EnemyIntent.StoneSeed(0), EnemyIntent.Strike(8),
                EnemyIntent.StoneSeed(2), EnemyIntent.Strike(10),
                EnemyIntent.StoneSeed(4), EnemyIntent.Strike(12)
            )),
            counterplay = "Break Stone with adjacent groups of four or more and preserve an open route through the board.",
            teaches = setOf("stone_break", "boss_pressure", "route_planning")
        )
    ).associateBy { it.id }

    init {
        require(encounters.size == 5)
        require(encounters.values.map { it.enemy.id }.distinct().size == encounters.size)
        require(encounters.values.all { it.forecast().size == 2 })
    }

    fun get(id: String): EncounterDefinition = encounters.getValue(id)
}
