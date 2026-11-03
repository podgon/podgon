package app.podgon.groups

import app.podgon.contract.ShareRule
import java.util.UUID

// События модуля групп. Публикуются синхронно внутри транзакции, поэтому обработчики других модулей
// (рекомендации) выполняются атомарно с изменением группы, а исключение откатывает всё.

/**
 * Участник вышел из группы или его удалил администратор (при удалении аккаунта события нет: связи снимают
 * обработчики `UserDeleting`).
 */
data class MemberLeft(
    val groupId: UUID,
    val userId: UUID,
    /** Удалить публикации этого автора в группе. */
    val deleteContributions: Boolean,
)

/** Группа сейчас будет удалена (событие — до удаления строк). */
data class GroupDeleting(
    val groupId: UUID,
    /** Удалить рекомендации, опубликованные только в этой группе; иначе они возвращаются авторам в «Личное». */
    val deleteExclusiveReviews: Boolean,
)

/** Пользователь вступил в группу; [share] — правило переноса его рекомендаций. */
data class MemberJoined(
    val groupId: UUID,
    val userId: UUID,
    val share: ShareRule?,
)

/** Порт для предпросмотра шаринга: в какую группу ведёт приглашение (с теми же проверками, что и при вступлении). */
interface InvitationGroups {
    /** @throws app.podgon.common.ApiException `INVALID_INVITATION`, если приглашение недействительно */
    fun resolve(
        userId: UUID,
        token: String?,
        invitationId: String?,
    ): UUID
}
