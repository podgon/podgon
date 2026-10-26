package app.podgon.mail.internal

import app.podgon.mail.MailQueue
import app.podgon.mail.MailTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

@Service
class OutboxMailQueue(
    private val repository: OutboxRepository,
    private val json: JsonMapper,
    private val clock: Clock,
) : MailQueue {
    @Transactional
    override fun enqueue(
        to: String,
        template: MailTemplate,
        locale: String,
        params: Map<String, String>,
    ) {
        repository.insert(
            UUID.randomUUID(),
            to,
            template.name,
            locale,
            json.writeValueAsString(params),
            clock.instant(),
        )
    }
}
