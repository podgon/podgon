package app.podgon.mobile.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.podgon.mobile.core.media.MyProfile
import app.podgon.mobile.core.media.avatarUrl
import app.podgon.mobile.core.network.NotificationsApi
import app.podgon.mobile.designsystem.PodgonTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/**
 * Число непрочитанных уведомлений для колокольчика. Push нет (этап 8.3.5): пока приложение на экране,
 * число обновляется раз в [POLL_MS] и при возврате в приложение.
 */
class UnreadCounter(
    private val api: NotificationsApi,
) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    suspend fun refresh() {
        api.unread().getOrNull()?.let { _count.value = it.count }
    }

    fun clear() {
        _count.value = 0
    }
}

/** Опрос числа непрочитанных, пока приложение на экране (вызывается один раз в основной навигации). */
@Composable
fun PollUnread() {
    val counter = koinInject<UnreadCounter>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(counter, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                counter.refresh()
                delay(POLL_MS)
            }
        }
    }
}

/** Верхняя строка разделов со своим аватаром и числом непрочитанных у колокольчика. */
@Composable
fun AppTopBar(
    offline: Boolean,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
) {
    val me by koinInject<MyProfile>().profile.collectAsStateWithLifecycle()
    val unread by koinInject<UnreadCounter>().count.collectAsStateWithLifecycle()
    PodgonTopBar(
        offline = offline,
        onNotifications = onNotifications,
        onProfile = onProfile,
        avatarUrl = me?.let { avatarUrl(it.id, it.avatarId) },
        unread = unread,
    )
}

private const val POLL_MS = 60_000L
