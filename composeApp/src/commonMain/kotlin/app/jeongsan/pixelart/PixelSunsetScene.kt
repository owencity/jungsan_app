package app.jeongsan.pixelart

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 로그인 화면 전체 배경 — 웹 `pixelSunset.ts` 를 그대로 옮긴 것.
 *
 * 원본이 명시적으로 경고한 것들이라 그대로 지킨다: 안티에일리어싱 없는 픽셀 직접
 * 쓰기, CSS 그라데이션이 아니라 13단계 팔레트 + 베이어 4x4 디더링, 12fps 고정.
 */
object SunsetConfig {
    const val SEED = 20260821
    const val FPS = 12
    const val TARGET_ART_WIDTH = 460
    const val MIN_PIXEL = 2
    const val MAX_PIXEL = 12
    const val GLOW_X = 0.66f
}

private val SKY = listOf(
    rgb(13, 17, 38), rgb(19, 25, 52), rgb(27, 32, 68), rgb(39, 37, 82),
    rgb(55, 44, 90), rgb(76, 51, 94), rgb(102, 58, 93), rgb(131, 69, 87),
    rgb(163, 84, 78), rgb(194, 105, 71), rgb(219, 131, 71), rgb(237, 163, 85),
    rgb(248, 196, 114),
)
private val WATER = listOf(
    rgb(44, 31, 50), rgb(38, 28, 48), rgb(33, 26, 47),
    rgb(29, 24, 46), rgb(26, 23, 45), rgb(24, 22, 44),
)
private object SC {
    val farBuild = rgb(46, 42, 74)
    val build = rgb(17, 16, 33)
    val buildAlt = rgb(23, 21, 43)
    val buildEdge = rgb(31, 28, 54)
    val win = rgb(255, 205, 120)
    val winWarm = rgb(255, 172, 88)
    val winPale = rgb(255, 232, 176)
    val winCool = rgb(150, 190, 232)
    val bridge = rgb(14, 13, 28)
    val bridgeLit = rgb(255, 190, 110)
    val fg = rgb(7, 8, 18)
    val fgSoft = rgb(12, 13, 27)
    val lamp = rgb(255, 214, 140)
    val phone = rgb(176, 214, 255)
    val star = rgb(214, 224, 255)
}

/** 화면 밝기를 따뜻한 색 쪽으로 끌어올린다. 채도가 아니라 항상 고정된 난색 델타를 더한다. */
private fun warm(c: Rgb, amt: Float, x: Int, y: Int): Rgb {
    val q = floor(amt * 6 + bayer(x, y)) / 6f
    if (q <= 0f) return c
    return Rgb(
        clampF(c.r + q * 92, 0f, 255f),
        clampF(c.g + q * 56, 0f, 255f),
        clampF(c.b + q * 22, 0f, 255f),
    )
}

internal class FarBuilding(val x: Int, val w: Int, val h: Int)
internal class Win(val x: Int, val y: Int, val c: Rgb, val base: Boolean, val phase: Float, val rate: Float)
internal class NearBuilding(val x: Int, val w: Int, val h: Int, val top: Int, val color: Rgb, val wins: List<Win>)
internal class BridgeLight(val x: Int, val phase: Float)
internal class Bridge(val x0: Int, val x1: Int, val t1: Int, val t2: Int, val deckY: Int, val towerH: Int, val lights: List<BridgeLight>)
internal class CloudRow(val dx: Int, val w: Int)
internal class Cloud(val x: Float, val y: Int, val rows: List<CloudRow>, val speed: Float)
internal class Star(val x: Int, val y: Int, val phase: Float, val rate: Float)
internal class Reflect(val x: Int, val c: Rgb, val len: Int, val ph: Float)
internal class Bird(val ox: Int, val oy: Int, val ph: Float)

internal class SunsetScene(
    val w: Int, val h: Int,
    val pavementTop: Int, val railTop: Int, val horizonY: Int, val glowCx: Int,
    val far: List<FarBuilding>, val near: List<NearBuilding>, val bridge: Bridge,
    val clouds: List<Cloud>, val stars: List<Star>, val reflect: List<Reflect>,
    val lampX: Int, val personX: Int, val birds: List<Bird>, val birdY: Int,
)

internal fun buildSunsetScene(w: Int, h: Int, seed: Int = SunsetConfig.SEED): SunsetScene {
    val rnd = Mulberry32(seed)
    fun r() = rnd.next()

    val pavementTop = h - (round(h * 0.085f).toInt()).coerceIn(14, 26)
    val railTop = pavementTop - (round(h * 0.075f).toInt()).coerceIn(14, 24)
    val horizonY = (railTop - (round(h * 0.10f).toInt()).coerceIn(18, 34))
        .coerceIn(round(h * 0.30f).toInt(), h - 50)
    val glowCx = round(w * SunsetConfig.GLOW_X).toInt()

    val far = mutableListOf<FarBuilding>()
    run {
        var x = -8
        while (x < w + 10) {
            val bw = 10 + (r() * 22).toInt()
            val bh = 10 + (r() * min(46, horizonY - 16)).toInt()
            far.add(FarBuilding(x, bw, bh))
            x += bw + (r() * 4).toInt()
        }
    }

    val near = mutableListOf<NearBuilding>()
    run {
        var x = -6
        while (x < w + 12) {
            val bw = 9 + (r() * 20).toInt()
            val bh = 16 + (r() * min(78, horizonY - 14)).toInt()
            val top = horizonY - bh
            val color = if (r() < 0.45f) SC.buildAlt else SC.build
            val wins = mutableListOf<Win>()
            var wy = top + 3
            while (wy < horizonY - 3) {
                var wx = x + 2
                while (wx < x + bw - 2) {
                    if (r() >= 0.30f) {
                        val roll = r()
                        val col = when {
                            roll < 0.06f -> SC.winCool
                            roll < 0.30f -> SC.winPale
                            roll < 0.68f -> SC.win
                            else -> SC.winWarm
                        }
                        wins.add(Win(wx, wy, col, base = r() < 0.58f, phase = r() * 100, rate = 0.03f + r() * 0.10f))
                    }
                    wx += 4
                }
                wy += 4
            }
            near.add(NearBuilding(x, bw, bh, top, color, wins))
            x += bw + if (r() < 0.34f) 1 + (r() * 3).toInt() else 0
        }
    }

    val bx0 = round(w * 0.01f).toInt()
    val bx1 = round(w * 0.36f).toInt()
    val span = bx1 - bx0
    val bLights = mutableListOf<BridgeLight>()
    run {
        var x = bx0 + 3
        while (x < bx1) {
            bLights.add(BridgeLight(x, r() * 100))
            x += 7
        }
    }
    val bridge = Bridge(
        x0 = bx0, x1 = bx1,
        t1 = bx0 + round(span * 0.30f).toInt(),
        t2 = bx0 + round(span * 0.76f).toInt(),
        deckY = horizonY - clampF(h * 0.045f, 10f, 20f).toInt(),
        towerH = round(clampF(h * 0.17f, 34f, 62f)).toInt(),
        lights = bLights,
    )

    val clouds = mutableListOf<Cloud>()
    val cloudN = 5 + (r() * 3).toInt()
    repeat(cloudN) {
        val cw = 30 + (r() * 70).toInt()
        val ch = 3 + (r() * 4).toInt()
        val rows = mutableListOf<CloudRow>()
        for (row in 0 until ch) {
            val inset = round(abs(row - (ch - 1) * 0.6f) * (2 + r() * 4)).toInt()
            rows.add(CloudRow(inset + (r() * 4).toInt(), cw - inset * 2))
        }
        clouds.add(
            Cloud(
                x = r() * (w + 120) - 60,
                y = round(horizonY * (0.30f + r() * 0.58f)).toInt(),
                rows = rows,
                speed = 0.9f + r() * 2.2f,
            ),
        )
    }

    val stars = mutableListOf<Star>()
    val starN = round(w * h * 0.0009f).toInt()
    repeat(starN) {
        stars.add(Star((r() * w).toInt(), (r() * horizonY * 0.52f).toInt(), r() * 100, 0.4f + r() * 1.6f))
    }

    val reflect = mutableListOf<Reflect>()
    for (b in near) {
        for (wn in b.wins) {
            if (wn.y < horizonY - 26 && r() < 0.13f) {
                reflect.add(Reflect(wn.x, wn.c, 2 + (r() * 5).toInt(), r() * 100))
            }
        }
    }

    val lampX = round(w * 0.085f).toInt()
    val personX = round(w * 0.225f).toInt()

    val birds = (0 until 6).map { i -> Bird(i * 7 + (r() * 5).toInt(), (r() * 7).toInt() - 3, r() * 6) }
    val birdY = round(horizonY * 0.30f).toInt()

    return SunsetScene(w, h, pavementTop, railTop, horizonY, glowCx, far, near, bridge, clouds, stars, reflect, lampX, personX, birds, birdY)
}

private fun paintSky(buf: PixelBuffer, s: SunsetScene, t: Float) {
    val hz = s.horizonY; val last = SKY.size - 1
    val glowR = kotlin.math.max(70f, hz * 0.95f)
    val gx = s.glowCx
    val pulse = 0.9f + sin(t * 0.55f) * 0.06f

    for (y in 0 until hz) {
        val f = (y.toFloat() / hz) * last
        val i0 = floor(f).toInt(); val fr = f - i0
        for (x in 0 until buf.width) {
            val idx = if (fr > bayer(x, y)) min(i0 + 1, last) else i0
            var c = SKY[idx]
            val dx = (x - gx) * 0.62f; val dy = (y - hz) * 1.5f
            val d = sqrt(dx * dx + dy * dy)
            if (d < glowR) {
                val g = 1f - d / glowR
                c = warm(c, g * g * 0.85f * pulse, x, y)
            }
            buf.px(x, y, c)
        }
    }
}

private fun paintStars(buf: PixelBuffer, s: SunsetScene, t: Float) {
    for (st in s.stars) {
        val tw = sin(t * st.rate + st.phase)
        if (tw < -0.15f) continue
        val a = 0.35f + tw * 0.35f
        val base = SKY[(st.y.toFloat() / s.horizonY * (SKY.size - 1)).toInt().coerceIn(0, SKY.size - 1)]
        buf.px(
            st.x, st.y,
            Rgb(base.r + (SC.star.r - base.r) * a, base.g + (SC.star.g - base.g) * a, base.b + (SC.star.b - base.b) * a),
        )
    }
}

private fun paintClouds(buf: PixelBuffer, s: SunsetScene, t: Float) {
    for (cl in s.clouds) {
        val period = buf.width + 160
        var cx = cl.x + t * cl.speed
        cx = ((cx % period) + period) % period - 80
        val cxi = round(cx).toInt()
        val nearGlow = 1f - clampF(abs(cl.y - s.horizonY) / (s.horizonY * 0.8f), 0f, 1f)

        cl.rows.forEachIndexed { r, row ->
            val y = cl.y + r
            val isBottom = r >= cl.rows.size - 2
            val idx = ((y.toFloat() / s.horizonY) * (SKY.size - 1)).toInt() + if (isBottom) 2 else -2
            val base = SKY[idx.coerceIn(0, SKY.size - 1)]
            val lit = if (isBottom) 0.30f + nearGlow * 0.55f else 0.05f
            for (x in (cxi + row.dx) until (cxi + row.dx + row.w)) {
                buf.px(x, y, warm(base, lit, x, y))
            }
        }
    }
}

private fun paintBirds(buf: PixelBuffer, s: SunsetScene, t: Float) {
    val cycle = 26f
    val p = (t % cycle) / cycle
    if (p > 0.55f) return
    val headX = -30f + (p * (buf.width + 90)) / 0.55f
    val c = rgb(30, 30, 52)
    for (b in s.birds) {
        val x = round(headX - b.ox).toInt()
        val y = round(s.birdY + b.oy + sin(t * 0.5f + b.ph) * 3).toInt()
        val up = sin(t * 7 + b.ph) > 0
        if (up) {
            buf.px(x - 1, y - 1, c); buf.px(x, y, c); buf.px(x + 1, y - 1, c)
        } else {
            buf.px(x - 1, y + 1, c); buf.px(x, y, c); buf.px(x + 1, y + 1, c)
        }
    }
}

private fun paintFarSkyline(buf: PixelBuffer, s: SunsetScene) {
    for (b in s.far) {
        val top = s.horizonY - b.h
        for (y in top until s.horizonY) {
            val k = (y - top).toFloat() / kotlin.math.max(1, b.h)
            val sky = SKY[((y.toFloat() / s.horizonY) * (SKY.size - 1)).toInt().coerceIn(0, SKY.size - 1)]
            val c = Rgb(
                SC.farBuild.r + (sky.r - SC.farBuild.r) * k * 0.55f,
                SC.farBuild.g + (sky.g - SC.farBuild.g) * k * 0.55f,
                SC.farBuild.b + (sky.b - SC.farBuild.b) * k * 0.55f,
            )
            buf.hline(b.x, y, b.w, c)
        }
    }
}

private fun paintBridge(buf: PixelBuffer, s: SunsetScene, t: Float) {
    val br = s.bridge
    val towerTop1 = br.deckY - br.towerH
    val sag = round(br.towerH * 0.55f).toInt()

    fun cableY(x: Int): Int = when {
        x < br.t1 -> {
            val k = (x - br.x0).toFloat() / kotlin.math.max(1, br.t1 - br.x0)
            round(br.deckY - 2 - (towerTop1 - br.deckY + 2) * -k).toInt()
        }
        x > br.t2 -> {
            val k = (x - br.t2).toFloat() / kotlin.math.max(1, br.x1 - br.t2)
            round(towerTop1 + (br.deckY - 2 - towerTop1) * k).toInt()
        }
        else -> {
            val k = (x - br.t1).toFloat() / kotlin.math.max(1, br.t2 - br.t1)
            round(towerTop1 + sin(k * kotlin.math.PI.toFloat()) * sag).toInt()
        }
    }

    for (x in br.x0..br.x1) {
        val cy = cableY(x)
        buf.px(x, cy, SC.bridge)
        if (x % 5 == 0 && cy < br.deckY - 2) buf.vline(x, cy, br.deckY - cy, SC.bridge)
    }

    buf.rect(br.x0, br.deckY, br.x1 - br.x0, 2, SC.bridge)
    buf.rect(br.t1 - 1, towerTop1, 2, br.deckY - towerTop1 + 2, SC.bridge)
    buf.rect(br.t2 - 1, towerTop1, 2, br.deckY - towerTop1 + 2, SC.bridge)

    for (l in br.lights) {
        val f = sin(t * 1.6f + l.phase)
        if (f < -0.75f) continue
        buf.px(l.x, br.deckY - 1, SC.bridgeLit)
        if (f > 0.6f) buf.px(l.x, br.deckY - 2, rgb(255, 224, 168))
    }
}

private fun paintNearSkyline(buf: PixelBuffer, s: SunsetScene, t: Float) {
    for (b in s.near) {
        buf.rect(b.x, b.top, b.w, b.h, b.color)
        buf.hline(b.x, b.top, b.w, SC.buildEdge)
        for (wn in b.wins) {
            val wave = sin(t * wn.rate + wn.phase)
            val on = if (wn.base) wave > -0.9f else wave > 0.86f
            if (!on) continue
            buf.rect(wn.x, wn.y, 2, 2, wn.c)
        }
    }
}

private fun paintWater(buf: PixelBuffer, s: SunsetScene, t: Float) {
    val top = s.horizonY; val bot = s.pavementTop
    val depth = kotlin.math.max(1, bot - top)
    val wLast = WATER.size - 1

    for (y in top until bot) {
        val k = (y - top).toFloat() / depth
        val f = k * wLast
        val i0 = floor(f).toInt(); val fr = f - i0
        val nearHz = (1f - k).coerceAtLeast(0f).pow(3.4f)

        for (x in 0 until buf.width) {
            val idx = if (fr > bayer(x, y)) min(i0 + 1, wLast) else i0
            var c = WATER[idx]
            if (nearHz > 0.02f) c = warm(c, nearHz * 0.16f, x, y)

            val dcol = abs(x - s.glowCx)
            val width = 8 + k * 40
            if (dcol < width) {
                val g = (1f - dcol / width) * (1f - k * 0.55f)
                val wob = sin(y * 0.9f + t * 2.4f) * 0.5f + 0.5f
                c = warm(c, g * g * 0.95f * (0.40f + wob * 0.60f), x, y)
            }
            buf.px(x, y, c)
        }

        if ((y - top) % 3 == 1) {
            val off = round(sin(y * 1.7f + t * 1.9f) * 9).toInt()
            val hl = WATER[(floor(f).toInt() - 1).coerceIn(0, wLast)]
            var x = ((off % 7) + 7) % 7
            while (x < buf.width) {
                buf.px(x, y, Rgb(hl.r + 9, hl.g + 7, hl.b + 13))
                x += 7
            }
        }
    }

    for (r in s.reflect) {
        for (i in 0 until r.len) {
            val y = top + 1 + i * 2
            if (y >= bot) break
            val wob = round(sin(t * 2.1f + r.ph + i * 0.9f) * 1.7f).toInt()
            val fade = (1f - i.toFloat() / r.len) * 0.34f
            val base = WATER[(((i * 2).toFloat() / depth) * (WATER.size - 1)).toInt().coerceIn(0, WATER.size - 1)]
            buf.px(
                r.x + wob, y,
                Rgb(base.r + r.c.r * fade, base.g + r.c.g * fade * 0.8f, base.b + r.c.b * fade * 0.55f),
            )
        }
    }

    buf.hline(0, top, buf.width, rgb(12, 14, 32))
}

private fun paintForeground(buf: PixelBuffer, s: SunsetScene, t: Float) {
    buf.rect(0, s.pavementTop, buf.width, buf.height - s.pavementTop, SC.fg)
    run {
        var y = s.pavementTop + 2
        while (y < buf.height) {
            var x = (y * 5) % 9
            while (x < buf.width) { buf.px(x, y, SC.fgSoft); x += 9 }
            y += 3
        }
    }

    val railBot = s.pavementTop
    buf.rect(0, s.railTop, buf.width, 2, SC.fg)
    buf.hline(0, s.railTop - 1, buf.width, rgb(26, 26, 50))
    buf.hline(0, railBot - 4, buf.width, SC.fg)
    run {
        var x = 0
        while (x < buf.width) { buf.vline(x, s.railTop, railBot - s.railTop - 3, SC.fg); x += 9 }
    }

    // 가로등 + 발광
    val lx = s.lampX
    val lampTop = s.railTop - clampF(buf.height * 0.20f, 34f, 68f).toInt()
    buf.vline(lx, lampTop, s.pavementTop - lampTop, SC.fg)
    buf.hline(lx, lampTop, 6, SC.fg)
    buf.px(lx + 6, lampTop + 1, SC.fg)
    val headX = lx + 6; val headY = lampTop + 2
    val flick = 0.86f + sin(t * 3.1f) * 0.05f + sin(t * 11.3f) * 0.03f
    val gr = 22
    for (dy in -gr..gr) {
        for (dx in -gr..gr) {
            val d = sqrt((dx * dx + dy * dy).toFloat())
            if (d > gr) continue
            val x = headX + dx; val y = headY + dy
            if (x < 0 || y < 0 || x >= buf.width || y >= buf.height) continue
            val g = 1f - d / gr
            buf.px(x, y, warm(buf.get(x, y), g * g * g * 1.15f * flick, x, y))
        }
    }
    buf.rect(headX - 1, headY, 3, 3, SC.lamp)
    buf.rect(headX - 1, headY + 3, 3, 1, rgb(255, 236, 190))

    // 사람 — 휴대폰을 보며 서 있다
    val pxx = s.personX; val feet = s.pavementTop + 1
    val headTop = feet - 34
    buf.rect(pxx - 2, headTop, 5, 5, SC.fg)
    buf.rect(pxx - 1, headTop + 5, 3, 2, SC.fg)
    buf.rect(pxx - 4, headTop + 6, 9, 13, SC.fg)
    buf.rect(pxx - 6, headTop + 9, 3, 7, SC.fg)
    buf.rect(pxx - 3, headTop + 19, 3, 15, SC.fg)
    buf.rect(pxx + 1, headTop + 19, 3, 15, SC.fg)
    val ph = 0.65f + sin(t * 1.3f) * 0.35f
    buf.rect(pxx - 7, headTop + 10, 2, 2, Rgb(SC.phone.r * ph, SC.phone.g * ph, SC.phone.b * ph))
    buf.px(pxx - 6, headTop + 7, Rgb(SC.phone.r * ph * 0.42f + 22, SC.phone.g * ph * 0.42f + 22, SC.phone.b * ph * 0.5f + 34))
    buf.px(pxx - 5, headTop + 6, Rgb(SC.phone.r * ph * 0.22f + 18, SC.phone.g * ph * 0.22f + 18, SC.phone.b * ph * 0.3f + 28))

    // 강아지
    val dx0 = pxx + 9
    buf.rect(dx0, feet - 9, 10, 6, SC.fg)
    buf.rect(dx0 + 8, feet - 13, 4, 4, SC.fg)
    buf.px(dx0 + 8, feet - 14, SC.fg)
    buf.px(dx0 + 11, feet - 14, SC.fg)
    buf.rect(dx0 + 1, feet - 3, 2, 3, SC.fg)
    buf.rect(dx0 + 6, feet - 3, 2, 3, SC.fg)
    val wag = if (sin(t * 6) > 0) -2 else -4
    buf.px(dx0 - 1, feet - 9, SC.fg)
    buf.rect(dx0 - 2, feet - 9 + wag, 2, 2, SC.fg)

    // 하단 비네트
    val vh = round(buf.height * 0.16f).toInt()
    for (y in 0 until vh) {
        val a = (1f - y.toFloat() / vh) * 0.5f
        val q = floor(a * 5 + bayer(0, y)) / 5f
        if (q <= 0f) continue
        for (x in 0 until buf.width) {
            if (bayer(x, y) > a) continue
            val c = buf.get(x, y)
            buf.px(x, y, Rgb(c.r * 0.55f, c.g * 0.55f, c.b * 0.62f))
        }
    }
}

/** 한 프레임을 그린다. `t` 는 시작 후 경과 초. */
internal fun paintSunsetFrame(buf: PixelBuffer, scene: SunsetScene, t: Float) {
    paintSky(buf, scene, t)
    paintStars(buf, scene, t)
    paintClouds(buf, scene, t)
    paintBirds(buf, scene, t)
    paintFarSkyline(buf, scene)
    paintBridge(buf, scene, t)
    paintNearSkyline(buf, scene, t)
    paintWater(buf, scene, t)
    paintForeground(buf, scene, t)
}
