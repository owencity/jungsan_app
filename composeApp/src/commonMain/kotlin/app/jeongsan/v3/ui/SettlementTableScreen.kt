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
    var capture by remember(roomId) { mutableStateOf(false) }
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
            key(current) { SettlementImageButton(current) }
            val pages = remember(current, capture) { current.table.capturePages(if (capture) 8 else Int.MAX_VALUE) }
            if (pages.isEmpty()) Text("표에 표시할 참여자가 없어요", color = JsColor.ink2)
            else {
                SubButton(if (capture) "전체 표로 보기" else "캡처용으로 보기") { capture = !capture; pageIndex = 0 }
                if (capture) {
                    Text("긴 표는 8행씩 나눴어요. 각 장을 화면 캡처해주세요. 큰 글씨에서는 아래 내용도 확인해주세요.", fontSize = 12.sp, color = JsColor.ink3)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pageIndex > 0) MiniButton("이전") { pageIndex-- }
                        if (pageIndex < pages.lastIndex) MiniButton("다음") { pageIndex++ }
                    }
                    key(current, pageIndex) { TableSheet(current, pages[pageIndex], "${pageIndex + 1}/${pages.size} 장") }
                } else {
                    pages.forEach { TableSheet(current, it, "전체 보기") }
                }
            }
        }
    }
}

@Composable
private fun TableSheet(view: ServerSettlementTable, page: TablePage, pageLabel: String) {
    fun name(id: Long) = view.table.lines.find { it.participantId == id }?.let { "${it.name} #$id" } ?: "#$id"
    Column(Modifier.fillMaxWidth().background(JsColor.surface).border(2.dp, JsColor.line).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(view.title, color = JsColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("${if (view.isEstimate) "예상 · 송금 전 확인" else "확정"} · 정산방 #${view.settlementUnitId} · $pageLabel", fontSize = 11.sp, color = JsColor.ink2)
        Text("조회 ${view.generatedAt}", fontSize = 10.sp, color = JsColor.ink3)
        page.rounds.forEach { round ->
            Text("${round.seq}차 · 결제 ${name(round.payerParticipantId)} · ${won(round.total)}", fontSize = 11.sp, color = JsColor.ink2)
            TableRow(listOf("이름", "참여", "안주·음료", "술", "차수 부담"), heading = true)
            page.people.forEach { person ->
                val cell = round.cells.find { it.participantId == person.participantId }
                TableRow(listOf("${person.name}\n#${person.participantId}", cell?.responseLabel() ?: "—",
                    cell?.food?.let(::tableMoney) ?: "—", cell?.alcohol?.let(::tableMoney) ?: "—", cell?.amount?.let(::tableMoney) ?: "—"))
            }
        }
        page.adjustments.forEach { adjustment ->
            Text("${name(adjustment.recipientParticipantId)} 수취 기준 원 단위 조정", fontSize = 11.sp, color = JsColor.ink2)
            TableRow(listOf("이름", "조정액"), heading = true)
            page.people.forEach { TableRow(listOf(name(it.participantId), tableMoney(adjustment.cells[it.participantId.toString()] ?: 0L))) }
        }
        if (page.kind == TablePageKind.SUMMARY) {
            Text("전체 정산", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = JsColor.ink)
            TableRow(listOf("이름", "전체 부담", "이미 결제"), heading = true)
            page.people.forEach { TableRow(listOf(name(it.participantId), tableMoney(it.total), tableMoney(it.paidTotal))) }
        }
        if (page.kind == TablePageKind.TRANSFER) {
            Text("송금 안내", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = JsColor.ink)
            TableRow(listOf("보내는 사람", "받는 사람", "금액"), heading = true)
        }
        page.transfers.forEach { transfer ->
            TableRow(listOf(name(transfer.fromParticipantId), name(transfer.toParticipantId), tableMoney(transfer.amount)))
        }
        Text("전체 결제 합계 ${won(view.table.grandTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JsColor.ink)
        Text("단위: 원 · 차수 부담은 표시용이며 최종 부담에는 원 단위 조정이 포함돼요. 보낼 돈은 송금 안내를 확인해주세요.", fontSize = 10.sp, color = JsColor.ink3)
    }
}

@Composable
private fun TableRow(values: List<String>, heading: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (heading) JsColor.p50 else JsColor.surface)) {
        values.forEach { value ->
            Text(value, Modifier.weight(1f).fillMaxHeight().border(1.dp, JsColor.line).padding(horizontal = 2.dp, vertical = 5.dp), color = JsColor.ink,
                fontSize = if (values.size == 5 || value.length > 15) 10.sp else 12.sp, fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

private fun tableMoney(amount: Long) = won(amount).removeSuffix("원")
