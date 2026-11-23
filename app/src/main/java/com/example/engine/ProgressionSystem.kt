package com.example.engine

/**
 * Pure-JVM Kashmir Shaivism (Trika) & Path of Exile 2–inspired Character Progression System.
 *
 * Rooted in 9th–12th century non-dual Shaiva Tantra (Vasugupta, Utpaladeva, Abhinavagupta,
 * Kṣemarāja, Vijñāna Bhairava Tantra, Spanda Kārikā, Tantrāloka):
 * - Every hero begins fragile, raw, and unadorned as an Āṇava Initiate (Level 1, 0 passives,
 *   0 filled Support Sockets, 0 Tantric Items, 1.0× Vimarśa MULT).
 * - Every encounter yields Sādhana XP -> Level-Ups -> Upāya Circuit Passive Points & Socket unlocks
 *   (Socket 2 at Lv 3, Socket 3 at Lv 6), evolving the practitioner from Āṇava -> Śākta -> Śāmbhava -> Jīvanmukta.
 * - Dhāraṇā Support Engravings socket into the humble Signature Skill (PoE2 Skill + Support Gem model).
 * - Sacred Shaiva Tantric Items (Rudrākṣa, Ḍamaru, Bhasma Casket, Sphaṭika Mālā, Copper Yantra,
 *   Kāśmīrī Darpaṇa, Kapāla, Tantrāloka Folio) accumulate across encounters, compounding Spanda × Vimarśa.
 */

enum class PassiveBranch(
  val displayName: String,
  val sanskritName: String,
  val subtitle: String,
) {
  FOUNDRY(
    displayName = "Āṇavopāya",
    sanskritName = "Way of Effort • Kriyā",
    subtitle = "Breath, Base Spanda & Kumbhaka",
  ),
  ESCAPEMENT(
    displayName = "Śāktopāya",
    sanskritName = "Way of Mantra • Jñāna",
    subtitle = "Spanda Cascades & Vimarśa MULT",
  ),
  IRON(
    displayName = "Śāmbhavopāya",
    sanskritName = "Way of Presence • Icchā",
    subtitle = "Vigil, Guard & Bhasma Non-Duality",
  ),
}

data class PassiveNodeDef(
  val id: String,
  val branch: PassiveBranch,
  val tier: Int, // 1..4 (4 = Keystone)
  val name: String,
  val sanskritSubtitle: String,
  val shortTag: String,
  val summary: String,
  val isKeystone: Boolean = false,
  val prereqId: String? = null,
)

sealed interface SupportEffect {
  data object EchoStrike : SupportEffect
  data object ForgePrism : SupportEffect
  data object ChainCascadeMult : SupportEffect
  data object DevourAsh : SupportEffect
  data object TemperedGuard : SupportEffect
  data object HueResonance : SupportEffect
  data object ShatterStoneWave : SupportEffect
  data object BloodKilnDouble : SupportEffect
}

data class SupportEngravingDef(
  val id: String,
  val name: String,
  val traditionNote: String,
  val shortTag: String,
  val summary: String,
  val effect: SupportEffect,
)

data class TantricArtifactDef(
  val id: String,
  val name: String,
  val sanskritTitle: String,
  val glyph: String,
  val shortTag: String,
  val summary: String,
  val loreQuote: String,
)

data class SignatureSkillDef(
  val heroClass: HeroClass,
  val name: String,
  val sanskritName: String,
  val baseHeat: Int,
  val baseFlavor: String,
)

data class ScoringStep(
  val label: String,
  val heatDelta: Int = 0,
  val multAddTenths: Int = 0, // e.g., +5 = +0.5x Vimarśa MULT
  val multFactorPercent: Int = 100, // e.g., 200 = x2.0 Vimarśa MULT
)

data class ScoringBreakdown(
  val sourceTitle: String,
  val totalHeat: Int, // Spanda / Prakāśa (Base Radiant Power)
  val finalMultTenths: Int, // Vimarśa (Reflective Multiplier: 10 = 1.0x)
  val finalOutput: Int,
  val steps: List<ScoringStep>,
) {
  fun formattedMult(): String {
    val whole = finalMultTenths / 10
    val frac = finalMultTenths % 10
    return if (frac == 0) "${whole}.0×" else "${whole}.${frac}×"
  }
}

data class XpGainResult(
  val growth: CharacterGrowthState,
  val levelsGained: Int,
  val unlockedNewSocket: Boolean,
)

data class CharacterGrowthState(
  val level: Int = 1,
  val xp: Int = 0,
  val xpToNextLevel: Int = 20,
  val unspentPassivePoints: Int = 0,
  val unlockedPassiveIds: List<String> = emptyList(),
  val socketedSupportIds: List<String> = emptyList(),
  val tantricArtifactIds: List<String> = emptyList(),
  val maxSockets: Int = 1,
) {
  fun stageTitle(): String = when {
    level >= 7 -> "Jīvanmukta"
    level >= 5 -> "Śāmbhava Adept"
    level >= 3 -> "Śākta Sādhaka"
    else -> "Āṇava Initiate"
  }

  fun hasPassive(nodeId: String): Boolean = nodeId in unlockedPassiveIds

  fun hasSupport(supportId: String): Boolean = supportId in socketedSupportIds

  fun hasArtifact(artifactId: String): Boolean = artifactId in tantricArtifactIds

  fun canUnlockPassive(nodeId: String): Boolean {
    if (unspentPassivePoints <= 0) return false
    if (nodeId in unlockedPassiveIds) return false
    val def = ProgressionCatalog.passiveByIdOrNull(nodeId) ?: return false
    return def.prereqId == null || def.prereqId in unlockedPassiveIds
  }

  fun unlockPassive(nodeId: String): CharacterGrowthState {
    if (!canUnlockPassive(nodeId)) return this
    return copy(
      unspentPassivePoints = unspentPassivePoints - 1,
      unlockedPassiveIds = unlockedPassiveIds + nodeId,
    )
  }

  fun socketSupport(supportId: String, replaceSlot: Int = -1): CharacterGrowthState {
    if (ProgressionCatalog.supportByIdOrNull(supportId) == null) return this
    if (supportId in socketedSupportIds) return this
    val updated = when {
      socketedSupportIds.size < maxSockets && replaceSlot == -1 -> socketedSupportIds + supportId
      socketedSupportIds.size >= maxSockets && replaceSlot in socketedSupportIds.indices ->
        socketedSupportIds.toMutableList().apply { set(replaceSlot, supportId) }
      else -> return this
    }
    return copy(socketedSupportIds = updated)
  }

  fun bindArtifact(artifactId: String): CharacterGrowthState {
    if (ProgressionCatalog.artifactByIdOrNull(artifactId) == null) return this
    if (artifactId in tantricArtifactIds) return this
    val bonusPoint = if (artifactId == "art_tantraloka_folio") 1 else 0
    return copy(
      tantricArtifactIds = tantricArtifactIds + artifactId,
      unspentPassivePoints = unspentPassivePoints + bonusPoint,
    )
  }

  fun gainXp(amount: Int): XpGainResult {
    if (amount <= 0) return XpGainResult(this, levelsGained = 0, unlockedNewSocket = false)
    var curLevel = level
    var curXp = xp + amount
    var curThreshold = xpToNextLevel
    var points = unspentPassivePoints
    var levelsGained = 0
    while (curXp >= curThreshold) {
      curXp -= curThreshold
      curLevel++
      points++
      levelsGained++
      curThreshold = xpThresholdForLevel(curLevel)
    }
    val nextMaxSockets = socketsForLevel(curLevel)
    val unlockedSocket = nextMaxSockets > maxSockets
    return XpGainResult(
      growth = copy(
        level = curLevel,
        xp = curXp,
        xpToNextLevel = curThreshold,
        unspentPassivePoints = points,
        maxSockets = nextMaxSockets,
      ),
      levelsGained = levelsGained,
      unlockedNewSocket = unlockedSocket,
    )
  }

  companion object {
    const val MAX_POSSIBLE_SOCKETS = 3

    fun xpThresholdForLevel(level: Int): Int = when {
      level <= 1 -> 20
      level == 2 -> 24
      level == 3 -> 30
      level == 4 -> 36
      level == 5 -> 44
      else -> 44 + (level - 5) * 10
    }

    fun socketsForLevel(level: Int): Int = when {
      level >= 6 -> 3
      level >= 3 -> 2
      else -> 1
    }
  }
}

object ProgressionCatalog {

  /**
   * Maps Emberfall's 5 locked Spectrum Hues to Paramashiva's Five Cosmic Acts (Pañcakṛtya).
   */
  fun panchakrityaName(hue: Hue): String = when (hue) {
    Hue.EMBER -> "Sṛṣṭi (Emanation)"
    Hue.GOLD -> "Sthiti (Sustenance)"
    Hue.MOSS -> "Saṃhāra (Dissolution)"
    Hue.TIDE -> "Tirodhāna (Concealment)"
    Hue.VIOLET -> "Anugraha (Grace)"
  }

  val signatureSkills: Map<HeroClass, SignatureSkillDef> = mapOf(
    HeroClass.ASHEN_MONK to SignatureSkillDef(
      heroClass = HeroClass.ASHEN_MONK,
      name = "Prāṇa-Mudrā",
      sanskritName = "Humble Breath-Seal",
      baseHeat = 8,
      baseFlavor = "Humble 8 Spanda palm strike (+1 per Ember/Gold tile), +2 Vigil & +1 Ember Ink. Evolves via Dhāraṇā Sockets & Tantric Items.",
    ),
    HeroClass.QUARRY_WITCH to SignatureSkillDef(
      heroClass = HeroClass.QUARRY_WITCH,
      name = "Quarry Chip",
      sanskritName = "Raw Chisel-Blow",
      baseHeat = 7,
      baseFlavor = "Humble 7 Spanda fracture (+1 per Stone/Ash tile), +4 Guard. Evolves via Support Sockets.",
    ),
    HeroClass.THE_UNLIT to SignatureSkillDef(
      heroClass = HeroClass.THE_UNLIT,
      name = "Hollow Spark",
      sanskritName = "Unlit Cinder",
      baseHeat = 7,
      baseFlavor = "Humble 7 Spanda flicker (+2 per Ash tile), +1 Spark. Evolves via Support Sockets.",
    ),
  )

  fun signatureSkillFor(heroClass: HeroClass): SignatureSkillDef =
    signatureSkills.getValue(heroClass)

  val passiveNodes: List<PassiveNodeDef> = listOf(
    // Branch 1: Āṇavopāya (Way of Individual Effort, Breath & Body — Kriyā)
    PassiveNodeDef(
      id = "pass_foundry_1",
      branch = PassiveBranch.FOUNDRY,
      tier = 1,
      name = "Prāṇa-Vāyu",
      sanskritSubtitle = "Inhaled Effort • Āṇava I",
      shortTag = "+2 SPANDA",
      summary = "+2 Base Spanda on every non-Ash board match.",
      prereqId = null,
    ),
    PassiveNodeDef(
      id = "pass_foundry_2",
      branch = PassiveBranch.FOUNDRY,
      tier = 2,
      name = "Bhasma-Lepa",
      sanskritSubtitle = "Consecrated Ash • Āṇava II",
      shortTag = "4+ SPANDA",
      summary = "4+ tile matches gain +3 Base Spanda and +8 extra Prāṇa-Mudrā charge.",
      prereqId = "pass_foundry_1",
    ),
    PassiveNodeDef(
      id = "pass_foundry_3",
      branch = PassiveBranch.FOUNDRY,
      tier = 3,
      name = "Uccāra-Strike",
      sanskritSubtitle = "Rising Syllable • Āṇava III",
      shortTag = "ATK +3",
      summary = "Attack cards gain +3 Base Spanda before Vimarśa MULT.",
      prereqId = "pass_foundry_2",
    ),
    PassiveNodeDef(
      id = "pass_foundry_key",
      branch = PassiveBranch.FOUNDRY,
      tier = 4,
      name = "Kumbhaka Void",
      sanskritSubtitle = "Suspended Breath • VBT vv. 24–25",
      shortTag = "KEYSTONE",
      summary = "KEYSTONE: Banking unused Moves into Cast (pausing in the gap between breaths) adds +1.0× Vimarśa MULT per Move to your first Attack.",
      isKeystone = true,
      prereqId = "pass_foundry_3",
    ),

    // Branch 2: Śāktopāya (Way of Mantra, Awareness & Vibration — Jñāna)
    PassiveNodeDef(
      id = "pass_escape_1",
      branch = PassiveBranch.ESCAPEMENT,
      tier = 1,
      name = "Spanda-Tremor",
      sanskritSubtitle = "Subtle Pulse • Śākta I",
      shortTag = "+0.2× CASC",
      summary = "Every Cascade wave stacks +0.2× Vimarśa MULT for the turn.",
      prereqId = null,
    ),
    PassiveNodeDef(
      id = "pass_escape_2",
      branch = PassiveBranch.ESCAPEMENT,
      tier = 2,
      name = "Pañcakṛtya Spur",
      sanskritSubtitle = "Five Cosmic Acts • Śākta II",
      shortTag = "+0.3× ACT",
      summary = "Advancing the 5-Hue Spectrum Wheel stacks +0.3× Vimarśa MULT.",
      prereqId = "pass_escape_1",
    ),
    PassiveNodeDef(
      id = "pass_escape_3",
      branch = PassiveBranch.ESCAPEMENT,
      tier = 3,
      name = "Ajapa-Japa",
      sanskritSubtitle = "Unceasing Chant • Śākta III",
      shortTag = "+0.5× CHAIN",
      summary = "Casting 2+ cards of the same Hue in one turn adds +0.5× Vimarśa MULT.",
      prereqId = "pass_escape_2",
    ),
    PassiveNodeDef(
      id = "pass_escape_key",
      branch = PassiveBranch.ESCAPEMENT,
      tier = 4,
      name = "Prakāśa–Vimarśa",
      sanskritSubtitle = "Light & Inner Mirror • Pratyabhijñā",
      shortTag = "KEYSTONE",
      summary = "KEYSTONE: Alternating different Hues on consecutive moves reflects Light into Mirror: +4 Spanda & +0.5× Vimarśa MULT.",
      isKeystone = true,
      prereqId = "pass_escape_3",
    ),

    // Branch 3: Śāmbhavopāya (Way of Pure Presence & Non-Duality — Icchā)
    PassiveNodeDef(
      id = "pass_iron_1",
      branch = PassiveBranch.IRON,
      tier = 1,
      name = "Rudrākṣa-Kavaca",
      sanskritSubtitle = "Tears of Compassion • Śāmbhava I",
      shortTag = "+6 MAX HP",
      summary = "Permanently gain +6 Max HP and immediately mend 6 HP.",
      prereqId = null,
    ),
    PassiveNodeDef(
      id = "pass_iron_2",
      branch = PassiveBranch.IRON,
      tier = 2,
      name = "Khecarī-Mudrā",
      sanskritSubtitle = "Seal of Sky-Space • Śāmbhava II",
      shortTag = "+2 GUARD",
      summary = "Every 4+ tile match seals +2 bonus Guard block.",
      prereqId = "pass_iron_1",
    ),
    PassiveNodeDef(
      id = "pass_iron_3",
      branch = PassiveBranch.IRON,
      tier = 3,
      name = "Unmeṣa-Vigil",
      sanskritSubtitle = "Sudden Flash • Śāmbhava III",
      shortTag = "+2 VIGIL",
      summary = "Begin every encounter already awake with +2 Vigil.",
      prereqId = "pass_iron_2",
    ),
    PassiveNodeDef(
      id = "pass_iron_key",
      branch = PassiveBranch.IRON,
      tier = 4,
      name = "Śmaśāna-Bhasma",
      sanskritSubtitle = "Cremation-Ground Non-Duality",
      shortTag = "KEYSTONE",
      summary = "KEYSTONE: Ash is not impurity—it is pure remainder. Clearing Ash restores +2 HP and stacks +0.3× Vimarśa MULT.",
      isKeystone = true,
      prereqId = "pass_iron_3",
    ),
  )

  val supportEngravings: List<SupportEngravingDef> = listOf(
    SupportEngravingDef(
      id = "sup_echo_strike",
      name = "Nāda-Echo",
      traditionNote = "Vijñāna Bhairava • Inner Sound Current",
      shortTag = "RETRIGGER",
      summary = "Signature Skill resonates a second strike at 60% Spanda.",
      effect = SupportEffect.EchoStrike,
    ),
    SupportEngravingDef(
      id = "sup_prism_forge",
      name = "Bindu-Prism",
      traditionNote = "Uccāra • Point of Emanation",
      shortTag = "+1 BINDU",
      summary = "Casting Signature Skill crystallizes +1 Prism (Bindu) onto the board.",
      effect = SupportEffect.ForgePrism,
    ),
    SupportEngravingDef(
      id = "sup_chain_cascade",
      name = "Śakti-Cakra",
      traditionNote = "Śiva Sūtras • Wheel of Energies",
      shortTag = "+0.5×/CASC",
      summary = "Signature Skill gains +0.5× Vimarśa MULT per Cascade triggered this turn.",
      effect = SupportEffect.ChainCascadeMult,
    ),
    SupportEngravingDef(
      id = "sup_soot_eater",
      name = "Bhasma-Sādhana",
      traditionNote = "Trika • Dissolving Mala into Ash",
      shortTag = "EAT ASH",
      summary = "Consumes up to 3 Ash tiles on cast (+4 Spanda & +0.4× MULT each).",
      effect = SupportEffect.DevourAsh,
    ),
    SupportEngravingDef(
      id = "sup_tempered_guard",
      name = "Yoni-Mudrā",
      traditionNote = "Vijñāna Bhairava • Inward Seal",
      shortTag = "60% GUARD",
      summary = "Casting Signature Skill seals 60% of its Spanda as Guard.",
      effect = SupportEffect.TemperedGuard,
    ),
    SupportEngravingDef(
      id = "sup_hue_resonance",
      name = "So'ham-Breath",
      traditionNote = "Ajapa Mantra • Spontaneous Breath",
      shortTag = "FAST +0.6×",
      summary = "Primary Hue matches charge Skill 50% faster; Skill gains +0.6× Vimarśa MULT.",
      effect = SupportEffect.HueResonance,
    ),
    SupportEngravingDef(
      id = "sup_shatter_wave",
      name = "Bhairava-Rava",
      traditionNote = "Fierce Roar • Shattering Illusion",
      shortTag = "CRACK STONE",
      summary = "Shatters up to 3 Stone tiles on cast (+5 Spanda & +3 Guard each).",
      effect = SupportEffect.ShatterStoneWave,
    ),
    SupportEngravingDef(
      id = "sup_blood_kiln",
      name = "Rakta-Tarpaṇa",
      traditionNote = "Kāpālika • Fierce Offering",
      shortTag = "-4HP ×2.0",
      summary = "Offer 4 HP (if HP > 4) on cast to double final Skill Vimarśa MULT (×2.0).",
      effect = SupportEffect.BloodKilnDouble,
    ),
  )

  /**
   * 8 authentic Shaiva Tantric ritual artifacts that empower the Monk across encounters.
   * Heroes start with ZERO artifacts (humble wandering ascetic) and bind them through play.
   */
  val tantricArtifacts: List<TantricArtifactDef> = listOf(
    TantricArtifactDef(
      id = "art_rudraksha_mala",
      name = "Pañcamukhī Rudrākṣa",
      sanskritTitle = "Five-Faced Seed Mālā",
      glyph = "◎",
      shortTag = "+1/NODE",
      summary = "+1 Base Spanda on every match for each unlocked Upāya Passive node you hold.",
      loreQuote = "Five facets for the Five Cosmic Acts; grows heavier with every vow kept.",
    ),
    TantricArtifactDef(
      id = "art_nada_damaru",
      name = "Nāda-Ḍamaru",
      sanskritTitle = "Hourglass Hand-Drum",
      glyph = "⋈",
      shortTag = "2ND +0.4×",
      summary = "Every 2nd board move in a turn beats the drum: +0.4× Vimarśa MULT & +8 Skill charge.",
      loreQuote = "From its two pinched cones sprang the Sanskrit alphabet and the pulse of Spanda.",
    ),
    TantricArtifactDef(
      id = "art_bhasma_casket",
      name = "Śmaśāna Bhasma Casket",
      sanskritTitle = "Sacred Cremation-Ash Vessel",
      glyph = "△",
      shortTag = "ASH +3 GRD",
      summary = "Begin every combat with +3 Guard & +1 Vigil; clearing Ash adds +2 bonus Spanda.",
      loreQuote = "When forms burn away, only the white ash of pure awareness remains.",
    ),
    TantricArtifactDef(
      id = "art_sphatika_mala",
      name = "Sphaṭika Quartz Mālā",
      sanskritTitle = "Clear Crystal Rosary",
      glyph = "◇",
      shortTag = "WHEEL +HP",
      summary = "Advancing the 5-Hue Spectrum Wheel restores +1 HP and grants +1 Ink of that Hue.",
      loreQuote = "Transparent as Prakāśa—reflecting every hue without clinging to any.",
    ),
    TantricArtifactDef(
      id = "art_tamra_yantra",
      name = "Tāmra Śrī-Yantra Plate",
      sanskritTitle = "Engraved Copperplate Yantra",
      glyph = "✡",
      shortTag = "4+ +0.5×",
      summary = "Every 4+ tile match adds +0.5× Vimarśa MULT & +4 Skill charge per socketed Dhāraṇā.",
      loreQuote = "Conductive red copper etched with interlocking triangles of Śiva and Śakti.",
    ),
    TantricArtifactDef(
      id = "art_kashmiri_darpana",
      name = "Kāśmīrī Darpaṇa",
      sanskritTitle = "Polished Brass Mirror",
      glyph = "◉",
      shortTag = "1ST GUARD",
      summary = "Your first Ink Card cast each turn mirrors +4 Guard (or 25% of its Spanda, whichever is higher).",
      loreQuote = "The cosmos appears inside Consciousness like a city reflected in a clear mirror.",
    ),
    TantricArtifactDef(
      id = "art_kapala_bowl",
      name = "Vāma-Kapāla Bowl",
      sanskritTitle = "Ascetic Alms Vessel",
      glyph = "☾",
      shortTag = "+8 XP/+10C",
      summary = "Winning an encounter with Vimarśa MULT ≥ 1.5× yields +8 bonus Sādhana XP & +10 Cinders.",
      loreQuote = "Carried by fearless wanderers who drink transcendence from impermanence.",
    ),
    TantricArtifactDef(
      id = "art_tantraloka_folio",
      name = "Tantrāloka Palm-Leaf",
      sanskritTitle = "Abhinavagupta's Commentary",
      glyph = "❖",
      shortTag = "+1 PT +0.2×",
      summary = "Immediately grants +1 Upāya Passive Point on pickup and +0.2× permanent starting Vimarśa MULT.",
      loreQuote = "An encyclopedic lamp illuminating the ladder from Āṇava effort to effortless recognition.",
    ),
  )

  private val passiveMap = passiveNodes.associateBy { it.id }
  private val supportMap = supportEngravings.associateBy { it.id }
  private val artifactMap = tantricArtifacts.associateBy { it.id }

  fun passiveById(id: String): PassiveNodeDef =
    passiveMap[id] ?: error("Unknown passive node id=$id")

  fun passiveByIdOrNull(id: String): PassiveNodeDef? = passiveMap[id]

  fun supportById(id: String): SupportEngravingDef =
    supportMap[id] ?: error("Unknown support engraving id=$id")

  fun supportByIdOrNull(id: String): SupportEngravingDef? = supportMap[id]

  fun artifactById(id: String): TantricArtifactDef =
    artifactMap[id] ?: error("Unknown tantric artifact id=$id")

  fun artifactByIdOrNull(id: String): TantricArtifactDef? = artifactMap[id]

  fun passivesByBranch(branch: PassiveBranch): List<PassiveNodeDef> =
    passiveNodes.filter { it.branch == branch }.sortedBy { it.tier }
}

/**
 * The 5 Emotional Currents (Kleśas / Vikalpas) of Kashmir Shaivism & Tantric psychology.
 * Emotions are not evil—they are waves of Śakti—but CLINGING (Rāga / Attachment) to a single
 * emotion traps the mind in Illusion (Māyā). Conquering the illusion through Witnessing (Sākṣī)
 * and non-attached flow awakens Pratyabhijñā (Recognition).
 */
enum class EmotionalIllusion(
  val hue: Hue,
  val sanskritName: String,
  val emotionName: String,
  val veilTitle: String,
  val projectionLabel: String,
  val attachmentTrap: String,
  val liberationInsight: String,
) {
  KRODHA_WRATH(
    hue = Hue.EMBER,
    sanskritName = "Krodha-Vikalpa",
    emotionName = "Wrath",
    veilTitle = "Veil of Wrath",
    projectionLabel = "Projection of Anger",
    attachmentTrap = "Clinging to Ember (Wrath) or repeating one hue swells Attachment (+2 Rāga).",
    liberationInsight = "Let wrath burn without gripping it: flow to another hue or practice Sākṣī Witness.",
  ),
  LOBHA_CRAVING(
    hue = Hue.GOLD,
    sanskritName = "Lobha-Vikalpa",
    emotionName = "Craving",
    veilTitle = "Veil of Craving",
    projectionLabel = "Mirage of Hunger",
    attachmentTrap = "Clinging to Gold (Craving) or repeating one hue swells Attachment (+2 Rāga).",
    liberationInsight = "Desire dissolves when observed: shift hues along the Pañcakṛtya wheel or Witness.",
  ),
  MOHA_SLUMBER(
    hue = Hue.MOSS,
    sanskritName = "Moha-Vikalpa",
    emotionName = "Delusion",
    veilTitle = "Veil of Slumber",
    projectionLabel = "Mist of Inertia",
    attachmentTrap = "Clinging to Moss (Inertia) or repeating one hue swells Attachment (+2 Rāga).",
    liberationInsight = "Wake from heavy habit: alternate hues or tap Sākṣī Witness to pierce the fog.",
  ),
  BHAYA_DREAD(
    hue = Hue.TIDE,
    sanskritName = "Bhaya-Vikalpa",
    emotionName = "Fear",
    veilTitle = "Veil of Fear",
    projectionLabel = "Phantom of Contraction",
    attachmentTrap = "Clinging to Tide (Fear) or repeating one hue swells Attachment (+2 Rāga).",
    liberationInsight = "Fear shrinks when witnessed openly: move through other hues or invoke Sākṣī.",
  ),
  MANA_PRIDE(
    hue = Hue.VIOLET,
    sanskritName = "Māna-Vikalpa",
    emotionName = "Pride",
    veilTitle = "Veil of Pride",
    projectionLabel = "Idol of the Ego",
    attachmentTrap = "Clinging to Violet (Pride) or repeating one hue swells Attachment (+2 Rāga).",
    liberationInsight = "Drop the mirrored mask: step across hues or release attachment with Sākṣī.",
  );

  companion object {
    fun forHue(hue: Hue): EmotionalIllusion = entries.first { it.hue == hue }
  }
}

/**
 * Per-combat state of the player's mind facing an Emotional Illusion.
 * - attachment (0..10): Rāga / emotional clinging. At >= 6 (Māyā Grip), enemy strikes resonate
 *   for +25% damage. At 10 (Vikalpa Collapse), attachment recoils for 4 HP & spawns 1 Ash mist.
 * - veilLayers (3..0): Peeling all 3 layers via Witnessing or Non-Attached Wheel Flow shatters
 *   the illusion (Pratyabhijñā!), breaking enemy block, dealing clarity damage, and granting
 *   +1.0× permanent Vimarśa MULT & +25 bonus Sādhana XP on victory.
 */
data class MindIllusionState(
  val illusion: EmotionalIllusion = EmotionalIllusion.KRODHA_WRATH,
  val attachment: Int = 0,
  val clarity: Int = 0,
  val veilLayers: Int = MAX_VEIL_LAYERS,
  val shattered: Boolean = false,
  val witnessUsedThisTurn: Boolean = false,
  val detachedBonusSpanda: Int = 0,
) {
  val isEntangled: Boolean get() = !shattered && attachment >= ENTANGLED_THRESHOLD

  fun mindStateLabel(): String = when {
    shattered -> "PRATYABHIJÑĀ • FREE"
    attachment >= ENTANGLED_THRESHOLD -> "MĀYĀ GRIP (+25% DMG)"
    attachment >= 3 -> "CLINGING (${attachment}/$MAX_ATTACHMENT)"
    else -> "SĀKṢĪ STILLNESS"
  }

  companion object {
    const val MAX_ATTACHMENT = 10
    const val ENTANGLED_THRESHOLD = 6
    const val MAX_VEIL_LAYERS = 3

    fun forEncounter(runSeed: Long, nodeId: String): EmotionalIllusion {
      var h = runSeed xor 0x2545F4914F6CDD1DL
      for (ch in nodeId) {
        h = (h * 37L) xor ch.code.toLong()
      }
      val idx = ((h xor (h ushr 32)) and 0x7FFFFFFFL).toInt() % EmotionalIllusion.entries.size
      return EmotionalIllusion.entries[idx]
    }
  }
}

