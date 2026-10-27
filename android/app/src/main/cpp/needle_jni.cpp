#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

#include "needle/needle.h"

#define LOG_TAG "AgentDeckNeedle"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

bool g_loaded = false;
bool g_inited = false;

// The Needle runtime is one process-global, non-thread-safe model. Kotlin also
// serializes calls, but keep a native lock so the JNI layer is safe on its own.
std::mutex g_mutex;

void appendUtf8(std::string & out, uint32_t codePoint) {
    if (codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
        codePoint = 0xFFFD;
    }
    // Standard UTF-8 uses a zero byte for U+0000; the C API takes a C string,
    // so replacing it with the replacement character avoids silent truncation.
    if (codePoint == 0) codePoint = 0xFFFD;
    if (codePoint < 0x80) {
        out.push_back(static_cast<char>(codePoint));
    } else if (codePoint < 0x800) {
        out.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else if (codePoint < 0x10000) {
        out.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
}

// GetStringUTFChars returns modified UTF-8 (CESU-8 for surrogate pairs and
// C0 80 for NUL). Needle expects standard UTF-8, so encode from UTF-16.
std::string toUtf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return std::string();
    const jsize length = env->GetStringLength(value);
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return std::string();

    std::string out;
    out.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t codePoint = chars[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            } else {
                codePoint = 0xFFFD;
            }
        } else if (codePoint >= 0xDC00 && codePoint <= 0xDFFF) {
            codePoint = 0xFFFD;
        }
        appendUtf8(out, codePoint);
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

std::string jsonEscape(const std::string & value) {
    std::string out;
    out.reserve(value.size() + 8);
    for (char c : value) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buffer[7];
                    std::snprintf(
                        buffer, sizeof(buffer), "\\u%04x",
                        static_cast<unsigned int>(static_cast<unsigned char>(c)));
                    out += buffer;
                } else {
                    out.push_back(c);
                }
        }
    }
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
    if (utf16.empty()) return env->NewStringUTF("");
    return env->NewString(
        reinterpret_cast<const jchar *>(utf16.data()),
        static_cast<jsize>(utf16.size()));
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeLoad(
    JNIEnv * env, jobject, jbyteArray cact) {
    if (cact == nullptr) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(g_mutex);
    const jsize size = env->GetArrayLength(cact);
    if (size <= 0) return JNI_FALSE;

    // GetByteArrayElements pins the existing array where the VM allows it,
    // avoiding the extra 35 MB vector copy the old wrapper made. The engine
    // parses the archive during needle_load, so the pointer need not outlive
    // this call.
    jbyte * bytes = env->GetByteArrayElements(cact, nullptr);
    if (bytes == nullptr) return JNI_FALSE;
    const int result = needle_load(
        reinterpret_cast<const unsigned char *>(bytes),
        static_cast<unsigned long long>(size));
    env->ReleaseByteArrayElements(cact, bytes, JNI_ABORT);

    if (result < 0) {
        LOGE("needle_load failed: %s", needle_last_error());
        return JNI_FALSE;
    }
    g_loaded = true;
    LOGI("needle_load ok: %d bytes, models=%d", size, needle_models());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeModels(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return needle_models();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeInit(
    JNIEnv * env, jobject,
    jstring systemPrompt, jstring toolsJson, jstring toolIndexPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_loaded) return JNI_FALSE;

    const std::string system = toUtf8(env, systemPrompt);
    const std::string tools = toUtf8(env, toolsJson);
    const std::string index = toUtf8(env, toolIndexPath);
    const char * indexPtr = toolIndexPath == nullptr ? nullptr : index.c_str();

    g_inited = false;
    const int result = needle_init(system.c_str(), tools.c_str(), indexPtr);
    if (result < 0) {
        LOGE("needle_init failed: %s", needle_last_error());
        return JNI_FALSE;
    }
    g_inited = true;
    LOGI("needle_init ok: static prefix %d tokens", result);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeComplete(
    JNIEnv * env, jobject,
    jstring input, jint maxNewTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) return toJString(env, "{\"error\":\"not initialized\"}");

    const std::string prompt = toUtf8(env, input);
    std::vector<char> out(65536, 0);

    // This wrapper is one-shot: the caller executes the selected tool and never
    // feeds results back into Needle. Reset the conversation so a previous
    // request cannot bias the next one.
    needle_reset();
    const int result = needle_complete(
        prompt.c_str(), nullptr, 0, maxNewTokens, out.data(),
        static_cast<int>(out.size()));
    if (result < 0) {
        LOGE("needle_complete failed: %s", needle_last_error());
        return toJString(
            env,
            std::string("{\"error\":\"") +
                jsonEscape(needle_last_error() ? needle_last_error() : "unknown") + "\"}");
    }
    // needle_complete's return is the generated token count, not a character
    // count. The buffer is zero-initialized, so read to the first null.
    return toJString(env, std::string(out.data()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeReset(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    needle_reset();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_NeedleEngine_nativeLastError(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char * error = needle_last_error();
    return toJString(env, std::string(error ? error : "unknown"));
}
