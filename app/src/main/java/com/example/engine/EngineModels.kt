package com.example.engine

/**
 * Pure Kotlin canonical domain models for EMBERFALL.
 * Contains zero Android, Compose, or Room dependencies.
 */

enum class Hue(
  val displayName: String,
  val glyph: String,
  val shortTag: String,
) {
  EMBER("Ember", "◆", "EMB"),
  GOLD("Gold", "☀", "GLD"),
  MOSS("Moss", "✦", "MOS"),
  TIDE("Tide", "●", "TID"),
  VIOLET("Violet", "☾", "VLT");

  companion object {
    // ADR-005 ordered Spectrum wheel: Ember -> Gold -> Moss -> Tide -> Violet
    val WHEEL_ORDER: List<Hue> = listOf(EMBER, GOLD, MOSS, TIDE, VIOLET)

    fun nextInWheel(current: Hue): Hue {
      val idx = WHEEL_ORDER.indexOf(current)
      return WHEEL_ORDER[(idx + 1) % WHEEL_ORDER.size]
    }

    /**
     * ADR-004 Prism tie-resolution order: wheel order starting from the hue
     * immediately following [currentRunHue] (or from EMBER when Run is at 0).
     */
    fun wheelOrderedFrom(currentRunHue: Hue?): List<Hue> {
      if (currentRunHue == null) return WHEEL_ORDER
      val startIdx = (WHEEL_ORDER.indexOf(currentRunHue) + 1) % WHEEL_ORDER.size
      return List(WHEEL_ORDER.size) { offset -> WHEEL_ORDER[(startIdx + offset) % WHEEL_ORDER.size] }
    }
  }
}

sealed class TileKind {
  data class Playable(val hue: Hue) : TileKind()
  data object Ash : TileKind()
  data object Stone : TileKind()
  data object Prism : TileKind()
  data object Empty : TileKind()

  val isNeutral: Boolean
    get() = this !is Playable
}

data class BoardCoord(val row: Int, val col: Int)

data class Tile(
  val id: Long,
  val kind: TileKind,
  val isBranded: Boolean = false, // ADR-010: popping Branded blocks costs 2 Vigil/block
)

enum class HeroClass(
  val displayName: String,
  val title: String,
  val maxHp: Int,
  val startingFlasks: Int,
  val maxFlasks: Int,
  val startingCinders: Int,
  val primaryHue: Hue,
  val secondaryHue: Hue?,
  val affinitySummary: String,
  val signatureRelicId: String,
  val description: String,
) {
  ASHEN_MONK(
    displayName = "Ashen Monk",
    title = "Ascetic of the Lit Bead",
    maxHp = 76,
    startingFlasks = 3,
    maxFlasks = 4,
    startingCinders = 80,
    primaryHue = Hue.EMBER,
    secondaryHue = Hue.MOSS,
    affinitySummary = "Ember / Moss",
    signatureRelicId = "relic_prayer_beads",
    description = "Disciplined Ember & Moss ascetic. Prayer Beads grant +2 Ink on the first Tap each turn; burns Vigil for heavy strikes.",
  ),
  QUARRY_WITCH(
    displayName = "Quarry Witch",
    title = "Shaper of Veined Slate",
    maxHp = 68,
    startingFlasks = 4,
    maxFlasks = 5,
    startingCinders = 90,
    primaryHue = Hue.TIDE,
    secondaryHue = Hue.VIOLET,
    affinitySummary = "Tide / Violet",
    signatureRelicId = "relic_chisel_wand",
    description = "Crafty Tide & Violet conjurer. Chisel Wand freely recolours one unsealed tile once per fight.",
  ),
  THE_UNLIT(
    displayName = "The Unlit",
    title = "Bearer of the Hollow Crown",
    maxHp = 64,
    startingFlasks = 3,
    maxFlasks = 4,
    startingCinders = 70,
    primaryHue = Hue.GOLD,
    secondaryHue = null,
    affinitySummary = "Gold / Ash",
    signatureRelicId = "relic_hollow_crown",
    description = "Cursed Gold-frame & Ash vessel. Hollow Crown yields +1 Spark for every Ash block popped.",
  ),
}

enum class CardRarity {
  STARTER,
  COMMON,
  UNCOMMON,
  RARE,
}

enum class CardCategory {
  ATTACK,
  SKILL,
  BOARD,
}

/**
 * ADR-015 Echo: identifies the single primary effect repeated once when echo == true.
 */
enum class PrimaryEffect {
  DAMAGE,
  GUARD,
  VIGIL,
  DRAW,
  PAINT_BOARD,
  SPAWN_PRISM,
  SHATTER_STONE,
  CLEAR_FOG,
}

data class CardDef(
  val id: String,
  val name: String,
  val heroClass: HeroClass?, // null = neutral pool
  val frameHue: Hue?, // ADR-003 board paint deck driver
  val coloredCost: Map<Hue, Int> = emptyMap(), // ADR-009 colored Ink pips
  val genericCost: Int = 0, // ADR-009 generic pips (payable by Spark or chosen Ink)
  val category: CardCategory,
  val rarity: CardRarity,
  val primaryEffect: PrimaryEffect = PrimaryEffect.DAMAGE,
  val damage: Int = 0,
  val blockGain: Int = 0,
  val vigilGain: Int = 0,
  val vigilCost: Int = 0, // Minimum Vigil required to play
  val vigilBurnMax: Int = 0, // Max Vigil points consumed on cast (99 = burn all)
  val damagePerBurnedVigil: Int = 0, // Bonus damage per consumed Vigil point
  val drawCards: Int = 0,
  val gainFlasks: Int = 0,
  val spawnPrisms: Int = 0,
  val shatterStoneCount: Int = 0,
  val paintKind: TileKind? = null,
  val paintCount: Int = 0,
  val sparkGain: Int = 0,
  val inkGainHue: Hue? = null,
  val inkGainAmount: Int = 0,
  val clearsFog: Boolean = false, // ADR-011 Gold Eye
  val unsealColumns: Boolean = false, // ADR-012 Quarry Purge
  val skipWallRise: Boolean = false,
  val echo: Boolean = false, // ADR-015 Echo (repeats primaryEffect once; Echo-of-Echo ignored)
  val exhaustOnPlay: Boolean = false,
  val upgradedDamageBonus: Int = 4,
  val upgradedBlockBonus: Int = 3,
  val upgradedGenericDiscount: Int = 0,
  val description: String,
) {
  val totalPipCost: Int
    get() = coloredCost.values.sum() + genericCost
}

data class CardInstance(
  val uid: Long,
  val defId: String,
  val upgraded: Boolean = false,
)

data class RelicDef(
  val id: String,
  val name: String,
  val summary: String,
  val price: Int,
  val isSignature: Boolean = false,
)

data class RemnantDef(
  val id: String,
  val name: String,
  val summary: String,
)

sealed class EnemyIntent {
  data class Attack(val damage: Int, val hits: Int = 1) : EnemyIntent()
  data class Defend(val block: Int) : EnemyIntent()
  data class CorruptBoard(val kind: TileKind, val count: Int) : EnemyIntent()
  data class SiphonFlask(val damage: Int, val flaskDrain: Int = 1) : EnemyIntent()
  data class InflictBrand(val count: Int, val damage: Int = 0) : EnemyIntent() // ADR-010 marks board blocks
  data class InflictFog(val taps: Int = 3, val damage: Int = 0) : EnemyIntent() // ADR-011 hides colors/Ink for N Taps
  data class SealColumn(val turns: Int = 3, val damage: Int = 0) : EnemyIntent() // ADR-012 locks 1 board column
}

data class EnemyDef(
  val id: String,
  val name: String,
  val act: Int,
  val maxHp: Int,
  val isElite: Boolean = false,
  val isBoss: Boolean = false,
  val isShade: Boolean = false,
  val title: String,
  val slot1Pattern: List<EnemyIntent>,
  val slot2Pattern: List<EnemyIntent>,
)

data class EnemyState(
  val defId: String,
  val name: String,
  val act: Int,
  val hp: Int,
  val maxHp: Int,
  val block: Int,
  val isElite: Boolean,
  val isBoss: Boolean,
  val isShade: Boolean,
  val patternStep: Int,
  val slot1Intent: EnemyIntent,
  val slot2Intent: EnemyIntent,
  val shadeCardIds: List<String> = emptyList(),
  val shadeVigorBonus: Int = 0, // ADR-016 Shade Vigor derived from prior run deck power
)

/**
 * ADR-001 Strict Turn Phase FSM.
 */
enum class TurnPhase {
  DRAW,
  WEAVE,
  CAST,
  WALL,
  ENEMY,
  CLEANUP,
  COMBAT_WON,
  GAME_OVER,
}

enum class GameOverReason(val label: String) {
  HP_DEPLETED("Fallen in Combat"),
  ZERO_FLASKS("Zero-Flask Collapse"),
}

enum class MapNodeType(val label: String) {
  COMBAT("Skirmish"),
  ELITE("Elite"),
  SHADE("The Shade"),
  CAMPFIRE("Campfire"),
  MERCHANT("Merchant"),
  EVENT("Event"),
  TREASURE("Cache"),
  BOSS("Act Boss"),
}

/**
 * Deterministic per-encounter tactical Arena Twist so no two combat rooms feel identical.
 */
enum class ArenaTwist(
  val title: String,
  val shortTag: String,
  val summary: String,
) {
  NONE("Standard Chamber", "STANDARD", "Classic Crucible rules."),
  VOLATILE_PRISMS(
    title = "Volatile Prisms",
    shortTag = "PRISM +1",
    summary = "+1 extra opening Prism • Detonating a Prism deals +6 bonus damage.",
  ),
  BLOOD_KILN(
    title = "Blood Kiln",
    shortTag = "4+ HEAL",
    summary = "4+ matches & cascades restore +3 HP and sear for +4 bonus damage.",
  ),
  SPECTRUM_SURGE(
    title = "Spectrum Surge",
    shortTag = "WHEEL +INK",
    summary = "Advancing the Spectrum wheel grants +1 bonus Ink & +12 extra Overdrive.",
  ),
  BRITTLE_QUARRY(
    title = "Brittle Quarry",
    shortTag = "CRACK STONE",
    summary = "Even 3-matches shatter adjacent Stone (+3 Guard & +5 dmg per Stone).",
  ),
  ECHO_CHAMBER(
    title = "Echo Chamber",
    shortTag = "1ST CARD+",
    summary = "Your first Ink Card cast each turn surges for +5 dmg & +4 Guard.",
  ),
  EMBER_UPDRAFT(
    title = "Ember Updraft",
    shortTag = "EMBER +35%",
    summary = "Ember & Gold matches strike for +35% bonus damage & +10 Overdrive.",
  );

  companion object {
    val ACTIVE_TWISTS: List<ArenaTwist> = listOf(
      VOLATILE_PRISMS,
      BLOOD_KILN,
      SPECTRUM_SURGE,
      BRITTLE_QUARRY,
      ECHO_CHAMBER,
      EMBER_UPDRAFT,
    )

    fun forNode(runSeed: Long, nodeId: String, nodeType: MapNodeType): ArenaTwist {
      if (nodeType != MapNodeType.COMBAT &&
        nodeType != MapNodeType.ELITE &&
        nodeType != MapNodeType.SHADE &&
        nodeType != MapNodeType.BOSS
      ) {
        return NONE
      }
      var h = runSeed xor 0x517CC1B727220A95L
      for (ch in nodeId) {
        h = (h * 31L) xor ch.code.toLong()
      }
      val idx = ((h xor (h ushr 32)) and 0x7FFFFFFFL).toInt() % ACTIVE_TWISTS.size
      return ACTIVE_TWISTS[idx]
    }
  }
}

data class MapNode(
  val id: String,
  val act: Int,
  val row: Int, // 1..15 per ADR-018
  val col: Int,
  val type: MapNodeType,
  val nextNodeIds: List<String>,
  val visited: Boolean = false,
)

data class ActMap(
  val act: Int,
  val nodes: List<MapNode>,
)

data class EventChoiceDef(
  val id: String,
  val label: String,
  val summary: String,
  val hpDelta: Int = 0,
  val maxHpDelta: Int = 0,
  val cindersDelta: Int = 0,
  val flasksDelta: Int = 0,
  val upgradeRandomCard: Boolean = false,
  val addCardDefId: String? = null,
  val removeRandomCard: Boolean = false,
  val grantRandomRelic: Boolean = false,
  val minCindersRequired: Int = 0,
  val minHpRequired: Int = 0,
  val minFlasksRequired: Int = 0,
)

data class EventDef(
  val id: String,
  val title: String,
  val body: String,
  val actMin: Int = 1,
  val actMax: Int = 3,
  val choices: List<EventChoiceDef>,
)

data class EventState(
  val eventId: String,
  val title: String,
  val body: String,
  val choices: List<EventChoiceDef>,
  val resolvedSummary: String? = null,
)

data class BoardPaintDeckState(
  val drawBag: List<TileKind>,
  val discardBag: List<TileKind>,
  val hueWeightsBp: Map<Hue, Int>, // Basis points (10000 = 1.0) derived from ADR-003
)

data class DeckState(
  val drawPile: List<CardInstance>,
  val hand: List<CardInstance>,
  val discardPile: List<CardInstance>,
  val exhaustPile: List<CardInstance>,
)

data class BoardState(
  val rows: Int = 7,
  val cols: Int = 7,
  val grid: List<List<Tile>>,
  val incomingRow: List<Tile>, // ADR-008 incoming/hidden row shown before rise
  val sealedColumns: Map<Int, Int> = emptyMap(), // ADR-012 colIndex -> turnsRemaining
  val nextTileId: Long = 1L,
) {
  fun tileAt(row: Int, col: Int): Tile? =
    grid.getOrNull(row)?.getOrNull(col)

  fun isColumnSealed(col: Int): Boolean = (sealedColumns[col] ?: 0) > 0
}

/**
 * ADR-009: 5 colored Ink pools + uncolored Spark.
 */
data class ResourceState(
  val inkPools: Map<Hue, Int> = Hue.entries.associateWith { 0 },
  val spark: Int = 0,
) {
  fun inkOf(hue: Hue): Int = inkPools[hue] ?: 0
  fun totalInk(): Int = inkPools.values.sum()

  fun addInk(hue: Hue, amount: Int): ResourceState {
    if (amount <= 0) return this
    val updated = inkPools.toMutableMap()
    updated[hue] = ((updated[hue] ?: 0) + amount).coerceAtMost(MAX_INK_PER_HUE)
    return copy(inkPools = updated.toMap())
  }

  fun addSpark(amount: Int): ResourceState {
    if (amount <= 0) return this
    return copy(spark = (spark + amount).coerceAtMost(MAX_SPARK))
  }

  companion object {
    const val MAX_INK_PER_HUE = 12
    const val MAX_SPARK = 12
  }
}

/**
 * ADR-005 ordered Spectrum wheel state.
 */
data class SpectrumState(
  val currentHue: Hue? = null, // null = Run is at 0
  val runLength: Int = 0,
  val halfSteps: Int = 2, // 2 = 1.0x, 3 = 1.5x, 4 = 2.0x ...
)

data class PlayerCombatState(
  val guardBlock: Int = 0,
  val vigil: Int = 0,
  val fogTapsRemaining: Int = 0, // ADR-011
  val chiselWandUsedThisCombat: Boolean = false,
  val firstPrismInkUsedThisCombat: Boolean = false,
  val firstBrandNegatedThisTurn: Boolean = false,
  val spectrumAstrolabeTriggeredThisTurn: Boolean = false,
  val overdriveCharge: Int = 0, // 0..100 Kindle Overdrive ultimate gauge
) {
  companion object {
    const val MAX_VIGIL = 30
    const val MAX_OVERDRIVE = 100
  }
}

data class TurnState(
  val turnNumber: Int,
  val phase: TurnPhase,
  val tapsRemaining: Int,
  val firstTapUsedThisTurn: Boolean = false,
  val skipWallRiseThisTurn: Boolean = false,
  val bonusMovesGrantedThisTurn: Int = 0,
  val comboMultTenths: Int = 10, // 10 = 1.0x baseline; grows ONLY via earned passives/supports/cascades
  val lastMatchedHue: Hue? = null,
  val lastCardHue: Hue? = null,
  val sameHueCardStreak: Int = 0,
  val cascadesThisTurn: Int = 0,
  val overpressureMultTenths: Int = 0, // Banked by Kumbhaka Void keystone
  val movesMadeThisTurn: Int = 0, // Tracks rhythm for Nāda-Ḍamaru drum
) {
  companion object {
    const val TAPS_PER_TURN = 3
    const val MAX_BONUS_MOVES_PER_TURN = 1
    const val BASE_MULT_TENTHS = 10
  }
}

data class GroupResolution(
  val coords: Set<BoardCoord>,
  val clearedAsh: Set<BoardCoord>,
  val shatteredStone: Set<BoardCoord>,
  val brandedCoords: Set<BoardCoord> = emptySet(),
  val brandVigilCost: Int = 0,
  val resolvedHue: Hue?, // Single resolved hue (ADR-004; null for direct Ash or isolated Prism)
  val isDirectAshGroup: Boolean = false,
  val prismCount: Int,
  val basePower: Int,
  val inkHue: Hue?,
  val inkAmount: Int, // Ash yields 0 Ink (ADR-006)
  val ashChipDamage: Int, // n - 1 chip after Enemy Block (ADR-006)
  val stoneDefenseBonus: Int,
  val isValid: Boolean,
)

data class ValidClusterOption(
  val anchor: BoardCoord,
  val resolution: GroupResolution,
  val effectiveHalfSteps: Int,
  val previewDamage: Int,
  val advancesWheel: Boolean,
)

enum class MatchDirection {
  HORIZONTAL,
  VERTICAL,
}

data class LineMatch(
  val coords: Set<BoardCoord>,
  val hue: Hue?,
  val isAshLine: Boolean,
  val prismCount: Int,
  val direction: MatchDirection,
)

data class SwapWaveResolution(
  val coords: Set<BoardCoord>,
  val clearedAsh: Set<BoardCoord>,
  val shatteredStone: Set<BoardCoord>,
  val brandedCoords: Set<BoardCoord> = emptySet(),
  val brandVigilCost: Int = 0,
  val primaryHue: Hue?,
  val hueCounts: Map<Hue, Int> = emptyMap(),
  val isDirectAshOnly: Boolean = false,
  val prismCount: Int = 0,
  val basePower: Int = 0,
  val ashChipDamage: Int = 0,
  val stoneDefenseBonus: Int = 0,
  val isValid: Boolean = false,
) {
  fun inkForHue(hue: Hue): Int {
    val count = hueCounts[hue] ?: 0
    if (count <= 0) return 0
    val base = maxOf(1, count - 1)
    return if (hue == primaryHue && prismCount > 0) base + prismCount else base
  }

  val totalInkAmount: Int
    get() = hueCounts.keys.sumOf { inkForHue(it) }
}

data class ValidSwapOption(
  val from: BoardCoord,
  val to: BoardCoord,
  val preview: SwapWaveResolution,
  val effectiveHalfSteps: Int,
  val previewDamage: Int,
  val advancesWheel: Boolean,
)

enum class EventPriority(val order: Int) {
  SYSTEM(0),
  BOARD_POP(1),
  RELIC(2),
  STATUS(3),
  CARD(4),
  ECHO(5),
  WALL(6),
  ENEMY(7),
}

enum class CombatEventKind {
  INFO,
  BOARD_POP,
  CARD_CAST,
  ECHO,
  WALL_RISE,
  WALL_OVERFLOW,
  ENEMY_DAMAGE,
  BRAND_DRAIN,
  FLASK_USE,
  ZERO_FLASK_COLLAPSE,
  OVERDRIVE,
  BONUS_MOVE,
  PRISM_FORGED,
  LEVEL_UP,
  PROGRESSION,
  ILLUSION_SHIFT,
  ILLUSION_SHATTERED,
  VICTORY,
  DEFEAT,
}

data class CombatEvent(
  val seqId: Long,
  val priority: EventPriority,
  val message: String,
  val isAlert: Boolean = false,
  val isEcho: Boolean = false,
  val kind: CombatEventKind = CombatEventKind.INFO,
  val effectiveSpectrumHalfSteps: Int? = null,
)

data class RewardBundle(
  val cindersEarned: Int,
  val cardChoices: List<String>,
  val relicRewardId: String? = null,
  val remnantRewardId: String? = null,
  val flaskRefill: Int = 0,
  val cardPicked: Boolean = false,
  val relicPicked: Boolean = false,
  val rerollUsed: Boolean = false,
  val xpEarned: Int = 0,
  val leveledUp: Boolean = false,
  val unlockedNewSocket: Boolean = false,
  val supportChoices: List<String> = emptyList(),
  val supportPicked: Boolean = false,
  val artifactChoices: List<String> = emptyList(),
  val artifactPicked: Boolean = false,
)

data class ShopStock(
  val cardIds: List<String>,
  val relicIds: List<String>,
  val flaskPrice: Int = 45,
  val removePrice: Int = 60,
  val flaskPurchased: Boolean = false,
  val removeUsed: Boolean = false,
)

data class CombatState(
  val nodeId: String,
  val board: BoardState,
  val paintDeck: BoardPaintDeckState,
  val combatDeck: DeckState,
  val enemy: EnemyState,
  val turn: TurnState,
  val resources: ResourceState,
  val spectrum: SpectrumState,
  val player: PlayerCombatState,
  val lastEvents: List<CombatEvent>,
  val highestSpectrumThisCombat: Int,
  val wallOverflowsTriggered: Int = 0,
  val ashPoppedThisTurn: Boolean = false,
  val nonAshPoppedThisTurn: Boolean = false,
  val vigilSpentThisTurn: Boolean = false,
  val cardsPlayedThisTurn: Int = 0,
  val flaskUsedThisTurn: Boolean = false,
  val overflowOccurredThisTurn: Boolean = false,
  val arenaTwist: ArenaTwist = ArenaTwist.NONE,
  val lastScoringBreakdown: ScoringBreakdown? = null,
  val mindIllusion: MindIllusionState = MindIllusionState(),
) {
  val phase: TurnPhase get() = turn.phase
  val turnNumber: Int get() = turn.turnNumber
  val tapsRemaining: Int get() = turn.tapsRemaining
}

data class RunStats(
  val turnsTaken: Int = 0,
  val enemiesSlain: Int = 0,
  val highestSpectrumHalfSteps: Int = 2,
  val prismsDetonated: Int = 0,
  val ashBlocksPopped: Int = 0,
  val stonesShattered: Int = 0,
  val cardsPlayed: Int = 0,
  val wallOverflowsTriggered: Int = 0,
)

enum class CommandRejectionReason(val message: String) {
  NO_ACTIVE_COMBAT("No active combat encounter."),
  WRONG_PHASE("Action not legal in the current turn phase."),
  NO_TAPS_REMAINING("All 3 Weave moves have been spent this turn."),
  INVALID_BOARD_GROUP("Tap a connected 2+ hue group, 2+ Ash group, or a Prism."),
  NON_ADJACENT_SWAP("Swap two orthogonally adjacent tiles."),
  NO_MATCH_FOR_SWAP("Swap must align 3+ matching tiles in a row or column."),
  COLUMN_SEALED("That board column is Sealed."),
  EMPTY_TILE("Empty socket cannot be moved."),
  RELIC_NOT_OWNED("Required signature relic is not held."),
  CHISEL_ALREADY_USED("Chisel Wand has already been used this fight."),
  CARD_NOT_IN_HAND("Selected card is not in hand."),
  INSUFFICIENT_INK_OR_SPARK("Insufficient colored Ink pips or Spark."),
  INSUFFICIENT_VIGIL("Not enough Vigil to cast this card."),
  NO_FLASKS("No Ember Flasks remaining."),
  NODE_UNAVAILABLE("Selected map node is not reachable."),
  REWARD_ALREADY_CLAIMED("Reward has already been claimed or skipped."),
  CAMPFIRE_USED("Campfire action already spent."),
  SHOP_UNAVAILABLE("Merchant is not open."),
  EVENT_UNAVAILABLE("Event choice is not available."),
  INSUFFICIENT_CINDERS("Not enough Cinders."),
  DECK_TOO_SMALL("Cannot burn cards below minimum deck size (4)."),
  OVERDRIVE_NOT_READY("Signature Skill is not fully charged (100%)."),
  REROLL_ALREADY_USED("Card draft can only be transmuted once per reward."),
  INSUFFICIENT_HP("Not enough HP to risk stoking the kiln."),
  NO_PASSIVE_POINTS("No unspent Passive Engraving points."),
  PASSIVE_NODE_LOCKED("Prerequisite Upāya Circuit node must be etched first."),
  SUPPORT_UNAVAILABLE("Selected Dhāraṇā Support is not available."),
  ARTIFACT_UNAVAILABLE("Selected Tantric Artifact is not available."),
  WITNESS_ALREADY_USED("You have already practiced Sākṣī Witnessing this turn."),
  RUN_TERMINATED("Expedition has already ended."),
}

sealed class GameCommand {
  data class ChooseNode(val nodeId: String) : GameCommand()
  data class TapCell(val row: Int, val col: Int) : GameCommand()
  data class SwapCells(val r1: Int, val c1: Int, val r2: Int, val c2: Int) : GameCommand()
  data class UseChiselWand(val row: Int, val col: Int, val targetHue: Hue) : GameCommand()
  data object TriggerOverdrive : GameCommand()
  data object WitnessIllusion : GameCommand()
  data object ProceedToCast : GameCommand()
  data class CastCard(val cardUid: Long, val genericPayHue: Hue? = null) : GameCommand()
  data object UseFlask : GameCommand()
  data object EndTurn : GameCommand()
  data object OpenRewards : GameCommand()
  data object ClaimRewardRelic : GameCommand()
  data object SkipRewardRelic : GameCommand()
  data class PickRewardCard(val cardDefId: String) : GameCommand()
  data object SkipRewardCard : GameCommand()
  data object RerollRewardCards : GameCommand()
  data class UnlockPassiveNode(val nodeId: String) : GameCommand()
  data class SocketSupport(val supportId: String, val replaceSlot: Int = -1) : GameCommand()
  data object SkipSupportChoice : GameCommand()
  data class BindArtifact(val artifactId: String) : GameCommand()
  data object SkipArtifactChoice : GameCommand()
  data object FinishRewards : GameCommand()
  data object CampfireRest : GameCommand()
  data class CampfireUpgradeCard(val cardUid: Long) : GameCommand()
  data object CampfireGamble : GameCommand()
  data class ChooseEventOption(val choiceId: String) : GameCommand()
  data object LeaveNode : GameCommand()
  data class ShopBuyCard(val cardId: String) : GameCommand()
  data class ShopBuyRelic(val relicId: String) : GameCommand()
  data object ShopBuyFlask : GameCommand()
  data class ShopRemoveCard(val cardUid: Long) : GameCommand()

  fun serialize(): String = when (this) {
    is ChooseNode -> "CHOOSE_NODE:$nodeId"
    is TapCell -> "TAP:$row,$col"
    is SwapCells -> "SWAP:$r1,$c1,$r2,$c2"
    is UseChiselWand -> "CHISEL:$row,$col,${targetHue.name}"
    TriggerOverdrive -> "OVERDRIVE"
    WitnessIllusion -> "WITNESS_ILLUSION"
    ProceedToCast -> "PROCEED_TO_CAST"
    is CastCard -> "CAST:$cardUid,${genericPayHue?.name ?: ""}"
    UseFlask -> "USE_FLASK"
    EndTurn -> "END_TURN"
    OpenRewards -> "OPEN_REWARDS"
    ClaimRewardRelic -> "CLAIM_RELIC"
    SkipRewardRelic -> "SKIP_RELIC"
    is PickRewardCard -> "PICK_CARD:$cardDefId"
    SkipRewardCard -> "SKIP_CARD"
    RerollRewardCards -> "REROLL_CARDS"
    is UnlockPassiveNode -> "UNLOCK_PASSIVE:$nodeId"
    is SocketSupport -> "SOCKET_SUPPORT:$supportId,$replaceSlot"
    SkipSupportChoice -> "SKIP_SUPPORT"
    is BindArtifact -> "BIND_ARTIFACT:$artifactId"
    SkipArtifactChoice -> "SKIP_ARTIFACT"
    FinishRewards -> "FINISH_REWARDS"
    CampfireRest -> "CAMP_REST"
    is CampfireUpgradeCard -> "CAMP_UPGRADE:$cardUid"
    CampfireGamble -> "CAMP_GAMBLE"
    is ChooseEventOption -> "EVENT_CHOICE:$choiceId"
    LeaveNode -> "LEAVE_NODE"
    is ShopBuyCard -> "SHOP_CARD:$cardId"
    is ShopBuyRelic -> "SHOP_RELIC:$relicId"
    ShopBuyFlask -> "SHOP_FLASK"
    is ShopRemoveCard -> "SHOP_REMOVE:$cardUid"
  }

  companion object {
    fun deserialize(raw: String): GameCommand {
      val parts = raw.split(":", limit = 2)
      val head = parts[0]
      val arg = parts.getOrNull(1).orEmpty()
      return when (head) {
        "CHOOSE_NODE" -> ChooseNode(arg)
        "TAP" -> {
          val (r, c) = arg.split(",")
          TapCell(r.toInt(), c.toInt())
        }
        "SWAP" -> {
          val (r1, c1, r2, c2) = arg.split(",")
          SwapCells(r1.toInt(), c1.toInt(), r2.toInt(), c2.toInt())
        }
        "CHISEL" -> {
          val (r, c, h) = arg.split(",")
          UseChiselWand(r.toInt(), c.toInt(), Hue.valueOf(h))
        }
        "OVERDRIVE" -> TriggerOverdrive
        "WITNESS_ILLUSION" -> WitnessIllusion
        "PROCEED_TO_CAST" -> ProceedToCast
        "CAST" -> {
          val (uid, hueRaw) = arg.split(",")
          CastCard(uid.toLong(), hueRaw.takeIf { it.isNotEmpty() }?.let { Hue.valueOf(it) })
        }
        "USE_FLASK" -> UseFlask
        "END_TURN" -> EndTurn
        "OPEN_REWARDS" -> OpenRewards
        "CLAIM_RELIC" -> ClaimRewardRelic
        "SKIP_RELIC" -> SkipRewardRelic
        "PICK_CARD" -> PickRewardCard(arg)
        "SKIP_CARD" -> SkipRewardCard
        "REROLL_CARDS" -> RerollRewardCards
        "UNLOCK_PASSIVE" -> UnlockPassiveNode(arg)
        "SOCKET_SUPPORT" -> {
          val sParts = arg.split(",")
          SocketSupport(sParts[0], sParts.getOrNull(1)?.toIntOrNull() ?: -1)
        }
        "SKIP_SUPPORT" -> SkipSupportChoice
        "BIND_ARTIFACT" -> BindArtifact(arg)
        "SKIP_ARTIFACT" -> SkipArtifactChoice
        "FINISH_REWARDS" -> FinishRewards
        "CAMP_REST" -> CampfireRest
        "CAMP_UPGRADE" -> CampfireUpgradeCard(arg.toLong())
        "CAMP_GAMBLE" -> CampfireGamble
        "EVENT_CHOICE" -> ChooseEventOption(arg)
        "LEAVE_NODE" -> LeaveNode
        "SHOP_CARD" -> ShopBuyCard(arg)
        "SHOP_RELIC" -> ShopBuyRelic(arg)
        "SHOP_FLASK" -> ShopBuyFlask
        "SHOP_REMOVE" -> ShopRemoveCard(arg.toLong())
        else -> error("Unknown serialized command: $raw")
      }
    }
  }
}

data class CommandResult(
  val run: RunState,
  val accepted: Boolean,
  val rejectionReason: CommandRejectionReason? = null,
  val events: List<CombatEvent> = emptyList(),
)

data class RunState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val engineVersion: Int = CURRENT_ENGINE_VERSION,
  val contentVersion: Int = CURRENT_CONTENT_VERSION,
  val runSeed: Long,
  val seedLabel: String,
  val rng: RngStreams,
  val heroClass: HeroClass,
  val currentAct: Int,
  val hp: Int,
  val maxHp: Int,
  val flasks: Int,
  val maxFlasks: Int,
  val cinders: Int, // Strictly run-scoped barter Cinders (no meta-currency)
  val masterDeck: List<CardInstance>,
  val nextCardUid: Long,
  val relics: List<String>,
  val remnants: List<String> = emptyList(),
  val actMap: ActMap,
  val currentNodeId: String?,
  val shadeDeckSnapshot: List<String> = emptyList(),
  val combat: CombatState? = null,
  val pendingReward: RewardBundle? = null,
  val shopStock: ShopStock? = null,
  val activeEvent: EventState? = null,
  val inNonCombatNode: Boolean = false,
  val campfireUsed: Boolean = false,
  val unlitCovenantActive: Boolean = false,
  val dreadLevel: Int = 0,
  val dreadConsecutiveAshTurns: Int = 0, // ADR-017: 3 consecutive Ash-only no-card turns vs Act III Boss
  val dreadEndingAchieved: Boolean = false,
  val gameOverReason: GameOverReason? = null,
  val runWon: Boolean = false,
  val stats: RunStats = RunStats(),
  val growth: CharacterGrowthState = CharacterGrowthState(),
  val commandLog: List<String> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 3
    const val CURRENT_ENGINE_VERSION = 3
    const val CURRENT_CONTENT_VERSION = 3
  }
}
