package app.jeongsan.v3

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.jeongsan.v3.api.ServerSettlementTable
import app.jeongsan.v3.api.responseLabel
import app.jeongsan.util.won

data class SettlementImageRow(val values: List<String>, val fullWidth: Boolean = false, val highlighted: Boolean = false)
data class SettlementImagePlan(val width: Int, val columnWidths: List<Int>, val rows: List<SettlementImageRow>)

fun ServerSettlementTable.canExportImage() = !isEstimate && status in setOf("SETTLING", "COMPLETED") && table.lines.isNotEmpty()

/** 화면용 행 분할을 사용하지 않는다. 전원 열과 서버의 표시 금액을 하나의 이미지에 보존한다. */
fun ServerSettlementTable.imagePlan(): SettlementImagePlan {
    require(canExportImage()) { "확정된 정산표만 저장할 수 있어요" }
    require(table.lines.size <= 50 && table.rounds.size <= 100 && table.transfers.size <= 1000) { "이미지가 너무 커요" }
    val width = maxOf(800, 380 + 150 * table.lines.size)
    val columns = listOf(200, 140) + List(table.lines.size) { (width - 380) / table.lines.size }
    fun money(value: Long) = won(value).removeSuffix("원")
    fun name(id: Long) = "${table.lines.find { it.participantId == id }?.name ?: "참여자"} #$id"
    val rows = buildList {
        add(SettlementImageRow(listOf("정산어택 · $title"), true, true))
        add(SettlementImageRow(listOf("금액 확정 · 정산방 #$settlementUnitId · $generatedAt 기준 · 단위: 원"), true))
        add(SettlementImageRow(listOf("차수·항목", "결제액") + table.lines.map { "${it.name}\n#${it.participantId}" }, highlighted = true))
        add(SettlementImageRow(listOf("최종 보낼 금액\n(받는 사람별 송금)", "—") + table.lines.map { person ->
            table.transfers.filter { it.fromParticipantId == person.participantId }
                .joinToString("\n\n") { "→ ${name(it.toParticipantId)}\n${won(it.amount)}" }
                .ifEmpty { "보낼 돈 없음" }
        }, highlighted = true))
        add(SettlementImageRow(listOf("아래는 계산 근거입니다 · 송금은 위 '최종 보낼 금액'을 확인해주세요"), true))
        table.rounds.forEach { round ->
            add(SettlementImageRow(listOf("${round.seq}차 · 결제자 ${name(round.payerParticipantId)} · ${won(round.total)}"), true, true))
            val cells = table.lines.map { p -> round.cells.find { it.participantId == p.participantId } }
            add(SettlementImageRow(listOf("참여", "—") + cells.map { it?.responseLabel() ?: "—" }))
            add(SettlementImageRow(listOf("안주·음료", money(round.foodTotal)) + cells.map { it?.food?.let(::money) ?: "—" }))
            add(SettlementImageRow(listOf("술", money(round.alcoholTotal)) + cells.map { it?.alcohol?.let(::money) ?: "—" }))
            add(SettlementImageRow(listOf("차수 부담 (조정 전)", "—") + cells.map { it?.amount?.let(::money) ?: "—" }))
        }
        table.adjustments.forEach { a -> add(SettlementImageRow(listOf("원 단위 조정\n${name(a.recipientParticipantId)} 결제분", "—") + table.lines.map { money(a.cells[it.participantId.toString()] ?: 0) })) }
        add(SettlementImageRow(listOf("전체 부담", money(table.grandTotal)) + table.lines.map { money(it.total) }, highlighted = true))
        add(SettlementImageRow(listOf("이미 결제한 금액", "—") + table.lines.map { money(it.paidTotal) }))
        add(SettlementImageRow(listOf("송금 안내 · 확정 당시 금액"), true, true))
        table.transfers.forEach { add(SettlementImageRow(listOf("${name(it.fromParticipantId)} → ${name(it.toParticipantId)} : ${won(it.amount)}"), true)) }
        if (table.transfers.isEmpty()) add(SettlementImageRow(listOf("보낼 금액이 없습니다."), true))
        add(SettlementImageRow(listOf("안주·음료는 함께 배분합니다. 표시 금액의 원 미만 차이는 원 단위 조정에 반영됩니다."), true))
        add(SettlementImageRow(listOf("이 이미지는 자동 갱신되지 않습니다. 입금 확인 여부는 정산방에서 확인해주세요."), true))
    }
    return SettlementImagePlan(width, columns, rows)
}

fun validateSettlementImageSize(width: Int, height: Int) {
    require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 12_000_000) { "이미지가 너무 커요. 전체 표에서 확인해주세요" }
}

enum class SettlementImageSaveResult { SAVED, HANDED_OFF, CANCELLED, FAILED }
expect fun encodeSettlementPng(bitmap: ImageBitmap): ByteArray
@Composable expect fun rememberSaveSettlementImage(): (ByteArray, String, (SettlementImageSaveResult) -> Unit) -> Unit
