package app.podgon.mobile.designsystem

import androidx.compose.runtime.Composable

/** Цвет значков системных панелей под тему приложения: на светлой теме — тёмные значки (edge-to-edge). */
@Composable
expect fun SystemBarsAppearance(dark: Boolean)
