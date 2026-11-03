package app.podgon.mobile.core.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/** Выбранный язык — в NSUserDefaults. */
class IosLanguageStore : LanguageStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): String? = defaults.stringForKey(KEY)

    override fun write(code: String?) {
        if (code == null) defaults.removeObjectForKey(KEY) else defaults.setObject(code, KEY)
    }

    private companion object {
        const val KEY = "podgon.language"
    }
}

actual fun systemLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') ?: "en"

/**
 * Ресурсы Compose берут язык из предпочитаемых языков (`AppleLanguages`); после подмены интерфейс
 * перестраивается заново. Проверить на устройстве — этап 11.
 */
@Composable
actual fun LocalizedContent(
    language: String?,
    content: @Composable () -> Unit,
) {
    remember(language) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (language == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(language), APPLE_LANGUAGES)
        }
    }
    key(language) { content() }
}

private const val APPLE_LANGUAGES = "AppleLanguages"
