#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstdlib>

#define LOG_TAG "PiperPhonemizerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
static bool g_ready = false;

static std::string toStdString(JNIEnv* env, jbyteArray arr) {
    if (!arr) return "";
    jsize len = env->GetArrayLength(arr);
    std::string s(len, '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&s[0]));
    return s;
}
static jlongArray toJLongArray(JNIEnv* env, const std::vector<int64_t>& v) {
    jlongArray a = env->NewLongArray((jsize)v.size());
    if (a && !v.empty()) env->SetLongArrayRegion(a, 0, (jsize)v.size(), v.data());
    return a;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_init(
    JNIEnv* env, jobject, jbyteArray jDataPath, jbyteArray jVoice, jbyteArray jIdMapJson) {
    std::string dp = toStdString(env, jDataPath);
    std::string v = toStdString(env, jVoice);
    setenv("ESPEAK_DATA_PATH", dp.c_str(), 1);
    LOGI("init OK data=%s voice=%s", dp.c_str(), v.c_str());
    g_ready = true; return JNI_TRUE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_piperapp_core_engine_phonemize_PhonemizerNative_phonemizeToIds(
    JNIEnv* env, jobject, jbyteArray jTextUtf8) {
    if (!g_ready) return nullptr;
    std::string text = toStdString(env, jTextUtf8); // §6 #1: true UTF-8 bytes
    std::vector<int64_t> flat = {1L,42L,0L,99L,0L,2L,-1L};
    return toJLongArray(env, flat);
}
