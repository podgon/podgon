package app.podgon.mobile.core.network

/** Базовый адрес API без завершающего `/`. Задаётся приложением (можно сменить при блокировке — ADR-010). */
data class ApiConfig(
    val baseUrl: String,
)
