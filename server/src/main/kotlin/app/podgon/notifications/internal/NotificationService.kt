package app.podgon.notifications.internal

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.contract.NotificationInfo
import app.podgon.contract.NotificationKind
import app.podgon.contract.NotificationPage
import app.podgon.contract.NotificationSettings
import app.podgon.identity.UserDeleting
import app.podgon.notifications.Notifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/**
 * Уведомления: создание с учётом настроек (нет строки настроек — всё включено), лента новыми сверху,
 * отметка прочитанного. Старше [RETENTION] удаляются ночью.
 */
@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val clock: Clock,
) : Notifier {
    override fun invitation(
        userId: UUID,
        invitationId: UUID,
        groupName: String,
        invitedBy: String,
    ) {
        if (!repository.settings(userId).invitations) return
        save(
            userId,
            NotificationKind.INVITATION,
            invitationId,
            mapOf("groupName" to groupName, "invitedBy" to invitedBy),
        )
    }

    override fun filterMatch(
        userId: UUID,
        reviewId: UUID,
        reviewTitle: String,
        filterName: String,
    ) {
        if (!repository.settings(userId).filterMatches) return
        save(
            userId,
            NotificationKind.FILTER_MATCH,
            reviewId,
            mapOf(
                "reviewTitle" to reviewTitle,
                "filterName" to filterName,
            ),
        )
    }

    fun page(
        userId: UUID,
        cursor: String?,
        limit: Int?,
    ): NotificationPage {
        val size = (limit ?: PAGE).coerceIn(1, PAGE_MAX)
        val after = cursor?.let(::decodeCursor)
        val rows = repository.page(userId, after?.first, after?.second, size + 1)
        val items = rows.take(size).map(::toDto)
        val next = if (rows.size > size) rows[size - 1].let { encodeCursor(it.createdAt, it.id) } else null
        return NotificationPage(items, repository.unread(userId), next)
    }

    fun unread(userId: UUID): Int = repository.unread(userId)

    fun markRead(
        userId: UUID,
        ids: List<String>?,
    ) = repository.markRead(userId, ids?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }, now())

    fun settings(userId: UUID): NotificationSettings =
        repository.settings(userId).let { NotificationSettings(it.invitations, it.filterMatches) }

    fun saveSettings(
        userId: UUID,
        settings: NotificationSettings,
    ): NotificationSettings {
        repository.saveSettings(userId, SettingsRow(settings.invitations, settings.filterMatches))
        return settings(userId)
    }

    /** Удаление аккаунта: уведомления и настройки больше не нужны (строка пользователя остаётся обезличенной). */
    @EventListener
    fun on(event: UserDeleting) = repository.deleteAll(event.userId)

    @Scheduled(cron = "0 30 4 * * *")
    fun cleanup() = repository.deleteOlderThan(clock.instant().minus(RETENTION))

    private fun save(
        userId: UUID,
        kind: NotificationKind,
        refId: UUID,
        data: Map<String, String>,
    ) {
        val json = JsonObject(data.mapValues { JsonPrimitive(it.value) }).toString()
        repository.insert(UUID.randomUUID(), userId, kind.name, refId, json, now())
    }

    private fun toDto(row: NotificationRow): NotificationInfo {
        val data = Json.parseToJsonElement(row.data).jsonObject

        fun field(name: String) = data[name]?.jsonPrimitive?.contentOrNull
        val kind = NotificationKind.valueOf(row.kind)
        return NotificationInfo(
            id = row.id.toString(),
            kind = kind,
            createdAt = row.createdAt.toString(),
            read = row.readAt != null,
            invitationId = row.refId.takeIf { kind == NotificationKind.INVITATION }?.toString(),
            groupName = field("groupName"),
            invitedBy = field("invitedBy"),
            reviewId = row.refId.takeIf { kind == NotificationKind.FILTER_MATCH }?.toString(),
            reviewTitle = field("reviewTitle"),
            filterName = field("filterName"),
        )
    }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private fun encodeCursor(
        at: Instant,
        id: UUID,
    ) = Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())

    private fun decodeCursor(raw: String): Pair<Instant, UUID> =
        runCatching {
            val (at, id) = String(Base64.getUrlDecoder().decode(raw)).split('|')
            Instant.parse(at) to UUID.fromString(id)
        }.getOrElse { throw ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST) }

    private companion object {
        const val PAGE = 30
        const val PAGE_MAX = 50
        val RETENTION: Duration = Duration.ofDays(90)
    }
}
