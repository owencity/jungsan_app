package app.jeongsan.v3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreScreenshotFixturesTest {
    @Test fun 응답현황과_차수인원을_혼동하지_않는_가상자료다() {
        val state = StoreScreenshotFixtures.state()
        val waiting = state.rooms.getValue(101)
        val ready = state.rooms.getValue(102)
        assertEquals("6 / 7명 응답", waiting.responseProgress())
        assertEquals(GatheringStatus.OPEN, waiting.status)
        assertEquals(listOf(6, 6, 5), waiting.rounds.map { it.expectedHeadcount })
        assertEquals(listOf(6, 6, 5), ready.rounds.map { ready.roundAttendeeCount(it.id) })
        assertEquals(7, ready.respondedCount())
        assertTrue(ready.transfers.isEmpty())
    }

    @Test fun 표의_부담금과_확정송금은_차수별_실제몫을_합한_금액이다() {
        val settled = StoreScreenshotFixtures.state().rooms.getValue(103)
        val table = StoreScreenshotFixtures.table(settled)
        assertFalse(table.isEstimate)
        assertEquals(242_000L, table.table.grandTotal)
        assertEquals(listOf(50_000L, 50_000L, 44_000L, 38_000L, 20_000L, 10_000L, 30_000L), table.table.lines.map { it.total })
        assertEquals(192_000L, table.table.transfers.sumOf { it.amount })
        assertTrue(table.table.transfers.all { it.toParticipantId == 1L })
        assertTrue(table.table.rounds.flatMap { it.cells }.all { it.food >= 0 && it.alcohol >= 0 && it.amount == it.food + it.alcohol })
        assertTrue(settled.participants.all { it.payout == null })
    }
}
