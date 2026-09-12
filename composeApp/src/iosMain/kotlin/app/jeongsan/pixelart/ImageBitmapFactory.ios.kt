package app.jeongsan.pixelart

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Compose Multiplatform 의 iOS 타깃은 Skia(Skiko) 가 그린다. 안드로이드처럼
 * packed-int 배열을 바로 받는 API가 없어, 채널을 바이트로 풀어 다시 담는다.
 *
 * `RGBA_8888` 을 **명시적으로** 고른다 — Skia 의 `makeN32`(플랫폼 기본 포맷)를 쓰면
 * 플랫폼에 따라 바이트 순서가 BGRA 로 달라질 수 있어 색이 뒤집힐 위험이 있다.
 */
actual fun argbPixelsToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    var i = 0
    for (p in pixels) {
        bytes[i++] = ((p shr 16) and 0xFF).toByte() // R
        bytes[i++] = ((p shr 8) and 0xFF).toByte() // G
        bytes[i++] = (p and 0xFF).toByte() // B
        bytes[i++] = ((p ushr 24) and 0xFF).toByte() // A — 우리는 항상 불투명이다
    }
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(info, bytes, width * 4)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
