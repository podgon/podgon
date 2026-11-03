package app.podgon.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.podgon.mobile.core.auth.AuthState
import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.media.LocalMediaUrls
import app.podgon.mobile.core.media.MediaUrls
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.core.network.UpdateGate
import app.podgon.mobile.core.network.UpdateRequiredScreen
import app.podgon.mobile.core.settings.LanguageSettings
import app.podgon.mobile.core.settings.LocalizedContent
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.designsystem.MainTab
import app.podgon.mobile.designsystem.PodgonBottomBar
import app.podgon.mobile.designsystem.PodgonTheme
import app.podgon.mobile.di.AUTHORIZED_CLIENT
import app.podgon.mobile.feature.auth.ui.CodeScreen
import app.podgon.mobile.feature.auth.ui.EmailScreen
import app.podgon.mobile.feature.auth.ui.RegisterScreen
import app.podgon.mobile.feature.groups.CreateGroupScreen
import app.podgon.mobile.feature.groups.GroupScreen
import app.podgon.mobile.feature.groups.GroupsScreen
import app.podgon.mobile.feature.groups.JoinGroupScreen
import app.podgon.mobile.feature.home.HomeActions
import app.podgon.mobile.feature.home.HomeArea
import app.podgon.mobile.feature.home.HomeScreen
import app.podgon.mobile.feature.home.SectionsScreen
import app.podgon.mobile.feature.main.MainNavState
import app.podgon.mobile.feature.main.MainNavigationViewModel
import app.podgon.mobile.feature.notifications.AppTopBar
import app.podgon.mobile.feature.notifications.NotificationSettingsScreen
import app.podgon.mobile.feature.notifications.NotificationsScreen
import app.podgon.mobile.feature.notifications.PollUnread
import app.podgon.mobile.feature.profile.ProfileScreen
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.MyReviewsScreen
import app.podgon.mobile.feature.reviews.ReviewFormScreen
import app.podgon.mobile.feature.reviews.ReviewScreen
import app.podgon.mobile.feature.settings.SettingsScreen
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
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

/** Вступление: [token] — код, введённый в окне «Присоединиться» (сразу проверяется). */
@Serializable
private data class JoinGroupRoute(
    val token: String? = null,
    /** Входящее адресное приглашение (из уведомления или списка в «Группах»). */
    val invitationId: String? = null,
)

@Serializable
private data object NotificationsRoute

@Serializable
private data object NotificationSettingsRoute

/** Страница группы; [invite] — сразу открыть лист приглашения (меню вкладки ленты). */
@Serializable
private data class GroupRoute(
    val groupId: String,
    val invite: Boolean = false,
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

/** Корневой экран: по состоянию сессии показывает вход или основное содержимое. */
@Composable
fun App() {
    val session = koinInject<SessionManager>()
    val state by session.state.collectAsStateWithLifecycle()
    val updateRequired by koinInject<UpdateGate>().required.collectAsStateWithLifecycle()
    val theme = koinInject<ThemeSettings>()
    val mode by theme.mode.collectAsStateWithLifecycle()
    val language by koinInject<LanguageSettings>().language.collectAsStateWithLifecycle()
    LaunchedEffect(session) {
        theme.load()
        session.restore()
    }
    val client = koinInject<HttpClient>(AUTHORIZED_CLIENT)
    setSingletonImageLoaderFactory { context ->
        // картинки — через клиент с авторизацией: фото и аватары доступны только вошедшим
        ImageLoader
            .Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { client })) }
            .crossfade(true)
            .build()
    }
    LocalizedContent(language.code) {
        CompositionLocalProvider(LocalMediaUrls provides koinInject<MediaUrls>()) {
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
                onChangeEmail = { nav.popBackStack() },
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

/** Главный экран раздела: лента (`groupId == null`), «Личное» или «Избранное» (id системной группы). */
@Serializable
private data class HomeRoute(
    val groupId: String?,
    val favorites: Boolean,
)

@Serializable
private data object SectionsRoute

@Serializable
private data object MyReviewsRoute

/** Лента, отфильтрованная по тегу (нажатие на тег в карточке). */
@Serializable
private data class TagRoute(
    val tag: String,
)

/**
 * Основная навигация: разделы нижнего меню, у каждого — свой стек экранов ([MainTab]); форма рекомендации —
 * модальный экран поверх разделов (без нижнего меню). После сохранения карточка открывается в текущем разделе.
 */
@Composable
private fun SignedInNavigation() {
    val outer = rememberNavController()
    val tabs = MainTab.entries.associateWith { rememberNavController() }
    val mainOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val main = koinViewModel<MainNavigationViewModel>(viewModelStoreOwner = mainOwner)
    val state by main.state.collectAsStateWithLifecycle()
    PollUnread()
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
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalMainOwner provides mainOwner) {
                        TabHost(state.tab, tabs.getValue(state.tab), state, main, openForm)
                    }
                }
                if (!state.barHidden) {
                    PodgonBottomBar(
                        selected = state.tab,
                        onSelect = main::select,
                        onPlus = { main.plus { group -> openForm(null, group) } },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                }
            }
        }
        composable<ReviewFormRoute> { entry ->
            val route = entry.toRoute<ReviewFormRoute>()
            ReviewFormScreen(
                koinViewModel(parameters = { parametersOf(route.reviewId, route.presetGroupId) }),
                onClose = { outer.popBackStack() },
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
        AppTopBar(
            offline = false,
            onNotifications = { nav.navigate(NotificationsRoute) },
            onProfile = { nav.navigate(ProfileRoute) },
        )
    }
    val start: Any =
        when (tab) {
            MainTab.FEED -> HomeRoute(null, favorites = false)
            MainTab.FAVORITES -> HomeRoute(state.favoritesGroupId, favorites = true)
            MainTab.PERSONAL -> HomeRoute(state.personalGroupId, favorites = false)
            MainTab.SETTINGS -> SettingsRoute
        }
    // «Личное» и «Избранное» ждут id системных групп из профиля
    val systemId = (start as? HomeRoute)?.groupId
    key(tab, systemId) {
        // отступ под строкой состояния — для всех экранов раздела (edge-to-edge)
        NavHost(nav, startDestination = start, modifier = Modifier.statusBarsPadding()) {
            composable<HomeRoute> { entry ->
                val route = entry.toRoute<HomeRoute>()
                val area =
                    when {
                        tab == MainTab.FEED -> HomeArea.Feed
                        route.groupId != null -> HomeArea.System(route.groupId, route.favorites)
                        else -> null
                    }
                if (area == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    HomeScreen(
                        koinViewModel(key = "home:$tab", parameters = { parametersOf(area) }),
                        area = area,
                        actions =
                            HomeActions(
                                openReview = { nav.navigate(ReviewRoute(it)) },
                                openProfile = { nav.navigate(ProfileRoute) },
                                openNotifications = { nav.navigate(NotificationsRoute) },
                                openGroup = { nav.navigate(GroupRoute(it)) },
                                invite = { nav.navigate(GroupRoute(it, invite = true)) },
                                openSections = { nav.navigate(SectionsRoute) },
                                newReview = {
                                    openForm(
                                        null,
                                        if (tab ==
                                            MainTab.FEED
                                        ) {
                                            main.newReviewGroup()
                                        } else {
                                            null
                                        },
                                    )
                                },
                                joinGroup = { nav.navigate(JoinGroupRoute()) },
                                createGroup = { nav.navigate(GroupsRoute) },
                                onTabChanged = { if (tab == MainTab.FEED) main.setFeedGroup(it) },
                            ),
                    )
                }
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    koinViewModel(),
                    version = koinInject<ApiConfig>().appVersion.toString(),
                    onGroups = { nav.navigate(GroupsRoute) },
                    onSections = { nav.navigate(SectionsRoute) },
                    onNotifications = { nav.navigate(NotificationSettingsRoute) },
                    header = header,
                )
            }
            composable<SectionsRoute> { SectionsScreen(koinViewModel(), onBack = { nav.popBackStack() }) }
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
        val main = koinViewModel<MainNavigationViewModel>(viewModelStoreOwner = LocalMainOwner.current)
        DisposableEffect(Unit) {
            main.setGroupsScreen(true)
            onDispose { main.setGroupsScreen(false) }
        }
        GroupsScreen(
            koinViewModel(),
            koinViewModel(),
            plusPressed = main.groupsPlusPressed,
            onOpen = { nav.navigate(GroupRoute(it)) },
            onJoin = { nav.navigate(JoinGroupRoute(it)) },
            onBack = { nav.popBackStack() },
            onIncoming = { nav.navigate(JoinGroupRoute(invitationId = it)) },
        )
    }
    composable<ProfileRoute> {
        ProfileScreen(koinViewModel(), onMyReviews = { nav.navigate(MyReviewsRoute) }, onBack = { nav.popBackStack() })
    }
    composable<MyReviewsRoute> {
        MyReviewsScreen(
            koinViewModel(parameters = {
                parametersOf(FeedScope.Mine)
            }),
            onOpen = { nav.navigate(ReviewRoute(it)) },
            onBack = { nav.popBackStack() },
        )
    }
    composable<CreateGroupRoute> {
        CreateGroupScreen(koinViewModel(), onCreated = { nav.openGroup(it, replacing = CreateGroupRoute) })
    }
    composable<JoinGroupRoute> { entry ->
        val main = koinViewModel<MainNavigationViewModel>(viewModelStoreOwner = LocalMainOwner.current)
        DisposableEffect(Unit) {
            main.setBarHidden(true)
            onDispose { main.setBarHidden(false) }
        }
        val route = entry.toRoute<JoinGroupRoute>()
        JoinGroupScreen(koinViewModel(), token = route.token, invitationId = route.invitationId, onJoined = {
            nav.openGroup(it, replacing = entry.toRoute<JoinGroupRoute>())
        })
    }
    composable<NotificationsRoute> {
        NotificationsScreen(
            koinViewModel(),
            onBack = { nav.popBackStack() },
            onSettings = { nav.navigate(NotificationSettingsRoute) },
            onInvitation = { nav.navigate(JoinGroupRoute(invitationId = it)) },
            onReview = { nav.navigate(ReviewRoute(it)) },
        )
    }
    composable<NotificationSettingsRoute> {
        NotificationSettingsScreen(koinViewModel(), onBack = { nav.popBackStack() })
    }
    composable<GroupRoute> { entry ->
        val route = entry.toRoute<GroupRoute>()
        GroupScreen(
            koinViewModel(parameters = { parametersOf(route.groupId) }),
            groupId = route.groupId,
            openInvite = route.invite,
            onBack = { nav.popBackStack() },
            // после выхода или удаления группы — к началу раздела (лента или настройки)
            onClosed = { nav.popBackStack(nav.graph.startDestinationId, inclusive = false) },
        )
    }
    composable<ReviewRoute> { entry ->
        val route = entry.toRoute<ReviewRoute>()
        ReviewScreen(
            koinViewModel(parameters = { parametersOf(route.reviewId) }),
            onEdit = { openForm(it, null) },
            onTag = { nav.navigate(TagRoute(it)) },
            onBack = { nav.popBackStack() },
        )
    }
    composable<TagRoute> { entry ->
        val area = HomeArea.Tagged(entry.toRoute<TagRoute>().tag)
        HomeScreen(
            koinViewModel(key = "tag:${area.tag}", parameters = { parametersOf(area) }),
            area = area,
            actions =
                HomeActions(
                    openReview = { nav.navigate(ReviewRoute(it)) },
                    openProfile = { nav.navigate(ProfileRoute) },
                    openNotifications = { nav.navigate(NotificationsRoute) },
                    openGroup = { nav.navigate(GroupRoute(it)) },
                    invite = { nav.navigate(GroupRoute(it, invite = true)) },
                    openSections = {},
                    newReview = { openForm(null, null) },
                    joinGroup = { nav.navigate(JoinGroupRoute()) },
                    createGroup = { nav.navigate(GroupsRoute) },
                    onTabChanged = {},
                ),
        )
    }
}

/** Владелец ViewModel основной навигации: экраны разделов получают тот же [MainNavigationViewModel]. */
private val LocalMainOwner = staticCompositionLocalOf<ViewModelStoreOwner> { error("нет владельца навигации") }

/** Открывает группу, убирая из стека экран создания/вступления: «назад» ведёт к списку групп. */
private fun androidx.navigation.NavController.openGroup(
    groupId: String,
    replacing: Any,
) {
    navigate(GroupRoute(groupId)) { popUpTo(replacing) { inclusive = true } }
}
