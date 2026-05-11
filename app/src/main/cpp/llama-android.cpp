/*
 * JNI bridge to llama.cpp — optimized for Android voice/text parsing.
 *
 * Key optimizations vs default llama.cpp setup:
 *   - use_mmap=false + use_mlock=true: force model into RAM, avoid disk thrashing
 *   - n_ctx=512: short context for voice JSON tasks (was 2048)
 *   - Greedy sampler: deterministic, fastest for structured output
 */

#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <math.h>
#include <string>
#include <vector>
#include <chrono>
#include <unistd.h>
#include "llama.h"

#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

jclass la_int_var;
jmethodID la_int_var_value;
jmethodID la_int_var_inc;

std::string cached_token_chars;

// ── Inline replacements for common/* helpers we need ─────────────────────────

static std::vector<llama_token> tokenize(
        const struct llama_context * ctx,
        const std::string & text,
        bool add_special,
        bool parse_special = false) {
    int n_tokens = text.length() + 2 * add_special;
    std::vector<llama_token> result(n_tokens);
    n_tokens = llama_tokenize(llama_get_model(ctx), text.data(), text.length(),
                              result.data(), result.size(), add_special, parse_special);
    if (n_tokens < 0) {
        result.resize(-n_tokens);
        int check = llama_tokenize(llama_get_model(ctx), text.data(), text.length(),
                                   result.data(), result.size(), add_special, parse_special);
        GGML_ASSERT(check == -n_tokens);
    } else {
        result.resize(n_tokens);
    }
    return result;
}

static std::string token_to_piece(const struct llama_context * ctx, llama_token token) {
    std::vector<char> result(8, 0);
    const int n_tokens = llama_token_to_piece(llama_get_model(ctx), token,
                                              result.data(), result.size(), 0, true);
    if (n_tokens < 0) {
        result.resize(-n_tokens);
        int check = llama_token_to_piece(llama_get_model(ctx), token,
                                         result.data(), result.size(), 0, true);
        GGML_ASSERT(check == -n_tokens);
    } else {
        result.resize(n_tokens);
    }
    return std::string(result.data(), result.size());
}

static void batch_clear(llama_batch & batch) {
    batch.n_tokens = 0;
}

static void batch_add(llama_batch & batch, llama_token id, llama_pos pos,
                      const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits  [batch.n_tokens] = logits;
    batch.n_tokens++;
}

bool is_valid_utf8(const char * string) {
    if (!string) return true;
    const unsigned char * bytes = (const unsigned char *)string;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00)      num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

static void log_callback(ggml_log_level level, const char * fmt, void * data) {
    if (level == GGML_LOG_LEVEL_ERROR)     __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_INFO) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN,  TAG, fmt, data);
    else                                   __android_log_print(ANDROID_LOG_DEFAULT, TAG, fmt, data);
}

// ── JNI exports ──────────────────────────────────────────────────────────────

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_load_1model(JNIEnv *env, jobject, jstring filename) {
    llama_model_params model_params = llama_model_default_params();

    // CRITICAL FIX: force model into RAM, not mmap from disk.
    // Default llama.cpp uses mmap which is fast on desktop SSDs but catastrophic
    // on Android: every token op triggers disk page faults. With use_mmap=false,
    // the entire model loads into RAM at start (slower load, but tokenization
    // and generation are 10-100x faster afterwards).
    model_params.use_mmap = false;
    model_params.use_mlock = true;  // pin pages in RAM, prevent paging out

    auto path_to_model = env->GetStringUTFChars(filename, 0);
    LOGi("Loading model from %s (mmap=off, mlock=on)", path_to_model);

    auto model = llama_load_model_from_file(path_to_model, model_params);
    env->ReleaseStringUTFChars(filename, path_to_model);

    if (!model) {
        LOGe("load_model() failed");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "load_model() failed");
        return 0;
    }
    LOGi("Model loaded into RAM successfully");
    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_free_1model(JNIEnv *, jobject, jlong model) {
    llama_free_model(reinterpret_cast<llama_model *>(model));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_new_1context(JNIEnv *env, jobject, jlong jmodel) {
    auto model = reinterpret_cast<llama_model *>(jmodel);
    if (!model) {
        LOGe("new_context(): model is null");
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "model null");
        return 0;
    }

    int n_threads = 4;  // target 4 performance cores on big.LITTLE phones
    LOGi("Using %d threads", n_threads);

    llama_context_params ctx_params = llama_context_default_params();
    // CRITICAL FIX: small context = small KV cache = fast inference.
    // Voice JSON: ~200 token input + ~80 output = 280 tokens max. 512 is generous.
    // Was 2048 (4x bigger KV cache than needed = 4x slower attention).
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context * context = llama_new_context_with_model(model, ctx_params);
    if (!context) {
        LOGe("llama_new_context_with_model() returned null");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "new_context failed");
        return 0;
    }
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_free_1context(JNIEnv *, jobject, jlong context) {
    llama_free(reinterpret_cast<llama_context *>(context));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_backend_1free(JNIEnv *, jobject) {
    llama_backend_free();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_log_1to_1android(JNIEnv *, jobject) {
    llama_log_set(log_callback, NULL);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_bench_1model(
        JNIEnv *env, jobject, jlong context_pointer, jlong model_pointer, jlong batch_pointer,
        jint pp, jint tg, jint pl, jint nr) {
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const int n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);
    char buf[64];
    snprintf(buf, sizeof buf, "n_ctx=%d", n_ctx);
    return env->NewStringUTF(buf);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_new_1batch(
        JNIEnv *, jobject, jint n_tokens, jint embd, jint n_seq_max) {
    llama_batch * batch = new llama_batch{
            0, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr,
    };
    if (embd) batch->embd = (float *) malloc(sizeof(float) * n_tokens * embd);
    else      batch->token = (llama_token *) malloc(sizeof(llama_token) * n_tokens);
    batch->pos      = (llama_pos *)     malloc(sizeof(llama_pos) * n_tokens);
    batch->n_seq_id = (int32_t *)       malloc(sizeof(int32_t) * n_tokens);
    batch->seq_id   = (llama_seq_id **) malloc(sizeof(llama_seq_id *) * (n_tokens + 1));
    for (int i = 0; i < n_tokens; ++i) {
        batch->seq_id[i] = (llama_seq_id *) malloc(sizeof(llama_seq_id) * n_seq_max);
    }
    batch->seq_id[n_tokens] = nullptr;
    batch->logits   = (int8_t *) malloc(sizeof(int8_t) * n_tokens);
    return reinterpret_cast<jlong>(batch);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_free_1batch(JNIEnv *, jobject, jlong batch_pointer) {
    llama_batch_free(*reinterpret_cast<llama_batch *>(batch_pointer));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_new_1sampler(JNIEnv *, jobject) {
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);

    // CRITICAL FIX: greedy sampling for deterministic JSON extraction.
    // Was: temp(0.1) + top_p(0.95) + dist sampler (3 stages, more compute).
    // Now: just greedy = pick highest-probability token every time.
    // Faster AND more consistent for structured output like JSON.
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    return reinterpret_cast<jlong>(smpl);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_free_1sampler(JNIEnv *, jobject, jlong sampler_pointer) {
    llama_sampler_free(reinterpret_cast<llama_sampler *>(sampler_pointer));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_backend_1init(JNIEnv *, jobject, jboolean numa) {
    llama_backend_init();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_system_1info(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_completion_1init(
        JNIEnv *env, jobject, jlong context_pointer, jlong batch_pointer,
        jstring jtext, jboolean format_chat, jint n_len) {

    cached_token_chars.clear();
    const auto text  = env->GetStringUTFChars(jtext, 0);
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch   = reinterpret_cast<llama_batch *>(batch_pointer);

    bool parse_special = (format_chat == JNI_TRUE);
    LOGi("completion_init: tokenizing %d chars...", (int)strlen(text));
    auto t_start = std::chrono::high_resolution_clock::now();
    const auto tokens_list = tokenize(context, text, true, parse_special);
    auto t_end = std::chrono::high_resolution_clock::now();
    auto tokenize_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    LOGi("completion_init: tokenization done in %lld ms, %zu tokens", (long long)tokenize_ms, tokens_list.size());

    auto n_ctx        = llama_n_ctx(context);
    auto n_kv_req     = tokens_list.size() + n_len;
    LOGi("n_len = %d, n_ctx = %d, n_kv_req = %zu", n_len, n_ctx, n_kv_req);
    if (n_kv_req > n_ctx) LOGe("error: n_kv_req > n_ctx");

    batch_clear(*batch);
    for (size_t i = 0; i < tokens_list.size(); i++) {
        batch_add(*batch, tokens_list[i], i, { 0 }, false);
    }
    batch->logits[batch->n_tokens - 1] = true;

    auto d_start = std::chrono::high_resolution_clock::now();
    if (llama_decode(context, *batch) != 0) LOGe("llama_decode() failed");
    auto d_end = std::chrono::high_resolution_clock::now();
    auto decode_ms = std::chrono::duration_cast<std::chrono::milliseconds>(d_end - d_start).count();
    LOGi("completion_init: initial decode done in %lld ms", (long long)decode_ms);

    env->ReleaseStringUTFChars(jtext, text);
    return batch->n_tokens;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_completion_1loop(
        JNIEnv * env, jobject, jlong context_pointer, jlong batch_pointer,
        jlong sampler_pointer, jint n_len, jobject intvar_ncur) {

    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch   = reinterpret_cast<llama_batch *>(batch_pointer);
    const auto sampler = reinterpret_cast<llama_sampler *>(sampler_pointer);
    const auto model   = llama_get_model(context);

    if (!la_int_var) la_int_var = env->GetObjectClass(intvar_ncur);
    if (!la_int_var_value) la_int_var_value = env->GetMethodID(la_int_var, "getValue", "()I");
    if (!la_int_var_inc)   la_int_var_inc   = env->GetMethodID(la_int_var, "inc",      "()V");

    const auto new_token_id = llama_sampler_sample(sampler, context, -1);
    const auto n_cur = env->CallIntMethod(intvar_ncur, la_int_var_value);

    if (llama_token_is_eog(model, new_token_id) || n_cur == n_len) return nullptr;

    auto new_token_chars = token_to_piece(context, new_token_id);
    cached_token_chars += new_token_chars;

    jstring new_token = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        new_token = env->NewStringUTF(cached_token_chars.c_str());
        cached_token_chars.clear();
    } else {
        new_token = env->NewStringUTF("");
    }

    batch_clear(*batch);
    batch_add(*batch, new_token_id, n_cur, { 0 }, true);
    env->CallVoidMethod(intvar_ncur, la_int_var_inc);

    if (llama_decode(context, *batch) != 0) LOGe("llama_decode() returned non-zero");
    return new_token;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_smartexpensetracker_llm_LLamaAndroid_kv_1cache_1clear(JNIEnv *, jobject, jlong context) {
    llama_kv_cache_clear(reinterpret_cast<llama_context *>(context));
}