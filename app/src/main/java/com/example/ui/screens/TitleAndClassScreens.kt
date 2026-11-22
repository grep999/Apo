package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SoundtrackUiState
import com.example.data.ActiveRunEntity
import com.example.engine.CampaignGenerator
import com.example.engine.GameCatalog
import com.example.engine.HeroClass
import com.example.engine.ProgressionCatalog
import com.example.ui.components.ClassEmblemArt
import com.example.ui.components.EmberfallTopBar
import com.example.ui.components.SkillCardShape
import com.example.ui.components.TitleSoundtrackStatusPill
import com.example.ui.components.copperplateEngraving
import com.example.ui.components.hueColor
import com.example.ui.theme.*

@Composable
fun TitleScreen(
  activeRun: ActiveRunEntity?,
  audioEnabled: Boolean = true,
  soundtrackState: SoundtrackUiState = SoundtrackUiState(),
  corruptSaveError: String? = null,
  onToggleAudioMute: () -> Unit = {},
  onOpenSoundtrackStudio: () -> Unit = {},
  onDismissCorruptSave: () -> Unit = {},
  onDiscardCorruptSave: () -> Unit = {},
  onNewExpedition: () -> Unit,
  onQuickDive: () -> Unit = {},
  onContinueRun: () -> Unit,
  onOpenCompendium: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_hero_citadel),
      contentDescription = "Emberfall Citadel Backdrop",
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
      alpha = 0.72f,
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              ObsidianVoid.copy(alpha = 0.35f),
              ObsidianVoid.copy(alpha = 0.65f),
              ObsidianVoid.copy(alpha = 0.96f),
            )
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TitleSoundtrackStatusPill(
            state = soundtrackState,
            musicEnabled = audioEnabled,
            onClick = onOpenSoundtrackStudio,
            modifier = Modifier.weight(1f, fill = false),
          )
          Spacer(Modifier.width(10.dp))
          OutlinedIconButton(
            onClick = onToggleAudioMute,
            modifier = Modifier
              .size(48.dp)
              .testTag("title_audio_toggle"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (audioEnabled) GildedBrass.copy(alpha = 0.7f) else SmokedBorder),
            colors = IconButtonDefaults.outlinedIconButtonColors(
              containerColor = CharredBasalt.copy(alpha = 0.78f),
              contentColor = if (audioEnabled) GildedBrass else MutedAsh,
            ),
          ) {
            Icon(
              imageVector = if (audioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = if (audioEnabled) "Mute soundtrack and effects" else "Unmute soundtrack and effects",
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text = "EMBERFALL",
          style = MaterialTheme.typography.displayLarge,
          color = WarmParchment,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.tagline).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = GildedBrass,
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (activeRun != null) {
          Surface(
            onClick = onContinueRun,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 64.dp)
              .testTag("continue_run_button"),
            color = SlateStone.copy(alpha = 0.94f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, GildedBrass),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                  text = stringResource(R.string.action_continue_run),
                  style = MaterialTheme.typography.titleMedium,
                  color = GildedBrass,
                )
                Text(
                  text = "${activeRun.heroClassName} • Act ${activeRun.act} • HP ${activeRun.hp}/${activeRun.maxHp} • Flasks ${activeRun.flasks}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = WarmParchment.copy(alpha = 0.85f),
                )
              }
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Continue Expedition",
                tint = GildedBrass,
              )
            }
          }
        }

        Button(
          onClick = onNewExpedition,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("new_expedition_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MoltenEmber,
            contentColor = ObsidianVoid,
          ),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(
            text = stringResource(R.string.action_new_run),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(
          onClick = onQuickDive,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("quick_dive_button"),
          border = BorderStroke(1.5.dp, GildedBrass),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CharredBasalt.copy(alpha = 0.88f),
            contentColor = GildedBrass,
          ),
        ) {
          Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = GildedBrass,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Quick Dive — Ashen Monk (Lv.1 Āṇava)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GildedBrass,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onOpenCompendium,
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("compendium_button"),
            border = BorderStroke(1.dp, SmokedBorder),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmParchment),
          ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = "Codex", style = MaterialTheme.typography.labelLarge)
          }

          OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("settings_button"),
            border = BorderStroke(1.dp, SmokedBorder),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarmParchment),
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = "Settings", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }

    if (corruptSaveError != null) {
      AlertDialog(
        onDismissRequest = onDismissCorruptSave,
        modifier = Modifier.testTag("corrupt_save_dialog"),
        containerColor = CharredBasalt,
        titleContentColor = MoltenEmber,
        textContentColor = WarmParchment,
        title = { Text("Saved Expedition Unreadable") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "The stored expedition snapshot could not be restored because its data is incompatible or corrupted.",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "Detail: $corruptSaveError",
              style = MaterialTheme.typography.labelSmall,
              color = MutedAsh,
            )
          }
        },
        confirmButton = {
          Button(
            onClick = onDiscardCorruptSave,
            modifier = Modifier.testTag("discard_corrupt_save_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = CrimsonRuin,
              contentColor = WarmParchment,
            ),
          ) {
            Text("Discard Save")
          }
        },
        dismissButton = {
          TextButton(onClick = onDismissCorruptSave) {
            Text("Keep", color = GildedBrass)
          }
        },
      )
    }
  }
}

@Composable
fun ClassSelectScreen(
  onBack: () -> Unit,
  onStartRun: (HeroClass, String, Boolean, Int) -> Unit,
) {
  var selectedClass by remember { mutableStateOf(HeroClass.ASHEN_MONK) }
  var seedInput by remember { mutableStateOf("EF-${(100000..999999).random()}") }
  var unlitCovenant by remember { mutableStateOf(false) }
  var dreadLevel by remember { mutableIntStateOf(0) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(
      title = "Choose Your Vessel",
      subtitle = "Ashen Monk • Quarry Witch • The Unlit",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp)
        .testTag("class_select_list"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(bottom = 16.dp),
    ) {
      items(HeroClass.entries) { hero ->
        val isSelected = hero == selectedClass
        val relic = GameCatalog.relicById(hero.signatureRelicId)
        val starterCount = GameCatalog.starterDeckIds(hero).size
        val signatureSkill = ProgressionCatalog.signatureSkillFor(hero)
        val isMonkSpotlight = hero == HeroClass.ASHEN_MONK

        Surface(
          onClick = {
            selectedClass = hero
            if (hero != HeroClass.THE_UNLIT) {
              unlitCovenant = false
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .copperplateEngraving(
              accent = if (isSelected) VermilionHot else KilnBrass,
              showCrossHatch = isMonkSpotlight && isSelected,
            )
            .testTag("class_card_${hero.name.lowercase()}"),
          color = if (isSelected) SlateStone else CharredBasalt,
          shape = SkillCardShape,
          border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) VermilionHot else SmokedBorder),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ClassEmblemArt(
              heroClass = hero,
              modifier = Modifier.size(width = 86.dp, height = 114.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              if (isMonkSpotlight) {
                Surface(
                  color = VermilionWax.copy(alpha = 0.24f),
                  shape = RoundedCornerShape(4.dp),
                  border = BorderStroke(1.dp, VermilionHot.copy(alpha = 0.8f)),
                ) {
                  Text(
                    text = "TRIKA KASHMIR SHAIVISM • FEATURED SĀDHAKA",
                    style = MaterialTheme.typography.labelSmall,
                    color = GildedBrass,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
                Spacer(Modifier.height(4.dp))
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = hero.displayName,
                  style = MaterialTheme.typography.titleLarge,
                  color = if (isSelected) GildedBrass else WarmParchment,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Box(
                    Modifier
                      .size(12.dp)
                      .clip(RoundedCornerShape(3.dp))
                      .background(hueColor(hero.primaryHue))
                  )
                  Box(
                    Modifier
                      .size(12.dp)
                      .clip(RoundedCornerShape(3.dp))
                      .background(hero.secondaryHue?.let { hueColor(it) } ?: Color(0xFF5D5668))
                  )
                }
              }
              Text(
                text = if (isMonkSpotlight) {
                  "Āṇava Initiate → Jīvanmukta • ${hero.affinitySummary}"
                } else {
                  "${hero.title} • ${hero.affinitySummary}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = GildedBrass,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = if (isMonkSpotlight) {
                  "Starts humble at Lv.1 (1.0× Vimarśa, 0 items). Grows every chamber via 3 Upāya paths, Dhāraṇā skill sockets, and sacred Shaiva Tantric items."
                } else {
                  hero.description
                },
                style = MaterialTheme.typography.bodyMedium,
                color = WarmParchment.copy(alpha = 0.88f),
              )
              Spacer(Modifier.height(5.dp))
              Text(
                text = "Lv.1 Skill: ${signatureSkill.name} (${signatureSkill.baseHeat} Spanda • 1 Open Link)",
                style = MaterialTheme.typography.labelSmall,
                color = VermilionHot,
                fontWeight = FontWeight.Bold,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = "HP ${hero.maxHp} • Flasks ${hero.startingFlasks}/${hero.maxFlasks} • $starterCount-card Deck • Relic: ${relic.name}",
                style = MaterialTheme.typography.labelSmall,
                color = VerdantHeal,
              )
            }
          }
        }
      }

      item {
        val isUnlitSelected = selectedClass == HeroClass.THE_UNLIT
        Surface(
          color = CharredBasalt,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SmokedBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Unlit Covenant (ADR-017)",
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isUnlitSelected) GildedBrass else MutedAsh,
                )
                Text(
                  text = if (isUnlitSelected) {
                    "Bind Covenant: 3 Ash-only no-card turns vs The Hearthkeeper unlock Dread ending."
                  } else {
                    "Exclusive to The Unlit — select The Unlit to bind this Covenant."
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MutedAsh,
                )
              }
              Switch(
                checked = isUnlitSelected && unlitCovenant,
                enabled = isUnlitSelected,
                onCheckedChange = { if (isUnlitSelected) unlitCovenant = it },
                modifier = Modifier.testTag("toggle_unlit_covenant"),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Dread Tier ($dreadLevel / 3)", style = MaterialTheme.typography.labelMedium, color = WarmParchment)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { lvl ->
                  FilterChip(
                    selected = dreadLevel == lvl,
                    onClick = { dreadLevel = lvl },
                    label = { Text("$lvl") },
                  )
                }
              }
            }
            Text(
              text = CampaignGenerator.dreadTierLabel(dreadLevel),
              style = MaterialTheme.typography.bodySmall,
              color = if (dreadLevel == 0) MutedAsh else MoltenEmber,
              modifier = Modifier.testTag("dread_tier_description"),
            )
            Text(
              text = stringResource(R.string.label_seed),
              style = MaterialTheme.typography.labelMedium,
              color = GildedBrass,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = seedInput,
                onValueChange = { seedInput = it },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("seed_input"),
                textStyle = MaterialTheme.typography.labelMedium,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MoltenEmber,
                  unfocusedBorderColor = SmokedBorder,
                  focusedTextColor = WarmParchment,
                  unfocusedTextColor = WarmParchment,
                ),
              )
              Spacer(Modifier.width(8.dp))
              IconButton(
                onClick = { seedInput = "EF-${(100000..999999).random()}" },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("randomize_seed_button"),
              ) {
                Icon(Icons.Default.Casino, contentDescription = "Randomize Seed", tint = GildedBrass)
              }
            }
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Button(
        onClick = { onStartRun(selectedClass, seedInput, unlitCovenant, dreadLevel) },
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("start_expedition_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = MoltenEmber,
          contentColor = ObsidianVoid,
        ),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = "Descend as ${selectedClass.displayName}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
