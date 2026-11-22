package com.example.audio

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "LyriaSoundtrack"

enum class SoundtrackSlot(
  val id: String,
  val displayName: String,
  val musicMode: MusicMode,
  val subtitle: String,
) {
  EXPLORATION(
    id = "exploration",
    displayName = "Exploration",
    musicMode = MusicMode.EXPLORATION,
    subtitle = "Citadel, Map & Contemplation",
  ),
  COMBAT(
    id = "combat",
    displayName = "Combat",
    musicMode = MusicMode.COMBAT,
    subtitle = "Focused Puzzle Flow & Pulse",
  ),
}

enum class SoundtrackModelTier(
  val modelId: String,
  val displayName: String,
  val durationLabel: String,
  val timeoutMs: Long,
) {
  PRO_FULL_LOOP(
    modelId = "lyria-3-pro-preview",
    displayName = "Full Loop (~2m)",
    durationLabel = "~2:00 Seamless Loop",
    timeoutMs = 120_000L,
  ),
  CLIP_PREVIEW_30S(
    modelId = "lyria-3-clip-preview",
    displayName = "Quick Sketch (30s)",
    durationLabel = "0:30 Musical Cell",
    timeoutMs = 60_000L,
  ),
}

enum class SoundtrackPreset(
  val id: String,
  val displayName: String,
  val shortDescription: String,
  val timbreGuidance: String,
) {
  INTERLOCKING_PULSE(
    id = "interlocking_pulse",
    displayName = "Interlocking Pulse",
    shortDescription = "Muted piano, marimba phasing, plucked strings & subtle pulse",
    timbreGuidance = "Center the musical cell on a warm muted felt piano and wooden marimba in subtle interlocking rhythmic phasing, supported by acoustic plucked strings, soft hand percussion, warm sustained chamber strings, and a restrained, velvety minimal techno pulse.",
  ),
  PLUCKED_STILLNESS(
    id = "plucked_stillness",
    displayName = "Plucked Stillness",
    shortDescription = "Acoustic plucked strings, felt piano ostinato & airy warmth",
    timbreGuidance = "Lead with delicate acoustic plucked strings (harp-like and pizzicato textures) weaving over a calm muted piano ostinato, cushioned by airy sustained low strings, whisper-soft hand percussion, and an understated warm sub-pulse.",
  ),
  KILN_MEDITATION(
    id = "kiln_meditation",
    displayName = "Kiln Meditation",
    shortDescription = "Warm sustained strings, soft hand drums & sparse marimba cells",
    timbreGuidance = "Emphasize warm sustained cello and viola harmonies beneath sparse wooden marimba figures, gentle muted hand drums, subtle plucked harmonics, and a deeply steady, restrained low-end minimal techno pulse.",
  ),
}

data class CachedTrackMeta(
  val slot: SoundtrackSlot,
  val modelId: String,
  val presetId: String,
  val customDirection: String,
  val fileName: String,
  val mimeType: String,
  val byteSize: Long,
  val generatedAtMs: Long,
) {
  val modelTier: SoundtrackModelTier
    get() = SoundtrackModelTier.entries.firstOrNull { it.modelId == modelId }
      ?: SoundtrackModelTier.PRO_FULL_LOOP

  val preset: SoundtrackPreset
    get() = SoundtrackPreset.entries.firstOrNull { it.id == presetId }
      ?: SoundtrackPreset.INTERLOCKING_PULSE

  fun toJson(): JSONObject = JSONObject().apply {
    put("slot", slot.id)
    put("modelId", modelId)
    put("presetId", presetId)
    put("customDirection", customDirection)
    put("fileName", fileName)
    put("mimeType", mimeType)
    put("byteSize", byteSize)
    put("generatedAtMs", generatedAtMs)
  }

  companion object {
    fun fromJsonOrNull(raw: String): CachedTrackMeta? = runCatching {
      val obj = JSONObject(raw)
      val slot = SoundtrackSlot.entries.firstOrNull { it.id == obj.optString("slot") } ?: return null
      val fileName = obj.optString("fileName").trim()
      if (fileName.isEmpty()) return null
      CachedTrackMeta(
        slot = slot,
        modelId = obj.optString("modelId", SoundtrackModelTier.PRO_FULL_LOOP.modelId),
        presetId = obj.optString("presetId", SoundtrackPreset.INTERLOCKING_PULSE.id),
        customDirection = obj.optString("customDirection", ""),
        fileName = fileName,
        mimeType = obj.optString("mimeType", "audio/mpeg"),
        byteSize = obj.optLong("byteSize", 0L),
        generatedAtMs = obj.optLong("generatedAtMs", 0L),
      )
    }.getOrNull()
  }
}

sealed interface SoundtrackSlotStatus {
  data object Idle : SoundtrackSlotStatus
  data class Generating(val modelId: String, val startedAtMs: Long) : SoundtrackSlotStatus
  data class Ready(val meta: CachedTrackMeta, val filePath: String) : SoundtrackSlotStatus
  data class Error(val message: String) : SoundtrackSlotStatus
}

data class SoundtrackUiState(
  val exploration: SoundtrackSlotStatus = SoundtrackSlotStatus.Idle,
  val combat: SoundtrackSlotStatus = SoundtrackSlotStatus.Idle,
  val selectedTier: SoundtrackModelTier = SoundtrackModelTier.PRO_FULL_LOOP,
  val selectedPreset: SoundtrackPreset = SoundtrackPreset.INTERLOCKING_PULSE,
  val customDirection: String = "",
  val activeMusicMode: MusicMode = MusicMode.EXPLORATION,
  val autoGenerationAttempted: Boolean = false,
) {
  fun statusFor(slot: SoundtrackSlot): SoundtrackSlotStatus = when (slot) {
    SoundtrackSlot.EXPLORATION -> exploration
    SoundtrackSlot.COMBAT -> combat
  }

  val isGeneratingAny: Boolean
    get() = exploration is SoundtrackSlotStatus.Generating || combat is SoundtrackSlotStatus.Generating

  val cachedCount: Int
    get() = listOf(exploration, combat).count { it is SoundtrackSlotStatus.Ready }

  val firstErrorMessage: String?
    get() = (exploration as? SoundtrackSlotStatus.Error)?.message
      ?: (combat as? SoundtrackSlotStatus.Error)?.message
}

data class GeneratedAudioPayload(
  val bytes: ByteArray,
  val mimeType: String,
  val modelNote: String? = null,
)

interface LyriaAudioClient {
  suspend fun generateAudio(
    modelId: String,
    prompt: String,
    timeoutMs: Long,
  ): GeneratedAudioPayload
}

/**
 * Pure deterministic prompt builder enforcing Emberfall's minimalist acoustic + minimal techno
 * musical identity and strict phone-speaker / loop-seam guardrails.
 */
object SoundtrackPromptBuilder {
  fun buildPrompt(
    slot: SoundtrackSlot,
    tier: SoundtrackModelTier,
    preset: SoundtrackPreset,
    customDirection: String = "",
  ): String {
    val sanitizedCustom = customDirection
      .replace("\n", " ")
      .replace("\r", " ")
      .trim()
      .take(160)

    val modeBlock = when (slot) {
      SoundtrackSlot.EXPLORATION ->
        "Role: Exploration & contemplation loop for an Android puzzle board game. Tempo around 104 BPM in warm D Dorian modal harmony. Spacious, calm, quietly intriguing, with breathing room between phrases and a very gentle electronic pulse."
      SoundtrackSlot.COMBAT ->
        "Role: Focused puzzle-combat loop sharing the same core musical cell and D Dorian harmonic identity as the exploration theme. Tempo around 110 BPM. Gently more propulsive interlocking rhythmic phasing and a subtle, steady minimal techno low-end pulse that gives quiet forward momentum without breaking concentration."
    }

    val lengthBlock = when (tier) {
      SoundtrackModelTier.PRO_FULL_LOOP ->
        "Duration & Structure: Polished ~2-minute seamless instrumental loop. Begin with a small, memorable rhythmic-melodic cell, develop gradually through subtle phase shifts, delicate counter-patterns, and slowly shifting modal harmonies, then taper back cleanly into the opening cell so the ending connects seamlessly to the beginning."
      SoundtrackModelTier.CLIP_PREVIEW_30S ->
        "Duration & Structure: Tight 30-second seamless instrumental loop introducing the small, memorable interlocking musical cell with gentle phasing and a clean loop return."
    }

    val customClause = if (sanitizedCustom.isNotEmpty()) {
      " Additional subtle texture nuance requested by the listener (strictly keeping it instrumental, calm, and unobtrusive): $sanitizedCustom."
    } else {
      ""
    }

    return buildString {
      append("Original instrumental musical identity soundtrack for a contemplative Android puzzle board game. ")
      append("Blend minimalist contemporary classical techniques—repeating small patterns, subtle interlocking rhythmic phasing, gradual unobtrusive development, and carefully shifting harmonies (using Steve Reich only as a broad reference for rhythmic process and interlocking repetition, never copying any specific composition or melody)—with restrained minimal techno and the natural warmth of real, acoustically played instruments. ")
      append(preset.timbreGuidance)
      append(" ")
      append(modeBlock)
      append(" ")
      append(lengthBlock)
      append(" Mix & Dynamics: Spacious arrangement, even dynamics, warm midrange clarity optimized for phone speakers, understated low-end. ")
      append("Strict Exclusions: Strictly instrumental—NO vocals, NO singing, NO spoken words, NO dramatic drops, NO busy drums or heavy percussion, NO sharp or piercing high-frequency sounds, and NO sudden transitions that could distract during play.")
      append(customClause)
    }
  }
}

/**
 * Production Firebase AI Logic client invoking Lyria 3 (`lyria-3-pro-preview` / `lyria-3-clip-preview`).
 * Models are initialized lazily via `by lazy` after `configureAppCheck(this, intent)` has run.
 */
class FirebaseLyriaAudioClient : LyriaAudioClient {

  private val audioGenConfig by lazy {
    generationConfig {
      responseModalities = listOf(ResponseModality.AUDIO, ResponseModality.TEXT)
    }
  }

  private val proModel by lazy {
    Firebase.ai.generativeModel(
      modelName = SoundtrackModelTier.PRO_FULL_LOOP.modelId,
      generationConfig = audioGenConfig,
    )
  }

  private val clipModel by lazy {
    Firebase.ai.generativeModel(
      modelName = SoundtrackModelTier.CLIP_PREVIEW_30S.modelId,
      generationConfig = audioGenConfig,
    )
  }

  override suspend fun generateAudio(
    modelId: String,
    prompt: String,
    timeoutMs: Long,
  ): GeneratedAudioPayload = withContext(Dispatchers.IO) {
    val model = when (modelId) {
      SoundtrackModelTier.CLIP_PREVIEW_30S.modelId -> clipModel
      else -> proModel
    }
    try {
      val response = withTimeout(timeoutMs) {
        model.generateContent(prompt)
      }
      val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
      val audioPart = parts
        .filterIsInstance<InlineDataPart>()
        .firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) && it.inlineData.isNotEmpty() }
        ?: parts.filterIsInstance<InlineDataPart>().firstOrNull { it.inlineData.isNotEmpty() }

      if (audioPart == null || audioPart.inlineData.isEmpty()) {
        throw IllegalStateException("Lyria model ($modelId) returned no audio payload.")
      }
      GeneratedAudioPayload(
        bytes = audioPart.inlineData,
        mimeType = audioPart.mimeType.ifBlank { "audio/mpeg" },
        modelNote = response.text,
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Lyria generation timed out after ${timeoutMs}ms ($modelId)", e)
      throw IllegalStateException(
        "Soundtrack generation timed out after ${timeoutMs / 1000}s. Tap Retry to compose again.",
        e,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Lyria generation failed ($modelId)", e)
      throw e
    }
  }
}

/**
 * Manages Lyria 3 soundtrack generation and persistent on-disk stereo audio caching.
 */
class LyriaSoundtrackRepository(
  private val storageDir: File,
  private val client: LyriaAudioClient = FirebaseLyriaAudioClient(),
) {
  constructor(
    context: Context,
    client: LyriaAudioClient = FirebaseLyriaAudioClient(),
  ) : this(
    storageDir = File(context.applicationContext.filesDir, "emberfall_lyria_soundtrack"),
    client = client,
  )

  private fun metaFileFor(slot: SoundtrackSlot): File = File(storageDir, "meta_${slot.id}.json")

  fun loadCached(slot: SoundtrackSlot): Pair<CachedTrackMeta, File>? {
    val metaFile = metaFileFor(slot)
    if (!metaFile.exists()) return null
    val meta = CachedTrackMeta.fromJsonOrNull(runCatching { metaFile.readText() }.getOrNull() ?: return null)
      ?: return null
    val audioFile = File(storageDir, meta.fileName)
    if (!audioFile.exists() || audioFile.length() < 64L) return null
    return meta to audioFile
  }

  suspend fun generateAndCache(
    slot: SoundtrackSlot,
    tier: SoundtrackModelTier,
    preset: SoundtrackPreset,
    customDirection: String = "",
  ): Pair<CachedTrackMeta, File> = withContext(Dispatchers.IO) {
    storageDir.mkdirs()
    val prompt = SoundtrackPromptBuilder.buildPrompt(
      slot = slot,
      tier = tier,
      preset = preset,
      customDirection = customDirection,
    )
    val payload = client.generateAudio(
      modelId = tier.modelId,
      prompt = prompt,
      timeoutMs = tier.timeoutMs,
    )
    if (payload.bytes.size < 64) {
      throw IllegalStateException("Received empty or truncated audio track (${payload.bytes.size} bytes).")
    }

    val ext = extensionForMime(payload.mimeType)
    val fileName = "track_${slot.id}_${System.currentTimeMillis()}.$ext"
    val targetFile = File(storageDir, fileName)
    val tempFile = File(storageDir, "$fileName.tmp")
    tempFile.writeBytes(payload.bytes)
    if (!tempFile.renameTo(targetFile)) {
      targetFile.writeBytes(payload.bytes)
      runCatching { tempFile.delete() }
    }

    // Remove previous audio file for this slot to keep persistent storage tidy.
    val previousMeta = runCatching {
      metaFileFor(slot).takeIf { it.exists() }?.readText()?.let { CachedTrackMeta.fromJsonOrNull(it) }
    }.getOrNull()
    if (previousMeta != null && previousMeta.fileName != fileName) {
      runCatching { File(storageDir, previousMeta.fileName).delete() }
    }

    val meta = CachedTrackMeta(
      slot = slot,
      modelId = tier.modelId,
      presetId = preset.id,
      customDirection = customDirection.trim().take(160),
      fileName = fileName,
      mimeType = payload.mimeType,
      byteSize = targetFile.length(),
      generatedAtMs = System.currentTimeMillis(),
    )
    val metaTemp = File(storageDir, "meta_${slot.id}.json.tmp")
    metaTemp.writeText(meta.toJson().toString())
    val metaDest = metaFileFor(slot)
    if (!metaTemp.renameTo(metaDest)) {
      metaDest.writeText(meta.toJson().toString())
      runCatching { metaTemp.delete() }
    }
    meta to targetFile
  }

  suspend fun clearCache() = withContext(Dispatchers.IO) {
    if (!storageDir.exists()) return@withContext
    storageDir.listFiles()?.forEach { file ->
      runCatching { file.delete() }
    }
  }

  companion object {
    fun extensionForMime(mimeType: String): String = when {
      mimeType.contains("wav", ignoreCase = true) -> "wav"
      mimeType.contains("ogg", ignoreCase = true) -> "ogg"
      mimeType.contains("mp4", ignoreCase = true) ||
        mimeType.contains("m4a", ignoreCase = true) ||
        mimeType.contains("aac", ignoreCase = true) -> "m4a"
      else -> "mp3"
    }
  }
}
