package app.podgon.mobile.platform

import app.podgon.mobile.core.auth.IosTokenStorage
import app.podgon.mobile.core.auth.TokenStorage
import app.podgon.mobile.feature.auth.data.DeviceInfoProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.UIKit.UIDevice

/** Платформенные зависимости iOS: сетевой движок (NSURLSession), Keychain, описание устройства. */
val iosPlatformModule =
    module {
        single<HttpClientEngine> { Darwin.create() }
        single<TokenStorage> { IosTokenStorage() }
        single {
            DeviceInfoProvider {
                val device = UIDevice.currentDevice
                "${device.model}, ${device.systemName} ${device.systemVersion}"
            }
        }
    }
