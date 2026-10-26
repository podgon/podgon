package app.podgon.mobile.core.auth

/** Пара токенов, сохранённая на устройстве. */
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Защищённое хранилище токенов. Платформенная реализация (Android Keystore / iOS Keychain)
 * подключается через DI; в тестах — подмена в памяти.
 */
interface TokenStorage {
    suspend fun read(): StoredTokens?

    suspend fun write(tokens: StoredTokens)

    suspend fun clear()
}
