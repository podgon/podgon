package app.podgon.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.designsystem.PodgonTheme
import app.podgon.mobile.feature.auth.ui.CodeScreen
import app.podgon.mobile.feature.auth.ui.EmailScreen
import app.podgon.mobile.feature.auth.ui.RegisterScreen
import app.podgon.mobile.feature.groups.CategoriesScreen
import app.podgon.mobile.feature.groups.CreateGroupScreen
import app.podgon.mobile.feature.groups.GroupScreen
import app.podgon.mobile.feature.groups.GroupsScreen
import app.podgon.mobile.feature.groups.InvitationsScreen
import app.podgon.mobile.feature.groups.JoinGroupScreen
import app.podgon.mobile.feature.groups.RolesScreen
import app.podgon.mobile.feature.profile.ProfileScreen
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.FeedScreen
import app.podgon.mobile.feature.reviews.ReviewFormScreen
import app.podgon.mobile.feature.reviews.ReviewScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
private data object EmailRoute

@Serializable
private data class CodeRoute(
    val email: String,
)

@Serializable
private data class RegisterRoute(
    val registrationToken: String,
)

@Serializable
private data object ProfileRoute

@Serializable
private data object GroupsRoute

@Serializable
private data object CreateGroupRoute

@Serializable
private data object JoinGroupRoute

@Serializable
private data class GroupRoute(
    val groupId: String,
)

@Serializable
private data class RolesRoute(
    val groupId: String,
)

@Serializable
private data class CategoriesRoute(
    val groupId: String,
    val canEdit: Boolean,
)

/** Лента: группа (`groupId`), мои рекомендации (`groupId == null`) или поиск по всем группам (`all`). */
@Serializable
private data class FeedRoute(
    val groupId: String?,
    val all: Boolean = false,
)

@Serializable
private data class ReviewRoute(
    val reviewId: String,
)

/** Форма рекомендации: `reviewId == null` — создание, `presetGroupId` — группа, отмеченная заранее. */
@Serializable
private data class ReviewFormRoute(
    val reviewId: String?,
    val presetGroupId: String?,
)

@Serializable
private data class InvitationsRoute(
    val groupId: String,
)

/** Корневой экран: по состоянию сессии показывает вход или основное содержимое. */
@Composable
fun App() {
    val session = koinInject<SessionManager>()
    val state by session.state.collectAsStateWithLifecycle()
    LaunchedEffect(session) { session.restore() }
    PodgonTheme {
        when (state) {
            AuthState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AuthState.SignedOut -> SignedOutNavigation()
            AuthState.SignedIn -> SignedInNavigation()
        }
    }
}

@Composable
private fun SignedOutNavigation() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = EmailRoute) {
        composable<EmailRoute> {
            EmailScreen(koinViewModel(), onCodeRequested = { email -> nav.navigate(CodeRoute(email)) })
        }
        composable<CodeRoute> { entry ->
            val route = entry.toRoute<CodeRoute>()
            CodeScreen(
                koinViewModel(parameters = { parametersOf(route.email) }),
                onRegistrationRequired = { token -> nav.navigate(RegisterRoute(token)) },
            )
        }
        composable<RegisterRoute> { entry ->
            val route = entry.toRoute<RegisterRoute>()
            RegisterScreen(koinViewModel(parameters = { parametersOf(route.registrationToken) }))
        }
    }
}

@Composable
private fun SignedInNavigation() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = GroupsRoute) {
        composable<GroupsRoute> {
            GroupsScreen(
                koinViewModel(),
                onCreate = { nav.navigate(CreateGroupRoute) },
                onJoin = { nav.navigate(JoinGroupRoute) },
                onOpen = { nav.navigate(GroupRoute(it)) },
                onProfile = { nav.navigate(ProfileRoute) },
                onMyReviews = { nav.navigate(FeedRoute(null)) },
                onSearch = { nav.navigate(FeedRoute(null, all = true)) },
            )
        }
        composable<ProfileRoute> { ProfileScreen(koinViewModel()) }
        composable<CreateGroupRoute> {
            CreateGroupScreen(koinViewModel(), onCreated = { nav.openGroup(it, replacing = CreateGroupRoute) })
        }
        composable<JoinGroupRoute> {
            JoinGroupScreen(koinViewModel(), onJoined = { nav.openGroup(it, replacing = JoinGroupRoute) })
        }
        composable<GroupRoute> { entry ->
            val route = entry.toRoute<GroupRoute>()
            GroupScreen(
                koinViewModel(parameters = { parametersOf(route.groupId) }),
                onReviews = { nav.navigate(FeedRoute(route.groupId)) },
                onCategories = { canEdit -> nav.navigate(CategoriesRoute(route.groupId, canEdit)) },
                onRoles = { nav.navigate(RolesRoute(route.groupId)) },
                onInvitations = { nav.navigate(InvitationsRoute(route.groupId)) },
                onClosed = { nav.popBackStack(GroupsRoute, inclusive = false) },
            )
        }
        composable<FeedRoute> { entry ->
            val route = entry.toRoute<FeedRoute>()
            val scope =
                when {
                    route.all -> FeedScope.AllGroups
                    route.groupId == null -> FeedScope.Mine
                    else -> FeedScope.Group(route.groupId)
                }
            FeedScreen(
                koinViewModel(parameters = { parametersOf(scope) }),
                scope = scope,
                onOpen = { nav.navigate(ReviewRoute(it)) },
                onNew = { nav.navigate(ReviewFormRoute(null, route.groupId)) },
            )
        }
        composable<ReviewRoute> { entry ->
            val route = entry.toRoute<ReviewRoute>()
            ReviewScreen(
                koinViewModel(parameters = { parametersOf(route.reviewId) }),
                onEdit = { nav.navigate(ReviewFormRoute(it, null)) },
                onClosed = { nav.popBackStack() },
            )
        }
        composable<ReviewFormRoute> { entry ->
            val route = entry.toRoute<ReviewFormRoute>()
            ReviewFormScreen(
                koinViewModel(parameters = { parametersOf(route.reviewId, route.presetGroupId) }),
                editing = route.reviewId != null,
                onSaved = { id ->
                    // «назад» из карточки ведёт к списку, а не к форме
                    nav.navigate(ReviewRoute(id)) { popUpTo(entry.destination.id) { inclusive = true } }
                },
            )
        }
        composable<CategoriesRoute> { entry ->
            val route = entry.toRoute<CategoriesRoute>()
            CategoriesScreen(koinViewModel(parameters = { parametersOf(route.groupId) }), canEdit = route.canEdit)
        }
        composable<RolesRoute> { entry ->
            val route = entry.toRoute<RolesRoute>()
            RolesScreen(koinViewModel(parameters = { parametersOf(route.groupId) }))
        }
        composable<InvitationsRoute> { entry ->
            val route = entry.toRoute<InvitationsRoute>()
            InvitationsScreen(koinViewModel(parameters = { parametersOf(route.groupId) }))
        }
    }
}

/** Открывает группу, убирая из стека экран создания/вступления: «назад» ведёт к списку групп. */
private fun androidx.navigation.NavController.openGroup(
    groupId: String,
    replacing: Any,
) {
    navigate(GroupRoute(groupId)) { popUpTo(replacing) { inclusive = true } }
}
