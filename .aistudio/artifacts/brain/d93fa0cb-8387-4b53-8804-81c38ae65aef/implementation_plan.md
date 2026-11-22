# Emberfall — Original Instrumental Soundtrack & Lyria 3 Studio

Add an original, calm, interlocking instrumental soundtrack identity powered by **Lyria 3** (`lyria-3-pro-preview` for ~2-minute seamless loops and `lyria-3-clip-preview` for 30-second sketches) via Firebase AI Logic, featuring automatic first-launch generation with persistent local caching, smooth Exploration-to-Combat crossfading with subtle SFX ducking, and a diegetic **Soundtrack Studio** built into Settings and the Title screen.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core soundtrack architecture choices from clarification are locked in below. Because Lyria 3 runs via **Firebase AI Logic** (with App Check attestation, requiring no client API key), approving this plan will first trigger the Firebase AI Logic terms consent dialog before wiring backend generation.

- **Confirmed Decision 1 — Dual Loops + Studio**: Generate two distinct ~2-minute instrumental loops (**Exploration** and **Combat**) using `lyria-3-pro-preview`, plus an interactive **Soundtrack Studio** supporting both full ~2-minute compositions (`lyria-3-pro-preview`) and rapid 30-second sketches (`lyria-3-clip-preview`).
- **Confirmed Decision 2 — Auto-Generate & Cache Locally**: On first play (once Music is enabled), automatically compose and cache the Exploration and Combat loops in persistent app storage. Subsequent launches load instantaneously offline with zero re-generation wait; while initial generation runs, the existing understated procedural bed bridges playback smoothly so there is never jarring silence.
- **Confirmed Decision 3 — Smooth Crossfade & Smart SFX Ducking**: Crossfade seamlessly (~1.4s equal-power transition) between Exploration and Combat modes, and gently duck the music bed (~-4 dB with ~280ms smooth release) during tactile puzzle cascades, Prisms, and skill hits so acoustic timbres stay clear on phone speakers.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces Emberfall's repetitive synthetic pulse loop with an authentic, warm, acoustic-meets-minimal-techno musical identity crafted specifically for extended puzzle concentration. Built from a memorable interlocking rhythmic cell—muted piano, marimba, plucked strings, soft hand percussion, warm sustained strings, and an understated electronic low-end pulse—with zero vocals, zero dramatic drops, and balanced phone-speaker clarity.
- **Target Audience / Persona**: Players spending 20–60+ minutes solving Match-3 tactical encounters and exploring Kashmir Shaivism progression who want a distinctive, meditative yet gently propulsive sonic atmosphere that never causes listening fatigue.
- **Key Value**: Transforms game feel from generic synthetic audio into a cohesive, studio-grade instrumental identity that adapts dynamically between contemplation (Title/Map/Campfire) and focused flow (Combat), while giving players a tactile studio to audition, switch, or re-compose variations anytime.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Zero-Friction First Launch**:
   - Player opens Emberfall. App Check initializes cleanly before UI composition.
   - If Music is enabled and cached Lyria tracks do not yet exist on disk, the Soundtrack Controller quietly starts generating the ~2-minute **Exploration** loop first, followed by the **Combat** loop, storing both in persistent app files.
   - A subtle, non-blocking brass pill on the Title screen shows live composition status (*"Composing Exploration Score..."* → *"Lyria Score Cached"*). Once ready, playback crossfades smoothly from the fallback bed into the generated acoustic-minimalist loop.
2. **Dynamic Gameplay Transitions**:
   - Entering a Combat chamber crossfades smoothly (~1.4s) from the spacious Exploration loop into the subtly tighter, gently pulsating Combat loop without abrupt cuts or volume spikes.
   - Triggering Match-3 cascades, Prism detonations, or *Prāṇa-Mudrā* activates a brief, gentle ducking envelope so puzzle audio cues stay crisp without interrupting musical flow.
   - Returning to Title, Map, Campfire, or Summary crossfades softly back to Exploration.
3. **Soundtrack Studio (Accessible from Title & Settings)**:
   - Player opens the **Soundtrack Studio** modal/section to inspect both cached tracks (**Exploration** and **Combat**), view active model (`lyria-3-pro-preview` ~2:00 loop vs `lyria-3-clip-preview` 30s sketch), audition either mode on demand, or compose a fresh variation using curated acoustic/minimalist focus presets (e.g., *Muted Piano & Marimba*, *Plucked Strings & Warm Pulse*, *Deep Stillness*) or a custom directional note.
   - Clear loading progress, elapsed indicator, and actionable **Retry** banner surface real errors transparently (network timeout, App Check attestation, quota) without ever faking audio output.

### Visual Identity & Theme
- **Aesthetic Direction**: Strictly unified with Emberfall's **Copperplate Engraving, Kiln Brass & Vermilion Wax** identity—resembling an antique acoustic tuning ledger rather than a neon synth app.
- **Color Palette & Mood**:
  - Canvas & Surfaces: Soot Carbon (`#0F0D0C`), Charred Basalt (`#1B1715`), Smoked Slate (`#25201D`).
  - Etched Accents: Kiln Brass (`#C8A252`) for active deck indicators, phasing pulse bars, and engraved borders; Vermilion Wax (`#C84228`) for primary *Compose Track* action stamps; Rag-Paper Ivory (`#EFE6D5`) and Muted Ash (`#9C9286`) for legible copy.
- **Typography & Hierarchy**:
  - Display headers in **Cormorant Garamond**; descriptive labels and preset copy in **IBM Plex Sans**; model tags, BPM, loop duration, and cache status in **IBM Plex Mono**.
- **Interactive Feedback & Motion**:
  - Subtle animated brass phase-ring / minimalist bar visualizer tied to active playback state (calm breathing motion, never distracting flashes).
  - Minimum `48dp` touch targets with ripple feedback, full `WindowInsets.safeDrawing` and `navigationBarsPadding()` compliance, and `testTag` coverage on all studio actions and audio toggles.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Dual-Model Strategy (`lyria-3-pro-preview` + `lyria-3-clip-preview`)**:
  - *Chosen Approach*: Default Exploration and Combat game loops use `lyria-3-pro-preview` to deliver full ~2-minute gradually developing instrumental arcs that avoid short-loop fatigue. Inside the Soundtrack Studio, players can toggle between **Full Loop (~2 min, `lyria-3-pro-preview`)** and **Quick Sketch (30s, `lyria-3-clip-preview`)** for fast musical experimentation.
  - *Why*: Meets the user's ~2-minute seamless loop brief for core gameplay while also exposing fast 30-second previews when iterating in the studio.
  - *Alternatives Considered*: Using 30-second clips for gameplay loops (rejected because 30s repeats too quickly during long tactical turns).
- **Decision 2 — Persistent On-Disk Audio Cache + Immediate Continuity**:
  - *Chosen Approach*: Save generated audio bytes (e.g. MP3/WAV returned by Lyria) into persistent internal app storage alongside lightweight metadata (mode, model ID, prompt preset, timestamp). On cold start, load directly from disk with zero network calls.
  - *Why*: Eliminates latency, saves AI quota, and ensures offline playability after first generation.
  - *Alternatives Considered*: Re-generating each session (rejected due to latency, bandwidth, and quota waste).
- **Decision 3 — Seamless Looping & Crossfade Playback Architecture**:
  - *Chosen Approach*: Upgrade the audio engine with dual managed `MediaPlayer` decks (Deck A / Deck B) for compressed stereo Lyria audio (`audio/mp3` / `audio/mpeg`), orchestrated on a dedicated coroutine crossfader for smooth Exploration ↔ Combat transitions, seamless looping, full Android `AudioManager` focus/ducking compliance, and automatic short-envelope ducking on SFX triggers. Retain the existing procedural `SoundPool` SFX bank and keep the procedural bed solely as a temporary bridge while first-run Lyria generation is in progress (or if the user has no network on first launch).
  - *Why*: Android's native `MediaPlayer` decodes MP3/AAC/WAV cleanly with minimal RAM footprint compared to holding 2 minutes of uncompressed stereo PCM in memory, and preserves existing SoundPool low-latency puzzle SFX and lifecycle safety.
  - *Alternatives Considered*: Adding heavy third-party media dependencies when native `MediaPlayer` dual-deck crossfading handles local cached stereo files cleanly with zero extra APK bloat.
- **Decision 4 — Strict Musical Prompt Engineering Guardrails**:
  - *Chosen Approach*: Anchor every Exploration and Combat prompt to an explicit shared musical cell (consistent tempo around ~106–112 BPM, D-Dorian / warm modal center, acoustic muted piano + marimba + plucked strings + soft hand percussion + warm sustained strings + restrained electronic pulse, phone-speaker-friendly midrange clarity, explicit negative constraints forbidding vocals, dramatic drops, sharp highs, or busy drums, and an explicit loop-closing instruction returning gently to the opening motif).
  - *Why*: Ensures Exploration and Combat feel like two sides of one recognizable musical identity rather than unrelated random tracks.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Flow

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                          MainActivity.onCreate()                          │
│  1. configureAppCheck(this, intent)  ──► Debug Secret (.commit()) / Prod  │
│  2. enableEdgeToEdge() & Compose UI Host                                  │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │
                                    ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         EmberfallViewModel (MVVM)                         │
│  • Observes SoundtrackState (Idle / Composing / Ready / Error)            │
│  • Auto-triggers first-run Exploration & Combat cache check               │
│  • Routes Studio actions (Compose Full 2m / Quick 30s / Audition / Reset) │
└───────────────────┬───────────────────────────────────────┬───────────────┘
                    │                                       │
                    ▼                                       ▼
┌───────────────────────────────────────┐   ┌───────────────────────────────┐
│      LyriaSoundtrackRepository        │   │  MinimalTechnoAudioEngine     │
│  • Lazy Firebase.ai generative models │   │  • Dual MediaPlayer Decks     │
│    - lyria-3-pro-preview (~2m loop)   │   │    (Deck A / Deck B crossfade)│
│    - lyria-3-clip-preview (30s clip)  │   │  • Exploration ↔ Combat fade  │
│  • ResponseModality.AUDIO extraction  │   │  • Subtle SFX ducking (~-4dB) │
│  • 120s / 60s coroutine timeouts      │   │  • AudioFocus + lifecycle     │
│  • Persistent local file + JSON cache │   │  • SoundPool puzzle SFX bank  │
└───────────────────┬───────────────────┘   └───────────────┬───────────────┘
                    │                                       │
                    └───────────────► Local App Storage ◄───┘
                                     (Cached Audio + Meta)
```

### Data Model & State
- **Soundtrack Slot (`SoundtrackSlot`)**: `EXPLORATION` and `COMBAT`, each tracking its own cached audio file path, active model ID (`lyria-3-pro-preview` or `lyria-3-clip-preview`), preset name, generation timestamp, and playback readiness.
- **Soundtrack Studio State (`SoundtrackUiState`)**: Immutable `StateFlow` exposing per-slot status (`Idle`, `Generating(slot, modelId, startedAtMs)`, `Ready(meta)`, `Error(message)`), currently active preset/custom direction, selected model tier (Full ~2-Min Loop vs 30s Clip), and active audible deck mode.
- **Musical Identity Presets (`SoundtrackPreset`)**: Curated minimalist acoustic-techno palettes built around the same interlocking core:
  1. *Interlocking Pulse (Default)* — Muted felt piano, wooden marimba phasing, soft hand percussion, warm sustained strings, subtle minimal techno pulse.
  2. *Plucked Stillness* — Pizzicato & acoustic plucked strings, gentle felt piano ostinato, understated sub-pulse, airy chamber warmth.
  3. *Kiln Meditation* — Warm low strings, soft hand drums, sparse marimba cells, hypnotic slow-shifting harmonies for deep concentration.

### Interactive Component & State Mapping
- **Title Screen Brass Score Pill**: Displays live composition progress or cached score badge; tapping opens the Soundtrack Studio directly.
- **Settings & Soundtrack Studio Panel**:
  - **Mode Audition Switch** (`Exploration` / `Combat`): Immediately crossfades live playback to preview the selected loop.
  - **Length / Model Selector** (`Full ~2m Loop • Lyria 3 Pro` vs `30s Sketch • Lyria 3 Clip`): Controls which Lyria 3 model backs the next composition.
  - **Preset Chips & Optional Mood Note**: Adjusts the instrumental balance while preserving all core acoustic/minimalist/no-vocal guardrails.
  - **Compose / Regenerate Action**: Triggers `LyriaSoundtrackRepository.generateTrack(...)` inside `viewModelScope` with cooperative cancellation (`CancellationException` rethrown), timeout handling, disk persistence, and immediate hot-swap crossfade into the newly generated track.
  - **Clear Cache / Restore Default**: Removes custom cached audio or triggers fresh regeneration cleanly.
  - **Transparent Error Banner**: Surfaces authentic Firebase AI Logic / App Check / timeout messages with a single-tap **Retry** button.

### Key Sequences & Integrations
- **Firebase Provisioning (Upon Plan Approval)**:
  1. Extract `debug.keystore` SHA-1 & SHA-256 fingerprints via `keytool`.
  2. Trigger Firebase setup UI consent (`requested_products: ["ai_logic"]`) and await user confirmation.
  3. Verify terms via `GetFirebaseTos`, provision via `ProvisionFirebase`, and verify certificate hash registration.
  4. Enable pre-configured Firebase AI Logic & App Check Gradle dependencies (`firebase-bom`, `firebase-ai`, `firebase-appcheck-debug`, `firebase-appcheck-recaptcha`), wire `configureAppCheck(this, intent)` in `MainActivity.onCreate()`, and remove any unused `GEMINI_API_KEY` placeholder from `.env.example`.
- **Lyria 3 Request & Audio Extraction Flow**:
  - Instantiate `Firebase.ai.generativeModel(modelName, generationConfig = generationConfig { responseModalities = listOf(ResponseModality.AUDIO) })` lazily via `by lazy`.
  - Wrap `generateContent(prompt)` in `withTimeout(120_000L)` (Pro) or `withTimeout(60_000L)` (Clip) on `Dispatchers.IO`.
  - Extract the returned audio `InlineDataPart` bytes (`audio/mp3` / `audio/mpeg` / `audio/wav`), write atomically to persistent app files, update metadata JSON, and hand the verified file URI to `MinimalTechnoAudioEngine` for gapless looped crossfade playback.
- **Verification**:
  - Pure-JVM & Robolectric unit tests covering prompt construction guardrails, cache metadata serialization/recovery, ViewModel state transitions (auto-generate, studio compose, error/retry, cancellation), and Compose UI controls in the Soundtrack Studio without invoking real network calls in unit tests.
