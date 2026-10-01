package app.jeongsan.v3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.pixelart.HostSprite
import app.jeongsan.pixelart.HostSprite.drawHost
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.PixelRiverBackground
import app.jeongsan.ui.RetroSurface
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.SELF_CHOICES
import app.jeongsan.v3.Tone
import app.jeongsan.v3.label
import kotlinx.coroutines.delay

/**
 * v3 화면 공용 부품 — 웹 `v3/v3.css`·공용 컴포넌트와 같은 모양(각진 모서리, 번짐 없는 그림자).
 */

/**
 * v3 화면 틀. 위 바(뒤로가기·제목) + 스크롤되는 본문 + **바닥에 붙는 할 일 버튼 영역**.
 * 배경은 기존 강변 그대로(SCREENS.md §1-5).
 *
 * 웹과 다른 점(플랫폼답게): 상태바·홈 인디케이터를 피하고(safe area), 키보드가 올라오면 바닥
 * 영역이 키보드 위로 따라 올라간다(imePadding) — 타임라인 입력칸이 키보드에 가리지 않게.
 */
@Composable
fun V3Screen(
    devBar: @Composable () -> Unit,
    top: @Composable RowScope.() -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(JsColor.bg)) {
        PixelRiverBackground(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(JsColor.bg.copy(alpha = 0.22f)))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            devBar()
            Column(Modifier.fillMaxWidth().weight(1f).background(JsColor.surface)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = top,
                )
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                if (bottom != null) {
                    Column(
                        Modifier.fillMaxWidth().background(JsColor.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = bottom,
                    )
                }
            }
        }
    }
}

/** 화면 제목 — 위 바 안에서 남는 폭을 다 쓰고, 넘치면 말줄임 */
@Composable
fun RowScope.TopTitle(text: String) {
    Text(
        text, modifier = Modifier.weight(1f), color = JsColor.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** 뒤로가기 — 웹 `BackButton`과 같다. ‹ 기호만으로는 버튼인지 몰라서 글자로 쓴다(CTO 피드백) */
@Composable
fun BackButton(onClick: () -> Unit) {
    RetroSurface(
        modifier = Modifier.height(40.dp).clickable(onClick = onClick).semantics { role = Role.Button },
        shadowOffset = JsShape.shadowOffsetSmall,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = JsColor.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(" 뒤로가기", color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** 주 행동 버튼(바닥) — 웹 `.js-cta`. 스푼 버튼은 주황 */
@Composable
fun CtaButton(text: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    RetroSurface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button },
        background = if (accent) JsColor.accentStrong else JsColor.p600,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** 보조 버튼 — 웹 `.js-cta2` */
@Composable
fun SubButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Color.White, RectangleShape).border(2.dp, JsColor.line, RectangleShape)
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = JsColor.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** 작은 버튼 — 웹 `.js-mini`. `filled`면 진하게(선택됨·확인) */
@Composable
fun MiniButton(text: String, filled: Color? = null, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .background(filled ?: Color.White, RectangleShape)
            .border(2.dp, filled ?: JsColor.ink, RectangleShape)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        color = if (filled != null) Color.White else JsColor.ink,
        fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
    )
}

/** 작은 표시 — 역할(총무/참여)·완료·뱃지. 웹 `.js-role`·`.js-badge`·`.js-newbadge` */
enum class ChipTone { NEUTRAL, HOST, DONE, ALERT, AUTO }

@Composable
fun Chip(text: String, tone: ChipTone = ChipTone.NEUTRAL) {
    val (bg, fg, bd) = when (tone) {
        ChipTone.NEUTRAL -> Triple(Color.White, JsColor.ink2, JsColor.ink3)
        ChipTone.HOST -> Triple(JsColor.accentBg, JsColor.accentStrong, JsColor.accent)
        ChipTone.DONE -> Triple(JsColor.okBg, JsColor.ok, JsColor.ok)
        ChipTone.ALERT -> Triple(JsColor.accentStrong, Color.White, JsColor.ink)
        ChipTone.AUTO -> Triple(Color.White, JsColor.accentStrong, JsColor.accent)
    }
    Text(
        text,
        modifier = Modifier.background(bg, RectangleShape).border(2.dp, bd, RectangleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
    )
}

/** 할 일 배너 — 웹 `.js-banner` */
@Composable
fun Banner(text: String, tone: Tone, note: String? = null) {
    val (bg, fg, sub) = when (tone) {
        Tone.TODO -> Triple(JsColor.p600, Color.White, JsColor.p100)
        Tone.WAIT -> Triple(JsColor.p50, JsColor.ink, JsColor.ink2)
        Tone.DONE -> Triple(JsColor.okBg, JsColor.ok, JsColor.ok)
    }
    RetroSurface(Modifier.fillMaxWidth(), background = bg, border = if (tone == Tone.DONE) JsColor.ok else JsColor.ink, shadowOffset = JsShape.shadowOffsetSmall) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(text, color = fg, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
            if (note != null) Text(note, Modifier.padding(top = 3.dp), color = sub, fontSize = 12.5.sp)
        }
    }
}

/** 소제목 — 웹 `.js-lab` */
@Composable
fun Label(text: String) {
    Text(text, color = JsColor.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
}

/** 칭호 칩 — 🥄 1,280 · 프로 총무. 전설은 금색 */
@Composable
fun SpoonChip(spoons: Int) {
    val legend = HostSprite.levelOf(spoons) == 4
    Text(
        "🥄 ${commas(spoons.toLong())} · ${HostSprite.titleOf(spoons)}",
        modifier = Modifier
            .background(if (legend) Color(0xFFFFF6D6) else JsColor.accentBg, RectangleShape)
            .border(2.dp, if (legend) Color(0xFFF2B233) else JsColor.accent, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (legend) Color(0xFF8A5F00) else JsColor.accentStrong, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
    )
}

fun commas(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/**
 * 국자 총무 캐릭터 — 웹 `HostCharacter`. 0.46초마다 1px 위아래로 흔들린다.
 * `pixel`은 논리 픽셀 하나의 크기(dp). 정수 픽셀로 맞춰 그려 도트가 번지지 않게 한다.
 */
@Composable
fun HostCharacter(spoons: Int, pixel: Dp = 2.6.dp) {
    val lv = HostSprite.levelOf(spoons)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(lv) {
        while (true) {
            delay(460)
            frame++
        }
    }
    val scale = with(LocalDensity.current) { maxOf(1, pixel.roundToPx()).toFloat() }
    val w = with(LocalDensity.current) { (scale * HostSprite.W).toDp() }
    val h = with(LocalDensity.current) { (scale * HostSprite.H).toDp() }
    Canvas(Modifier.size(w, h).semantics { contentDescription = "${HostSprite.titleOf(spoons)} 캐릭터" }) {
        drawHost(lv, frame, scale)
    }
}

/** 한 차수의 응답 버튼 [불참][논알코올][알코올] — P2와 R3(총무 대리)가 같이 쓴다 */
@Composable
fun ResponseRow(value: ResponseType?, compact: Boolean = false, onChange: (ResponseType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in SELF_CHOICES) {
            val on = value == t
            val fill = when (t) {
                ResponseType.ABSENT -> JsColor.ink3
                ResponseType.SOBER -> JsColor.ok
                else -> JsColor.p600
            }
            Box(
                Modifier.weight(1f)
                    .background(if (on) fill else Color.White, RectangleShape)
                    .border(2.dp, if (on) JsColor.ink else JsColor.line, RectangleShape)
                    .clickable { onChange(t) }
                    .semantics { role = Role.RadioButton; selected = on }
                    .padding(vertical = if (compact) 8.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, color = if (on) Color.White else JsColor.ink2, fontSize = if (compact) 12.5.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * 개발용 바 — 목데이터 단계에서 "누구로 볼지"를 바꾼다(웹 `.js-dev.v3`와 같은 역할).
 * 같은 정산방을 총무·참여자 시점으로 번갈아 보며 배너·버튼이 어떻게 달라지는지 확인한다.
 * 백엔드가 붙으면 지운다.
 */
@Composable
fun DevBar(users: List<Pair<Long, String>>, current: Long, roleOf: (Long) -> String, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(JsColor.ink).horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("보는 사람", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        for ((id, name) in users) {
            val on = id == current
            val role = roleOf(id)
            Text(
                if (role.isEmpty()) name else "$name $role",
                modifier = Modifier
                    .background(if (on) JsColor.accent else Color.White.copy(alpha = 0.12f), RectangleShape)
                    .clickable { onPick(id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = if (on) JsColor.ink else Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}
