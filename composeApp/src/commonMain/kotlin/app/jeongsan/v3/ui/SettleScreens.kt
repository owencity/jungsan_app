package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.SELF_CHOICES
import app.jeongsan.v3.SettlePreview
import app.jeongsan.v3.SettleResult
import app.jeongsan.v3.host
import app.jeongsan.v3.isLockedByHost
import app.jeongsan.v3.label
import app.jeongsan.v3.nameOf
import app.jeongsan.v3.responseOf
import app.jeongsan.v3.unrespondedParticipants

/**
 * R3 정산하기 확인(총무) — 웹 `SettlePage.tsx`.
 *
 * 1인당 금액 미리보기(서버 결과 그대로) · 미응답자 경고 · [정산하기]. 미응답자 이름을 누르면 그
 * 자리에서 차수별 응답을 대신 넣을 수 있다 — 단톡방에서 "나 2차 안 갔어"라고 말만 한 사람을 위해.
 * SCREENS.md는 이 화면을 "시트"로 적었지만 내용이 길어 웹·앱 모두 한 화면으로 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettleScreen(
    devBar: @Composable () -> Unit,
    g: Gathering,
    preview: SettlePreview,
    onBack: () -> Unit,
    onRespondFor: (Id, Id, ResponseType) -> Unit,
    onSettle: (Int) -> SettleResult,
) {
    val host = g.host()
    val missing = g.unrespondedParticipants()
    var editing by remember { mutableStateOf<Id?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sum = preview.lines.sumOf { it.total }
    val roundsTotal = g.rounds.sumOf { it.total }

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle("정산하기")
        },
        bottom = {
            error?.let {
                Text(
                    it, Modifier.fillMaxWidth().background(JsColor.warnBg).border(2.dp, JsColor.warn).padding(10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                )
            }
            CtaButton("정산하기") {
                error = when (onSettle(preview.inputRevision)) {
                    SettleResult.STALE -> "그 사이 응답이 바뀌었어요. 바뀐 금액을 확인하고 다시 눌러주세요"
                    SettleResult.DENIED -> "지금은 정산할 수 없어요"
                    SettleResult.OK -> null
                }
            }
        },
    ) {
        if (missing.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(JsColor.accentBg).border(2.dp, JsColor.accent).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(missing.joinToString(", ") { it.displayName }, color = JsColor.accentStrong, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                Text("응답이 없어 전 차수 참석·알코올로 계산돼요. 이름을 누르면 대신 넣을 수 있어요.", color = JsColor.ink2, fontSize = 13.sp, lineHeight = 19.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in missing) {
                        MiniButton("${p.displayName} 응답 넣기", filled = if (editing == p.id) JsColor.ink else null) {
                            editing = if (editing == p.id) null else p.id
                        }
                    }
                }
            }
        }

        editing?.let { pid ->
            RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.nameOf(pid)}님 응답", Modifier.weight(1f), color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text("닫기", Modifier.clickable { editing = null }.padding(4.dp), color = JsColor.ink3, fontSize = 12.5.sp)
                    }
                    for (r in g.rounds) {
                        val cur = g.responseOf(pid, r.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.label, Modifier.width(40.dp), color = JsColor.p700, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                            if (cur?.type == ResponseType.EXEMPT) {
                                Text("면제", color = JsColor.accentStrong, fontWeight = FontWeight.Bold)
                            } else {
                                Column(Modifier.weight(1f)) { ResponseRow(cur?.type, compact = true) { t -> onRespondFor(pid, r.id, t) } }
                            }
                        }
                    }
                }
            }
        }

        Label("한 사람당 금액")
        Column(Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.ink)) {
            preview.lines.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = JsColor.line)
                val p = g.participants.first { it.id == l.participantId }
                val paid = g.rounds.any { it.payerParticipantId == p.id }
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.displayName, color = JsColor.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        if (p.id == host.id) Chip("총무", ChipTone.HOST)
                        if (paid) Chip("낸 사람")
                        if (l.auto) Chip("자동", ChipTone.AUTO)
                        Text(won(l.total), Modifier.weight(1f), color = JsColor.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                    }
                    Text(
                        l.rounds.joinToString(" · ") { b -> "${g.rounds.find { it.id == b.roundId }?.label} ${b.type.label}" },
                        Modifier.padding(top = 2.dp), color = JsColor.ink3, fontSize = 11.5.sp,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("합계 ", color = JsColor.ink2, fontSize = 13.sp)
            Text(won(sum), color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (sum != roundsTotal) Text(" · 차수 합계 ${won(roundsTotal)}와 달라요", color = JsColor.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("정산하면 금액이 고정돼요. 아무도 보내기 전이면 되돌릴 수 있어요.", Modifier.fillMaxWidth(), color = JsColor.ink3, fontSize = 12.5.sp, textAlign = TextAlign.Center)
    }
}

/**
 * P2 내 응답 — 웹 `RespondPage.tsx`. 차수마다 [불참][논알코올][알코올] 하나만 고르면 끝이다
 * (음료수 같은 추가 체크 없음 — CTO 결정 2026-10-01). 총무가 면제로 정한 칸은 잠긴다.
 */
@Composable
fun RespondScreen(
    devBar: @Composable () -> Unit,
    g: Gathering,
    meId: Id,
    onBack: () -> Unit,
    onSubmit: (Map<Id, ResponseType>) -> Unit,
) {
    val editable = g.rounds.filter { !g.responseOf(meId, it.id).isLockedByHost() }
    val draft = remember(g.id, meId) {
        mutableStateMapOf<Id, ResponseType>().apply {
            for (r in editable) g.responseOf(meId, r.id)?.type?.let { put(r.id, it) }
        }
    }
    val filled = editable.all { draft[it.id] != null }
    val already = editable.any { g.responseOf(meId, it.id)?.source == app.jeongsan.v3.ResponseSource.SELF }

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle(if (already) "응답 고치기" else "응답하기")
        },
        bottom = {
            if (!filled) Text("모든 차수를 골라야 저장할 수 있어요", Modifier.fillMaxWidth(), color = JsColor.ink3, fontSize = 12.sp, textAlign = TextAlign.Center)
            CtaButton("응답 완료", enabled = filled) { onSubmit(editable.associate { it.id to draft.getValue(it.id) }) }
        },
    ) {
        Text("차수마다 하나만 고르면 끝이에요.", color = JsColor.ink2, fontSize = 14.sp)

        if (editable.size > 1) {
            Label("전 차수 똑같이")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in SELF_CHOICES) MiniButton(t.label) { for (r in editable) draft[r.id] = t }
            }
        }

        for (r in g.rounds) {
            val locked = g.responseOf(meId, r.id).isLockedByHost()
            RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row {
                        Text(r.label, Modifier.weight(1f), color = JsColor.p700, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text(won(r.total), color = JsColor.ink3, fontSize = 13.sp)
                    }
                    if (locked) {
                        Text(
                            "총무가 면제로 지정했어요 🎁",
                            Modifier.fillMaxWidth().background(JsColor.accentBg).border(2.dp, JsColor.accent).padding(10.dp),
                            color = JsColor.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                    } else {
                        ResponseRow(draft[r.id]) { draft[r.id] = it }
                    }
                }
            }
        }
    }
}
