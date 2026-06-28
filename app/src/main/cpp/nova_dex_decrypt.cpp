// nova_dex_decrypt.cpp
// ─────────────────────────────────────────────────────────────────────────────
// NDK C++ JNI function that decrypts assets/nova.enc at runtime.
//
// JNI export matches NovaDexLoader.kt exactly:
//   package : com.cristal.bristral.tristal.mistral
//   class   : NovaDexLoader
//   method  : decryptNovaDex(ByteArray): ByteArray?
//
// Blob layout written by encrypt_nova_dex.py:
//   [12 bytes IV][ciphertext][16 bytes GCM tag]
//
// NOVA_DEX_KEY_HEX is auto-patched by encrypt_nova_dex.py at build time.
// ─────────────────────────────────────────────────────────────────────────────

#include <jni.h>
#include <openssl/evp.h>
#include <android/log.h>
#include <cstring>
#include <vector>

#define LOG_TAG "NovaDexDecrypt"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static constexpr int KEY_SIZE = 32;
static constexpr int IV_SIZE  = 12;
static constexpr int TAG_SIZE = 16;

// Auto-patched by encrypt_nova_dex.py before NDK build
static const char *NOVA_DEX_KEY_HEX =
    "NOVA_DEX_KEY_PLACEHOLDER_64_CHARS";

static bool hexToBytes(const char *hex, unsigned char *out, int len) {
    for (int i = 0; i < len; i++) {
        unsigned int b = 0;
        if (sscanf(hex + i * 2, "%2x", &b) != 1) return false;
        out[i] = static_cast<unsigned char>(b);
    }
    return true;
}

static bool aesGcmDecrypt(const unsigned char *src, int srcLen,
                           const unsigned char *key,
                           std::vector<unsigned char> &plain) {
    if (srcLen < IV_SIZE + TAG_SIZE) {
        LOGE("Blob too small: %d bytes", srcLen);
        return false;
    }

    const unsigned char *iv         = src;
    int                  cipherLen  = srcLen - IV_SIZE - TAG_SIZE;
    const unsigned char *ciphertext = src + IV_SIZE;
    const unsigned char *tag        = src + IV_SIZE + cipherLen;

    plain.resize(static_cast<size_t>(cipherLen));
    bool ok = false;
    int  outLen = 0;

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (!ctx) { LOGE("EVP_CIPHER_CTX_new failed"); return false; }

    do {
        if (EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1) break;
        if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, IV_SIZE, nullptr) != 1) break;
        if (EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv) != 1) break;

        int len1 = 0;
        if (EVP_DecryptUpdate(ctx, plain.data(), &len1, ciphertext, cipherLen) != 1) break;
        outLen = len1;

        if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, TAG_SIZE,
                                  const_cast<unsigned char *>(tag)) != 1) break;
        int len2 = 0;
        if (EVP_DecryptFinal_ex(ctx, plain.data() + len1, &len2) != 1) {
            LOGE("GCM tag verification FAILED");
            break;
        }
        outLen += len2;
        ok = true;
    } while (false);

    EVP_CIPHER_CTX_free(ctx);
    if (ok) {
        plain.resize(static_cast<size_t>(outLen));
        LOGI("Decrypted %d bytes OK", outLen);
    }
    return ok;
}

// JNI export — must match NovaDexLoader.kt:
//   external fun decryptNovaDex(encryptedBlob: ByteArray): ByteArray?
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_cristal_bristral_tristal_mistral_NovaDexLoader_decryptNovaDex(
        JNIEnv *env, jobject /* thiz */, jbyteArray encryptedBlob) {

    // Parse key
    unsigned char key[KEY_SIZE];
    if (!hexToBytes(NOVA_DEX_KEY_HEX, key, KEY_SIZE)) {
        LOGE("Failed to parse embedded key");
        return nullptr;
    }

    // Get blob bytes from JVM
    jsize blobLen = env->GetArrayLength(encryptedBlob);
    if (blobLen <= 0) { LOGE("Empty blob"); return nullptr; }

    jbyte *blobRaw = env->GetByteArrayElements(encryptedBlob, nullptr);
    if (!blobRaw) { LOGE("GetByteArrayElements null"); return nullptr; }

    // Decrypt
    std::vector<unsigned char> plain;
    bool ok = aesGcmDecrypt(
        reinterpret_cast<const unsigned char *>(blobRaw),
        static_cast<int>(blobLen), key, plain);

    env->ReleaseByteArrayElements(encryptedBlob, blobRaw, JNI_ABORT);

    if (!ok || plain.empty()) { LOGE("Decryption failed"); return nullptr; }

    // Verify DEX magic (dex\n035 or dex\n036 or dex\n039)
    if (plain.size() < 4 || plain[0] != 'd' || plain[1] != 'e' || plain[2] != 'x') {
        LOGE("Decrypted data is not a valid DEX (no dex magic)");
        return nullptr;
    }

    // Zero key from stack
    memset(key, 0, KEY_SIZE);

    LOGI("Nova DEX decrypted successfully — %zu bytes", plain.size());

    // Return decrypted bytes to Kotlin
    jbyteArray result = env->NewByteArray(static_cast<jsize>(plain.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(plain.size()),
                             reinterpret_cast<jbyte *>(plain.data()));
    return result;
}
