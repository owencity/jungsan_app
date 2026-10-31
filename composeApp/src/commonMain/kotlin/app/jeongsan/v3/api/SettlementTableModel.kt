package app.jeongsan.v3.api

import kotlinx.serialization.Serializable

/** FC-028: 금액은 서버 표시값 그대로 사용한다. 합계나 반올림을 앱에서 다시 계산하지 않는다. */
@Serializable data class ServerSettlementTable(
    val gatheringId: Long, val settlementUnitId: Long, val title: String,
    val status: String, val isEstimate: Boolean, val generatedAt: String,
    val inputRevision: Long, val inputHash: String, val table: TableData,
)
@Serializable data class TableData(
    val lines: List<TablePerson>, val rounds: List<TableRound>,
    val adjustments: List<TableAdjustment>, val transfers: List<TableTransfer>, val grandTotal: Long,
)
@Serializable data class TablePerson(val participantId: Long, val name: String, val total: Long, val paidTotal: Long)
@Serializable data class TableRound(
    val roundId: Long, val seq: Int, val payerParticipantId: Long, val total: Long,
    val foodTotal: Long, val alcoholTotal: Long, val displayTotal: Long, val cells: List<TableCell>,
)
@Serializable data class TableCell(
    val participantId: Long, val type: String, val food: Long, val alcohol: Long,
    val amount: Long, val source: String,
)
@Serializable data class TableAdjustment(val recipientParticipantId: Long, val cells: Map<String, Long>)
@Serializable data class TableTransfer(val fromParticipantId: Long, val toParticipantId: Long, val amount: Long)

data class TablePage(
    val kind: TablePageKind, val people: List<TablePerson> = emptyList(), val rounds: List<TableRound> = emptyList(),
    val adjustments: List<TableAdjustment> = emptyList(), val transfers: List<TableTransfer> = emptyList(),
)

enum class TablePageKind { ROUND, ADJUSTMENT, SUMMARY, TRANSFER }

/** 인원은 세로로 늘리고 긴 캡처만 행으로 나눈다. 전체 화면은 Int.MAX_VALUE로 분할 없이 표시한다. */
fun TableData.capturePages(rowsPerPage: Int = 8): List<TablePage> = buildList {
    require(rowsPerPage > 0)
    val groups = lines.chunked(rowsPerPage)
    rounds.forEach { round -> groups.forEach { add(TablePage(TablePageKind.ROUND, it, rounds = listOf(round))) } }
    adjustments.forEach { adjustment -> groups.forEach { add(TablePage(TablePageKind.ADJUSTMENT, it, adjustments = listOf(adjustment))) } }
    groups.forEach { add(TablePage(TablePageKind.SUMMARY, it)) }
    transfers.chunked(rowsPerPage).forEach { add(TablePage(TablePageKind.TRANSFER, transfers = it)) }
}

fun TableCell.responseLabel(): String {
    if (source == "UNANSWERED") return "미응답·예상"
    if (type == "ABSENT") return "불참"
    if (type == "EXEMPT") return "면제"
    val label = if (type == "DRANK") "알코올" else "논알코올"
    return label + when (source) { "AUTO" -> "·자동"; "HOST" -> "·총무"; else -> "" }
}
