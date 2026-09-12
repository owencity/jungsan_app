package app.jeongsan.pixelart

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * ARGB(`0xAARRGGBB`) 픽셀을 직접 쓰는 오프스크린 버퍼.
 *
 * **`drawRect`/`drawImage` 대신 픽셀을 직접 쓴다** — 안티에일리어싱을 완전히 배제해야
 * "진짜 도트"로 보인다. Compose 의 벡터 드로잉으로는 이 느낌을 못 낸다. 웹의
 * `ImageData` 직접 쓰기와 완전히 같은 접근이다.
 */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height) { -0x1000000 } // 불투명 검정(0xFF000000)으로 시작

    private fun clampByte(v: Float): Int = when {
        v <= 0f -> 0
        v >= 255f -> 255
        else -> round(v).toInt()
    }

    private fun pack(c: Rgb): Int =
        -0x1000000 or (clampByte(c.r) shl 16) or (clampByte(c.g) shl 8) or clampByte(c.b)

    fun get(x: Int, y: Int): Rgb {
        val p = pixels[y * width + x]
        return Rgb(((p shr 16) and 0xFF).toFloat(), ((p shr 8) and 0xFF).toFloat(), (p and 0xFF).toFloat())
    }

    fun px(x: Int, y: Int, c: Rgb) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        pixels[y * width + x] = pack(c)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, c: Rgb) {
        val x0 = max(0, x); val y0 = max(0, y)
        val x1 = min(width, x + w); val y1 = min(height, y + h)
        if (x1 <= x0 || y1 <= y0) return
        val packed = pack(c)
        for (yy in y0 until y1) {
            val base = yy * width
            for (xx in x0 until x1) pixels[base + xx] = packed
        }
    }

    fun hline(x: Int, y: Int, len: Int, c: Rgb) = rect(x, y, len, 1, c)
    fun vline(x: Int, y: Int, len: Int, c: Rgb) = rect(x, y, 1, len, c)

    /** 빛을 더한다. 6칸으로 끊고 디더링 → 부드러운 번짐이 아니라 도트로 퍼진다. */
    fun glow(c: Rgb, amt: Float, x: Int, y: Int, tint: Rgb): Rgb {
        val q = kotlin.math.floor(amt * 6 + bayer(x, y)) / 6f
        if (q <= 0f) return c
        return Rgb(
            clampF(c.r + (tint.r - c.r) * q * 0.9f, 0f, 255f),
            clampF(c.g + (tint.g - c.g) * q * 0.9f, 0f, 255f),
            clampF(c.b + (tint.b - c.b) * q * 0.9f, 0f, 255f),
        )
    }

    /** 광원 하나를 원형으로 번지게 한다. 이미 그려진 픽셀 위에 덧칠한다. */
    fun lightBlob(cx: Int, cy: Int, r: Int, strength: Float, tint: Rgb) {
        for (dy in -r..r) {
            for (dx in -r..r) {
                val d = sqrt((dx * dx + dy * dy).toFloat())
                if (d > r) continue
                val x = cx + dx; val y = cy + dy
                if (x < 0 || y < 0 || x >= width || y >= height) continue
                val g = 1f - d / r
                px(x, y, glow(get(x, y), g * g * strength, x, y, tint))
            }
        }
    }

    fun toImageBitmap(): ImageBitmap = argbPixelsToImageBitmap(width, height, pixels)
}
