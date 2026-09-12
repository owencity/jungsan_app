package app.jeongsan.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.Id
import app.jeongsan.domain.PaymentStatus
import app.jeongsan.domain.Settlement
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.MiniButton
import app.jeongsan.ui.PersonRow
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.ProgressBar
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.Tag
import app.jeongsan.ui.TagTone
import app.jeongsan.ui.WarnBox
import app.jeongsan.util.won

/**
 * H4 — 결과 / 입금 관리. 웹의 `host/ResultPage.tsx` 를 옮긴 것.
 *
 * 입금은 **양쪽이 누른다.** `SENT` 인데 `RECEIVED` 가 아닌 상태가 화면에 그대로
 * 보이면 "나 보냈는데?" vs "안 왔는데?" 분쟁이 대화가 아니라 화면에서 드러난다.
 *
 * ⚠ `joinRequest` 는 웹에서도 지금은 항상 "지원"으로 고정돼 있다(뒤늦은 참여
 * 요청 데이터가 아직 없어서다). 백엔드가 붙으면 실제 데이터로 바뀐다.
 */
@Composable
fun ResultScreen(
    g: Gathering,
    s: Settlement,
    onReopen: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    joinRequest: String? = null,
    onRemind: (() -> Unit)? = null,
    onDispute: ((participantId: Id, name: String) -> Unit)? = null,
    onMarkReceived: ((Id) -> Unit)? = null,
) {
    val payers = g.participants.filter { (s.breakdown[it.id]?.netAmount ?: 0) > 0 }
    val debtors = g.participants.filter { (s.breakdown[it.id]?.netAmount ?: 0) < 0 }
    val paid = debtors.count { it.paymentStatus == PaymentStatus.RECEIVED }
    val sent = debtors.find { it.paymentStatus == PaymentStatus.SENT }

    // 재정산으로 차액이 생긴 사람
    val diffs = g.participants
        .mapNotNull { p ->
            val paidAmount = p.paidAmount ?: return@mapNotNull null
            val diff = (s.amounts[p.id] ?: 0) - paidAmount
            if (diff == 0L) null else p to diff
        }

    Screen {
        JsBar("정산 완료", onBack = onBack, step = "확정됨")

        if (joinRequest != null) {
            WarnBox(
                title = "🔔 ${joinRequest}님이 뒤늦게 참여를 요청했어요",
                actions = listOf("되돌리고 추가" to onReopen, "무시" to {}),
            ) {
                Text("되돌리면 ${joinRequest}님이 참여하고 전원이 다시 계산됩니다.", color = JsColor.ink2, fontSize = 12.5.sp)
                Text(
                    "이미 입금한 ${paid}명에게 차액이 생깁니다.",
                    modifier = Modifier.padding(top = 3.dp), color = JsColor.ink2, fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        ProgressBar(value = paid, total = debtors.size, unit = "명 입금", ok = debtors.isNotEmpty() && paid >= debtors.size)

        payers.forEach { p ->
            PersonRow(
                name = p.name,
                isHost = p.isHost,
                sub = won(s.breakdown[p.id]?.paidTotal ?: 0) + " 결제" + if (p.id == s.mainPayerId) " · 대표결제자" else "",
                right = { Tag("받는 사람", TagTone.MUTED) },
            )
        }

        debtors.forEach { p ->
            PersonRow(
                name = p.name,
                isHost = p.isHost,
                sub = won(kotlin.math.abs(s.breakdown[p.id]?.netAmount ?: 0)),
                right = {
                    if (p.paymentStatus == PaymentStatus.RECEIVED) {
                        Tag("확인됨", TagTone.DONE)
                    } else {
                        Row {
                            Tag(if (p.paymentStatus == PaymentStatus.SENT) "보냈다고 함" else "미입금", if (p.paymentStatus == PaymentStatus.SENT) TagTone.WAIT else TagTone.ACCENT)
                            // 금액이 안 맞거나 안 들어왔을 때 **이 사람에게만** 물어본다.
                            // 단톡방에서 "누구누구 아직 안 냈어요" 하면 아무도 말을 안 꺼낸다.
                            if (onDispute != null) {
                                Spacer(Modifier.width(6.dp))
                                MiniButton("확인 요청", onClick = { onDispute(p.id, p.name) })
                            }
                        }
                    }
                },
            )
        }

        // **정산은 금액이 나왔다고 끝이 아니라 입금까지 돼야 끝난다.**
        if (onRemind != null && paid < debtors.size) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("아직 안 낸 ${debtors.size - paid}명에게 알림 보내기", onClick = onRemind)
        }

        if (diffs.isNotEmpty()) {
            SectionLabel("재정산 차액")
            diffs.forEach { (p, diff) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(p.name, modifier = Modifier.weight(1f), color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (diff > 0) "추가 ${won(diff)}" else "환불 ${won(-diff)}",
                        color = if (diff > 0) JsColor.accentStrong else JsColor.ok,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (sent != null && onMarkReceived != null) {
            Hint("보냈다고 함 상태의 ${sent.name}님을 확인하셨나요?")
            SecondaryButton("${sent.name} · 받았어요 확인", onClick = { onMarkReceived(sent.id) })
        }

        Spacer(Modifier.height(14.dp))
        Row {
            // 결과를 이미지로 저장하는 기능은 아직 없다 — 캡처·공유 파이프라인을
            // 따로 붙여야 해서 이번 단계에는 넣지 않았다.
            SecondaryButton("🖼 이미지로", modifier = Modifier.weight(1f), onClick = {})
            Spacer(Modifier.width(8.dp))
            PrimaryButton("단톡에 공유", modifier = Modifier.weight(1f), onClick = onShare)
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("되돌리기 (수정하기)", onClick = onReopen)
    }
}
