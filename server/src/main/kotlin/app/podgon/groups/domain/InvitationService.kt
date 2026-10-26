package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.common.RateLimitedException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateInvitationRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.GroupSummary
import app.podgon.contract.InvitationCreated
import app.podgon.contract.InvitationInfo
import app.podgon.contract.InvitationPreview
import app.podgon.contract.Validation
import app.podgon.groups.GroupsProperties
import app.podgon.groups.persistence.GroupRepository
import app.podgon.groups.persistence.InvitationRepository
import app.podgon.groups.persistence.InvitationRow
import app.podgon.identity.UserDirectory
import app.podgon.identity.UserSummary
import app.podgon.mail.MailQueue
import app.podgon.mail.MailTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class InvitationService(
    private val groups: GroupRepository,
    private val invitations: InvitationRepository,
    private val policy: GroupPolicy,
    private val users: UserDirectory,
    private val mail: MailQueue,
    private val props: GroupsProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val random = SecureRandom()

    /**
     * Создаёт приглашение. Токен (128 бит) возвращается один раз, в БД хранится его SHA-256.
     * Для адресного приглашения письмо ставится в очередь в той же транзакции.
     */
    fun create(
        adminId: UUID,
        groupId: UUID,
        request: CreateInvitationRequest,
    ): InvitationCreated {
        val email = request.email?.let { Validation.normalizeEmail(it) }
        val errors =
            listOfNotNull(
                email?.let { e -> Validation.email(e)?.let { FieldError("email", it) } },
                Validation.maxUses(request.maxUses)?.let { FieldError("maxUses", it) },
                Validation.ttlHours(request.ttlHours)?.let { FieldError("ttlHours", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)

        return tx.execute {
            if (!groups.lock(groupId)) throw notFound()
            val mine = policy.admin(adminId, groupId)
            val now = clock.instant()
            if (invitations.countActive(groupId, now) >= props.maxActiveInvitationsPerGroup) {
                throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
            }
            if (email != null &&
                invitations.countEmailInvitationsSince(adminId, now.minus(Duration.ofHours(1))) >=
                props.maxEmailInvitationsPerUserPerHour
            ) {
                throw RateLimitedException(Duration.ofHours(1).seconds)
            }

            val token = newToken()
            val id = UUID.randomUUID()
            val expiresAt = now.plus(Duration.ofHours(request.ttlHours.toLong()))
            invitations.insert(id, groupId, adminId, email, hash(token), expiresAt, request.maxUses, now)

            if (email != null) {
                val inviter = users.find(adminId)
                // письмо на языке получателя, если он уже зарегистрирован, иначе на языке пригласившего
                val locale = users.findByEmail(email)?.locale ?: inviter?.locale ?: "ru"
                mail.enqueue(
                    to = email,
                    template = MailTemplate.GROUP_INVITATION,
                    locale = locale,
                    params =
                        mapOf(
                            "groupName" to mine.group.name,
                            "inviter" to (inviter?.displayName?.ifBlank { inviter.username } ?: ""),
                            "token" to token,
                            "ttlHours" to request.ttlHours.toString(),
                        ),
                )
            }
            InvitationCreated(id.toString(), token, email, expiresAt.toString(), request.maxUses)
        }!!
    }

    fun list(
        adminId: UUID,
        groupId: UUID,
    ): List<InvitationInfo> {
        policy.admin(adminId, groupId)
        val rows = invitations.list(groupId)
        val authors = users.findAll(rows.map { it.createdBy }.toSet())
        val now = clock.instant()
        return rows.map {
            InvitationInfo(
                id = it.id.toString(),
                email = it.email,
                createdBy = authors[it.createdBy]?.username ?: "deleted",
                createdAt = it.createdAt.toString(),
                expiresAt = it.expiresAt.toString(),
                maxUses = it.maxUses,
                usedCount = it.usedCount,
                revoked = it.revokedAt != null,
                active = it.isActive(now),
            )
        }
    }

    /** Идемпотентно: повторный отзыв ничего не меняет. */
    fun revoke(
        adminId: UUID,
        groupId: UUID,
        invitationId: UUID,
    ) {
        policy.admin(adminId, groupId)
        val invitation = invitations.find(groupId, invitationId) ?: throw notFound()
        invitations.revoke(invitation.id, clock.instant())
    }

    /** Показывает приглашённому, куда его зовут. Любая причина отказа даёт один и тот же ответ. */
    fun preview(
        userId: UUID,
        token: String,
    ): InvitationPreview {
        val user = users.find(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        val invitation = usable(invitations.findByHash(hash(token)), user)
        val group = groupInfo(invitation)
        val inviter = users.find(invitation.createdBy)
        return InvitationPreview(
            groupName = group.name,
            groupDescription = group.description,
            memberCount = group.memberCount,
            invitedBy = inviter?.displayName?.ifBlank { inviter.username } ?: "",
        )
    }

    /** Вступление в группу: одна транзакция — проверки, членство с ролью MEMBER, счётчик использований. */
    fun accept(
        userId: UUID,
        token: String,
    ): GroupSummary {
        val user = users.find(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        if (user.blocked) throw ApiException(ErrorCode.USER_BLOCKED, HttpStatus.FORBIDDEN)
        val groupId =
            tx.execute {
                val invitation = addressedTo(invitations.findByHashForUpdate(hash(token)), user)
                if (!groups.lock(invitation.groupId)) throw invalidInvitation()
                // уже состоящему в группе важнее сказать об этом, даже если приглашение исчерпано его же вступлением
                if (groups.member(invitation.groupId, userId) != null) {
                    throw ApiException(ErrorCode.ALREADY_MEMBER, HttpStatus.CONFLICT)
                }
                if (!invitation.isActive(clock.instant())) throw invalidInvitation()
                if (groups.countGroupsOf(userId) >= props.maxGroupsPerUser ||
                    groups.countMembers(invitation.groupId) >= props.maxMembersPerGroup
                ) {
                    throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
                }
                groups.insertMember(
                    invitation.groupId,
                    userId,
                    groups.roleByCode(invitation.groupId, "MEMBER").id,
                    clock.instant(),
                )
                invitations.incrementUsed(invitation.id)
                invitation.groupId
            }!!
        return policy.member(userId, groupId).toDto()
    }

    /** Приглашение существует и (если адресное) предназначено этому пользователю. */
    private fun addressedTo(
        invitation: InvitationRow?,
        user: UserSummary,
    ): InvitationRow {
        if (invitation == null) throw invalidInvitation()
        if (invitation.email != null &&
            !invitation.email.equals(user.email, ignoreCase = true)
        ) {
            throw invalidInvitation()
        }
        return invitation
    }

    /** Приглашение ещё можно использовать: не отозвано, не истекло, не исчерпано. */
    private fun usable(
        invitation: InvitationRow?,
        user: UserSummary,
    ): InvitationRow {
        val addressed = addressedTo(invitation, user)
        if (!addressed.isActive(clock.instant())) throw invalidInvitation()
        return addressed
    }

    private fun groupInfo(invitation: InvitationRow) = groups.groupInfo(invitation.groupId) ?: throw invalidInvitation()

    private fun invalidInvitation() = ApiException(ErrorCode.INVALID_INVITATION, HttpStatus.BAD_REQUEST)

    /** Код приглашения читается и вводится человеком: 120 бит, алфавит Crockford без похожих символов, группы по 4. */
    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val chars = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl BYTE_BITS) or (byte.toInt() and BYTE_MASK)
            bits += BYTE_BITS
            while (bits >= SYMBOL_BITS) {
                bits -= SYMBOL_BITS
                chars.append(ALPHABET[(buffer shr bits) and SYMBOL_MASK])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return chars.chunked(GROUP).joinToString("-")
    }

    /**
     * Приводит введённый код к каноническому виду: без дефисов и пробелов, в верхнем регистре, похожие
     * символы заменены по правилам Crockford (O→0, I и L→1). Не код — сразу отказ без обращения к БД.
     */
    private fun canonical(token: String): String {
        if (token.length > MAX_TOKEN_LENGTH) throw invalidInvitation()
        val normalized =
            token
                .uppercase()
                .filter { it != '-' && !it.isWhitespace() }
                .map {
                    when (it) {
                        'O' -> '0'
                        'I', 'L' -> '1'
                        else -> it
                    }
                }.joinToString("")
        if (normalized.length != TOKEN_CHARS || normalized.any { it !in ALPHABET }) throw invalidInvitation()
        return normalized
    }

    private fun hash(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(canonical(token).toByteArray(Charsets.UTF_8))

    private companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val TOKEN_BYTES = 15
        const val TOKEN_CHARS = 24
        const val GROUP = 4
        const val BYTE_BITS = 8
        const val BYTE_MASK = 0xFF
        const val SYMBOL_BITS = 5
        const val SYMBOL_MASK = 0x1F
        const val MAX_TOKEN_LENGTH = 200
    }
}
