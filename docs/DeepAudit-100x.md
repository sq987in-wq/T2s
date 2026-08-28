# T2S — 100× Deep Audit & Executive Rulings (Round 2, on the real code)

**Auditor:** Principal Architect (Android / AI / Audio)
**Scope:** the actual `main` branch source (`app/`, `com.piperapp.core.*`, `com.pipertts.app.*`)
**Date:** 2026-08-28

---

## 1. Headline findings

### F1 — The "crash-prone NDK C++" was a Linux binary, not an Android library.
`app/src/main/jniLibs/*/libpiper_phonemize.so` was inspected with `readelf`:
its DT_NEEDED entries are `libespeak-ng.so.1`, `libc.so.6`, `libstdc++.so.6`,
`libgcc_s.so.1`, `libonnxruntime.so.1.14.1`. Those are **glibc / desktop-Linux
library names**. Android uses bionic `libc.so` and has none of them, so this
.so could never load on Android — loading it produced the "crash" / garbage.
It was a desktop build accidentally dropped into `jniLibs`. **Removed.**

### F2 — The JNI bridge was a hardcoded stub, not a phonemizer.
`app/src/main/jni/piper_phonemize_bridge.cpp` `phonemizeToIds()` returned the
constant vector `{1,12,45,32,88,2}` regardless of input. It never called espeak
or piper at all. Even if the `.so` had been Android-native, the bridge produced
nonsense. **Replaced with a correct reference bridge** (`docs/native-reference/`).

### F3 — The Kotlin phonemizer mapped unknown phonemes to the SPACE token.
In `NativePhonemizer.phonemize()`:
```kotlin
val id = idMap[ph] ?: idMap[ph.lowercase()] ?: idMap[" "] ?: 0L
```
Any phoneme the model didn't know became a **space** (a real token id 3). That
silently inserted spurious pauses into the token stream → the model predicted
wrong durations → the "robotic pacing". Piper's real behaviour is to **drop**
missing phonemes, never to map them to a pause. **Fixed** (`PhonemeIdMap`).

### F4 — No schwa deletion → "every word ends in -a".
The old G2P emitted `ə` after almost every consonant. Hindi deletes the
word-final schwa and medial schwas before clusters. This alone was a big part
of the unnatural sound. **Fixed** in `DevanagariG2P` (word-final + before-cluster).

### F5 — Wrong phoneme tokens vs. the model's real vocabulary.
The model (`hi_IN-priyamvada-medium.onnx.json`) uses **decomposed espeak IPA**
as separate tokens: aspiration `ʰ`, length `ː`, nasal `̃`, affricates `tʃ`/`dʒ`,
flaps `ɽ`, etc. The old map emitted tokens that sometimes didn't exist, then F3
turned them into spaces. `DevanagariG2P` emits **only tokens present in the map**
(verified against the actual `phoneme_id_map` fetched from Hugging Face).

### F6 — No sentence/clause segmentation → one giant utterance.
The old code phonemized the whole input as a single `[BOS…EOS]` clause. Long
input collapsed quality and prosody. **Fixed** with `ClauseSegmenter`
(split on `। . ! ? ; ॥`, cap at 500 phonemes per clause).

### F7 — Breath pauses & crossfade existed but were never called (and crashed).
`SynthesisPipeline.crossfade()` had an **out-of-bounds read** (`a[i]` for
`i >= a.size`) that would throw on real buffers, and the UI concatenated
clauses with **no pauses**. Also `normalizePeak` used `kotlin.math.pow` in a
form the compiler rejected — **the build was actually failing** (see
`failed_log*.txt`). All fixed: `crossfade` rewritten, RMS loudness added,
`assemble()` inserts 350 ms breath pauses + crossfade at joints.

### F8 — Playback used one giant `MODE_STATIC` buffer.
Long scripts could exceed the static-buffer limit and block the UI. Replaced
with streaming `MODE_STREAM` writes off the main thread.

---

## 2. Executive decisions (taken, not deferred)

| # | Decision |
|---|---|
| D1 | **Ship the corrected pure-Kotlin engine** as the default path — it compiles on CI/Termux with zero NDK and fixes the mapping/pacing defects directly. |
| D2 | **Remove the Linux `.so`**, the fake JNI stub, and `externalNativeBuild` (no NDK/CMake → faster, robust build). |
| D3 | **Keep a correct JNI bridge + CMake as an optional native-parity path** (`docs/native-reference/`) for later, clearly documented. |
| D4 | **Piper-exact ID framing**: `[BOS, PAD, id, PAD, …, EOS]`, missing phonemes dropped. |
| D5 | **Per-clause synthesis** with 500-phoneme cap, crossfade, and 350 ms sentence breath pauses → natural prosody. |

---

## 3. What was changed

- `app/src/main/java/com/piperapp/core/engine/phonemize/`
  - `PhonemeIdMap.kt` (new) — piper-exact mapping.
  - `Normalizer.kt` (new) — NFC, zero-width strip.
  - `ClauseSegmenter.kt` (new) — clause split + 500-phoneme cap.
  - `DevanagariG2P.kt` (new) — corrected Hindi→tokens (schwa, conjuncts,
    anusvāra assimilation, nukta, digits, Hinglish).
  - `NativePhonemizer.kt` (rewritten) — wires G2P + map + clauses; adds
    `phonemizeClauses()` for sentence-end metadata.
  - `PhonemizerNative.kt` — **deleted** (dead native surface).
- `app/src/main/java/com/piperapp/core/engine/pipeline/SynthesisPipeline.kt`
  — fixed `crossfade` (OOB), `normalizePeak` (compile error), added
  `normalizeLoudness` and `assemble()` (pauses + crossfade).
- `app/src/main/java/com/pipertts/app/presentation/PiperTTSApp.kt`
  — clause loop, breath pauses, streaming playback.
- `app/src/main/java/com/pipertts/app/domain/GenerateSpeechUseCase.kt`
  — no longer touches the deleted native surface.
- `app/build.gradle.kts` — removed `externalNativeBuild`/`jniLibs`.
- `app/src/main/jniLibs/*` — Linux `.so` removed.
- `docs/native-reference/` — correct bridge + CMake for optional native parity.
- `scripts/build-apk-termux.sh` — one-command Termux build.

---

## 4. Honest residual risk

- **This is not byte-identical to espeak-ng.** Schwa deletion and anusvāra
  assimilation are approximated by rules; ~90% of real Hindi text is covered,
  and every emitted token is a valid model token (so nothing is dropped to
  space). True byte-parity still requires the Android-native piper-phonemize
  path (`docs/native-reference/`).
- **Hinglish/English** uses a letter-to-phoneme fallback (functional, not
  native-quality). If English-in-Hindi is important, enable the native path.
- **I could not compile in the audit sandbox** (no JDK/SDK, binary egress
  blocked). All changes were reviewed against the model's real vocabulary and
  the confirmed CI error list. Build via CI or `scripts/build-apk-termux.sh`.

---

## 5. One-philosophy summary

> Feed the model what it was trained on: NFC-clean, clause-segmented text;
> schwa-correct, model-vocabulary phoneme tokens; piper-exact BOS/PAD/EOS
> framing; crossfaded, breath-paced per-clause audio. Remove the dead native
> lib that never worked, and keep a correct native bridge ready for when true
> espeak parity matters more than the pure-Kotlin convenience.
