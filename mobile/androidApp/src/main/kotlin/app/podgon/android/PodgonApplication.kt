package app.podgon.android

import android.app.Application
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.di.appModule
import app.podgon.mobile.platform.androidPlatformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class PodgonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PodgonApplication)
            modules(
                module {
                    single {
                        ApiConfig(
                            baseUrl = BuildConfig.API_BASE_URL,
                            fallbackBaseUrls =
                                BuildConfig.API_FALLBACK_URLS
                                    .split(
                                        ',',
                                    ).map(String::trim)
                                    .filter(String::isNotEmpty),
                            platform = "android",
                            appVersion = BuildConfig.VERSION_CODE,
                            updateUrl = BuildConfig.UPDATE_URL,
                        )
                    }
                },
                androidPlatformModule,
                appModule,
            )
        }
    }
}
