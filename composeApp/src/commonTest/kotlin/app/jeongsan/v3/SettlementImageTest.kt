package app.jeongsan.v3

import app.jeongsan.v3.api.*
import kotlin.test.*

class SettlementImageTest {
    private fun view(count: Int = 25): ServerSettlementTable {
        val people = (1..count).map { TablePerson(it.toLong(), "김하나", 1000, if (it == 1) count * 1000L else 0) }
        val cells = people.map { TableCell(it.participantId, "SOBER", 1000, 0, 1000, "SELF") }
        return ServerSettlementTable(1, 2, "정산표", "SETTLING", false, "2026-10-31T00:00:00Z", 1, "hash",
            TableData(people, listOf(TableRound(3, 1, 1, count * 1000L, count * 1000L, 0, count * 1000L, cells)),
                listOf(TableAdjustment(1, mapOf("1" to -1L, "2" to 1L))), people.drop(1).map { TableTransfer(it.participantId, 1, 1000) }, count * 1000L))
    }
    @Test fun 모든_인원이_가로_한줄에_들어가고_동명이인_ID도_남는다() {
        val plan = view().imagePlan()
        assertEquals(27, plan.columnWidths.size)
        val header = plan.rows.first { !it.fullWidth }
        assertEquals((1..25).map { "김하나\n#$it" }, header.values.drop(2))
        assertTrue(plan.rows.filter { !it.fullWidth }.all { it.values.size == 27 })
        assertEquals(25, plan.rows.first { it.values.first() == "전체 부담" }.values.drop(2).size)
        assertEquals(24, plan.rows.count { it.fullWidth && it.values.single().contains("→") })
    }
    @Test fun 원단위조정과_서버금액을_계산하지_않고_그대로_그린다() {
        val plan = view().imagePlan()
        val adjustment = plan.rows.first { it.values.first().startsWith("원 단위 조정") }
        assertEquals(listOf("-1", "1", "0"), adjustment.values.drop(2).take(3))
        assertEquals("25,000", plan.rows.first { it.values.first() == "이미 결제한 금액" }.values[2])
    }
    @Test fun 최종송금은_상세차수보다_먼저_보이고_여러_수취인도_구분한다() {
        val original = view()
        val result = original.copy(table = original.table.copy(transfers = listOf(TableTransfer(2, 1, 700), TableTransfer(2, 3, 300))))
        val rows = result.imagePlan().rows
        val summaryIndex = rows.indexOfFirst { it.values.first().startsWith("최종 보낼 금액") }
        val roundIndex = rows.indexOfFirst { it.values.first().startsWith("1차") }
        assertTrue(summaryIndex < roundIndex)
        assertEquals("보낼 돈 없음", rows[summaryIndex].values[2])
        assertEquals("→ 김하나 #1\n700원\n\n→ 김하나 #3\n300원", rows[summaryIndex].values[3])
    }
    @Test fun 금액확정과_입금완료를_구분하고_예상표는_저장하지_않는다() {
        assertTrue(view().canExportImage())
        assertTrue(view().copy(status = "COMPLETED").canExportImage())
        assertFalse(view().copy(status = "OPEN", isEstimate = true).canExportImage())
        assertFailsWith<IllegalArgumentException> { view().copy(isEstimate = true).imagePlan() }
        assertFailsWith<IllegalArgumentException> { view(0).imagePlan() }
    }
    @Test fun 큰_이미지를_잘라내지_않고_할당전에_거절한다() {
        validateSettlementImageSize(4130, 1800)
        assertFailsWith<IllegalArgumentException> { validateSettlementImageSize(9000, 100) }
        assertFailsWith<IllegalArgumentException> { validateSettlementImageSize(4000, 4000) }
        assertFailsWith<IllegalArgumentException> { view(51).imagePlan() }
    }
}
