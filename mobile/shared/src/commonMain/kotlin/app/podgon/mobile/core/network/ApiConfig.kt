package app.podgon.mobile.core.network

/** Настройки доступа к API, задаются приложением при сборке. */
data class ApiConfig(
    /** Основной адрес API (только схема, хост и порт, без пути). */
    val baseUrl: String,
    /** Запасные адреса на случай блокировки основного (ADR-010, замечание R2 ревью); пробуются по порядку. */
    val fallbackBaseUrls: List<String> = emptyList(),
    /** Платформа для заголовка `X-Client-Platform`: `android` или `ios`. */
    val platform: String = "",
    /** Номер сборки для заголовка `X-Client-Version` (0 — не передаётся). */
    val appVersion: Int = 0,
    /** Куда вести пользователя с экрана обязательного обновления (страница в магазине или сайт). */
    val updateUrl: String = "",
)
