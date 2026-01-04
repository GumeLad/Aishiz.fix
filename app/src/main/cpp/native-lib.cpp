#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <chrono>

static JavaVM* g_vm = nullptr;

struct Request {
    std::atomic<bool> stop{false};
};

static std::mutex g_mutex;
static std::unordered_map<long long, Request*> g_requests;
static std::atomic<long long> g_next_id{1};

static void safeCallVoid(JNIEnv* env, jobject callback, jmethodID mid, jstring arg) {
    env->CallVoidMethod(callback, mid, arg);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
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
    auto* req = new Request();

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_requests[id] = req;
    }

    // Spawn generation thread
    std::thread([id, req, cbGlobal, onToken, onComplete, onError, modelPath, prompt,
                 temperature, topP, topK, repeatPenalty, maxTokens, seed]() {

        JNIEnv* tenv = nullptr;
        if (g_vm->AttachCurrentThread(reinterpret_cast<JNIEnv **>(reinterpret_cast<void **>(&tenv)), nullptr) != JNI_OK || tenv == nullptr) {
            return;
        }

        // Basic validation: model path should exist (we're not actually loading it yet)
        if (modelPath.empty()) {
            jstring msg = tenv->NewStringUTF("Model path is empty. Select a model first.");
            safeCallVoid(tenv, cbGlobal, onError, msg);
            tenv->DeleteLocalRef(msg);
            tenv->DeleteGlobalRef(cbGlobal);

            g_vm->DetachCurrentThread();
            return;
        }

        // IMPORTANT:
        // This native generator is a *streaming stub*.
        // It proves the JNI + callback plumbing. Once llama.cpp is wired in, replace this
        // with real token generation from llama.cpp.
        std::string text =
                "Engine status: Streaming online. "
                "Llama backend pending integration. "
                "Input received: " + prompt + ".";

        // Stream word-by-word
        std::string tok_str;
        for (size_t i = 0; i < text.size(); i++) {
            if (req->stop.load()) break;

            char c = text[i];
            tok_str.push_back(c);

            // Emit token chunks periodically to simulate streaming
            if (tok_str.size() >= 6 || c == ' ' || i == text.size() - 1) {
                jstring jtok = tenv->NewStringUTF(tok_str.c_str());
                safeCallVoid(tenv, cbGlobal, onToken, jtok);
                tenv->DeleteLocalRef(jtok);
                tok_str.clear();
                std::this_thread::sleep_for(std::chrono::milliseconds(12));
            }
        }

        tenv->CallVoidMethod(cbGlobal, onComplete);
        if (tenv->ExceptionCheck()) tenv->ExceptionClear();

        tenv->DeleteGlobalRef(cbGlobal);

        // Cleanup request entry
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            auto it = g_requests.find(id);
            if (it != g_requests.end()) {
                delete it->second;
                g_requests.erase(it);
            }
        }

        g_vm->DetachCurrentThread();
    }).detach();

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
    return JNI_VERSION_1_6;
}
