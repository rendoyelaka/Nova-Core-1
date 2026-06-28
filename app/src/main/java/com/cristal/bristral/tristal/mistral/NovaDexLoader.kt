package com.cristal.bristral.tristral.mistral

import android.content.Context
import android.os.Build
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import java.io.IOException
import java.nio.ByteBuffer

/**
 * NovaDexLoader
 *
 * Decrypts assets/nova.enc at runtime and loads Nova's real DEX
 * via InMemoryDexClassLoader. Must be called from
 * LauncherApplication.onCreate() BEFORE any other Nova class
 * is referenced — otherwise the JVM will try to resolve classes
 * that don't exist in the stub classes.dex and crash.
 *
 * This class and LauncherApplication are the ONLY classes that
 * must remain in the stub classes.dex. Everything else can be
 * encrypted into nova.enc.
 *
 * Place at: app/src/main/java/com/cristal/bristral/tristral/mistral/NovaDexLoader.kt
 */
class NovaDexLoader(private val context: Context) {

    companion object {
        private const val TAG           = "NovaDexLoader"
        private const val ENCRYPTED_ASSET = "nova.enc"

        // Singleton ClassLoader — once loaded, reused everywhere
        @Volatile
        var dexClassLoader: ClassLoader? = null
            private set

        init {
            // Load libnovaguard.so — built from nova_dex_decrypt.cpp
            try {
                System.loadLibrary("novaguard")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "libnovaguard.so failed to load: ${e.message}")
            }
        }
    }

    // JNI — implemented in nova_dex_decrypt.cpp
    private external fun decryptNovaDex(encryptedBlob: ByteArray): ByteArray?

    /**
     * Load Nova's encrypted DEX into memory.
     * Returns true on success, false on any failure.
     *
     * Call from LauncherApplication.onCreate() as the very first thing.
     */
    fun load(): Boolean {
        // Already loaded — skip
        if (dexClassLoader != null) return true

        // InMemoryDexClassLoader requires API 26+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e(TAG, "InMemoryDexClassLoader requires API 26+")
            return false
        }

        // Step 1: Read encrypted blob from assets
        val encBlob = readEncryptedAsset() ?: run {
            Log.e(TAG, "Failed to read $ENCRYPTED_ASSET from assets")
            return false
        }
        Log.i(TAG, "Read ${encBlob.size} bytes from assets/$ENCRYPTED_ASSET")

        // Step 2: Decrypt via NDK
        val dexBytes = decryptNovaDex(encBlob) ?: run {
            Log.e(TAG, "NDK decryption failed")
            return false
        }
        Log.i(TAG, "Decrypted ${dexBytes.size} bytes")

        // Step 3: Load into InMemoryDexClassLoader
        return try {
            val loader = InMemoryDexClassLoader(
                ByteBuffer.wrap(dexBytes),
                context.classLoader
            )
            dexClassLoader = loader
            Log.i(TAG, "Nova DEX loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "InMemoryDexClassLoader failed: ${e.message}")
            false
        }
    }

    private fun readEncryptedAsset(): ByteArray? {
        return try {
            context.assets.open(ENCRYPTED_ASSET).use { it.readBytes() }
        } catch (e: IOException) {
            Log.e(TAG, "readEncryptedAsset: ${e.message}")
            null
        }
    }
}
