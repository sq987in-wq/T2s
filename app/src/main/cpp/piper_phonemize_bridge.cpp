// piper_phonemize_bridge.cpp — Android-native espeak-ng phonemizer (OPT-IN path).
//
// This is the REAL byte-parity bridge. It links piper-phonemize (which embeds
// espeak-ng) and reproduces the exact phoneme->ID stream the Piper hi_IN models
// were trained on — including espeak's schwa deletion, stress/tone markers,
// anusvara assimilation, and per-clause sentence boundaries.
//
// WHY IT IS OPT-IN (not in the default build):
//   Cross-compiling piper-phonemize + espeak-ng for ARM64 requires the Android
//   NDK and a network fetch of the pinned piper-phonemize source. The default
//   `assembleRelease` (used by CI and the Termux build script) stays on the
//   proven pure-Kotlin engine and does NOT compile this, so nothing breaks.
//   To enable: build with `-Pt2s.native=true` on an NDK-equipped host/CI, then
//   ship the produced libpiper_phonemize.so in jniLibs (see
//   scripts/build-native-lib.sh).
//
// THE CRITICAL FIX vs. history:
//   Never use GetStringUTFChars for Indic text — it returns MODIFIED UTF-8,
//   which corrupts Devanagari (outside the BMP) into garbage phonemes and, on
//   several ABIs, a segfault. We always receive String.toByteArray(UTF_8) from
//   Kotlin and read it with GetByteArrayRegion (true UTF-8).

#include <jni.h>
#include <cstdlib>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <android/log.h>

#include <piper-phonemize/phonemize.hpp>
#include <piper-phonemize/phoneme_ids.hpp>
#include <piper-phonemize/json.hpp>

static std::mutex gMutex;
static bool gReady = false;
static piper::eSpeakPhonemeConfig gESpeakCfg;
static piper::PhonemeIdConfig gIdCfg;

namespace {
std::string fromUtf8Bytes(JNIEnv* env, jbyteArray arr) {
    if (!arr) return "";
    jsize len = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(len), '\0');
    if (len > 0) {
        env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&s[0]));
    }
    return s;
}

void toJLongArray(JNIEnv* env, jlongArray out, const std::vector<jlong>& v) {
    if (!out || v.empty()) return;
    env->SetLongArrayRegion(out, 0, static_cast<jsize>(v.size()), v.data());
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_nativeInit(
    JNIEnv* env, jobject, jbyteArray jDataPath, jbyteArray jVoice, jbyteArray jIdMapJson) {
    std::lock_guard<std::mutex> lock(gMutex);
    std::string dataPath = fromUtf8Bytes(env, jDataPath);
    std::string voice    = fromUtf8Bytes(env, jVoice);
    std::string idJson   = fromUtf8Bytes(env, jIdMapJson);

    // Point espeak-ng at the extracted data dir before first use.
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
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_nativePhonemizeToIds(
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
    toJLongArray(env, out, flat);
    return out;
}
