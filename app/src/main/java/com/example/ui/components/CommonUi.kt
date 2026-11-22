package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.*
import com.example.ui.theme.*

fun hueColor(hue: Hue): Color = when (hue) {
  Hue.EMBER -> HueEmber
  Hue.GOLD -> HueGold
  Hue.MOSS -> HueMoss
  Hue.TIDE -> HueTide
  Hue.VIOLET -> HueViolet
}

fun tileColor(kind: TileKind): Color = when (kind) {
  is TileKind.Playable -> hueColor(kind.hue)
  TileKind.Ash -> Color(0xFF4B4752)
  TileKind.Stone -> Color(0xFF6A6770)
  TileKind.Prism -> HuePrism
  TileKind.Empty -> ObsidianVoid
}

fun tileGlyph(kind: TileKind): String = when (kind) {
  is TileKind.Playable -> kind.hue.glyph
  TileKind.Ash -> "▲"
  TileKind.Stone -> "■"
  TileKind.Prism -> "★"
  TileKind.Empty -> ""
}

fun tileContentDescription(kind: TileKind): String = when (kind) {
  is TileKind.Playable -> kind.hue.displayName
  TileKind.Ash -> "Ash neutral tile"
  TileKind.Stone -> "Stone obstacle tile"
  TileKind.Prism -> "Prism catalyst tile"
  TileKind.Empty -> "Empty wall socket"
}

@Composable
fun ClassEmblemArt(
  heroClass: HeroClass,
  modifier: Modifier = Modifier,
) {
  val primary = hueColor(heroClass.primaryHue)
  val drawableRes = when (heroClass) {
    HeroClass.ASHEN_MONK -> R.drawable.ef_class_ashen_monk
    HeroClass.QUARRY_WITCH -> R.drawable.ef_class_quarry_witch
    HeroClass.THE_UNLIT -> R.drawable.ef_class_the_unlit
  }

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianVoid)
      .border(1.5.dp, primary.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = drawableRes),
      contentDescription = "${heroClass.displayName} portrait",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              Color.Transparent,
              ObsidianVoid.copy(alpha = 0.45f),
            )
          )
        )
    )
  }
}

@Composable
fun EmberfallTopBar(
  title: String,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(48.dp)
          .testTag("top_bar_back"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = WarmParchment,
        )
      }
      Spacer(Modifier.width(4.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = WarmParchment,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MutedAsh,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailingContent != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = trailingContent,
      )
    }
  }
}

@Composable
fun RunResourceBanner(run: RunState, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = CharredBasalt.copy(alpha = 0.94f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, SmokedBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StatBadge("HP", "${run.hp}/${run.maxHp}", CrimsonRuin)
      StatBadge("FLASKS", "${run.flasks}/${run.maxFlasks}", VerdantHeal)
      StatBadge("CINDERS", "${run.cinders}", GildedBrass)
      StatBadge("ACT", "${run.currentAct}/3", MoltenEmber)
    }
  }
}

@Composable
fun StatBadge(label: String, value: String, accent: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MutedAsh,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      color = accent,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
fun CardPillView(
  cardDef: CardDef,
  upgraded: Boolean,
  resources: ResourceState? = null,
  vigilAvailable: Int? = null,
  onClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val canPayInk = resources == null || GameEngine.tryPayCardCost(resources, cardDef, upgraded) != null
  val canPayVigil = vigilAvailable == null || vigilAvailable >= cardDef.vigilCost
  val canAfford = canPayInk && canPayVigil
  val frameColor = cardDef.frameHue?.let { hueColor(it) } ?: SmokedBorder
  val borderColor = when {
    upgraded -> GildedBrass
    canAfford && enabled -> frameColor
    else -> SmokedBorder
  }
  val bgBrush = Brush.verticalGradient(
    colors = listOf(
      if (canAfford && enabled) SlateStone else CharredBasalt,
      ObsidianVoid,
    )
  )

  val effectiveGeneric = (cardDef.genericCost - if (upgraded) cardDef.upgradedGenericDiscount else 0).coerceAtLeast(0)
  val cardShape = cardSilhouetteShape(cardDef)

  Surface(
    onClick = { if (onClick != null && enabled && canAfford) onClick() },
    enabled = onClick != null && enabled && canAfford,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .clip(cardShape)
      .background(bgBrush)
      .copperplateEngraving(
        accent = if (upgraded) GildedBrass else borderColor,
        showCrossHatch = cardDef.echo || cardDef.vigilBurnMax > 0,
      ),
    color = Color.Transparent,
    shape = cardShape,
    border = BorderStroke(if (canAfford && enabled) 1.5.dp else 1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (upgraded) "${cardDef.name}+" else cardDef.name,
          style = MaterialTheme.typography.titleMedium,
          color = if (upgraded) GildedBrass else WarmParchment,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          cardDef.coloredCost.forEach { (hue, count) ->
            repeat(count) {
              Box(
                modifier = Modifier
                  .size(18.dp)
                  .clip(CircleShape)
                  .background(hueColor(hue))
                  .border(1.dp, WarmParchment.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = hue.shortTag.take(1),
                  style = MaterialTheme.typography.labelSmall,
                  color = ObsidianVoid,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
          if (effectiveGeneric > 0) {
            Box(
              modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (canAfford) WarmParchment.copy(alpha = 0.85f) else SmokedBorder)
                .border(1.dp, GildedBrass, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = effectiveGeneric.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = ObsidianVoid,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
      Spacer(Modifier.height(3.dp))
      val frameLabel = cardDef.frameHue?.displayName ?: "Neutral"
      val metaTags = buildList {
        add("$frameLabel • ${cardDef.category.name}")
        if (cardDef.echo) add("ECHO")
        if (cardDef.vigilCost > 0) add("REQ ${cardDef.vigilCost}V")
        else if (cardDef.vigilBurnMax > 0) add("BURN V")
      }.joinToString(" • ")
      Text(
        text = metaTags,
        style = MaterialTheme.typography.labelSmall,
        color = frameColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = cardDef.description,
        style = MaterialTheme.typography.bodyMedium,
        color = if (canAfford) WarmParchment.copy(alpha = 0.9f) else MutedAsh,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
