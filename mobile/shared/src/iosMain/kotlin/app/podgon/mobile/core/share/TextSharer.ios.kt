package app.podgon.mobile.core.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberTextSharer(): (String) -> Unit =
    remember {
        { text ->
            val controller = UIActivityViewController(listOf(text), null)
            UIApplication.sharedApplication.keyWindow
                ?.rootViewController
                ?.presentViewController(controller, animated = true, completion = null)
        }
    }
