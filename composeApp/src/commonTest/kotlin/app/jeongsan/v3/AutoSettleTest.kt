package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 인원 입력 · 전원 응답 시 자동 정산 · 입금 요청(FC-020) — 웹 `autoSettle.test.ts`와 같은 케이스.
 * 총무가 [정산하기]를 누르지 않는다 — 넣어 둔 인원만큼 모두 응답한 순간 계산이 확정된다.
 */
class AutoSettleTest {
    private lateinit var s: V3Store
    private val open = 101L // 김동규 총무, 5명 중 최지영(4)·정민수(5)가 아직 응답 안 함
    private fun g() = s.state.rooms.getValue(open)
    private fun answerAll(userId: Long) {
        s.actAs(userId)
        s.respond(open, g().rounds.associate { it.id to ResponseType.DRANK })
    }

    @BeforeTest fun setUp() { s = V3Store() }

    @Test fun 넣어_둔_인원이_모두_응답하면_마지막_응답에_정산이_확정된다() {
        s.setHeadcount(open, 5)
        answerAll(4)
        assertEquals(GatheringStatus.OPEN, g().status) // 아직 한 명 남음
        answerAll(5)
        assertEquals(GatheringStatus.SETTLING, g().status)
        assertTrue(g().transfers.isNotEmpty())
        assertTrue(g().timeline.any { it.body.startsWith("모두 응답해서 자동으로 계산했어요") })
    }

    @Test fun 정산되면_총무에게_입금_요청_알림이_간다() {
        s.setHeadcount(open, 5)
        answerAll(4)
        answerAll(5)
        val host = g().host().userId
        assertTrue(s.state.notifications.any { it.userId == host && it.title.contains("입금 요청") })
    }

    @Test fun 인원보다_적게_모이면_모두_응답해도_정산하지_않는다() {
        s.setHeadcount(open, 6)
        answerAll(4)
        answerAll(5)
        assertEquals(GatheringStatus.OPEN, g().status)
    }

    @Test fun 인원을_넣지_않은_술자리는_총무가_정산할_때까지_기다린다() {
        answerAll(4)
        answerAll(5)
        assertEquals(GatheringStatus.OPEN, g().status)
    }

    @Test fun 이미_모두_응답한_뒤_인원을_맞추면_그때_정산된다() {
        answerAll(4)
        answerAll(5)
        s.actAs(1)
        s.setHeadcount(open, 5)
        assertEquals(GatheringStatus.SETTLING, g().status)
    }

    @Test fun 인원은_총무만_2명에서_50명_안에서만_바꾼다() {
        s.actAs(2)
        s.setHeadcount(open, 4)
        assertNull(g().headcount)
        s.actAs(1)
        s.setHeadcount(open, 99)
        assertEquals(50, g().headcount)
        s.setHeadcount(open, 1)
        assertEquals(2, g().headcount)
    }

    @Test fun 인원을_넣었으면_총무는_몇_명_응답했는지와_링크_공유를_본다() {
        s.setHeadcount(open, 5)
        val a = nextAction(g(), 1)
        assertEquals("5명 중 3명 응답했어요 · 다 모이면 자동으로 계산돼요", a.banner)
        assertEquals(ActionKind.SHARE, a.action?.kind)
        assertTrue(canSettleNow(g(), 1))
        assertFalse(canSettleNow(g(), 2))
    }

    @Test fun 참여자는_응답_뒤_다_모이면_자동으로_계산된다를_본다() {
        s.setHeadcount(open, 5)
        assertEquals("응답 완료! 다 모이면 자동으로 계산돼요", nextAction(g(), 2).banner)
    }

    @Test fun 계산이_끝나면_총무_할_일은_입금_요청_보내기() {
        s.setHeadcount(open, 5)
        answerAll(4)
        answerAll(5)
        val a = nextAction(g(), 1)
        assertEquals("계산 끝! 단톡방에 입금 요청을 보내주세요", a.banner)
        assertEquals(ActionKind.REQUEST_PAYMENT, a.action?.kind)
    }

    @Test fun 입금_요청_문구는_받는_사람별로_보낼_사람_금액_계좌를_담고_확인된_송금은_뺀다() {
        s.setHeadcount(open, 5)
        answerAll(4)
        answerAll(5)
        val room = g()
        val text = paymentRequestMessage(room, "https://x/jungsan/j/k7Qx2")
        assertTrue(text.startsWith("[정산어택] ${room.title} 계산 끝!"))
        for (t in room.transfers) assertTrue(text.contains("${room.nameOf(t.fromParticipantId)} "))
        assertTrue(text.contains(room.host().payout!!.accountNo))

        val first = room.transfers.first()
        val done = room.copy(transfers = room.transfers.map { if (it.id == first.id) it.copy(status = TransferStatus.CONFIRMED) else it })
        assertFalse(paymentRequestMessage(done, "u").contains("· ${room.nameOf(first.fromParticipantId)} "))
    }
}

/** 인원보다 더 들어오면(CTO 결정 2026-10-09) — 웹 autoSettle.test.ts 같은 이름 describe 와 같은 케이스 */
class OverflowTest {
    private lateinit var s: V3Store
    private val open = 101L
    private fun g() = s.state.rooms.getValue(open)
    private fun answerAll(userId: Long) {
        s.actAs(userId)
        s.respond(open, g().rounds.associate { it.id to ResponseType.DRANK })
    }

    @BeforeTest fun setUp() { s = V3Store() }

    @Test fun 총무에게_인원_확인과_모두_포함하기가_먼저_뜬다() {
        s.setHeadcount(open, 4)
        val a = nextAction(g(), 1)
        assertEquals("현재 5명이 참여했어요 · 인원(4명)이 맞는지 확인해주세요", a.banner)
        assertEquals("그대로면 4명으로 계산되고 정민수님은 빠져요", a.note)
        assertEquals(ActionKind.INCLUDE_EXTRA, a.action?.kind)
        assertEquals("5명 모두 포함하기", a.action?.label)
        assertFalse(canSettleNow(g(), 1))
    }

    @Test fun 링크로_인원보다_한_명_더_들어오는_순간_총무에게_알림이_한_번_간다() {
        s.setHeadcount(open, 5)
        s.actAs(6)
        s.joinGathering(g().shareToken, emptyMap())
        s.actAs(2)
        s.sendMessage(open, "안녕")
        val host = g().host().userId
        assertEquals(1, s.state.notifications.count { it.userId == host && it.title.startsWith("현재 6명이 참여했어요") })
    }

    @Test fun 그대로_두면_앞에서부터_인원만큼_응답했을_때_정산되고_뒤에_온_사람은_빠진다() {
        s.setHeadcount(open, 4)
        answerAll(4)
        assertEquals(GatheringStatus.SETTLING, g().status)
        assertFalse(g().participants.any { it.displayName == "정민수" })
        assertTrue(g().timeline.any { it.body == "정민수님은 인원(4명) 밖이라 이번 정산에서 빠졌어요" })
        assertTrue(s.state.notifications.any { it.userId == 5L && it.body.contains("인원(4명) 밖이라") })
    }

    @Test fun 포함하기를_누르면_인원이_늘고_모두_응답해야_함께_정산된다() {
        s.setHeadcount(open, 4)
        s.setHeadcount(open, g().participants.size)
        answerAll(4)
        assertEquals(GatheringStatus.OPEN, g().status)
        answerAll(5)
        assertEquals(GatheringStatus.SETTLING, g().status)
        assertTrue(g().participants.any { it.displayName == "정민수" })
    }

    @Test fun 인원_밖에_들어온_사람은_총무가_포함하면_함께_정산된다를_본다() {
        s.setHeadcount(open, 4)
        assertEquals("인원이 다 찼어요 · 총무가 포함하면 함께 정산돼요", nextAction(g(), 5).note)
    }

    @Test fun 응답_수는_인원_안의_사람만_센다() {
        s.setHeadcount(open, 4)
        assertEquals(3, g().respondedCount())
    }
}


/** 인원 밖 사람이 결제자면(API v8 REMOVE_PAYER, CTO 승인 2026-10-10) — 웹 autoSettle.test.ts 와 같은 케이스 */
class RemovePayerTest {
    private lateinit var s: V3Store
    private val open = 101L
    private fun g() = s.state.rooms.getValue(open)
    private fun answerAll(userId: Long) {
        s.actAs(userId)
        s.respond(open, g().rounds.associate { it.id to ResponseType.DRANK })
    }
    private fun pay2ndBy(pid: Long) {
        s.actAs(1)
        val r2 = g().rounds[1]
        s.saveRound(open, RoundDraft(r2.id, r2.total, r2.drinks, pid))
    }

    @BeforeTest fun setUp() { s = V3Store() }

    @Test fun 인원이_다_응답해도_확정하지_않고_멈춘_이유를_남긴다() {
        s.setHeadcount(open, 4)
        pay2ndBy(15) // 인원 밖 정민수가 2차를 냈다
        answerAll(4)
        assertEquals(GatheringStatus.OPEN, g().status)
        assertEquals("REMOVE_PAYER", g().autoSettlementError)
        val a = nextAction(g(), 1)
        assertEquals("인원 밖 정민수님이 결제자라 자동 계산을 멈췄어요", a.banner)
        assertEquals(ActionKind.INCLUDE_EXTRA, a.action?.kind)
    }

    @Test fun 총무가_낸_사람을_바꾸면_다시_판정해_정산된다() {
        s.setHeadcount(open, 4)
        pay2ndBy(15)
        answerAll(4)
        pay2ndBy(11)
        assertEquals(GatheringStatus.SETTLING, g().status)
        assertNull(g().autoSettlementError)
    }

    @Test fun 그_밖의_이유로_멈추면_지금_계산하기로_마무리하게_한다() {
        val a = nextAction(g().copy(headcount = 5, autoSettlementError = "NO_ROUNDS"), 1)
        assertEquals("자동 계산이 멈췄어요 · 금액을 확인하고 지금 계산해주세요", a.banner)
        assertEquals(ActionKind.SETTLE, a.action?.kind)
    }
}
