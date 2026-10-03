package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.AppNotification
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.Transfer
import app.jeongsan.v3.TransferStatus
import app.jeongsan.v3.label
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * P3 내 금액 — 웹 `PayPage.tsx`. 받는 사람마다 카드 한 장: 금액 · 근거(차수별) · 계좌 + [계좌 복사] ·
 * [보냈어요]. 금액과 근거는 서버가 준 값을 그대로 보여준다(앱은 계산하지 않는다).
 */
@Composable
fun PayScreen(
    devBar: @Composable () -> Unit,
    g: Gathering,
    meId: Id,
    onBack: () -> Unit,
    onSeen: () -> Unit,
    onSent: (Id) -> Unit,
) {
    val outgoing = g.transfers.filter { it.fromParticipantId == meId }
    // 열었을 때 한 번 — 목록의 [정산금액 확인] 뱃지를 뗀다
    LaunchedEffect(g.id, meId) { onSeen() }

    V3Screen(devBar = devBar, top = { BackButton(onBack); TopTitle("보낼 돈") }) {
        if (outgoing.isEmpty()) {
            Text(
                "보낼 돈이 없어요", Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.ink3).padding(18.dp),
                color = JsColor.ink2, textAlign = TextAlign.Center,
            )
        }
        for (t in outgoing) PayCard(g, t, onSent)
    }
}

@Composable
private fun PayCard(g: Gathering, t: Transfer, onSent: (Id) -> Unit) {
    val to = g.participants.find { it.id == t.toParticipantId }
    val payout = to?.payout
    var open by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val confirmed = t.status == TransferStatus.CONFIRMED

    RetroSurface(Modifier.fillMaxWidth().alpha(if (confirmed) 0.8f else 1f), shadowOffset = if (confirmed) 0.dp else JsShape.shadowOffsetSmall) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${to?.displayName}님께", Modifier.weight(1f), color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(won(t.amount), color = JsColor.p700, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }

            if (t.basis.isNotEmpty()) {
                Text(
                    if (open) "근거 접기 ▴" else "어떻게 나온 금액인가요? ▾",
                    Modifier.clickable { open = !open }.semantics { role = Role.Button },
                    color = JsColor.p600, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                )
                if (open) {
                    Column(Modifier.fillMaxWidth().background(JsColor.p50).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (b in t.basis) {
                            Row {
                                Text("${g.rounds.find { it.id == b.roundId }?.label} · ${b.type.label}", Modifier.weight(1f), color = JsColor.ink, fontSize = 13.sp)
                                Text(won(b.amount), color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (payout != null) {
                Row(
                    Modifier.fillMaxWidth().border(2.dp, JsColor.line).padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${payout.bank} · ${payout.holder}", color = JsColor.ink3, fontSize = 11.5.sp)
                        Text(payout.accountNo, color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    MiniButton(if (copied) "번호만 복사했어요" else "계좌 복사") {
                        // 숫자만 복사 — 이체 화면 계좌번호 칸에 하이픈·은행 이름이 섞이면 잘린다(PayoutRules.copyableAccountNo)
                        clipboard.setText(AnnotatedString(app.jeongsan.v3.copyableAccountNo(payout.accountNo)))
                        copied = true
                    }
                }
            } else {
                Text(
                    "${to?.displayName}님이 계좌를 등록하면 보여드릴게요",
                    Modifier.fillMaxWidth().border(2.dp, JsColor.line).padding(10.dp), color = JsColor.ink3, fontSize = 13.sp,
                )
            }

            if (t.status == TransferStatus.WAITING && t.notReceivedAt != null) {
                Text(
                    "${to?.displayName}님이 아직 입금을 확인 못 했대요. 보낸 내역을 확인해주세요",
                    Modifier.fillMaxWidth().background(JsColor.warnBg).padding(horizontal = 10.dp, vertical = 8.dp),
                    color = JsColor.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            when (t.status) {
                TransferStatus.WAITING -> CtaButton(if (t.notReceivedAt != null) "다시 보냈어요" else "보냈어요", enabled = payout != null) { onSent(t.id) }
                TransferStatus.SENT -> StateLine("확인 기다리는 중이에요", JsColor.ink2, JsColor.p50)
                TransferStatus.CONFIRMED -> StateLine("✓ ${to?.displayName}님이 확인했어요", JsColor.ok, JsColor.okBg)
            }
        }
    }
}

@Composable
private fun StateLine(text: String, fg: Color, bg: Color) {
    Text(text, Modifier.fillMaxWidth().background(bg).padding(11.dp), color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
}

fun ago(t: Instant, now: Instant = Clock.System.now()): String {
    val min = (now - t).inWholeMinutes
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        min < 60 * 24 -> "${(min + 30) / 60}시간 전"
        else -> "${(min + 720) / (60 * 24)}일 전"
    }
}

/** N1 알림함 — 웹 `NotificationsPage.tsx`. 누르면 읽음 처리하고 그 알림이 가리키는 화면으로 */
@Composable
fun NotificationsScreen(
    devBar: @Composable () -> Unit,
    items: List<AppNotification>,
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onReadAll: () -> Unit,
) {
    val unread = items.count { !it.read }
    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle("알림")
            if (unread > 0) Text("모두 읽음", Modifier.clickable(onClick = onReadAll).padding(4.dp), color = JsColor.ink3, fontSize = 13.sp)
        },
    ) {
        if (items.isEmpty()) {
            Text("아직 알림이 없어요", Modifier.fillMaxWidth().padding(18.dp), color = JsColor.ink2, textAlign = TextAlign.Center)
        } else {
            Column(Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.ink)) {
                items.forEachIndexed { i, n ->
                    if (i > 0) HorizontalDivider(color = JsColor.line)
                    Row(
                        Modifier.fillMaxWidth().background(if (n.read) Color.White else JsColor.p50).clickable { onOpen(n) }
                            .semantics { role = Role.Button }.padding(horizontal = 13.dp, vertical = 11.dp),
                    ) {
                        Box(Modifier.padding(top = 6.dp, end = 8.dp).size(6.dp).background(if (n.read) Color.Transparent else JsColor.accentStrong))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(n.title, color = if (n.read) JsColor.ink2 else JsColor.ink, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Bold else FontWeight.Black)
                            Text(n.body, color = JsColor.ink3, fontSize = 12.5.sp, lineHeight = 18.sp)
                            Text(ago(n.createdAt), color = JsColor.ink3, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
