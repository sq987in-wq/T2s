# Piper TTS — Option C Hybrid (Kotlin + ONNX)

Offline Piper (VITS) TTS for Hindi. This is the **fixed engine** after the
100× deep audit — see [`docs/DeepAudit-100x.md`](docs/DeepAudit-100x.md).

## What was wrong & what's fixed
- **Fake native lib removed.** The bundled `libpiper_phonemize.so` was a
  desktop-Linux/glibc binary that could never load on Android (root cause of
  the historical "crash-prone NDK C++"). The JNI bridge was a hardcoded stub.
  Both removed; the app now uses a corrected pure-Kotlin engine.
- **Phonemizer overhauled.** Schwa deletion, virāma conjuncts, homorganic
  anusvāra, nukta, and **only model-vocabulary tokens** (verified against the
  real `phoneme_id_map`). Unknown phonemes are dropped like piper does —
  never silently turned into spaces (that was the robotic-pacing culprit).
- **Clause segmentation.** Text is split into prosody clauses (। . ! ? ; ॥),
  each synthesized with its own BOS/EOS, capped at 500 phonemes.
- **Pacing.** 350 ms breath pauses + equal-power crossfade at clause joints,
  RMS loudness normalization, streaming `MODE_STREAM` playback.
- **Build fixed.** The CI build was failing (`failed_log*.txt`) on a
  `crossfade` out-of-bounds crash and a `pow` compile error — both fixed.

## Build (Termux, one command)
```bash
cd ~/T2s
bash scripts/build-apk-termux.sh
```
The script installs Java 17 + Gradle + the Android SDK and produces a debug APK.
No NDK / CMake / C++ toolchain is required.

## Optional: true espeak-ng byte-parity (native path)
If you later want output byte-identical to espeak-ng, build piper-phonemize for
Android and enable the bridge — full instructions and corrected sources in
[`docs/native-reference/`](docs/native-reference/piper_phonemize_bridge.cpp).

## Structure
```
app/src/main/java/
  com/piperapp/core/engine/
    phonemize/  PhonemeIdMap, Normalizer, ClauseSegmenter, DevanagariG2P, NativePhonemizer
    ort/        OnnxTtsEngine, (session tuning)
    pipeline/   SynthesisPipeline (crossfade, pauses, loudness)
    audio/      AudioTrackSink, AacExporter
  com/pipertts/app/  Compose UI, service, domain, Room
```
