package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.MusicMode
import com.example.audio.SoundtrackModelTier
import com.example.audio.SoundtrackPreset
import com.example.audio.SoundtrackSlot
import com.example.audio.SoundtrackUiState
import com.example.data.PlayerSettings
import com.example.data.RunHistoryEntity
import com.example.engine.GameCatalog
import com.example.engine.GameEngine
import com.example.engine.GameOverReason
import com.example.engine.RunState
import com.example.ui.components.CardPillView
import com.example.ui.components.EmberfallTopBar
import com.example.ui.components.SoundtrackStudioPanel
import com.example.ui.theme.*

@Composable
fun RunSummaryScreen(
  run: RunState,
  onReturnToTitle: () -> Unit,
) {
  val title = when {
    run.runWon && run.dreadEndingAchieved -> "Dread Sovereign Extinguished"
    run.runWon -> "Ember Throne Reclaimed"
    run.gameOverReason == GameOverReason.ZERO_FLASKS -> "Zero-Flask Collapse"
    else -> "Extinguished in the Ash"
  }
  val accent = if (run.runWon) GildedBrass else CrimsonRuin

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(20.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.displayLarge,
        color = accent,
      )
      Text(
        text = "${run.heroClass.displayName} • Act ${run.currentAct} • Seed ${run.seedLabel} • Dread ${run.dreadLevel}",
        style = MaterialTheme.typography.titleMedium,
        color = WarmParchment,
      )
      Surface(
        color = CharredBasalt,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SummaryRow("Enemies Slain", "${run.stats.enemiesSlain}")
          SummaryRow("Turns Taken", "${run.stats.turnsTaken}")
          SummaryRow("Cards Played", "${run.stats.cardsPlayed}")
          SummaryRow("Prisms Detonated", "${run.stats.prismsDetonated}")
          SummaryRow("Ash Blocks Popped", "${run.stats.ashBlocksPopped}")
          SummaryRow("Stones Shattered", "${run.stats.stonesShattered}")
          SummaryRow("Peak Spectrum", GameEngine.formatSpectrum(run.stats.highestSpectrumHalfSteps))
          SummaryRow("Remnants Held", "${run.remnants.size}")
          SummaryRow("Final Deck Size", "${run.masterDeck.size} cards")
        }
      }
      Surface(
        color = SlateStone,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HueViolet.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Your final deck echoes into Act I Row 5 for future runs (Relics never transfer).",
          style = MaterialTheme.typography.bodyMedium,
          color = HueViolet,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    Button(
      onClick = onReturnToTitle,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("summary_return_title"),
      colors = ButtonDefaults.buttonColors(containerColor = MoltenEmber, contentColor = ObsidianVoid),
      shape = RoundedCornerShape(14.dp),
    ) {
      Text("Return to Citadel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun SummaryRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MutedAsh)
    Text(text = value, style = MaterialTheme.typography.labelMedium, color = WarmParchment, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun CompendiumScreen(
  history: List<RunHistoryEntity>,
  onBack: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf("Cards", "Relics", "Bestiary", "History")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = "Emberfall Codex", subtitle = "No Meta-Currency • Pure Mastery", onBack = onBack)

    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = CharredBasalt,
      contentColor = MoltenEmber,
    ) {
      tabs.forEachIndexed { index, label ->
        Tab(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          text = { Text(label, style = MaterialTheme.typography.labelMedium) },
        )
      }
    }

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      when (selectedTab) {
        0 -> items(GameCatalog.cards) { def ->
          CardPillView(cardDef = def, upgraded = false, modifier = Modifier.fillMaxWidth())
        }
        1 -> items(GameCatalog.relics) { relic ->
          Surface(
            color = CharredBasalt,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SmokedBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(relic.name, style = MaterialTheme.typography.titleMedium, color = GildedBrass)
              Spacer(Modifier.height(4.dp))
              Text(relic.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            }
          }
        }
        2 -> items(GameCatalog.enemies) { enemy ->
          Surface(
            color = CharredBasalt,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (enemy.isShade) HueViolet else SmokedBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "${enemy.name} (Act ${enemy.act})",
                style = MaterialTheme.typography.titleMedium,
                color = if (enemy.isShade) HueViolet else MoltenEmber,
              )
              Text(
                text = "${enemy.title} • HP ${enemy.maxHp} • 2 Telegraph Slots",
                style = MaterialTheme.typography.labelSmall,
                color = MutedAsh,
              )
            }
          }
        }
        else -> {
          if (history.isEmpty()) {
            item {
              Text(
                text = "No completed expeditions yet. Descend into Act I to record your first run.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedAsh,
              )
            }
          } else {
            items(history) { run ->
              Surface(
                color = CharredBasalt,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (run.victory) GildedBrass else SmokedBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                    text = "${run.heroClassName} — ${run.terminationReason}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (run.victory) GildedBrass else CrimsonRuin,
                  )
                  Text(
                    text = "Act ${run.actReached} • Seed ${run.seedLabel} • Slain ${run.enemiesSlain} • Peak ${GameEngine.formatSpectrum(run.highestSpectrumHalfSteps)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmParchment.copy(alpha = 0.85f),
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

@Composable
fun SettingsScreen(
  settings: PlayerSettings,
  soundtrackState: SoundtrackUiState = SoundtrackUiState(),
  onToggleMusic: (Boolean) -> Unit = {},
  onToggleSfx: (Boolean) -> Unit = {},
  onSelectSoundtrackTier: (SoundtrackModelTier) -> Unit = {},
  onSelectSoundtrackPreset: (SoundtrackPreset) -> Unit = {},
  onUpdateSoundtrackDirection: (String) -> Unit = {},
  onAuditionSoundtrackMode: (MusicMode) -> Unit = {},
  onComposeSoundtrackSlot: (SoundtrackSlot) -> Unit = {},
  onRetryMissingSoundtracks: () -> Unit = {},
  onClearSoundtrackCache: () -> Unit = {},
  onToggleColorblind: (Boolean) -> Unit,
  onToggleHints: (Boolean) -> Unit,
  onToggleFastAnimations: (Boolean) -> Unit,
  onToggleHaptics: (Boolean) -> Unit,
  onToggleOneTap: (Boolean) -> Unit,
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = "Settings & Field Guide", subtitle = "Lyria 3 Score, Audio & Rules", onBack = onBack)

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SettingToggleRow(
          title = "Lyria 3 Instrumental Soundtrack",
          description = "Acoustic minimalist + subtle minimal techno loops (Exploration & Combat crossfade with SFX ducking).",
          checked = settings.musicEnabled,
          onCheckedChange = onToggleMusic,
          tag = "toggle_music",
        )
      }
      item {
        SettingToggleRow(
          title = "Tactile Board & Spell SFX",
          description = "Spectrum pitch-scaled hue pops, cascades, and gentle music ducking on impacts.",
          checked = settings.sfxEnabled,
          onCheckedChange = onToggleSfx,
          tag = "toggle_sfx",
        )
      }
      item {
        SoundtrackStudioPanel(
          state = soundtrackState,
          musicEnabled = settings.musicEnabled && !settings.masterAudioMuted,
          onSelectTier = onSelectSoundtrackTier,
          onSelectPreset = onSelectSoundtrackPreset,
          onUpdateCustomDirection = onUpdateSoundtrackDirection,
          onAuditionMode = onAuditionSoundtrackMode,
          onComposeSlot = onComposeSoundtrackSlot,
          onRetryMissing = onRetryMissingSoundtracks,
          onClearCache = onClearSoundtrackCache,
        )
      }
      item {
        SettingToggleRow(
          title = "One-Tap Quick-Swap",
          description = "Tap any tile in a legal Match-3 swap to fire its top move immediately (off = manual 2-tap or drag swap).",
          checked = settings.oneTapBoardFire,
          onCheckedChange = onToggleOneTap,
          tag = "toggle_one_tap",
        )
      }
      item {
        SettingToggleRow(
          title = "Colorblind Hue Glyphs",
          description = "Display geometric symbols on Ember, Gold, Moss, Tide, Violet, Ash, Stone, and Prism tiles.",
          checked = settings.colorblindSymbols,
          onCheckedChange = onToggleColorblind,
          tag = "toggle_colorblind",
        )
      }
      item {
        SettingToggleRow(
          title = "Tactical Swap Hints",
          description = "Show live Match-3 swap hints, damage, Spectrum wheel, and Ink previews during Weave.",
          checked = settings.showTacticalHints,
          onCheckedChange = onToggleHints,
          tag = "toggle_hints",
        )
      }
      item {
        SettingToggleRow(
          title = "Fast Cascades",
          description = "Shorten tile highlight and cascade transitions.",
          checked = settings.fastAnimations,
          onCheckedChange = onToggleFastAnimations,
          tag = "toggle_fast_anim",
        )
      }
      item {
        SettingToggleRow(
          title = "Haptic Feedback",
          description = "Tactile vibration on Prism detonations and card casts.",
          checked = settings.hapticsEnabled,
          onCheckedChange = onToggleHaptics,
          tag = "toggle_haptics",
        )
      }
      item {
        Surface(
          color = CharredBasalt,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SmokedBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Canonical Rules Summary", style = MaterialTheme.typography.titleMedium, color = GildedBrass)
            Text("• Turn Order (ADR-001): Draw -> Weave (3 Match-3 Swaps) -> Cast -> Wall -> Enemy -> Cleanup.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Match-3 Cascades & Ember Surge: Swap adjacent tiles to line up 3+ in a row or column; 4+ matches forge a Prism and grant a bonus move (up to 2 per turn).", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Spectrum Wheel (ADR-005): Ember -> Gold -> Moss -> Tide -> Violet; starts at Run 0, out-of-order resolves at 1.0x then resets.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Prism (ADR-004): Swap Prism with any hue tile to detonate that hue's row/column cross, or swap two Prisms for a twin-cross blast.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Ash (▲, ADR-006): Clears in 3-in-a-row or adjacent to a hue match; yields 0 Ink, deals n - 1 chip after Enemy Block (+1 Spark with Hollow Crown).", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Stone (■, ADR-007): Immovable; shatters via orthogonal chain only on qualifying 4+ matches.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Wall Overflow (ADR-008): Rising into a full top row loses 8 HP, burns top 2 rows to Empty, costs 1 Flask, and skips Wall rise.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Brand / Fog / Seal (ADR-010..012): Branded blocks drain 2 Vigil on clear; Fog conceals colors & Ink for 3 moves (Gold Eye clears); Seal locks 1 column for 3 turns (Quarry Purge unseals).", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Echo & Dread (ADR-015 / ADR-017): Echo repeats primary effect once only; Unlit Covenant Dread ending requires 3 Ash-only, 0-card, 0-Flask turns vs The Hearthkeeper.", style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
            Text("• Zero-Flask Collapse: Reaching 0 Flasks immediately ends the expedition.", style = MaterialTheme.typography.bodyMedium, color = CrimsonRuin)
          }
        }
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String,
) {
  Surface(
    color = CharredBasalt,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, SmokedBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = WarmParchment)
        Spacer(Modifier.height(2.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MutedAsh)
      }
      Spacer(Modifier.width(10.dp))
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag(tag),
      )
    }
  }
}
