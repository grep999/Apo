package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.*
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@DrawableRes
fun enemyPortraitRes(enemy: EnemyState): Int? = when {
  enemy.isShade || enemy.defId == "special_shade" -> R.drawable.img_enemy_shade
  enemy.defId == "act1_boss_warden" -> R.drawable.img_boss_warden
  enemy.defId == "act2_boss_furnace" -> R.drawable.img_boss_furnace
  enemy.defId == "act3_boss_malakor" -> R.drawable.img_boss_hearthkeeper
  else -> null
}

@Composable
fun EnemyPortraitFrame(
  enemy: EnemyState,
  hitFlash: Boolean,
  modifier: Modifier = Modifier,
  size: androidx.compose.ui.unit.Dp = 48.dp,
) {
  val resId = enemyPortraitRes(enemy) ?: return
  val borderTint by animateColorAsState(
    targetValue = when {
      hitFlash -> CrimsonRuin
      enemy.isShade -> HueViolet
      enemy.isBoss -> GildedBrass
      else -> MoltenEmber
    },
    animationSpec = tween(durationMillis = 160),
    label = "enemy_portrait_border",
  )

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(if (hitFlash) 2.dp else 1.5.dp, borderTint),
    color = ObsidianVoid,
    modifier = modifier
      .size(size)
      .testTag("enemy_portrait_art"),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(id = resId),
        contentDescription = "${enemy.name} Portrait",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(9.dp)),
      )
      if (hitFlash) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(CrimsonRuin.copy(alpha = 0.36f)),
        )
      }
    }
  }
}

/**
 * Circular stained-glass 5-hue Spectrum Wheel dial (ADR-005):
 * Clockwise order: Ember -> Gold -> Moss -> Tide -> Violet.
 * Highlights the active hue and shows a target brass beacon on the next expected hue.
 */
@Composable
fun SpectrumWheelDial(
  spectrum: SpectrumState,
  isFogActive: Boolean,
  modifier: Modifier = Modifier,
  dialSize: androidx.compose.ui.unit.Dp = 46.dp,
) {
  val nextExpected = remember(spectrum.currentHue) {
    spectrum.currentHue?.let { Hue.nextInWheel(it) }
  }
  val specLabel = if (isFogActive) "?" else GameEngine.formatSpectrum(spectrum.halfSteps)
  val accessibilityText = if (isFogActive) {
    "Pañcakṛtya Astrolabe obscured by Fog"
  } else {
    val active = spectrum.currentHue?.let { "${it.displayName} (${ProgressionCatalog.panchakrityaName(it)})" } ?: "Neutral"
    val next = nextExpected?.let { "${it.displayName} (${ProgressionCatalog.panchakrityaName(it)})" } ?: "Any Hue"
    "Spectrum $specLabel, active $active, next Pañcakṛtya hue $next"
  }

  Box(
    modifier = modifier
      .size(dialSize)
      .semantics { contentDescription = accessibilityText }
      .testTag("spectrum_wheel_dial"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val diameter = minOf(size.width, size.height)
      val strokePx = diameter * 0.14f
      val inset = strokePx / 2f + 3f
      val arcSize = Size(diameter - inset * 2f, diameter - inset * 2f)
      val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = arcSize.width / 2f

      // Outer engraved kiln-brass astrolabe rim
      drawCircle(
        color = KilnBrass.copy(alpha = 0.55f),
        radius = diameter / 2f - 1f,
        center = center,
        style = Stroke(width = 1.dp.toPx()),
      )

      // Inner soot-carbon medallion with brass calibration hairline
      drawCircle(
        color = ObsidianVoid,
        radius = radius - strokePx * 0.42f,
        center = center,
      )
      drawCircle(
        color = KilnBrass.copy(alpha = 0.35f),
        radius = radius - strokePx * 0.45f,
        center = center,
        style = Stroke(width = 0.75.dp.toPx()),
      )

      val sweepPerHue = 360f / Hue.WHEEL_ORDER.size
      val gap = 10f
      Hue.WHEEL_ORDER.forEachIndexed { idx, hue ->
        val startAngle = -90f + idx * sweepPerHue + gap / 2f
        val sweep = sweepPerHue - gap
        val isCurrent = !isFogActive && spectrum.currentHue == hue
        val isNext = !isFogActive && nextExpected == hue

        val segColor = when {
          isFogActive -> FogVeilSlate
          isCurrent -> hueColor(hue)
          isNext -> hueColor(hue).copy(alpha = 0.88f)
          else -> hueColor(hue).copy(alpha = 0.34f)
        }
        drawArc(
          color = segColor,
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(
            width = if (isCurrent) strokePx * 1.25f else strokePx,
            cap = StrokeCap.Butt,
          ),
        )

        // Engraved brass astrolabe radial tick between each Pañcakṛtya sector
        val tickRad = ((-90f + idx * sweepPerHue) * PI / 180.0)
        val innerTick = radius - strokePx * 0.55f
        val outerTick = radius + strokePx * 0.55f
        drawLine(
          color = KilnBrass.copy(alpha = 0.7f),
          start = Offset(center.x + innerTick * cos(tickRad).toFloat(), center.y + innerTick * sin(tickRad).toFloat()),
          end = Offset(center.x + outerTick * cos(tickRad).toFloat(), center.y + outerTick * sin(tickRad).toFloat()),
          strokeWidth = 0.9.dp.toPx(),
        )

        if (isNext) {
          val midRad = ((startAngle + sweep / 2f) * PI / 180.0)
          val dotX = center.x + (radius + strokePx * 0.32f) * cos(midRad).toFloat()
          val dotY = center.y + (radius + strokePx * 0.32f) * sin(midRad).toFloat()
          drawCircle(
            color = VermilionHot,
            radius = 2.8f,
            center = Offset(dotX, dotY),
          )
        }
      }
    }

    Text(
      text = specLabel,
      fontSize = 9.sp,
      lineHeight = 10.sp,
      fontFamily = IbmPlexMonoFamily,
      fontWeight = FontWeight.ExtraBold,
      color = when {
        isFogActive -> MutedAsh
        spectrum.halfSteps >= 4 -> GildedBrass
        spectrum.halfSteps >= 3 -> VermilionHot
        else -> WarmParchment
      },
    )
  }
}

data class CombatCalloutBadge(
  val text: String,
  val color: Color,
)

private val BOARD_DMG_REGEX = Regex("= (\\d+) dmg")
private val CARD_DMG_REGEX = Regex(": (\\d+) dmg")
private val CHIP_DMG_REGEX = Regex("(\\d+) chip dmg")
private val INK_GAIN_REGEX = Regex("\\+(\\d+) (Ember|Gold|Moss|Tide|Violet) Ink")
private val GUARD_GAIN_REGEX = Regex("\\+(\\d+) Guard")
private val SPARK_GAIN_REGEX = Regex("\\+(\\d+) Spark")

fun extractCombatCallouts(events: List<CombatEvent>): List<CombatCalloutBadge> {
  if (events.isEmpty()) return emptyList()
  val badges = mutableListOf<CombatCalloutBadge>()

  for (evt in events) {
    val msg = evt.message
    when {
      msg.contains("WALL OVERFLOW", ignoreCase = true) ->
        badges.add(CombatCalloutBadge("OVERFLOW -8 HP", CrimsonRuin))

      evt.isEcho ->
        badges.add(CombatCalloutBadge("ECHO!", HueViolet))

      msg.contains("Branded blocks drained", ignoreCase = true) ->
        badges.add(CombatCalloutBadge("BRAND DRAIN", CrimsonRuin))

      msg.contains("Wickless Lantern", ignoreCase = true) ->
        badges.add(CombatCalloutBadge("BRAND NEGATED", GildedBrass))

      msg.contains("Spectrum Astrolabe", ignoreCase = true) ->
        badges.add(CombatCalloutBadge("ASTROLABE +1 SPK", GildedBrass))

      else -> {
        BOARD_DMG_REGEX.find(msg)?.groupValues?.getOrNull(1)?.let { dmg ->
          if (dmg != "0") badges.add(CombatCalloutBadge("-$dmg DMG", MoltenEmber))
        }
        CARD_DMG_REGEX.find(msg)?.groupValues?.getOrNull(1)?.let { dmg ->
          if (dmg != "0") badges.add(CombatCalloutBadge("-$dmg DMG", CrimsonRuin))
        }
        CHIP_DMG_REGEX.find(msg)?.groupValues?.getOrNull(1)?.let { dmg ->
          if (dmg != "0") badges.add(CombatCalloutBadge("-$dmg ASH", MutedAsh))
        }
        INK_GAIN_REGEX.find(msg)?.let { m ->
          val amt = m.groupValues[1]
          val hueName = m.groupValues[2]
          val hue = Hue.entries.firstOrNull { it.displayName.equals(hueName, ignoreCase = true) }
          badges.add(CombatCalloutBadge("+$amt ${hue?.shortTag ?: hueName}", hue?.let { hueColor(it) } ?: GildedBrass))
        }
        GUARD_GAIN_REGEX.find(msg)?.groupValues?.getOrNull(1)?.let { grd ->
          badges.add(CombatCalloutBadge("+$grd GRD", SteelShield))
        }
        SPARK_GAIN_REGEX.find(msg)?.groupValues?.getOrNull(1)?.let { spk ->
          badges.add(CombatCalloutBadge("+$spk SPK", GildedBrass))
        }
      }
    }
    if (badges.size >= 3) break
  }

  return badges.distinctBy { it.text }.take(3)
}

/**
 * Non-intercepting animated floating combat callouts overlay.
 * Automatically rises and fades out without blocking taps on the 7x7 board underneath.
 */
@Composable
fun FloatingCombatCallouts(
  events: List<CombatEvent>,
  eventTriggerKey: Any,
  fastAnimations: Boolean,
  modifier: Modifier = Modifier,
) {
  val badges = remember(eventTriggerKey) { extractCombatCallouts(events) }
  val progress = remember { Animatable(1f) }

  LaunchedEffect(eventTriggerKey) {
    if (badges.isEmpty()) {
      progress.snapTo(1f)
      return@LaunchedEffect
    }
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(
        durationMillis = if (fastAnimations) 420 else 860,
        easing = FastOutSlowInEasing,
      ),
    )
  }

  if (badges.isEmpty() || progress.value >= 0.99f) return

  val alpha = (1f - ((progress.value - 0.55f).coerceAtLeast(0f) / 0.45f)).coerceIn(0f, 1f)
  val riseOffsetPx = -42f * progress.value

  Row(
    modifier = modifier
      .graphicsLayer {
        translationY = riseOffsetPx
      }
      .alpha(alpha)
      .testTag("floating_combat_callouts"),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    badges.forEach { badge ->
      Surface(
        color = ObsidianVoid.copy(alpha = 0.9f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.25.dp, badge.color),
      ) {
        Text(
          text = badge.text,
          style = MaterialTheme.typography.labelSmall,
          color = badge.color,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
  }
}
