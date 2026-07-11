// JNI-мост к llama.cpp для on-device инференса GGUF-моделей.
//
// Реализует функции, объявленные в LlamaEngine.kt:
//   nativeLoadModel(path, nCtx) : Long   — загрузить модель, вернуть хэндл
//   nativeGenerate(handle, prompt, nPredict) : String
//   nativeFree(handle)
//
// Ориентирован на недавний API llama.cpp (рефактор vocab: llama_vocab_*).
// Декодирование — жадное (argmax), чтобы не зависеть от меняющегося sampler-API.
// При смене тега llama.cpp могут потребоваться мелкие правки имён функций.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "llamabridge"
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    int n_ctx = 2048;
};

bool g_backend_ready = false;

std::string token_to_text(const llama_vocab *vocab, llama_token tok) {
    char buf[256];
    int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
    if (n < 0) return {};
    return std::string(buf, n);
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_wordtrainer_data_coach_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject /*thiz*/, jstring path, jint nCtx) {
    if (!g_backend_ready) { llama_backend_init(); g_backend_ready = true; }

    const char *cpath = env->GetStringUTFChars(path, nullptr);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU; для Vulkan-делегата собрать llama.cpp с GGML_VULKAN

    llama_model *model = llama_load_model_from_file(cpath, mparams);
    env->ReleaseStringUTFChars(path, cpath);
    if (model == nullptr) { LOGe("model load failed"); return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) nCtx;

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (ctx == nullptr) { LOGe("ctx create failed"); llama_free_model(model); return 0; }

    auto *s = new LlamaSession{model, ctx, nCtx};
    return reinterpret_cast<jlong>(s);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_wordtrainer_data_coach_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jstring prompt, jint nPredict) {
    auto *s = reinterpret_cast<LlamaSession *>(handle);
    if (s == nullptr) return env->NewStringUTF("");

    const llama_vocab *vocab = llama_model_get_vocab(s->model);
    const char *cprompt = env->GetStringUTFChars(prompt, nullptr);
    std::string text(cprompt);
    env->ReleaseStringUTFChars(prompt, cprompt);

    // Токенизация промпта.
    int n_max = (int) text.size() + 16;
    std::vector<llama_token> tokens(n_max);
    int n_tok = llama_tokenize(vocab, text.c_str(), (int) text.size(),
                               tokens.data(), n_max, true, true);
    if (n_tok < 0) return env->NewStringUTF("");
    tokens.resize(n_tok);

    // Прогон промпта.
    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    if (llama_decode(s->ctx, batch) != 0) return env->NewStringUTF("");

    std::string out;
    for (int i = 0; i < nPredict; ++i) {
        float *logits = llama_get_logits_ith(s->ctx, -1);
        int n_vocab = llama_vocab_n_tokens(vocab);

        // Жадный выбор следующего токена (argmax).
        llama_token best = 0;
        float best_logit = logits[0];
        for (int t = 1; t < n_vocab; ++t) {
            if (logits[t] > best_logit) { best_logit = logits[t]; best = t; }
        }

        if (llama_vocab_is_eog(vocab, best)) break;
        out += token_to_text(vocab, best);

        llama_batch next = llama_batch_get_one(&best, 1);
        if (llama_decode(s->ctx, next) != 0) break;
    }

    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_wordtrainer_data_coach_LlamaEngine_nativeFree(
        JNIEnv *env, jobject /*thiz*/, jlong handle) {
    auto *s = reinterpret_cast<LlamaSession *>(handle);
    if (s == nullptr) return;
    if (s->ctx) llama_free(s->ctx);
    if (s->model) llama_free_model(s->model);
    delete s;
}
