package app.podgon.groups.domain

import app.podgon.contract.GroupSummary
import app.podgon.contract.RoleInfo
import app.podgon.groups.GroupReviewCounts
import app.podgon.groups.persistence.MyGroupRow
import app.podgon.groups.persistence.RoleRow
import org.springframework.stereotype.Component

fun RoleRow.toDto() = RoleInfo(id.toString(), code, title, description, admin, system)

/** Группы глазами пользователя: с ролью, числом участников и опубликованных рекомендаций. */
@Component
class GroupSummaries(
    private val counts: GroupReviewCounts,
) {
    fun of(row: MyGroupRow): GroupSummary = of(listOf(row)).single()

    fun of(rows: List<MyGroupRow>): List<GroupSummary> {
        val reviewCounts = counts.count(rows.map { it.group.id })
        return rows.map {
            GroupSummary(
                id = it.group.id.toString(),
                name = it.group.name,
                description = it.group.description,
                memberCount = it.group.memberCount,
                myRole = it.role.toDto(),
                reviewCount = reviewCounts[it.group.id] ?: 0,
            )
        }
    }
}
