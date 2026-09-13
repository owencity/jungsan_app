package app.jeongsan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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

/**
 * `fps` 마다 한 번씩만 값이 갱신되는 "경과 초". 12fps 로 고정 — 60fps 로 올리면
 * 도트 애니 특유의 느낌이 사라진다(웹과 같은 이유).
 *
 * ⚠ **`Float` 가 아니라 `MutableFloatState` 자체를 리턴한다.** 처음엔 `t` 를 읽어서
 * 평범한 `Float` 로 돌려줬는데, 그러면 "값을 읽은 지점"이 이 함수 안이 돼버려서
 * 이 함수 자신만 재구성되고 — 그 결과를 받아 쓰는 배경 화면 쪽은 한 번도 다시
 * 그려지지 않았다(값은 계속 바뀌는데 화면은 첫 프레임에 멈춰 있었다). 상태 객체를
 * 그대로 돌려주고 **호출부에서** `.value` 를 읽어야, 재구성이 걸릴 지점이 실제로
 * 화면을 그리는 그 자리가 된다.
 */
@Composable
private fun rememberPixelClock(fps: Int): MutableFloatState {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps) {
        val start = withFrameNanos { it }
        val intervalNanos = 1_000_000_000L / fps
        // ⚠ "아직 안 그렸다"는 뜻으로 Long.MIN_VALUE 를 썼었는데, `now - Long.MIN_VALUE`
        // 는 부호 있는 64비트 덧셈에서 반드시 오버플로한다 — Long.MIN_VALUE 의 절댓값이
        // Long.MAX_VALUE 보다 1 크기 때문에, `now`(nanoTime, 항상 0 이상)가 무엇이든
        // wraparound 로 거대한 음수가 나온다. 그러면 첫 조건 판정이 영원히 참이 되어
        // 이 시계가 **한 번도 갱신되지 않는다** — 실제로 화면이 첫 프레임에 멈춰 있었다.
        // "한 간격 전에 그렸다" 로 잡으면 같은 뜻이면서 오버플로가 안 난다.
        var lastDrawNanos = start - intervalNanos
        while (true) {
            val now = withFrameNanos { it }
            if (now - lastDrawNanos < intervalNanos) continue
            lastDrawNanos = now
            state.floatValue = (now - start) / 1_000_000_000f
        }
    }
    return state
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
        val clock = rememberPixelClock(SunsetConfig.FPS)
        val t = clock.floatValue // 이 지점에서 읽어야 이 컴포저블이 매 프레임 다시 그려진다

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
        val clock = rememberPixelClock(RiverConfig.FPS)
        val t = clock.floatValue

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
