package app.podgon.mobile.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Токены, зашифрованные AES-256-GCM ключом из Android Keystore (ключ не покидает хранилище).
 * В SharedPreferences лежит только шифртекст; если ключ недоступен (например, после восстановления
 * на другом устройстве), токены считаются отсутствующими и пользователь входит заново.
 */
class AndroidTokenStorage(
    context: Context,
) : TokenStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun read(): StoredTokens? =
        withContext(Dispatchers.IO) {
            val encoded = prefs.getString(KEY_TOKENS, null) ?: return@withContext null
            runCatching {
                val raw = Base64.decode(encoded, Base64.NO_WRAP)
                val iv = raw.copyOfRange(0, IV_SIZE)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
                val plain = cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE).toString(Charsets.UTF_8)
                val (access, refresh) = plain.split(SEPARATOR, limit = 2)
                StoredTokens(access, refresh)
            }.getOrNull()
        }

    override suspend fun write(tokens: StoredTokens) {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted =
                cipher.doFinal(
                    "${tokens.accessToken}$SEPARATOR${tokens.refreshToken}".toByteArray(Charsets.UTF_8),
                )
            val packed = cipher.iv + encrypted
            prefs.edit().putString(KEY_TOKENS, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().remove(KEY_TOKENS).apply() }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "podgon_secure"
        const val KEY_TOKENS = "tokens"
        const val KEY_ALIAS = "podgon_tokens_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = "\n"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
