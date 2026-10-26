package app.podgon.identity.domain

import app.podgon.identity.UserDirectory
import app.podgon.identity.UserSummary
import app.podgon.identity.persistence.UserRepository
import app.podgon.identity.persistence.UserRow
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserDirectoryImpl(
    private val users: UserRepository,
) : UserDirectory {
    override fun find(id: UUID): UserSummary? = users.findById(id)?.toSummary()

    override fun findAll(ids: Collection<UUID>): Map<UUID, UserSummary> =
        users.findByIds(ids).associate { it.id to it.toSummary() }

    override fun findByEmail(email: String): UserSummary? = users.findLoginableByEmail(email)?.toSummary()

    private fun UserRow.toSummary() =
        UserSummary(
            id,
            email,
            username,
            displayName,
            locale,
            blocked =
                status == "BLOCKED",
        )
}
