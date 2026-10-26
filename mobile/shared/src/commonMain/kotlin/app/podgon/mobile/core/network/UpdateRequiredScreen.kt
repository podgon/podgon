package app.podgon.mobile.core.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.update_action
import app.podgon.mobile.resources.update_text
import app.podgon.mobile.resources.update_title
import org.jetbrains.compose.resources.stringResource

/** Экран обязательного обновления: сервер больше не поддерживает эту версию (R1). Дальше приложение не пускает. */
@Composable
fun UpdateRequiredScreen(updateUrl: String) {
    val uriHandler = LocalUriHandler.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.update_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.update_text), textAlign = TextAlign.Center)
            if (updateUrl.isNotBlank()) {
                Button(onClick = { runCatching { uriHandler.openUri(updateUrl) } }) {
                    Text(stringResource(Res.string.update_action))
                }
            }
        }
    }
}
