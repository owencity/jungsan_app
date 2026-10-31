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
    val people: List<TablePerson>, val rounds: List<TableRound> = emptyList(),
    val adjustments: List<TableAdjustment> = emptyList(), val transfers: List<TableTransfer> = emptyList(),
)

/** 좁은 휴대폰에서도 각 금액을 읽도록 사람과 세로 행을 함께 나눈다. 전체 금액은 페이지 합계가 아니다. */
fun TableData.capturePages(): List<TablePage> = buildList {
    val peoplePerPage = if (grandTotal >= 100_000_000) 1 else 2
    lines.chunked(peoplePerPage).forEach { people ->
        rounds.chunked(2).forEach { add(TablePage(people, rounds = it)) }
        adjustments.chunked(4).forEach { add(TablePage(people, adjustments = it)) }
        transfers.filter { t -> people.any { it.participantId == t.fromParticipantId } }
            .chunked(4).forEach { add(TablePage(people, transfers = it)) }
        if (rounds.isEmpty() && adjustments.isEmpty() && transfers.none { t -> people.any { it.participantId == t.fromParticipantId } }) {
            add(TablePage(people))
        }
    }
}

fun TableCell.responseLabel(): String {
    if (source == "UNANSWERED") return "미응답·예상"
    if (type == "ABSENT") return "불참"
    if (type == "EXEMPT") return "면제"
    val label = if (type == "DRANK") "알코올" else "논알코올"
    return label + when (source) { "AUTO" -> "·자동"; "HOST" -> "·총무"; else -> "" }
}
