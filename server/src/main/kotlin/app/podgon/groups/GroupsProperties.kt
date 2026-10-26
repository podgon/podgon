package app.podgon.groups

import org.springframework.boot.context.properties.ConfigurationProperties

/** Ограничения модуля групп (защита от злоупотреблений); значения по умолчанию подходят для старта. */
@ConfigurationProperties("podgon.groups")
data class GroupsProperties(
    /** В скольких группах может состоять один пользователь. */
    val maxGroupsPerUser: Int = 50,
    val maxMembersPerGroup: Int = 500,
    val maxRolesPerGroup: Int = 50,
    /** Действующих (не отозванных, не истёкших) приглашений в одной группе. */
    val maxActiveInvitationsPerGroup: Int = 100,
    /** Писем-приглашений, которые один пользователь может отправить за час. */
    val maxEmailInvitationsPerUserPerHour: Int = 20,
)
