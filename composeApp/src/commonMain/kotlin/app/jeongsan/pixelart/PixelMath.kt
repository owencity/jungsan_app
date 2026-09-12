package app.jeongsan.pixelart

/**
 * 절차적 픽셀아트 렌더러의 수학 기반. 웹의 `pixelSunset.ts`/`pixelStreet.ts` 와
 * **완전히 같은 시드로 완전히 같은 배치**를 만들어야 하므로, 난수·디더링 알고리즘은
 * 한 글자도 바꾸지 않고 그대로 옮긴다.
 */

/** r/g/b 는 0..255 범위의 부동소수 — JS 의 `Uint8ClampedArray` 계산 방식과 맞춘다. */
data class Rgb(val r: Float, val g: Float, val b: Float)

fun rgb(r: Int, g: Int, b: Int) = Rgb(r.toFloat(), g.toFloat(), b.toFloat())

fun clampF(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v

/**
 * mulberry32 — 시드 기반 PRNG. **Kotlin `Int` 곱셈·덧셈은 이미 32비트로 감싸 도는
 * 연산**이라(두 값 모두 JS 의 `Math.imul`/`ToInt32` 트렁케이션과 동치), 별도 처리 없이
 * 그대로 옮길 수 있다. 마지막 `>>> 0`(부호 없는 32비트)만 `toUInt()` 로 재해석한다.
 */
class Mulberry32(seed: Int) {
    private var a: Int = seed

    fun next(): Float {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        val inner = (t xor (t ushr 7)) * (t or 61)
        t = (t + inner) xor t
        return (t xor (t ushr 14)).toUInt().toFloat() / 4294967296f
    }
}

/** 4x4 베이어 디더링 행렬. 그라데이션·발광을 색 보간이 아니라 도트로 흩뿌린다. */
private val BAYER = arrayOf(
    intArrayOf(0, 8, 2, 10),
    intArrayOf(12, 4, 14, 6),
    intArrayOf(3, 11, 1, 9),
    intArrayOf(15, 7, 13, 5),
)

fun bayer(x: Int, y: Int): Float = BAYER[y and 3][x and 3] / 16f

/**
 * 화면 크기에서 "블록 하나의 화면 픽셀 수"를 정한다. 웹은 CSS 컨테이너 폭 기준으로
 * `targetArtWidth` 만큼의 논리 블록이 가로로 들어가게 픽셀 크기를 역산했다 — 화면이
 * 커질수록 블록도 커져서(minPixel~maxPixel), 그림의 "도트 굵기"가 항상 비슷해 보인다.
 */
fun pickPixelBlockSize(screenWidthPx: Float, targetArtWidth: Int, minPixel: Int, maxPixel: Int): Int {
    val raw = kotlin.math.round(screenWidthPx / targetArtWidth).toInt()
    return raw.coerceIn(minPixel, maxPixel)
}
