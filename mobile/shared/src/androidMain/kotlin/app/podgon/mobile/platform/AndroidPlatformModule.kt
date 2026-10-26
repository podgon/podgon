package app.podgon.mobile.platform

import android.os.Build
import app.podgon.mobile.core.auth.AndroidTokenStorage
import app.podgon.mobile.core.auth.TokenStorage
import app.podgon.mobile.feature.auth.data.DeviceInfoProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Платформенные зависимости Android: сетевой движок, защищённое хранилище токенов, описание устройства. */
val androidPlatformModule =
    module {
        single<HttpClientEngine> { OkHttp.create() }
        single<TokenStorage> { AndroidTokenStorage(androidContext()) }
        single { DeviceInfoProvider { "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}" } }
    }
