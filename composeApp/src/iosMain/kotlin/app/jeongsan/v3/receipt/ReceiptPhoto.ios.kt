package app.jeongsan.v3.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

/** Swift의 카메라/시스템 사진 선택기와 연결하며 내부 API 키나 사진을 영구 저장하지 않는다. */
object IosReceiptPhotoBridge {
    var launcher: ((String) -> Unit)? = null
    var cancel: (() -> Unit)? = null
    private var completion: ((ReceiptPhotoResult) -> Unit)? = null

    fun start(source: ReceiptSource, callback: (ReceiptPhotoResult) -> Unit) {
        val launch = launcher
        if (completion != null || launch == null) { callback(ReceiptPhotoResult.Failed("사진 선택기를 열지 못했어요. 직접 입력해 주세요.")); return }
        completion = callback
        launch(source.name)
    }

    fun complete(jpegBase64: String?, cancelled: Boolean, message: String?) {
        val callback = completion ?: return
        completion = null
        val result = when {
            cancelled -> ReceiptPhotoResult.Cancelled
            message != null -> ReceiptPhotoResult.Failed(message)
            jpegBase64 != null -> try {
                require(jpegBase64.length <= ((MAX_RECEIPT_BYTES + 2) / 3) * 4)
                val bytes = Base64.decode(jpegBase64)
                ReceiptPhotoResult.Selected(ReceiptPhoto(bytes, Image.makeFromEncoded(bytes).toComposeImageBitmap()))
            } catch (_: Exception) { ReceiptPhotoResult.Failed("사진을 읽지 못했어요. 다시 선택해 주세요.") }
            else -> ReceiptPhotoResult.Failed("사진을 읽지 못했어요. 다시 선택해 주세요.")
        }
        callback(result)
    }

    fun cancelPending() { cancel?.invoke(); completion = null }
}

@Composable
actual fun rememberReceiptPhotoPicker(): (ReceiptSource, (ReceiptPhotoResult) -> Unit) -> Unit {
    DisposableEffect(Unit) { onDispose { IosReceiptPhotoBridge.cancelPending() } }
    return remember { { source, callback -> IosReceiptPhotoBridge.start(source, callback) } }
}
