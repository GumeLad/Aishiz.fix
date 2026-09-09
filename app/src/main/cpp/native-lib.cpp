#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <ctime>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "llama.h"
#include "common.h"
#include "sampling.h"

#define LOG_TAG "Aishiz-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM * g_vm = nullptr;

static std::atomic<bool> g_backend_initialized{false};
static std::mutex g_backend_mutex;

// A single generation at a time keeps llama context/model lifetime deterministic.
static std::mutex g_generation_mutex;

// Cache the selected model across messages. Loading GGUF on every prompt was the
// largest avoidable latency in the original implementation.
static std::mutex g_model_mutex;
static llama_model * g_cached_model = nullptr;
static std::string g_cached_model_path;

struct GenerationRequest {
    std::atomic<bool> stop{false};
    llama_context * context = nullptr;
    common_sampler * sampler = nullptr;
};

static std::mutex g_requests_mutex;
static std::unordered_map<long long, GenerationRequest *> g_requests;
static std::atomic<long long> g_next_id{1};

static void safe_call_string(JNIEnv * env, jobject callback, jmethodID method, jstring value) {
    env->CallVoidMethod(callback, method, value);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static void safe_call_void(JNIEnv * env, jobject callback, jmethodID method) {
    env->CallVoidMethod(callback, method);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static void cleanup_request(long long id) {
    GenerationRequest * req = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_requests_mutex);
        const auto it = g_requests.find(id);
        if (it == g_requests.end()) {
            return;
        }
        req = it->second;
        g_requests.erase(it);
    }

    if (req != nullptr) {
        if (req->sampler != nullptr) {
            common_sampler_free(req->sampler);
            req->sampler = nullptr;
        }
        if (req->context != nullptr) {
            llama_free(req->context);
            req->context = nullptr;
        }
        delete req;
    }
}

static llama_model * get_or_load_model(const std::string & path) {
    std::lock_guard<std::mutex> lock(g_model_mutex);

    if (g_cached_model != nullptr && g_cached_model_path == path) {
        LOGI("Reusing cached model: %s", path.c_str());
        return g_cached_model;
    }

    if (g_cached_model != nullptr) {
        LOGI("Releasing previous cached model");
        llama_model_free(g_cached_model);
        g_cached_model = nullptr;
        g_cached_model_path.clear();
    }

    llama_model_params params = llama_model_default_params();
    params.use_mmap = true;

    LOGI("Loading GGUF model: %s", path.c_str());
    g_cached_model = llama_model_load_from_file(path.c_str(), params);
    if (g_cached_model != nullptr) {
        g_cached_model_path = path;
        LOGI("Model loaded and cached");
    }

    return g_cached_model;
}

// Decode as much complete standard UTF-8 as possible into UTF-16 for JNI.
// Token pieces may split a multibyte character, so NewStringUTF per token is
// unsafe. Incomplete bytes remain buffered until the next token.
static std::u16string drain_utf8(std::string & pending, bool final_flush) {
    std::u16string out;
    size_t i = 0;

    while (i < pending.size()) {
        const unsigned char c0 = static_cast<unsigned char>(pending[i]);
        uint32_t cp = 0;
        size_t needed = 0;

        if (c0 < 0x80) {
            cp = c0;
            needed = 1;
        } else if ((c0 & 0xE0) == 0xC0) {
            cp = c0 & 0x1F;
            needed = 2;
        } else if ((c0 & 0xF0) == 0xE0) {
            cp = c0 & 0x0F;
            needed = 3;
        } else if ((c0 & 0xF8) == 0xF0) {
            cp = c0 & 0x07;
            needed = 4;
        } else {
            out.push_back(static_cast<char16_t>(0xFFFD));
            ++i;
            continue;
        }

        if (i + needed > pending.size()) {
            if (final_flush) {
                out.push_back(static_cast<char16_t>(0xFFFD));
                i = pending.size();
            }
            break;
        }

        bool valid = true;
        for (size_t j = 1; j < needed; ++j) {
            const unsigned char cx = static_cast<unsigned char>(pending[i + j]);
            if ((cx & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            cp = (cp << 6) | (cx & 0x3F);
        }

        if (!valid || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            out.push_back(static_cast<char16_t>(0xFFFD));
            ++i;
            continue;
        }

        if (cp <= 0xFFFF) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        }
        i += needed;
    }

    if (i > 0) {
        pending.erase(0, i);
    }
    return out;
}

static void emit_pending_text(
        JNIEnv * env,
        jobject callback,
        jmethodID on_token,
        std::string & pending,
        bool final_flush) {
    const std::u16string text = drain_utf8(pending, final_flush);
    if (text.empty()) {
        return;
    }

    jstring jtext = env->NewString(
        reinterpret_cast<const jchar *>(text.data()),
        static_cast<jsize>(text.size())
    );
    if (jtext != nullptr) {
        safe_call_string(env, callback, on_token, jtext);
        env->DeleteLocalRef(jtext);
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_aishiz_NativeLlamaBridge_startGeneration(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring modelPath_,
        jstring prompt_,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat minP,
        jfloat repeatPenalty,
        jint maxTokens,
        jint contextLength,
        jint batchSize,
        jint threads,
        jint seed,
        jobject callback) {

    if (callback == nullptr || modelPath_ == nullptr || prompt_ == nullptr) {
        return 0;
    }

    const char * modelPathC = env->GetStringUTFChars(modelPath_, nullptr);
    const char * promptC = env->GetStringUTFChars(prompt_, nullptr);

    std::string modelPath = modelPathC != nullptr ? modelPathC : "";
    std::string prompt = promptC != nullptr ? promptC : "";

    if (modelPathC != nullptr) env->ReleaseStringUTFChars(modelPath_, modelPathC);
    if (promptC != nullptr) env->ReleaseStringUTFChars(prompt_, promptC);

    jclass cbClass = env->GetObjectClass(callback);
    if (cbClass == nullptr) {
        return 0;
    }

    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cbClass);

    if (onToken == nullptr || onComplete == nullptr || onError == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return 0;
    }

    jobject cbGlobal = env->NewGlobalRef(callback);
    if (cbGlobal == nullptr) {
        return 0;
    }

    const long long id = g_next_id.fetch_add(1);
    auto * req = new GenerationRequest();

    {
        std::lock_guard<std::mutex> lock(g_requests_mutex);
        g_requests[id] = req;
    }

    std::thread([
            id,
            req,
            cbGlobal,
            onToken,
            onComplete,
            onError,
            modelPath,
            prompt,
            temperature,
            topP,
            topK,
            minP,
            repeatPenalty,
            maxTokens,
            contextLength,
            batchSize,
            threads,
            seed]() {

        JNIEnv * tenv = nullptr;
        if (g_vm == nullptr ||
            g_vm->AttachCurrentThread(
                reinterpret_cast<JNIEnv **>(reinterpret_cast<void **>(&tenv)),
                nullptr
            ) != JNI_OK ||
            tenv == nullptr) {
            cleanup_request(id);
            return;
        }

        auto fail = [&](const char * message) {
            jstring msg = tenv->NewStringUTF(message);
            if (msg != nullptr) {
                safe_call_string(tenv, cbGlobal, onError, msg);
                tenv->DeleteLocalRef(msg);
            }
        };

        // Only one inference context is allowed to drive the shared cached model
        // at once. A queued generation will start after the prior request exits.
        std::unique_lock<std::mutex> generation_lock(g_generation_mutex);

        if (req->stop.load()) {
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        if (modelPath.empty()) {
            fail("Model path is empty. Select a GGUF model first.");
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        {
            std::lock_guard<std::mutex> backend_lock(g_backend_mutex);
            if (!g_backend_initialized.load()) {
                llama_backend_init();
                g_backend_initialized.store(true);
                LOGI("llama backend initialized");
            }
        }

        llama_model * model = get_or_load_model(modelPath);
        if (model == nullptr) {
            fail("Unable to load the GGUF model. Check the model file and available memory.");
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        const int n_ctx = std::clamp(static_cast<int>(contextLength), 512, 32768);
        const int n_batch = std::clamp(
            static_cast<int>(batchSize),
            32,
            std::min(n_ctx, 2048)
        );

        const unsigned int hardware = std::thread::hardware_concurrency();
        const int auto_threads = std::max(
            2,
            std::min(6, hardware > 2 ? static_cast<int>(hardware) - 2 : 4)
        );
        const int n_threads = threads > 0
            ? std::clamp(static_cast<int>(threads), 1, 12)
            : auto_threads;

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = static_cast<uint32_t>(n_ctx);
        ctx_params.n_batch = static_cast<uint32_t>(n_batch);
        ctx_params.n_ubatch = static_cast<uint32_t>(n_batch);
        ctx_params.n_threads = n_threads;
        ctx_params.n_threads_batch = n_threads;

        req->context = llama_init_from_model(model, ctx_params);
        if (req->context == nullptr) {
            fail("Unable to create the llama inference context.");
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        common_params_sampling sampling;
        sampling.temp = std::clamp(static_cast<float>(temperature), 0.0f, 2.0f);
        sampling.top_p = std::clamp(static_cast<float>(topP), 0.0f, 1.0f);
        sampling.top_k = std::max(0, static_cast<int>(topK));
        sampling.min_p = std::clamp(static_cast<float>(minP), 0.0f, 1.0f);
        sampling.penalty_repeat = std::clamp(
            static_cast<float>(repeatPenalty),
            0.0f,
            2.0f
        );
        sampling.seed = seed >= 0
            ? static_cast<uint32_t>(seed)
            : static_cast<uint32_t>(std::time(nullptr));
        sampling.no_perf = true;

        req->sampler = common_sampler_init(model, sampling);
        if (req->sampler == nullptr) {
            fail("Unable to create the sampler.");
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        std::vector<llama_token> tokens =
            common_tokenize(req->context, prompt, true, true);

        if (tokens.empty()) {
            fail("The prompt produced no tokens.");
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        const int safe_max_tokens = std::clamp(
            static_cast<int>(maxTokens),
            1,
            std::max(1, n_ctx / 2)
        );
        const size_t prompt_limit = static_cast<size_t>(
            std::max(32, n_ctx - safe_max_tokens - 8)
        );

        if (tokens.size() > prompt_limit) {
            std::vector<llama_token> trimmed;
            trimmed.reserve(prompt_limit);
            trimmed.push_back(tokens.front());
            const size_t tail_count = prompt_limit > 1 ? prompt_limit - 1 : 0;
            trimmed.insert(
                trimmed.end(),
                tokens.end() - static_cast<std::ptrdiff_t>(tail_count),
                tokens.end()
            );
            tokens.swap(trimmed);
            LOGI("Prompt trimmed to %zu tokens for context safety", tokens.size());
        }

        llama_batch batch = llama_batch_init(n_batch, 0, 1);

        // Decode long prompts in batches rather than overflowing llama_batch.
        bool decode_failed = false;
        for (size_t offset = 0; offset < tokens.size() && !req->stop.load();) {
            common_batch_clear(batch);
            const size_t end = std::min(tokens.size(), offset + static_cast<size_t>(n_batch));

            for (size_t i = offset; i < end; ++i) {
                const bool need_logits = i + 1 == tokens.size();
                common_batch_add(
                    batch,
                    tokens[i],
                    static_cast<llama_pos>(i),
                    {0},
                    need_logits
                );
            }

            if (llama_decode(req->context, batch) != 0) {
                decode_failed = true;
                break;
            }
            offset = end;
        }

        if (decode_failed) {
            fail("The model failed while processing the prompt.");
            llama_batch_free(batch);
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            generation_lock.unlock();
            g_vm->DetachCurrentThread();
            return;
        }

        const llama_vocab * vocab = llama_model_get_vocab(model);
        llama_pos n_cur = static_cast<llama_pos>(tokens.size());
        int generated = 0;
        std::string utf8_pending;

        while (generated < safe_max_tokens && !req->stop.load()) {
            const llama_token token =
                common_sampler_sample(req->sampler, req->context, -1);

            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            utf8_pending += common_token_to_piece(req->context, token);
            emit_pending_text(tenv, cbGlobal, onToken, utf8_pending, false);

            common_sampler_accept(req->sampler, token, true);

            common_batch_clear(batch);
            common_batch_add(batch, token, n_cur, {0}, true);

            ++n_cur;
            ++generated;

            if (llama_decode(req->context, batch) != 0) {
                LOGE("llama_decode failed during generation");
                break;
            }
        }

        emit_pending_text(tenv, cbGlobal, onToken, utf8_pending, true);
        llama_batch_free(batch);

        LOGI("Generation complete: %d tokens, ctx=%d, batch=%d, threads=%d",
             generated, n_ctx, n_batch, n_threads);

        safe_call_void(tenv, cbGlobal, onComplete);
        tenv->DeleteGlobalRef(cbGlobal);

        cleanup_request(id);
        generation_lock.unlock();
        g_vm->DetachCurrentThread();
    }).detach();

    return static_cast<jlong>(id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aishiz_NativeLlamaBridge_stopGeneration(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jlong requestId) {
    std::lock_guard<std::mutex> lock(g_requests_mutex);
    const auto it = g_requests.find(static_cast<long long>(requestId));
    if (it != g_requests.end() && it->second != nullptr) {
        it->second->stop.store(true);
    }
}

jint JNI_OnLoad(JavaVM * vm, void * /*reserved*/) {
    g_vm = vm;
    LOGI("JNI loaded; llama.cpp backend ready");
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM * /*vm*/, void * /*reserved*/) {
    std::lock_guard<std::mutex> generation_lock(g_generation_mutex);
    std::lock_guard<std::mutex> model_lock(g_model_mutex);

    if (g_cached_model != nullptr) {
        llama_model_free(g_cached_model);
        g_cached_model = nullptr;
        g_cached_model_path.clear();
    }

    if (g_backend_initialized.exchange(false)) {
        llama_backend_free();
    }
}
