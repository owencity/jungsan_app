package app.jeongsan.pixelart

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * 로그인이 아닌 화면들의 배경 — 야시장 골목 밤장면(불꽃/장식나무 + 포장마차 벽 +
 * 편의점). 원래는 웹 `pixelStreet.ts`의 강변(다리) 쪽 절반만 옮겼었는데, 실제
 * 웹 메인화면(로그인 후 목록 화면)에서 좌우로 보이는 배경을 비교해보니 사용자가
 * 원한 건 반대쪽 절반인 이 골목 장면이었다. 웹 원본 소스는 이 저장소에 없어서
 * 스크린샷을 기준으로 새로 설계했다 — 강변 장면처럼 픽셀 단위로 1:1 포팅한 게
 * 아니라는 뜻.
 *
 * 이 배경은 로그인 화면과 같은 기준으로 화면 전체에서 선명하게 보인다
 * (`ui/PixelBackground.kt` 참고) — 카드 위 글자를 가리면 안 된다는 원칙은 웹과
 * 같지만, 카드의 불투명한 배경과 텍스트 헤일로로 지키지 스크림으로 죽이지 않는다.
 */
object RiverConfig {
    const val SEED = 20260827 + 7717
    const val FPS = 12
}

private val SKY_A = listOf(
    rgb(18, 15, 30), rgb(22, 18, 36), rgb(27, 22, 43), rgb(33, 27, 51),
    rgb(40, 33, 60), rgb(48, 40, 70), rgb(58, 49, 81), rgb(70, 59, 93),
)

private object CA {
    val star = rgb(190, 200, 236)
    val lamp = rgb(255, 250, 235)
    val treeDark = rgb(24, 20, 36)
    val sparkBright = rgb(220, 228, 255)
    val sparkDim = rgb(150, 166, 214)
    val farCity = rgb(38, 32, 54)
    val farCityLit = rgb(255, 198, 112)
    val nearCity = rgb(26, 21, 38)
    val water = rgb(22, 18, 36)
    val waterDeep = rgb(15, 12, 25)
    val ripple = rgb(50, 42, 70)
    val wall = rgb(198, 150, 84)
    val wallShade = rgb(164, 120, 64)
    val wallKnot = rgb(122, 86, 48)
    val merlonRed = rgb(196, 64, 58)
    val merlonRedD = rgb(150, 46, 44)
    val merlonBlue = rgb(120, 190, 210)
    val cvs = rgb(46, 58, 74)
    val cvsLit = rgb(186, 232, 236)
}

internal class Spark(val x: Int, val y: Int, val phase: Float, val rate: Float)
internal class ArcPoint(val x: Int, val y: Int)
internal class FireworkArc(val trunk: List<ArcPoint>, val sparks: List<Spark>)
internal class AlleyBuilding(val x: Int, val w: Int, val top: Int, val wins: List<Spark>)
internal class Wall(val x: Int, val w: Int, val h: Int, val accentIndex: Int)
internal class CvsBox(val x: Int, val w: Int, val h: Int)
internal class Lamp(val x: Int, val y: Int)

internal class RiverScene(
    val w: Int, val h: Int,
    val waterY: Int,
    val stars: List<Spark>, val lamp: Lamp,
    val arcs: List<FireworkArc>,
    val city: List<AlleyBuilding>,
    val wall: Wall, val cvs: CvsBox,
)

internal fun buildRiverScene(w: Int, h: Int, seed: Int = RiverConfig.SEED): RiverScene {
    val rnd = Mulberry32(seed)
    fun r() = rnd.next()

    val waterY = round(h * 0.62f).toInt()

    val stars = (0 until 22).map {
        Spark((r() * w).toInt(), (r() * waterY * 0.55f).toInt(), r() * 100, 0.6f + r() * 1.8f)
    }
    val lamp = Lamp(round(w * 0.88f).toInt(), round(h * 0.09f).toInt())

    // 불꽃/장식나무 — 밑동 하나에서 여러 갈래로 휘어 올라가는 아치. 웹의 골목 쪽
    // 하이라이트라 판단해 별도 요소로 새로 설계했다.
    val baseX = round(w * 0.30f).toInt()
    val baseY = round(h * 0.5f).toInt()
    val arcs = (0 until 6).map { i ->
        val lean = -1f + 2f * i / 5f
        val height = h * (0.30f + r() * 0.16f)
        val spread = w * (0.05f + kotlin.math.abs(lean) * 0.10f) * (0.7f + r() * 0.5f)
        val steps = 24
        val trunk = mutableListOf<ArcPoint>()
        val sparks = mutableListOf<Spark>()
        for (s in 0..steps) {
            val f = s / steps.toFloat()
            val bend = f * f
            val x = baseX + round(lean * spread * bend).toInt()
            val y = baseY - round(height * f).toInt()
            trunk.add(ArcPoint(x, y))
            if (f > 0.35f && r() > 0.45f) {
                sparks.add(Spark(x + round(r() * 6 - 3).toInt(), y + round(r() * 6 - 3).toInt(), r() * 100, 0.5f + r() * 1.5f))
            }
        }
        FireworkArc(trunk, sparks)
    }

    val city = mutableListOf<AlleyBuilding>()
    run {
        var x = -4
        while (x < w + 6) {
            val bw = 7 + (r() * 13).toInt()
            val bh = 8 + (r() * 22).toInt()
            val top = waterY - bh
            val wins = mutableListOf<Spark>()
            var wy = top + 3
            while (wy < waterY - 3) {
                var wx = x + 2
                while (wx < x + bw - 2) {
                    if (r() >= 0.66f) wins.add(Spark(wx, wy, r() * 100, 0.04f + r() * 0.09f))
                    wx += 4
                }
                wy += 5
            }
            city.add(AlleyBuilding(x, bw, top, wins))
            x += bw + 1 + (r() * 3).toInt()
        }
    }

    val wallW = round(w * 0.58f).toInt()
    val wallH = round(h * 0.20f).toInt()
    val wall = Wall(round(w * 0.02f).toInt(), wallW, wallH, accentIndex = (wallW / 5) / 2)
    val cvs = CvsBox(wall.x + wall.w + round(w * 0.04f).toInt(), round(w * 0.16f).toInt(), wallH)

    return RiverScene(w, h, waterY, stars, lamp, arcs, city, wall, cvs)
}

private fun paintSky(buf: PixelBuffer, s: RiverScene, t: Float) {
    val last = SKY_A.size - 1
    for (y in 0 until s.waterY) {
        val f = clampF(y.toFloat() / s.waterY, 0f, 1f) * last
        val i0 = floor(f).toInt(); val fr = f - i0
        for (x in 0 until buf.width) {
            val idx = if (fr > bayer(x, y)) min(i0 + 1, last) else i0
            buf.px(x, y, SKY_A[idx])
        }
    }
    for (st in s.stars) {
        if (sin(t * st.rate + st.phase) < -0.4f) continue
        buf.px(st.x, st.y, CA.star)
    }
    buf.px(s.lamp.x, s.lamp.y, CA.lamp)
    buf.lightBlob(s.lamp.x, s.lamp.y, 11, 0.55f, CA.lamp)
}

private fun paintFireworks(buf: PixelBuffer, s: RiverScene, t: Float) {
    val sway = sin(t * 0.25f) * 1.2f
    for (arc in s.arcs) {
        for (p in arc.trunk) {
            val x = p.x + round(sway * (p.y.toFloat() / s.h)).toInt()
            buf.px(x, p.y, CA.treeDark)
        }
        for (sp in arc.sparks) {
            val v = sin(t * sp.rate + sp.phase)
            if (v < -0.2f) continue
            buf.px(sp.x, sp.y, if (v > 0.5f) CA.sparkBright else CA.sparkDim)
        }
    }
}

private fun paintCity(buf: PixelBuffer, s: RiverScene, t: Float) {
    for (b in s.city) {
        buf.rect(b.x, b.top, b.w, s.waterY - b.top, CA.farCity)
        buf.hline(b.x, b.top, b.w, CA.nearCity)
        for (wn in b.wins) {
            if (sin(t * wn.rate + wn.phase) < -0.88f) continue
            buf.rect(wn.x, wn.y, 2, 1, CA.farCityLit)
        }
    }
}

private fun paintWallAndCvs(buf: PixelBuffer, s: RiverScene) {
    val w = s.wall
    val wallTop = s.waterY - w.h
    buf.rect(w.x, wallTop, w.w, w.h, CA.wall)

    // 대나무발/왕겨 벽면 같은 짜임 텍스처 — 격자무늬 + 옹이 점.
    for (yy in wallTop until s.waterY) {
        for (xx in w.x until w.x + w.w) {
            if ((xx + yy) and 3 == 0) buf.px(xx, yy, CA.wallShade)
        }
    }
    var kx = w.x + 1
    while (kx < w.x + w.w) {
        var ky = wallTop + 2
        while (ky < s.waterY) {
            if (bayer(kx, ky) > 0.82f) buf.px(kx, ky, CA.wallKnot)
            ky += 3
        }
        kx += 5
    }

    // 지붕 위 붉은 장식 블록 — 가운데 하나만 파랑으로 포인트.
    var mx = w.x
    var idx = 0
    while (mx < w.x + w.w) {
        val color = when {
            idx == w.accentIndex -> CA.merlonBlue
            idx % 2 == 0 -> CA.merlonRed
            else -> CA.merlonRedD
        }
        buf.rect(mx, wallTop - 3, 3, 3, color)
        mx += 5
        idx++
    }

    val c = s.cvs
    val cTop = s.waterY - c.h
    buf.rect(c.x, cTop, c.w, c.h, CA.cvs)
    buf.rect(c.x + 2, cTop + 3, c.w - 4, c.h - 6, CA.cvsLit)
    buf.lightBlob(c.x + c.w / 2, cTop + 4, round(c.w * 1.1f).toInt(), 0.5f, CA.cvsLit)
}

private fun paintWater(buf: PixelBuffer, s: RiverScene, t: Float) {
    val depth = s.h - s.waterY
    for (y in s.waterY until s.h) {
        val k = (y - s.waterY).toFloat() / depth
        buf.rect(0, y, buf.width, 1, if (k < 0.5f) CA.water else CA.waterDeep)
    }
    buf.hline(0, s.waterY, buf.width, CA.ripple)

    fun streak(srcX: Int, tint: Rgb, strength: Float, len: Int) {
        for (i in 0 until len) {
            val y = s.waterY + i
            if (y >= s.h) break
            val wob = round(sin(i * 0.4f + t * 1.3f + srcX * 0.7f) * (1 + i * 0.1f)).toInt()
            val x = srcX + wob
            if (x < 0 || x >= buf.width) continue
            val fade = (1f - i.toFloat() / len) * strength
            if (fade <= 0.02f) continue
            buf.px(x, y, buf.glow(buf.get(x, y), fade, x, y, tint))
            if (bayer(x, y) < 0.3f && x + 1 < buf.width) {
                buf.px(x + 1, y, buf.glow(buf.get(x + 1, y), fade * 0.5f, x + 1, y, tint))
            }
        }
    }

    streak(s.lamp.x, CA.lamp, 0.45f, round(depth * 0.6f).toInt())
    streak(s.wall.x + s.wall.w / 2, CA.wall, 0.4f, round(depth * 0.7f).toInt())
    streak(s.cvs.x + s.cvs.w / 2, CA.cvsLit, 0.45f, round(depth * 0.6f).toInt())
    for (b in s.city) {
        if (b.wins.isEmpty()) continue
        streak(b.x + (b.w shr 1), CA.farCityLit, 0.28f, round(depth * 0.35f).toInt())
    }
}

internal fun paintRiverFrame(buf: PixelBuffer, scene: RiverScene, t: Float) {
    paintSky(buf, scene, t)
    paintFireworks(buf, scene, t)
    paintCity(buf, scene, t)
    paintWallAndCvs(buf, scene)
    paintWater(buf, scene, t)
}
