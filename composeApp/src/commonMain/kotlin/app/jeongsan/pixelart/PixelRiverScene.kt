package app.jeongsan.pixelart

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * 로그인이 아닌 화면들의 배경 — 웹 `pixelStreet.ts` 의 **강변(오른쪽 거터) 장면**만
 * 옮긴 것. 웹은 왼쪽에 밤골목을 같이 그리지만, 앱은 화면 전체가 하나뿐이라
 * 강변 한 장면만 쓴다 — 넓고 차분한 쪽이 콘텐츠를 덮어도 덜 시끄럽다.
 *
 * ⚠ 이 배경은 **콘텐츠 뒤에 옅게** 깔린다(`ui/PixelBackground.kt` 의 스크림 참고) —
 * 카드 위 글자를 가리면 안 된다는 원칙은 웹과 같다.
 */
object RiverConfig {
    const val SEED = 20260827 + 7717
    const val FPS = 12
}

private val SKY_R = listOf(
    rgb(18, 26, 44), rgb(22, 32, 52), rgb(26, 38, 60), rgb(30, 45, 69),
    rgb(35, 53, 78), rgb(41, 62, 88), rgb(48, 72, 98), rgb(56, 83, 108),
)

private object CR {
    val moon = rgb(232, 240, 255)
    val star = rgb(188, 206, 236)
    val farCity = rgb(34, 46, 70)
    val farCityLit = rgb(255, 206, 128)
    val nearCity = rgb(24, 33, 52)
    val bridge = rgb(40, 52, 76)
    val bridgeDark = rgb(26, 35, 54)
    val cable = rgb(62, 80, 110)
    val bridgeLit = rgb(255, 214, 150)
    val towerLit = rgb(255, 120, 96)
    val water = rgb(20, 30, 50)
    val waterDeep = rgb(14, 22, 38)
    val ripple = rgb(44, 62, 92)
    val bank = rgb(22, 28, 40)
    val railing = rgb(52, 64, 86)
    val cvs = rgb(46, 58, 74)
    val cvsLit = rgb(186, 232, 236)
    val parasol = rgb(188, 74, 70)
    val parasolD = rgb(132, 48, 50)
    val table = rgb(70, 78, 92)
    val canLit = rgb(255, 196, 120)
}

internal class CityWin(val x: Int, val y: Int, val phase: Float, val rate: Float)
internal class CityBuilding(val x: Int, val w: Int, val top: Int, val wins: List<CityWin>)
internal class DeckLamp(val x: Int, val phase: Float)
internal class Cvs(val x: Int, val w: Int, val h: Int)
internal class Parasol(val x: Int, val y: Int)
internal class RiverStar(val x: Int, val y: Int, val phase: Float, val rate: Float)
internal class Moon(val x: Int, val y: Int, val r: Int)

internal class RiverScene(
    val w: Int, val h: Int,
    val waterY: Int, val bankY: Int, val bridgeY: Int, val towerH: Int,
    val stars: List<RiverStar>, val moon: Moon,
    val city: List<CityBuilding>, val towers: List<Int>, val deckLamps: List<DeckLamp>,
    val cvs: Cvs, val parasol: Parasol,
)

internal fun buildRiverScene(w: Int, h: Int, seed: Int = RiverConfig.SEED): RiverScene {
    val rnd = Mulberry32(seed)
    fun r() = rnd.next()

    val waterY = round(h * 0.52f).toInt()
    val bankY = round(h * 0.86f).toInt()
    // 상판은 수면 바로 위다 — 강을 건너는 다리라면 물에 닿을 듯 낮게 지나가고
    // 주탑만 스카이라인을 뚫고 올라간다.
    val bridgeY = waterY - round(h * 0.035f).toInt()
    val towerH = round(h * 0.19f).toInt()

    val stars = (0 until 26).map {
        RiverStar((r() * w).toInt(), (r() * (bridgeY - towerH) * 0.95f).toInt(), r() * 100, 0.6f + r() * 1.8f)
    }
    val moon = Moon(round(w * 0.74f).toInt(), round(h * 0.10f).toInt(), 7)

    val city = mutableListOf<CityBuilding>()
    run {
        var x = -4
        while (x < w + 6) {
            val bw = 7 + (r() * 13).toInt()
            val bh = 12 + (r() * 40).toInt()
            val top = waterY - bh
            val wins = mutableListOf<CityWin>()
            var wy = top + 3
            while (wy < waterY - 3) {
                var wx = x + 2
                while (wx < x + bw - 2) {
                    if (r() >= 0.62f) wins.add(CityWin(wx, wy, r() * 100, 0.04f + r() * 0.09f))
                    wx += 4
                }
                wy += 5
            }
            city.add(CityBuilding(x, bw, top, wins))
            x += bw + 1 + (r() * 3).toInt()
        }
    }

    val towers = listOf(round(w * 0.26f).toInt(), round(w * 0.78f).toInt())
    val deckLamps = mutableListOf<DeckLamp>()
    run {
        var x = 4
        while (x < w) { deckLamps.add(DeckLamp(x, r() * 100)); x += 11 }
    }

    val cvs = Cvs(round(w * 0.06f).toInt(), round(w * 0.30f).toInt(), round(h * 0.085f).toInt())
    val parasol = Parasol(round(w * 0.62f).toInt(), bankY + round(h * 0.03f).toInt())

    return RiverScene(w, h, waterY, bankY, bridgeY, towerH, stars, moon, city, towers, deckLamps, cvs, parasol)
}

private fun paintRiverSky(buf: PixelBuffer, s: RiverScene, t: Float) {
    val last = SKY_R.size - 1
    for (y in 0 until s.waterY) {
        val f = clampF(y.toFloat() / s.waterY, 0f, 1f) * last
        val i0 = floor(f).toInt(); val fr = f - i0
        for (x in 0 until buf.width) {
            val idx = if (fr > bayer(x, y)) min(i0 + 1, last) else i0
            buf.px(x, y, SKY_R[idx])
        }
    }
    for (st in s.stars) {
        if (sin(t * st.rate + st.phase) < -0.4f) continue
        buf.px(st.x, st.y, CR.star)
    }
    val m = s.moon
    for (dy in -m.r..m.r) {
        for (dx in -m.r..m.r) {
            val d = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
            if (d > m.r) continue
            if (d > m.r - 1.2f && bayer(m.x + dx, m.y + dy) > 0.45f) continue
            buf.px(m.x + dx, m.y + dy, CR.moon)
        }
    }
    buf.lightBlob(m.x, m.y, m.r + 9, 0.42f, CR.moon)
}

private fun paintCity(buf: PixelBuffer, s: RiverScene, t: Float) {
    for (b in s.city) {
        buf.rect(b.x, b.top, b.w, s.waterY - b.top, CR.farCity)
        buf.hline(b.x, b.top, b.w, CR.nearCity)
        for (wn in b.wins) {
            if (sin(t * wn.rate + wn.phase) < -0.88f) continue
            buf.rect(wn.x, wn.y, 2, 1, CR.farCityLit)
        }
    }
}

private fun paintBridge(buf: PixelBuffer, s: RiverScene, t: Float) {
    val y = s.bridgeY
    val towerTop = y - s.towerH

    for (tx in s.towers) {
        for (side in intArrayOf(-1, 1)) {
            for (i in 2 until 30) {
                val x = round(tx + side * i * 1.4f).toInt()
                if (x < 0 || x >= buf.width) continue
                val drop = (i / 30f) * (i / 30f) * s.towerH
                val yy = round(towerTop + drop).toInt()
                if (yy >= y) continue
                buf.px(x, yy, CR.cable)
            }
        }
    }

    buf.rect(0, y, buf.width, 3, CR.bridge)
    buf.hline(0, y + 3, buf.width, CR.bridgeDark)

    run {
        var px0 = round(buf.width * 0.08f).toInt()
        while (px0 < buf.width) {
            buf.rect(px0 - 1, y + 3, 3, s.waterY - y + 4, CR.bridgeDark)
            px0 += round(buf.width * 0.28f).toInt()
        }
    }

    for (tx in s.towers) {
        buf.rect(tx - 1, towerTop, 3, y - towerTop, CR.bridge)
        buf.hline(tx - 2, towerTop + 4, 5, CR.bridge)
        buf.hline(tx - 2, towerTop + round(s.towerH * 0.45f).toInt(), 5, CR.bridge)
        if (sin(t * 1.5f + tx) > 0) {
            buf.px(tx, towerTop - 1, CR.towerLit)
            buf.lightBlob(tx, towerTop - 1, 4, 0.7f, CR.towerLit)
        }
    }

    for (l in s.deckLamps) {
        buf.px(l.x, y - 1, CR.bridgeLit)
        buf.lightBlob(l.x, y - 1, 5, 0.72f + sin(t * 2.2f + l.phase) * 0.05f, CR.bridgeLit)
    }
}

private fun paintWater(buf: PixelBuffer, s: RiverScene, t: Float) {
    val depth = s.bankY - s.waterY
    for (y in s.waterY until s.bankY) {
        val k = (y - s.waterY).toFloat() / depth
        buf.rect(0, y, buf.width, 1, if (k < 0.5f) CR.water else CR.waterDeep)
    }
    buf.hline(0, s.waterY, buf.width, CR.ripple)

    fun streak(srcX: Int, tint: Rgb, strength: Float, len: Int) {
        for (i in 0 until len) {
            val y = s.waterY + i
            if (y >= s.bankY) break
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

    streak(s.moon.x, CR.moon, 0.55f, round(depth * 0.9f).toInt())
    for (l in s.deckLamps) streak(l.x, CR.bridgeLit, 0.5f, round(depth * 0.55f).toInt())
    for (b in s.city) {
        if (b.wins.isEmpty()) continue
        streak(b.x + (b.w shr 1), CR.farCityLit, 0.3f, round(depth * 0.35f).toInt())
    }
}

private fun paintBank(buf: PixelBuffer, s: RiverScene, t: Float) {
    buf.rect(0, s.bankY, buf.width, buf.height - s.bankY, CR.bank)
    buf.hline(0, s.bankY, buf.width, CR.railing)

    run {
        var x = 2
        while (x < buf.width) { buf.vline(x, s.bankY - 4, 4, CR.railing); x += 6 }
    }
    buf.hline(0, s.bankY - 4, buf.width, CR.railing)

    val c = s.cvs
    val top = buf.height - c.h - 2
    buf.rect(c.x, top, c.w, c.h, CR.cvs)
    buf.rect(c.x + 2, top + 3, c.w - 4, c.h - 6, CR.cvsLit)
    buf.hline(c.x, top, c.w, CR.railing)
    buf.lightBlob(c.x + c.w / 2, top + 4, round(c.w * 1.2f).toInt(), 0.5f, CR.cvsLit)

    val pz = s.parasol
    for (i in -7..7) {
        val dy = if (kotlin.math.abs(i) > 4) 1 else 0
        buf.px(pz.x + i, pz.y + dy, if (i % 3 == 0) CR.parasolD else CR.parasol)
    }
    buf.vline(pz.x, pz.y + 1, 9, CR.table)
    buf.hline(pz.x - 5, pz.y + 10, 11, CR.table)
    if (sin(t * 1.1f) > -0.5f) {
        buf.px(pz.x - 2, pz.y + 9, CR.canLit)
        buf.px(pz.x + 2, pz.y + 9, CR.canLit)
    }
}

internal fun paintRiverFrame(buf: PixelBuffer, scene: RiverScene, t: Float) {
    paintRiverSky(buf, scene, t)
    paintCity(buf, scene, t)
    paintBridge(buf, scene, t)
    paintWater(buf, scene, t)
    paintBank(buf, scene, t)
}
