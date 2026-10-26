package app.podgon.groups.persistence

import app.podgon.common.instant
import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class RoleRow(
    val id: UUID,
    val groupId: UUID,
    val code: String,
    val title: String,
    val description: String,
    val admin: Boolean,
    val system: Boolean,
)

data class GroupRow(
    val id: UUID,
    val name: String,
    val description: String,
    val memberCount: Int,
)

/** Группа вместе с ролью конкретного участника. */
data class MyGroupRow(
    val group: GroupRow,
    val role: RoleRow,
)

data class MemberRow(
    val userId: UUID,
    val role: RoleRow,
    val joinedAt: Instant,
)

/** Группы, роли и членство. Все запросы параметризованы. */
@Repository
class GroupRepository(
    private val jdbc: JdbcClient,
) {
    private val roleColumns =
        "r.id as role_id, r.group_id as role_group_id, r.code, r.title, r.description as role_description, r.is_admin, r.is_system"

    private fun role(rs: ResultSet) =
        RoleRow(
            id = rs.getObject("role_id", UUID::class.java),
            groupId = rs.getObject("role_group_id", UUID::class.java),
            code = rs.getString("code"),
            title = rs.getString("title"),
            description = rs.getString("role_description"),
            admin = rs.getBoolean("is_admin"),
            system = rs.getBoolean("is_system"),
        )

    private fun group(rs: ResultSet) =
        GroupRow(
            id = rs.getObject("group_id", UUID::class.java),
            name = rs.getString("name"),
            description = rs.getString("group_description"),
            memberCount = rs.getInt("member_count"),
        )

    fun insertGroup(
        id: UUID,
        name: String,
        description: String,
        createdBy: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into groups (id, name, description, created_by, created_at, updated_at)
                values (:id, :name, :description, :createdBy, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("name", name)
            .param("description", description)
            .param("createdBy", createdBy)
            .param("now", now.toSql())
            .update()
    }

    fun insertRole(
        role: RoleRow,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into group_roles (id, group_id, code, title, description, is_admin, is_system, created_at)
                values (:id, :groupId, :code, :title, :description, :admin, :system, :now)
                """.trimIndent(),
            ).param("id", role.id)
            .param("groupId", role.groupId)
            .param("code", role.code)
            .param("title", role.title)
            .param("description", role.description)
            .param("admin", role.admin)
            .param("system", role.system)
            .param("now", now.toSql())
            .update()
    }

    fun insertMember(
        groupId: UUID,
        userId: UUID,
        roleId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql("insert into memberships (group_id, user_id, role_id, joined_at) values (:g, :u, :r, :now)")
            .param("g", groupId)
            .param("u", userId)
            .param("r", roleId)
            .param("now", now.toSql())
            .update()
    }

    /** Блокирует строку группы до конца транзакции: сериализует изменения состава и ролей. */
    fun lock(groupId: UUID): Boolean =
        jdbc
            .sql("select id from groups where id = :id for update")
            .param("id", groupId)
            .query(UUID::class.java)
            .list()
            .isNotEmpty()

    /** Группа с ролью пользователя; `null`, если группы нет или пользователь в ней не состоит. */
    fun findForMember(
        groupId: UUID,
        userId: UUID,
    ): MyGroupRow? =
        jdbc
            .sql(
                """
                select g.id as group_id, g.name, g.description as group_description,
                       (select count(*) from memberships c where c.group_id = g.id) as member_count, $roleColumns
                from groups g
                join memberships m on m.group_id = g.id and m.user_id = :user
                join group_roles r on r.id = m.role_id
                where g.id = :group
                """.trimIndent(),
            ).param("group", groupId)
            .param("user", userId)
            .query { rs, _ -> MyGroupRow(group(rs), role(rs)) }
            .optional()
            .orElse(null)

    fun groupIdsOf(userId: UUID): Set<UUID> =
        jdbc
            .sql("select group_id from memberships where user_id = :user")
            .param("user", userId)
            .query { rs, _ -> rs.getObject("group_id", UUID::class.java) }
            .list()
            .toSet()

    /** Группа без привязки к участнику (для предпросмотра приглашения). */
    fun groupInfo(groupId: UUID): GroupRow? =
        jdbc
            .sql(
                """
                select g.id as group_id, g.name, g.description as group_description,
                       (select count(*) from memberships c where c.group_id = g.id) as member_count
                from groups g where g.id = :id
                """.trimIndent(),
            ).param("id", groupId)
            .query { rs, _ -> group(rs) }
            .optional()
            .orElse(null)

    fun listForUser(userId: UUID): List<MyGroupRow> =
        jdbc
            .sql(
                """
                select g.id as group_id, g.name, g.description as group_description,
                       (select count(*) from memberships c where c.group_id = g.id) as member_count, $roleColumns
                from groups g
                join memberships m on m.group_id = g.id and m.user_id = :user
                join group_roles r on r.id = m.role_id
                order by lower(g.name), g.id
                """.trimIndent(),
            ).param("user", userId)
            .query { rs, _ -> MyGroupRow(group(rs), role(rs)) }
            .list()

    fun countGroupsOf(userId: UUID): Int =
        jdbc
            .sql("select count(*) from memberships where user_id = :user")
            .param("user", userId)
            .query(Int::class.java)
            .single()

    fun countMembers(groupId: UUID): Int =
        jdbc
            .sql("select count(*) from memberships where group_id = :g")
            .param("g", groupId)
            .query(Int::class.java)
            .single()

    fun update(
        groupId: UUID,
        name: String,
        description: String,
        now: Instant,
    ) {
        jdbc
            .sql("update groups set name = :name, description = :description, updated_at = :now where id = :id")
            .param("id", groupId)
            .param("name", name)
            .param("description", description)
            .param("now", now.toSql())
            .update()
    }

    fun delete(groupId: UUID) {
        jdbc.sql("delete from groups where id = :id").param("id", groupId).update()
    }

    /** Группы (id, название), где пользователь — единственный админ при наличии других участников. */
    fun soleAdminGroups(userId: UUID): List<Pair<UUID, String>> =
        jdbc
            .sql(
                """
                select g.id, g.name
                from memberships m
                join group_roles r on r.id = m.role_id and r.is_admin
                join groups g on g.id = m.group_id
                where m.user_id = :u
                  and (select count(*) from memberships a join group_roles ar on ar.id = a.role_id
                       where a.group_id = g.id and ar.is_admin) = 1
                  and (select count(*) from memberships a where a.group_id = g.id) > 1
                order by g.name
                """.trimIndent(),
            ).param("u", userId)
            .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("name") }
            .list()

    /** Группы, где пользователь — единственный участник. */
    fun groupsWhereOnlyMember(userId: UUID): List<UUID> =
        jdbc
            .sql(
                """
                select m.group_id from memberships m
                where m.user_id = :u and (select count(*) from memberships a where a.group_id = m.group_id) = 1
                """.trimIndent(),
            ).param("u", userId)
            .query { rs, _ -> rs.getObject("group_id", UUID::class.java) }
            .list()

    fun deleteMembershipsOf(userId: UUID) {
        jdbc.sql("delete from memberships where user_id = :u").param("u", userId).update()
    }

    fun members(groupId: UUID): List<MemberRow> =
        jdbc
            .sql(
                """
                select m.user_id, m.joined_at, $roleColumns
                from memberships m
                join group_roles r on r.id = m.role_id
                where m.group_id = :g
                order by r.is_admin desc, m.joined_at, m.user_id
                """.trimIndent(),
            ).param("g", groupId)
            .query { rs, _ -> MemberRow(rs.getObject("user_id", UUID::class.java), role(rs), rs.instant("joined_at")) }
            .list()

    fun member(
        groupId: UUID,
        userId: UUID,
    ): MemberRow? =
        jdbc
            .sql(
                """
                select m.user_id, m.joined_at, $roleColumns
                from memberships m
                join group_roles r on r.id = m.role_id
                where m.group_id = :g and m.user_id = :u
                """.trimIndent(),
            ).param("g", groupId)
            .param("u", userId)
            .query { rs, _ -> MemberRow(rs.getObject("user_id", UUID::class.java), role(rs), rs.instant("joined_at")) }
            .optional()
            .orElse(null)

    fun deleteMember(
        groupId: UUID,
        userId: UUID,
    ): Boolean =
        jdbc
            .sql("delete from memberships where group_id = :g and user_id = :u")
            .param("g", groupId)
            .param("u", userId)
            .update() > 0

    fun setMemberRole(
        groupId: UUID,
        userId: UUID,
        roleId: UUID,
    ) {
        jdbc
            .sql("update memberships set role_id = :r where group_id = :g and user_id = :u")
            .param("g", groupId)
            .param("u", userId)
            .param("r", roleId)
            .update()
    }

    fun countAdmins(groupId: UUID): Int =
        jdbc
            .sql(
                """
                select count(*) from memberships m join group_roles r on r.id = m.role_id
                where m.group_id = :g and r.is_admin
                """.trimIndent(),
            ).param("g", groupId)
            .query(Int::class.java)
            .single()

    fun roles(groupId: UUID): List<RoleRow> =
        jdbc
            .sql(
                "select $roleColumns from group_roles r where r.group_id = :g order by r.is_system desc, r.is_admin desc, r.created_at, r.id",
            ).param("g", groupId)
            .query { rs, _ -> role(rs) }
            .list()

    fun role(
        groupId: UUID,
        roleId: UUID,
    ): RoleRow? =
        jdbc
            .sql("select $roleColumns from group_roles r where r.group_id = :g and r.id = :r")
            .param("g", groupId)
            .param("r", roleId)
            .query { rs, _ -> role(rs) }
            .optional()
            .orElse(null)

    fun roleByCode(
        groupId: UUID,
        code: String,
    ): RoleRow =
        jdbc
            .sql("select $roleColumns from group_roles r where r.group_id = :g and r.code = :c")
            .param("g", groupId)
            .param("c", code)
            .query { rs, _ -> role(rs) }
            .single()

    fun updateRole(
        roleId: UUID,
        title: String,
        description: String,
        admin: Boolean,
    ) {
        jdbc
            .sql("update group_roles set title = :title, description = :description, is_admin = :admin where id = :id")
            .param("id", roleId)
            .param("title", title)
            .param("description", description)
            .param("admin", admin)
            .update()
    }

    /** Участники с ролью [fromRoleId] получают [toRoleId]. */
    fun reassignRole(
        groupId: UUID,
        fromRoleId: UUID,
        toRoleId: UUID,
    ) {
        jdbc
            .sql("update memberships set role_id = :to where group_id = :g and role_id = :from")
            .param("g", groupId)
            .param("from", fromRoleId)
            .param("to", toRoleId)
            .update()
    }

    fun deleteRole(roleId: UUID) {
        jdbc.sql("delete from group_roles where id = :id").param("id", roleId).update()
    }
}
