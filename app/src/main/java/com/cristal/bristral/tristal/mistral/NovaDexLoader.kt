package com.cristal.bristral.tristral.mistral

import android.content.Context
import android.os.Build
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import java.io.IOException
import java.nio.ByteBuffer

class NovaDexLoader(private val context: Context) {

    companion object {
        private const val TAG = "NovaDexLoader"
        private const val ENCRYPTED_ASSET = "nova.enc"

        @Volatile
        var dexClassLoader: ClassLoader? = null
            private set

        init {
            try {
                System.loadLibrary("novaguard")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "libnovaguard.so failed: ${e.message}")
            }
        }
    }

    private external fun decryptNovaDex(encryptedBlob: ByteArray): ByteArray?

    fun load(): Boolean {
        if (dexClassLoader != null) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val encBlob = try {
            context.assets.open(ENCRYPTED_ASSET).use { it.readBytes() }
        } catch (e: IOException) { return false }
        val dexBytes = decryptNovaDex(encBlob) ?: return false
        return try {
            dexClassLoader = InMemoryDexClassLoader(ByteBuffer.wrap(dexBytes), context.classLoader)
            Log.i(TAG, "Nova DEX loaded OK")
            true
        } catch (e: Exception) { false }
    }
}
