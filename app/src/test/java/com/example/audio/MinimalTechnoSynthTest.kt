package com.example.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MinimalTechnoSynthTest {

  private fun rms(pcm: ShortArray): Double {
    if (pcm.isEmpty()) return 0.0
    var sumSq = 0.0
    for (s in pcm) {
      val v = s.toDouble() / 32768.0
      sumSq += v * v
    }
    return sqrt(sumSq / pcm.size)
  }

  @Test
  fun `exploration and combat 122 BPM 4-bar loops synthesize non-silent distinct PCM beds`() {
    val exploration = MinimalTechnoSynth.synthesizeExplorationLoop()
    val combat = MinimalTechnoSynth.synthesizeCombatLoop()

    // 4 bars at 122 BPM, 44100 Hz = ~347,016 samples
    assertTrue("Exploration loop should exceed 300k samples", exploration.size > 300_000)
    assertEquals("Exploration and Combat loops should match bar length", exploration.size, combat.size)

    val expRms = rms(exploration)
    val cbtRms = rms(combat)
    assertTrue("Exploration loop must be audible (RMS=$expRms)", expRms > 0.02)
    assertTrue("Combat loop must be audible (RMS=$cbtRms)", cbtRms > 0.04)
    assertTrue("Combat pulse should have higher RMS energy than Exploration ($cbtRms vs $expRms)", cbtRms > expRms)
  }

  @Test
  fun `all minimal techno SFX render valid RIFF WAVE PCM16 payloads`() {
    assertTrue("Expected at least 18 minimal SFX cues", MinimalSfx.entries.size >= 18)
    for (sfx in MinimalSfx.entries) {
      val wav = MinimalTechnoSynth.synthesizeSfxWav(sfx)
      assertTrue("${sfx.name} WAV too small (${wav.size})", wav.size > 200)
      assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
      assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
      assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
      assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

      // Ensure payload contains non-zero audio energy
      var maxAbs = 0
      var i = 44
      while (i + 1 < wav.size) {
        val lo = wav[i].toInt() and 0xFF
        val hi = wav[i + 1].toInt() shl 8
        val sample = (hi or lo).toShort().toInt()
        val abs = kotlin.math.abs(sample)
        if (abs > maxAbs) maxAbs = abs
        i += 2
      }
      assertTrue("${sfx.name} PCM payload must not be silent (peak=$maxAbs)", maxAbs > 500)
    }
  }

  @Test
  fun `spectrum pitch scaling climbs monotonically across D-Phrygian steps`() {
    val rates = (2..8).map { MinimalTechnoSynth.spectrumPitchRate(it) }
    assertEquals(1.0f, rates.first(), 0.001f)
    assertEquals(2.0f, rates.last(), 0.001f)
    for (idx in 0 until rates.lastIndex) {
      assertTrue(
        "Pitch rate must increase monotonically: ${rates[idx]} < ${rates[idx + 1]}",
        rates[idx] < rates[idx + 1],
      )
    }
  }

  @Test
  fun `cascade pitch scaling climbs monotonically across multi-wave chains`() {
    val cascadeRates = (1..5).map { wave -> MinimalTechnoSynth.cascadePitchRate(baseHalfSteps = 2, waveIndex = wave) }
    assertEquals(1.0f, cascadeRates.first(), 0.001f)
    assertTrue("Final cascade wave should stay clamped <= 2.0f", cascadeRates.last() <= 2.0f)
    for (idx in 0 until cascadeRates.lastIndex) {
      assertTrue(
        "Cascade wave ${idx + 2} should pitch higher than wave ${idx + 1}",
        cascadeRates[idx] < cascadeRates[idx + 1],
      )
    }
  }
}
