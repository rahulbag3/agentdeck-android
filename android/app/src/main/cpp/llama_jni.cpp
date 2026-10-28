#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "AgentDeckLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    mtmd_context * mctx = nullptr;
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
            // FastRPC resolves HTP skels through these paths; without them the
            // DSP fails to open a session with AEE_EUNABLETOLOAD (0x80000406).
            const char * dspPaths[] = {
                "ADSP_LIBRARY_PATH", "CDSP_LIBRARY_PATH", "CDSP1_LIBRARY_PATH",
                "CDSP2_LIBRARY_PATH", "CDSP3_LIBRARY_PATH",
            };
            for (const char * name : dspPaths) {
                setenv(name, g_backend_dir.c_str(), 1);
            }
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
    ggml_backend_dev_t cpu = nullptr;
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; i++) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (isNpu(device) && npu == nullptr) npu = device;
        if (isGpu(device) && gpu == nullptr) gpu = device;
        if (isCpu(device) && cpu == nullptr) cpu = device;
    }
    if (accelerator == "npu") return npu != nullptr ? npu : cpu;
    if (accelerator == "gpu") return gpu != nullptr ? gpu : cpu;
    if (accelerator == "cpu") return cpu;
    if (npu != nullptr) return npu;
    if (gpu != nullptr) return gpu;
    return cpu;
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

llama_model * loadModel(const std::string & path, ggml_backend_dev_t device) {
    llama_model_params params = llama_model_default_params();
    ggml_backend_dev_t devices[2] = {device, nullptr};
    if (device != nullptr) {
        // A single-device list also keeps context init from touching the NPU
        // when the request only targets the CPU or GPU.
        params.devices = devices;
        params.n_gpu_layers = isCpu(device) ? 0 : 999;
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
    JNIEnv * env, jobject, jstring path, jstring mmprojPath, jint threads, jint contextSize, jstring accelerator) {
    ensureBackend();
    const std::string modelPath = toUtf8(env, path);
    if (modelPath.empty()) return 0;

    const std::string requested = accelerator != nullptr
        ? lowerCopy(toUtf8(env, accelerator).c_str())
        : std::string("auto");
    LOGI("Load model=%s accel=%s", modelPath.c_str(), requested.c_str());

    auto * session = new Session();
    ggml_backend_dev_t device = pickDevice(requested);
    bool accelerated = device != nullptr && !isCpu(device);
    session->model = loadModel(modelPath, device);
    if (session->model == nullptr && accelerated) {
        // The accelerator refused this model/quant; keep the task usable on CPU.
        accelerated = false;
        device = pickDevice("cpu");
        session->model = loadModel(modelPath, device);
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
    if (session->ctx == nullptr && accelerated) {
        // Context creation can fail on a backend that loaded the weights.
        llama_model_free(session->model);
        accelerated = false;
        device = pickDevice("cpu");
        session->model = loadModel(modelPath, device);
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
    const std::string projectorPath = toUtf8(env, mmprojPath);
    if (!projectorPath.empty()) {
        mtmd_context_params mp = mtmd_context_params_default();
        mp.n_threads = threads > 0 ? threads : 4;
        mp.use_gpu = false;
        mp.print_timings = false;
        mp.image_min_tokens = 256;
        mp.image_max_tokens = 1024;
        session->mctx = mtmd_init_from_file(projectorPath.c_str(), session->model, mp);
        if (session->mctx == nullptr) {
            LOGE("Failed to init vision projector: %s", projectorPath.c_str());
        } else {
            LOGI("Vision projector loaded: %s support_vision=%d", projectorPath.c_str(), mtmd_support_vision(session->mctx));
        }
    }
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
    jobjectArray imagePaths,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jobject callback) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr || callback == nullptr) return -1;

    std::unique_lock<std::mutex> lock(session->mutex);
    session->cancelled.store(false);

    // Each call re-prefills the whole conversation, so any KV state from a
    // previous reply is stale. Without clearing it, the second message in a
    // conversation appends onto a full cache and decode fails ("failed to
    // find a memory slot"), which cuts replies off mid-generation.
    llama_memory_clear(llama_get_memory(session->ctx), true);

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

    std::vector<std::string> images;
    const jsize imageCount = imagePaths != nullptr ? env->GetArrayLength(imagePaths) : 0;
    for (jsize i = 0; i < imageCount; i++) {
        auto image = static_cast<jstring>(env->GetObjectArrayElement(imagePaths, i));
        std::string imagePath = toUtf8(env, image);
        env->DeleteLocalRef(image);
        if (!imagePath.empty()) images.push_back(imagePath);
    }
    if (!images.empty() && session->mctx == nullptr) return -4;

    if (!images.empty()) {
        const char * markerPtr = mtmd_get_marker(session->mctx);
        const std::string marker = markerPtr != nullptr ? markerPtr : mtmd_default_marker();
        for (auto it = history.rbegin(); it != history.rend(); ++it) {
            if (it->first == "user") {
                std::string prefix;
                for (size_t i = 0; i < images.size(); ++i) {
                    prefix += marker;
                    prefix += "\n";
                }
                it->second = prefix + it->second;
                break;
            }
        }
    }

    const std::string prompt = buildPrompt(session, toUtf8(env, system), history);
    const int nCtx = static_cast<int>(llama_n_ctx(session->ctx));
    const int batchSize = std::min(nCtx, 512);

    if (!images.empty()) {
        std::vector<mtmd_bitmap *> bitmaps;
        std::vector<mtmd_helper_video *> videos;
        const mtmd_helper_init_opt opt = mtmd_helper_init_opt_default();
        for (const auto & image : images) {
            auto wrapper = mtmd_helper_bitmap_init_from_file(
                session->mctx, image.c_str(), false, opt);
            if (wrapper.bitmap != nullptr) bitmaps.push_back(wrapper.bitmap);
            if (wrapper.video_ctx != nullptr) videos.push_back(wrapper.video_ctx);
        }
        auto cleanupMedia = [&]() {
            for (auto * bitmap : bitmaps) mtmd_bitmap_free(bitmap);
            for (auto * video : videos) mtmd_helper_video_free(video);
            bitmaps.clear();
            videos.clear();
        };
        if (bitmaps.size() != images.size()) {
            LOGE("Could not load %zu of %zu attached images", images.size() - bitmaps.size(), images.size());
            cleanupMedia();
            return -5;
        }
        mtmd_input_chunks * chunks = mtmd_input_chunks_init();
        mtmd_input_text input{prompt.c_str(), prompt.size(), true, true};
        int32_t result = mtmd_tokenize(session->mctx, chunks, &input, bitmaps.data(), bitmaps.size());
        if (result != 0) {
            LOGE("mtmd_tokenize failed: %d", result);
            mtmd_input_chunks_free(chunks);
            cleanupMedia();
            return -2;
        }
        llama_pos newPast = 0;
        result = mtmd_helper_eval_chunks(
            session->mctx, session->ctx, chunks, 0, 0, batchSize, true, &newPast);
        mtmd_input_chunks_free(chunks);
        cleanupMedia();
        if (result != 0) {
            LOGE("mtmd eval failed: %d", result);
            return -2;
        }
    } else {
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
            // Over budget: keep the template/system head (the model needs its
            // opening structure) plus the most recent tokens, dropping the
            // middle. Keeping only the tail previously cut the chat template
            // apart and produced silence or garbage.
            const size_t headKeep = std::min<size_t>(tokens.size(), 256);
            const size_t tailKeep = static_cast<size_t>(budget) - headKeep;
            std::vector<llama_token> kept(
                tokens.begin(),
                tokens.begin() + static_cast<long>(headKeep));
            kept.insert(
                kept.end(),
                tokens.end() - static_cast<long>(tailKeep),
                tokens.end());
            tokens.swap(kept);
        }

        for (size_t offset = 0; offset < tokens.size(); offset += batchSize) {
            size_t end = std::min(tokens.size(), offset + batchSize);
            llama_batch batch = llama_batch_get_one(tokens.data() + offset, static_cast<int32_t>(end - offset));
            if (llama_decode(session->ctx, batch) != 0) return -2;
            if (session->cancelled.load()) return -3;
        }
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onToken == nullptr || env->ExceptionCheck()) {
        // A renamed/missing callback must fail the generation, never crash.
        env->ExceptionClear();
        return -3;
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
    std::string pending;  // incomplete UTF-8 tail carried across tokens
    for (int i = 0; i < maxTokens; i++) {
        if (session->cancelled.load()) { generated = -3; break; }
        llama_token id = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(session->vocab, id)) break;
        llama_sampler_accept(sampler, id);

        char piece[256];
        int pieceLength = llama_token_to_piece(session->vocab, id, piece, sizeof(piece), 0, true);
        if (pieceLength > 0) {
            pending.append(piece, static_cast<size_t>(pieceLength));
            // Multi-byte UTF-8 characters can be split across tokens, and
            // NewStringUTF aborts the process on such fragments. Decode into
            // UTF-16 ourselves; incomplete trailing bytes wait for the next
            // token, invalid sequences become U+FFFD.
            std::u16string utf16;
            size_t at = 0;
            const size_t len = pending.size();
            while (at < len) {
                const unsigned char lead = static_cast<unsigned char>(pending[at]);
                size_t need;
                uint32_t cp;
                if (lead < 0x80) { need = 1; cp = lead; }
                else if ((lead & 0xE0) == 0xC0) { need = 2; cp = lead & 0x1F; }
                else if ((lead & 0xF0) == 0xE0) { need = 3; cp = lead & 0x0F; }
                else if ((lead & 0xF8) == 0xF0) { need = 4; cp = lead & 0x07; }
                else { utf16.push_back(u'\uFFFD'); at++; continue; }
                if (at + need > len) break;  // incomplete tail stays pending
                bool valid = true;
                for (size_t k = 1; k < need; k++) {
                    const unsigned char cont = static_cast<unsigned char>(pending[at + k]);
                    if ((cont & 0xC0) != 0x80) { valid = false; break; }
                    cp = (cp << 6) | (cont & 0x3F);
                }
                if (!valid ||
                    (need == 2 && cp < 0x80) || (need == 3 && cp < 0x800) ||
                    (need == 4 && cp < 0x10000) || cp > 0x10FFFF ||
                    (cp >= 0xD800 && cp <= 0xDFFF)) {
                    utf16.push_back(u'\uFFFD');
                    at++;
                    continue;
                }
                if (cp <= 0xFFFF) {
                    utf16.push_back(static_cast<char16_t>(cp));
                } else {
                    cp -= 0x10000;
                    utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
                    utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
                }
                at += need;
            }
            pending.erase(0, at);
            if (!utf16.empty()) {
                jstring javaText = env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
                jboolean keep = env->CallBooleanMethod(callback, onToken, javaText);
                env->DeleteLocalRef(javaText);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    generated = -3;
                    break;
                }
                if (keep == JNI_FALSE) { generated = -3; break; }
            }
        }
        generated++;
        llama_batch batch = llama_batch_get_one(&id, 1);
        if (llama_decode(session->ctx, batch) != 0) { generated = -2; break; }
    }

    llama_sampler_free(sampler);
    return generated;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_agentdeck_app_data_LocalEngine_nativeVisionReady(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    return session != nullptr && session->mctx != nullptr ? JNI_TRUE : JNI_FALSE;
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
    if (session->mctx != nullptr) mtmd_free(session->mctx);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}
