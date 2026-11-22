package com.example.data

import com.example.engine.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned lossless deterministic JSON codec for RunState snapshots (v2 -> v3 migration included).
 * Explicitly rejects corrupt/unknown TileKind strings rather than silently defaulting.
 */
object RunSnapshotCodec {

  fun encode(run: RunState): String {
    val root = JSONObject()
    root.put("schemaVersion", run.schemaVersion)
    root.put("engineVersion", run.engineVersion)
    root.put("contentVersion", run.contentVersion)
    root.put("runSeed", run.runSeed)
    root.put("seedLabel", run.seedLabel)
    root.put("rng", JSONObject().apply {
      put("map", run.rng.map.state)
      put("boardPaint", run.rng.boardPaint.state)
      put("combatDeck", run.rng.combatDeck.state)
      put("enemy", run.rng.enemy.state)
      put("rewards", run.rng.rewards.state)
    })
    root.put("heroClass", run.heroClass.name)
    root.put("currentAct", run.currentAct)
    root.put("hp", run.hp)
    root.put("maxHp", run.maxHp)
    root.put("flasks", run.flasks)
    root.put("maxFlasks", run.maxFlasks)
    root.put("cinders", run.cinders)
    root.put("nextCardUid", run.nextCardUid)
    root.put("currentNodeId", run.currentNodeId ?: JSONObject.NULL)
    root.put("inNonCombatNode", run.inNonCombatNode)
    root.put("campfireUsed", run.campfireUsed)
    root.put("unlitCovenantActive", run.unlitCovenantActive)
    root.put("dreadLevel", run.dreadLevel)
    root.put("dreadConsecutiveAshTurns", run.dreadConsecutiveAshTurns)
    root.put("dreadEndingAchieved", run.dreadEndingAchieved)
    root.put("gameOverReason", run.gameOverReason?.name ?: JSONObject.NULL)
    root.put("runWon", run.runWon)

    root.put("masterDeck", encodeCards(run.masterDeck))
    root.put("relics", JSONArray(run.relics))
    root.put("remnants", JSONArray(run.remnants))
    root.put("shadeDeckSnapshot", JSONArray(run.shadeDeckSnapshot))
    root.put("actMap", encodeActMap(run.actMap))
    root.put("stats", encodeStats(run.stats))
    root.put("growth", encodeGrowth(run.growth))
    root.put("commandLog", JSONArray(run.commandLog))
    root.put("combat", run.combat?.let { encodeCombat(it) } ?: JSONObject.NULL)
    root.put("pendingReward", run.pendingReward?.let { encodeReward(it) } ?: JSONObject.NULL)
    root.put("shopStock", run.shopStock?.let { encodeShop(it) } ?: JSONObject.NULL)
    root.put("activeEvent", run.activeEvent?.let { encodeEvent(it) } ?: JSONObject.NULL)
    return root.toString()
  }

  fun decode(json: String): RunState {
    val rawRoot = JSONObject(json)
    val rawSchemaVersion = rawRoot.optInt("schemaVersion", 1)
    require(rawSchemaVersion in 2..RunState.CURRENT_SCHEMA_VERSION) {
      "Unsupported snapshot schemaVersion=$rawSchemaVersion (supported 2..${RunState.CURRENT_SCHEMA_VERSION})"
    }

    val root = if (rawSchemaVersion < RunState.CURRENT_SCHEMA_VERSION) {
      migrateV2ToV3(rawRoot)
    } else {
      rawRoot
    }

    val rngObj = root.getJSONObject("rng")
    val rng = RngStreams(
      map = SeededRng(rngObj.getLong("map")),
      boardPaint = SeededRng(rngObj.getLong("boardPaint")),
      combatDeck = SeededRng(rngObj.getLong("combatDeck")),
      enemy = SeededRng(rngObj.getLong("enemy")),
      rewards = SeededRng(rngObj.getLong("rewards")),
    )

    val decoded = RunState(
      schemaVersion = RunState.CURRENT_SCHEMA_VERSION,
      engineVersion = root.getInt("engineVersion"),
      contentVersion = root.getInt("contentVersion"),
      runSeed = root.getLong("runSeed"),
      seedLabel = root.getString("seedLabel"),
      rng = rng,
      heroClass = HeroClass.valueOf(root.getString("heroClass")),
      currentAct = root.getInt("currentAct"),
      hp = root.getInt("hp"),
      maxHp = root.getInt("maxHp"),
      flasks = root.getInt("flasks"),
      maxFlasks = root.getInt("maxFlasks"),
      cinders = root.getInt("cinders"),
      masterDeck = decodeCards(root.getJSONArray("masterDeck")),
      nextCardUid = root.getLong("nextCardUid"),
      relics = decodeStringList(root.getJSONArray("relics")),
      remnants = decodeStringList(root.getJSONArray("remnants")),
      shadeDeckSnapshot = decodeStringList(root.getJSONArray("shadeDeckSnapshot")),
      actMap = decodeActMap(root.getJSONObject("actMap")),
      currentNodeId = if (root.isNull("currentNodeId")) null else root.getString("currentNodeId"),
      combat = if (root.isNull("combat")) null else decodeCombat(root.getJSONObject("combat")),
      pendingReward = if (root.isNull("pendingReward")) null else decodeReward(root.getJSONObject("pendingReward")),
      shopStock = if (root.isNull("shopStock")) null else decodeShop(root.getJSONObject("shopStock")),
      activeEvent = if (root.isNull("activeEvent")) null else decodeEvent(root.getJSONObject("activeEvent")),
      inNonCombatNode = root.optBoolean("inNonCombatNode", false),
      campfireUsed = root.getBoolean("campfireUsed"),
      unlitCovenantActive = root.optBoolean("unlitCovenantActive", false),
      dreadLevel = root.optInt("dreadLevel", 0),
      dreadConsecutiveAshTurns = root.optInt("dreadConsecutiveAshTurns", 0),
      dreadEndingAchieved = root.optBoolean("dreadEndingAchieved", false),
      gameOverReason = if (root.isNull("gameOverReason")) null else GameOverReason.valueOf(root.getString("gameOverReason")),
      runWon = root.getBoolean("runWon"),
      stats = decodeStats(root.getJSONObject("stats")),
      growth = root.optJSONObject("growth")?.let { decodeGrowth(it) } ?: CharacterGrowthState(),
      commandLog = decodeStringList(root.getJSONArray("commandLog")),
    )
    validateDecodedRun(decoded)
    return decoded
  }

  private fun validateDecodedRun(run: RunState) {
    require(run.engineVersion in 2..RunState.CURRENT_ENGINE_VERSION) {
      "Unsupported snapshot engineVersion=${run.engineVersion} (supported 2..${RunState.CURRENT_ENGINE_VERSION})"
    }
    require(run.contentVersion in 2..RunState.CURRENT_CONTENT_VERSION) {
      "Unsupported snapshot contentVersion=${run.contentVersion} (supported 2..${RunState.CURRENT_CONTENT_VERSION})"
    }
    require(run.currentAct in 1..3) { "Invalid currentAct=${run.currentAct}" }
    require(run.maxHp > 0 && run.hp in 0..run.maxHp) { "Invalid HP bounds: ${run.hp}/${run.maxHp}" }
    require(run.maxFlasks > 0 && run.flasks in 0..run.maxFlasks) { "Invalid Flask bounds: ${run.flasks}/${run.maxFlasks}" }
    require(run.cinders >= 0) { "Negative cinders=${run.cinders}" }
    require(run.dreadLevel in 0..3) { "Invalid dreadLevel=${run.dreadLevel}" }
    require(run.dreadConsecutiveAshTurns >= 0) { "Invalid dreadConsecutiveAshTurns=${run.dreadConsecutiveAshTurns}" }
    require(CampaignGenerator.validateMap(run.actMap)) { "Invalid ActMap graph in snapshot" }
    require(run.currentNodeId == null || run.actMap.nodes.any { it.id == run.currentNodeId }) {
      "currentNodeId=${run.currentNodeId} not found in actMap"
    }

    val masterUids = HashSet<Long>(run.masterDeck.size)
    run.masterDeck.forEach { card ->
      require(masterUids.add(card.uid)) { "Duplicate card uid=${card.uid} in masterDeck" }
      require(runCatching { GameCatalog.cardById(card.defId) }.isSuccess) {
        "Unknown card defId=${card.defId} in masterDeck"
      }
    }
    run.relics.forEach { relicId ->
      require(runCatching { GameCatalog.relicById(relicId) }.isSuccess) {
        "Unknown relic id=$relicId in snapshot"
      }
    }
    run.remnants.forEach { remnantId ->
      require(runCatching { GameCatalog.remnantById(remnantId) }.isSuccess) {
        "Unknown remnant id=$remnantId in snapshot"
      }
    }
    run.pendingReward?.let { reward ->
      reward.cardChoices.forEach { cid ->
        require(runCatching { GameCatalog.cardById(cid) }.isSuccess) {
          "Unknown reward card id=$cid in snapshot"
        }
      }
      reward.relicRewardId?.let { rid ->
        require(runCatching { GameCatalog.relicById(rid) }.isSuccess) {
          "Unknown reward relic id=$rid in snapshot"
        }
      }
      reward.remnantRewardId?.let { rid ->
        require(runCatching { GameCatalog.remnantById(rid) }.isSuccess) {
          "Unknown reward remnant id=$rid in snapshot"
        }
      }
    }
    run.shopStock?.let { shop ->
      shop.cardIds.forEach { cid ->
        require(runCatching { GameCatalog.cardById(cid) }.isSuccess) {
          "Unknown shop card id=$cid in snapshot"
        }
      }
      shop.relicIds.forEach { rid ->
        require(runCatching { GameCatalog.relicById(rid) }.isSuccess) {
          "Unknown shop relic id=$rid in snapshot"
        }
      }
    }

    run.combat?.let { combat ->
      val board = combat.board
      require(board.rows == BoardEngine.ROWS && board.cols == BoardEngine.COLS) {
        "Invalid board dimensions ${board.rows}x${board.cols}"
      }
      require(board.grid.size == board.rows && board.grid.all { it.size == board.cols }) {
        "Board grid shape does not match ${board.rows}x${board.cols}"
      }
      require(board.incomingRow.size == board.cols) {
        "Incoming row length ${board.incomingRow.size} != ${board.cols}"
      }
      board.sealedColumns.forEach { (col, turns) ->
        require(col in 0 until board.cols && turns > 0) {
          "Invalid sealed column entry col=$col turns=$turns"
        }
      }
      require(combat.resources.spark >= 0 && combat.resources.inkPools.values.all { it >= 0 }) {
        "Negative combat resource value in snapshot"
      }
      require(combat.enemy.maxHp > 0 && combat.enemy.hp in 0..combat.enemy.maxHp && combat.enemy.block >= 0) {
        "Invalid enemy HP/block in snapshot"
      }
      val allCombatCards = combat.combatDeck.drawPile +
        combat.combatDeck.hand +
        combat.combatDeck.discardPile +
        combat.combatDeck.exhaustPile
      val combatUids = HashSet<Long>(allCombatCards.size)
      allCombatCards.forEach { card ->
        require(combatUids.add(card.uid)) { "Duplicate card uid=${card.uid} in combatDeck" }
        require(runCatching { GameCatalog.cardById(card.defId) }.isSuccess) {
          "Unknown card defId=${card.defId} in combatDeck"
        }
      }
    }
  }

  private fun migrateV2ToV3(v2: JSONObject): JSONObject {
    val migrated = JSONObject(v2.toString())
    migrated.put("schemaVersion", RunState.CURRENT_SCHEMA_VERSION)
    val hasShop = !migrated.isNull("shopStock")
    val hasReward = !migrated.isNull("pendingReward")
    val hasCombat = !migrated.isNull("combat")
    val hasActiveEvent = !migrated.isNull("activeEvent")
    val currentNodeId = if (migrated.isNull("currentNodeId")) null else migrated.optString("currentNodeId", "")
    val currentNodeType = findNodeTypeInJson(migrated.optJSONObject("actMap"), currentNodeId)
    val leftCurrentNode = lastCommandInJson(migrated) == "LEAVE_NODE"

    // EFB-007 + EFB-027: Mark inNonCombatNode=true for active non-combat nodes (Merchant, Event,
    // or Campfire both before and after Rest/Upgrade) unless the player has already executed
    // LEAVE_NODE or transitioned to a non-campfire/combat node. Never mark completed COMBAT /
    // ELITE / BOSS / SHADE / TREASURE nodes as inNonCombatNode.
    val inferredNonCombat = !hasCombat && !hasReward && !leftCurrentNode && (
      hasShop ||
        hasActiveEvent ||
        currentNodeType == MapNodeType.CAMPFIRE ||
        currentNodeType == MapNodeType.MERCHANT ||
        currentNodeType == MapNodeType.EVENT
      )
    migrated.put("inNonCombatNode", inferredNonCombat)
    migrated.put("dreadConsecutiveAshTurns", 0)
    if (!migrated.has("activeEvent")) {
      migrated.put("activeEvent", JSONObject.NULL)
    }
    if (!migrated.isNull("combat")) {
      val combatObj = migrated.getJSONObject("combat")
      val playerObj = combatObj.getJSONObject("player")
      if (!playerObj.has("fogTapsRemaining")) {
        playerObj.put("fogTapsRemaining", playerObj.optInt("fogStacks", 0))
      }
      val boardObj = combatObj.getJSONObject("board")
      if (!boardObj.has("sealedColumns")) {
        boardObj.put("sealedColumns", JSONObject())
      }
      combatObj.put("ashPoppedThisTurn", false)
      combatObj.put("nonAshPoppedThisTurn", false)
      combatObj.put("cardsPlayedThisTurn", 0)
      combatObj.put("flaskUsedThisTurn", false)
      combatObj.put("overflowOccurredThisTurn", false)
      combatObj.put("vigilSpentThisTurn", false)
    }
    return migrated
  }

  private fun lastCommandInJson(root: JSONObject): String? {
    val log = root.optJSONArray("commandLog") ?: return null
    if (log.length() == 0) return null
    return log.optString(log.length() - 1, "").ifBlank { null }
  }

  private fun findNodeTypeInJson(actMapObj: JSONObject?, nodeId: String?): MapNodeType? {
    if (actMapObj == null || nodeId.isNullOrBlank()) return null
    val nodes = actMapObj.optJSONArray("nodes") ?: return null
    for (i in 0 until nodes.length()) {
      val nodeObj = nodes.optJSONObject(i) ?: continue
      if (nodeObj.optString("id") == nodeId) {
        return runCatching { MapNodeType.valueOf(nodeObj.optString("type")) }.getOrNull()
      }
    }
    return null
  }

  private fun encodeTileKind(kind: TileKind): String = when (kind) {
    is TileKind.Playable -> "HUE:${kind.hue.name}"
    TileKind.Ash -> "ASH"
    TileKind.Stone -> "STONE"
    TileKind.Prism -> "PRISM"
    TileKind.Empty -> "EMPTY"
  }

  private fun decodeTileKind(raw: String): TileKind = when {
    raw.startsWith("HUE:") -> {
      val hueName = raw.removePrefix("HUE:")
      runCatching { TileKind.Playable(Hue.valueOf(hueName)) }
        .getOrElse { throw IllegalArgumentException("Unknown playable hue in snapshot: $raw", it) }
    }
    raw == "ASH" -> TileKind.Ash
    raw == "STONE" -> TileKind.Stone
    raw == "PRISM" -> TileKind.Prism
    raw == "EMPTY" -> TileKind.Empty
    else -> throw IllegalArgumentException("Unknown TileKind in snapshot: $raw")
  }

  private fun encodeCards(cards: List<CardInstance>): JSONArray = JSONArray().apply {
    cards.forEach { card ->
      put(JSONObject().apply {
        put("uid", card.uid)
        put("defId", card.defId)
        put("upgraded", card.upgraded)
      })
    }
  }

  private fun decodeCards(arr: JSONArray): List<CardInstance> = List(arr.length()) { i ->
    val obj = arr.getJSONObject(i)
    CardInstance(
      uid = obj.getLong("uid"),
      defId = obj.getString("defId"),
      upgraded = obj.getBoolean("upgraded"),
    )
  }

  private fun decodeStringList(arr: JSONArray): List<String> = List(arr.length()) { arr.getString(it) }

  private fun encodeActMap(map: ActMap): JSONObject = JSONObject().apply {
    put("act", map.act)
    put("nodes", JSONArray().apply {
      map.nodes.forEach { n ->
        put(JSONObject().apply {
          put("id", n.id)
          put("act", n.act)
          put("row", n.row)
          put("col", n.col)
          put("type", n.type.name)
          put("nextNodeIds", JSONArray(n.nextNodeIds))
          put("visited", n.visited)
        })
      }
    })
  }

  private fun decodeActMap(obj: JSONObject): ActMap {
    val arr = obj.getJSONArray("nodes")
    val nodes = List(arr.length()) { i ->
      val n = arr.getJSONObject(i)
      MapNode(
        id = n.getString("id"),
        act = n.getInt("act"),
        row = n.getInt("row"),
        col = n.getInt("col"),
        type = MapNodeType.valueOf(n.getString("type")),
        nextNodeIds = decodeStringList(n.getJSONArray("nextNodeIds")),
        visited = n.getBoolean("visited"),
      )
    }
    return ActMap(act = obj.getInt("act"), nodes = nodes)
  }

  private fun encodeStats(s: RunStats): JSONObject = JSONObject().apply {
    put("turnsTaken", s.turnsTaken)
    put("enemiesSlain", s.enemiesSlain)
    put("highestSpectrumHalfSteps", s.highestSpectrumHalfSteps)
    put("prismsDetonated", s.prismsDetonated)
    put("ashBlocksPopped", s.ashBlocksPopped)
    put("stonesShattered", s.stonesShattered)
    put("cardsPlayed", s.cardsPlayed)
    put("wallOverflowsTriggered", s.wallOverflowsTriggered)
  }

  private fun decodeStats(o: JSONObject): RunStats = RunStats(
    turnsTaken = o.getInt("turnsTaken"),
    enemiesSlain = o.getInt("enemiesSlain"),
    highestSpectrumHalfSteps = o.getInt("highestSpectrumHalfSteps"),
    prismsDetonated = o.getInt("prismsDetonated"),
    ashBlocksPopped = o.optInt("ashBlocksPopped", 0),
    stonesShattered = o.optInt("stonesShattered", 0),
    cardsPlayed = o.getInt("cardsPlayed"),
    wallOverflowsTriggered = o.optInt("wallOverflowsTriggered", 0),
  )

  private fun encodeCombat(c: CombatState): JSONObject = JSONObject().apply {
    put("nodeId", c.nodeId)
    put("highestSpectrumThisCombat", c.highestSpectrumThisCombat)
    put("wallOverflowsTriggered", c.wallOverflowsTriggered)
    put("ashPoppedThisTurn", c.ashPoppedThisTurn)
    put("nonAshPoppedThisTurn", c.nonAshPoppedThisTurn)
    put("cardsPlayedThisTurn", c.cardsPlayedThisTurn)
    put("flaskUsedThisTurn", c.flaskUsedThisTurn)
    put("overflowOccurredThisTurn", c.overflowOccurredThisTurn)
    put("vigilSpentThisTurn", c.vigilSpentThisTurn)
    put("arenaTwist", c.arenaTwist.name)
    put("turn", JSONObject().apply {
      put("turnNumber", c.turn.turnNumber)
      put("phase", c.turn.phase.name)
      put("tapsRemaining", c.turn.tapsRemaining)
      put("firstTapUsedThisTurn", c.turn.firstTapUsedThisTurn)
      put("skipWallRiseThisTurn", c.turn.skipWallRiseThisTurn)
      put("bonusMovesGrantedThisTurn", c.turn.bonusMovesGrantedThisTurn)
      put("comboMultTenths", c.turn.comboMultTenths)
      put("lastMatchedHue", c.turn.lastMatchedHue?.name ?: JSONObject.NULL)
      put("lastCardHue", c.turn.lastCardHue?.name ?: JSONObject.NULL)
      put("sameHueCardStreak", c.turn.sameHueCardStreak)
      put("cascadesThisTurn", c.turn.cascadesThisTurn)
      put("overpressureMultTenths", c.turn.overpressureMultTenths)
      put("movesMadeThisTurn", c.turn.movesMadeThisTurn)
    })
    put("resources", JSONObject().apply {
      put("spark", c.resources.spark)
      put("inkPools", JSONObject().apply {
        c.resources.inkPools.forEach { (h, amount) -> put(h.name, amount) }
      })
    })
    put("spectrum", JSONObject().apply {
      put("currentHue", c.spectrum.currentHue?.name ?: JSONObject.NULL)
      put("runLength", c.spectrum.runLength)
      put("halfSteps", c.spectrum.halfSteps)
    })
    put("player", JSONObject().apply {
      put("guardBlock", c.player.guardBlock)
      put("vigil", c.player.vigil)
      put("fogTapsRemaining", c.player.fogTapsRemaining)
      put("chiselWandUsedThisCombat", c.player.chiselWandUsedThisCombat)
      put("firstPrismInkUsedThisCombat", c.player.firstPrismInkUsedThisCombat)
      put("firstBrandNegatedThisTurn", c.player.firstBrandNegatedThisTurn)
      put("spectrumAstrolabeTriggeredThisTurn", c.player.spectrumAstrolabeTriggeredThisTurn)
      put("overdriveCharge", c.player.overdriveCharge)
    })
    put("board", JSONObject().apply {
      put("rows", c.board.rows)
      put("cols", c.board.cols)
      put("nextTileId", c.board.nextTileId)
      put("sealedColumns", JSONObject().apply {
        c.board.sealedColumns.forEach { (col, turns) -> put(col.toString(), turns) }
      })
      put("grid", JSONArray().apply {
        c.board.grid.forEach { row ->
          put(JSONArray().apply {
            row.forEach { t ->
              put(JSONObject().apply {
                put("id", t.id)
                put("kind", encodeTileKind(t.kind))
                put("isBranded", t.isBranded)
              })
            }
          })
        }
      })
      put("incomingRow", JSONArray().apply {
        c.board.incomingRow.forEach { t ->
          put(JSONObject().apply {
            put("id", t.id)
            put("kind", encodeTileKind(t.kind))
            put("isBranded", t.isBranded)
          })
        }
      })
    })
    put("paintDeck", JSONObject().apply {
      put("drawBag", JSONArray(c.paintDeck.drawBag.map { encodeTileKind(it) }))
      put("discardBag", JSONArray(c.paintDeck.discardBag.map { encodeTileKind(it) }))
      put("hueWeightsBp", JSONObject().apply {
        c.paintDeck.hueWeightsBp.forEach { (h, w) -> put(h.name, w) }
      })
    })
    put("combatDeck", JSONObject().apply {
      put("drawPile", encodeCards(c.combatDeck.drawPile))
      put("hand", encodeCards(c.combatDeck.hand))
      put("discardPile", encodeCards(c.combatDeck.discardPile))
      put("exhaustPile", encodeCards(c.combatDeck.exhaustPile))
    })
    put("enemy", JSONObject().apply {
      put("defId", c.enemy.defId)
      put("name", c.enemy.name)
      put("act", c.enemy.act)
      put("hp", c.enemy.hp)
      put("maxHp", c.enemy.maxHp)
      put("block", c.enemy.block)
      put("isElite", c.enemy.isElite)
      put("isBoss", c.enemy.isBoss)
      put("isShade", c.enemy.isShade)
      put("patternStep", c.enemy.patternStep)
      put("shadeCardIds", JSONArray(c.enemy.shadeCardIds))
      put("shadeVigorBonus", c.enemy.shadeVigorBonus)
    })
    put("mindIllusion", JSONObject().apply {
      put("illusion", c.mindIllusion.illusion.name)
      put("attachment", c.mindIllusion.attachment)
      put("clarity", c.mindIllusion.clarity)
      put("veilLayers", c.mindIllusion.veilLayers)
      put("shattered", c.mindIllusion.shattered)
      put("witnessUsedThisTurn", c.mindIllusion.witnessUsedThisTurn)
      put("detachedBonusSpanda", c.mindIllusion.detachedBonusSpanda)
    })
  }

  private fun decodeCombat(o: JSONObject): CombatState {
    val boardObj = o.getJSONObject("board")
    val gridArr = boardObj.getJSONArray("grid")
    val grid = List(gridArr.length()) { r ->
      val rowArr = gridArr.getJSONArray(r)
      List(rowArr.length()) { c ->
        val t = rowArr.getJSONObject(c)
        Tile(
          id = t.getLong("id"),
          kind = decodeTileKind(t.getString("kind")),
          isBranded = t.optBoolean("isBranded", false),
        )
      }
    }
    val incArr = boardObj.getJSONArray("incomingRow")
    val incomingRow = List(incArr.length()) { c ->
      val t = incArr.getJSONObject(c)
      Tile(
        id = t.getLong("id"),
        kind = decodeTileKind(t.getString("kind")),
        isBranded = t.optBoolean("isBranded", false),
      )
    }
    val sealedColsObj = boardObj.optJSONObject("sealedColumns")
    val sealedCols = buildMap<Int, Int> {
      if (sealedColsObj != null) {
        val keys = sealedColsObj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          put(k.toInt(), sealedColsObj.getInt(k))
        }
      }
    }
    val board = BoardState(
      rows = boardObj.getInt("rows"),
      cols = boardObj.getInt("cols"),
      grid = grid,
      incomingRow = incomingRow,
      sealedColumns = sealedCols,
      nextTileId = boardObj.getLong("nextTileId"),
    )

    val pdObj = o.getJSONObject("paintDeck")
    val weightsObj = pdObj.getJSONObject("hueWeightsBp")
    val paintDeck = BoardPaintDeckState(
      drawBag = decodeStringList(pdObj.getJSONArray("drawBag")).map { decodeTileKind(it) },
      discardBag = decodeStringList(pdObj.getJSONArray("discardBag")).map { decodeTileKind(it) },
      hueWeightsBp = Hue.entries.associateWith { weightsObj.optInt(it.name, 2000) },
    )

    val cdObj = o.getJSONObject("combatDeck")
    val combatDeck = DeckState(
      drawPile = decodeCards(cdObj.getJSONArray("drawPile")),
      hand = decodeCards(cdObj.getJSONArray("hand")),
      discardPile = decodeCards(cdObj.getJSONArray("discardPile")),
      exhaustPile = decodeCards(cdObj.getJSONArray("exhaustPile")),
    )

    val eObj = o.getJSONObject("enemy")
    val defId = eObj.getString("defId")
    val act = eObj.getInt("act")
    val patternStep = eObj.getInt("patternStep")
    val isShade = eObj.getBoolean("isShade")
    val shadeCardIds = decodeStringList(eObj.getJSONArray("shadeCardIds"))
    val shadeVigorBonus = eObj.optInt("shadeVigorBonus", 0)
    val def = runCatching { GameCatalog.enemyById(defId) }
      .getOrElse { throw IllegalArgumentException("Unknown enemy defId=$defId in snapshot", it) }
    val (slot1Pat, slot2Pat) = if (isShade) {
      CampaignGenerator.buildShadeIntentsFromDeck(shadeCardIds, act, shadeVigorBonus)
    } else {
      def.slot1Pattern to def.slot2Pattern
    }
    val enemy = EnemyState(
      defId = defId,
      name = eObj.getString("name"),
      act = act,
      hp = eObj.getInt("hp"),
      maxHp = eObj.getInt("maxHp"),
      block = eObj.getInt("block"),
      isElite = eObj.getBoolean("isElite"),
      isBoss = eObj.getBoolean("isBoss"),
      isShade = isShade,
      patternStep = patternStep,
      slot1Intent = slot1Pat[patternStep % slot1Pat.size],
      slot2Intent = slot2Pat[patternStep % slot2Pat.size],
      shadeCardIds = shadeCardIds,
      shadeVigorBonus = shadeVigorBonus,
    )

    val turnObj = o.getJSONObject("turn")
    val turn = TurnState(
      turnNumber = turnObj.getInt("turnNumber"),
      phase = TurnPhase.valueOf(turnObj.getString("phase")),
      tapsRemaining = turnObj.getInt("tapsRemaining"),
      firstTapUsedThisTurn = turnObj.getBoolean("firstTapUsedThisTurn"),
      skipWallRiseThisTurn = turnObj.getBoolean("skipWallRiseThisTurn"),
      bonusMovesGrantedThisTurn = turnObj.optInt("bonusMovesGrantedThisTurn", 0),
      comboMultTenths = turnObj.optInt("comboMultTenths", TurnState.BASE_MULT_TENTHS),
      lastMatchedHue = if (!turnObj.has("lastMatchedHue") || turnObj.isNull("lastMatchedHue")) null else Hue.valueOf(turnObj.getString("lastMatchedHue")),
      lastCardHue = if (!turnObj.has("lastCardHue") || turnObj.isNull("lastCardHue")) null else Hue.valueOf(turnObj.getString("lastCardHue")),
      sameHueCardStreak = turnObj.optInt("sameHueCardStreak", 0),
      cascadesThisTurn = turnObj.optInt("cascadesThisTurn", 0),
      overpressureMultTenths = turnObj.optInt("overpressureMultTenths", 0),
      movesMadeThisTurn = turnObj.optInt("movesMadeThisTurn", 0),
    )

    val resObj = o.getJSONObject("resources")
    val poolsObj = resObj.getJSONObject("inkPools")
    val resources = ResourceState(
      inkPools = Hue.entries.associateWith { poolsObj.optInt(it.name, 0) },
      spark = resObj.getInt("spark"),
    )

    val specObj = o.getJSONObject("spectrum")
    val spectrum = SpectrumState(
      currentHue = if (specObj.isNull("currentHue")) null else Hue.valueOf(specObj.getString("currentHue")),
      runLength = specObj.getInt("runLength"),
      halfSteps = specObj.getInt("halfSteps"),
    )

    val plObj = o.getJSONObject("player")
    val player = PlayerCombatState(
      guardBlock = plObj.getInt("guardBlock"),
      vigil = plObj.getInt("vigil"),
      fogTapsRemaining = plObj.optInt("fogTapsRemaining", plObj.optInt("fogStacks", 0)),
      chiselWandUsedThisCombat = plObj.getBoolean("chiselWandUsedThisCombat"),
      firstPrismInkUsedThisCombat = plObj.optBoolean("firstPrismInkUsedThisCombat", false),
      firstBrandNegatedThisTurn = plObj.optBoolean("firstBrandNegatedThisTurn", false),
      spectrumAstrolabeTriggeredThisTurn = plObj.optBoolean("spectrumAstrolabeTriggeredThisTurn", false),
      overdriveCharge = plObj.optInt("overdriveCharge", 0).coerceIn(0, PlayerCombatState.MAX_OVERDRIVE),
    )

    val arenaTwist = o.optString("arenaTwist", ArenaTwist.NONE.name)
      .let { raw -> runCatching { ArenaTwist.valueOf(raw) }.getOrDefault(ArenaTwist.NONE) }

    val mindObj = o.optJSONObject("mindIllusion")
    val mindIllusion = if (mindObj != null) {
      val ill = runCatching { EmotionalIllusion.valueOf(mindObj.optString("illusion", EmotionalIllusion.KRODHA_WRATH.name)) }
        .getOrDefault(EmotionalIllusion.KRODHA_WRATH)
      MindIllusionState(
        illusion = ill,
        attachment = mindObj.optInt("attachment", 0).coerceIn(0, MindIllusionState.MAX_ATTACHMENT),
        clarity = mindObj.optInt("clarity", 0).coerceAtLeast(0),
        veilLayers = mindObj.optInt("veilLayers", MindIllusionState.MAX_VEIL_LAYERS).coerceIn(0, MindIllusionState.MAX_VEIL_LAYERS),
        shattered = mindObj.optBoolean("shattered", false),
        witnessUsedThisTurn = mindObj.optBoolean("witnessUsedThisTurn", false),
        detachedBonusSpanda = mindObj.optInt("detachedBonusSpanda", 0).coerceAtLeast(0),
      )
    } else {
      MindIllusionState()
    }

    return CombatState(
      nodeId = o.getString("nodeId"),
      board = board,
      paintDeck = paintDeck,
      combatDeck = combatDeck,
      enemy = enemy,
      turn = turn,
      resources = resources,
      spectrum = spectrum,
      player = player,
      lastEvents = emptyList(),
      highestSpectrumThisCombat = o.getInt("highestSpectrumThisCombat"),
      wallOverflowsTriggered = o.optInt("wallOverflowsTriggered", 0),
      ashPoppedThisTurn = o.optBoolean("ashPoppedThisTurn", false),
      nonAshPoppedThisTurn = o.optBoolean("nonAshPoppedThisTurn", false),
      cardsPlayedThisTurn = o.optInt("cardsPlayedThisTurn", 0),
      flaskUsedThisTurn = o.optBoolean("flaskUsedThisTurn", false),
      overflowOccurredThisTurn = o.optBoolean("overflowOccurredThisTurn", false),
      vigilSpentThisTurn = o.optBoolean("vigilSpentThisTurn", false),
      arenaTwist = arenaTwist,
      mindIllusion = mindIllusion,
    )
  }

  private fun encodeGrowth(g: CharacterGrowthState): JSONObject = JSONObject().apply {
    put("level", g.level)
    put("xp", g.xp)
    put("xpToNextLevel", g.xpToNextLevel)
    put("unspentPassivePoints", g.unspentPassivePoints)
    put("unlockedPassiveIds", JSONArray(g.unlockedPassiveIds))
    put("socketedSupportIds", JSONArray(g.socketedSupportIds))
    put("tantricArtifactIds", JSONArray(g.tantricArtifactIds))
    put("maxSockets", g.maxSockets)
  }

  private fun decodeGrowth(o: JSONObject): CharacterGrowthState {
    val level = o.optInt("level", 1).coerceAtLeast(1)
    return CharacterGrowthState(
      level = level,
      xp = o.optInt("xp", 0).coerceAtLeast(0),
      xpToNextLevel = o.optInt("xpToNextLevel", CharacterGrowthState.xpThresholdForLevel(level)).coerceAtLeast(1),
      unspentPassivePoints = o.optInt("unspentPassivePoints", 0).coerceAtLeast(0),
      unlockedPassiveIds = o.optJSONArray("unlockedPassiveIds")?.let { decodeStringList(it) } ?: emptyList(),
      socketedSupportIds = o.optJSONArray("socketedSupportIds")?.let { decodeStringList(it) } ?: emptyList(),
      tantricArtifactIds = o.optJSONArray("tantricArtifactIds")?.let { decodeStringList(it) } ?: emptyList(),
      maxSockets = o.optInt("maxSockets", CharacterGrowthState.socketsForLevel(level)).coerceIn(1, CharacterGrowthState.MAX_POSSIBLE_SOCKETS),
    )
  }

  private fun encodeReward(r: RewardBundle): JSONObject = JSONObject().apply {
    put("cindersEarned", r.cindersEarned)
    put("cardChoices", JSONArray(r.cardChoices))
    put("relicRewardId", r.relicRewardId ?: JSONObject.NULL)
    put("remnantRewardId", r.remnantRewardId ?: JSONObject.NULL)
    put("flaskRefill", r.flaskRefill)
    put("cardPicked", r.cardPicked)
    put("relicPicked", r.relicPicked)
    put("rerollUsed", r.rerollUsed)
    put("xpEarned", r.xpEarned)
    put("leveledUp", r.leveledUp)
    put("unlockedNewSocket", r.unlockedNewSocket)
    put("supportChoices", JSONArray(r.supportChoices))
    put("supportPicked", r.supportPicked)
    put("artifactChoices", JSONArray(r.artifactChoices))
    put("artifactPicked", r.artifactPicked)
  }

  private fun decodeReward(o: JSONObject): RewardBundle = RewardBundle(
    cindersEarned = o.getInt("cindersEarned"),
    cardChoices = decodeStringList(o.getJSONArray("cardChoices")),
    relicRewardId = if (o.isNull("relicRewardId")) null else o.getString("relicRewardId"),
    remnantRewardId = if (o.isNull("remnantRewardId")) null else o.getString("remnantRewardId"),
    flaskRefill = o.getInt("flaskRefill"),
    cardPicked = o.getBoolean("cardPicked"),
    relicPicked = o.getBoolean("relicPicked"),
    rerollUsed = o.optBoolean("rerollUsed", false),
    xpEarned = o.optInt("xpEarned", 0),
    leveledUp = o.optBoolean("leveledUp", false),
    unlockedNewSocket = o.optBoolean("unlockedNewSocket", false),
    supportChoices = o.optJSONArray("supportChoices")?.let { decodeStringList(it) } ?: emptyList(),
    supportPicked = o.optBoolean("supportPicked", true),
    artifactChoices = o.optJSONArray("artifactChoices")?.let { decodeStringList(it) } ?: emptyList(),
    artifactPicked = o.optBoolean("artifactPicked", true),
  )

  private fun encodeShop(s: ShopStock): JSONObject = JSONObject().apply {
    put("cardIds", JSONArray(s.cardIds))
    put("relicIds", JSONArray(s.relicIds))
    put("flaskPrice", s.flaskPrice)
    put("removePrice", s.removePrice)
    put("flaskPurchased", s.flaskPurchased)
    put("removeUsed", s.removeUsed)
  }

  private fun decodeShop(o: JSONObject): ShopStock = ShopStock(
    cardIds = decodeStringList(o.getJSONArray("cardIds")),
    relicIds = decodeStringList(o.getJSONArray("relicIds")),
    flaskPrice = o.getInt("flaskPrice"),
    removePrice = o.getInt("removePrice"),
    flaskPurchased = o.getBoolean("flaskPurchased"),
    removeUsed = o.getBoolean("removeUsed"),
  )

  private fun encodeEvent(e: EventState): JSONObject = JSONObject().apply {
    put("eventId", e.eventId)
    put("resolvedSummary", e.resolvedSummary ?: JSONObject.NULL)
  }

  private fun decodeEvent(o: JSONObject): EventState {
    val eventId = o.getString("eventId")
    val def = runCatching { GameCatalog.eventById(eventId) }
      .getOrElse { throw IllegalArgumentException("Unknown eventId=$eventId in snapshot", it) }
    return EventState(
      eventId = def.id,
      title = def.title,
      body = def.body,
      choices = def.choices,
      resolvedSummary = if (o.isNull("resolvedSummary")) null else o.getString("resolvedSummary"),
    )
  }
}
