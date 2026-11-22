package com.example.engine

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ADR-018 15-row deterministic spire map generator with O(V+E) DAG path-composition validation,
 * ADR-016 Shade placement at Act I Row 5 (full previous-deck intents, Vigor scaling),
 * and ADR-013 exponential enemy scaling: round(36 * 1.11^node).
 */
object CampaignGenerator {

  const val ACT_ROW_COUNT = 15

  fun generateActMap(act: Int, rng: SeededRng): Pair<ActMap, SeededRng> {
    var currentRng = rng
    val rowCols = List(ACT_ROW_COUNT) { idx ->
      val rowNum = idx + 1
      when {
        rowNum == 1 -> 3
        act == 1 && rowNum == 5 -> 1 // Act I Row 5 Shade milestone
        rowNum in listOf(6, 8, 9, 14) -> 2
        rowNum == 15 -> 1 // Act Boss
        else -> 3
      }
    }

    val rowsGrid = mutableListOf<List<MapNode>>()
    for (idx in rowCols.indices) {
      val rowNum = idx + 1
      val count = rowCols[idx]
      val rowList = mutableListOf<MapNode>()
      for (c in 0 until count) {
        val (roll, rNext) = currentRng.nextInt(100)
        currentRng = rNext
        val type = when {
          rowNum == 1 -> MapNodeType.COMBAT
          rowNum == 2 -> MapNodeType.EVENT
          rowNum == 3 -> MapNodeType.COMBAT
          rowNum == 4 -> if (roll < 50 && c != 1) MapNodeType.ELITE else MapNodeType.COMBAT
          rowNum == 5 -> if (act == 1) MapNodeType.SHADE else MapNodeType.COMBAT
          rowNum == 6 -> MapNodeType.CAMPFIRE
          rowNum == 7 -> if (roll < 45) MapNodeType.EVENT else MapNodeType.COMBAT
          rowNum == 8 -> MapNodeType.TREASURE
          rowNum == 9 -> MapNodeType.MERCHANT
          rowNum == 10 -> MapNodeType.ELITE
          rowNum == 11 -> MapNodeType.COMBAT
          rowNum == 12 -> if (roll < 40) MapNodeType.EVENT else MapNodeType.COMBAT
          rowNum == 13 -> MapNodeType.COMBAT
          rowNum == 14 -> MapNodeType.CAMPFIRE
          else -> MapNodeType.BOSS
        }
        rowList.add(
          MapNode(
            id = "a${act}_r${rowNum}_c${c}",
            act = act,
            row = rowNum,
            col = c,
            type = type,
            nextNodeIds = emptyList(),
            visited = false,
          )
        )
      }
      rowsGrid.add(rowList)
    }

    val nodes = mutableListOf<MapNode>()
    for (r in 0 until rowsGrid.lastIndex) {
      val currRow = rowsGrid[r]
      val nextRow = rowsGrid[r + 1]
      val linkedTargets = mutableSetOf<String>()

      val updatedCurr = currRow.mapIndexed { idx, node ->
        val primaryTargetIdx = ((idx.toFloat() / currRow.size) * nextRow.size).toInt()
          .coerceIn(0, nextRow.lastIndex)
        val targets = mutableSetOf(nextRow[primaryTargetIdx].id)
        if (nextRow.size > 1) {
          val (branchRoll, nextRng) = currentRng.nextInt(100)
          currentRng = nextRng
          if (branchRoll < 60) {
            val altIdx = (primaryTargetIdx + 1).coerceAtMost(nextRow.lastIndex)
            targets.add(nextRow[altIdx].id)
          }
        }
        linkedTargets.addAll(targets)
        node.copy(nextNodeIds = targets.toList())
      }.toMutableList()

      nextRow.forEachIndexed { nextIdx, nextNode ->
        if (nextNode.id !in linkedTargets) {
          val parentIdx = nextIdx.coerceIn(0, updatedCurr.lastIndex)
          val parent = updatedCurr[parentIdx]
          updatedCurr[parentIdx] = parent.copy(nextNodeIds = (parent.nextNodeIds + nextNode.id).distinct())
        }
      }

      nodes.addAll(updatedCurr)
    }
    nodes.addAll(rowsGrid.last())

    return ActMap(act = act, nodes = nodes) to currentRng
  }

  /**
   * Validates reachability, no-orphan rows 2..15, Shade placement, Boss root, and
   * O(V+E) topological DP min/max node-type bounds across all paths from Row 1 to Row 15.
   */
  fun validateMap(map: ActMap): Boolean {
    if (map.nodes.isEmpty()) return false
    val byId = map.nodes.associateBy { it.id }
    if (byId.size != map.nodes.size) return false

    val seenCoords = mutableSetOf<Pair<Int, Int>>()
    for (n in map.nodes) {
      if (n.act != map.act) return false
      if (n.row !in 1..ACT_ROW_COUNT || n.col < 0) return false
      if (!seenCoords.add(n.row to n.col)) return false
      if (n.nextNodeIds.size != n.nextNodeIds.toSet().size) return false
      if (n.row == ACT_ROW_COUNT) {
        if (n.nextNodeIds.isNotEmpty()) return false
      } else {
        if (n.nextNodeIds.isEmpty()) return false
        for (targetId in n.nextNodeIds) {
          val target = byId[targetId] ?: return false
          if (target.row != n.row + 1) return false
        }
      }
    }

    val row1 = map.nodes.filter { it.row == 1 }
    val row15 = map.nodes.filter { it.row == ACT_ROW_COUNT }
    if (row1.isEmpty() || row15.size != 1 || row15.first().type != MapNodeType.BOSS) return false
    if (map.act == 1 && map.nodes.none { it.row == 5 && it.type == MapNodeType.SHADE }) return false

    val incomingCounts = mutableMapOf<String, Int>()
    map.nodes.forEach { n ->
      n.nextNodeIds.forEach { target ->
        incomingCounts[target] = (incomingCounts[target] ?: 0) + 1
      }
    }
    if (map.nodes.any { it.row > 1 && (incomingCounts[it.id] ?: 0) == 0 }) return false

    val bossId = row15.first().id
    val allReachBoss = row1.all { start ->
      val seen = mutableSetOf<String>()
      val queue = ArrayDeque<String>()
      queue.add(start.id)
      var reachedBoss = false
      while (queue.isNotEmpty()) {
        val curr = queue.removeFirst()
        if (curr == bossId) {
          reachedBoss = true
          break
        }
        if (seen.add(curr)) {
          byId[curr]?.nextNodeIds?.forEach { queue.add(it) }
        }
      }
      reachedBoss
    }
    if (!allReachBoss) return false

    // O(V+E) topological DP tracking (min, max) count of every MapNodeType across all routes
    val minBounds = mutableMapOf<String, MutableMap<MapNodeType, Int>>()
    val maxBounds = mutableMapOf<String, MutableMap<MapNodeType, Int>>()
    for (node in map.nodes.sortedBy { it.row }) {
      val mins = MapNodeType.entries.associateWith { 0 }.toMutableMap()
      val maxs = MapNodeType.entries.associateWith { 0 }.toMutableMap()
      mins[node.type] = 1
      maxs[node.type] = 1
      minBounds[node.id] = mins
      maxBounds[node.id] = maxs
    }

    val parents = mutableMapOf<String, MutableList<MapNode>>()
    map.nodes.forEach { parent ->
      parent.nextNodeIds.forEach { childId ->
        parents.getOrPut(childId) { mutableListOf() }.add(parent)
      }
    }

    for (node in map.nodes.sortedBy { it.row }) {
      if (node.row == 1) continue
      val nodeParents = parents[node.id].orEmpty()
      if (nodeParents.isEmpty()) return false
      val mins = minBounds.getValue(node.id)
      val maxs = maxBounds.getValue(node.id)
      for (type in MapNodeType.entries) {
        val selfInc = if (node.type == type) 1 else 0
        mins[type] = nodeParents.minOf { p -> minBounds.getValue(p.id).getValue(type) } + selfInc
        maxs[type] = nodeParents.maxOf { p -> maxBounds.getValue(p.id).getValue(type) } + selfInc
      }
    }

    val bossMin = minBounds.getValue(bossId)
    val bossMax = maxBounds.getValue(bossId)
    return bossMin.getValue(MapNodeType.COMBAT) >= 4 && bossMax.getValue(MapNodeType.COMBAT) <= 8 &&
      bossMin.getValue(MapNodeType.ELITE) >= 1 && bossMax.getValue(MapNodeType.ELITE) <= 2 &&
      bossMin.getValue(MapNodeType.CAMPFIRE) == 2 && bossMax.getValue(MapNodeType.CAMPFIRE) == 2 &&
      bossMin.getValue(MapNodeType.MERCHANT) == 1 && bossMax.getValue(MapNodeType.MERCHANT) == 1 &&
      bossMin.getValue(MapNodeType.EVENT) >= 1 && bossMax.getValue(MapNodeType.EVENT) <= 3 &&
      bossMin.getValue(MapNodeType.TREASURE) == 1 && bossMax.getValue(MapNodeType.TREASURE) == 1 &&
      bossMin.getValue(MapNodeType.BOSS) == 1 && bossMax.getValue(MapNodeType.BOSS) == 1 &&
      (map.act != 1 || (bossMin.getValue(MapNodeType.SHADE) == 1 && bossMax.getValue(MapNodeType.SHADE) == 1))
  }

  fun pickEnemyForNode(act: Int, nodeType: MapNodeType, rng: SeededRng): Pair<EnemyDef, SeededRng> {
    if (nodeType == MapNodeType.SHADE) {
      return GameCatalog.enemyById("special_shade") to rng
    }
    val pool = when (nodeType) {
      MapNodeType.BOSS -> GameCatalog.enemies.filter { it.act == act && it.isBoss }
      MapNodeType.ELITE -> GameCatalog.enemies.filter { it.act == act && it.isElite && !it.isShade }
      else -> GameCatalog.enemies.filter { it.act == act && !it.isBoss && !it.isElite && !it.isShade }
    }
    val (idx, nextRng) = rng.nextInt(pool.size)
    return pool[idx] to nextRng
  }

  /**
   * ADR-013 Exponential Vigor scaling: round(36 * 1.11^node) with Elite (1.6x), Boss (3.0x), and Dread tier.
   */
  fun scaledEnemyHp(
    nodeIndex: Int,
    act: Int,
    isElite: Boolean,
    isBoss: Boolean,
    dreadLevel: Int,
    isShade: Boolean,
    shadeDeckSize: Int,
  ): Int {
    if (isShade) {
      return 52 + (shadeDeckSize * 2) + (dreadLevel * 8)
    }
    val step = ((act - 1) * 5 + ((nodeIndex - 1) / 3).coerceIn(0, 4) + 1)
    val base = 36.0 * 1.11.pow(step.toDouble())
    val roleMult = when {
      isBoss -> 3.0
      isElite -> 1.6
      else -> 1.0
    }
    val dreadMult = 1.0 + (dreadLevel * 0.1)
    return (base * roleMult * dreadMult).roundToInt().coerceAtLeast(24)
  }

  /**
   * ADR-016 Shade Vigor derived deterministically from the prior run's deck rarity density and Dread level.
   */
  fun computeShadeVigorBonus(shadeDeckIds: List<String>, dreadLevel: Int): Int {
    if (shadeDeckIds.isEmpty()) return dreadLevel.coerceAtLeast(0)
    val defs = shadeDeckIds.mapNotNull { runCatching { GameCatalog.cardById(it) }.getOrNull() }
    val rareWeight = defs.count { it.rarity == CardRarity.RARE } * 2 + defs.count { it.rarity == CardRarity.UNCOMMON }
    return ((rareWeight / 3) + dreadLevel).coerceIn(0, 8)
  }

  fun dreadTierLabel(level: Int): String = when (level.coerceIn(0, 3)) {
    0 -> "Dread 0 — Standard Pilgrimage"
    1 -> "Dread 1 — Ash Storm (+10% Enemy HP)"
    2 -> "Dread 2 — Sealed Crucible (+20% Enemy HP, +2 Opening Ash)"
    else -> "Dread 3 — Sovereign's Vigil (+30% HP, +2 Ash & +1 Stone)"
  }

  /**
   * Derives Shade's two telegraph slots dynamically from the FULL previous-run deck snapshot (ADR-016).
   */
  fun buildShadeIntentsFromDeck(
    shadeDeckIds: List<String>,
    act: Int,
    shadeVigorBonus: Int = 0,
  ): Pair<List<EnemyIntent>, List<EnemyIntent>> {
    if (shadeDeckIds.isEmpty()) {
      val def = GameCatalog.enemyById("special_shade")
      return def.slot1Pattern to def.slot2Pattern
    }
    val defs = shadeDeckIds.mapNotNull { runCatching { GameCatalog.cardById(it) }.getOrNull() }
    if (defs.isEmpty()) {
      val def = GameCatalog.enemyById("special_shade")
      return def.slot1Pattern to def.slot2Pattern
    }

    val slot1 = defs.map { card ->
      val dmg = (card.damage.coerceAtLeast(7)) + (act * 2) + shadeVigorBonus
      if (card.gainFlasks > 0 || card.rarity == CardRarity.RARE) {
        EnemyIntent.SiphonFlask((dmg - 1).coerceAtLeast(6), 1)
      } else {
        EnemyIntent.Attack(dmg)
      }
    }
    val slot2 = defs.map { card ->
      when {
        card.blockGain > 0 -> EnemyIntent.Defend(card.blockGain + act * 2 + shadeVigorBonus)
        card.paintCount > 0 -> EnemyIntent.CorruptBoard(TileKind.Ash, 2)
        card.unsealColumns -> EnemyIntent.SealColumn(turns = 3, damage = 0)
        card.clearsFog -> EnemyIntent.InflictFog(taps = 3, damage = 0)
        card.vigilBurnMax > 0 -> EnemyIntent.InflictBrand(count = 2, damage = 0)
        else -> EnemyIntent.Defend(6 + act * 2)
      }
    }
    return slot1 to slot2
  }
}
