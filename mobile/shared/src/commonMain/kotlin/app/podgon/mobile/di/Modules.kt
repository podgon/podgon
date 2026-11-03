package app.podgon.mobile.di

import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.cache.CacheDatabase
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.media.MediaUrls
import app.podgon.mobile.core.media.MyProfile
import app.podgon.mobile.core.network.ApiEndpoints
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.CategoriesApi
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.NotificationsApi
import app.podgon.mobile.core.network.ReviewsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.core.network.UpdateGate
import app.podgon.mobile.core.network.createAuthorizedClient
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.core.settings.LanguageSettings
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.feature.auth.data.AuthRepository
import app.podgon.mobile.feature.auth.data.CaptchaProvider
import app.podgon.mobile.feature.auth.ui.CodeViewModel
import app.podgon.mobile.feature.auth.ui.EmailViewModel
import app.podgon.mobile.feature.auth.ui.RegisterViewModel
import app.podgon.mobile.feature.groups.CreateGroupViewModel
import app.podgon.mobile.feature.groups.GroupViewModel
import app.podgon.mobile.feature.groups.GroupsViewModel
import app.podgon.mobile.feature.groups.InvitationsViewModel
import app.podgon.mobile.feature.groups.JoinGroupViewModel
import app.podgon.mobile.feature.groups.RolesViewModel
import app.podgon.mobile.feature.home.FiltersViewModel
import app.podgon.mobile.feature.home.HomeArea
import app.podgon.mobile.feature.home.HomeViewModel
import app.podgon.mobile.feature.home.SectionsViewModel
import app.podgon.mobile.feature.main.MainNavigationViewModel
import app.podgon.mobile.feature.notifications.NotificationSettingsViewModel
import app.podgon.mobile.feature.notifications.NotificationsViewModel
import app.podgon.mobile.feature.notifications.UnreadCounter
import app.podgon.mobile.feature.profile.ProfileViewModel
import app.podgon.mobile.feature.reviews.CategoryRepository
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.FeedViewModel
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.feature.reviews.ReviewFormViewModel
import app.podgon.mobile.feature.reviews.ReviewViewModel
import app.podgon.mobile.feature.reviews.ReviewsRepository
import app.podgon.mobile.feature.settings.SettingsViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val PUBLIC_CLIENT = named("public")
internal val AUTHORIZED_CLIENT = named("authorized")

/**
 * Общие зависимости. Платформа дополнительно предоставляет: `ApiConfig`, `HttpClientEngine`,
 * `TokenStorage`, `DeviceInfoProvider` (см. platformModule в androidMain).
 */
val appModule =
    module {
        single { ResponseCache(get<CacheDatabase>().responses()) }
        single { ReviewsRepository(get(), get()) }
        single { CategoryRepository(get(), get()) }
        single { ThemeSettings(get()) }
        single { LanguageSettings(get()) }
        single { SessionManager(get()) { get<ResponseCache>().clear() } }
        single { ApiEndpoints(get()) }
        single { MediaUrls(get()) }
        single { MyProfile() }
        single { UpdateGate() }
        single<HttpClient>(PUBLIC_CLIENT) {
            val language = get<LanguageSettings>()
            createPublicClient(get(), get(), get(), get()) { language.effective }
        }
        single<HttpClient>(AUTHORIZED_CLIENT) {
            val language = get<LanguageSettings>()
            createAuthorizedClient(get(), get(), get(), get(PUBLIC_CLIENT), get(), get()) { language.effective }
        }
        single { AuthApi(get(PUBLIC_CLIENT)) }
        single { MeApi(get(AUTHORIZED_CLIENT)) }
        single { GroupsApi(get(AUTHORIZED_CLIENT)) }
        single { ReviewsApi(get(AUTHORIZED_CLIENT)) }
        single { CategoriesApi(get(AUTHORIZED_CLIENT)) }
        single { SavedFiltersApi(get(AUTHORIZED_CLIENT)) }
        single { NotificationsApi(get(AUTHORIZED_CLIENT)) }
        single { UnreadCounter(get()) }
        // Виджет SmartCaptcha подключается на этапе 9; пока токена нет
        single { CaptchaProvider { null } }
        single { AuthRepository(get(), get(), get(), get()) }

        viewModel { EmailViewModel(get()) }
        viewModel { (email: String) -> CodeViewModel(email, get()) }
        viewModel { (registrationToken: String) -> RegisterViewModel(registrationToken, get()) }
        viewModel { ProfileViewModel(get(), get(), get()) }
        viewModel {
            val reviews = get<ReviewsRepository>()
            GroupsViewModel(get()) { reviews.retainGroups(it) }
        }
        viewModel { CreateGroupViewModel(get()) }
        viewModel { MainNavigationViewModel(get(), get()) }
        viewModel { SectionsViewModel(get(), get()) }
        viewModel { (groupId: String?, filter: ReviewFilter) -> FiltersViewModel(groupId, filter, get(), get(), get()) }
        viewModel { (area: HomeArea) -> HomeViewModel(area, get(), get(), get(), get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { NotificationsViewModel(get(), get()) }
        viewModel { NotificationSettingsViewModel(get()) }
        viewModel { JoinGroupViewModel(get(), get()) }
        viewModel { (groupId: String) -> GroupViewModel(groupId, get(), get()) }
        viewModel { (scope: FeedScope) -> FeedViewModel(scope, get(), get(), get()) }
        viewModel { (reviewId: String) -> ReviewViewModel(reviewId, get(), get(), get(), get()) }
        viewModel { (reviewId: String?, presetGroupId: String?) ->
            ReviewFormViewModel(reviewId, presetGroupId, get(), get(), get())
        }
        viewModel { (groupId: String) -> RolesViewModel(groupId, get()) }
        viewModel { (groupId: String) -> InvitationsViewModel(groupId, get()) }
    }
