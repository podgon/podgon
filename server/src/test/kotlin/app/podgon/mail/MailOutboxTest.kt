package app.podgon.mail

import app.podgon.IntegrationTest
import app.podgon.mail.internal.OutboxProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MailOutboxTest : IntegrationTest() {
    @Autowired
    lateinit var queue: MailQueue

    @Autowired
    lateinit var processor: OutboxProcessor

    private fun enqueue() =
        queue.enqueue(
            "x@example.com",
            MailTemplate.LOGIN_CODE,
            "ru",
            mapOf(
                "code" to "123456",
                "ttlMinutes" to "10",
            ),
        )

    private fun row() =
        jdbc
            .sql("select status, attempts, payload::text as payload from mail_outbox")
            .query { rs, _ -> Triple(rs.getString("status"), rs.getInt("attempts"), rs.getString("payload")) }
            .single()

    @Test
    fun `после отправки параметры письма стираются`() {
        enqueue()
        assertTrue(row().third.contains("123456"))
        assertEquals(1, processor.processBatch())
        val (status, attempts, payload) = row()
        assertEquals("SENT", status)
        assertEquals(1, attempts)
        assertEquals("{}", payload)
        assertTrue(
            mailSender.sent
                .single()
                .body
                .contains("123456"),
        )
        assertEquals(0, processor.processBatch())
    }

    @Test
    fun `сбой отправки - повтор с нарастающей паузой, затем FAILED`() {
        mailSender.failWith = RuntimeException("SMTP down for x@example.com")
        enqueue()

        assertEquals(1, processor.processBatch())
        assertEquals("PENDING" to 1, row().let { it.first to it.second })
        // пауза ещё не прошла
        assertEquals(0, processor.processBatch())
        clock.advance(Duration.ofSeconds(31))
        assertEquals(1, processor.processBatch())
        assertEquals(2, row().second)

        // доводим до предела попыток (по умолчанию 8)
        repeat(10) {
            clock.advance(Duration.ofHours(2))
            processor.processBatch()
        }
        val (status, attempts, payload) = row()
        assertEquals("FAILED", status)
        assertEquals(8, attempts)
        assertEquals("{}", payload)
    }

    @Test
    fun `после восстановления транспорта письмо уходит`() {
        mailSender.failWith = RuntimeException("temporary")
        enqueue()
        processor.processBatch()
        mailSender.failWith = null
        clock.advance(Duration.ofMinutes(1))
        assertEquals(1, processor.processBatch())
        assertEquals("SENT", row().first)
    }

    @Test
    fun `очистка удаляет старые обработанные письма`() {
        enqueue()
        processor.processBatch()
        clock.advance(Duration.ofDays(8))
        assertEquals(1, processor.cleanup())
    }
}
