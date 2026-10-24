#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "llama.h"

#define LOG_TAG "AgentDeckLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex mutex;
};

std::once_flag g_backend_once;

void ensureBackend() {
    std::call_once(g_backend_once, [] { llama_backend_init(); });
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
    JNIEnv * env, jobject, jstring path, jint threads, jint contextSize) {
    ensureBackend();
    const std::string modelPath = toUtf8(env, path);
    if (modelPath.empty()) return 0;

    auto * session = new Session();
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    session->model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (session->model == nullptr) {
        LOGE("Failed to load model: %s", modelPath.c_str());
        delete session;
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = contextSize > 0 ? static_cast<uint32_t>(contextSize) : 2048;
    ctxParams.n_batch = std::min<uint32_t>(ctxParams.n_ctx, 512);
    ctxParams.n_threads = threads > 0 ? threads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;
    session->ctx = llama_init_from_model(session->model, ctxParams);
    if (session->ctx == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(session->model);
        delete session;
        return 0;
    }
    session->vocab = llama_model_get_vocab(session->model);
    return reinterpret_cast<jlong>(session);
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
