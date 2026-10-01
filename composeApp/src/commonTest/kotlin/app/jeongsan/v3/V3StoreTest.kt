package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 목데이터 저장소의 상태 전이·알림·목록 규칙 — 웹 `store.test.ts`·`settle.test.ts`·`notify.test.ts`·
 * `home.test.ts`와 같은 케이스. 백엔드가 붙으면 전이는 서버가 결정하지만, 그때까지 화면이 거짓
 * 상태를 보여주면 안 된다.
 */
private const val OPEN = 101L
private const val SETTLING = 102L
private const val COMPLETED = 103L

class V3StoreTest {
    private lateinit var s: V3Store
    private fun g(id: Long) = s.state.rooms.getValue(id)
    private fun inbox(userId: Long) = s.state.notifications.filter { it.userId == userId }
    private fun tr(roomId: Long, id: Long) = g(roomId).transfers.first { it.id == id }

    @BeforeTest fun setUp() {
        s = V3Store()
    }

    // ── 스푼 ──

    @Test fun 참여자가_스푼을_주면_총무_누적이_1_오르고_타임라인에_소식이_남는다() {
        val before = g(COMPLETED).host().spoonCount
        s.giveSpoon(COMPLETED)
        assertEquals(before + 1, g(COMPLETED).host().spoonCount)
        assertTrue(32L in g(COMPLETED).spoonGivers)
        assertEquals(TimelineType.SPOON, g(COMPLETED).timeline.last().type)
        assertEquals(32L, g(COMPLETED).timeline.last().authorParticipantId)
    }

    @Test fun 한_술자리에서_두_번_눌러도_한_번만_반영된다() {
        s.giveSpoon(COMPLETED)
        val once = g(COMPLETED).host().spoonCount
        s.giveSpoon(COMPLETED)
        assertEquals(once, g(COMPLETED).host().spoonCount)
    }

    @Test fun 정산_전에는_줄_수_없고_총무는_자기_자신에게_줄_수_없다() {
        val before = g(OPEN)
        s.giveSpoon(OPEN)
        assertSame(before, g(OPEN))
    }

    // ── 송금 상태 ──

    @Test fun 보냈어요는_내가_보내는_대기_중_송금만_보냄_상태로_바꾸고_시각을_남긴다() {
        s.markSent(SETTLING, 1)
        assertEquals(TransferStatus.SENT, tr(SETTLING, 1).status)
        assertNotNull(tr(SETTLING, 1).sentAt)
    }

    @Test fun 남의_송금에는_보냈어요를_누를_수_없다() {
        s.markSent(SETTLING, 5) // 민지 → 재훈
        assertEquals(TransferStatus.WAITING, tr(SETTLING, 5).status)
    }

    @Test fun 아직_안_들어왔어요는_대기로_되돌리되_보낸_시각은_지우지_않는다() {
        s.actAs(2) // 민지 — 재훈이 보낸 29,000원의 수취인
        s.notReceived(SETTLING, 3)
        assertEquals(TransferStatus.WAITING, tr(SETTLING, 3).status)
        assertNotNull(tr(SETTLING, 3).notReceivedAt)
        assertNotNull(tr(SETTLING, 3).sentAt)
    }

    @Test fun 수취인이_아니면_입금_확인을_할_수_없다() {
        s.confirmIncoming(SETTLING, 3)
        assertEquals(TransferStatus.SENT, tr(SETTLING, 3).status)
    }

    @Test fun 마지막_송금이_확인되면_완료로_바뀌고_참여자_2명_이상이면_총무가_기본_스푼_1개를_받는다() {
        val hostBefore = g(SETTLING).host().spoonCount
        s.actAs(2); s.confirmIncoming(SETTLING, 3); s.confirmIncoming(SETTLING, 1)
        assertEquals(GatheringStatus.SETTLING, g(SETTLING).status) // 재훈이 받을 돈이 남았다
        s.actAs(3); s.confirmIncoming(SETTLING, 5); s.confirmIncoming(SETTLING, 2)
        assertEquals(GatheringStatus.COMPLETED, g(SETTLING).status)
        assertNotNull(g(SETTLING).completedAt)
        assertEquals(hostBefore + 1, g(SETTLING).host().spoonCount)
        assertEquals("모두 입금 완료! 🎉", g(SETTLING).timeline.last().body)
    }

    // ── 차수 입력 (총무, 정산 전에만) ──

    private val draft = RoundDraft(null, 50_000, emptyList(), 11)

    @Test fun 새_차수는_다음_번호로_붙고_입력_버전이_오르고_타임라인에_남는다() {
        val rev = g(OPEN).inputRevision
        val id = s.saveRound(OPEN, draft)
        assertNotNull(id)
        assertEquals("3차", g(OPEN).rounds.last().label)
        assertEquals(50_000L, g(OPEN).rounds.last().total)
        assertEquals(rev + 1, g(OPEN).inputRevision)
        assertEquals("3차 50,000원을 넣었어요", g(OPEN).timeline.last().body)
    }

    @Test fun 기존_차수를_고치면_번호는_그대로고_값만_바뀐다() {
        s.saveRound(OPEN, draft.copy(id = 2, total = 99_000))
        val r = g(OPEN).rounds.first { it.id == 2L }
        assertEquals("2차", r.label)
        assertEquals(99_000L, r.total)
        assertEquals(2, g(OPEN).rounds.size)
    }

    @Test fun 중간_차수를_지우면_그_차수의_응답도_지워지고_뒤_차수가_당겨진다() {
        s.saveRound(OPEN, draft)
        s.deleteRound(OPEN, 2)
        assertEquals(listOf("1차", "2차"), g(OPEN).rounds.map { it.label })
        assertFalse(g(OPEN).responses.any { it.roundId == 2L })
    }

    @Test fun 총무가_아니면_차수를_넣을_수_없다() {
        s.actAs(2)
        assertNull(s.saveRound(OPEN, draft))
        assertEquals(2, g(OPEN).rounds.size)
    }

    @Test fun 정산한_뒤에는_차수를_넣거나_지울_수_없다() {
        s.actAs(2) // 102의 총무 민지
        val before = g(SETTLING)
        s.saveRound(SETTLING, draft.copy(payerParticipantId = 21))
        s.deleteRound(SETTLING, 11)
        assertSame(before, g(SETTLING))
    }

    @Test fun 타임라인_메시지는_보낸_사람이_나로_기록된다() {
        s.sendMessage(OPEN, "다들 고생했어요")
        val last = g(OPEN).timeline.last()
        assertEquals(TimelineType.MESSAGE, last.type)
        assertEquals(11L, last.authorParticipantId)
        assertEquals("다들 고생했어요", last.body)
    }

    // ── 정산 미리보기(서버 흉내) ──

    @Test fun 사람별_금액을_다_더하면_차수_합계와_같다_돈이_새거나_생기지_않는다() {
        val p = mockPreview(g(OPEN))
        assertEquals(g(OPEN).rounds.sumOf { it.total }, p.lines.sumOf { it.total })
    }

    @Test fun 미리보기는_웹과_같은_금액을_낸다() {
        // 웹 R3 캡처와 같은 값 — 두 플랫폼이 같은 흉내 규칙을 쓰는지
        val totals = mockPreview(g(OPEN)).lines.associate { it.participantId to it.total }
        assertEquals(mapOf(11L to 72_300L, 12L to 40_300L, 13L to 22_800L, 14L to 72_300L, 15L to 72_300L), totals)
    }

    @Test fun 면제_불참인_차수는_0원이다() {
        val lines = mockPreview(g(OPEN)).lines
        assertEquals(TransferBasis(2, ResponseType.EXEMPT, 0), lines.first { it.participantId == 12L }.rounds.first { it.roundId == 2L })
        assertEquals(TransferBasis(2, ResponseType.ABSENT, 0), lines.first { it.participantId == 13L }.rounds.first { it.roundId == 2L })
    }

    // ── 정산하기 ──

    @Test fun 총무가_정산하면_송금_중으로_넘어가고_보낼_돈_목록이_생긴다() {
        assertEquals(SettleResult.OK, s.settle(OPEN, g(OPEN).inputRevision))
        assertEquals(GatheringStatus.SETTLING, g(OPEN).status)
        assertTrue(g(OPEN).transfers.isNotEmpty())
        assertTrue(g(OPEN).transfers.all { it.status == TransferStatus.WAITING })
    }

    @Test fun 응답_없던_사람은_자동응답으로_채워지고_타임라인에_이름이_남는다() {
        s.settle(OPEN, g(OPEN).inputRevision)
        for (pid in listOf(14L, 15L)) {
            assertTrue(g(OPEN).responses.filter { it.participantId == pid }.all { it.type == ResponseType.DRANK && it.source == ResponseSource.AUTO })
        }
        assertContains(g(OPEN).timeline.last().body, "지영·민수님은 응답이 없어")
    }

    @Test fun 미리보기_뒤_입력이_바뀌었으면_거절된다() {
        val rev = g(OPEN).inputRevision
        s.respondAsHost(OPEN, 14, 1, ResponseType.ABSENT)
        assertEquals(SettleResult.STALE, s.settle(OPEN, rev))
        assertEquals(GatheringStatus.OPEN, g(OPEN).status)
    }

    @Test fun 총무가_아니면_정산할_수_없다() {
        s.actAs(2)
        assertEquals(SettleResult.DENIED, s.settle(OPEN, g(OPEN).inputRevision))
    }

    @Test fun 정산한_뒤_송금_금액은_미리보기에서_본_금액_그대로다() {
        val preview = mockPreview(g(OPEN))
        s.settle(OPEN, g(OPEN).inputRevision)
        for (t in preview.transfers) assertEquals(t.amount, g(OPEN).transfers.first { it.fromParticipantId == t.fromParticipantId }.amount)
    }

    // ── 응답 ──

    @Test fun 총무가_대신_넣은_응답은_HOST로_남고_다_채우면_미응답자에서_빠진다() {
        s.respondAsHost(OPEN, 14, 1, ResponseType.SOBER)
        s.respondAsHost(OPEN, 14, 2, ResponseType.ABSENT)
        assertEquals(ResponseSource.HOST, g(OPEN).responseOf(14, 1)?.source)
        assertFalse(g(OPEN).unrespondedParticipants().any { it.id == 14L })
    }

    @Test fun 참여자가_응답하면_SELF로_저장되고_배너가_응답_완료로_바뀐다() {
        s.actAs(5)
        s.respond(OPEN, mapOf(1L to ResponseType.DRANK, 2L to ResponseType.SOBER))
        assertEquals(ResponseSource.SELF, g(OPEN).responseOf(15, 2)?.source)
        assertEquals(ActionKind.EDIT_RESPONSE, nextAction(g(OPEN), 5).action?.kind)
        assertEquals("민수님이 응답했어요", g(OPEN).timeline.last().body)
    }

    @Test fun 총무가_면제로_정한_칸과_스스로_면제는_참여자가_바꿀_수_없다() {
        s.actAs(2)
        s.respond(OPEN, mapOf(1L to ResponseType.SOBER, 2L to ResponseType.DRANK))
        assertEquals(ResponseType.EXEMPT, g(OPEN).responseOf(12, 2)?.type)
        s.actAs(4)
        s.respond(OPEN, mapOf(1L to ResponseType.EXEMPT))
        assertNull(g(OPEN).responseOf(14, 1))
    }

    @Test fun 정산된_뒤에는_응답이_바뀌지_않는다() {
        val before = g(SETTLING).responseOf(22, 11)
        s.respond(SETTLING, mapOf(11L to ResponseType.ABSENT))
        assertEquals(before, g(SETTLING).responseOf(22, 11))
    }

    // ── 알림 ──

    @Test fun 정산하면_참여자마다_입금액을_확인해주세요_알림이_가고_누르면_내_금액으로_간다() {
        s.settle(OPEN, g(OPEN).inputRevision)
        for (userId in listOf(3L, 4L, 5L)) {
            val n = inbox(userId).first { it.roomId == OPEN }
            assertEquals("정산이 나왔어요! 입금액을 확인해주세요", n.title)
            assertEquals(Target.Pay(OPEN), n.target)
        }
        assertContains(inbox(4).first { it.roomId == OPEN }.body, "응답이 없어 전 차수 참석·알코올로 계산됐어요")
        assertTrue(inbox(1).none { it.roomId == OPEN }) // 정산한 본인에게는 안 간다
    }

    @Test fun 보냈어요를_누르면_받는_사람에게_알림이_간다() {
        s.markSent(SETTLING, 1)
        assertEquals("동규님이 보냈대요. 입금을 확인해주세요", inbox(2).first().title)
    }

    @Test fun 아직_안_들어왔어요를_누르면_보낸_사람에게_알림이_가고_누르면_내_금액으로_간다() {
        s.actAs(2)
        s.notReceived(SETTLING, 3)
        assertEquals("민지님이 아직 입금을 확인 못 했대요", inbox(3).first().title)
        assertEquals(Target.Pay(SETTLING), inbox(3).first().target)
    }

    @Test fun 모두_입금되면_행동한_사람_빼고_모두에게_정산_완료가_간다() {
        s.markSent(SETTLING, 1); s.markSent(SETTLING, 2)
        s.actAs(2); s.confirmIncoming(SETTLING, 1); s.confirmIncoming(SETTLING, 3); s.markSent(SETTLING, 5)
        s.actAs(3); s.confirmIncoming(SETTLING, 2); s.confirmIncoming(SETTLING, 5)
        assertEquals(GatheringStatus.COMPLETED, g(SETTLING).status)
        for (u in listOf(1L, 2L, 6L)) assertEquals("정산 완료! 🎉", inbox(u).first().title)
        assertTrue(inbox(3).none { it.title == "정산 완료! 🎉" })
    }

    @Test fun 모두_읽음은_지금_보는_사람_것만_바꾼다() {
        s.markAllRead()
        assertTrue(inbox(1).all { it.read })
        assertTrue(inbox(2).any { !it.read })
    }

    // ── 내 술자리 탭·진입·뱃지 ──

    @Test fun 진행_중인_방은_내_역할로_나뉘고_끝난_방은_완료에만_있다() {
        val t = myRoomTabs(s.state.rooms.values, 1)
        assertEquals(listOf(OPEN), t.getValue(HomeTab.HOSTING).map { it.id })
        assertEquals(listOf(SETTLING), t.getValue(HomeTab.JOINED).map { it.id })
        assertEquals(listOf(COMPLETED), t.getValue(HomeTab.DONE).map { it.id })
    }

    @Test fun 처음_열리는_탭은_할_일이_있는_탭이다() {
        assertEquals(HomeTab.JOINED, initialTab(myRoomTabs(s.state.rooms.values, 1), 1)) // 동규: 보낼 돈
        assertEquals(HomeTab.HOSTING, initialTab(myRoomTabs(s.state.rooms.values, 2), 2)) // 민지: 입금 확인
        assertEquals(HomeTab.HOSTING, initialTab(myRoomTabs(emptyList(), 1), 1))
    }

    @Test fun 새_술자리는_내가_총무이자_유일한_참여자이고_할_일은_1차_입력이다() {
        val id = s.createGathering()
        val created = g(id)
        assertEquals(GatheringStatus.OPEN, created.status)
        assertEquals(1, created.participants.size)
        assertEquals(ActionKind.EDIT_FIRST_ROUND, nextAction(created, 1).action?.kind)
        assertEquals(id, myRoomTabs(s.state.rooms.values, 1).getValue(HomeTab.HOSTING).first().id)
        assertNotEquals(id, s.createGathering())
    }

    @Test fun 참여자는_할_일_화면을_먼저_본다() {
        assertEquals(Target.Respond(OPEN), entryTarget(g(OPEN), 4, false)) // 응답 전
        assertEquals(Target.Room(OPEN), entryTarget(g(OPEN), 3, false)) // 응답함
        assertEquals(Target.Pay(SETTLING), entryTarget(g(SETTLING), 1, false)) // 금액 안 봄
        assertEquals(Target.Room(SETTLING), entryTarget(g(SETTLING), 1, true)) // 봤음
        assertEquals(Target.Room(OPEN), entryTarget(g(OPEN), 1, false)) // 총무는 늘 정산방
    }

    @Test fun 목록_뱃지는_입금_확인_정산금액_확인_응답하기_순이다() {
        assertEquals("입금 확인", rowBadge(g(SETTLING), 2, false)) // 민지: 보낼 돈도 있지만 확인이 먼저
        assertEquals("정산금액 확인", rowBadge(g(SETTLING), 1, false))
        s.markPaySeen(SETTLING)
        assertTrue(s.state.isPaySeen(SETTLING))
        assertNull(rowBadge(g(SETTLING), 1, true))
        assertEquals("응답하기", rowBadge(g(OPEN), 4, false))
        assertNull(rowBadge(g(COMPLETED), 1, false))
    }
}
