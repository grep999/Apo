package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.*
import com.example.ui.components.CardPillView
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ActMapScreen(
  run: RunState,
  onSelectNode: (String) -> Unit,
  onExitToTitle: () -> Unit,
) {
  val availableIds = remember(run) { GameEngine.availableNodeIds(run) }
  var showDeckModal by remember { mutableStateOf(false) }
  var showMapKeyModal by remember { mutableStateOf(false) }
  var inspectedRelic by remember { mutableStateOf<RelicDef?>(null) }
  var inspectedRemnant by remember { mutableStateOf<RemnantDef?>(null) }
  var inspectedNode by remember { mutableStateOf<MapNode?>(null) }

  val actName = when (run.currentAct) {
    1 -> "Act I: The Scorched Threshold"
    2 -> "Act II: The Obsidian Crucible"
    else -> "Act III: The Ember Throne"
  }
  val actAtmosphereTint = when (run.currentAct) {
    1 -> Color(0xFF4A2E14) // Warm antique ochre-ember parchment glow
    2 -> Color(0xFF2E211A) // Deep smoked crucible bronze
    else -> Color(0xFF3A1A22) // Imperial twilight crimson-gold
  }

  val rows = remember(run.actMap) {
    run.actMap.nodes.groupBy { it.row }.toSortedMap(reverseOrder())
  }
  val nodesById = remember(run.actMap) {
    run.actMap.nodes.associateBy { it.id }
  }
  val orderedRowKeys = remember(rows) { rows.keys.toList() }
  val targetRow = remember(run, availableIds) {
    run.actMap.nodes.firstOrNull { it.id in availableIds }?.row ?: 1
  }
  val clearedCount = remember(run.actMap) {
    run.actMap.nodes.count { it.visited }
  }
  val initialIndex = remember(orderedRowKeys, targetRow) {
    orderedRowKeys.indexOf(targetRow).coerceAtLeast(0)
  }
  val mapListState = rememberLazyListState(
    initialFirstVisibleItemIndex = initialIndex,
  )
  LaunchedEffect(targetRow, run.currentAct) {
    val idx = orderedRowKeys.indexOf(targetRow)
    if (idx >= 0) {
      mapListState.animateScrollToItem(idx)
    }
  }

  val pulseTransition = rememberInfiniteTransition(label = "map_beacon_pulse")
  val beaconPulse by pulseTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "beacon_scale",
  )

  val reachableNodes = remember(run.actMap, availableIds) {
    run.actMap.nodes.filter { it.id in availableIds }.sortedBy { it.col }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid),
  ) {
    // 1. Full-bleed illustrated antique parchment world map (high clarity, soft edge vignette only!)
    Image(
      painter = painterResource(id = R.drawable.img_world_map_parchment),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alpha = 0.88f,
      modifier = Modifier.fillMaxSize(),
    )
    // Gentle Act-specific atmospheric wash + top/bottom legibility vignette (leaves center world clear!)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.0f to ObsidianVoid.copy(alpha = 0.78f),
              0.14f to actAtmosphereTint.copy(alpha = 0.30f),
              0.50f to actAtmosphereTint.copy(alpha = 0.18f),
              0.84f to actAtmosphereTint.copy(alpha = 0.34f),
              1.0f to ObsidianVoid.copy(alpha = 0.82f),
            ),
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      // 2. Slim Floating Expedition Header (replaces 4 previously stacked top bars!)
      Surface(
        color = ObsidianVoid.copy(alpha = 0.84f),
        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
        border = BorderStroke(1.dp, KilnBrass.copy(alpha = 0.45f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("act_map_header"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconButton(
              onClick = onExitToTitle,
              modifier = Modifier
                .size(44.dp)
                .testTag("map_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Return to Title",
                tint = WarmParchment,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = actName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = GildedBrass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                MapVitalChip("HP ${run.hp}/${run.maxHp}", CrimsonRuin)
                MapVitalChip("${run.cinders}C", GildedBrass)
                MapVitalChip("FL ${run.flasks}/${run.maxFlasks}", VerdantHeal)
                MapVitalChip("R$targetRow/15", MoltenEmber)
              }
            }
            IconButton(
              onClick = { showMapKeyModal = true },
              modifier = Modifier
                .size(42.dp)
                .testTag("map_key_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = "Map Legend & Relics",
                tint = GildedBrass,
                modifier = Modifier.size(20.dp),
              )
            }
            OutlinedButton(
              onClick = { showDeckModal = true },
              modifier = Modifier
                .height(38.dp)
                .testTag("view_deck_button"),
              border = BorderStroke(1.dp, KilnBrass.copy(alpha = 0.7f)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              shape = RoundedCornerShape(9.dp),
            ) {
              Text(
                text = "Deck (${run.masterDeck.size})",
                fontSize = 11.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.Bold,
                color = WarmParchment,
              )
            }
          }

          // Compact Relics & Shade Remnants ribbon (only rendered when player owns relics/remnants)
          if (run.relics.isNotEmpty() || run.remnants.isNotEmpty()) {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              items(run.relics) { relicId ->
                val relic = GameCatalog.relicById(relicId)
                Surface(
                  onClick = { inspectedRelic = relic },
                  color = CharredBasalt.copy(alpha = 0.90f),
                  shape = RoundedCornerShape(7.dp),
                  border = BorderStroke(1.dp, GildedBrass.copy(alpha = 0.6f)),
                ) {
                  Text(
                    text = "✦ ${relic.name}",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontFamily = IbmPlexMonoFamily,
                    color = GildedBrass,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                  )
                }
              }
              items(run.remnants) { remnantId ->
                val remnant = GameCatalog.remnantById(remnantId)
                Surface(
                  onClick = { inspectedRemnant = remnant },
                  color = CharredBasalt.copy(alpha = 0.90f),
                  shape = RoundedCornerShape(7.dp),
                  border = BorderStroke(1.dp, HueViolet.copy(alpha = 0.8f)),
                  modifier = Modifier.testTag("remnant_chip_${remnant.id}"),
                ) {
                  Text(
                    text = "☾ ${remnant.name}",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontFamily = IbmPlexMonoFamily,
                    color = HueViolet,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                  )
                }
              }
            }
          }
        }
      }

      // 3. Winding Illustrated World Pilgrimage Canvas (occupies >=75% of screen height!)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("act_map_canvas"),
      ) {
        LazyColumn(
          state = mapListState,
          modifier = Modifier
            .fillMaxSize()
            .testTag("act_map_list"),
          contentPadding = PaddingValues(
            top = 12.dp,
            bottom = if (inspectedNode != null) 128.dp else 18.dp,
          ),
        ) {
          rows.forEach { (rowIdx, rowNodesRaw) ->
            val upperNodes = rowNodesRaw.sortedBy { it.col }
            val lowerNodes = rows[rowIdx - 1]?.sortedBy { it.col }.orEmpty()
            val zoneBanner = biomeZoneBanner(act = run.currentAct, rowIdx = rowIdx)

            item(key = "row_$rowIdx") {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                // Engraved Biome / Elevation Milestone Cartouche (replaces flat "ROW 2 / ROW 3" clutter!)
                if (zoneBanner != null) {
                  MapElevationCartouche(
                    title = zoneBanner.first,
                    subtitle = zoneBanner.second,
                    accent = when (rowIdx) {
                      CampaignGenerator.ACT_ROW_COUNT -> CrimsonRuin
                      5 -> if (run.currentAct == 1) HueViolet else GildedBrass
                      else -> GildedBrass
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                  )
                }

                // Organic seed-offset landmark row aligned seamlessly with curved Bézier trail connectors
                BoxWithConstraints(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(if (rowIdx == CampaignGenerator.ACT_ROW_COUNT) 86.dp else 72.dp),
                ) {
                  val rowWidthPx = constraints.maxWidth.toFloat()
                  // Subtle engraved elevation tick on left margin for geographic grounding
                  Text(
                    text = if (rowIdx == targetRow) "▸$rowIdx" else "$rowIdx",
                    fontSize = 9.sp,
                    fontFamily = IbmPlexMonoFamily,
                    fontWeight = if (rowIdx == targetRow) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (rowIdx == targetRow) MoltenEmber else WarmParchment.copy(alpha = 0.55f),
                    modifier = Modifier
                      .align(Alignment.CenterStart)
                      .padding(start = 8.dp),
                  )

                  upperNodes.forEachIndexed { idx, node ->
                    val isAvailable = node.id in availableIds
                    val isCurrent = node.id == run.currentNodeId
                    val isInspected = inspectedNode?.id == node.id
                    val normX = nodeNormalizedX(
                      node = node,
                      indexInRow = idx,
                      rowCount = upperNodes.size,
                      runSeed = run.runSeed,
                    )
                    val medallionWidthDp = if (node.type == MapNodeType.BOSS) 116.dp else 88.dp

                    Box(
                      modifier = Modifier
                        .width(medallionWidthDp)
                        .offset {
                          val centerX = (normX * rowWidthPx).roundToInt()
                          val halfW = (medallionWidthDp.toPx() / 2f).roundToInt()
                          IntOffset(
                            x = (centerX - halfW).coerceIn(0, (rowWidthPx - halfW * 2).roundToInt().coerceAtLeast(0)),
                            y = 0,
                          )
                        }
                        .align(Alignment.CenterStart),
                      contentAlignment = Alignment.Center,
                    ) {
                      MapLandmarkMedallion(
                        node = node,
                        isAvailable = isAvailable,
                        isCurrent = isCurrent,
                        isInspected = isInspected,
                        beaconScale = if (isAvailable) beaconPulse else 1f,
                        onClick = {
                          if (isAvailable) {
                            inspectedNode = null
                            onSelectNode(node.id)
                          } else {
                            inspectedNode = if (isInspected) null else node
                          }
                        },
                      )
                    }
                  }
                }

                // Graceful curved pilgrimage trail connectors between organic node positions
                if (rowIdx > 1 && lowerNodes.isNotEmpty()) {
                  GraphPathConnector(
                    upperNodes = upperNodes,
                    lowerNodes = lowerNodes,
                    nodesById = nodesById,
                    currentNodeId = run.currentNodeId,
                    availableIds = availableIds,
                    runSeed = run.runSeed,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(42.dp),
                  )
                }
              }
            }
          }
        }

        // Tap-to-inspect Node Preview Tooltip Card
        inspectedNode?.let { node ->
          MapNodeTooltipCard(
            node = node,
            act = run.currentAct,
            isVisited = node.visited,
            isCurrent = node.id == run.currentNodeId,
            onDismiss = { inspectedNode = null },
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("map_node_tooltip"),
          )
        }
      }

      // 4. Compact Bottom-Docked Wayfarer Crossroads Strip (preserves 1-tap next room & keeps top of map clean!)
      if (reachableNodes.isNotEmpty()) {
        Surface(
          color = ObsidianVoid.copy(alpha = 0.88f),
          shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
          border = BorderStroke(1.dp, KilnBrass.copy(alpha = 0.55f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("next_crossroads_strip"),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.padding(end = 2.dp),
              verticalArrangement = Arrangement.Center,
            ) {
              Text(
                text = "NEXT",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = GildedBrass,
              )
              Text(
                text = "R${reachableNodes.first().row}",
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = WarmParchment,
              )
            }
            reachableNodes.forEach { node ->
              val (icon, tint) = nodeVisual(node.type)
              val twist = ArenaTwist.forNode(run.runSeed, node.id, node.type)
              val teaser = if (twist != ArenaTwist.NONE) twist.shortTag else node.type.label
              Surface(
                onClick = { onSelectNode(node.id) },
                color = CharredBasalt.copy(alpha = 0.96f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.4.dp, tint.copy(alpha = 0.9f)),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("crossroads_node_${node.id}"),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Icon(icon, contentDescription = node.type.label, tint = tint, modifier = Modifier.size(16.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = node.type.label,
                      fontSize = 11.sp,
                      lineHeight = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = WarmParchment,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = teaser,
                      fontSize = 8.sp,
                      lineHeight = 9.sp,
                      fontFamily = IbmPlexMonoFamily,
                      color = if (twist != ArenaTwist.NONE) GildedBrass else tint.copy(alpha = 0.9f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showMapKeyModal) {
    AlertDialog(
      onDismissRequest = { showMapKeyModal = false },
      containerColor = CharredBasalt,
      title = {
        Text(
          text = "Cartographer's Key • ${run.heroClass.displayName}",
          color = GildedBrass,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Seed ${run.seedLabel} • Dread ${run.dreadLevel} • Cleared $clearedCount chambers",
            style = MaterialTheme.typography.labelSmall,
            color = MutedAsh,
          )
          HorizontalDivider(color = KilnBrass.copy(alpha = 0.4f))
          MapNodeType.entries.forEach { type ->
            val (icon, tint) = nodeVisual(type)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
              Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.width(74.dp),
              )
              Text(
                text = nodeTooltipDescription(type, run.currentAct),
                style = MaterialTheme.typography.bodySmall,
                color = WarmParchment.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showMapKeyModal = false }) {
          Text("Return to Map", color = GildedBrass)
        }
      },
    )
  }

  if (showDeckModal) {
    AlertDialog(
      onDismissRequest = { showDeckModal = false },
      containerColor = CharredBasalt,
      title = {
        Text("Expedition Deck (${run.masterDeck.size})", color = WarmParchment)
      },
      text = {
        LazyColumn(
          modifier = Modifier.heightIn(max = 360.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(run.masterDeck, key = { it.uid }) { card ->
            CardPillView(
              cardDef = GameCatalog.cardById(card.defId),
              upgraded = card.upgraded,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDeckModal = false }) {
          Text("Close", color = MoltenEmber)
        }
      },
    )
  }

  inspectedRelic?.let { relic ->
    AlertDialog(
      onDismissRequest = { inspectedRelic = null },
      containerColor = CharredBasalt,
      title = { Text(relic.name, color = GildedBrass) },
      text = { Text(relic.summary, color = WarmParchment) },
      confirmButton = {
        TextButton(onClick = { inspectedRelic = null }) {
          Text("Close", color = MoltenEmber)
        }
      },
    )
  }

  inspectedRemnant?.let { remnant ->
    AlertDialog(
      onDismissRequest = { inspectedRemnant = null },
      containerColor = CharredBasalt,
      title = { Text(remnant.name, color = HueViolet) },
      text = { Text(remnant.summary, color = WarmParchment) },
      confirmButton = {
        TextButton(onClick = { inspectedRemnant = null }) {
          Text("Close", color = MoltenEmber)
        }
      },
    )
  }
}

/**
 * Computes deterministic organic horizontal coordinates (0.14..0.86) for each node so pilgrimage paths
 * wind gracefully across the parchment map while keeping nodes & Canvas trails aligned to the pixel.
 */
private fun nodeNormalizedX(
  node: MapNode,
  indexInRow: Int,
  rowCount: Int,
  runSeed: Long,
): Float {
  if (rowCount <= 1) return 0.50f
  val base = 0.17f + (indexInRow.toFloat() / (rowCount - 1).coerceAtLeast(1)) * 0.66f
  val hash = ((runSeed xor (node.row.toLong() * 7919L) xor (node.col.toLong() * 104729L)) and 0xFFL).toInt()
  val wander = ((hash / 255f) - 0.5f) * 0.10f
  return (base + wander).coerceIn(0.14f, 0.86f)
}

private fun biomeZoneBanner(act: Int, rowIdx: Int): Pair<String, String>? = when (rowIdx) {
  CampaignGenerator.ACT_ROW_COUNT -> when (act) {
    1 -> "SUMMIT • HOLLOW WARDEN'S KEEP" to "Act I Boss Citadel"
    2 -> "SUMMIT • THE LIVING FURNACE" to "Act II Boss Crucible"
    else -> "SUMMIT • THE EMBER THRONE" to "Final Hearthkeeper Sanctum"
  }
  10 -> when (act) {
    1 -> "UPPER ASH TERRACES" to "Highlands • Elites & Reliquaries"
    2 -> "MOLTEN SLUICE SPANS" to "Upper Crucible Ascent"
    else -> "CROWN OF CINDERS" to "Inner Sanctum Approach"
  }
  5 -> if (act == 1) {
    "THE ECHO CROSSING" to "Row 5 • Meet Your Fallen Shade"
  } else {
    "MID-PASS SANCTUARY" to "Crossroads of the Ascent"
  }
  1 -> when (act) {
    1 -> "SCORCHED TRAILHEAD" to "Begin the Pilgrimage"
    2 -> "CRUCIBLE FOOTHILLS" to "Enter Act II"
    else -> "ASHEN GATES" to "Final Ascent Begins"
  }
  else -> null
}

@Composable
private fun MapVitalChip(label: String, accent: Color) {
  Text(
    text = label,
    fontSize = 9.sp,
    lineHeight = 10.sp,
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Bold,
    color = accent,
    maxLines = 1,
  )
}

@Composable
private fun MapElevationCartouche(
  title: String,
  subtitle: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = ObsidianVoid.copy(alpha = 0.78f),
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "✦",
        fontSize = 9.sp,
        color = accent,
      )
      Text(
        text = title,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.ExtraBold,
        color = accent,
      )
      Text(
        text = "• $subtitle",
        fontSize = 9.sp,
        lineHeight = 10.sp,
        color = WarmParchment.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun GraphPathConnector(
  upperNodes: List<MapNode>,
  lowerNodes: List<MapNode>,
  nodesById: Map<String, MapNode>,
  currentNodeId: String?,
  availableIds: Set<String>,
  runSeed: Long,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f))

    lowerNodes.forEachIndexed { lowerIdx, lowerNode ->
      val startX = nodeNormalizedX(lowerNode, lowerIdx, lowerNodes.size, runSeed) * size.width
      val startY = size.height

      lowerNode.nextNodeIds.forEach { targetId ->
        val upperIdx = upperNodes.indexOfFirst { it.id == targetId }
        if (upperIdx >= 0) {
          val upperNode = nodesById[targetId]
          val endX = nodeNormalizedX(upperNodes[upperIdx], upperIdx, upperNodes.size, runSeed) * size.width
          val endY = 0f

          val isActiveChoice = lowerNode.id == currentNodeId && targetId in availableIds
          val isTraversed = lowerNode.visited && upperNode?.visited == true
          val isShadeEdge = upperNode?.type == MapNodeType.SHADE

          val midY = size.height * 0.5f
          val path = Path().apply {
            moveTo(startX, startY)
            cubicTo(
              startX, midY + 4f,
              endX, midY - 4f,
              endX, endY,
            )
          }

          // Subtle dark ink shadow behind every path so trails stay ultra-legible over bright parchment!
          drawPath(
            path = path,
            color = ObsidianVoid.copy(alpha = 0.52f),
            style = Stroke(width = if (isActiveChoice) 12f else 6.5f, cap = StrokeCap.Round),
          )

          if (isActiveChoice) {
            drawPath(
              path = path,
              color = MoltenEmber.copy(alpha = 0.36f),
              style = Stroke(width = 11f, cap = StrokeCap.Round),
            )
            drawPath(
              path = path,
              color = GildedBrass,
              style = Stroke(width = 4.2f, cap = StrokeCap.Round),
            )
          } else if (isTraversed) {
            drawPath(
              path = path,
              color = GildedBrass,
              style = Stroke(width = 3.8f, cap = StrokeCap.Round),
            )
          } else if (isShadeEdge) {
            drawPath(
              path = path,
              color = HueViolet.copy(alpha = 0.88f),
              style = Stroke(width = 3.0f, cap = StrokeCap.Round, pathEffect = dashEffect),
            )
          } else {
            drawPath(
              path = path,
              color = WarmParchment.copy(alpha = 0.52f),
              style = Stroke(width = 2.4f, cap = StrokeCap.Round, pathEffect = dashEffect),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MapLandmarkMedallion(
  node: MapNode,
  isAvailable: Boolean,
  isCurrent: Boolean,
  isInspected: Boolean,
  beaconScale: Float,
  onClick: () -> Unit,
) {
  val (icon, accent) = nodeVisual(node.type)
  val isBoss = node.type == MapNodeType.BOSS
  val medallionSize = if (isBoss) 56.dp else 44.dp
  val ringColor = when {
    isCurrent -> GildedBrass
    isAvailable -> MoltenEmber
    isInspected -> accent
    node.visited -> VerdantHeal.copy(alpha = 0.78f)
    node.type == MapNodeType.SHADE -> HueViolet
    isBoss -> CrimsonRuin
    else -> KilnBrass.copy(alpha = 0.55f)
  }
  val fillColor = when {
    isAvailable -> CharredBasalt.copy(alpha = 0.96f)
    isCurrent -> CopperPlateTint.copy(alpha = 0.96f)
    node.visited -> ObsidianVoid.copy(alpha = 0.82f)
    else -> ObsidianVoid.copy(alpha = 0.88f)
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.scale(if (isAvailable) beaconScale else 1f),
    ) {
      // Soft halo ring behind reachable or current traveler landmark
      if (isAvailable || isCurrent || isBoss) {
        Box(
          modifier = Modifier
            .size(medallionSize + 10.dp)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  ringColor.copy(alpha = if (isAvailable || isCurrent) 0.42f else 0.28f),
                  Color.Transparent,
                )
              )
            ),
        )
      }

      Surface(
        onClick = onClick,
        modifier = Modifier
          .size(medallionSize)
          .semantics {
            contentDescription = "${node.type.label} at Row ${node.row}" +
              when {
                isCurrent -> ", Current Position"
                isAvailable -> ", Available to enter"
                node.visited -> ", Cleared"
                else -> ", Tap to inspect"
              }
          }
          .testTag("map_node_${node.id}"),
        color = fillColor,
        shape = CircleShape,
        border = BorderStroke(
          width = if (isAvailable || isCurrent || isInspected || isBoss) 2.2.dp else 1.2.dp,
          color = ringColor,
        ),
        shadowElevation = if (isAvailable || isBoss) 6.dp else 2.dp,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isAvailable || isCurrent || isInspected || isBoss || node.type == MapNodeType.SHADE) {
              accent
            } else if (node.visited) {
              VerdantHeal.copy(alpha = 0.85f)
            } else {
              WarmParchment.copy(alpha = 0.78f)
            },
            modifier = Modifier.size(if (isBoss) 26.dp else 20.dp),
          )
        }
      }

      // Traveler / Cleared corner wax pip
      if (isCurrent) {
        Surface(
          color = GildedBrass,
          shape = CircleShape,
          border = BorderStroke(1.dp, ObsidianVoid),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(16.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("★", fontSize = 9.sp, color = ObsidianVoid, fontWeight = FontWeight.Black)
          }
        }
      } else if (node.visited) {
        Surface(
          color = VerdantHeal,
          shape = CircleShape,
          border = BorderStroke(1.dp, ObsidianVoid),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(15.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 9.sp, color = ObsidianVoid, fontWeight = FontWeight.Black)
          }
        }
      }
    }

    // Crisp engraved parchment-dark nameplate beneath the medallion
    Surface(
      color = ObsidianVoid.copy(alpha = if (isAvailable || isCurrent) 0.88f else 0.72f),
      shape = RoundedCornerShape(6.dp),
      border = BorderStroke(0.75.dp, ringColor.copy(alpha = 0.65f)),
    ) {
      Text(
        text = if (isCurrent) "YOU • ${node.type.label}" else node.type.label,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontFamily = IbmPlexMonoFamily,
        fontWeight = if (isAvailable || isCurrent || isBoss) FontWeight.ExtraBold else FontWeight.Medium,
        color = if (isAvailable || isCurrent || isInspected) WarmParchment else WarmParchment.copy(alpha = 0.82f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
      )
    }
  }
}

@Composable
private fun MapNodeTooltipCard(
  node: MapNode,
  act: Int,
  isVisited: Boolean,
  isCurrent: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (icon, accent) = nodeVisual(node.type)
  val statusTag = when {
    isCurrent -> "CURRENT POSITION"
    isVisited -> "CLEARED"
    else -> "ROW ${node.row} • UPCOMING"
  }

  Surface(
    color = CharredBasalt.copy(alpha = 0.97f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.5.dp, accent),
    shadowElevation = 6.dp,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${node.type.label}  •  $statusTag",
          style = MaterialTheme.typography.titleSmall,
          color = accent,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = nodeTooltipDescription(node.type, act),
          style = MaterialTheme.typography.bodySmall,
          color = WarmParchment.copy(alpha = 0.9f),
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .size(48.dp)
          .testTag("dismiss_map_tooltip"),
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close Node Details", tint = MutedAsh)
      }
    }
  }
}

private fun nodeTooltipDescription(type: MapNodeType, act: Int): String = when (type) {
  MapNodeType.COMBAT -> "Standard chamber skirmish. Grants Cinders and a 3-card draft."
  MapNodeType.ELITE -> "High-threat guardian. Drops a Relic, +1 Flask refill, heavy Cinders, and cards."
  MapNodeType.SHADE -> "Act I Row 5 Echo mirroring your previous run's deck. Grants a permanent Shade Remnant."
  MapNodeType.CAMPFIRE -> "Brazier sanctuary. Rest to recover 35% HP & +1 Flask, or Temper a card."
  MapNodeType.MERCHANT -> "Reliquary trader. Spend Cinders on cards, relics, Flask refills, or deck purging."
  MapNodeType.EVENT -> "Branching cathedral dilemma with risk-reward choices for Cinders, HP, or Relics."
  MapNodeType.TREASURE -> "Unsealed reliquary cache granting +40 Cinders, +1 Flask, and a free Relic."
  MapNodeType.BOSS -> when (act) {
    1 -> "Act I Boss: Hollow Warden. Defeat to ascend into Act II: The Obsidian Crucible."
    2 -> "Act II Boss: The Living Furnace. Defeat to reach Act III: The Ember Throne."
    else -> "Act III Boss: The Hearthkeeper. Vanquish or complete 3 Ash-only Covenant turns."
  }
}

private fun nodeVisual(type: MapNodeType): Pair<ImageVector, Color> = when (type) {
  MapNodeType.COMBAT -> Icons.Default.Whatshot to HueEmber
  MapNodeType.ELITE -> Icons.Default.Shield to MoltenEmber
  MapNodeType.SHADE -> Icons.Default.AutoAwesome to HueViolet
  MapNodeType.CAMPFIRE -> Icons.Default.LocalFireDepartment to HueGold
  MapNodeType.MERCHANT -> Icons.Default.Storefront to GildedBrass
  MapNodeType.EVENT -> Icons.Default.Explore to HueTide
  MapNodeType.TREASURE -> Icons.Default.CardGiftcard to VerdantHeal
  MapNodeType.BOSS -> Icons.Default.Warning to CrimsonRuin
}
