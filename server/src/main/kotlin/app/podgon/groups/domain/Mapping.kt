package app.podgon.groups.domain

import app.podgon.contract.GroupSummary
import app.podgon.contract.RoleInfo
import app.podgon.groups.persistence.MyGroupRow
import app.podgon.groups.persistence.RoleRow

fun RoleRow.toDto() = RoleInfo(id.toString(), code, title, description, admin, system)

fun MyGroupRow.toDto() =
    GroupSummary(group.id.toString(), group.name, group.description, group.memberCount, role.toDto())
