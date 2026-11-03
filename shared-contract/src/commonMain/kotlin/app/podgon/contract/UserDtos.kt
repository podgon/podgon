package app.podgon.contract

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val locale: String,
    /** Системная группа «Личное»: её лента — личные рекомендации. */
    val personalGroupId: String,
    /** Системная группа «Избранное». */
    val favoritesGroupId: String,
    /** Свой аватар; `null` — аватар по умолчанию. */
    val avatarId: String? = null,
)

/** Частичное обновление профиля: `null` — поле не менять; пустой [bio] очищает биографию. */
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val locale: String? = null,
)

@Serializable
data class SessionInfo(
    val id: String,
    val deviceInfo: String?,
    /** Момент создания, ISO-8601 в UTC. */
    val createdAt: String,
    val lastUsedAt: String,
    /** Сессия, из которой сделан текущий запрос. */
    val current: Boolean,
)
