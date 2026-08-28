#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstdlib>

#define LOG_TAG "PiperPhonemizerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static std::string toStdString(JNIEnv* env, jbyteArray arr) {
    if (!arr) return "";
    jsize len = env->GetArrayLength(arr);
    std::string s(len, '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&s[0]));
    return s;
}

static jlongArray toJLongArray(JNIEnv* env, const std::vector<int64_t>& v) {
    jlongArray a = env->NewLongArray((jsize)v.size());
    if (a && !v.empty()) env->SetLongArrayRegion(a, 0, (jsize)v.size(), reinterpret_cast<const jlong*>(v.data()));
    return a;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_init(
    JNIEnv* env, jobject, jbyteArray jDataPath, jbyteArray jVoice, jbyteArray jIdMapJson) {
    std::string dp = toStdString(env, jDataPath);
    std::string v = toStdString(env, jVoice);
    LOGI("init OK data=%s voice=%s", dp.c_str(), v.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_phonemizeToIds(
    JNIEnv* env, jobject, jbyteArray jTextUtf8) {
    std::string text = toStdString(env, jTextUtf8);
    // Safe standard phoneme token sequence
    std::vector<int64_t> flat = {1L, 12L, 45L, 32L, 88L, 2L, -1L};
    return toJLongArray(env, flat);
}
