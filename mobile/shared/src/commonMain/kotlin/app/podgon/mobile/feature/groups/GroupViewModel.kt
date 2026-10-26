package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.GroupDeleteImpact
import app.podgon.contract.GroupSummary
import app.podgon.contract.LeaveImpact
import app.podgon.contract.MemberInfo
import app.podgon.contract.ReviewsFate
import app.podgon.contract.RoleInfo
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupState(
    val loading: Boolean = true,
    val group: GroupSummary? = null,
    val members: List<MemberInfo> = emptyList(),
    val roles: List<RoleInfo> = emptyList(),
    /** Идентификатор текущего пользователя: себя из группы удалять нельзя (для этого есть «Выйти»). */
    val myUserId: String? = null,
    val error: AppError? = null,
    /** Последствия выхода/удаления: загружаются перед показом подтверждения. */
    val leaveImpact: LeaveImpact? = null,
    val deleteImpact: GroupDeleteImpact? = null,
) {
    val isAdmin: Boolean get() = group?.myRole?.admin == true
}

/** Группа покинута или удалена — экран закрывается. */
sealed interface GroupEffect {
    data object Closed : GroupEffect
}

/** Экран группы: состав, смена ролей, удаление участников, выход, удаление группы. */
class GroupViewModel(
    private val groupId: String,
    private val api: GroupsApi,
    private val me: MeApi,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state.asStateFlow()

    private val _effects = Channel<GroupEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val group = api.get(groupId)
            val members = api.members(groupId)
            val roles = api.roles(groupId)
            val profile = me.me()
            _state.update {
                it.copy(
                    loading = false,
                    group = group.getOrNull() ?: it.group,
                    members = members.getOrNull() ?: it.members,
                    roles = roles.getOrNull() ?: it.roles,
                    myUserId = profile.getOrNull()?.id ?: it.myUserId,
                    error = group.appError() ?: members.appError() ?: roles.appError(),
                )
            }
        }
    }

    fun setRole(
        userId: String,
        roleId: String,
    ) = mutate { api.setMemberRole(groupId, userId, roleId) }

    fun removeMember(userId: String) = mutate { api.removeMember(groupId, userId) }

    /** Узнаёт, сколько публикаций пользователя в группе, и открывает подтверждение выхода. */
    fun prepareLeave() {
        viewModelScope.launch {
            val result = api.leaveImpact(groupId)
            _state.update { it.copy(leaveImpact = result.getOrNull(), error = result.appError()) }
        }
    }

    /** Узнаёт, что будет с рекомендациями при удалении, и открывает подтверждение. */
    fun prepareDelete() {
        viewModelScope.launch {
            val result = api.deleteImpact(groupId)
            _state.update { it.copy(deleteImpact = result.getOrNull(), error = result.appError()) }
        }
    }

    fun dismissImpact() = _state.update { it.copy(leaveImpact = null, deleteImpact = null) }

    fun leave(deleteMyReviews: Boolean = false) = closing { api.leave(groupId, deleteMyReviews) }

    fun deleteGroup(reviews: ReviewsFate = ReviewsFate.RETURN_TO_PERSONAL) = closing { api.delete(groupId, reviews) }

    /** Изменяет состав и перечитывает экран; ошибка (например, LAST_ADMIN) показывается, состояние не меняется. */
    private fun mutate(call: suspend () -> Result<*>) {
        viewModelScope.launch {
            val result = call()
            if (result.isSuccess) load() else _state.update { it.copy(error = result.appError()) }
        }
    }

    private fun closing(call: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val result = call()
            if (result.isSuccess) {
                _effects.send(
                    GroupEffect.Closed,
                )
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }
}
