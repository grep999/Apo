package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.emberfallDataStore: DataStore<Preferences> by preferencesDataStore(name = "emberfall_settings")

data class PlayerSettings(
  val musicEnabled: Boolean = true,
  val sfxEnabled: Boolean = true,
  val masterAudioMuted: Boolean = false,
  val hapticsEnabled: Boolean = true,
  val fastAnimations: Boolean = false,
  val colorblindSymbols: Boolean = true,
  val showTacticalHints: Boolean = true,
  val oneTapBoardFire: Boolean = false,
) {
  val effectiveAudioOn: Boolean
    get() = !masterAudioMuted && (musicEnabled || sfxEnabled)
}

class SettingsDataStore(private val context: Context) {

  private object Keys {
    val MUSIC = booleanPreferencesKey("music_enabled")
    val SFX = booleanPreferencesKey("sfx_enabled")
    val MASTER_MUTE = booleanPreferencesKey("master_audio_muted")
    val HAPTICS = booleanPreferencesKey("haptics_enabled")
    val FAST_ANIM = booleanPreferencesKey("fast_animations")
    val COLORBLIND = booleanPreferencesKey("colorblind_symbols")
    val HINTS = booleanPreferencesKey("show_tactical_hints")
    // EFB-014: Preserve the canonical user preference key while reading any prior v2 value as fallback.
    val ONE_TAP = booleanPreferencesKey("one_tap_board_fire")
    val ONE_TAP_V2_LEGACY = booleanPreferencesKey("one_tap_board_fire_v2")
  }

  val settingsFlow: Flow<PlayerSettings> = context.emberfallDataStore.data.map { prefs ->
    PlayerSettings(
      musicEnabled = prefs[Keys.MUSIC] ?: true,
      sfxEnabled = prefs[Keys.SFX] ?: true,
      masterAudioMuted = prefs[Keys.MASTER_MUTE] ?: false,
      hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
      fastAnimations = prefs[Keys.FAST_ANIM] ?: false,
      colorblindSymbols = prefs[Keys.COLORBLIND] ?: true,
      showTacticalHints = prefs[Keys.HINTS] ?: true,
      oneTapBoardFire = prefs[Keys.ONE_TAP] ?: prefs[Keys.ONE_TAP_V2_LEGACY] ?: false,
    )
  }

  suspend fun setMusicEnabled(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.MUSIC] = enabled }
  }

  suspend fun setSfxEnabled(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.SFX] = enabled }
  }

  suspend fun setMasterAudioMuted(muted: Boolean) {
    context.emberfallDataStore.edit { it[Keys.MASTER_MUTE] = muted }
  }

  suspend fun setHapticsEnabled(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.HAPTICS] = enabled }
  }

  suspend fun setFastAnimations(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.FAST_ANIM] = enabled }
  }

  suspend fun setColorblindSymbols(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.COLORBLIND] = enabled }
  }

  suspend fun setShowTacticalHints(enabled: Boolean) {
    context.emberfallDataStore.edit { it[Keys.HINTS] = enabled }
  }

  suspend fun setOneTapBoardFire(enabled: Boolean) {
    context.emberfallDataStore.edit {
      it[Keys.ONE_TAP] = enabled
      it.remove(Keys.ONE_TAP_V2_LEGACY)
    }
  }
}
