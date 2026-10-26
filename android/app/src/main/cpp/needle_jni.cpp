#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "needle/needle.h"

#define LOG_TAG "AgentDeckNeedle"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

bool g_loaded = false;
bool g_inited = false;

std::string toStd(JNIEnv * env, jstring value) {
    if (value == nullptr) return std::string();
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring toJString(JNIEnv * env, const std::string & value) {
    // needle_complete output is valid UTF-8 JSON.
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeLoad(
    JNIEnv * env, jobject, jbyteArray cact) {
    if (cact == nullptr) return JNI_FALSE;
    const jsize size = env->GetArrayLength(cact);
    std::vector<unsigned char> buffer(static_cast<size_t>(size));
    env->GetByteArrayRegion(cact, 0, size, reinterpret_cast<jbyte *>(buffer.data()));
    const int result = needle_load(buffer.data(), static_cast<unsigned long long>(size));
    if (result < 0) {
        LOGE("needle_load failed: %s", needle_last_error());
        return JNI_FALSE;
    }
    g_loaded = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeModels(JNIEnv *, jobject) {
    return needle_models();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeInit(
    JNIEnv * env, jobject,
    jstring systemPrompt, jstring toolsJson, jstring toolIndexPath) {
    if (!g_loaded) return JNI_FALSE;
    const int result = needle_init(
        toStd(env, systemPrompt).c_str(),
        toStd(env, toolsJson).c_str(),
        toStd(env, toolIndexPath).c_str());
    if (result < 0) {
        LOGE("needle_init failed: %s", needle_last_error());
        return JNI_FALSE;
    }
    g_inited = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeComplete(
    JNIEnv * env, jobject,
    jstring input, jint maxNewTokens) {
    if (!g_inited) return toJString(env, "{\"error\":\"not initialized\"}");
    const std::string prompt = toStd(env, input);
    std::vector<char> out(32768);
    const int result = needle_complete(
        prompt.c_str(), nullptr, 0, maxNewTokens, out.data(), static_cast<int>(out.size()));
    if (result < 0) {
        LOGE("needle_complete failed: %s", needle_last_error());
        return toJString(env, std::string("{\"error\":\"") + needle_last_error() + "\"}");
    }
    return toJString(env, std::string(out.data()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeLastError(JNIEnv * env, jobject) {
    return toJString(env, std::string(needle_last_error() ? needle_last_error() : "unknown"));
}
