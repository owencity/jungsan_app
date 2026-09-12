package app.jeongsan.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.attKey
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsCard
import app.jeongsan.ui.KvRow
import app.jeongsan.ui.PersonRow
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.ProgressBar
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.Tag
import app.jeongsan.ui.TagTone
import app.jeongsan.util.won

/**
 * H3 — 수집 현황. **사람 지정은 전부 여기서** 한다. 웹의 `host/CollectPage.tsx` 를 옮긴 것.
 *
 * 진행률의 분모는 `expectedCount`(본인 포함)이고 **계산 분모와는 무관하다.**
 */
@Composable
fun CollectScreen(
    g: Gathering,
    onConfirm: () -> Unit,
    onRoster: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    fun nameOf(id: Long) = g.participants.find { it.id == id }?.name ?: "?"
    val expected = g.expectedCount ?: g.participants.size

    fun summary(participantId: Long): String =
        g.rounds.joinToString(" / ") { r ->
            val a = g.attendance[attKey(participantId, r.id)]
            if (a?.attended != true) "${r.seq}차 불참" else "${r.seq}차 참석·${if (a.drank) "음주" else "논알콜"}"
        }

    Screen {
        JsBar(g.name, onBack = onBack, step = "수집 중")

        ProgressBar(value = g.participants.size, total = expected, unit = "명 참여", onEdit = {})

        g.participants.forEach { p ->
            PersonRow(
                name = p.name,
                isHost = p.isHost,
                off = !p.responded,
                sub = if (p.responded) summary(p.id) else "대신 체크하기 ›",
                right = {
                    when {
                        p.isHost -> Tag("자동 · 수정", TagTone.MUTED)
                        p.responded -> Tag("완료", TagTone.OK)
                        else -> Tag("미체크", TagTone.WARN)
                    }
                },
            )
        }

        SectionLabel("사람 지정")

        JsCard(title = "차수 결제자") {
            g.rounds.forEach { r -> KvRow(r.label, nameOf(r.payerId)) }
        }

        g.extras.forEach { e ->
            JsCard(modifier = Modifier, title = "${e.label} ${won(e.amount)}") {
                KvRow("결제", nameOf(e.payerId))
                KvRow("부담할 사람", e.bearerIds.joinToString(" · ") { nameOf(it) })
            }
        }

        JsCard(title = "면제자", right = "지정") {
            val exempt = g.participants.filter { it.exempt }
            KvRow(
                if (exempt.isNotEmpty()) exempt.joinToString(" · ") { it.name } else "없음 · 부담 0이 되고 분모에서 빠집니다",
                sub = true,
            )
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton("지금 확정하기", onClick = onConfirm)
        Spacer(Modifier.height(9.dp))
        Row {
            SecondaryButton("명단", modifier = Modifier.weight(1f), onClick = onRoster)
            Spacer(Modifier.width(8.dp))
            SecondaryButton("링크 다시 공유", modifier = Modifier.weight(1f), onClick = onShare)
        }
    }
}
