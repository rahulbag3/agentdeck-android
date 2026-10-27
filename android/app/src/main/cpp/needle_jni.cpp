#include <jni.h>
#include <android/log.h>
#include <cstdint>
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
    // needle_complete returns standard UTF-8. NewStringUTF expects modified
    // UTF-8, which rejects 4-byte sequences (emoji) and can abort the process
    // with "input is not valid Modified UTF-8". Decode to UTF-16 and use
    // NewString so any argument the model produces survives the JNI boundary.
    std::u16string utf16;
    utf16.reserve(value.size());
    size_t index = 0;
    while (index < value.size()) {
        const unsigned char lead = static_cast<unsigned char>(value[index]);
        uint32_t codePoint = 0xFFFD;
        size_t extra = 0;
        if (lead < 0x80) {
            codePoint = lead;
        } else if ((lead & 0xE0) == 0xC0) {
            codePoint = lead & 0x1F;
            extra = 1;
        } else if ((lead & 0xF0) == 0xE0) {
            codePoint = lead & 0x0F;
            extra = 2;
        } else if ((lead & 0xF8) == 0xF0) {
            codePoint = lead & 0x07;
            extra = 3;
        }
        if (extra > 0 && index + extra >= value.size()) {
            extra = 0;
            codePoint = 0xFFFD;
        } else {
            for (size_t k = 1; k <= extra; ++k) {
                const unsigned char next = static_cast<unsigned char>(value[index + k]);
                if ((next & 0xC0) != 0x80) {
                    codePoint = 0xFFFD;
                    extra = 0;
                    break;
                }
                codePoint = (codePoint << 6) | (next & 0x3F);
            }
        }
        index += extra + 1;
        if (codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            codePoint = 0xFFFD;
        }
        if (codePoint >= 0x10000) {
            codePoint -= 0x10000;
            utf16.push_back(static_cast<char16_t>(0xD800 + (codePoint >> 10)));
            utf16.push_back(static_cast<char16_t>(0xDC00 + (codePoint & 0x3FF)));
        } else {
            utf16.push_back(static_cast<char16_t>(codePoint));
        }
    }
    return env->NewString(
        reinterpret_cast<const jchar *>(utf16.data()),
        static_cast<jsize>(utf16.size()));
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
    std::vector<char> out(65536);
    const int result = needle_complete(
        prompt.c_str(), nullptr, 0, maxNewTokens, out.data(), static_cast<int>(out.size()));
    if (result < 0) {
        LOGE("needle_complete failed: %s", needle_last_error());
        return toJString(env, std::string("{\"error\":\"") + needle_last_error() + "\"}");
    }
    // needle_complete's return is the generated token count, not a character
    // count. The buffer is zero-initialized, so read to the first null.
    return toJString(env, std::string(out.data()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeLastError(JNIEnv * env, jobject) {
    return toJString(env, std::string(needle_last_error() ? needle_last_error() : "unknown"));
}
