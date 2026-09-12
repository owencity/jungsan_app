package app.jeongsan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import app.jeongsan.pixelart.PixelBuffer
import app.jeongsan.pixelart.RiverConfig
import app.jeongsan.pixelart.SunsetConfig
import app.jeongsan.pixelart.buildRiverScene
import app.jeongsan.pixelart.buildSunsetScene
import app.jeongsan.pixelart.paintRiverFrame
import app.jeongsan.pixelart.paintSunsetFrame
import app.jeongsan.pixelart.pickPixelBlockSize
import kotlin.math.ceil

/**
 * 절차적 픽셀아트 배경을 화면에 올리는 Compose 래퍼.
 *
 * 로그인 화면 전용(석양 도시뷰, 화면 전체·선명하게)과 그 외 화면들(강변, 옅은
 * 스크림 아래로·기능을 가리지 않게) 둘로 나뉜다 — 웹의 두 배경 체계와 같다.
 */

/** `fps` 마다 한 번씩만 값이 갱신되는 "경과 초". 12fps 로 고정 — 60fps 로 올리면
 *  도트 애니 특유의 느낌이 사라진다(웹과 같은 이유). */
@Composable
private fun rememberPixelClock(fps: Int): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps) {
        val start = withFrameNanos { it }
        var lastDrawNanos = Long.MIN_VALUE
        val intervalNanos = 1_000_000_000L / fps
        while (true) {
            val now = withFrameNanos { it }
            if (now - lastDrawNanos < intervalNanos) continue
            lastDrawNanos = now
            t = (now - start) / 1_000_000_000f
        }
    }
    return t
}

/** 로그인 화면 전체 배경 — 도트 석양 시티뷰. 화면을 꽉 채우고 선명하게 보인다. */
@Composable
fun PixelSunsetBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        val block = remember(widthPx) {
            pickPixelBlockSize(widthPx, SunsetConfig.TARGET_ART_WIDTH, SunsetConfig.MIN_PIXEL, SunsetConfig.MAX_PIXEL)
        }
        val logicalW = remember(widthPx, block) { ceil(widthPx / block).toInt().coerceAtLeast(1) }
        val logicalH = remember(heightPx, block) { ceil(heightPx / block).toInt().coerceAtLeast(1) }

        val scene = remember(logicalW, logicalH) { buildSunsetScene(logicalW, logicalH) }
        val buffer = remember(logicalW, logicalH) { PixelBuffer(logicalW, logicalH) }
        val t = rememberPixelClock(SunsetConfig.FPS)

        val bitmap = remember(t) {
            paintSunsetFrame(buffer, scene, t)
            buffer.toImageBitmap()
        }

        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            // 확대해도 안티에일리어싱 없이 — 이게 없으면 도트가 뭉개져 그냥 흐린 그림이 된다.
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * 로그인이 아닌 화면들의 배경 — 강변 야경을 **옅게** 깐다.
 *
 * 카드가 이미 불투명한 흰 배경 위에 뜨므로 카드 안 글자는 원래도 안전하다.
 * 이 배경이 실제로 보이는 곳은 카드 사이 여백뿐이라, 스크림이 없어도 텍스트를
 * 가리진 않는다 — 다만 은은하게 남겨서 "장식이 튄다"는 인상을 주지 않게 한다.
 */
@Composable
fun PixelRiverBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        // 로그인보다 굵은 블록을 쓴다 — 배경이라는 티가 나야 "장식"으로 읽히고,
        // 너무 또렷하면 콘텐츠와 시선을 다툰다.
        val block = remember(widthPx) { pickPixelBlockSize(widthPx, 220, 3, 16) }
        val logicalW = remember(widthPx, block) { ceil(widthPx / block).toInt().coerceAtLeast(1) }
        val logicalH = remember(heightPx, block) { ceil(heightPx / block).toInt().coerceAtLeast(1) }

        val scene = remember(logicalW, logicalH) { buildRiverScene(logicalW, logicalH) }
        val buffer = remember(logicalW, logicalH) { PixelBuffer(logicalW, logicalH) }
        val t = rememberPixelClock(RiverConfig.FPS)

        val bitmap = remember(t) {
            paintRiverFrame(buffer, scene, t)
            buffer.toImageBitmap()
        }

        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
        )
    }
}
