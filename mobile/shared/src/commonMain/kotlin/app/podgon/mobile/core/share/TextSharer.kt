package app.podgon.mobile.core.share

import androidx.compose.runtime.Composable

/**
 * Системное окно «Поделиться» с готовым текстом (рекомендация, код приглашения). Ссылки появятся вместе
 * с доменом и App Links (этапы 9–11).
 */
@Composable
expect fun rememberTextSharer(): (String) -> Unit
