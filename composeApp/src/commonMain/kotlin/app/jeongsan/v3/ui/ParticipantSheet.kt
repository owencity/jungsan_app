package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.Features
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.label
import app.jeongsan.v3.nameWithNick
import app.jeongsan.v3.removeBlockedReason
import app.jeongsan.v3.responseOf

/**
 * R4 참여자 관리 시트(총무) — 웹 `ParticipantSheet.tsx`.
 *
 * 차수별 응답을 보고 [면제]를 켜고 끄고, 정산 전이면 [이 술자리에서 내보내기].
 * 면제를 끄면 그 칸은 빈칸(응답 전)으로 돌아간다 — flow-changes FC-010.
 *
 * 앱다운 조작: Material 바텀시트 — 아래로 끌어내리거나 시스템 뒤로가기로 닫힌다. 모서리는 레트로 규칙대로 각지게.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantSheet(
    g: Gathering,
    participantId: Id,
    onClose: () -> Unit,
    onExempt: (Id, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val p = g.participants.find { it.id == participantId } ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirm by remember { mutableStateOf(false) }
    val open = g.status == GatheringStatus.OPEN
    val blocked = removeBlockedReason(g, participantId)
    val paid = g.rounds.filter { it.payerParticipantId == participantId }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, shape = RectangleShape, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.nameWithNick(), Modifier.weight(1f), color = JsColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                if (paid.isNotEmpty()) Chip("${paid.joinToString("·") { it.label }} 낸 사람")
                Text("닫기", Modifier.clickable(onClick = onClose).padding(4.dp), color = JsColor.ink3, fontSize = 13.sp)
            }
            if (!open) {
                Text(
                    if (Features.SHOW_EXEMPT) "정산한 뒤에는 면제·내보내기를 바꿀 수 없어요." else "정산한 뒤에는 내보낼 수 없어요.",
                    color = JsColor.ink3, fontSize = 12.5.sp,
                )
            }

            Column(Modifier.fillMaxWidth().border(2.dp, JsColor.line)) {
                g.rounds.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = JsColor.line)
                    val cur = g.responseOf(participantId, r.id)
                    val exempt = cur?.type == ResponseType.EXEMPT
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, Modifier.width(40.dp), color = JsColor.p700, fontWeight = FontWeight.ExtraBold)
                        Text(cur?.type?.label ?: "아직 응답 안 함", Modifier.weight(1f), color = if (cur == null) JsColor.ink3 else JsColor.ink, fontSize = 13.5.sp)
                        if (Features.SHOW_EXEMPT) Text(
                            if (exempt) "면제 🎁" else "면제",
                            Modifier
                                .background(if (exempt) JsColor.accentBg else Color.White)
                                .border(2.dp, if (exempt) JsColor.accent else JsColor.line)
                                .clickable(enabled = open) { onExempt(r.id, !exempt) }
                                .semantics {
                                    role = Role.Switch
                                    toggleableState = ToggleableState(exempt)
                                    contentDescription = "${r.label} 면제"
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (exempt) JsColor.accentStrong else JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
            if (open && Features.SHOW_EXEMPT) Text("면제를 풀면 그 차수는 다시 응답을 받아요.", color = JsColor.ink3, fontSize = 12.sp)

            if (open) {
                if (blocked != null) {
                    Text(blocked, Modifier.fillMaxWidth(), color = JsColor.ink3, fontSize = 12.sp, textAlign = TextAlign.Center)
                } else {
                    Text(
                        if (confirm) "한 번 더 누르면 ${p.displayName}님을 내보내요" else "이 술자리에서 내보내기",
                        Modifier.align(Alignment.CenterHorizontally).clickable { if (confirm) onRemove() else confirm = true }
                            .semantics { role = Role.Button }.padding(8.dp),
                        color = if (confirm) JsColor.warn else JsColor.ink3, fontSize = 13.sp,
                        fontWeight = if (confirm) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
