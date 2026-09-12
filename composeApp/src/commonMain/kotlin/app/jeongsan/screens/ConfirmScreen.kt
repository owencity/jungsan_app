package app.jeongsan.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.Settlement
import app.jeongsan.ui.FixedNotice
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.PersonRow
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.TransferRow
import app.jeongsan.ui.WarnBox
import app.jeongsan.util.won

/**
 * H3-b — 확정 미리보기. **주최자가 개입하는 유일한 시점.** 웹의 `host/ConfirmPage.tsx` 를 옮긴 것.
 *
 * 확정을 두 단계로 나눈 이유는 수정 비용을 사후 통보가 아니라 사전 고지로 옮기려는
 * 것이다. 경고는 **금액보다 먼저** 보이게 배치한다 — 금액을 먼저 보면 "이 정도면
 * 됐네" 하고 그냥 넘어간다. 경고가 **진행을 막지는 않는다.**
 */
@Composable
fun ConfirmScreen(
    g: Gathering,
    s: Settlement,
    onAccept: () -> Unit,
    onRoster: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    fun nameOf(id: Long) = g.participants.find { it.id == id }?.name ?: "?"
    val actual = g.participants.size
    val expected = g.expectedCount
    val short = expected != null && actual < expected
    val over = expected != null && actual > expected

    // 결제해서 받을 돈이 있는데 계좌를 등록하지 않은 사람
    val missingPayout = g.participants.filter { (s.breakdown[it.id]?.netAmount ?: 0) > 0 && it.payout == null }

    Screen {
        JsBar(
            "이대로 확정할까요?",
            onBack = onBack,
            step = if (short || over) "확인 필요" else null,
        )

        if (short) {
            WarnBox(
                title = "⚠ 등록한 인원과 다릅니다",
                actions = listOf("링크 다시 공유" to onShare, "${actual}명으로 확정" to onAccept),
                primaryActionIndex = 0,
            ) {
                Text("참여자 수 ${expected}명 · 실제 참여 ${actual}명", color = JsColor.ink2, fontSize = 12.5.sp)
                Text(
                    "아직 안 들어온 분이 있어요. 지금 확정하면 ${actual}명이 나눠 냅니다.",
                    modifier = Modifier.padding(top = 3.dp), color = JsColor.ink2, fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (over) {
            WarnBox(
                title = "⚠ 등록한 인원보다 많습니다",
                actions = listOf("명단 확인" to onRoster),
            ) {
                Text("참여자 수 ${expected}명 · 실제 참여 ${actual}명", color = JsColor.ink2, fontSize = 12.5.sp)
                Text(
                    "모르는 분이 들어왔을 수 있습니다. 링크에는 참석자 이름과 금액이 담겨 있습니다.",
                    modifier = Modifier.padding(top = 3.dp), color = JsColor.ink2, fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (missingPayout.isNotEmpty()) {
            Text(
                "💳 ${missingPayout.joinToString(" · ") { it.name }}님의 계좌가 아직 없어요. " +
                    "받을 돈이 있는데 계좌를 알 수 없습니다. 확정은 됩니다 — 나중에 등록하면 바로 보입니다.",
                color = JsColor.warn, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(won(s.grandTotal), color = JsColor.ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    "${g.rounds.size}차수 + 기타 ${g.extras.size}건",
                    modifier = Modifier.padding(top = 3.dp),
                    color = JsColor.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        g.participants.forEach { p ->
            PersonRow(
                name = p.name,
                isHost = p.isHost,
                sub = if (p.id == s.mainPayerId) "대표결제자" else if (p.exempt) "면제" else null,
                right = {
                    Text(won(s.amounts[p.id] ?: 0), color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                },
            )
        }

        SectionLabel("송금 목록")
        s.transfers.forEach { t ->
            TransferRow(from = nameOf(t.fromId), to = nameOf(t.toId), amount = won(t.amount))
        }

        if (s.roundingUnitDowngraded) {
            Hint("1인당 금액이 작아 100원 단위로는 계산할 수 없어 10원 단위로 정산했습니다.")
        }

        FixedNotice(
            unit = s.appliedRoundingUnit,
            extra = "수락하면 참여자에게 금액이 공개됩니다. 이후 수정하면 전원이 다시 계산되어 추가금이나 환불이 발생할 수 있습니다.",
        )

        Spacer(Modifier.height(12.dp))
        Row {
            SecondaryButton("다시 볼게요", modifier = Modifier.weight(1f), onClick = onBack)
            Spacer(Modifier.width(8.dp))
            PrimaryButton("수락", modifier = Modifier.weight(1f), onClick = onAccept)
        }
    }
}
