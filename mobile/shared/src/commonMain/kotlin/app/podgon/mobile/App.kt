package app.podgon.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.UpdateGate
import app.podgon.mobile.core.network.UpdateRequiredScreen
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.designsystem.EmptyState
import app.podgon.mobile.designsystem.MainTab
import app.podgon.mobile.designsystem.PodgonBottomBar
import app.podgon.mobile.designsystem.PodgonTheme
import app.podgon.mobile.designsystem.PodgonTopBar
import app.podgon.mobile.feature.auth.ui.CodeScreen
import app.podgon.mobile.feature.auth.ui.EmailScreen
import app.podgon.mobile.feature.auth.ui.RegisterScreen
import app.podgon.mobile.feature.groups.CreateGroupScreen
import app.podgon.mobile.feature.groups.GroupScreen
import app.podgon.mobile.feature.groups.GroupsScreen
import app.podgon.mobile.feature.groups.InvitationsScreen
import app.podgon.mobile.feature.groups.JoinGroupScreen
import app.podgon.mobile.feature.groups.RolesScreen
import app.podgon.mobile.feature.main.MainNavState
import app.podgon.mobile.feature.main.MainNavigationViewModel
import app.podgon.mobile.feature.profile.ProfileScreen
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.FeedScreen
import app.podgon.mobile.feature.reviews.ReviewFormScreen
import app.podgon.mobile.feature.reviews.ReviewScreen
import app.podgon.mobile.feature.settings.SettingsScreen
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.favorites_stub_text
import app.podgon.mobile.resources.favorites_stub_title
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
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
    val updateRequired by koinInject<UpdateGate>().required.collectAsStateWithLifecycle()
    val theme = koinInject<ThemeSettings>()
    val mode by theme.mode.collectAsStateWithLifecycle()
    LaunchedEffect(session) {
        theme.load()
        session.restore()
    }
    PodgonTheme(mode) {
        when {
            updateRequired -> UpdateRequiredScreen(koinInject<ApiConfig>().updateUrl)
            state == AuthState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state == AuthState.SignedOut -> SignedOutNavigation()
            else -> SignedInNavigation()
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

@Serializable
private data object MainRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object FavoritesRoute

@Serializable
private data class PersonalRoute(
    val groupId: String?,
)

/**
 * Основная навигация: разделы нижнего меню, у каждого — свой стек экранов ([MainTab]); форма рекомендации —
 * модальный экран поверх разделов (без нижнего меню). После сохранения карточка открывается в текущем разделе.
 */
@Composable
private fun SignedInNavigation() {
    val outer = rememberNavController()
    val tabs = MainTab.entries.associateWith { rememberNavController() }
    val main = koinViewModel<MainNavigationViewModel>()
    val state by main.state.collectAsStateWithLifecycle()
    LaunchedEffect(main) {
        main.reselected.collect { tab ->
            tabs.getValue(tab).let { it.popBackStack(it.graph.startDestinationId, inclusive = false) }
        }
    }
    val openForm: (
        String?,
        String?,
    ) -> Unit = { reviewId, groupId -> outer.navigate(ReviewFormRoute(reviewId, groupId)) }
    NavHost(outer, startDestination = MainRoute) {
        composable<MainRoute> {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(bottom = BOTTOM_BAR_SPACE)) {
                    TabHost(state.tab, tabs.getValue(state.tab), state, main, openForm)
                }
                PodgonBottomBar(
                    selected = state.tab,
                    onSelect = main::select,
                    onPlus = { openForm(null, main.newReviewGroup()) },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }
        composable<ReviewFormRoute> { entry ->
            val route = entry.toRoute<ReviewFormRoute>()
            ReviewFormScreen(
                koinViewModel(parameters = { parametersOf(route.reviewId, route.presetGroupId) }),
                editing = route.reviewId != null,
                onSaved = { id ->
                    outer.popBackStack()
                    val nav = tabs.getValue(main.state.value.tab)
                    // после правки карточка уже открыта: заменяем её свежей, «назад» ведёт к списку
                    nav.navigate(ReviewRoute(id)) {
                        if (route.reviewId != null) popUpTo<ReviewRoute> { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Стек экранов одного раздела. */
@Composable
private fun TabHost(
    tab: MainTab,
    nav: NavHostController,
    state: MainNavState,
    main: MainNavigationViewModel,
    openForm: (String?, String?) -> Unit,
) {
    val header: @Composable () -> Unit = {
        PodgonTopBar(offline = false, onNotifications = {}, onProfile = { nav.navigate(ProfileRoute) })
    }
    val start: Any =
        when (tab) {
            MainTab.FEED -> GroupsRoute
            MainTab.FAVORITES -> FavoritesRoute
            MainTab.PERSONAL -> PersonalRoute(state.personalGroupId)
            MainTab.SETTINGS -> SettingsRoute
        }
    // «Личное» ждёт id системной группы из профиля
    key(tab, if (tab == MainTab.PERSONAL) state.personalGroupId else null) {
        NavHost(nav, startDestination = start) {
            composable<FavoritesRoute> {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    header()
                    EmptyState(
                        stringResource(Res.string.favorites_stub_title),
                        stringResource(Res.string.favorites_stub_text),
                    )
                }
            }
            composable<PersonalRoute> { entry ->
                val groupId = entry.toRoute<PersonalRoute>().groupId
                if (groupId == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val scope = FeedScope.Group(groupId)
                    FeedScreen(
                        koinViewModel(key = "personal", parameters = { parametersOf(scope) }),
                        scope = scope,
                        onOpen = { nav.navigate(ReviewRoute(it)) },
                        onNew = { openForm(null, null) },
                    )
                }
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    koinViewModel(),
                    version = koinInject<ApiConfig>().appVersion.toString(),
                    onGroups = { nav.navigate(GroupsRoute) },
                    onSections = {},
                    header = header,
                )
            }
            commonDestinations(nav, openForm, onFeedGroup = main::setFeedGroup)
        }
    }
}

/** Экраны, доступные из любого раздела: группы, ленты, карточка, профиль. */
private fun NavGraphBuilder.commonDestinations(
    nav: NavHostController,
    openForm: (String?, String?) -> Unit,
    onFeedGroup: (String?) -> Unit,
) {
    composable<GroupsRoute> {
        LaunchedEffect(Unit) { onFeedGroup(null) }
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
        LaunchedEffect(route.groupId) { onFeedGroup(route.groupId) }
        FeedScreen(
            koinViewModel(parameters = { parametersOf(scope) }),
            scope = scope,
            onOpen = { nav.navigate(ReviewRoute(it)) },
            onNew = { openForm(null, route.groupId) },
        )
    }
    composable<ReviewRoute> { entry ->
        val route = entry.toRoute<ReviewRoute>()
        ReviewScreen(
            koinViewModel(parameters = { parametersOf(route.reviewId) }),
            onEdit = { openForm(it, null) },
            onClosed = { nav.popBackStack() },
        )
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

/** Место под плавающее нижнее меню: последняя запись списка не прячется под ним. */
private val BOTTOM_BAR_SPACE = 96.dp

/** Открывает группу, убирая из стека экран создания/вступления: «назад» ведёт к списку групп. */
private fun androidx.navigation.NavController.openGroup(
    groupId: String,
    replacing: Any,
) {
    navigate(GroupRoute(groupId)) { popUpTo(replacing) { inclusive = true } }
}
