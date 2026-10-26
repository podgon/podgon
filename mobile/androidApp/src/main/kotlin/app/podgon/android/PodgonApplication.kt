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
                module { single { ApiConfig(BuildConfig.API_BASE_URL) } },
                androidPlatformModule,
                appModule,
            )
        }
    }
}
