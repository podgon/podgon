package app.podgon.mobile.core.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Clock

/**
 * Кэш ответов сервера для чтения без сети. Хранит JSON под строковым ключом; повреждённая или устаревшая
 * по формату запись читается как отсутствующая. Данные личные (рекомендации групп), поэтому при выходе
 * из аккаунта кэш очищается ([clear]); настройки устройства хранятся здесь же с префиксом [PREF_PREFIX].
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

    /** Удаляет сохранённые ответы; настройки устройства (ключи `pref:`) не личные и остаются. */
    suspend fun clear() {
        val prefs = dao.keys(PREF_PREFIX)
        if (prefs.isEmpty()) {
            dao.clear()
        } else {
            dao.keys("").filter { it !in prefs }.forEach { dao.delete(it) }
        }
    }

    suspend fun keys(prefix: String): List<String> = dao.keys(prefix)

    fun timestamp(): Long = now()
}

/** Префикс ключей настроек устройства (тема): они переживают выход из аккаунта. */
const val PREF_PREFIX = "pref:"
