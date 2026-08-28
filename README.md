# Piper TTS — Option C Hybrid (Kotlin + ONNX + JNI)

Native Android app for **offline Piper TTS** following Option C — Hybrid architecture.

## Architecture (Option C)
- **Kotlin** (UI, Domain, Data layers)
- **onnxruntime-android** (offline inference engine)
- **JNI bridge** (`piper-phonemize`) via CMake / `CMakeLists.txt`
- **Room** (offline voice config + utterance DB)
- **Compose** (Material3 UI)

## Constraints
- No local `./gradlew build` (1.9 GB RAM sandbox)
- No local NDK / C++ compilation
- All builds via **GitHub Actions CI/CD** (`.github/workflows/android-release.yml`)

## Structure
```
app/
  src/main/java/com/pipertts/app/
    core/app/      — Application class
    data/room/     — VoiceConfig, Utterance, DB, DAO
    domain/        — GenerateSpeechUseCase
    jni/           — PiperPhonemizeJNI (Kotlin interface)
    presentation/  — Compose UI
    service/       — OfflineTTSService
  src/main/jni/    — piper_phonemize_bridge.cpp
  CMakeLists.txt  — JNI build stub
```

## CI
Push to `arena/01a042df-t2s` triggers `.github/workflows/android-release.yml` which builds the release APK on GitHub runners.
