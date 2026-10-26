package app.podgon

import app.podgon.mail.MailSender
import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist
import com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageHasKey
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Часы, которыми управляет тест (TTL кодов, токенов, лимиты по времени). */
class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: java.time.Duration) {
        now = now.plus(duration)
    }
}

/**
 * Строгая проверка запросов по OpenAPI. Тесты, которые намеренно шлют запросы, нарушающие контракт
 * (неверный формат кода, пустое тело, нет токена), отключают её через [IntegrationTest.invalidRequests];
 * ответы сверяются с контрактом всегда.
 */
class ContractMode {
    @Volatile
    var lenientRequests = false
}

/** Почтовый транспорт-заглушка: запоминает отправленные письма, может имитировать сбой. */
class CapturingMailSender : MailSender {
    data class Sent(
        val to: String,
        val subject: String,
        val body: String,
    )

    val sent = mutableListOf<Sent>()
    var failWith: RuntimeException? = null

    override fun send(
        to: String,
        subject: String,
        body: String,
    ) {
        failWith?.let { throw it }
        sent += Sent(to, subject, body)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestSupportConfig {
    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock(Instant.parse("2026-06-01T10:00:00Z"))

    @Bean
    @Primary
    fun capturingMailSender(): CapturingMailSender = CapturingMailSender()

    @Bean
    fun contractMode(): ContractMode = ContractMode()

    /** Каждый запрос и ответ MockMvc сверяется с `api/openapi.yaml`. */
    @Bean
    fun openApiValidation(mode: ContractMode): MockMvcBuilderCustomizer {
        val whitelist =
            ValidationErrorsWhitelist
                .create()
                .withRule("не-API пути (actuator и т.п.)", messageHasKey("validation.request.path.missing"))
                .withRule("намеренно некорректные запросы") { message, _, _, _ ->
                    mode.lenientRequests && message.key.startsWith("validation.request.")
                }
        val validator =
            OpenApiInteractionValidator
                .createFor(
                    java.nio.file.Path
                        .of("../api/openapi.yaml")
                        .toAbsolutePath()
                        .normalize()
                        .toUri()
                        .toString(),
                ).withWhitelist(whitelist)
                .build()
        return MockMvcBuilderCustomizer { builder ->
            @Suppress("UNCHECKED_CAST")
            (builder as ConfigurableMockMvcBuilder<DefaultMockMvcBuilder>)
                .alwaysExpect<DefaultMockMvcBuilder>(openApi().isValid(validator))
        }
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig::class, TestSupportConfig::class)
abstract class IntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var mailSender: CapturingMailSender

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var contractMode: ContractMode

    /** Выполняет [block], не проверяя запросы на соответствие контракту (ответы проверяются). */
    fun <T> invalidRequests(block: () -> T): T {
        contractMode.lenientRequests = true
        try {
            return block()
        } finally {
            contractMode.lenientRequests = false
        }
    }

    @BeforeEach
    fun cleanState() {
        jdbc.sql("delete from groups").update()
        jdbc.sql("delete from reviews").update()
        jdbc.sql("delete from tags").update()
        jdbc.sql("delete from refresh_tokens").update()
        jdbc.sql("delete from email_codes").update()
        jdbc.sql("delete from mail_outbox").update()
        jdbc.sql("delete from users where status <> 'SYSTEM'").update()
        clock.now = Instant.parse("2026-06-01T10:00:00Z")
        mailSender.sent.clear()
        mailSender.failWith = null
    }

    fun post(
        path: String,
        body: Any?,
        ip: String = "10.0.0.1",
        acceptLanguage: String? = null,
        bearer: String? = null,
    ): MvcResult =
        mockMvc
            .post("/api/v1$path") {
                contentType = MediaType.APPLICATION_JSON
                content = json.writeValueAsString(body)
                acceptLanguage?.let { header("Accept-Language", it) }
                bearer?.let { header("Authorization", "Bearer $it") }
                with { request ->
                    request.remoteAddr = ip
                    request
                }
            }.andReturn()

    fun body(result: MvcResult): JsonNode = json.readTree(result.response.contentAsString)

    /** Код, который сервер отправил (читаем из outbox до фоновой отправки). */
    fun latestCode(email: String): String =
        jdbc
            .sql(
                "select payload ->> 'code' from mail_outbox where to_address = :email order by created_at desc limit 1",
            ).param("email", email)
            .query(String::class.java)
            .single()

    fun requestCode(
        email: String,
        ip: String = "10.0.0.1",
    ): MvcResult = post("/auth/otp/request", mapOf("email" to email, "captchaToken" to "t"), ip)

    /** Полный вход нового пользователя: код → регистрация. Возвращает токены. */
    fun registerUser(
        email: String,
        username: String,
        deviceInfo: String? = "Test device",
    ): JsonNode {
        requestCode(email)
        val verify =
            body(
                post(
                    "/auth/otp/verify",
                    mapOf(
                        "email" to email,
                        "code" to latestCode(email),
                        "deviceInfo" to deviceInfo,
                    ),
                ),
            )
        val registration = verify["registrationToken"].asString()
        clock.advance(java.time.Duration.ofSeconds(61))
        return body(
            post(
                "/auth/register",
                mapOf(
                    "registrationToken" to registration,
                    "username" to username,
                    "displayName" to "Имя $username",
                    "deviceInfo" to deviceInfo,
                ),
            ),
        )
    }

    /** Повторный вход существующего пользователя по коду. Возвращает токены. */
    fun login(
        email: String,
        deviceInfo: String? = "Another device",
    ): JsonNode {
        clock.advance(java.time.Duration.ofSeconds(61))
        requestCode(email)
        return body(
            post(
                "/auth/otp/verify",
                mapOf(
                    "email" to email,
                    "code" to latestCode(email),
                    "deviceInfo" to deviceInfo,
                ),
            ),
        )["tokens"]
    }
}
