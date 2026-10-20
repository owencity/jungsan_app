package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.ReportReason

/**
 * 메시지 신고·차단 시트(FC-024, App Store 1.2) — 웹 `MessageSheet.tsx`. 정산방 타임라인에서 남의 메시지를 길게 누르면 올라온다.
 * 처음엔 [신고하기]·[○○님 차단하기]. 신고는 사유를 하나 골라야 보내지고, 차단은 한 번 더 확인한다.
 * 차단은 메시지만 숨긴다 — 같은 술자리의 명단·금액·송금은 그대로라는 것을 확인 문구에 적는다. 뒤로 가기·아래로 끌기로 닫힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSheet(
    authorName: String,
    body: String,
    onClose: () -> Unit,
    onReport: (ReportReason) -> Unit,
    onBlock: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf("menu") }
    var reason by remember { mutableStateOf<ReportReason?>(null) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, shape = RectangleShape, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${authorName}님의 메시지", Modifier.weight(1f), color = JsColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("닫기", Modifier.clickable(onClick = onClose).padding(4.dp), color = JsColor.ink3, fontSize = 13.sp)
            }
            Text(
                body,
                Modifier.fillMaxWidth().heightIn(max = 120.dp).border(2.dp, JsColor.line).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                color = JsColor.ink, fontSize = 14.sp, lineHeight = 20.sp,
            )

            when (step) {
                "menu" -> {
                    SheetButton("신고하기", JsColor.ink) { step = "report" }
                    SheetButton("${authorName}님 차단하기", JsColor.warn) { step = "block" }
                }
                "report" -> {
                    Label("무엇이 문제인가요?")
                    for (row in ReportReason.entries.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in row) {
                                val on = reason == r
                                Text(
                                    r.label,
                                    Modifier.weight(1f).background(if (on) JsColor.ink else Color.White).border(2.dp, if (on) JsColor.ink else JsColor.line)
                                        .clickable { reason = r }.semantics { role = Role.RadioButton; selected = on }.padding(vertical = 11.dp),
                                    color = if (on) Color.White else JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Text("신고한 메시지는 나에게서 바로 사라지고, 운영자가 24시간 안에 확인해요.", color = JsColor.ink3, fontSize = 12.5.sp, lineHeight = 18.sp)
                    CtaButton("신고", enabled = reason != null) { reason?.let(onReport) }
                }
                else -> {
                    Text(
                        "차단하면 ${authorName}님의 메시지가 모든 술자리에서 나에게 보이지 않아요. 상대에게는 알리지 않아요. " +
                            "같은 술자리의 정산·금액·송금은 그대로예요. 내 계정 화면에서 언제든 풀 수 있어요.",
                        color = JsColor.ink2, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                    SheetButton("차단하기", JsColor.warn, filled = true, onClick = onBlock)
                }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().background(if (filled) color else Color.White).border(2.dp, color)
            .clickable(onClick = onClick).semantics { role = Role.Button }.padding(vertical = 13.dp),
        color = if (filled) Color.White else color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
    )
}
