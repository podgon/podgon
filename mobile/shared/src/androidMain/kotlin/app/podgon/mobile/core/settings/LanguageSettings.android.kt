package app.podgon.mobile.core.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Выбранный язык — в SharedPreferences: читается синхронно при запуске. */
class AndroidLanguageStore(
    context: Context,
) : LanguageStore {
    private val prefs = context.getSharedPreferences("podgon_settings", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(code: String?) {
        prefs.edit().apply { if (code == null) remove(KEY) else putString(KEY, code) }.apply()
    }

    private companion object {
        const val KEY = "language"
    }
}

private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

actual fun systemLanguage(): String = systemLocale().language

/**
 * Ресурсы Compose берут язык из `Locale.getDefault()`. При запуске он подменяется до первого кадра; при смене
 * языка активити пересоздаётся — строки читаются заново, а состояние навигации сохраняется.
 */
@Composable
actual fun LocalizedContent(
    language: String?,
    content: @Composable () -> Unit,
) {
    val locale = language?.let(Locale::forLanguageTag) ?: systemLocale()
    val shown = remember { locale.also(Locale::setDefault) }
    val context = LocalContext.current
    if (locale != shown) {
        LaunchedEffect(locale) {
            Locale.setDefault(locale)
            context.findActivity()?.recreate()
        }
    }
    content()
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
