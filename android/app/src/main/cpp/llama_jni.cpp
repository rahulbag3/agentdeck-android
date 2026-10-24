#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstring>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

#define LOG_TAG "AgentDeckLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex mutex;
};

std::string listDevices();

std::once_flag g_backend_once;
std::mutex g_backend_name_mutex;
std::string g_last_backend = "CPU";
std::string g_backend_dir;

void setLastBackend(const char * name) {
    std::lock_guard<std::mutex> lock(g_backend_name_mutex);
    g_last_backend = name != nullptr ? name : "CPU";
}

void ensureBackend() {
    std::call_once(g_backend_once, [] {
        llama_log_set([](ggml_log_level level, const char * text, void *) {
            int priority = ANDROID_LOG_INFO;
            if (level == GGML_LOG_LEVEL_ERROR) priority = ANDROID_LOG_ERROR;
            else if (level == GGML_LOG_LEVEL_WARN) priority = ANDROID_LOG_WARN;
            else if (level == GGML_LOG_LEVEL_DEBUG) priority = ANDROID_LOG_DEBUG;
            __android_log_print(priority, LOG_TAG, "%s", text);
        }, nullptr);
        // DL builds (the standard CPU package) load the best CPU variant here.
        if (!g_backend_dir.empty()) {
            LOGI("Loading ggml backends from %s", g_backend_dir.c_str());
            ggml_backend_load_all_from_path(g_backend_dir.c_str());
        }
        llama_backend_init();
        LOGI("Backends: %s", listDevices().c_str());
    });
}

std::string lowerCopy(const char * value) {
    std::string out = value != nullptr ? value : "";
    std::transform(out.begin(), out.end(), out.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return out;
}

bool nameContains(ggml_backend_dev_t device, const char * needle) {
    if (device == nullptr) return false;
    const std::string name = lowerCopy(ggml_backend_dev_name(device));
    const std::string description = lowerCopy(ggml_backend_dev_description(device));
    return name.find(needle) != std::string::npos || description.find(needle) != std::string::npos;
}

bool isNpu(ggml_backend_dev_t device) {
    return nameContains(device, "htp") || nameContains(device, "hexagon") ||
        nameContains(device, "npu");
}

bool isGpu(ggml_backend_dev_t device) {
    if (isNpu(device)) return false;
    return nameContains(device, "gpu") || nameContains(device, "opencl") ||
        nameContains(device, "adreno");
}

bool isCpu(ggml_backend_dev_t device) {
    return nameContains(device, "cpu");
}

ggml_backend_dev_t pickDevice(const std::string & accelerator) {
    ensureBackend();
    ggml_backend_dev_t npu = nullptr;
    ggml_backend_dev_t gpu = nullptr;
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; i++) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (isNpu(device) && npu == nullptr) npu = device;
        if (isGpu(device) && gpu == nullptr) gpu = device;
    }
    if (accelerator == "npu") return npu;
    if (accelerator == "gpu") return gpu;
    if (accelerator == "cpu") return nullptr;
    if (npu != nullptr) return npu;
    return gpu;
}

std::string listDevices() {
    std::string out;
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; i++) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (device == nullptr) continue;
        if (!out.empty()) out += "|";
        out += ggml_backend_dev_name(device);
    }
    return out.empty() ? "CPU" : out;
}

llama_model * loadModel(const std::string & path, ggml_backend_dev_t device, int32_t gpuLayers) {
    llama_model_params params = llama_model_default_params();
    ggml_backend_dev_t devices[2] = {device, nullptr};
    if (device != nullptr) {
        params.devices = devices;
        params.n_gpu_layers = gpuLayers;
    } else {
        params.devices = nullptr;
        params.n_gpu_layers = 0;
    }
    llama_model * model = llama_model_load_from_file(path.c_str(), params);
    params.devices = nullptr;
    return model;
}

std::string toUtf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars != nullptr ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string buildPrompt(
    Session * session,
    const std::string & system,
    const std::vector<std::pair<std::string, std::string>> & history) {
    std::vector<llama_chat_message> messages;
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    roles.reserve(history.size() + 1);
    contents.reserve(history.size() + 1);
    if (!system.empty()) {
        roles.emplace_back("system");
        contents.push_back(system);
    }
    for (const auto & turn : history) {
        roles.push_back(turn.first);
        contents.push_back(turn.second);
    }
    messages.reserve(roles.size());
    for (size_t i = 0; i < roles.size(); i++) {
        messages.push_back({roles[i].c_str(), contents[i].c_str()});
    }

    const char * tmpl = llama_model_chat_template(session->model, nullptr);
    if (tmpl != nullptr) {
        int32_t needed = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true, nullptr, 0);
        if (needed > 0) {
            std::string buffer(static_cast<size_t>(needed), '\0');
            int32_t written = llama_chat_apply_template(
                tmpl, messages.data(), messages.size(), true, buffer.data(), needed);
            if (written > 0) {
                buffer.resize(static_cast<size_t>(written));
                return buffer;
            }
        }
    }

    std::string plain;
    for (const auto & turn : history) {
        plain += turn.first;
        plain += ": ";
        plain += turn.second;
        plain += "\n";
    }
    plain += "assistant:";
    return plain;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeLoad(
    JNIEnv * env, jobject, jstring path, jint threads, jint contextSize, jstring accelerator) {
    ensureBackend();
    const std::string modelPath = toUtf8(env, path);
    if (modelPath.empty()) return 0;

    const std::string requested = accelerator != nullptr
        ? lowerCopy(toUtf8(env, accelerator).c_str())
        : std::string("auto");
    LOGI("Load model=%s accel=%s", modelPath.c_str(), requested.c_str());

    auto * session = new Session();
    ggml_backend_dev_t device = pickDevice(requested);
    session->model = loadModel(modelPath, device, 999);
    if (session->model == nullptr && device != nullptr) {
        // The accelerator refused this model/quant; keep the task usable on CPU.
        device = nullptr;
        session->model = loadModel(modelPath, nullptr, 0);
    }
    if (session->model == nullptr) {
        LOGE("Failed to load model: %s", modelPath.c_str());
        setLastBackend("CPU");
        delete session;
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = contextSize > 0 ? static_cast<uint32_t>(contextSize) : 2048;
    ctxParams.n_batch = std::min<uint32_t>(ctxParams.n_ctx, 512);
    ctxParams.n_threads = threads > 0 ? threads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;
    session->ctx = llama_init_from_model(session->model, ctxParams);
    if (session->ctx == nullptr && device != nullptr) {
        // Context creation can fail on a backend that loaded the weights.
        llama_model_free(session->model);
        device = nullptr;
        session->model = loadModel(modelPath, nullptr, 0);
        if (session->model != nullptr) {
            session->ctx = llama_init_from_model(session->model, ctxParams);
        }
    }
    if (session->ctx == nullptr) {
        LOGE("Failed to create context");
        if (session->model != nullptr) llama_model_free(session->model);
        setLastBackend("CPU");
        delete session;
        return 0;
    }
    session->vocab = llama_model_get_vocab(session->model);
    setLastBackend(device != nullptr ? ggml_backend_dev_name(device) : "CPU");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeInit(JNIEnv * env, jobject, jstring backendDir) {
    const std::string dir = toUtf8(env, backendDir);
    if (!dir.empty()) {
        g_backend_dir = dir;
    }
    ensureBackend();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeDevices(JNIEnv * env, jobject) {
    ensureBackend();
    return env->NewStringUTF(listDevices().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeLastBackend(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_backend_name_mutex);
    return env->NewStringUTF(g_last_backend.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring system,
    jobjectArray roles,
    jobjectArray contents,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jobject callback) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr || callback == nullptr) return -1;

    std::unique_lock<std::mutex> lock(session->mutex);
    session->cancelled.store(false);

    std::vector<std::pair<std::string, std::string>> history;
    const jsize count = roles != nullptr ? env->GetArrayLength(roles) : 0;
    for (jsize i = 0; i < count; i++) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        std::string roleText = toUtf8(env, role);
        std::string contentText = toUtf8(env, content);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
        if (!roleText.empty() && !contentText.empty()) {
            history.emplace_back(roleText, contentText);
        }
    }

    const std::string prompt = buildPrompt(session, toUtf8(env, system), history);
    const int nCtx = static_cast<int>(llama_n_ctx(session->ctx));

    std::vector<llama_token> tokens(static_cast<size_t>(nCtx));
    int tokenCount = llama_tokenize(
        session->vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (tokenCount < 0) {
        tokens.resize(static_cast<size_t>(-tokenCount));
        tokenCount = llama_tokenize(
            session->vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (tokenCount <= 0) return -1;
    tokens.resize(static_cast<size_t>(tokenCount));

    int budget = nCtx - maxTokens - 8;
    if (budget < 16) budget = std::max(16, nCtx / 2);
    if (static_cast<int>(tokens.size()) > budget) {
        tokens.erase(tokens.begin(), tokens.end() - budget);
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onToken == nullptr || env->ExceptionCheck()) {
        // A renamed/missing callback must fail the generation, never crash.
        env->ExceptionClear();
        return -3;
    }

    const int batchSize = std::min(nCtx, 512);
    for (size_t offset = 0; offset < tokens.size(); offset += batchSize) {
        size_t end = std::min(tokens.size(), offset + batchSize);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, static_cast<int32_t>(end - offset));
        if (llama_decode(session->ctx, batch) != 0) return -2;
        if (session->cancelled.load()) return -3;
    }

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    samplerParams.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    if (topP > 0.0f && topP < 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    }
    llama_sampler_chain_add(
        sampler, llama_sampler_init_temp(temperature > 0.0f ? temperature : 0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int generated = 0;
    for (int i = 0; i < maxTokens; i++) {
        if (session->cancelled.load()) { generated = -3; break; }
        llama_token id = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(session->vocab, id)) break;
        llama_sampler_accept(sampler, id);

        char piece[256];
        int pieceLength = llama_token_to_piece(session->vocab, id, piece, sizeof(piece), 0, true);
        if (pieceLength > 0) {
            std::string text(piece, static_cast<size_t>(pieceLength));
            jstring javaText = env->NewStringUTF(text.c_str());
            jboolean keep = env->CallBooleanMethod(callback, onToken, javaText);
            env->DeleteLocalRef(javaText);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                generated = -3;
                break;
            }
            if (keep == JNI_FALSE) { generated = -3; break; }
        }
        generated++;
        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(session->ctx, batch) != 0) { generated = -2; break; }
    }

    llama_sampler_free(sampler);
    return generated;
}

extern "C" JNIEXPORT void JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session != nullptr) session->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return;
    session->cancelled.store(true);
    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}
