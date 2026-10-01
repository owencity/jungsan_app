package app.jeongsan.pixelart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 국자 총무 캐릭터 — 웹 `profile/src/jeongsan/character/hostSprite.ts`를 **글자 하나 바꾸지 않고**
 * 옮긴 것이다. 원본은 웹이다. 캐릭터를 고칠 땐 웹부터 고치고 여기를 맞춘다(`docs/SCREENS.md` §6).
 *
 * 20×21 논리 픽셀. 맨 위 한 줄은 위아래로 흔들릴 여유다. 레이어를 아래 순서로 겹친다:
 * 들고 있는 도구 → 몸 → 앞치마 → 모자/왕관 → 반짝임. 도구를 몸보다 먼저 그려야
 * 손잡이 끝이 손 뒤로 숨는다.
 */
object HostSprite {
    const val W = 20
    const val H = 21

    data class Title(val level: Int, val name: String, val min: Int)

    /** 칭호 구간 — `REQUIREMENTS.md` §8.3. 경계값은 위 단계로 친다. */
    val TITLES = listOf(
        Title(1, "새내기 총무", 0),
        Title(2, "믿음직한 총무", 100),
        Title(3, "프로 총무", 1_000),
        Title(4, "전설의 총무", 10_000),
    )

    fun levelOf(spoons: Int): Int = TITLES.last { spoons >= it.min }.level

    fun titleOf(spoons: Int): String = TITLES[levelOf(spoons) - 1].name

    private fun hex(s: String) = Color(0xFF000000 or s.removePrefix("#").toLong(16))

    private val PAL: Map<Char, Color> = mapOf(
        'K' to "#2A2238", 'H' to "#3B2F4A", 'S' to "#FFD9B5", 'C' to "#FF9DB0", 'T' to "#4A8CE8", 'P' to "#3D4A66",
        'A' to "#FF9A3D", 'a' to "#E07B1C", 'W' to "#FFFFFF", 'w' to "#DCE6F2",
        'o' to "#C98A4B", 'O' to "#8A5A2B",
        'G' to "#F2B233", 'g' to "#C98A12", 'Y' to "#FFF3B0",
    ).mapValues { hex(it.value) }

    /** 숟가락·국자 재질. `m` 본체, `h` 광택. */
    private val STEEL = mapOf('m' to hex("#A9B7C9"), 'h' to hex("#EAF0F7"))
    private val SILVER = mapOf('m' to hex("#CFD9E6"), 'h' to hex("#FFFFFF"))
    private val GOLD = mapOf('m' to hex("#F2B233"), 'h' to hex("#FFE58A"))

    // ── 격자 ('.'는 투명) ─────────────────────────
    private val BODY = listOf(
        "....KKKKKKKK....",
        "...KHHHHHHHHK...",
        "..KHHHHHHHHHHK..",
        "..KHHSSSSSSHHK..",
        "..KHSSSSSSSSHK..",
        "..KSSKSSSSKSSK..",
        "..KSSKSSSSKSSK..",
        "..KSCSSKKSSCSK..",
        "...KSSSSSSSSK...",
        "....KKTTTTKK....",
        "...KTTTTTTTTK...",
        "..KSKTTTTTTKSK..",
        "..KKKTTTTTTKKK..",
        "...KPPPPPPPPK...",
        "...KPPPKKPPPK...",
        "...KKKK..KKKK...",
    )
    private val APRON = listOf(
        ".....A....A.....",
        "......AAAA......",
        ".....AAAAAA.....",
        ".....AAaaAA.....",
        "....AAAAAAAA....",
    )
    private val CHEF_HAT = listOf(
        ".....KKKKKK.....",
        "....KWWWWWWK....",
        "...KWWWWWWWWK...",
        "...KWwWWwWWwK...",
    )
    private val CROWN = listOf(
        "....G..GG..G....",
        "....GGgYYgGG....",
        "....gggggggg....",
    )

    /** 머리와 겹치지 않게 오른쪽 위로 뻗는다. */
    private val SPOON_WOOD = listOf("..o..", ".ooo.", ".oOo.", "..o..", "..O..", ".O...", ".O...", "O....", "O....")
    private val SPOON_METAL = listOf("..mm.", ".mmmm", ".mhmm", ".mmmm", "..mm.", "..m..", ".m...", ".m...", "m....", "m....")

    /** 전설의 반짝임 — 세 무리가 번갈아 꺼진다. 좌표는 캔버스 기준(흔들림 오프셋 없음). */
    private val SPARKS: List<List<Pair<Int, Int>>> = listOf(
        listOf(2 to 2, 1 to 3, 2 to 3, 3 to 3, 2 to 4),
        listOf(18 to 16, 17 to 17, 18 to 17, 19 to 17, 18 to 18),
        listOf(17 to 2),
    )

    private data class Layer(val grid: List<String>, val x: Int, val y: Int, val map: Map<Char, Color>? = null)

    private fun layersFor(lv: Int): List<Layer> {
        val tool = if (lv == 1) {
            Layer(SPOON_WOOD, 14, 8)
        } else {
            Layer(SPOON_METAL, 14, 6, if (lv == 2) STEEL else if (lv == 3) SILVER else GOLD)
        }
        val layers = mutableListOf(tool, Layer(BODY, 1, 4))
        if (lv >= 2) layers += Layer(APRON, 1, 13)
        if (lv == 3) layers += Layer(CHEF_HAT, 1, 0)
        if (lv == 4) layers += Layer(CROWN, 1, 1)
        return layers
    }

    /**
     * 한 프레임을 그린다. `scale`은 논리 픽셀 하나의 실제 픽셀 크기.
     * `frame`이 홀수면 한 칸 위로 떠 있다 — 대기 애니메이션.
     */
    fun DrawScope.drawHost(lv: Int, frame: Int, scale: Float) {
        val bob = if (frame % 2 == 1) -1 else 0
        val px = Size(scale, scale)
        for ((grid, x, y, map) in layersFor(lv)) {
            grid.forEachIndexed { ry, row ->
                row.forEachIndexed { rx, ch ->
                    if (ch == '.') return@forEachIndexed
                    val color = map?.get(ch) ?: PAL[ch] ?: return@forEachIndexed
                    drawRect(color, Offset((x + rx) * scale, (y + ry + 1 + bob) * scale), px)
                }
            }
        }
        if (lv == 4) {
            SPARKS.forEachIndexed { k, group ->
                if ((frame + k) % 3 == 2) return@forEachIndexed
                val color = if (k == 2) PAL.getValue('Y') else PAL.getValue('G')
                for ((sx, sy) in group) drawRect(color, Offset(sx * scale, (sy + bob) * scale), px)
            }
        }
    }
}
