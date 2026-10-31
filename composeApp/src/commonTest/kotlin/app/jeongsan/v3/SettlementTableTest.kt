package app.jeongsan.v3

import app.jeongsan.v3.api.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class SettlementTableTest {
    private val response = """{"gatheringId":91,"settlementUnitId":42,"title":"금요일","status":"SETTLED","isEstimate":false,"generatedAt":"2026-10-31T12:00:00Z","inputRevision":3,"inputHash":"hash","table":{"lines":[{"participantId":1,"name":"김하나","total":334,"paidTotal":1000},{"participantId":2,"name":"김하나","total":333,"paidTotal":0}],"rounds":[{"roundId":10,"seq":1,"payerParticipantId":1,"total":1000,"foodTotal":1000,"alcoholTotal":0,"displayTotal":999,"cells":[{"participantId":1,"type":"SOBER","food":333,"alcohol":0,"amount":333,"source":"SELF"},{"participantId":2,"type":"SOBER","food":333,"alcohol":0,"amount":333,"source":"AUTO"}]}],"adjustments":[{"recipientParticipantId":1,"cells":{"1":1,"2":0}}],"transfers":[{"fromParticipantId":2,"toParticipantId":1,"amount":333}],"grandTotal":1000}}"""
    private fun data() = Json.decodeFromString<ServerSettlementTable>(response).table

    @Test fun 서버_정수금액과_동명이인_ID_원단위조정_송금을_그대로_읽는다() {
        val table = data()
        assertEquals(listOf(1L, 2L), table.lines.map { it.participantId })
        assertEquals(334L, table.lines.first().total)
        assertEquals(333L, table.rounds.single().cells.first().amount)
        assertEquals(1L, table.adjustments.single().cells["1"])
        assertEquals(333L, table.transfers.single().amount)
    }

    @Test fun 모든_사람과_차수_조정_송금을_페이지에서_누락하지_않는다() {
        val original = data()
        val table = original.copy(
            lines = (1L..25L).map { TablePerson(it, "김하나", 100, 0) },
            rounds = (1L..5L).map { original.rounds.single().copy(roundId = it) },
            adjustments = (1L..5L).map { TableAdjustment(it, mapOf("1" to -1)) },
            transfers = (1L..7L).flatMap { from -> (8L..12L).map { TableTransfer(from, it, 10) } },
        )
        val pages = table.capturePages()
        assertTrue(pages.all { it.people.size <= 8 && it.rounds.size <= 1 && it.adjustments.size <= 1 && it.transfers.size <= 8 })
        table.lines.forEach { person ->
            val mine = pages.filter { page -> page.people.any { it.participantId == person.participantId } }
            assertEquals(table.rounds, mine.flatMap { it.rounds })
            assertEquals(table.adjustments, mine.flatMap { it.adjustments })
        }
        assertEquals(table.transfers, pages.flatMap { it.transfers })
        assertEquals(table.lines, pages.filter { it.kind == TablePageKind.SUMMARY }.flatMap { it.people })
        table.rounds.forEach { round ->
            assertEquals(table.lines, pages.filter { it.rounds.contains(round) }.flatMap { it.people })
        }
        assertEquals(25, table.capturePages(Int.MAX_VALUE).first().people.size)
    }

    @Test fun 금액이_길어도_사람을_열로_나누지_않고_빈_차수도_최종금액은_보인다() {
        val table = data().copy(grandTotal = 100_000_000, rounds = emptyList(), adjustments = emptyList(), transfers = emptyList())
        val pages = table.capturePages()
        assertEquals(1, pages.size)
        assertEquals(TablePageKind.SUMMARY, pages.single().kind)
        assertEquals(table.lines, pages.single().people)
    }

    @Test fun 열명은_차수별로_8행과_2행으로_나누고_마지막에_전체정산을_둔다() {
        val table = data().copy(lines = (1L..10L).map { TablePerson(it, "김하나", 100, 0) }, adjustments = emptyList(), transfers = emptyList())
        val pages = table.capturePages()
        assertEquals(listOf(8, 2, 8, 2), pages.map { it.people.size })
        assertEquals(listOf(TablePageKind.ROUND, TablePageKind.ROUND, TablePageKind.SUMMARY, TablePageKind.SUMMARY), pages.map { it.kind })
    }

    @Test fun 미응답과_자동응답을_본인_응답처럼_보이지_않는다() {
        val cell = data().rounds.single().cells.first()
        assertEquals("미응답·예상", cell.copy(source = "UNANSWERED").responseLabel())
        assertEquals("논알코올·자동", cell.copy(source = "AUTO").responseLabel())
        assertEquals("불참", cell.copy(type = "ABSENT").responseLabel())
        assertEquals("면제", cell.copy(type = "EXEMPT").responseLabel())
    }

    @Test fun 총무의_인증과_술자리_정산단위_경로로_표를_조회한다() = runTest {
        val engine = MockEngine { req ->
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("/api/v1/gatherings/91/settlement-units/42/settlement/table", req.url.encodedPath)
            assertEquals("Bearer host-token", req.headers[HttpHeaders.Authorization])
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = ApiClient("https://api.test", MemoryTokenStore("host-token"), engine).settlementTable(91, 42)
        assertFalse(result.isEstimate)
        assertEquals(42L, result.settlementUnitId)
    }

    @Test fun 총무_권한_오류를_빈_정산표로_숨기지_않는다() = runTest {
        val engine = MockEngine { respond("""{"code":"FORBIDDEN","message":"총무만 가능"}""", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json")) }
        val error = assertFailsWith<ApiError> { ApiClient("https://api.test", MemoryTokenStore("t"), engine).settlementTable(91, 42) }
        assertEquals(403, error.status)
    }
}
