package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.groups.persistence.GroupRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Инвариант «в группе есть хотя бы один администратор». Вызывается внутри транзакции после изменения
 * состава или ролей, при нарушении исключение откатывает изменение. Строка группы к этому моменту
 * заблокирована (`GroupRepository.lock`), поэтому параллельные изменения не обходят проверку;
 * в БД есть страховка — отложенный constraint trigger.
 */
@Component
class AdminGuard(
    private val groups: GroupRepository,
) {
    fun ensureAdminRemains(groupId: UUID) {
        if (groups.countAdmins(groupId) == 0) throw ApiException(ErrorCode.LAST_ADMIN, HttpStatus.CONFLICT)
    }
}
