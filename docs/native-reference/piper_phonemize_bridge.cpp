// piper_phonemize_bridge.cpp — CORRECTED reference JNI bridge (OPTIONAL native path).
//
// WHY THIS IS NOT IN THE DEFAULT BUILD:
//   The `libpiper_phonemize.so` that shipped in this repo is a *desktop-Linux
//   (glibc)* binary — its DT_NEEDED lists `libc.so.6`, `libstdc++.so.6`,
//   `libgcc_s.so.1`, `libespeak-ng.so.1`. Android uses bionic `libc.so` and
//   has none of those, so this .so can never load on Android. That was the real
//   root cause of the historical "crash-prone NDK C++" experience — not the
//   bridge logic.
//
//   True byte-parity with the espeak-ng phonemizer requires an ANDROID build of
//   piper-phonemize (+ espeak-ng data) produced by NDK on a host/CI, then this
//   bridge links against it. To enable it:
//     1. Build piper-phonemize for arm64-v8a/x86_64 (NDK) with espeak-ng.
//     2. Bundle libpiper_phonemize.so + libespeak-ng.so + espeak-ng-data.
//     3. Re-add `externalNativeBuild { cmake { ... } }` to app/build.gradle.kts
//        and restore jniLibs packaging. The app's NativePhonemizer then routes
//        through the native lib automatically.
//
// THE HISTORICAL BUG THIS FIXES:
//   Never use GetStringUTFChars for Indic text. It returns *modified* UTF-8,
//   which corrupts Devanagari (outside the BMP) -> garbage phonemes, and on
//   several ABIs/inputs a segfault. Always pass String.toByteArray(UTF_8) from
//   Kotlin and read it with GetByteArrayRegion (true UTF-8) as below.

#include <jni.h>
#include <cstdlib>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <android/log.h>

// piper-phonemize headers (pin an exact tag; see docs/native-reference/CMakeLists.txt).
#include <piper-phonemize/phonemize.hpp>
#include <piper-phonemize/phoneme_ids.hpp>
#include <piper-phonemize/json.hpp>

static std::mutex gMutex;
static bool gReady = false;
static piper::eSpeakPhonemeConfig gESpeakCfg;
static piper::PhonemeIdConfig gIdCfg;

namespace {
std::string fromUtf8Bytes(JNIEnv* env, jbyteArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&s[0]));
    }
    return s;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_init(
    JNIEnv* env, jobject, jbyteArray jDataPath, jbyteArray jVoice, jbyteArray jIdMapJson) {
    std::lock_guard<std::mutex> lock(gMutex);
    std::string dataPath = fromUtf8Bytes(env, jDataPath);
    std::string voice    = fromUtf8Bytes(env, jVoice);
    std::string idJson   = fromUtf8Bytes(env, jIdMapJson);

    setenv("ESPEAK_DATA_PATH", dataPath.c_str(), 1);

    try {
        piper::json j = piper::json::parse(idJson);
        gIdCfg.phonemeIdMap =
            std::make_shared<piper::PhonemeIdMap>(piper::json::parsePhonemeIdMap(j));
        gESpeakCfg.voice = voice;
        gReady = true;
        return JNI_TRUE;
    } catch (const std::exception&) {
        gReady = false;
        return JNI_FALSE;
    }
}

// Flattened ids; -1 marks a clause boundary. nullptr on failure/empty.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_phonemizeToIds(
    JNIEnv* env, jobject, jbyteArray jTextUtf8) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gReady) return nullptr;

    std::string text = fromUtf8Bytes(env, jTextUtf8);
    std::vector<std::vector<piper::Phoneme>> sentences;
    piper::phonemize_eSpeak(text, gESpeakCfg, sentences);

    std::vector<jlong> flat;
    for (auto& phons : sentences) {
        std::vector<piper::PhonemeId> ids;
        std::map<piper::Phoneme, std::size_t> missing;
        piper::phonemes_to_ids(phons, gIdCfg, ids, missing);
        flat.insert(flat.end(), ids.begin(), ids.end());
        flat.push_back(-1);
    }
    if (flat.empty()) return nullptr;

    jlongArray out = env->NewLongArray(static_cast<jsize>(flat.size()));
    if (out == nullptr) return nullptr;
    env->SetLongArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    return out;
}
