package app.podgon.mobile.di

import app.podgon.mobile.core.auth.SessionManager
import app.podgon.mobile.core.cache.CacheDatabase
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.network.AuthApi
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.ReviewsApi
import app.podgon.mobile.core.network.createAuthorizedClient
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.feature.auth.data.AuthRepository
import app.podgon.mobile.feature.auth.data.CaptchaProvider
import app.podgon.mobile.feature.auth.ui.CodeViewModel
import app.podgon.mobile.feature.auth.ui.EmailViewModel
import app.podgon.mobile.feature.auth.ui.RegisterViewModel
import app.podgon.mobile.feature.groups.CategoriesViewModel
import app.podgon.mobile.feature.groups.CreateGroupViewModel
import app.podgon.mobile.feature.groups.GroupViewModel
import app.podgon.mobile.feature.groups.GroupsViewModel
import app.podgon.mobile.feature.groups.InvitationsViewModel
import app.podgon.mobile.feature.groups.JoinGroupViewModel
import app.podgon.mobile.feature.groups.RolesViewModel
import app.podgon.mobile.feature.profile.ProfileViewModel
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.FeedViewModel
import app.podgon.mobile.feature.reviews.ReviewFormViewModel
import app.podgon.mobile.feature.reviews.ReviewViewModel
import app.podgon.mobile.feature.reviews.ReviewsRepository
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val PUBLIC_CLIENT = named("public")
private val AUTHORIZED_CLIENT = named("authorized")

/**
 * Общие зависимости. Платформа дополнительно предоставляет: `ApiConfig`, `HttpClientEngine`,
 * `TokenStorage`, `DeviceInfoProvider` (см. platformModule в androidMain).
 */
val appModule =
    module {
        single { ResponseCache(get<CacheDatabase>().responses()) }
        single { ReviewsRepository(get(), get()) }
        single { SessionManager(get()) { get<ResponseCache>().clear() } }
        single<HttpClient>(PUBLIC_CLIENT) { createPublicClient(get(), get()) }
        single<HttpClient>(AUTHORIZED_CLIENT) { createAuthorizedClient(get(), get(), get(), get(PUBLIC_CLIENT)) }
        single { AuthApi(get(PUBLIC_CLIENT)) }
        single { MeApi(get(AUTHORIZED_CLIENT)) }
        single { GroupsApi(get(AUTHORIZED_CLIENT)) }
        single { ReviewsApi(get(AUTHORIZED_CLIENT)) }
        // Виджет SmartCaptcha подключается на этапе 9; пока токена нет
        single { CaptchaProvider { null } }
        single { AuthRepository(get(), get(), get(), get()) }

        viewModel { EmailViewModel(get()) }
        viewModel { (email: String) -> CodeViewModel(email, get()) }
        viewModel { (registrationToken: String) -> RegisterViewModel(registrationToken, get()) }
        viewModel { ProfileViewModel(get(), get()) }
        viewModel { GroupsViewModel(get()) }
        viewModel { CreateGroupViewModel(get()) }
        viewModel { JoinGroupViewModel(get()) }
        viewModel { (groupId: String) -> GroupViewModel(groupId, get(), get()) }
        viewModel { (groupId: String) -> CategoriesViewModel(groupId, get()) }
        viewModel { (scope: FeedScope) -> FeedViewModel(scope, get(), get()) }
        viewModel { (reviewId: String) -> ReviewViewModel(reviewId, get(), get(), get()) }
        viewModel { (reviewId: String?, presetGroupId: String?) ->
            ReviewFormViewModel(reviewId, presetGroupId, get(), get())
        }
        viewModel { (groupId: String) -> RolesViewModel(groupId, get()) }
        viewModel { (groupId: String) -> InvitationsViewModel(groupId, get()) }
    }
