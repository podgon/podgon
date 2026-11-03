package app.podgon.mobile.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

/** Проверить на устройстве — этап 11 (iOS-приложения пока нет). */
@Composable
actual fun rememberImagePicker(
    maxSide: Int,
    onPicked: (ByteArray) -> Unit,
): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate(maxSide) { current(it) } }
    return remember {
        {
            val config =
                PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                }
            val picker = PHPickerViewController(config)
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow
                ?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val maxSide: Int,
    private val onPicked: (ByteArray) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val image = data?.let { UIImage(data = it) } ?: return@loadDataRepresentationForTypeIdentifier
            val bytes = jpeg(image, maxSide) ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPicked(bytes) }
        }
    }
}

/** Перерисовка учитывает ориентацию снимка; заодно уменьшает до [maxSide]. */
@OptIn(ExperimentalForeignApi::class)
private fun jpeg(
    image: UIImage,
    maxSide: Int,
): ByteArray? {
    val (width, height) = image.size.useContents { width to height }
    val scale = min(1.0, maxSide / max(width, height))
    val size = CGSizeMake(width * scale, height * scale)
    val renderer = UIGraphicsImageRenderer(size)
    val resized =
        renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
        }
    return UIImageJPEGRepresentation(resized, JPEG_QUALITY)?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}

private const val JPEG_QUALITY = 0.85
