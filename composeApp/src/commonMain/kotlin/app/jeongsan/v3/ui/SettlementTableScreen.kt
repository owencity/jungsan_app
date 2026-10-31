package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.util.won
import app.jeongsan.v3.api.*
import kotlinx.coroutines.CancellationException

/** 화면 캡처에 계좌나 토큰을 넣지 않는다. 송금 대상·금액은 서버 스냅샷을 그대로 표시한다. */
@Composable
fun SettlementTableScreen(roomId: Long, gateway: V3Gateway, onBack: () -> Unit) {
    var view by remember(roomId) { mutableStateOf<ServerSettlementTable?>(null) }
    var error by remember(roomId) { mutableStateOf<String?>(null) }
    var refresh by remember(roomId) { mutableIntStateOf(0) }
    var pageIndex by remember(roomId) { mutableIntStateOf(0) }
    var loading by remember(roomId) { mutableStateOf(true) }
    LaunchedEffect(roomId, refresh) {
        loading = true
        view = null // 재조회가 실패했는데 예전 금액을 최신 값처럼 보여주지 않는다.
        error = null
        try {
            view = gateway.settlementTable(roomId)
            pageIndex = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            error = when (e.code) {
                "SETTLEMENT_VIEW_UNAVAILABLE" -> "정산표를 확인할 수 없어요. 잠시 뒤 다시 시도해주세요"
                else -> V3Gateway.messageOf(e)
            }
        } catch (e: Exception) {
            error = "정산표를 읽지 못했어요. 잠시 뒤 다시 시도해주세요"
        } finally {
            loading = false
        }
    }
    V3Screen(devBar = {}, top = { BackButton(onBack); TopTitle("정산표") }) {
        if (!loading) SubButton("새로고침") { refresh++ }
        if (loading) Text("정산표를 불러오는 중이에요", color = JsColor.ink2)
        error?.let { Text(it, color = JsColor.ink2) }
        view?.let { current ->
            val pages = remember(current) { current.table.capturePages() }
            if (pages.isEmpty()) Text("표에 표시할 참여자가 없어요", color = JsColor.ink2)
            else {
                Text("각 페이지를 화면 캡처해 공유해주세요. 큰 글씨 설정에서는 아래 내용도 확인해주세요.", fontSize = 12.sp, color = JsColor.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (pageIndex > 0) MiniButton("이전") { pageIndex-- }
                    if (pageIndex < pages.lastIndex) MiniButton("다음") { pageIndex++ }
                }
                key(current, pageIndex) {
                    TableSheet(current, pages[pageIndex], pageIndex + 1, pages.size)
                }
            }
        }
    }
}

@Composable
private fun TableSheet(view: ServerSettlementTable, page: TablePage, index: Int, count: Int) {
    fun name(id: Long) = view.table.lines.find { it.participantId == id }?.let { "${it.name} #$id" } ?: "#$id"
    Column(Modifier.fillMaxWidth().background(JsColor.surface).border(2.dp, JsColor.line).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(view.title, color = JsColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("${if (view.isEstimate) "예상 · 송금 전 확인" else "확정"} · 정산방 #${view.settlementUnitId} · $index/$count 장", fontSize = 11.sp, color = JsColor.ink2)
        Text("조회 ${view.generatedAt}", fontSize = 10.sp, color = JsColor.ink3)
        TableRow("항목", page.people.map { "${it.name}\n#${it.participantId}" }, heading = true)
        page.rounds.forEach { round ->
            Text("${round.seq}차 · 결제 ${name(round.payerParticipantId)} · ${won(round.total)}", fontSize = 11.sp, color = JsColor.ink2)
            val cells = page.people.map { p -> round.cells.find { it.participantId == p.participantId } }
            TableRow("참석", cells.map { it?.responseLabel() ?: "—" })
            TableRow("술 제외", cells.map { it?.let { won(it.food) } ?: "—" })
            TableRow("술", cells.map { it?.let { won(it.alcohol) } ?: "—" })
        }
        page.adjustments.forEach { adjustment ->
            Text("${name(adjustment.recipientParticipantId)} 수취 기준 원 단위 조정", fontSize = 11.sp, color = JsColor.ink2)
            TableRow("조정", page.people.map { won(adjustment.cells[it.participantId.toString()] ?: 0L) })
        }
        page.transfers.forEach { transfer ->
            Text("${name(transfer.fromParticipantId)} → ${name(transfer.toParticipantId)}\n${won(transfer.amount)}", fontSize = 13.sp, color = JsColor.ink, fontWeight = FontWeight.Bold)
        }
        TableRow("최종 부담", page.people.map { won(it.total) }, heading = true)
        TableRow("이미 결제", page.people.map { won(it.paidTotal) })
        Text("전체 결제 합계 ${won(view.table.grandTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JsColor.ink)
        Text("최종 부담은 모든 차수·원 단위 조정을 포함해요. 이미 결제한 돈과 보낼 돈은 달라요. 다른 차수·조정·송금은 다른 장에 있어요.", fontSize = 10.sp, color = JsColor.ink3)
    }
}

@Composable
private fun TableRow(label: String, values: List<String>, heading: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (heading) JsColor.p50 else JsColor.surface)) {
        Text(label, Modifier.width(58.dp).fillMaxHeight().border(1.dp, JsColor.line).padding(4.dp), color = JsColor.ink, fontSize = 11.sp)
        values.forEach { value ->
            Text(value, Modifier.weight(1f).fillMaxHeight().border(1.dp, JsColor.line).padding(4.dp), color = JsColor.ink,
                fontSize = if (value.length > 15) 10.sp else 12.sp, fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
