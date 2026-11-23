package com.emberfall.content

import com.emberfall.board.Cell
import com.emberfall.board.Hue
import com.emberfall.cards.*
import com.emberfall.combat.CombatState

enum class PlayerClassId { ASHEN_MONK, QUARRY_WITCH, THE_UNLIT }
enum class SignatureRelicRule { FIRST_TAP_BONUS_INK, FREE_RECOLOR_ONCE_PER_FIGHT, SPARK_PER_ASH_BLOCK }

data class SignatureRelicDefinition(
    val id: String,
    val name: String,
    val rule: SignatureRelicRule,
    val magnitude: Int = 1
) {
    init { require(id.isNotBlank() && name.isNotBlank() && magnitude > 0) }
}

data class PlayerClassDefinition(
    val id: PlayerClassId,
    val displayName: String,
    val firstHue: Hue,
    val secondHue: Hue,
    val fantasy: String,
    val signatureRelic: SignatureRelicDefinition,
    val starterDeck: List<String>
) {
    init {
        require(displayName.isNotBlank() && fantasy.isNotBlank())
        require(starterDeck.size == 12)
        require(starterDeck.count { it.endsWith("pop_shot") } == 2)
        require(starterDeck.count { it.endsWith("_sift") || it == "monk_sift" } == 1)
    }
}

object ClassCardCatalog {
    private fun card(
        id: String, name: String, hue: Hue, type: CardType, cost: CardCost,
        vararg effects: CardEffect, rarity: CardRarity = CardRarity.BASIC, exhaust: Boolean = false
    ) = CardDefinition(id, name, hue, type, rarity, cost, effects.toList(), exhaust = exhaust)

    val cards: Map<String, CardDefinition> = (
        StarterCardCatalog.cards.values + listOf(
            card("monk_sift", "Sift", Hue.GOLD, CardType.SKILL, CardCost(generic = 1),
                CardEffect.Sift, rarity = CardRarity.COMMON),
            card("monk_sacrifice_rite", "Sacrifice Rite", Hue.GOLD, CardType.SKILL, CardCost(mapOf(Hue.MOSS to 1)),
                CardEffect.LoseVigil(6), CardEffect.Damage(12), CardEffect.Draw(2),
                rarity = CardRarity.RARE, exhaust = true),

            card("quarry_tide_strike", "Tide Strike", Hue.TIDE, CardType.ATTACK, CardCost(mapOf(Hue.TIDE to 1)),
                CardEffect.Damage(6)),
            card("quarry_violet_ward", "Violet Ward", Hue.VIOLET, CardType.SKILL, CardCost(mapOf(Hue.VIOLET to 1)),
                CardEffect.Block(5)),
            card("quarry_sift", "Sift", Hue.GOLD, CardType.SKILL, CardCost(generic = 1),
                CardEffect.Sift, rarity = CardRarity.COMMON),
            card("quarry_prism_seed", "Prism Seed", Hue.GOLD, CardType.SKILL, CardCost(mapOf(Hue.VIOLET to 1)),
                CardEffect.SpawnBeside(Cell.prism()), CardEffect.Draw(1), rarity = CardRarity.RARE),

            card("unlit_gold_strike", "Gold Strike", Hue.GOLD, CardType.ATTACK, CardCost(mapOf(Hue.GOLD to 1)),
                CardEffect.Damage(6)),
            card("unlit_ash_ward", "Ash Ward", Hue.GOLD, CardType.SKILL, CardCost(mapOf(Hue.GOLD to 1)),
                CardEffect.SpawnBeside(Cell.ash()), CardEffect.Block(3), CardEffect.GainSpark(1)),
            card("unlit_pop_shot", "Pop Shot", Hue.GOLD, CardType.ATTACK, CardCost(generic = 1),
                CardEffect.Pop(largest = true), CardEffect.Damage(3), rarity = CardRarity.COMMON),
            card("unlit_sift", "Sift", Hue.GOLD, CardType.SKILL, CardCost(generic = 1),
                CardEffect.Sift, rarity = CardRarity.COMMON),
            card("unlit_hollow_crown", "Hollow Crown", Hue.GOLD, CardType.SKILL, CardCost(mapOf(Hue.GOLD to 1)),
                CardEffect.GainSpark(2), CardEffect.Block(2), rarity = CardRarity.RARE, exhaust = true)
        )
    ).associateBy { it.id }

    init { require(cards.size == StarterCardCatalog.cards.size + 11) }

    fun deck(classId: PlayerClassId): List<String> = PlayerClassCatalog.classes.getValue(classId).starterDeck.toList()
}

object PlayerClassCatalog {
    val classes: Map<PlayerClassId, PlayerClassDefinition> = listOf(
        PlayerClassDefinition(
            PlayerClassId.ASHEN_MONK, "Ashen Monk", Hue.EMBER, Hue.MOSS,
            "Burns Vigil to turn a dangerous opening into a decisive strike.",
            SignatureRelicDefinition("relic_prayer_beads", "Prayer Beads", SignatureRelicRule.FIRST_TAP_BONUS_INK, 2),
            listOf("ember_strike", "ember_strike", "ember_strike", "ember_strike",
                "moss_ward", "moss_ward", "moss_ward", "moss_ward",
                "pop_shot", "pop_shot", "monk_sift", "monk_sacrifice_rite")
        ),
        PlayerClassDefinition(
            PlayerClassId.QUARRY_WITCH, "Quarry Witch", Hue.TIDE, Hue.VIOLET,
            "Rewrites the board, seeds Prism and chains the Spectrum wheel.",
            SignatureRelicDefinition("relic_chisel_wand", "Chisel Wand", SignatureRelicRule.FREE_RECOLOR_ONCE_PER_FIGHT, 1),
            listOf("quarry_tide_strike", "quarry_tide_strike", "quarry_tide_strike", "quarry_tide_strike",
                "quarry_violet_ward", "quarry_violet_ward", "quarry_violet_ward", "quarry_violet_ward",
                "pop_shot", "pop_shot", "quarry_sift", "quarry_prism_seed")
        ),
        PlayerClassDefinition(
            PlayerClassId.THE_UNLIT, "The Unlit", Hue.GOLD, Hue.GOLD,
            "Turns enemy Ash into Spark and keeps a thin, fast hand moving.",
            SignatureRelicDefinition("relic_hollow_crown", "Hollow Crown", SignatureRelicRule.SPARK_PER_ASH_BLOCK, 1),
            listOf("unlit_gold_strike", "unlit_gold_strike", "unlit_gold_strike", "unlit_gold_strike",
                "unlit_ash_ward", "unlit_ash_ward", "unlit_ash_ward", "unlit_ash_ward",
                "unlit_pop_shot", "unlit_pop_shot", "unlit_sift", "unlit_hollow_crown")
        )
    ).associateBy { it.id }

    init {
        require(classes.size == PlayerClassId.entries.size)
        classes.values.forEach { definition ->
            require(definition.starterDeck.all { it in ClassCardCatalog.cards }) {
                "Unknown card in ${definition.displayName} starter deck"
            }
        }
    }

    fun starterDeck(id: PlayerClassId): List<String> = classes.getValue(id).starterDeck.toList()

    fun startBattle(combat: CombatState, id: PlayerClassId, seed: Long = combat.runSeed): CardBattleState {
        val profile = classes.getValue(id)
        return CardBattleState.start(combat, profile.starterDeck, ClassCardCatalog.cards, seed,
            equippedRelicIds = setOf(profile.signatureRelic.id))
    }
}
