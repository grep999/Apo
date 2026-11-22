package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoardCoord
import com.example.engine.Hue
import com.example.engine.TileKind
import com.example.ui.theme.CrimsonRuin
import com.example.ui.theme.GildedBrass
import com.example.ui.theme.HueViolet
import com.example.ui.theme.MoltenEmber
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelShield
import com.example.ui.theme.WarmParchment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class FxCell(
  val row: Int,
  val col: Int,
  val kind: TileKind,
)

data class FxWave(
  val waveIndex: Int,
  val matchedCells: List<FxCell>,
  val ashCells: List<FxCell> = emptyList(),
  val stoneCells: List<FxCell> = emptyList(),
  val primaryHue: Hue? = null,
  val isPrism: Boolean = false,
  val waveDamage: Int = 0,
) {
  val allCells: List<FxCell> get() = matchedCells + ashCells + stoneCells
}

data class BoardJuiceFx(
  val id: Long,
  val waves: List<FxWave>,
  val totalDamage: Int,
  val cascadeCount: Int,
  val primaryHue: Hue?,
  val isPrismBlast: Boolean,
  val headline: String,
  val subline: String? = null,
)

data class InvalidSwapFx(
  val id: Long,
  val from: BoardCoord,
  val to: BoardCoord,
)

data class CardCastFx(
  val id: Long,
  val hue: Hue?,
  val damageDealt: Int,
  val guardGained: Int,
  val isEcho: Boolean,
  val cardName: String,
)

private data class ShatterParticle(
  val angleRad: Float,
  val speedMul: Float,
  val sizeMul: Float,
  val spinDeg: Float,
  val isDiamond: Boolean,
)

private fun buildCellParticles(seed: Int, count: Int): List<ShatterParticle> {
  var s = (seed * 1103515245 + 12345) and 0x7FFFFFFF
  fun nextF(): Float {
    s = (s * 1103515245 + 12345) and 0x7FFFFFFF
    return (s % 10000) / 10000f
  }
  return List(count) { idx ->
    val baseAngle = (idx.toFloat() / count) * (2f * PI.toFloat())
    val jitter = (nextF() - 0.5f) * 0.45f
    ShatterParticle(
      angleRad = baseAngle + jitter,
      speedMul = 0.55f + nextF() * 1.15f,
      sizeMul = 0.65f + nextF() * 0.95f,
      spinDeg = (nextF() - 0.5f) * 420f,
      isDiamond = idx % 2 == 0,
    )
  }
}

/**
 * 60fps Canvas VFX overlay rendered directly over the 7x7 combat board (non-intercepting):
 * - White-hot cell ignition flashes & expanding luminous shockwave rings
 * - Blazing horizontal/vertical Match Energy Beams slicing across 3+ aligned tiles
 * - 14 faceted diamond & ember shatter shards erupting from every disappearing block
 * - Prism 8-ray starbursts & Stone fracture debris chunks
 * - Homing energy orbs streaking upward into the Enemy HP bar
 * - Bold spring-overshoot impact headline & damage stamp over the match centroid
 */
@Composable
fun BoardJuiceFxOverlay(
  fx: BoardJuiceFx?,
  rows: Int,
  cols: Int,
  fastAnimations: Boolean,
  modifier: Modifier = Modifier,
) {
  if (fx == null || fx.waves.isEmpty()) return

  val waveCount = fx.waves.size.coerceAtLeast(1)
  val totalDurationMs = if (fastAnimations) {
    320 + (waveCount - 1) * 75
  } else {
    640 + (waveCount - 1) * 145
  }

  val timeline = remember(fx.id) { Animatable(0f) }
  LaunchedEffect(fx.id) {
    timeline.snapTo(0f)
    timeline.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing),
    )
  }

  val progress = timeline.value
  if (progress >= 0.995f) return

  // Precompute deterministic shard vectors for every cell in every wave
  val particleMap = remember(fx.id) {
    buildMap<Pair<Int, BoardCoord>, List<ShatterParticle>> {
      fx.waves.forEach { wave ->
        wave.allCells.forEachIndexed { idx, cell ->
          val key = wave.waveIndex to BoardCoord(cell.row, cell.col)
          val count = if (cell.kind is TileKind.Prism || cell.kind is TileKind.Stone) 16 else 12
          put(key, buildCellParticles(seed = (fx.id.toInt() * 31) + wave.waveIndex * 97 + cell.row * 17 + cell.col * 7 + idx, count = count))
        }
      }
    }
  }

  // Compute board centroid for the floating headline stamp
  val centroid = remember(fx.id) {
    val cells = fx.waves.firstOrNull()?.matchedCells.orEmpty().ifEmpty { fx.waves.first().allCells }
    if (cells.isEmpty()) {
      0.5f to 0.5f
    } else {
      val avgCol = cells.map { it.col + 0.5f }.average().toFloat() / cols.coerceAtLeast(1)
      val avgRow = cells.map { it.row + 0.5f }.average().toFloat() / rows.coerceAtLeast(1)
      avgCol.coerceIn(0.22f, 0.78f) to avgRow.coerceIn(0.20f, 0.78f)
    }
  }

  val accentColor = when {
    fx.isPrismBlast -> GildedBrass
    fx.primaryHue != null -> hueColor(fx.primaryHue)
    else -> MoltenEmber
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("board_juice_fx_overlay"),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cellW = size.width / cols.coerceAtLeast(1)
      val cellH = size.height / rows.coerceAtLeast(1)
      val cellMin = minOf(cellW, cellH)
      val waveSpan = if (waveCount == 1) 1f else 0.72f
      val waveStep = if (waveCount == 1) 0f else (1f - waveSpan) / (waveCount - 1)

      fx.waves.forEachIndexed { wIdx, wave ->
        val localT = ((progress - wIdx * waveStep) / waveSpan).coerceIn(0f, 1f)
        if (localT <= 0f || localT >= 0.999f) return@forEachIndexed

        val waveHueColor = when {
          wave.isPrism -> GildedBrass
          wave.primaryHue != null -> hueColor(wave.primaryHue)
          else -> MoltenEmber
        }

        // 1. Blazing Horizontal / Vertical Match Energy Beams connecting 3+ matched tiles
        if (localT < 0.52f && wave.matchedCells.size >= 2) {
          val beamT = (localT / 0.52f).coerceIn(0f, 1f)
          val beamAlpha = (1f - beamT) * (1f - beamT * 0.35f)

          // Group by rows (>= 3 contiguous or same-row prism blast)
          wave.matchedCells.groupBy { it.row }.forEach { (r, rowCells) ->
            if (rowCells.size >= 3) {
              val minC = rowCells.minOf { it.col }
              val maxC = rowCells.maxOf { it.col }
              val y = (r + 0.5f) * cellH
              val x1 = (minC + 0.25f) * cellW
              val x2 = (maxC + 0.75f) * cellW
              drawLine(
                color = waveHueColor.copy(alpha = beamAlpha * 0.55f),
                start = Offset(x1, y),
                end = Offset(x2, y),
                strokeWidth = cellMin * (0.48f * (1f - beamT * 0.45f)),
                cap = StrokeCap.Round,
              )
              drawLine(
                color = WarmParchment.copy(alpha = beamAlpha * 0.92f),
                start = Offset(x1, y),
                end = Offset(x2, y),
                strokeWidth = cellMin * (0.16f * (1f - beamT * 0.4f)),
                cap = StrokeCap.Round,
              )
            }
          }
          // Group by columns (>= 3 vertical line)
          wave.matchedCells.groupBy { it.col }.forEach { (c, colCells) ->
            if (colCells.size >= 3) {
              val minR = colCells.minOf { it.row }
              val maxR = colCells.maxOf { it.row }
              val x = (c + 0.5f) * cellW
              val y1 = (minR + 0.25f) * cellH
              val y2 = (maxR + 0.75f) * cellH
              drawLine(
                color = waveHueColor.copy(alpha = beamAlpha * 0.55f),
                start = Offset(x, y1),
                end = Offset(x, y2),
                strokeWidth = cellMin * (0.48f * (1f - beamT * 0.45f)),
                cap = StrokeCap.Round,
              )
              drawLine(
                color = WarmParchment.copy(alpha = beamAlpha * 0.92f),
                start = Offset(x, y1),
                end = Offset(x, y2),
                strokeWidth = cellMin * (0.16f * (1f - beamT * 0.4f)),
                cap = StrokeCap.Round,
              )
            }
          }
        }

        // 2. Per-cell Ignition Flash, Expanding Shockwave Ring, Prism Cross Flare, & Shatter Shards
        wave.allCells.forEach { cell ->
          val cx = (cell.col + 0.5f) * cellW
          val cy = (cell.row + 0.5f) * cellH
          val center = Offset(cx, cy)
          val baseCol = when (cell.kind) {
            is TileKind.Prism -> GildedBrass
            is TileKind.Stone -> Color(0xFFD2CBD8)
            is TileKind.Ash -> Color(0xFFB3ABB9)
            else -> tileColor(cell.kind)
          }

          // A. White-hot core ignition flash (0.0 .. 0.34)
          if (localT < 0.34f) {
            val flashT = localT / 0.34f
            val flashAlpha = (1f - flashT)
            val expand = 1f + flashT * 0.24f
            val w = cellW * 0.92f * expand
            val h = cellH * 0.92f * expand
            drawRoundRect(
              color = baseCol.copy(alpha = flashAlpha * 0.75f),
              topLeft = Offset(cx - w / 2f, cy - h / 2f),
              size = Size(w, h),
              cornerRadius = CornerRadius(cellMin * 0.2f, cellMin * 0.2f),
            )
            drawRoundRect(
              color = Color.White.copy(alpha = flashAlpha * 0.88f),
              topLeft = Offset(cx - w * 0.32f, cy - h * 0.32f),
              size = Size(w * 0.64f, h * 0.64f),
              cornerRadius = CornerRadius(cellMin * 0.14f, cellMin * 0.14f),
            )
          }

          // B. Expanding luminous shockwave ring (0.0 .. 0.72)
          if (localT < 0.72f) {
            val ringT = localT / 0.72f
            val eased = 1f - (1f - ringT) * (1f - ringT)
            val maxR = if (cell.kind is TileKind.Prism) cellMin * 1.45f else cellMin * 0.95f
            val ringAlpha = (1f - ringT) * 0.85f
            drawCircle(
              color = baseCol.copy(alpha = ringAlpha),
              radius = cellMin * 0.24f + maxR * eased,
              center = center,
              style = Stroke(width = (cellMin * 0.11f * (1f - ringT * 0.7f)).coerceAtLeast(1.5f)),
            )
          }

          // C. Prism 8-point radiant starburst rays
          if (cell.kind is TileKind.Prism && localT < 0.65f) {
            val rayT = localT / 0.65f
            val rayAlpha = (1f - rayT) * 0.90f
            val rayLen = cellMin * (0.9f + 1.4f * rayT)
            for (k in 0 until 8) {
              val ang = (k * 45f + rayT * 35f) * (PI / 180.0)
              val end = Offset(cx + cos(ang).toFloat() * rayLen, cy + sin(ang).toFloat() * rayLen)
              drawLine(
                color = if (k % 2 == 0) GildedBrass.copy(alpha = rayAlpha) else Color.White.copy(alpha = rayAlpha),
                start = center,
                end = end,
                strokeWidth = if (k % 2 == 0) 4.5f * (1f - rayT) else 2.5f * (1f - rayT),
                cap = StrokeCap.Round,
              )
            }
          }

          // D. 12-16 faceted diamond & glowing spark shards flying outward with gravity
          val particles = particleMap[wave.waveIndex to BoardCoord(cell.row, cell.col)].orEmpty()
          val pAlpha = (1f - (localT * localT)).coerceIn(0f, 1f)
          val travel = cellMin * 1.65f * (1f - (1f - localT) * (1f - localT))
          val gravityDrop = cellMin * 0.78f * localT * localT

          for (p in particles) {
            val px = cx + cos(p.angleRad) * travel * p.speedMul
            val py = cy + sin(p.angleRad) * travel * p.speedMul + gravityDrop
            val shardRadius = cellMin * 0.125f * p.sizeMul * (1f - localT * 0.55f)
            if (shardRadius <= 0.6f) continue

            // Soft outer glow bloom
            drawCircle(
              color = baseCol.copy(alpha = pAlpha * 0.36f),
              radius = shardRadius * 2.1f,
              center = Offset(px, py),
            )

            if (p.isDiamond) {
              rotate(degrees = p.spinDeg * localT, pivot = Offset(px, py)) {
                val path = Path().apply {
                  moveTo(px, py - shardRadius * 1.35f)
                  lineTo(px + shardRadius * 0.85f, py)
                  lineTo(px, py + shardRadius * 1.35f)
                  lineTo(px - shardRadius * 0.85f, py)
                  close()
                }
                drawPath(path = path, color = baseCol.copy(alpha = pAlpha))
                drawPath(
                  path = path,
                  color = Color.White.copy(alpha = pAlpha * 0.65f),
                  style = Stroke(width = 1.2f),
                )
              }
            } else {
              drawCircle(
                color = WarmParchment.copy(alpha = pAlpha * 0.92f),
                radius = shardRadius * 0.68f,
                center = Offset(px, py),
              )
            }
          }
        }

        // 3. Homing Ember Orbs curving from match centroid upward toward the Enemy HP bar!
        if (wave.waveDamage > 0 && wave.matchedCells.isNotEmpty()) {
          val avgX = wave.matchedCells.map { (it.col + 0.5f) * cellW }.average().toFloat()
          val avgY = wave.matchedCells.map { (it.row + 0.5f) * cellH }.average().toFloat()
          val targetX = size.width * 0.5f
          val targetY = -cellH * 0.35f
          val orbCount = if (wave.waveDamage >= 16 || wave.isPrism) 3 else 2

          for (o in 0 until orbCount) {
            val orbT = ((localT - 0.08f - o * 0.05f) / 0.72f).coerceIn(0f, 1f)
            if (orbT <= 0f || orbT >= 1f) continue
            val curveSide = if (o % 2 == 0) -1f else 1f
            val ctrlX = (avgX + targetX) * 0.5f + curveSide * size.width * (0.20f + o * 0.06f)
            val ctrlY = (avgY + targetY) * 0.45f
            val inv = 1f - orbT
            val ox = inv * inv * avgX + 2f * inv * orbT * ctrlX + orbT * orbT * targetX
            val oy = inv * inv * avgY + 2f * inv * orbT * ctrlY + orbT * orbT * targetY
            val orbAlpha = (1f - orbT * 0.45f).coerceIn(0f, 1f)

            // Comet tail
            val prevT = (orbT - 0.11f).coerceAtLeast(0f)
            val pInv = 1f - prevT
            val tx = pInv * pInv * avgX + 2f * pInv * prevT * ctrlX + prevT * prevT * targetX
            val ty = pInv * pInv * avgY + 2f * pInv * prevT * ctrlY + prevT * prevT * targetY
            drawLine(
              brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, waveHueColor.copy(alpha = orbAlpha * 0.85f), Color.White),
                start = Offset(tx, ty),
                end = Offset(ox, oy),
              ),
              start = Offset(tx, ty),
              end = Offset(ox, oy),
              strokeWidth = cellMin * 0.18f,
              cap = StrokeCap.Round,
            )
            drawCircle(
              color = waveHueColor.copy(alpha = orbAlpha * 0.5f),
              radius = cellMin * 0.28f,
              center = Offset(ox, oy),
            )
            drawCircle(
              color = Color.White.copy(alpha = orbAlpha),
              radius = cellMin * 0.12f,
              center = Offset(ox, oy),
            )
          }
        }
      }
    }

    // 4. Bold Spring-Overshoot Floating Combo & Damage Stamp over Match Centroid
    val stampAlpha = when {
      progress < 0.12f -> progress / 0.12f
      progress > 0.70f -> (1f - (progress - 0.70f) / 0.30f).coerceIn(0f, 1f)
      else -> 1f
    }
    val stampScale = when {
      progress < 0.18f -> 0.45f + (progress / 0.18f) * 0.82f // punch overshoot to 1.27x!
      progress < 0.34f -> 1.27f - ((progress - 0.18f) / 0.16f) * 0.27f // settle to 1.0x
      else -> 1f + (progress - 0.34f) * 0.06f
    }
    val risePx = -46f * progress

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        color = ObsidianVoid.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, accentColor),
        shadowElevation = 8.dp,
        modifier = Modifier
          .graphicsLayer {
            translationX = (centroid.first - 0.5f) * 140f
            translationY = (centroid.second - 0.5f) * 140f + risePx
            scaleX = stampScale
            scaleY = stampScale
            alpha = stampAlpha
          },
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = fx.headline,
            style = MaterialTheme.typography.titleMedium,
            fontSize = if (fx.cascadeCount >= 2 || fx.isPrismBlast) 17.sp else 15.sp,
            fontWeight = FontWeight.Black,
            color = accentColor,
          )
          if (!fx.subline.isNullOrBlank()) {
            Text(
              text = fx.subline,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = WarmParchment,
            )
          }
        }
      }
    }
  }
}

/**
 * Dramatic glowing elemental slash arc + shield pulse over the Enemy HUD when a Card is cast!
 */
@Composable
fun CardCastSlashOverlay(
  fx: CardCastFx?,
  fastAnimations: Boolean,
  modifier: Modifier = Modifier,
) {
  if (fx == null) return
  val anim = remember(fx.id) { Animatable(0f) }
  LaunchedEffect(fx.id) {
    anim.snapTo(0f)
    anim.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = if (fastAnimations) 240 else 440, easing = FastOutSlowInEasing),
    )
  }
  val t = anim.value
  if (t >= 0.99f) return

  val slashColor = when {
    fx.hue != null -> hueColor(fx.hue)
    fx.damageDealt > 0 -> CrimsonRuin
    fx.guardGained > 0 -> SteelShield
    else -> GildedBrass
  }
  val alpha = (1f - t * t).coerceIn(0f, 1f)

  Canvas(modifier = modifier.fillMaxSize()) {
    if (fx.damageDealt > 0) {
      // Diagonal blazing energy slash across enemy card
      val headT = (t * 1.45f).coerceIn(0f, 1f)
      val tailT = ((t - 0.18f) * 1.35f).coerceIn(0f, 1f)
      val start = Offset(size.width * 0.12f, size.height * 0.88f)
      val end = Offset(size.width * 0.88f, size.height * 0.12f)
      val p1 = Offset(start.x + (end.x - start.x) * tailT, start.y + (end.y - start.y) * tailT)
      val p2 = Offset(start.x + (end.x - start.x) * headT, start.y + (end.y - start.y) * headT)
      drawLine(
        color = slashColor.copy(alpha = alpha * 0.62f),
        start = p1,
        end = p2,
        strokeWidth = 24f * (1f - t * 0.5f),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = Color.White.copy(alpha = alpha * 0.95f),
        start = p1,
        end = p2,
        strokeWidth = 7f * (1f - t * 0.4f),
        cap = StrokeCap.Round,
      )
      if (fx.isEcho) {
        val eStart = Offset(size.width * 0.22f, size.height * 0.15f)
        val eEnd = Offset(size.width * 0.82f, size.height * 0.85f)
        val ep1 = Offset(eStart.x + (eEnd.x - eStart.x) * tailT, eStart.y + (eEnd.y - eStart.y) * tailT)
        val ep2 = Offset(eStart.x + (eEnd.x - eStart.x) * headT, eStart.y + (eEnd.y - eStart.y) * headT)
        drawLine(
          color = HueViolet.copy(alpha = alpha * 0.72f),
          start = ep1,
          end = ep2,
          strokeWidth = 18f * (1f - t * 0.5f),
          cap = StrokeCap.Round,
        )
      }
    } else if (fx.guardGained > 0) {
      // Shimmering ward ripple
      val rad = sqrt(size.width * size.width + size.height * size.height) * 0.35f * t
      drawCircle(
        color = SteelShield.copy(alpha = alpha * 0.55f),
        radius = rad,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(width = 6f * (1f - t * 0.6f)),
      )
    }
  }
}

/**
 * Subtle living atmospheric backdrop: slowly drifting glowing ember motes tinted to the current Act.
 * Pure Canvas, zero pointer interception, pauses smoothly when fastAnimations is enabled.
 */
@Composable
fun AmbientArenaEmbers(
  accentColor: Color,
  fastAnimations: Boolean,
  modifier: Modifier = Modifier,
) {
  val phase = if (fastAnimations) {
    0.35f
  } else {
    val transition = rememberInfiniteTransition(label = "arena_embers")
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 12000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "ember_drift",
    ).value
  }

  Canvas(modifier = modifier) {
    val moteCount = 14
    for (i in 0 until moteCount) {
      val seed = i * 0.6180339f
      val baseX = ((seed * 7.3f) % 1f) * size.width
      val speed = 0.7f + (i % 3) * 0.35f
      val yFrac = (1f - ((phase * speed + seed) % 1f))
      val sway = sin((phase * 2f * PI + i * 1.4).toDouble()).toFloat() * 14f
      val x = (baseX + sway).coerceIn(0f, size.width)
      val y = yFrac * size.height
      val edgeFade = (minOf(yFrac, 1f - yFrac) * 4f).coerceIn(0f, 1f)
      val radius = 1.8f + (i % 4) * 1.1f
      drawCircle(
        color = accentColor.copy(alpha = 0.22f * edgeFade),
        radius = radius * 2.4f,
        center = Offset(x, y),
      )
      drawCircle(
        color = WarmParchment.copy(alpha = 0.32f * edgeFade),
        radius = radius * 0.7f,
        center = Offset(x, y),
      )
    }
  }
}
