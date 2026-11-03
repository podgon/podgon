package app.podgon.mobile.core.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Язык приложения: как в системе или выбранный; [code] — код для ресурсов и сервера. */
enum class AppLanguage(
    val code: String?,
) {
    SYSTEM(null),
    RU("ru"),
    EN("en"),
}

/**
 * Синхронное хранилище выбранного языка на устройстве: язык нужен до первого кадра, поэтому не в Room-кэше.
 * Не сбрасывается при выходе из аккаунта.
 */
interface LanguageStore {
    fun read(): String?

    fun write(code: String?)
}

/**
 * Выбор языка (раздел «Настройки»). Язык профиля на сервере (письма, названия категорий) меняется вслед за ним
 * (`SettingsViewModel`); в запросах — заголовок `Accept-Language` с [effective].
 */
class LanguageSettings(
    private val store: LanguageStore,
) {
    private val _language =
        MutableStateFlow(
            AppLanguage.entries.firstOrNull { it.code == store.read() } ?: AppLanguage.SYSTEM,
        )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Действующий язык: выбранный, иначе язык системы (русский — русский, остальные — английский). */
    val effective: String get() = _language.value.code ?: if (systemLanguage() == "ru") "ru" else "en"

    fun set(language: AppLanguage) {
        _language.value = language
        store.write(language.code)
    }
}

/** Язык системы (двухбуквенный код) без учёта выбора в приложении. */
expect fun systemLanguage(): String

/** Показывает [content] на языке [language] (`null` — язык системы); смена языка перерисовывает интерфейс. */
@Composable
expect fun LocalizedContent(
    language: String?,
    content: @Composable () -> Unit,
)
