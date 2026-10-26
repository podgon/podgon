package app.podgon.mobile.core.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock

/**
 * Кэш ответов сервера для чтения без сети. Хранит JSON под строковым ключом; повреждённая или устаревшая
 * по формату запись читается как отсутствующая. Данные личные (рекомендации групп), поэтому при выходе
 * из аккаунта кэш очищается ([clear]).
 */
class ResponseCache(
    val dao: CachedResponseDao,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> read(key: String): T? {
        val entry = dao.find(key) ?: return null
        return runCatching { json.decodeFromString(serializer<T>(), entry.json) }.getOrNull()
    }

    suspend inline fun <reified T> write(
        key: String,
        value: T,
    ) {
        dao.save(CachedResponse(key, json.encodeToString(serializer<T>(), value), timestamp()))
    }

    suspend fun remove(key: String) = dao.delete(key)

    suspend fun clear() = dao.clear()

    fun timestamp(): Long = now()
}
