#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <android/log.h>

#include "llama.h"
#include "common.h"
#include "sampling.h"

#define LOG_TAG "Aishiz-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;

struct GenerationRequest {
    std::atomic<bool> stop{false};
    std::thread thread;
    llama_model* model = nullptr;
    llama_context* context = nullptr;
    common_sampler* sampler = nullptr;
};

static std::mutex g_mutex;
static std::unordered_map<long long, GenerationRequest*> g_requests;
static std::atomic<long long> g_next_id{1};

static void safeCallVoid(JNIEnv* env, jobject callback, jmethodID mid, jstring arg) {
    env->CallVoidMethod(callback, mid, arg);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static void cleanup_request(long long id) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_requests.find(id);
    if (it != g_requests.end()) {
        GenerationRequest* req = it->second;
        if (req->sampler) {
            common_sampler_free(req->sampler);
        }
        if (req->context) {
            llama_free(req->context);
        }
        if (req->model) {
            llama_model_free(req->model);
        }
        delete req;
        g_requests.erase(it);
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_aishiz_NativeLlamaBridge_startGeneration(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring modelPath_,
        jstring prompt_,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repeatPenalty,
        jint maxTokens,
        jint seed,
        jobject callback) {

    if (callback == nullptr) return 0;

    const char* modelPathC = env->GetStringUTFChars(modelPath_, nullptr);
    const char* promptC = env->GetStringUTFChars(prompt_, nullptr);

    std::string modelPath = modelPathC ? modelPathC : "";
    std::string prompt = promptC ? promptC : "";

    env->ReleaseStringUTFChars(modelPath_, modelPathC);
    env->ReleaseStringUTFChars(prompt_, promptC);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    if (onToken == nullptr || onComplete == nullptr || onError == nullptr) {
        return 0;
    }

    // Hold callback beyond this JNI call
    jobject cbGlobal = env->NewGlobalRef(callback);

    long long id = g_next_id.fetch_add(1);
    auto* req = new GenerationRequest();

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_requests[id] = req;
    }

    // Spawn generation thread
    req->thread = std::thread([id, req, cbGlobal, onToken, onComplete, onError, modelPath, prompt,
                               temperature, topP, topK, repeatPenalty, maxTokens, seed]() {

        JNIEnv* tenv = nullptr;
        if (g_vm->AttachCurrentThread(reinterpret_cast<JNIEnv **>(reinterpret_cast<void **>(&tenv)), nullptr) != JNI_OK || tenv == nullptr) {
            return;
        }

        // Validate model path
        if (modelPath.empty()) {
            jstring msg = tenv->NewStringUTF("Model path is empty. Select a model first.");
            safeCallVoid(tenv, cbGlobal, onError, msg);
            tenv->DeleteLocalRef(msg);
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            g_vm->DetachCurrentThread();
            return;
        }

        LOGI("Loading model from: %s", modelPath.c_str());

        // Initialize llama backend
        llama_backend_init();

        // Load model
        llama_model_params model_params = llama_model_default_params();
        req->model = llama_model_load_from_file(modelPath.c_str(), model_params);
        
        if (req->model == nullptr) {
            LOGE("Failed to load model from %s", modelPath.c_str());
            jstring msg = tenv->NewStringUTF("Failed to load model. Check if the file is a valid GGUF model.");
            safeCallVoid(tenv, cbGlobal, onError, msg);
            tenv->DeleteLocalRef(msg);
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            g_vm->DetachCurrentThread();
            llama_backend_free();
            return;
        }

        LOGI("Model loaded successfully");

        // Create context
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 2048;  // Default context size
        ctx_params.n_batch = 512;
        ctx_params.n_threads = 4;
        
        req->context = llama_init_from_model(req->model, ctx_params);
        
        if (req->context == nullptr) {
            LOGE("Failed to create context");
            jstring msg = tenv->NewStringUTF("Failed to create inference context.");
            safeCallVoid(tenv, cbGlobal, onError, msg);
            tenv->DeleteLocalRef(msg);
            tenv->DeleteGlobalRef(cbGlobal);
            cleanup_request(id);
            g_vm->DetachCurrentThread();
            llama_backend_free();
            return;
        }

        LOGI("Context created successfully");

        // Setup sampler
        common_params_sampling sparams;
        sparams.temp = temperature;
        sparams.top_p = topP;
        sparams.top_k = topK;
        sparams.penalty_repeat = repeatPenalty;
        sparams.seed = seed >= 0 ? seed : time(nullptr);
        
        req->sampler = common_sampler_init(req->model, sparams);

        // Tokenize prompt
        std::vector<llama_token> tokens = common_tokenize(req->context, prompt, true, true);
        LOGI("Prompt tokenized to %zu tokens", tokens.size());

        // Prepare batch for prompt processing
        llama_batch batch = llama_batch_init(512, 0, 1);
        
        // Add prompt tokens to batch
        for (size_t i = 0; i < tokens.size(); i++) {
            common_batch_add(batch, tokens[i], i, {0}, false);
        }
        
        // Process prompt (last token needs logits)
        if (tokens.size() > 0) {
            batch.logits[batch.n_tokens - 1] = true;
        }
        
        if (llama_decode(req->context, batch) != 0) {
            LOGE("Failed to decode prompt");
            jstring msg = tenv->NewStringUTF("Failed to process prompt.");
            safeCallVoid(tenv, cbGlobal, onError, msg);
            tenv->DeleteLocalRef(msg);
            tenv->DeleteGlobalRef(cbGlobal);
            llama_batch_free(batch);
            cleanup_request(id);
            g_vm->DetachCurrentThread();
            llama_backend_free();
            return;
        }

        LOGI("Starting generation loop");

        // Generation loop
        int n_generated = 0;
        llama_pos n_cur = tokens.size();
        
        while (n_generated < maxTokens && !req->stop.load()) {
            // Sample next token
            const llama_token new_token_id = common_sampler_sample(req->sampler, req->context, -1);
            
            // Get vocab for token checking
            const llama_vocab* vocab = llama_model_get_vocab(req->model);
            
            // Check for EOS
            if (llama_vocab_is_eog(vocab, new_token_id)) {
                LOGI("EOS token generated, stopping");
                break;
            }
            
            // Convert token to string
            std::string token_str = common_token_to_piece(req->context, new_token_id);
            
            // Send token to callback
            jstring jtok = tenv->NewStringUTF(token_str.c_str());
            safeCallVoid(tenv, cbGlobal, onToken, jtok);
            tenv->DeleteLocalRef(jtok);
            
            // Prepare for next iteration
            common_sampler_accept(req->sampler, new_token_id, true);
            
            common_batch_clear(batch);
            common_batch_add(batch, new_token_id, n_cur, {0}, true);
            
            n_cur++;
            n_generated++;
            
            // Decode
            if (llama_decode(req->context, batch) != 0) {
                LOGE("Failed to decode token");
                break;
            }
        }

        LOGI("Generation completed, generated %d tokens", n_generated);

        // Free batch
        llama_batch_free(batch);

        // Call onComplete
        tenv->CallVoidMethod(cbGlobal, onComplete);
        if (tenv->ExceptionCheck()) tenv->ExceptionClear();

        tenv->DeleteGlobalRef(cbGlobal);
        
        // Cleanup
        cleanup_request(id);

        g_vm->DetachCurrentThread();
        llama_backend_free();
    });
    
    req->thread.detach();

    return static_cast<jlong>(id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aishiz_NativeLlamaBridge_stopGeneration(JNIEnv* env, jobject /*thiz*/, jlong requestId) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_requests.find(static_cast<long long>(requestId));
    if (it != g_requests.end() && it->second != nullptr) {
        it->second->stop.store(true);
    }
}

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    LOGI("JNI_OnLoad called, llama.cpp integration active");
    return JNI_VERSION_1_6;
}
