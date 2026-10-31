@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.jeongsan.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.UIKit.*

actual fun encodeSettlementPng(bitmap: ImageBitmap): ByteArray = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
    checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
}

/** 파일 저장/공유는 시스템 창에서 사용자가 결정한다. iPad에서는 팝오버 기준 뷰가 필수다. */
@Composable
actual fun rememberSaveSettlementImage(): (ByteArray, String, (SettlementImageSaveResult) -> Unit) -> Unit = remember {
    { bytes, filename, callback ->
        var presenter = (UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() })?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        if (presenter == null || bytes.isEmpty()) callback(SettlementImageSaveResult.FAILED)
        else {
            val path = NSTemporaryDirectory() + NSUUID().UUIDString + "-" + filename
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            if (!data.writeToFile(path, atomically = true)) callback(SettlementImageSaveResult.FAILED)
            else {
                val activity = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
                activity.view.accessibilityIdentifier = "settlement-image-save-sheet"
                activity.popoverPresentationController?.sourceView = presenter.view
                activity.popoverPresentationController?.sourceRect = presenter.view.bounds
                activity.completionWithItemsHandler = { _, completed, _, error ->
                    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                    callback(if (error != null) SettlementImageSaveResult.FAILED else if (completed) SettlementImageSaveResult.HANDED_OFF else SettlementImageSaveResult.CANCELLED)
                }
                presenter.presentViewController(activity, animated = true, completion = null)
            }
        }
    }
}
