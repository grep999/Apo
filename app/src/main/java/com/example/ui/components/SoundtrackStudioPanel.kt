package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.MusicMode
import com.example.audio.SoundtrackModelTier
import com.example.audio.SoundtrackPreset
import com.example.audio.SoundtrackSlot
import com.example.audio.SoundtrackSlotStatus
import com.example.audio.SoundtrackUiState
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Compact engraved brass status pill on the Title screen indicating live Lyria 3 soundtrack
 * composition/cache status. Tapping opens the Soundtrack Studio in Settings.
 */
@Composable
fun TitleSoundtrackStatusPill(
  state: SoundtrackUiState,
  musicEnabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val label = when {
    !musicEnabled -> "Score Muted • Open Studio"
    state.isGeneratingAny -> "Composing Lyria 3 Score..."
    state.cachedCount == 2 -> "Lyria 3 Score • 2 Loops Cached"
    state.cachedCount == 1 -> "Lyria 3 Score • 1/2 Cached"
    state.firstErrorMessage != null -> "Score Studio • Tap to Retry"
    else -> "Lyria 3 Soundtrack Studio"
  }
  val accent = when {
    !musicEnabled -> MutedAsh
    state.isGeneratingAny -> VermilionHot
    state.cachedCount > 0 -> GildedBrass
    state.firstErrorMessage != null -> CrimsonRuin
    else -> KilnBrass
  }

  Surface(
    onClick = onClick,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .testTag("title_soundtrack_pill"),
    color = CharredBasalt.copy(alpha = 0.86f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.72f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.isGeneratingAny) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          strokeWidth = 2.dp,
          color = accent,
        )
      } else {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = "Open Soundtrack Studio",
          tint = accent,
          modifier = Modifier.size(16.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = WarmParchment,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Copperplate & Kiln Brass Soundtrack Studio embedded in Settings.
 * Lets players audition Exploration & Combat loops, switch between `lyria-3-pro-preview` (~2m)
 * and `lyria-3-clip-preview` (30s), select acoustic/minimalist presets, compose variations,
 * and recover transparently from errors.
 */
@Composable
fun SoundtrackStudioPanel(
  state: SoundtrackUiState,
  musicEnabled: Boolean,
  onSelectTier: (SoundtrackModelTier) -> Unit,
  onSelectPreset: (SoundtrackPreset) -> Unit,
  onUpdateCustomDirection: (String) -> Unit,
  onAuditionMode: (MusicMode) -> Unit,
  onComposeSlot: (SoundtrackSlot) -> Unit,
  onRetryMissing: () -> Unit,
  onClearCache: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = CharredBasalt,
    shape = SkillCardShape,
    border = BorderStroke(1.5.dp, KilnBrass.copy(alpha = 0.72f)),
    modifier = modifier
      .fillMaxWidth()
      .copperplateEngraving(accent = KilnBrass, showCrossHatch = true)
      .testTag("soundtrack_studio_panel"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header + Brass Phase Visualizer
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "LYRIA 3 SOUNDTRACK STUDIO",
            style = MaterialTheme.typography.labelSmall,
            color = KilnBrass,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Acoustic Minimalist Score",
            style = MaterialTheme.typography.titleMedium,
            color = WarmParchment,
          )
          Text(
            text = "Interlocking piano, marimba & warm strings • Crossfade + SFX ducking",
            style = MaterialTheme.typography.bodySmall,
            color = MutedAsh,
          )
        }
        Spacer(Modifier.width(10.dp))
        BrassPhaseVisualizer(
          isActive = musicEnabled && (state.cachedCount > 0 || state.isGeneratingAny),
          isComposing = state.isGeneratingAny,
          modifier = Modifier.size(width = 68.dp, height = 40.dp),
        )
      }

      // Transparent Error Banner (surfaces real Firebase AI / App Check / Timeout errors)
      state.firstErrorMessage?.let { errMsg ->
        Surface(
          color = CrimsonRuin.copy(alpha = 0.16f),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, CrimsonRuin.copy(alpha = 0.7f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("soundtrack_error_banner"),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = "Soundtrack error",
              tint = CrimsonRuin,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = errMsg,
              style = MaterialTheme.typography.bodySmall,
              color = WarmParchment,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = onRetryMissing,
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("soundtrack_retry_button"),
            ) {
              Text("Retry", color = GildedBrass, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Exploration & Combat Deck Cards
      SoundtrackSlotDeckCard(
        slot = SoundtrackSlot.EXPLORATION,
        status = state.exploration,
        isAuditioning = state.activeMusicMode == MusicMode.EXPLORATION,
        selectedTier = state.selectedTier,
        onAudition = { onAuditionMode(MusicMode.EXPLORATION) },
        onCompose = { onComposeSlot(SoundtrackSlot.EXPLORATION) },
      )

      SoundtrackSlotDeckCard(
        slot = SoundtrackSlot.COMBAT,
        status = state.combat,
        isAuditioning = state.activeMusicMode == MusicMode.COMBAT,
        selectedTier = state.selectedTier,
        onAudition = { onAuditionMode(MusicMode.COMBAT) },
        onCompose = { onComposeSlot(SoundtrackSlot.COMBAT) },
      )

      HorizontalDivider(color = KilnBrass.copy(alpha = 0.30f))

      // Model Tier Selector: lyria-3-pro-preview (~2m) vs lyria-3-clip-preview (30s)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "GENERATION MODEL",
          style = MaterialTheme.typography.labelSmall,
          color = KilnBrass,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SoundtrackModelTier.entries.forEach { tier ->
            val selected = state.selectedTier == tier
            Surface(
              onClick = { onSelectTier(tier) },
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 52.dp)
                .testTag("soundtrack_tier_${tier.name.lowercase()}"),
              color = if (selected) SlateStone else ObsidianVoid.copy(alpha = 0.7f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) GildedBrass else SmokedBorder,
              ),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = tier.displayName,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (selected) GildedBrass else WarmParchment,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = tier.modelId,
                  style = MaterialTheme.typography.labelSmall,
                  color = MutedAsh,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      // Acoustic Palette Presets
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "ACOUSTIC PALETTE",
          style = MaterialTheme.typography.labelSmall,
          color = KilnBrass,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          SoundtrackPreset.entries.forEach { preset ->
            val selected = state.selectedPreset == preset
            Surface(
              onClick = { onSelectPreset(preset) },
              modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("soundtrack_preset_${preset.id}"),
              color = if (selected) SlateStone else ObsidianVoid.copy(alpha = 0.55f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) GildedBrass else SmokedBorder,
              ),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) GildedBrass else WarmParchment,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = preset.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedAsh,
                  )
                }
                if (selected) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Selected preset",
                    tint = GildedBrass,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      }

      // Optional Direction Input
      OutlinedTextField(
        value = state.customDirection,
        onValueChange = onUpdateCustomDirection,
        label = { Text("Optional Subtle Nuance (Instrumental Only)") },
        placeholder = { Text("e.g., warmer cello harmonics, softer marimba") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("soundtrack_custom_direction_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GildedBrass,
          unfocusedBorderColor = SmokedBorder,
          focusedTextColor = WarmParchment,
          unfocusedTextColor = WarmParchment,
          focusedLabelColor = GildedBrass,
          unfocusedLabelColor = MutedAsh,
        ),
      )

      // Bottom Utility Row: Compose Missing + Clear Cache
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onRetryMissing,
          enabled = !state.isGeneratingAny,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("soundtrack_compose_all_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = VermilionWax,
            contentColor = WarmParchment,
          ),
          shape = RoundedCornerShape(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (state.cachedCount == 2) "Regenerate Both" else "Compose Missing",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(
          onClick = onClearCache,
          enabled = state.cachedCount > 0 && !state.isGeneratingAny,
          modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("soundtrack_clear_cache_button"),
          border = BorderStroke(1.dp, SmokedBorder),
          shape = RoundedCornerShape(10.dp),
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Clear cached soundtrack files",
            tint = MutedAsh,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Reset", color = MutedAsh, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun SoundtrackSlotDeckCard(
  slot: SoundtrackSlot,
  status: SoundtrackSlotStatus,
  isAuditioning: Boolean,
  selectedTier: SoundtrackModelTier,
  onAudition: () -> Unit,
  onCompose: () -> Unit,
) {
  val statusLabel = when (status) {
    SoundtrackSlotStatus.Idle -> "Procedural Bridge • Not yet composed"
    is SoundtrackSlotStatus.Generating -> "Composing with ${status.modelId}..."
    is SoundtrackSlotStatus.Ready -> {
      val kb = (status.meta.byteSize / 1024L).coerceAtLeast(1L)
      "Cached • ${status.meta.modelTier.displayName} • ${kb}KB"
    }
    is SoundtrackSlotStatus.Error -> "Composition failed • Tap Compose to retry"
  }
  val borderColor = when {
    isAuditioning -> GildedBrass
    status is SoundtrackSlotStatus.Ready -> KilnBrass.copy(alpha = 0.55f)
    status is SoundtrackSlotStatus.Error -> CrimsonRuin.copy(alpha = 0.65f)
    else -> SmokedBorder
  }

  Surface(
    color = SlateStone.copy(alpha = 0.72f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(if (isAuditioning) 1.5.dp else 1.dp, borderColor),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("soundtrack_slot_${slot.id}"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${slot.displayName} Loop",
            style = MaterialTheme.typography.titleSmall,
            color = if (isAuditioning) GildedBrass else WarmParchment,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = slot.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MutedAsh,
          )
        }
        if (status is SoundtrackSlotStatus.Generating) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = VermilionHot,
          )
        } else if (isAuditioning) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Active deck",
            tint = GildedBrass,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelSmall,
        color = when (status) {
          is SoundtrackSlotStatus.Ready -> VerdantHeal
          is SoundtrackSlotStatus.Generating -> VermilionHot
          is SoundtrackSlotStatus.Error -> CrimsonRuin
          SoundtrackSlotStatus.Idle -> MutedAsh
        },
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onAudition,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("soundtrack_audition_${slot.id}"),
          border = BorderStroke(1.dp, if (isAuditioning) GildedBrass else SmokedBorder),
          shape = RoundedCornerShape(10.dp),
        ) {
          Icon(
            imageVector = if (isAuditioning) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isAuditioning) GildedBrass else WarmParchment,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = if (isAuditioning) "Active Deck" else "Audition",
            color = if (isAuditioning) GildedBrass else WarmParchment,
            style = MaterialTheme.typography.labelMedium,
          )
        }

        Button(
          onClick = onCompose,
          enabled = status !is SoundtrackSlotStatus.Generating,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("soundtrack_compose_${slot.id}"),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (status is SoundtrackSlotStatus.Ready) CharredBasalt else VermilionWax,
            contentColor = WarmParchment,
          ),
          border = if (status is SoundtrackSlotStatus.Ready) BorderStroke(1.dp, KilnBrass) else null,
          shape = RoundedCornerShape(10.dp),
        ) {
          Text(
            text = if (status is SoundtrackSlotStatus.Ready) {
              "Re-Compose"
            } else {
              "Compose (${if (selectedTier == SoundtrackModelTier.PRO_FULL_LOOP) "2m" else "30s"})"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

/**
 * Restrained brass phasing bars mirroring Steve Reich–inspired interlocking pulse cycles.
 */
@Composable
private fun BrassPhaseVisualizer(
  isActive: Boolean,
  isComposing: Boolean,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "brass_phase")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = (2.0 * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isComposing) 1600 else 3600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "phase_angle",
  )

  Canvas(modifier = modifier) {
    val barCount = 7
    val gap = 4.dp.toPx()
    val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
    val baseColor = if (isComposing) VermilionHot else GildedBrass

    for (i in 0 until barCount) {
      // Subtle prime-ratio phase offsets evoke interlocking minimalist pulses
      val wave = if (isActive) {
        0.28f + 0.62f * ((sin(phase * (1f + i * 0.14f) + i * 0.85f) + 1f) * 0.5f)
      } else {
        0.18f
      }
      val barH = size.height * wave
      val x = i * (barWidth + gap)
      val y = (size.height - barH) * 0.5f
      drawRect(
        color = baseColor.copy(alpha = if (isActive) 0.78f else 0.30f),
        topLeft = Offset(x, y),
        size = Size(barWidth, barH),
      )
    }
    drawLine(
      color = KilnBrass.copy(alpha = 0.35f),
      start = Offset(0f, size.height / 2f),
      end = Offset(size.width, size.height / 2f),
      strokeWidth = 1f,
    )
    if (isComposing) {
      drawRect(
        color = VermilionHot.copy(alpha = 0.45f),
        topLeft = Offset.Zero,
        size = size,
        style = Stroke(width = 1.dp.toPx()),
      )
    }
  }
}
