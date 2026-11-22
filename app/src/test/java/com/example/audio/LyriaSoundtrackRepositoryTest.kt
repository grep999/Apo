package com.example.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyriaSoundtrackRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `prompt builder enforces minimalist acoustic minimal techno guardrails for both Lyria 3 models`() {
    val expPro = SoundtrackPromptBuilder.buildPrompt(
      slot = SoundtrackSlot.EXPLORATION,
      tier = SoundtrackModelTier.PRO_FULL_LOOP,
      preset = SoundtrackPreset.INTERLOCKING_PULSE,
      customDirection = "warmer muted cello harmonics",
    )
    assertTrue(expPro.contains("Steve Reich", ignoreCase = true))
    assertTrue(expPro.contains("never copying", ignoreCase = true))
    assertTrue(expPro.contains("muted felt piano", ignoreCase = true))
    assertTrue(expPro.contains("marimba", ignoreCase = true))
    assertTrue(expPro.contains("NO vocals", ignoreCase = true))
    assertTrue(expPro.contains("NO dramatic drops", ignoreCase = true))
    assertTrue(expPro.contains("2-minute", ignoreCase = true))
    assertTrue(expPro.contains("104 BPM", ignoreCase = true))
    assertTrue(expPro.contains("warmer muted cello harmonics"))

    val cbtClip = SoundtrackPromptBuilder.buildPrompt(
      slot = SoundtrackSlot.COMBAT,
      tier = SoundtrackModelTier.CLIP_PREVIEW_30S,
      preset = SoundtrackPreset.KILN_MEDITATION,
    )
    assertTrue(cbtClip.contains("30-second", ignoreCase = true))
    assertTrue(cbtClip.contains("110 BPM", ignoreCase = true))
    assertTrue(cbtClip.contains("NO vocals", ignoreCase = true))
    assertEquals("lyria-3-pro-preview", SoundtrackModelTier.PRO_FULL_LOOP.modelId)
    assertEquals("lyria-3-clip-preview", SoundtrackModelTier.CLIP_PREVIEW_30S.modelId)
  }

  @Test
  fun `repository generates caches reloads replaces and clears tracks atomically`() = runBlocking {
    val dir = tempFolder.newFolder("lyria_cache_test")
    var calls = 0
    val fakeClient = object : LyriaAudioClient {
      override suspend fun generateAudio(
        modelId: String,
        prompt: String,
        timeoutMs: Long,
      ): GeneratedAudioPayload {
        calls++
        val bytes = ByteArray(256) { ((it + calls) % 127).toByte() }
        return GeneratedAudioPayload(
          bytes = bytes,
          mimeType = if (calls == 1) "audio/mpeg" else "audio/wav",
        )
      }
    }

    val repo = LyriaSoundtrackRepository(storageDir = dir, client = fakeClient)
    assertNull(repo.loadCached(SoundtrackSlot.EXPLORATION))

    val (meta1, file1) = repo.generateAndCache(
      slot = SoundtrackSlot.EXPLORATION,
      tier = SoundtrackModelTier.PRO_FULL_LOOP,
      preset = SoundtrackPreset.INTERLOCKING_PULSE,
    )
    assertEquals(SoundtrackModelTier.PRO_FULL_LOOP.modelId, meta1.modelId)
    assertTrue(file1.exists())
    assertTrue(file1.name.endsWith(".mp3"))
    assertEquals(256L, meta1.byteSize)

    // Reload from a brand new repository instance pointing at the same directory
    val reloaded = LyriaSoundtrackRepository(storageDir = dir, client = fakeClient)
      .loadCached(SoundtrackSlot.EXPLORATION)
    assertNotNull(reloaded)
    assertEquals(meta1.fileName, reloaded!!.first.fileName)
    assertEquals(file1.absolutePath, reloaded.second.absolutePath)

    // Regenerating with clip model replaces previous file cleanly
    val (meta2, file2) = repo.generateAndCache(
      slot = SoundtrackSlot.EXPLORATION,
      tier = SoundtrackModelTier.CLIP_PREVIEW_30S,
      preset = SoundtrackPreset.PLUCKED_STILLNESS,
    )
    assertEquals("lyria-3-clip-preview", meta2.modelId)
    assertTrue(file2.name.endsWith(".wav"))
    assertTrue(file2.exists())
    assertFalse("Old cached track file should be removed after replacement", file1.exists())

    // Clearing cache removes all tracks and metadata
    repo.clearCache()
    assertNull(repo.loadCached(SoundtrackSlot.EXPLORATION))
  }
}
