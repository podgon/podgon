package app.podgon.mobile

import androidx.compose.ui.window.ComposeUIViewController
import app.podgon.mobile.core.network.ApiConfig
import app.podgon.mobile.di.appModule
import app.podgon.mobile.platform.iosPlatformModule
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var koinStarted = false

/** Точка входа для iosApp (этап 11): запускает Koin один раз и возвращает корневой экран Compose. */
fun mainViewController(apiBaseUrl: String): UIViewController {
    if (!koinStarted) {
        startKoin {
            modules(
                module { single { ApiConfig(apiBaseUrl) } },
                iosPlatformModule,
                appModule,
            )
        }
        koinStarted = true
    }
    return ComposeUIViewController { App() }
}
