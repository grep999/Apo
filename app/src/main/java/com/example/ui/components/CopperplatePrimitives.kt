package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.*
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// 1. DIEGETIC PHYSICAL SILHOUETTE SHAPES (Form Follows Mechanic)
// ─────────────────────────────────────────────────────────────────────────────

/** Attack cards: sharp iron-blade diagonal chamfer at top-right & clipped bottom-left. */
val AttackCardShape: Shape = GenericShape { size, _ ->
  val cutTop = minOf(size.width, size.height) * 0.16f
  val cutBot = minOf(size.width, size.height) * 0.08f
  moveTo(0f, 0f)
  lineTo(size.width - cutTop, 0f)
  lineTo(size.width, cutTop)
  lineTo(size.width, size.height)
  lineTo(cutBot, size.height)
  lineTo(0f, size.height - cutBot)
  close()
}

/** Skill / Utility / Volatile cards: notched apothecary specimen corners on all 4 sides. */
val SkillCardShape: Shape = GenericShape { size, _ ->
  val n = minOf(size.width, size.height) * 0.10f
  moveTo(n, 0f)
  lineTo(size.width - n, 0f)
  lineTo(size.width, n)
  lineTo(size.width, size.height - n)
  lineTo(size.width - n, size.height)
  lineTo(n, size.height)
  lineTo(0f, size.height - n)
  lineTo(0f, n)
  close()
}

/** Guard / Power plates: symmetrical riveted top-arch chamfer. */
val GuardCardShape: Shape = GenericShape { size, _ ->
  val c = minOf(size.width, size.height) * 0.12f
  moveTo(c, 0f)
  lineTo(size.width - c, 0f)
  lineTo(size.width, c)
  lineTo(size.width, size.height - c * 0.5f)
  lineTo(size.width - c * 0.5f, size.height)
  lineTo(c * 0.5f, size.height)
  lineTo(0f, size.height - c * 0.5f)
  lineTo(0f, c)
  close()
}

fun cardSilhouetteShape(cardDef: CardDef): Shape = when {
  cardDef.category == CardCategory.ATTACK -> AttackCardShape
  cardDef.blockGain > 0 && cardDef.damage == 0 -> GuardCardShape
  else -> SkillCardShape
}

/**
 * Draws fine 17th-century intaglio copperplate inner hairline borders, corner registration ticks,
 * and subtle diagonal cross-hatching for volatile / echo plates.
 */
fun Modifier.copperplateEngraving(
  accent: Color = KilnBrass,
  showCrossHatch: Boolean = false,
): Modifier = this.drawBehind {
  val inset = 3.5.dp.toPx()
  val tick = 6.dp.toPx()
  val hairline = 0.75.dp.toPx()

  if (showCrossHatch) {
    val step = 10.dp.toPx()
    var x = -size.height
    while (x < size.width + size.height) {
      drawLine(
        color = accent.copy(alpha = 0.08f),
        start = Offset(x, size.height),
        end = Offset(x + size.height * 0.65f, 0f),
        strokeWidth = hairline,
      )
      x += step
    }
  }

  // Inner copperplate registration frame
  drawRect(
    color = accent.copy(alpha = 0.24f),
    topLeft = Offset(inset, inset),
    size = Size((size.width - inset * 2).coerceAtLeast(1f), (size.height - inset * 2).coerceAtLeast(1f)),
    style = Stroke(width = hairline),
  )

  // Corner brass engraving ticks
  val tickColor = accent.copy(alpha = 0.55f)
  drawLine(tickColor, Offset(inset, inset + tick), Offset(inset, inset), strokeWidth = hairline * 1.4f)
  drawLine(tickColor, Offset(inset, inset), Offset(inset + tick, inset), strokeWidth = hairline * 1.4f)
  drawLine(tickColor, Offset(size.width - inset - tick, size.height - inset), Offset(size.width - inset, size.height - inset), strokeWidth = hairline * 1.4f)
  drawLine(tickColor, Offset(size.width - inset, size.height - inset - tick), Offset(size.width - inset, size.height - inset), strokeWidth = hairline * 1.4f)
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. TWO-COLOR WAX-SEAL ENEMY INTENT MEDALLION (3-Primitive Geometry)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WaxSealEnemyIntent(
  intent: EnemyIntent,
  slotLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (waxColor, valueLabel, desc) = when (intent) {
    is EnemyIntent.Attack -> Triple(
      VermilionWax,
      if (intent.hits > 1) "${intent.damage}×${intent.hits}" else "${intent.damage}",
      "$slotLabel Strike for ${intent.damage} damage" + if (intent.hits > 1) " (${intent.hits} hits)" else "",
    )
    is EnemyIntent.SiphonFlask -> Triple(
      VermilionWax,
      "${intent.damage}!",
      "$slotLabel Siphon Strike for ${intent.damage} dmg and ${intent.flaskDrain} Flask",
    )
    is EnemyIntent.Defend -> Triple(
      KilnBrass,
      "+${intent.block}",
      "$slotLabel Guard for +${intent.block} Block",
    )
    is EnemyIntent.CorruptBoard -> Triple(
      Color(0xFF5A3B7A),
      "×${intent.count}",
      "$slotLabel Corrupt ${intent.count} board tiles",
    )
    is EnemyIntent.InflictBrand -> Triple(
      Color(0xFF8C2D46),
      if (intent.damage > 0) "${intent.damage}/B" else "B×${intent.count}",
      "$slotLabel Inflict Brand on ${intent.count} tiles",
    )
    is EnemyIntent.InflictFog -> Triple(
      Color(0xFF42526B),
      "FOG",
      "$slotLabel Shroud board in Fog (${intent.taps} moves)",
    )
    is EnemyIntent.SealColumn -> Triple(
      Color(0xFF5B4837),
      "SEAL",
      "$slotLabel Seal 1 board column (${intent.turns} turns)",
    )
  }

  val isStrike = intent is EnemyIntent.Attack || intent is EnemyIntent.SiphonFlask
  val isGuard = intent is EnemyIntent.Defend

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(CharredBasalt)
      .border(1.dp, waxColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 4.dp)
      .semantics { contentDescription = desc }
      .testTag("wax_seal_intent"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    // 2-Color, 3-Primitive Scalloped Wax Seal Canvas
    Canvas(modifier = Modifier.size(22.dp)) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val r = size.minDimension / 2f

      // Primitive 1: Scalloped organic wax edge
      val sealPath = Path()
      val lobes = 10
      for (i in 0 until lobes * 2) {
        val angle = (i * PI / lobes).toFloat()
        val rad = if (i % 2 == 0) r * 0.98f else r * 0.84f
        val px = cx + rad * cos(angle)
        val py = cy + rad * sin(angle)
        if (i == 0) sealPath.moveTo(px, py) else sealPath.lineTo(px, py)
      }
      sealPath.close()
      drawPath(sealPath, color = waxColor)

      // Primitive 2: Pressed inner wax rim
      drawCircle(
        color = WarmParchment.copy(alpha = 0.45f),
        radius = r * 0.68f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.dp.toPx()),
      )

      // Primitive 3: High-contrast Ivory semantic glyph (Spearhead / Shield Arch / Fractured Ring)
      when {
        isStrike -> {
          val spear = Path().apply {
            moveTo(cx, cy - r * 0.45f)
            lineTo(cx + r * 0.36f, cy + r * 0.36f)
            lineTo(cx, cy + r * 0.14f)
            lineTo(cx - r * 0.36f, cy + r * 0.36f)
            close()
          }
          drawPath(spear, color = WarmParchment)
        }
        isGuard -> {
          val shield = Path().apply {
            moveTo(cx, cy - r * 0.42f)
            lineTo(cx + r * 0.36f, cy - r * 0.2f)
            lineTo(cx + r * 0.28f, cy + r * 0.24f)
            lineTo(cx, cy + r * 0.44f)
            lineTo(cx - r * 0.28f, cy + r * 0.24f)
            lineTo(cx - r * 0.36f, cy - r * 0.2f)
            close()
          }
          drawPath(shield, color = WarmParchment, style = Stroke(width = 1.6.dp.toPx()))
        }
        else -> {
          drawArc(
            color = WarmParchment,
            startAngle = -30f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.36f, cy - r * 0.36f),
            size = Size(r * 0.72f, r * 0.72f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
          )
          drawCircle(color = WarmParchment, radius = r * 0.13f, center = Offset(cx, cy))
        }
      }
    }

    Column {
      Text(
        text = slotLabel,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontFamily = IbmPlexMonoFamily,
        color = MutedAsh,
      )
      Text(
        text = valueLabel,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.ExtraBold,
        color = WarmParchment,
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. ETCHED BRASS KILN LEDGER (SPANDA × VIMARŚA = OUTPUT)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpandaVimarsaLedgerHud(
  turn: TurnState,
  breakdown: ScoringBreakdown?,
  modifier: Modifier = Modifier,
) {
  val multTenths = breakdown?.finalMultTenths ?: turn.comboMultTenths
  val spandaVal = breakdown?.totalHeat ?: 0
  val outputVal = breakdown?.finalOutput ?: 0
  val isBoosted = multTenths > TurnState.BASE_MULT_TENTHS
  val multText = "${multTenths / 10}.${multTenths % 10}×"

  var pulseKey by remember { mutableIntStateOf(0) }
  LaunchedEffect(breakdown) {
    if (breakdown != null) pulseKey++
  }
  val scale by animateFloatAsState(
    targetValue = if (pulseKey > 0 && isBoosted) 1.03f else 1f,
    animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f),
    label = "ledger_scale",
  )

  Surface(
    color = CharredBasalt,
    shape = SkillCardShape,
    border = BorderStroke(1.dp, if (isBoosted) VermilionWax.copy(alpha = 0.85f) else KilnBrass.copy(alpha = 0.55f)),
    modifier = modifier
      .fillMaxWidth()
      .scale(scale)
      .copperplateEngraving(accent = if (isBoosted) VermilionWax else KilnBrass)
      .testTag("kiln_ledger_hud"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // SPANDA (Light / Base Power)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Surface(
            color = ObsidianVoid,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, KilnBrass.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("SPANDA", fontSize = 8.sp, fontFamily = IbmPlexMonoFamily, color = KilnBrass, fontWeight = FontWeight.Bold)
              Text("$spandaVal", fontSize = 13.sp, fontFamily = IbmPlexMonoFamily, color = WarmParchment, fontWeight = FontWeight.ExtraBold)
            }
          }

          Text("×", fontSize = 14.sp, fontWeight = FontWeight.Black, color = VermilionHot)

          // VIMARŚA (Reflective Multiplier)
          Surface(
            color = if (isBoosted) VermilionWax.copy(alpha = 0.26f) else ObsidianVoid,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, if (isBoosted) VermilionHot else SmokedBorder),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("VIMARŚA", fontSize = 8.sp, fontFamily = IbmPlexMonoFamily, color = if (isBoosted) VermilionHot else MutedAsh, fontWeight = FontWeight.Bold)
              Text(multText, fontSize = 13.sp, fontFamily = IbmPlexMonoFamily, color = if (isBoosted) GildedBrass else WarmParchment, fontWeight = FontWeight.ExtraBold)
            }
          }
        }

        // Manifested Output / Kumbhaka Bank
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (turn.overpressureMultTenths > 0) {
            Surface(
              color = VermilionWax.copy(alpha = 0.25f),
              shape = RoundedCornerShape(5.dp),
              border = BorderStroke(1.dp, VermilionHot),
            ) {
              Text(
                text = "KUMBHAKA +${turn.overpressureMultTenths / 10}.0×",
                fontSize = 8.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.Bold,
                color = GildedBrass,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
              )
            }
          }
          if (outputVal > 0) {
            Text(
              text = "= $outputVal",
              fontSize = 14.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.ExtraBold,
              color = GildedBrass,
            )
          } else {
            Text(
              text = "PRAKĀŚA LEDGER",
              fontSize = 9.sp,
              fontFamily = IbmPlexMonoFamily,
              color = MutedAsh,
            )
          }
        }
      }

      // Live ticker of triggered Upāya Passives, Dhāraṇā Supports & Tantric Artifacts
      if (breakdown != null && breakdown.steps.isNotEmpty()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          breakdown.steps.forEach { step ->
            val badge = buildString {
              append(step.label)
              if (step.heatDelta > 0) append(" +${step.heatDelta}")
              if (step.multAddTenths > 0) append(" +${step.multAddTenths / 10}.${step.multAddTenths % 10}×")
              if (step.multFactorPercent > 100) append(" ×${step.multFactorPercent / 100}")
            }
            Surface(
              color = CopperPlateTint,
              shape = RoundedCornerShape(4.dp),
              border = BorderStroke(0.75.dp, KilnBrass.copy(alpha = 0.55f)),
            ) {
              Text(
                text = badge,
                fontSize = 9.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = GildedBrass,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
              )
            }
          }
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. EVOLVING SIGNATURE SKILL PLATE (PoE2 Skill + Live Dhāraṇā Sockets)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SignatureSkillSocketPlate(
  heroClass: HeroClass,
  growth: CharacterGrowthState,
  charge: Int,
  enabled: Boolean,
  onUnleash: () -> Unit,
  onInspectSkill: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val skillDef = remember(heroClass) { ProgressionCatalog.signatureSkillFor(heroClass) }
  val ready = charge >= PlayerCombatState.MAX_OVERDRIVE && enabled
  val fraction = (charge.toFloat() / PlayerCombatState.MAX_OVERDRIVE.toFloat()).coerceIn(0f, 1f)

  Surface(
    color = if (ready) CopperPlateTint else CharredBasalt,
    shape = AttackCardShape,
    border = BorderStroke(if (ready) 1.5.dp else 1.dp, if (ready) VermilionHot else KilnBrass.copy(alpha = 0.65f)),
    modifier = modifier
      .fillMaxWidth()
      .copperplateEngraving(accent = if (ready) VermilionHot else KilnBrass, showCrossHatch = ready)
      .testTag("signature_skill_plate"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier
            .weight(1f)
            .clickable(onClick = onInspectSkill),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = null,
            tint = if (ready) VermilionHot else GildedBrass,
            modifier = Modifier.size(16.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${skillDef.name} • ${growth.socketedSupportIds.size}/${growth.maxSockets} Links",
              style = MaterialTheme.typography.labelLarge,
              color = if (ready) GildedBrass else WarmParchment,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            // Live PoE2-style connected Copper Support Sockets
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              for (slotIdx in 0 until CharacterGrowthState.MAX_POSSIBLE_SOCKETS) {
                val unlocked = slotIdx < growth.maxSockets
                val supportId = growth.socketedSupportIds.getOrNull(slotIdx)
                val supportDef = supportId?.let { ProgressionCatalog.supportByIdOrNull(it) }
                if (slotIdx > 0) {
                  Box(
                    modifier = Modifier
                      .width(8.dp)
                      .height(1.5.dp)
                      .background(if (supportDef != null) GildedBrass else SmokedBorder),
                  )
                }
                Surface(
                  color = when {
                    supportDef != null -> VermilionWax.copy(alpha = 0.30f)
                    unlocked -> ObsidianVoid
                    else -> ObsidianVoid.copy(alpha = 0.4f)
                  },
                  shape = RoundedCornerShape(4.dp),
                  border = BorderStroke(
                    width = 1.dp,
                    color = when {
                      supportDef != null -> GildedBrass
                      unlocked -> KilnBrass.copy(alpha = 0.7f)
                      else -> SmokedBorder.copy(alpha = 0.45f)
                    },
                  ),
                ) {
                  Text(
                    text = when {
                      supportDef != null -> "● ${supportDef.shortTag}"
                      unlocked -> "○ OPEN"
                      else -> "🔒 Lv.${if (slotIdx == 1) 3 else 6}"
                    },
                    fontSize = 8.sp,
                    fontFamily = IbmPlexMonoFamily,
                    color = when {
                      supportDef != null -> GildedBrass
                      unlocked -> WarmParchment
                      else -> MutedAsh.copy(alpha = 0.65f)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                  )
                }
              }
            }
          }
        }

        if (ready) {
          Button(
            onClick = onUnleash,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("trigger_overdrive_button"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = VermilionWax,
              contentColor = WarmParchment,
            ),
          ) {
            Text(
              text = "UNLEASH!",
              fontSize = 11.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.ExtraBold,
            )
          }
        } else {
          Text(
            text = "$charge%",
            fontSize = 12.sp,
            fontFamily = IbmPlexMonoFamily,
            fontWeight = FontWeight.Bold,
            color = KilnBrass,
            modifier = Modifier.testTag("overdrive_gauge_bar"),
          )
        }
      }

      // Pressurized brass calibration tube
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(ObsidianVoid)
          .border(0.75.dp, KilnBrass.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
      ) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(fraction)
            .background(
              Brush.horizontalGradient(
                listOf(KilnBrass, MoltenEmber, if (ready) VermilionHot else GildedBrass),
              ),
            ),
        )
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. SĀDHANA STAGE, UPĀYA CIRCUIT & TANTRIC RELIQUARY STRIP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TantricReliquaryStrip(
  growth: CharacterGrowthState,
  onOpenCircuit: () -> Unit,
  onInspectArtifact: (TantricArtifactDef) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .testTag("tantric_reliquary_strip"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Upāya Stage & Passive Circuit button
    val hasPoints = growth.unspentPassivePoints > 0
    Surface(
      onClick = onOpenCircuit,
      color = if (hasPoints) VermilionWax.copy(alpha = 0.25f) else CharredBasalt,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, if (hasPoints) VermilionHot else KilnBrass.copy(alpha = 0.7f)),
      modifier = Modifier.testTag("open_upaya_circuit_button"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          text = "Lv.${growth.level} ${growth.stageTitle()}",
          fontSize = 10.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.Bold,
          color = GildedBrass,
        )
        Text(
          text = if (hasPoints) "+${growth.unspentPassivePoints} PT!" else "${growth.unlockedPassiveIds.size}/12 Upāya",
          fontSize = 9.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = if (hasPoints) VermilionHot else MutedAsh,
        )
      }
    }

    // Bound Shaiva Tantric Ritual Artifacts (accumulated encounter-by-encounter!)
    if (growth.tantricArtifactIds.isEmpty()) {
      Text(
        text = "No Tantric Items bound yet — earn via encounters",
        fontSize = 9.sp,
        fontStyle = FontStyle.Italic,
        color = MutedAsh,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    } else {
      growth.tantricArtifactIds.forEach { artId ->
        val def = ProgressionCatalog.artifactByIdOrNull(artId) ?: return@forEach
        Surface(
          onClick = { onInspectArtifact(def) },
          color = CopperPlateTint,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, KilnBrass.copy(alpha = 0.75f)),
          modifier = Modifier.testTag("artifact_badge_${def.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(def.glyph, fontSize = 12.sp, color = GildedBrass)
            Text(
              text = def.name.substringBefore(" "),
              fontSize = 9.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.Bold,
              color = WarmParchment,
            )
          }
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. INTERACTIVE 3-BRANCH TRIKA UPĀYA PASSIVE CIRCUIT DIALOG (PoE2 Tree)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UpayaCircuitDialog(
  growth: CharacterGrowthState,
  onUnlockPassive: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CharredBasalt,
    titleContentColor = GildedBrass,
    textContentColor = WarmParchment,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Trika Upāya Circuit • Lv.${growth.level} ${growth.stageTitle()}",
          style = MaterialTheme.typography.titleLarge,
          color = GildedBrass,
        )
        Text(
          text = "XP ${growth.xp}/${growth.xpToNextLevel} • Unspent Points: ${growth.unspentPassivePoints} • Sockets: ${growth.socketedSupportIds.size}/${growth.maxSockets}",
          style = MaterialTheme.typography.labelSmall,
          color = if (growth.unspentPassivePoints > 0) VermilionHot else MutedAsh,
        )
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .testTag("brass_circuit_picker"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PassiveBranch.entries.forEach { branch ->
          item(key = branch.name) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianVoid)
                .border(1.dp, KilnBrass.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "${branch.displayName} (${branch.sanskritName})",
                style = MaterialTheme.typography.titleMedium,
                color = GildedBrass,
              )
              Text(
                text = branch.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MutedAsh,
              )

              ProgressionCatalog.passivesByBranch(branch).forEach { node ->
                val unlocked = growth.hasPassive(node.id)
                val available = growth.canUnlockPassive(node.id)
                val borderTint = when {
                  unlocked -> GildedBrass
                  available && node.isKeystone -> VermilionHot
                  available -> MoltenEmber
                  else -> SmokedBorder.copy(alpha = 0.55f)
                }
                Surface(
                  onClick = { if (available) onUnlockPassive(node.id) },
                  enabled = available,
                  color = when {
                    unlocked -> CopperPlateTint
                    available -> SlateStone
                    else -> CharredBasalt
                  },
                  shape = if (node.isKeystone) AttackCardShape else RoundedCornerShape(8.dp),
                  border = BorderStroke(if (node.isKeystone || unlocked) 1.5.dp else 1.dp, borderTint),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("passive_node_${node.id}"),
                ) {
                  Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(
                          text = if (unlocked) "✓ ${node.name}" else node.name,
                          style = MaterialTheme.typography.labelLarge,
                          color = if (unlocked) GildedBrass else if (node.isKeystone) VermilionHot else WarmParchment,
                        )
                        Text(
                          text = node.shortTag,
                          fontSize = 9.sp,
                          fontFamily = IbmPlexMonoFamily,
                          color = if (node.isKeystone) VermilionHot else KilnBrass,
                        )
                      }
                      Text(
                        text = node.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unlocked || available) WarmParchment else MutedAsh,
                        fontSize = 11.sp,
                      )
                    }
                    if (available) {
                      Surface(
                        color = VermilionWax,
                        shape = RoundedCornerShape(6.dp),
                      ) {
                        Text(
                          text = "ETCH",
                          fontSize = 9.sp,
                          fontFamily = IbmPlexMonoFamily,
                          fontWeight = FontWeight.ExtraBold,
                          color = WarmParchment,
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
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
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close Circuit", color = GildedBrass)
      }
    },
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. INLINE VICTORY SPOILS, CHARACTER GROWTH & SINGLE-TAP NEXT ROOM OVERLAY
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InlineSpoilsAndGrowthOverlay(
  run: RunState,
  onUnlockPassive: (String) -> Unit,
  onSocketSupport: (String) -> Unit,
  onSkipSupport: () -> Unit,
  onBindArtifact: (String) -> Unit,
  onSkipArtifact: () -> Unit,
  onClaimRelic: () -> Unit,
  onSkipRelic: () -> Unit,
  onPickCard: (String) -> Unit,
  onSkipCard: () -> Unit,
  onRerollCards: () -> Unit,
  onAdvanceToNode: (String) -> Unit,
  onOpenFullMap: () -> Unit,
  onOpenLegacyRewards: () -> Unit,
) {
  val reward = run.pendingReward ?: return
  val growth = run.growth
  val cardsResolved = reward.cardChoices.isEmpty() || reward.cardPicked
  val relicResolved = reward.relicRewardId == null || reward.relicPicked
  val readyToAdvance = cardsResolved && relicResolved

  // Preview next reachable nodes from current node (for 1-tap descent right out of victory!)
  val currentNode = remember(run.actMap, run.currentNodeId) {
    run.actMap.nodes.firstOrNull { it.id == run.currentNodeId }
  }
  val isActBoss = currentNode?.type == MapNodeType.BOSS
  val nextNodes = remember(run.actMap, currentNode) {
    if (currentNode == null || isActBoss) emptyList()
    else currentNode.nextNodeIds.mapNotNull { id -> run.actMap.nodes.firstOrNull { it.id == id } }
  }

  // First unlockable passive per branch for quick inline 1-tap etching!
  val quickPassives = remember(growth) {
    if (growth.unspentPassivePoints <= 0) emptyList()
    else PassiveBranch.entries.mapNotNull { branch ->
      ProgressionCatalog.passivesByBranch(branch).firstOrNull { growth.canUnlockPassive(it.id) }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid.copy(alpha = 0.88f))
      .padding(horizontal = 12.dp, vertical = 14.dp)
      .testTag("inline_spoils_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = CharredBasalt,
      shape = SkillCardShape,
      border = BorderStroke(1.5.dp, KilnBrass),
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp)
        .fillMaxHeight(0.92f)
        .copperplateEngraving(accent = KilnBrass),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Header: Victory + Sādhana XP & Spiritual Evolution Banner
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (reward.leveledUp) "SĀDHANA ASCENSION • LV.${growth.level}!" else "CHAMBER CONQUERED",
              style = MaterialTheme.typography.titleLarge,
              color = if (reward.leveledUp) VermilionHot else GildedBrass,
            )
            Text(
              text = "${growth.stageTitle()} • +${reward.xpEarned} XP (${growth.xp}/${growth.xpToNextLevel}) • +${reward.cindersEarned} Cinders",
              style = MaterialTheme.typography.labelSmall,
              color = WarmParchment,
            )
            if (reward.unlockedNewSocket) {
              Text(
                text = "★ NEW DHĀRAṆĀ SKILL SOCKET UNLOCKED (${growth.maxSockets} LINKS)!",
                fontSize = 10.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = GildedBrass,
              )
            }
          }
          Surface(
            color = ObsidianVoid,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, KilnBrass),
          ) {
            Text(
              text = "${run.cinders} C",
              fontSize = 11.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.Bold,
              color = GildedBrass,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // STEP A: Inline Upāya Passive Point Allocation (appears right on Level-Up!)
          if (quickPassives.isNotEmpty()) {
            item(key = "inline_passives") {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(CopperPlateTint)
                  .border(1.dp, VermilionHot, RoundedCornerShape(10.dp))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "ETCH UPĀYA PASSIVE (${growth.unspentPassivePoints} PT AVAILABLE)",
                  fontSize = 11.sp,
                  fontFamily = IbmPlexMonoFamily,
                  fontWeight = FontWeight.ExtraBold,
                  color = GildedBrass,
                )
                quickPassives.forEach { node ->
                  Surface(
                    onClick = { onUnlockPassive(node.id) },
                    color = CharredBasalt,
                    shape = if (node.isKeystone) AttackCardShape else RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (node.isKeystone) VermilionHot else KilnBrass),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("inline_passive_${node.id}"),
                  ) {
                    Row(
                      modifier = Modifier.padding(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "${node.branch.displayName}: ${node.name} (${node.shortTag})",
                          style = MaterialTheme.typography.labelLarge,
                          color = if (node.isKeystone) VermilionHot else GildedBrass,
                        )
                        Text(
                          text = node.summary,
                          style = MaterialTheme.typography.bodyMedium,
                          color = WarmParchment,
                          fontSize = 11.sp,
                        )
                      }
                      Text("ETCH", fontSize = 10.sp, fontFamily = IbmPlexMonoFamily, fontWeight = FontWeight.ExtraBold, color = VermilionHot)
                    }
                  }
                }
              }
            }
          }

          // STEP B: Socket Dhāraṇā Support Engraving into Signature Skill (PoE2 Support Gem!)
          if (!reward.supportPicked && reward.supportChoices.isNotEmpty()) {
            item(key = "inline_supports") {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(ObsidianVoid)
                  .border(1.dp, KilnBrass.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "SOCKET DHĀRAṆĀ SUPPORT (${growth.socketedSupportIds.size}/${growth.maxSockets} LINKS)",
                    fontSize = 10.sp,
                    fontFamily = IbmPlexMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = GildedBrass,
                  )
                  TextButton(
                    onClick = onSkipSupport,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("inline_skip_support"),
                  ) {
                    Text("Melt +8C", fontSize = 10.sp, color = MutedAsh)
                  }
                }
                reward.supportChoices.forEach { supId ->
                  val sup = ProgressionCatalog.supportByIdOrNull(supId) ?: return@forEach
                  Surface(
                    onClick = { onSocketSupport(sup.id) },
                    color = SlateStone,
                    shape = SkillCardShape,
                    border = BorderStroke(1.dp, MoltenEmber.copy(alpha = 0.8f)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("inline_socket_support_${sup.id}"),
                  ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(sup.name, style = MaterialTheme.typography.labelLarge, color = GildedBrass)
                        Text(sup.shortTag, fontSize = 10.sp, fontFamily = IbmPlexMonoFamily, color = VermilionHot, fontWeight = FontWeight.Bold)
                      }
                      Text(sup.traditionNote, fontSize = 9.sp, fontStyle = FontStyle.Italic, color = MutedAsh)
                      Text(sup.summary, fontSize = 11.sp, color = WarmParchment)
                    }
                  }
                }
              }
            }
          }

          // STEP C: Bind Authentic Shaiva Tantric Ritual Artifact!
          if (!reward.artifactPicked && reward.artifactChoices.isNotEmpty()) {
            item(key = "inline_artifacts") {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(ObsidianVoid)
                  .border(1.dp, HueViolet.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "BIND SACRED TANTRIC ARTIFACT",
                    fontSize = 10.sp,
                    fontFamily = IbmPlexMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = HueViolet,
                  )
                  TextButton(
                    onClick = onSkipArtifact,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("inline_skip_artifact"),
                  ) {
                    Text("Melt +8C", fontSize = 10.sp, color = MutedAsh)
                  }
                }
                reward.artifactChoices.forEach { artId ->
                  val art = ProgressionCatalog.artifactByIdOrNull(artId) ?: return@forEach
                  Surface(
                    onClick = { onBindArtifact(art.id) },
                    color = CopperPlateTint,
                    shape = GuardCardShape,
                    border = BorderStroke(1.dp, KilnBrass),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("inline_bind_artifact_${art.id}"),
                  ) {
                    Row(
                      modifier = Modifier.padding(8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(art.glyph, fontSize = 20.sp, color = GildedBrass)
                      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                          Text(art.name, style = MaterialTheme.typography.labelLarge, color = GildedBrass)
                          Text(art.shortTag, fontSize = 9.sp, fontFamily = IbmPlexMonoFamily, color = HueViolet, fontWeight = FontWeight.Bold)
                        }
                        Text(art.summary, fontSize = 11.sp, color = WarmParchment)
                      }
                    }
                  }
                }
              }
            }
          }

          // STEP D: Elite/Boss Relic Drop
          if (reward.relicRewardId != null && !reward.relicPicked) {
            val relic = GameCatalog.relicById(reward.relicRewardId)
            item(key = "inline_relic") {
              Surface(
                color = SlateStone,
                shape = GuardCardShape,
                border = BorderStroke(1.5.dp, GildedBrass),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text("Relic Found: ${relic.name}", style = MaterialTheme.typography.titleMedium, color = GildedBrass)
                  Text(relic.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = onClaimRelic,
                      modifier = Modifier.weight(1f).testTag("inline_claim_relic"),
                      colors = ButtonDefaults.buttonColors(containerColor = GildedBrass, contentColor = ObsidianVoid),
                    ) { Text("Claim Relic") }
                    OutlinedButton(
                      onClick = onSkipRelic,
                      modifier = Modifier.testTag("inline_skip_relic"),
                    ) { Text("Skip", color = MutedAsh) }
                  }
                }
              }
            }
          }

          // STEP E: Card Draft (or Skip for +12 Cinders / Transmute for 15 Cinders)
          if (!reward.cardPicked && reward.cardChoices.isNotEmpty()) {
            item(key = "inline_cards") {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "INSCRIBE 1 CARD TO GRIMOIRE",
                    fontSize = 10.sp,
                    fontFamily = IbmPlexMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = WarmParchment,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!reward.rerollUsed && run.cinders >= GameEngine.REROLL_CARDS_COST) {
                      OutlinedButton(
                        onClick = onRerollCards,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("inline_reroll_cards"),
                        border = BorderStroke(1.dp, GildedBrass),
                      ) {
                        Text("Reroll ${GameEngine.REROLL_CARDS_COST}C", fontSize = 11.sp, color = GildedBrass)
                      }
                    }
                    OutlinedButton(
                      onClick = onSkipCard,
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      modifier = Modifier.heightIn(min = 48.dp).testTag("inline_skip_card"),
                      border = BorderStroke(1.dp, MoltenEmber),
                    ) {
                      Text("Melt +${GameEngine.SKIP_CARD_CINDER_BONUS}C", fontSize = 11.sp, color = MoltenEmber)
                    }
                  }
                }
                reward.cardChoices.forEach { cardId ->
                  CardPillView(
                    cardDef = GameCatalog.cardById(cardId),
                    upgraded = false,
                    onClick = { onPickCard(cardId) },
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("inline_pick_card_$cardId"),
                  )
                }
              }
            }
          }
        }

        // FOOTER: Single-Tap Next Room Crossroads (no repetitive map screen bounce!)
        if (readyToAdvance) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (nextNodes.isNotEmpty()) {
              Text(
                text = "DESCEND IMMEDIATELY — ROW ${nextNodes.first().row}",
                fontSize = 10.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = GildedBrass,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                nextNodes.forEach { nextNode ->
                  val nextTwist = ArenaTwist.forNode(run.runSeed, nextNode.id, nextNode.type)
                  Surface(
                    onClick = { onAdvanceToNode(nextNode.id) },
                    color = CopperPlateTint,
                    shape = AttackCardShape,
                    border = BorderStroke(1.5.dp, VermilionHot),
                    modifier = Modifier
                      .weight(1f)
                      .defaultMinSize(minHeight = 48.dp)
                      .testTag("inline_next_node_${nextNode.id}"),
                  ) {
                    Column(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                      verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Text(
                        text = nextNode.type.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmParchment,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = if (nextTwist != ArenaTwist.NONE) nextTwist.shortTag else "ENTER ROOM →",
                        fontSize = 9.sp,
                        fontFamily = IbmPlexMonoFamily,
                        color = GildedBrass,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onOpenFullMap,
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("inline_open_full_map"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (nextNodes.isEmpty()) GildedBrass else SlateStone,
                  contentColor = if (nextNodes.isEmpty()) ObsidianVoid else WarmParchment,
                ),
              ) {
                Text(if (isActBoss) "Advance to Act ${run.currentAct + 1} →" else "Inspect 15-Row Map")
              }
            }
          }
        } else {
          // Keep backward-compatible button tag so legacy flows/tests can still open standalone RewardsScreen if desired
          OutlinedButton(
            onClick = onOpenLegacyRewards,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("combat_outcome_continue"),
            border = BorderStroke(1.dp, SmokedBorder),
          ) {
            Text("Open Full Spoils Screen", color = MutedAsh)
          }
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. MIND ILLUSION & EMOTIONAL ATTACHMENT BAR (VIKALPA / RĀGA / SĀKṢĪ WITNESS)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MindIllusionBar(
  mind: MindIllusionState,
  canWitness: Boolean,
  onWitness: () -> Unit,
  onInspectIllusion: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val illusion = mind.illusion
  val hueAccent = hueColor(illusion.hue)
  val borderColor = when {
    mind.shattered -> GildedBrass
    mind.isEntangled -> VermilionHot
    mind.attachment >= 3 -> MoltenEmber
    else -> KilnBrass.copy(alpha = 0.6f)
  }
  val bgColor = when {
    mind.shattered -> CopperPlateTint
    mind.isEntangled -> VermilionWax.copy(alpha = 0.22f)
    else -> CharredBasalt
  }
  val attachFraction = (mind.attachment.toFloat() / MindIllusionState.MAX_ATTACHMENT.toFloat()).coerceIn(0f, 1f)
  val witnessEnabled = canWitness && !mind.witnessUsedThisTurn

  Surface(
    color = bgColor,
    shape = RoundedCornerShape(9.dp),
    border = BorderStroke(if (mind.isEntangled || mind.shattered) 1.5.dp else 1.dp, borderColor),
    modifier = modifier
      .fillMaxWidth()
      .testTag("mind_illusion_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .clickable(onClick = onInspectIllusion),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = illusion.hue.glyph,
              fontSize = 11.sp,
              color = if (mind.shattered) GildedBrass else hueAccent,
              fontWeight = FontWeight.Black,
            )
            Text(
              text = if (mind.shattered) {
                "✦ PRATYABHIJÑĀ • ${illusion.veilTitle.uppercase()} CONQUERED"
              } else {
                "${illusion.veilTitle.uppercase()} (${illusion.emotionName})"
              },
              fontSize = 9.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.ExtraBold,
              color = if (mind.shattered) GildedBrass else if (mind.isEntangled) VermilionHot else WarmParchment,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          if (!mind.shattered) {
            // 3 Illusion Veil Seals (◆ intact veil, ◇ pierced)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              for (i in 0 until MindIllusionState.MAX_VEIL_LAYERS) {
                val intact = i < mind.veilLayers
                Text(
                  text = if (intact) "◆" else "◇",
                  fontSize = 10.sp,
                  color = if (intact) VermilionHot else GildedBrass,
                )
              }
            }
          }
        }

        // Emotional Attachment (Rāga) Bar + Status
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = mind.mindStateLabel(),
            fontSize = 8.sp,
            fontFamily = IbmPlexMonoFamily,
            fontWeight = FontWeight.Bold,
            color = when {
              mind.shattered -> GildedBrass
              mind.isEntangled -> VermilionHot
              mind.attachment >= 3 -> MoltenEmber
              else -> MutedAsh
            },
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(ObsidianVoid)
              .border(0.5.dp, SmokedBorder, RoundedCornerShape(2.dp)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (mind.shattered) 1f else attachFraction)
                .background(
                  when {
                    mind.shattered -> GildedBrass
                    mind.isEntangled -> VermilionHot
                    else -> hueAccent
                  }
                ),
            )
          }
        }
      }

      // One-tap SĀKṢĪ WITNESS button (conscious letting-go of emotional attachment!)
      Surface(
        onClick = { if (witnessEnabled) onWitness() },
        enabled = witnessEnabled,
        color = when {
          !witnessEnabled -> ObsidianVoid
          mind.isEntangled -> VermilionWax
          mind.attachment > 0 -> CopperPlateTint
          else -> SlateStone
        },
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(
          width = if (witnessEnabled && mind.attachment > 0) 1.5.dp else 1.dp,
          color = when {
            !witnessEnabled -> SmokedBorder.copy(alpha = 0.45f)
            mind.isEntangled -> WarmParchment
            else -> GildedBrass
          },
        ),
        modifier = Modifier
          .defaultMinSize(minHeight = 32.dp)
          .testTag("witness_illusion_button"),
      ) {
        Text(
          text = when {
            mind.witnessUsedThisTurn -> "WITNESSED ✓"
            mind.attachment > 0 -> "SĀKṢĪ • LET GO"
            else -> "SĀKṢĪ • STILL"
          },
          fontSize = 9.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = when {
            !witnessEnabled -> MutedAsh.copy(alpha = 0.6f)
            mind.isEntangled -> WarmParchment
            else -> GildedBrass
          },
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. BOARD-FIRST COMPACT COMBAT PILLS (SLIM RIVAL SEALS + 1-ROW SYSTEM RIBBON)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Ultra-compact scalloped Wax-Seal intent pill designed to sit inline inside the slim Rival Bar
 * without pushing the 7x7 puzzle board down. Preserves the `wax_seal_intent` semantics & tap dialog.
 */
@Composable
fun CompactWaxSealBadge(
  intent: EnemyIntent,
  slotLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (waxColor, valueLabel, desc) = when (intent) {
    is EnemyIntent.Attack -> Triple(
      VermilionWax,
      if (intent.hits > 1) "${intent.damage}×${intent.hits}" else "${intent.damage}",
      "$slotLabel Strike for ${intent.damage} damage" + if (intent.hits > 1) " (${intent.hits} hits)" else "",
    )
    is EnemyIntent.SiphonFlask -> Triple(
      VermilionWax,
      "${intent.damage}!",
      "$slotLabel Siphon Strike for ${intent.damage} dmg and ${intent.flaskDrain} Flask",
    )
    is EnemyIntent.Defend -> Triple(
      KilnBrass,
      "+${intent.block}",
      "$slotLabel Guard for +${intent.block} Block",
    )
    is EnemyIntent.CorruptBoard -> Triple(
      Color(0xFF5A3B7A),
      "×${intent.count}",
      "$slotLabel Corrupt ${intent.count} board tiles",
    )
    is EnemyIntent.InflictBrand -> Triple(
      Color(0xFF8C2D46),
      if (intent.damage > 0) "${intent.damage}/B" else "B×${intent.count}",
      "$slotLabel Inflict Brand on ${intent.count} tiles",
    )
    is EnemyIntent.InflictFog -> Triple(
      Color(0xFF42526B),
      "FOG",
      "$slotLabel Shroud board in Fog (${intent.taps} moves)",
    )
    is EnemyIntent.SealColumn -> Triple(
      Color(0xFF5B4837),
      "SEAL",
      "$slotLabel Seal 1 board column (${intent.turns} turns)",
    )
  }
  val isStrike = intent is EnemyIntent.Attack || intent is EnemyIntent.SiphonFlask
  val isGuard = intent is EnemyIntent.Defend

  Surface(
    onClick = onClick,
    color = ObsidianVoid.copy(alpha = 0.92f),
    shape = RoundedCornerShape(9.dp),
    border = BorderStroke(1.2.dp, waxColor.copy(alpha = 0.85f)),
    modifier = modifier
      .height(38.dp)
      .semantics { contentDescription = desc }
      .testTag("wax_seal_intent"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Canvas(modifier = Modifier.size(18.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val sealPath = Path()
        val lobes = 8
        for (i in 0 until lobes * 2) {
          val angle = (i * PI / lobes).toFloat()
          val rad = if (i % 2 == 0) r * 0.98f else r * 0.82f
          val px = cx + rad * cos(angle)
          val py = cy + rad * sin(angle)
          if (i == 0) sealPath.moveTo(px, py) else sealPath.lineTo(px, py)
        }
        sealPath.close()
        drawPath(sealPath, color = waxColor)
        when {
          isStrike -> {
            val spear = Path().apply {
              moveTo(cx, cy - r * 0.44f)
              lineTo(cx + r * 0.34f, cy + r * 0.34f)
              lineTo(cx, cy + r * 0.14f)
              lineTo(cx - r * 0.34f, cy + r * 0.34f)
              close()
            }
            drawPath(spear, color = WarmParchment)
          }
          isGuard -> {
            drawCircle(
              color = WarmParchment,
              radius = r * 0.36f,
              center = Offset(cx, cy),
              style = Stroke(width = 1.4.dp.toPx()),
            )
          }
          else -> {
            drawCircle(color = WarmParchment, radius = r * 0.24f, center = Offset(cx, cy))
          }
        }
      }
      Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
        Text(
          text = if (slotLabel.endsWith("II")) "NEXT" else "NOW",
          fontSize = 7.sp,
          lineHeight = 8.sp,
          fontFamily = IbmPlexMonoFamily,
          color = MutedAsh,
        )
        Text(
          text = valueLabel,
          fontSize = 11.sp,
          lineHeight = 12.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = WarmParchment,
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * Single-row 38dp unified interactive system pill bar replacing four previously stacked cards:
 * 1) Mind Illusion veil & 1-tap Sākṣī Witness pill (`mind_illusion_bar` + `witness_illusion_button`)
 * 2) Signature Skill charge / 1-tap UNLEASH! pill (`signature_skill_plate` + `trigger_overdrive_button` / `overdrive_gauge_bar`)
 * 3) Sādhana Level, Upāya Circuit & Bound Artifacts pill (`tantric_reliquary_strip` + `open_upaya_circuit_button`)
 * 4) Compact Spanda × Vimarśa Multiplier pill (`kiln_ledger_hud`) -> tap to inspect full breakdown
 */
@Composable
fun CombatSystemPillRibbon(
  heroClass: HeroClass,
  growth: CharacterGrowthState,
  mind: MindIllusionState,
  charge: Int,
  turn: TurnState,
  breakdown: ScoringBreakdown?,
  canAct: Boolean,
  onWitness: () -> Unit,
  onInspectIllusion: () -> Unit,
  onUnleashSkill: () -> Unit,
  onInspectSkill: () -> Unit,
  onOpenCircuit: () -> Unit,
  onInspectLedger: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val illusion = mind.illusion
  val hueAccent = hueColor(illusion.hue)
  val attachFraction = (mind.attachment.toFloat() / MindIllusionState.MAX_ATTACHMENT.toFloat()).coerceIn(0f, 1f)
  val witnessEnabled = canAct && !mind.witnessUsedThisTurn

  val skillReady = charge >= PlayerCombatState.MAX_OVERDRIVE && canAct
  val chargeFraction = (charge.toFloat() / PlayerCombatState.MAX_OVERDRIVE.toFloat()).coerceIn(0f, 1f)

  val hasPoints = growth.unspentPassivePoints > 0

  val multTenths = breakdown?.finalMultTenths ?: turn.comboMultTenths
  val isBoosted = multTenths > TurnState.BASE_MULT_TENTHS
  val multText = "${multTenths / 10}.${multTenths % 10}×"
  val outputVal = breakdown?.finalOutput ?: 0

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(38.dp)
      .testTag("tantric_reliquary_strip"),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 1. Mind Illusion Veil + 1-tap Sākṣī Witness pill
    Surface(
      color = when {
        mind.shattered -> CopperPlateTint
        mind.isEntangled -> VermilionWax.copy(alpha = 0.24f)
        else -> CharredBasalt
      },
      shape = RoundedCornerShape(9.dp),
      border = BorderStroke(
        width = if (mind.isEntangled || mind.shattered) 1.4.dp else 1.dp,
        color = when {
          mind.shattered -> GildedBrass
          mind.isEntangled -> VermilionHot
          mind.attachment >= 3 -> MoltenEmber
          else -> KilnBrass.copy(alpha = 0.55f)
        },
      ),
      modifier = Modifier
        .weight(1.42f)
        .fillMaxHeight()
        .testTag("mind_illusion_bar"),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .clickable(onClick = onInspectIllusion),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = illusion.hue.glyph,
              fontSize = 9.sp,
              lineHeight = 10.sp,
              color = if (mind.shattered) GildedBrass else hueAccent,
              fontWeight = FontWeight.Black,
            )
            if (mind.shattered) {
              Text(
                text = "CLEAR",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = GildedBrass,
                maxLines = 1,
              )
            } else {
              for (i in 0 until MindIllusionState.MAX_VEIL_LAYERS) {
                Text(
                  text = if (i < mind.veilLayers) "◆" else "◇",
                  fontSize = 8.sp,
                  lineHeight = 9.sp,
                  color = if (i < mind.veilLayers) VermilionHot else GildedBrass,
                )
              }
            }
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(ObsidianVoid),
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (mind.shattered) 1f else attachFraction)
                .background(if (mind.shattered) GildedBrass else if (mind.isEntangled) VermilionHot else hueAccent),
            )
          }
        }

        Surface(
          onClick = { if (witnessEnabled) onWitness() },
          enabled = witnessEnabled,
          color = when {
            !witnessEnabled -> ObsidianVoid
            mind.isEntangled -> VermilionWax
            mind.attachment > 0 -> CopperPlateTint
            else -> SlateStone
          },
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(
            width = 1.dp,
            color = when {
              !witnessEnabled -> SmokedBorder.copy(alpha = 0.45f)
              mind.isEntangled -> WarmParchment
              else -> GildedBrass
            },
          ),
          modifier = Modifier
            .fillMaxHeight()
            .testTag("witness_illusion_button"),
        ) {
          Box(
            modifier = Modifier.padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (mind.witnessUsedThisTurn) "✓" else "SĀKṢĪ",
              fontSize = 8.sp,
              lineHeight = 9.sp,
              fontFamily = IbmPlexMonoFamily,
              fontWeight = FontWeight.ExtraBold,
              color = if (!witnessEnabled) MutedAsh.copy(alpha = 0.6f) else if (mind.isEntangled) WarmParchment else GildedBrass,
              maxLines = 1,
            )
          }
        }
      }
    }

    // 2. Signature Skill Charge / 1-tap UNLEASH! pill
    Box(
      modifier = Modifier
        .weight(1.0f)
        .fillMaxHeight()
        .testTag("signature_skill_plate"),
    ) {
      Surface(
        onClick = { if (skillReady) onUnleashSkill() else onInspectSkill() },
        color = if (skillReady) VermilionWax else CharredBasalt,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(if (skillReady) 1.5.dp else 1.dp, if (skillReady) WarmParchment else KilnBrass.copy(alpha = 0.6f)),
        modifier = Modifier
          .fillMaxSize()
          .testTag(if (skillReady) "trigger_overdrive_button" else "overdrive_gauge_bar"),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
        if (skillReady) {
          Text(
            text = "⚡ UNLEASH",
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontFamily = IbmPlexMonoFamily,
            fontWeight = FontWeight.ExtraBold,
            color = WarmParchment,
            maxLines = 1,
          )
        } else {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${growth.socketedSupportIds.size}/${growth.maxSockets}✦",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontFamily = IbmPlexMonoFamily,
                color = GildedBrass,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "$charge%",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontFamily = IbmPlexMonoFamily,
                color = WarmParchment,
                fontWeight = FontWeight.ExtraBold,
              )
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ObsidianVoid),
            ) {
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  .fillMaxWidth(chargeFraction)
                  .background(Brush.horizontalGradient(listOf(KilnBrass, MoltenEmber))),
              )
            }
          }
        }
      }
    }
    }

    // 3. Sādhana Level / Upāya Circuit & Tantric Reliquary trigger pill
    Surface(
      onClick = onOpenCircuit,
      color = if (hasPoints) VermilionWax.copy(alpha = 0.25f) else CharredBasalt,
      shape = RoundedCornerShape(9.dp),
      border = BorderStroke(1.dp, if (hasPoints) VermilionHot else KilnBrass.copy(alpha = 0.6f)),
      modifier = Modifier
        .weight(0.82f)
        .fillMaxHeight()
        .testTag("open_upaya_circuit_button"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = if (hasPoints) "Lv.${growth.level} +${growth.unspentPassivePoints}" else "Lv.${growth.level}",
          fontSize = 9.sp,
          lineHeight = 10.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = if (hasPoints) VermilionHot else GildedBrass,
          maxLines = 1,
        )
        Text(
          text = "Upāya • ${growth.tantricArtifactIds.size}✦",
          fontSize = 7.sp,
          lineHeight = 8.sp,
          fontFamily = IbmPlexMonoFamily,
          color = MutedAsh,
          maxLines = 1,
        )
      }
    }

    // 4. Compact Spanda × Vimarśa Ledger pill (tap opens full scoring breakdown dialog)
    Surface(
      onClick = onInspectLedger,
      color = if (isBoosted) VermilionWax.copy(alpha = 0.24f) else CharredBasalt,
      shape = RoundedCornerShape(9.dp),
      border = BorderStroke(1.dp, if (isBoosted) VermilionHot else KilnBrass.copy(alpha = 0.55f)),
      modifier = Modifier
        .weight(0.76f)
        .fillMaxHeight()
        .testTag("kiln_ledger_hud"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = multText,
          fontSize = 10.sp,
          lineHeight = 11.sp,
          fontFamily = IbmPlexMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = if (isBoosted) GildedBrass else WarmParchment,
          maxLines = 1,
        )
        Text(
          text = if (outputVal > 0) "=$outputVal" else "MULT",
          fontSize = 7.sp,
          lineHeight = 8.sp,
          fontFamily = IbmPlexMonoFamily,
          color = if (outputVal > 0) MoltenEmber else MutedAsh,
          maxLines = 1,
        )
      }
    }
  }
}


