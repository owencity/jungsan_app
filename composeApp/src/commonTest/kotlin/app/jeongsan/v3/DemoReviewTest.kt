package app.jeongsan.v3

import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.AppAuth
import app.jeongsan.v3.api.AppLinks
import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.V3Gateway
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DemoReviewTest {
    private fun store() = V3Store(DemoFixtures.state(), awardSpoons = false)

    @Test fun 체험은_가상3명과_응답중_입금중_완료_시나리오를_제공한다() {
        val state = DemoFixtures.state()
        assertEquals(3, state.users.size)
        assertTrue(state.me.needsName)
        assertEquals(listOf(GatheringStatus.OPEN, GatheringStatus.SETTLING, GatheringStatus.COMPLETED),
            state.rooms.values.map { it.status })
        assertTrue(state.users.all { it.spoonCount == 0 })
        assertEquals("0000000000", state.users.first().payout?.accountNo)
        assertTrue(state.rooms.values.all { it.participants.size == 3 })
    }

    @Test fun 실명입력과_참여자응답으로_정산한뒤_입금을_확인할수있다() = runTest {
        val store = store()
        val gateway = V3Gateway(store, null, isDemoMode = true)
        assertNull(gateway.confirmName("체험총무"))
        assertFalse(store.state.me.needsName)
        store.actAs(3)
        assertNull(gateway.respond(101, mapOf(1010L to ResponseType.SOBER)))
        assertEquals(GatheringStatus.OPEN, store.state.rooms.getValue(101).status)
        store.actAs(1)
        assertEquals(SettleResult.OK, store.settle(101, store.state.rooms.getValue(101).inputRevision))
        val settled = store.state.rooms.getValue(101)
        assertEquals(GatheringStatus.SETTLING, settled.status)
        assertEquals(listOf(10_000L, 10_000L), settled.transfers.map { it.amount })
        for (transfer in settled.transfers) {
            val sender = settled.participants.first { it.id == transfer.fromParticipantId }
            store.actAs(checkNotNull(sender.userId))
            assertNull(gateway.markSent(101, transfer.id))
            store.actAs(1)
            assertNull(gateway.confirmIncoming(101, transfer.id))
        }
        assertEquals(GatheringStatus.COMPLETED, store.state.rooms.getValue(101).status)
        assertTrue(store.state.rooms.getValue(101).participants.all { it.spoonCount == 0 })
    }

    @Test fun 체험에서도_신고자개인숨김과_서로다른2명신고_전체숨김을_확인한다() = runTest {
        val store = store()
        val gateway = V3Gateway(store, null, isDemoMode = true)
        assertNull(gateway.reportMessage(101, 3, ReportReason.SPAM))
        fun visible(userId: Long) = store.state.rooms.getValue(101).visibleTimeline(userId, store.state.moderation).any { it.id == 3L }
        assertFalse(visible(1))
        assertTrue(visible(2))
        store.actAs(2)
        assertNull(gateway.reportMessage(101, 3, ReportReason.SPAM))
        assertFalse(visible(3))
    }

    @Test fun 차단은_메시지만_숨기며_명단과금액을_유지하고_해제할수있다() = runTest {
        val store = store()
        val gateway = V3Gateway(store, null, isDemoMode = true)
        val before = store.state.rooms.getValue(101)
        assertNull(gateway.blockParticipant(101, 1013))
        assertFalse(before.visibleTimeline(1, store.state.moderation).any { it.id == 3L })
        assertEquals(before.participants, store.state.rooms.getValue(101).participants)
        assertEquals(before.rounds, store.state.rooms.getValue(101).rounds)
        assertNull(gateway.unblock(3))
        assertTrue(before.visibleTimeline(1, store.state.moderation).any { it.id == 3L })
    }

    @Test fun 체험메시지도_빈값_길이_금칙어와_정규화를_검사한다() = runTest {
        val store = store()
        val gateway = V3Gateway(store, null, isDemoMode = true)
        val before = store.state.rooms.getValue(101).timeline.size
        listOf(" ", "a".repeat(201), "f.u.c.k", "ＦＵＣＫ").forEach { assertNotNull(gateway.sendMessage(101, it)) }
        assertEquals(before, store.state.rooms.getValue(101).timeline.size)
        assertNull(gateway.sendMessage(101, "안녕하세요"))
        assertEquals(before + 1, store.state.rooms.getValue(101).timeline.size)
    }

    @Test fun 데모를_실제API에_연결할수없고_체험자료삭제는_실제토큰을_건드리지않는다() = runTest {
        val tokens = MemoryTokenStore("real-session-token")
        val client = ApiClient("https://api.test", tokens, MockEngine { error("데모에서 실제 요청 금지") })
        val store = store()
        assertFailsWith<IllegalArgumentException> { V3Gateway(store, client, isDemoMode = true) }
        val gateway = V3Gateway(store, null, isDemoMode = true)
        assertNotNull(gateway.giveSpoon(102))
        assertNull(gateway.deleteAccount())
        assertTrue(store.state.rooms.isEmpty())
        assertEquals("real-session-token", tokens.get())
        assertEquals(3, DemoFixtures.state().rooms.size)
    }

    @Test fun 데모링크는_정확한주소만_허용하고_로그인티켓으로_취급하지않는다() {
        assertTrue(AppLinks.isDemoLink("jeongsan://demo"))
        assertTrue(AppLinks.isDemoLink("jeongsan://demo/"))
        listOf("jeongsan://demo.evil", "jeongsan://demo?ticket=x", "https://demo", "jeongsan://auth?ticket=x")
            .forEach { assertFalse(AppLinks.isDemoLink(it)) }
        val before = AppLinks.demoRequest
        AppLinks.handle("jeongsan://demo")
        assertEquals(before + 1, AppLinks.demoRequest)
        assertNull(AppAuth.ticketOf("jeongsan://demo"))
    }
}
