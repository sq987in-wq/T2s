# Piper TTS → Production Android App
## Architecture Blueprint & Delivery Roadmap

**Prepared as:** Principal Android Architect / Embedded AI Engineering consultation
**Scope:** Convert a verified Termux offline Piper pipeline (hi_IN priyamvada / rohan / pratham, medium VITS, 22050 Hz) into a standalone, production-grade, 100% offline Android application.
**Verified against (Aug 2026):** ONNX Runtime Android 1.23.2 (Maven), `rhasspy/piper-phonemize` `master` API, `espeak-ng` upstream Android build docs, Google Play Asset Delivery current limits, sherpa-onnx Android ecosystem.

---

# 0. Executive Summary — The Decisions

| # | Question | Decision | Confidence |
|---|----------|----------|------------|
| D1 | ONNX inference runtime | **`onnxruntime-android` Java/Kotlin API** (`com.microsoft.onnxruntime:onnxruntime-android`), *not* a custom C++ engine | High |
| D2 | Phonemization | **Cross-compile `piper-phonemize` (embeds espeak-ng) via NDK → tiny JNI bridge**. No pure-Kotlin phonemizer exists worth using | High |
| D3 | Model delivery | **Slim APK + in-app model downloader** (GitHub Releases / any CDN, SHA-256 verified). Use **Play Asset Delivery (on-demand packs)** only if Play-Store-distributed | High |
| D4 | Memory strategy | **One cached `OrtSession` + sentence-chunked streaming synthesis** → peak RAM is O(one sentence), never O(script) | High |
| D5 | Execution provider | **CPU EP (NEON) with 4 intra-op threads**. Skip NNAPI (deprecated in Android 15, chokes on int64 + dynamic shapes). Benchmark XNNPACK; don't block release on it | High |
| D6 | Audio export | **WAV + AAC-LC `.m4a` via `MediaCodec`/`MediaMuxer`**. Android has **no MP3 encoder**, and `ffmpeg-kit` is retired — do not plan around MP3 | High |
| D7 | "Pitch" control | VITS has no pitch input. Map sliders to `length_scale` (speed), `noise_scale` (expressiveness), `noise_scale_w` (phonation stability). True pitch-shift = post-DSP (SoundTouch JNI) in v2 | High |
| D8 | Licensing | espeak-ng is **GPLv3** → your APK (and sherpa-onnx's piper path) carries GPL obligations. Decide open-source vs. legal review **before** writing code | Critical |

**Recommended path in one sentence:** Kotlin + Jetpack Compose Clean Architecture app; `:core:engine` module wraps ONNX Runtime Java API for inference and a ~100-line JNI bridge over `piper-phonemize` for phonemization; models fetched on first run into `filesDir`; synthesis runs clause-by-clause in a foreground service, streamed into `AudioTrack` (live) and/or `MediaCodec` → `.m4a` (export).

---

# 1. Engine & Runtime Integration Paths

## 1.1 Option matrix

| | **A. `onnxruntime-android` (Java API)** | **B. Full custom C++ (NDK)** | **C. Hybrid: ORT-Java + JNI phonemizer** ✅ | **D. sherpa-onnx AAR (bootstrap)** |
|---|---|---|---|---|
| What | Official Maven AAR: `ai.onnxruntime.*` Kotlin/Java bindings over the same C core | Port piper C++ (`piper1-gpl`) + link ORT C API; everything in `.so` | ORT Java for tensors/session; JNI **only** for espeak-ng phonemization | Prebuilt library that already runs Piper VITS on Android with a Java API |
| Time to first audio | 1–2 days | 2–4 weeks | 3–5 days | 1 day |
| Inference perf | ≈ B (JNI overhead is nanoseconds vs. 100s-of-ms per run) | Baseline | ≈ B | Same core ORT, similar |
| Debuggability | Excellent (Kotlin debugger, ANR/watchdog, profiling) | Poor (native crashes, tombstones, symbol stripping, ABI pain) | Excellent for 95% of the code; JNI surface is tiny and stable | Medium (opaque library) |
| Prosody pipeline control (your crossfades, pauses, per-clause scales) | Full control in Kotlin coroutines | Full control in C++ | **Full control, unit-testable in JVM** | Limited — sherpa splits/generates internally (`generateWithCallback` streams, but you don't own clause policy) |
| Build system cost | Zero NDK for inference | Full NDK + ORT version pinning + CI matrix | NDK only for espeak-ng (small, stable) | None |
| Risk | Slight extra copy of output tensor (~0.9 MB per 10 s audio — negligible) | Highest maintenance in the whole project | Low | Dependency on upstream project's pace; GPL via espeak (same as C) |

**Decision D1 + D2: Option C.** Your differentiating work (clause crossfade, 350 ms breath pauses, tuned scales, Hindi text handling) is *pipeline logic*, not inference logic. Pipeline logic belongs in Kotlin where it's testable; inference is already solved perfectly by the official ORT AAR. Reserve JNI for the one thing that cannot be done in Kotlin: espeak-ng.

Option D (sherpa-onnx, actively shipping in apps like VoxSherpa TTS in 2026) is a legitimate **one-day prototype** to validate device performance early — keep it as a spike, not the product, unless you accept its abstraction.

## 1.2 espeak-ng on Android — the three real strategies

### Strategy 1 (✅ recommended): `piper-phonemize` cross-compiled → `.so` + JNI
`piper-phonemize` is exactly the layer you verified in Termux: it embeds a maintained espeak-ng fork + `espeak-ng-loader`, and implements piper's exact phoneme→ID rules. Its verified public API (`src/phonemize.hpp`, `src/phoneme_ids.hpp`):

```cpp
// Returns phonemes per SENTENCE (espeak clause boundaries preserved)
void phonemize_eSpeak(std::string text, eSpeakPhonemeConfig &config,
                      std::vector<std::vector<Phoneme>> &phonemes);
// config.voice = "hi" (from your .onnx.json: espeak.voice)

// Produces exactly the [BOS, id, PAD, id, PAD, ..., EOS] pattern
// (pad='_'→0, bos='^'→1, eos='$'→2, interspersePad=true, addBos, addEos)
void phonemes_to_ids(const std::vector<Phoneme> &phonemes, PhonemeIdConfig &config,
                     std::vector<PhonemeId> &phonemeIds,
                     std::map<Phoneme, std::size_t> &missingPhonemes);
// config.phonemeIdMap = parsed from your model's .onnx.json phoneme_id_map
```

This gives **bit-identical ID sequences to your verified Python** (your `[1, id, 0, id, 2]` is precisely `interspersePad` behavior) and sentence segmentation with intonation metadata for free.

**Build facts (verified):**
- espeak-ng officially supports Android/Gradle/NDK builds (upstream `docs/building.md` has an `android/` project; API 34, NDK, Gradle 8.13+, JDK 17).
- Cross-compiling espeak-ng data requires a **host build first** (it runs built binaries to compile dictionaries) — `piper-phonemize` releases ship prebuilt `espeak-ng-data`; bundle that as an app asset and skip the pain.
- Deliverables: `libpiper-phonemize.so` (statically links espeak-ng, ~2–4 MB) + `espeak-ng-data/` (~15–20 MB, includes `hi` dictionary).

**JNI bridge — the entire native surface (keep it this small):**

```cpp
// piper_phonemizer_jni.cpp  — compiled with CMake against piper-phonemize
#include <jni.h>
#include <piper-phonemize/phonemize.hpp>
#include <piper-phonemize/phoneme_ids.hpp>
#include <piper-phonemize/json.hpp>
#include <cstdlib>
#include <string>

static bool g_ready = false;
static piper::eSpeakPhonemeConfig g_eSpeakCfg;
static piper::PhonemeIdConfig g_idCfg;          // holds phonemeIdMap from .onnx.json

extern "C" JNIEXPORT jboolean JNICALL
Java_com_piperapp_core_engine.phonemize.PhonemizerNative_init(
    JNIEnv *env, jobject, jbyteArray jDataPath, jbyteArray jVoice,
    jbyteArray jIdMapJson /* raw bytes of .onnx.json */) {

  // Point espeak-ng-loader at extracted assets BEFORE first use.
  // (piper-phonemize honors the ESPEAK_DATA_PATH env override; alternatively
  //  call its loader init — pin one release and mirror its init sequence.)
  std::string dataPath = toStdString(env, jDataPath);   // true UTF-8 helper
  setenv("ESPEAK_DATA_PATH", dataPath.c_str(), 1);

  // Parse voice + phoneme_id_map from the model JSON you already parse in Kotlin
  piper::json j = piper::json::parse(toStdString(env, jIdMapJson));
  g_idCfg.phonemeIdMap =
      std::make_shared<piper::PhonemeIdMap>(piper::json::parsePhonemeIdMap(j));
  g_eSpeakCfg.voice = toStdString(env, jVoice);          // "hi"
  g_ready = true;
  return JNI_TRUE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_piperapp_core.engine.phonemize.PhonemizerNative_phonemizeToIds(
    JNIEnv *env, jobject, jbyteArray jTextUtf8) {
  if (!g_ready) return nullptr;

  std::string text = toStdString(env, jTextUtf8);        // true UTF-8, see §6 pitfall #1
  std::vector<std::vector<piper::Phoneme>> sentences;
  piper::phonemize_eSpeak(text, g_eSpeakCfg, sentences);

  // Flatten: ids of sentence 1, -1 separator, ids of sentence 2, ...
  std::vector<int64_t> flat;
  for (auto &phons : sentences) {
    std::vector<int64_t> ids;
    std::map<piper::Phoneme, std::size_t> missing;       // log & surface these!
    piper::phonemes_to_ids(phons, g_idCfg, ids, missing);
    flat.insert(flat.end(), ids.begin(), ids.end());
    flat.push_back(-1);
  }
  return toJLongArray(env, flat);
}
```

```kotlin
// Kotlin side — the ONLY class that knows native code exists
internal object PhonemizerNative {
    init { System.loadLibrary("piper_phonemizer") }
    external fun init(dataPath: ByteArray, voice: ByteArray, idMapJson: ByteArray): Boolean
    external fun phonemizeToIds(textUtf8: ByteArray): LongArray?  // -1 = sentence boundary
}

class NativePhonemizer(private val voice: VoiceConfig) : Phonemizer {
    override fun phonemize(text: String): List<LongArray> =
        PhonemizerNative.phonemizeToIds(text.toByteArray(Charsets.UTF_8))
            ?.split(-1L).filter { it.isNotEmpty() }
            ?: throw PhonemizerException()
}
```

### Strategy 2: pure-Kotlin phonemizer — **rejected (for v1)**
There is no maintained pure-Kotlin espeak-ng port. A hand-rolled Hindi G2P would need to replicate: schwa deletion, nasalization (अनुस्वार/चंद्रबिंदु), inherent-vowel rules, Nukta handling, code-mixed English words (Hinglish!), numerals ("1984" → "उन्नीस सौ चौरासी"), and IPA stress marks — and any divergence from the *training-time* phonemization directly degrades output quality. Hindi's Devanagari is phonemic, so a rules-only G2P is a credible **v2 offline-mini mode**, but v1 must ship byte-exact parity with what the models were trained on. espeak-ng also gives you ~100 languages for free.

### Strategy 3: JNI to raw `libespeak-ng` (skip piper-phonemize)
Call `espeak_TextToPhonemesWithTerminator` directly and re-implement ID mapping + interspersing in Kotlin. Viable and slightly leaner, but you re-implement `phonemes_to_ids` semantics yourself → parity risk exactly where quality lives. Only do this if `piper-phonemize`'s CMake fights you.

> **GPL gate (D8):** espeak-ng is GPLv3; `piper-phonemize` is GPL *when distributed* (this is why upstream piper renamed to `piper1-gpl`). Shipping it in your APK makes your app a derivative work — plan to release your source under a GPL-compatible license (like the ecosystem apps do), or get proper legal advice on isolation strategies. Decide **now**; it shapes distribution.

---

# 2. Asset Delivery & Memory Architecture

## 2.1 Size budget

| Asset | Size | Notes |
|---|---|---|
| App code + ORT AAR (per-ABI after App Bundle split) | ~35–45 MB APK | arm64-v8a slice only |
| `libpiper-phonemize.so` | ~2–4 MB | inside APK, arm64-v8a (+ x86_64 for emulator) |
| `espeak-ng-data/` | ~15–20 MB | compresses well in APK assets |
| Each voice (`model.onnx` + `.onnx.json`) | ~61 MB | ×3 voices ≈ 185 MB |
| **Total for all 3 voices** | **≈ 230–250 MB** | exceeds any sane APK budget |

## 2.2 Delivery strategies

| Strategy | Verdict |
|---|---|
| **Base APK `assets/` for everything** | ❌ Blows the ~200 MB base-module limit; doubles storage (APK + extracted copies); assets are ZIP-compressed so ORT/espeak need extraction to real paths anyway (both want `mmap`-able file paths, not APK sub-entries). |
| **Play Asset Delivery** ✅ *(Play distribution)* | One install-time pack (espeak-ng-data + *default* voice ≈ 80 MB) + **on-demand packs** for rohan/pratham. Current limits: base module ~200 MB, install-time packs ~1 GB combined (Play has been raising this — re-verify in Play Console at publish), on-demand/fast-follow packs 512 MB each. `AssetPackManager.getPackLocation().assetsPath()` returns a **real filesystem path** → pass straight to ORT/espeak, zero extraction, delta-patched by Play for free. |
| **In-app downloader** ✅ *(sideload / GitHub / F-Droid)* | ~40 MB APK; first-run downloads a signed `models.json` manifest (version, URL, SHA-256, size, gender, lang) from GitHub Releases; download with OkHttp + resume, verify SHA-256, atomic rename. Works identically on Play too (just declare `INTERNET` and disclose — Play allows model downloads; they just can't deliver *executable code* outside Play). |

**Recommendation (D3):** Build the in-app downloader as the canonical path — it's store-agnostic and gives you versioned models (`models.json` is your migration tool). If/when you ship on Play, wrap the same voices as PAD on-demand packs and make the downloader prefer pack locations when present. Ship `espeak-ng-data` inside the APK (it's small, shared by all voices, and required before any first synth).

**Filesystem layout (single source of truth):**

```
<context.filesDir>/
  espeak-ng-data/                          # extracted once from APK assets on first run
  models/
    hi_IN-priyamvada-medium/
      model.onnx          (61 MB)
      model.onnx.json     (~100 KB)
      CACHE.opt           # ORT optimized-graph cache (see §2.3)
      meta.properties     # version=2, sha256=..., downloadedAt=...
  renders/                                 # scratch WAV/M4A before MediaStore publish
```

## 2.3 Session & buffer lifecycle (OOM defense)

**Rules:**
1. **One live `OrtSession` per loaded voice, cap the cache at 1 (2 only if `ActivityManager.memoryClass ≥ 256`).** A medium VITS session costs ~150–250 MB RSS (weights + arena + HiFiGAN workspace). Switching voices = `close()` old → `createSession()` new (sequential, never overlapping, or you'll 2×-spike on 3 GB devices).
2. **Lazy-load on first synth**, not on app start. Keep it alive across screens (it's in native heap; the Java GC doesn't see it — you must manage it explicitly).
3. **Persist ORT's optimized graph** via `SessionOptions.setOptimizedModelFilePath(CACHE.opt)` so sessions re-create in ~200–400 ms instead of 1–2 s. **Invalidate it whenever the ORT AAR version or model SHA changes** (stale optimized files = cryptic `OrtException`s).
4. **`onTrimMemory(TRIM_MEMORY_RUNNING_LOW/COMPLETE)` → close session + drop waveform buffers.** Re-create lazily; user pays one small reload, not an OOM kill.
5. **All synthesis memory is O(one clause), never O(script)** — this is the chunking architecture below.

**Chunked streaming (the core OOM-proof design):**

```
Script (50 KB of text)
  → sentence segmentation (from phonemizer, espeak clause boundaries)
  → queue of clauses (each ≤ ~300 chars / ≤ 500 phoneme IDs)
  → for each clause: OrtSession.run → float[N] PCM (~1–6 MB) → convert to
    int16 into a reusable direct ByteBuffer → write to AudioTrack AND/OR
    encoder → release tensor → next clause
```

Peak native memory ≈ session (fixed) + one clause of PCM. A 1-hour script costs the same peak RAM as one sentence. It also gives you: sentence-level progress, cancel-between-sentences, infinite-length scripts, and time to apply your **150-sample crossfade at clause joints and 350 ms (7,718 samples @ 22,050 Hz) breath pauses** exactly as validated in Termux (consider making crossfade 10–25 ms / 220–550 samples tunable — 150 samples ≈ 6.8 ms is on the short side; keep your verified value as default).

**Output copies:** consume ORT results via `OnnxTensor.getFloatBuffer()` (zero `float[]` materialization) and convert in-place into a reused `DirectByteBuffer` (16-bit LE). One allocation, amortized forever.

---

# 3. Performance & Hardware Acceleration

## 3.1 Execution-provider reality check

| EP | Verdict for VITS-on-Android |
|---|---|
| **CPU (NEON)** ✅ | The ORT Android AAR is NEON/FP16-built. This is the reliable baseline; VITS (transformer flow + HiFiGAN) maps well to CPU GEMM/conv. **Start and probably finish here.** |
| **XNNPACK** | Java API exists (`SessionOptions.addXnnpack(mapOf("intra_op_num_threads" to "4"))`). Caveat: XNNPACK partitions best on *static* shapes; your `input` length is dynamic, so large parts (or all) of the graph silently fall back to CPU. Benchmark it (`ORT logging` partition stats); a common trick is padding IDs to buckets (128/256/384/512) to stabilize shapes — only pursue if CPU misses your latency target. |
| **NNAPI** ❌ | Deprecated since Android 15. On top of that: VITS feeds **int64** phoneme IDs and dynamic lengths — most NNAPI drivers reject int64/dynamic shapes → CPU fallback *anyway*, sometimes with worse perf due to partitioning overhead. Zero-investment zone. |
| **QNN / Hexagon** | `onnxruntime-android-qnn` exists on Maven. NPU acceleration of a 61 MB dynamic-shape autoregressive-ish VITS is an engineering project of its own; not worth it for 3× faster-than-realtime already achievable on CPU. Revisit only if you chase battery/thermals hard. |
| **INT8 quantization** | Dynamic-range INT8 wrecks VITS quality (flow/generator are sensitive); skip. Model size isn't your problem — delivery is. |

**Session configuration (the whole tuning story):**

```kotlin
fun createSession(env: OrtEnvironment, modelFile: File, threads: Int): OrtSession =
    env.createSession(modelFile.absolutePath, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)          // 4 on big.LITTLE; 2 when thermals bite
        setInterOpNumThreads(1)                // VITS graph is effectively serial
        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        addConfigEntry("session.intra_op.allow_spinning", "0")  // kinder to thermals/battery in bg
        setMemoryPatternOptimization(false)    // dynamic shapes → per-shape pattern cache would grow
        setOptimizedModelFilePath(modelFile.resolveSibling("CACHE.opt").absolutePath)
    })
```

## 3.2 Threading model

- Synthesis runs on a **single dedicated dispatcher** (`Dispatchers.IO.limitedParallelism(1)` or your own `ExecutorService`). ORT sessions tolerate concurrent runs, but two VITS runs saturate memory bandwidth and big cores → both slow down. Serialize per clause.
- Never block the Compose main thread; engine publishes `StateFlow<EngineState>` (Idle / Phonemizing / Synthesizing(clause i/n) / Streaming / Paused / Error).
- Cancellation is cooperative: check `isActive` **between clauses** (never mid-run — `session.run()` isn't cancellable).
- Long scripts/export run in a **foreground service** (typed `mediaPlayback`, required behavior on Android 14+), so Doze/recent-app kills don't destroy a 10-minute render.

## 3.3 Thermals & battery

- Poll `PowerManager.getThermalHeadroom(30)` between clauses: ≥ `0.85` → insert 50–150 ms pacing delays or drop to 2 intra-op threads (thread count is session-level → requires session rebuild; pacing is the cheap lever).
- Expect sustained synthesis to draw 3–6 W on mid-range SoCs; a 30-minute batch render *will* throttle on a Redmi Note class device. Pacing beats throttling (predictable latency).

**Expected realtime factors (CPU EP, 4 threads, medium model — validate on-device):**

| Device class | RTF (synth time ÷ audio time) | Feel |
|---|---|---|
| Snapdragon 8-gen / Dimensity flagship | ~0.15–0.30 | Instant |
| SD 7xx / upper mid-range | ~0.35–0.60 | Fast, comfortably realtime |
| SD 6xx / Helio G entry | ~0.8–1.8 | Borderline; chunk-streaming + first-chunk-priming still feels responsive |

---

# 4. End-to-End System Design (Clean Architecture)

## 4.1 Component diagram

```
┌──────────────────────────── PRESENTATION (:app, Compose) ────────────────────────────┐
│  HomeScreen (script editor + Synth button + live waveform)                           │
│  VoiceScreen (catalog: downloaded / downloadable, progress)                           │
│  LibraryScreen (MediaStore renders, Media3 ExoPlayer)   SettingsScreen (DataStore)   │
│  ViewModels ← StateFlow → Engine/Repositories (no engine type leaks into UI)         │
└──────────────────────────────────┬───────────────────────────────────────────────────┘
                                   │ domain interfaces (use cases)
┌──────────────────────────────────▼───────────────────────────────────────────────────┐
│ DOMAIN (pure Kotlin, zero Android deps)                                              │
│  SynthesizeScriptUseCase · ExportAudioUseCase · ManageVoicesUseCase                  │
│  Models: SynthesisRequest · SynthesisParams(speed,expressiveness,stability)          │
│  Ports: TtsEngine · Phonemizer · AudioSink · AudioExporter · VoiceRepository         │
└───────┬──────────────────────────────────────────────┬───────────────────────────────┘
        │ implements                                  │ implements
┌───────▼──────────────────────────────┐   ┌──────────▼────────────────────────────────┐
│ ENGINE  (:core:engine)               │   │ DATA (:core:data)                          │
│ ┌─────────────────────────────────┐  │   │  Room: scripts, renders, voices           │
│ │ NativePhonemizer (JNI)          │  │   │  ModelDownloader (OkHttp + SHA-256)       │
│ │  libpiper_phonemizer.so         │  │   │  MediaStoreAudioStore                     │
│ │ OnnxTtsEngine (ORT Java)        │  │   │  DataStore settings                       │
│ │  └─ OrtSession, tensors         │  │   └───────────────────────────────────────────┘
│ │ SynthesisPipeline (clause loop, │  │
│ │  crossfade/pause, normalization)│  │   ┌───────────────────────────────────────────┐
│ │ AudioTrackSink (streaming)      │  │   │ AUDIO OUT                                  │
│ │ WavWriter / AacExporter         │  │   │  AudioTrack (22.05 kHz mono, MODE_STREAM)  │
│ │ Waveform Downsampler            │  │   │  MediaCodec AAC → MediaMuxer → MediaStore  │
│ └─────────────────────────────────┘  │   │  Media3 ExoPlayer (library playback)       │
└──────────────────────────────────────┘   └───────────────────────────────────────────┘
```

## 4.2 Gradle module & directory blueprint

```
settings.gradle.kts: :app, :core:engine, :core:data, :core:domain, :core:ui

:app/
  src/main/java/com/piperapp/
    ui/ home/ voices/ library/ settings/ player/ theme/ components/waveform/
    MainActivity.kt   SynthesisService.kt (foreground, mediaPlayback)
    di/AppModule.kt (Hilt)
:core:domain/                       ← pure Kotlin module (no Android plugin)
  synthesize/ SynthesizeScriptUseCase.kt  SynthesisParams.kt  ClauseJob.kt
  ports/ TtsEngine.kt  Phonemizer.kt  AudioSink.kt  AudioExporter.kt  VoiceRepository.kt
:core:engine/
  ort/ OnnxTtsEngine.kt  OrtSessionFactory.kt  VoiceConfig.kt (.onnx.json parser)
  phonemize/ NativePhonemizer.kt  PhonemizerNative.kt  EspeakDataInstaller.kt
  pipeline/ SynthesisPipeline.kt  Crossfader.kt  BreathPauses.kt  Normalizer.kt
  audio/ AudioTrackSink.kt  WavStreamWriter.kt  AacExporter.kt
  waveform/ EnvelopeExtractor.kt
  src/main/cpp/ CMakeLists.txt  piper_phonemizer_jni.cpp
  src/main/jniLibs/arm64-v8a/   (CI-built piper-phonemize + deps)
:core:data/
  db/ (Room: ScriptEntity, RenderEntity, VoiceEntity + DAOs)
  downloads/ ModelDownloader.kt  ModelsManifest.kt
  mediastore/ AudioContentStore.kt
  settings/ SettingsDataStore.kt
  repo/ DefaultVoiceRepository.kt DefaultScriptRepository.kt
```

## 4.3 The data flow (one tap → sound)

```
1  Raw text (Compose editor, NFC-normalized)
       ↓  SynthesizeScriptUseCase
2  NativePhonemizer (JNI → libpiper-phonemize → espeak-ng "hi")
       → List<LongArray> clause IDs  ([1, id, 0, id, …, 2] per sentence)   ← byte-parity with your Python
3  OnnxTtsEngine: for each clause
       input        : OnnxTensor int64 [1, N]   (LongBuffer.wrap(ids))
       input_lengths: OnnxTensor int64 [1]      ([N])
       scales       : OnnxTensor float32 [3]    ([noiseScale, lengthScale, noiseScaleW]
                                                  e.g. [0.45, 1.02, 0.8] from Termux tuning)
       → OrtSession.run → output tensor (float PCM, mono, 22,050 Hz — from config, never hardcoded)
4  SynthesisPipeline post-processing per clause:
       clip/normalize (peak −1 dBFS or RMS target — your loudnorm substitute, §5.2)
       apply 150-sample equal-power crossfade at clause joints; insert 350 ms silence at
       sentence breaks (paragraph break = 700 ms) — port your Termux values verbatim
5  Two consumers run off the same PCM stream:
   a) LIVE:  AudioTrackSink — AudioTrack MODE_STREAM 22,050 Hz mono PCM16; first clause
             primes the buffer, play() starts, subsequent clauses queue behind playback
             (underrun-proof: keep ≥ 2 clauses ahead or insert silence)
   b) EXPORT: WavStreamWriter (streaming, patch RIFF sizes on close) OR
              AacExporter — MediaCodec "audio/mp4a-latm" (AAC-LC, 64 kbps, 22.05 kHz)
              → MediaMuxer(FileDescriptor from MediaStore uri)
6  Waveform: EnvelopeExtractor reduces each PCM chunk to ~20 envelopes/sec → StateFlow →
             Compose Canvas draws scrolling waveform + progress cursor
7  Persistence: RenderEntity(scriptId, voiceId, params, durationMs, uri, createdAt) → Room;
             audio file published via MediaStore (Music/PiperTTS/, IS_PENDING until finalized)
8  Library playback: Media3 ExoPlayer on content:// uri
```

## 4.4 Engine interfaces (the ports that keep this clean)

```kotlin
interface TtsEngine : AutoCloseable {
    val isReady: StateFlow<Boolean>
    suspend fun load(voice: Voice)                       // lazy, suspend until warm
    suspend fun synthesize(ids: LongArray, params: SynthesisParams): PcmChunk  // one clause
}

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray> // one LongArray per clause
}

interface AudioSink : AutoCloseable {
    fun open(sampleRate: Int)
    fun write(pcm: ShortArray)                           // blocking-safe, backpressure-aware
    fun pause(); fun resume(); fun stop()
    val playbackHead: Flow<Int>                          // samples → progress bar
}

interface AudioExporter : AutoCloseable {
    fun begin(mime: String, sampleRate: Int, uri: Uri)
    fun writePcm(pcm: ShortArray)
    fun finish(durationMs: Long)
}
```

---

# 5. Audio Output, Export & Persistence Details

## 5.1 Live playback (`AudioTrack`)

```kotlin
val track = AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(CONTENT_TYPE_SPEECH).build())
    .setAudioFormat(AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(voiceConfig.sampleRate)     // 22050 — ALWAYS from .onnx.json
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setTransferMode(AudioTrack.MODE_STREAM)
    .setBufferSizeInBytes(trackMinBuffer * 4)      // deep buffer = underrun immunity
    .build()
// Prime with clause #1 BEFORE play(); write remaining clauses from the synth dispatcher.
```

BT note: Bluetooth A2DP resamples fine, but expect +100–200 ms latency on some stacks — stream, don't block.

## 5.2 Export — WAV / AAC, and the MP3 truth

- **WAV**: stream 44-byte-header + PCM chunks to `ContentResolver.openOutputStream(uri)`; keep a byte counter and **patch RIFF sizes after the final flush**. `RELATIVE_PATH = Music/PiperTTS`, `IS_PENDING = 1` until finalized.
- **AAC `.m4a`**: `MediaCodec.createEncoderByType("audio/mp4a-latm")` (AAC-LC, 22050 Hz mono, 64–96 kbps) → `MediaMuxer(parcelFileDescriptor.fileDescriptor, MUXER_OUTPUT_MPEG_4)` (FD constructor is API 26+ — one reason `minSdk 26`).
- **MP3 is not available**: Android's `MediaCodec` ships **MP3 decoders only — no encoder**. `ffmpeg-kit` was retired upstream (binaries pulled in early 2025). If MP3 is a hard product requirement, your options are bundling LAME via JNI (LGPL) or your own FFmpeg build (big, LGPL/GPL decisions) — recommend AAC `.m4a` (universal on Android/iOS/desktop) and move on.
- **loudnorm replacement**: implement single-pass loudness normalization — compute running RMS per clause, apply gain toward target (~ −16 LUFS equivalent), peak-limit at −1 dBFS with a simple soft-knee limiter. Your per-clause architecture makes this a ~60-line Kotlin class.

## 5.3 Room schema

```kotlin
@Entity ScriptEntity(id, title, body, lang="hi", lastVoiceId, createdAt, updatedAt)
@Entity VoiceEntity(voiceId PK, name, gender, lang, localPath, sizeBytes, version, status)
@Entity RenderEntity(id, scriptId FK, voiceId, speed, expressiveness, stability,
                     durationMs, uri, format, createdAt)   // + FTS4 on ScriptEntity.title/body if search matters
```

## 5.4 Controls mapping (be honest in the UI)

| Slider | Tensor | Range | Effect |
|---|---|---|---|
| Speed | `length_scale` | 0.5–1.5 | 1.02 = your Termux default (slightly slower) |
| Expressiveness | `noise_scale` | 0.1–0.99 | 0.45 = tuned value; lower = flatter/newsreader |
| Phonation stability | `noise_scale_w` | 0.3–1.0 | 0.8 default; higher = more variable phonation |
| **Pitch** | — | — | Not a VITS input. v2: SoundTouch (JNI) post-DSP pitch shift; v1: omit or preset-based |

---

# 6. Critical Pitfalls (ranked by how often they kill projects)

1. **JNI "modified UTF-8" corruption — the #1 native bug you'd hit.** `GetStringUTFChars` returns *modified* UTF-8; Devanagari (outside BMP handling, surrogate encoding) arrives garbled → espeak produces garbage phonemes → "the model sounds broken." **Fix:** always pass `String.toByteArray(Charsets.UTF_8)` as `byte[]` and use `GetByteArrayRegion` (as in §1.2). Never `GetStringUTFChars` for Indic text.
2. **Unicode normalization.** Clipboard/keyboard input can be NFD; espeak expects NFC. Apply `Normalizer.normalize(text, Form.NFC)` before phonemizing. Also strip zero-width joiners that some IMEs emit.
3. **Sample-rate mismatch.** All three of your voices are 22,050 Hz, but piper voices exist at 16,000 too. Hardcode nothing — read `audio.sample_rate` from each `.onnx.json` and configure `AudioTrack`/encoders per voice. A mismatch plays as chipmunk/slow-motion garbage.
4. **Blocking the main thread.** All engine calls are suspend/off-main by contract; a 61 MB session `createSession` on main = ANR. Also never call `session.run` from multiple coroutines (serialize — §3.2).
5. **Unbounded clause length.** VITS quality collapses (and latency balloons) past ~500 phoneme IDs; split long sentences at commas/दंड (।) when `ids.size > 500`.
6. **Session rebuild vs. thread change.** Intra-op thread count is baked into `SessionOptions` at creation. "Drop to 2 threads on thermal throttle" = new session (or just pace between clauses).
7. **ORT optimized-graph cache staleness.** `CACHE.opt` from ORT 1.22 + runtime 1.23 upgrade = cryptic failures. Key the cache on `(modelSha256, ortVersion)`; nuke on mismatch.
8. **`UnsatisfiedLinkError` / ABI holes.** Ship `arm64-v8a` + `x86_64` only via `abiFilters`; make sure `System.loadLibrary` failure on a weird ABI degrades gracefully (block synth, show message — don't crash-loop).
9. **Missing `espeak-ng-data` extraction.** First run must copy it from APK assets to `filesDir` (assets are compressed; espeak needs a real dir). Verify `hi` dict present; surface init failure explicitly.
10. **Process death mid-render.** Foreground service (typed `mediaPlayback`) + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission; export to `IS_PENDING` MediaStore rows so interrupted renders never appear in Music apps.
11. **GPL (espeak-ng).** §1.2 gate. If you ship closed-source without resolving this, you're shipping a violation.
12. **MediaCodec drain stalls.** The AAC encoder's output buffer loop must run on its own thread with `dequeueOutputBuffer(10ms)` timeouts; blocking it while feeding PCM = deadlock mid-export. Write it once, test with a 30-minute script.
13. **"Offline" claims with `INTERNET` permission.** You need `INTERNET` only for model downloads. Make first-run download optional (sideload-able model dir), state offline-ness honestly in Play listing.

---

# 7. Roadmap: Zero → Release APK

**Phase 0 — Parity harness (2–3 days, do not skip).**
In Termux, dump golden artifacts for ~20 Hindi texts (Devanagari, Hinglish, numerals, danda punctuation, long sentence): `text → clause phoneme-IDs → PCM duration + RMS envelope`. These become Android instrumented-test fixtures. *Acceptance: golden set frozen and committed.*

**Phase 1 — Engine skeleton (1 week).**
`:core:engine` with ORT Java: load priyamvada from `filesDir`, hand-feed IDs from a golden fixture, synthesize one clause, play via AudioTrack. *Acceptance: first audio from app; RTF measured on your test devices.*

**Phase 2 — Native phonemizer (1–1.5 weeks).**
CI (GitHub Actions, NDK) cross-compiles piper-phonemize → `jniLibs`; JNI bridge (§1.2); `espeak-ng-data` extractor. *Acceptance: instrumented test — native clause IDs == Termux golden IDs for all 20 fixtures.*

**Phase 3 — Streaming pipeline (1 week).**
Clause loop, crossfade/pauses/normalization, cancellation, foreground service, waveform envelope flow. *Acceptance: 1-hour script synthesizes with flat memory profile; cancel/resume works; no ANR.*

**Phase 4 — UI + persistence (1.5–2 weeks).**
Compose: Home (editor + live waveform + controls), Voices, Library, Settings; Room; MediaStore; Media3 player; AAC exporter. *Acceptance: full happy-path product loop on device.*

**Phase 5 — Delivery (1 week).**
In-app downloader + manifest + SHA-256; (optional) PAD packs; DataStore defaults; `onTrimMemory` hooks; thermal pacing. *Acceptance: clean-device install → first offline synth with zero manual steps.*

**Phase 6 — Hardening (1 week).**
R8/full-mode + baseline profile; instrumented matrix (low-RAM device, Android 11 & 15+); crash reporting decision; GPL decision executed; Play listing assets. *Acceptance: signed release APK/AAB.*

**~7–9 weeks solo, realistic.** The riskiest externals are Phase 2 (NDK build) — spike it in week 1, not week 5.

---

# 8. Appendix

**Dependencies (verify latest at build time):**
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")  // verified Aug 2026
implementation(platform("androidx.compose:compose-bom:<latest-stable>"))
implementation("androidx.media3:media3-exoplayer:<latest>")
implementation("androidx.room:room-runtime/<room-ktx>:<latest>") + ksp
implementation("com.squareup.okhttp3:okhttp:<latest>")
implementation("androidx.hilt:hilt-android") + ksp
```

**Gradle essentials:** `android { defaultConfig { ndk { abiFilters += listOf("arm64-v8a","x86_64") } } }`, `minSdk 26`, `externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt") } }` with `android.useAndroidX=true`, `packaging { jniLibs.useLegacyPackaging = false }` (lets the loader `mmap` uncompressed `.so` straight from the APK).

**CMake sketch:**
```cmake
cmake_minimum_required(VERSION 3.22)
project(piper_phonemizer_jni)
set(CMAKE_CXX_STANDARD 17)
include(FetchContent)  # or git submodule; pin an exact tag
FetchContent_Declare(piper-phonemize GIT_REPOSITORY https://github.com/rhasspy/piper-phonemize
                     GIT_TAG <pinned-tag> GIT_SUBMODULES_RECURSE true)
FetchContent_MakeAvailable(piper-phonemize)
add_library(piper_phonemizer_jni SHARED piper_phonemizer_jni.cpp)
target_link_libraries(piper_phonemizer_jni PRIVATE piper-phonemize::piper-phonemize log)
```
(espeak-ng cross-compile quirk: dictionary compilation runs host binaries — use the prebuilt `espeak-ng-data` from piper-phonemize releases as app assets rather than cross-compiling data.)

**Testing ladder:** JVM units (tokenizer config, WAV writer math, crossfade, normalizer, Room DAOs) → instrumented (native phonemizer parity, ORT golden PCM correlation on 1 fixed sentence with 1 intra-op thread, exporter file validity) → monkey/manual on a 2 GB-RAM device.

---

*End of blueprint. The one-sentence philosophy: own your differentiator (the Hindi prosody pipeline) in Kotlin, buy the commodity (ORT inference), isolate the unavoidable native code (espeak-ng) behind a 100-line wall, and make memory flat by streaming clauses — everything else is packaging.*
