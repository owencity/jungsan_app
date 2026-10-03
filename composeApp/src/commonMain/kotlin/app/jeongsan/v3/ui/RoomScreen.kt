package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.ActionKind
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.Participant
import app.jeongsan.v3.TimelineType
import app.jeongsan.v3.TransferStatus
import app.jeongsan.v3.hasResponded
import app.jeongsan.v3.host
import app.jeongsan.v3.nameOf
import app.jeongsan.v3.nextAction
import app.jeongsan.v3.participantOfUser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * R1 정산방 — 웹 `RoomPage.tsx`. 맨 위 "지금 할 일" 한 줄과 맨 아래 버튼 하나가 짝이다.
 * 나머지(참여자 상태·차수·받을 돈·타임라인)는 그 할 일의 맥락이다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomScreen(
    devBar: @Composable () -> Unit,
    g: Gathering,
    meUserId: Id,
    onBack: () -> Unit,
    onAction: (ActionKind) -> Unit,
    onEditRound: (Id) -> Unit,
    onAddRound: () -> Unit,
    onSend: (String) -> Unit,
    onConfirm: (Id) -> Unit,
    onNotReceived: (Id) -> Unit,
    /** R4 — 총무가 한 사람의 차수 면제를 켜고 끈다 (참여자 id, 차수 id, 면제) */
    onExempt: (Id, Id, Boolean) -> Unit,
    /** R4 — 총무가 정산 전 한 사람을 내보낸다 */
    onRemove: (Id) -> Unit,
) {
    var managing by remember { mutableStateOf<Id?>(null) }
    val host = g.host()
    val me = g.participantOfUser(meUserId)
    val isHost = g.hostUserId == meUserId
    // 정산하기 이후엔 계산 입력이 고정이라 차수를 고칠 수 없다
    val canEditRounds = isHost && g.status == GatheringStatus.OPEN
    val act = nextAction(g, meUserId)
    val incoming = if (me == null) emptyList() else g.transfers.filter { it.toParticipantId == me.id && it.status != TransferStatus.CONFIRMED }

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle(g.title)
            if (g.status == GatheringStatus.COMPLETED) Chip("완료", ChipTone.DONE)
        },
        bottom = {
            if (me != null) ChatInput(onSend)
            act.action?.let { a -> CtaButton(a.label, accent = a.kind == ActionKind.GIVE_SPOON) { onAction(a.kind) } }
        },
    ) {
        // 총무 카드
        RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(JsColor.p50).border(2.dp, JsColor.line), contentAlignment = Alignment.Center) {
                    HostCharacter(host.spoonCount, pixel = 2.4.dp)
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(host.displayName, color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isHost) "  총무 · 나" else "  총무", color = JsColor.ink3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SpoonChip(host.spoonCount)
                }
            }
        }

        Banner(act.banner, act.tone, act.note)

        // 총무는 사람을 눌러 면제·내보내기(R4). 총무 자신은 관리 대상이 아니다
        People(g, onPick = if (isHost) ({ pid -> if (pid != host.id) managing = pid }) else null)
        if (isHost && g.rounds.isNotEmpty() && g.status == GatheringStatus.OPEN && g.participants.size > 1) {
            Text("사람을 누르면 차수별 면제·내보내기를 할 수 있어요", color = JsColor.ink3, fontSize = 12.sp)
        }
        managing?.let { pid ->
            ParticipantSheet(
                g = g,
                participantId = pid,
                onClose = { managing = null },
                onExempt = { rid, ex -> onExempt(pid, rid, ex) },
                onRemove = {
                    onRemove(pid)
                    managing = null
                },
            )
        }

        if (g.rounds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in g.rounds) {
                    RoundChip(r.label, r.total, if (canEditRounds) ({ onEditRound(r.id) }) else null)
                }
                if (canEditRounds) {
                    Text(
                        "+ 차수",
                        Modifier.background(Color.White).border(2.dp, JsColor.p500).clickable(onClick = onAddRound)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        color = JsColor.p600, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        if (incoming.isNotEmpty()) {
            Label("받을 돈")
            for (t in incoming) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.line).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${g.nameOf(t.fromParticipantId)}  ${won(t.amount)}", color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                t.status == TransferStatus.SENT -> "보냈대요"
                                t.notReceivedAt != null -> "다시 기다리는 중"
                                else -> "기다리는 중"
                            },
                            color = JsColor.ink3, fontSize = 11.5.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (t.status == TransferStatus.SENT) MiniButton("아직 안 들어왔어요") { onNotReceived(t.id) }
                        MiniButton("확인", filled = JsColor.ok) { onConfirm(t.id) }
                    }
                }
            }
        }

        Timeline(g, me?.id)
    }
}

@Composable
private fun RoundChip(label: String, total: Long, onClick: (() -> Unit)?) {
    Row(
        Modifier.background(Color.White).border(2.dp, JsColor.line)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(label, color = JsColor.p600, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
        Text(" ${commas(total)}", color = JsColor.ink, fontSize = 13.5.sp)
    }
}

/** 참여자 줄 — 정산 전엔 응답 여부, 정산 후엔 송금 상태를 점으로 */
@Composable
private fun People(g: Gathering, onPick: ((Id) -> Unit)? = null) {
    fun stateOf(p: Participant): Triple<String, Color, String> {
        if (g.status == GatheringStatus.OPEN) {
            return if (g.hasResponded(p.id)) Triple("●", JsColor.ok, "응답함") else Triple("○", JsColor.ink3, "아직")
        }
        val out = g.transfers.filter { it.fromParticipantId == p.id }
        return when {
            out.all { it.status == TransferStatus.CONFIRMED } -> Triple("✓", JsColor.ok, "완료")
            out.any { it.status == TransferStatus.SENT } -> Triple("…", JsColor.accentStrong, "보냈어요")
            else -> Triple("○", JsColor.ink3, "대기")
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in g.participants) {
            val (mark, color, label) = stateOf(p)
            // 총무만 누를 수 있다(본인 칸 제외)
            val pickable = onPick != null && p.userId != g.hostUserId
            Column(
                Modifier.widthIn(min = 46.dp)
                    .then(if (pickable) Modifier.clickable { onPick?.invoke(p.id) } else Modifier)
                    .padding(top = 4.dp)
                    .semantics {
                        contentDescription = if (pickable) "${p.displayName} 관리 · $label" else "${p.displayName} · $label"
                        if (pickable) role = Role.Button
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(Modifier.size(32.dp).background(JsColor.p100).border(2.dp, JsColor.ink), contentAlignment = Alignment.Center) {
                        Text(p.displayName.take(1), color = JsColor.p600, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).size(17.dp).background(Color.White).border(2.dp, color),
                        contentAlignment = Alignment.Center,
                    ) { Text(mark, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                }
                Text(
                    p.displayName, Modifier.padding(top = 3.dp).widthIn(max = 52.dp), color = JsColor.ink2, fontSize = 11.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun fmtTime(t: Instant, now: Instant = Clock.System.now()): String {
    val zone = TimeZone.currentSystemDefault()
    val d = t.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    if (d.date != today) return "${d.monthNumber}/${d.dayOfMonth}"
    val h12 = if (d.hour % 12 == 0) 12 else d.hour % 12
    return "${if (d.hour < 12) "오전" else "오후"} $h12:${d.minute.toString().padStart(2, '0')}"
}

/** 타임라인 — 상자 안에서만 스크롤하고, 새 소식이 오면 맨 아래로 */
@Composable
private fun Timeline(g: Gathering, meId: Id?) {
    val scroll = rememberScrollState()
    LaunchedEffect(g.timeline.size) { scroll.scrollTo(scroll.maxValue) }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).background(JsColor.p50).border(2.dp, JsColor.line)
            .verticalScroll(scroll).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (e in g.timeline) {
            if (e.type == TimelineType.MESSAGE) {
                val mine = e.authorParticipantId == meId
                Column(
                    Modifier.align(if (mine) Alignment.End else Alignment.Start).widthIn(max = 280.dp)
                        .background(if (mine) JsColor.p100 else Color.White)
                        .border(2.dp, if (mine) JsColor.p500 else JsColor.line).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    if (!mine) Text(g.nameOf(e.authorParticipantId ?: -1), color = JsColor.p700, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text(e.body, color = JsColor.ink, fontSize = 14.sp, lineHeight = 20.sp)
                    Text(fmtTime(e.createdAt), Modifier.align(Alignment.End), color = JsColor.ink3, fontSize = 10.5.sp)
                }
            } else {
                val spoon = e.type == TimelineType.SPOON
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${if (spoon) "🥄" else "⚙"} ${e.body}", Modifier.weight(1f),
                        color = if (spoon) JsColor.accentStrong else JsColor.ink2, fontSize = 12.5.sp,
                        fontWeight = if (spoon) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                    Text(fmtTime(e.createdAt), color = JsColor.ink3, fontSize = 10.5.sp)
                }
            }
        }
    }
}

/** 타임라인 한마디 입력. 키보드의 [보내기]로도 보낸다(앱다운 조작) */
@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        val t = text.trim()
        if (t.isNotEmpty()) {
            onSend(t.take(500))
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 500) text = it },
            singleLine = true,
            textStyle = TextStyle(color = JsColor.ink, fontSize = 14.sp),
            cursorBrush = SolidColor(JsColor.p600),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).background(Color.White, RectangleShape).border(2.dp, JsColor.line, RectangleShape)
                .padding(horizontal = 12.dp, vertical = 11.dp).semantics { contentDescription = "메시지" },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("한마디 남기기", color = JsColor.ink3, fontSize = 14.sp)
                    inner()
                }
            },
        )
        Text(
            "보내기",
            Modifier.background(Color.White).border(2.dp, if (text.isBlank()) JsColor.line else JsColor.ink)
                .clickable(enabled = text.isNotBlank()) { submit() }.padding(horizontal = 13.dp, vertical = 11.dp),
            color = if (text.isBlank()) JsColor.ink3 else JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}
