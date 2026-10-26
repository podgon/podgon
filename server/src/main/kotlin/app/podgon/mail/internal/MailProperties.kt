package app.podgon.mail.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("podgon.mail")
data class MailProperties(
    /** Адрес отправителя. */
    val from: String = "Podgon <no-reply@podgon.local>",
    /** Включает фоновую отправку (в тестах выключается, обработчик вызывается вручную). */
    val pollEnabled: Boolean = true,
    val maxAttempts: Int = 8,
    val batchSize: Int = 20,
    /** Начальная задержка повтора; растёт вдвое с каждой попыткой. */
    val retryBackoff: Duration = Duration.ofSeconds(30),
)
