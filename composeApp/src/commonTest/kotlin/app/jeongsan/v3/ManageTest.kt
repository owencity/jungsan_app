package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * R4 참여자 관리 — 웹 `manage.test.ts`와 같은 케이스. 면제는 총무만, 정산 전에만.
 * 면제를 풀면 그 칸은 빈칸으로 돌아간다(flow-changes FC-010).
 */
private const val OPEN = 101L // 김동규(1) 총무 · 11 김동규(결제자) 12 이민지 13 박재훈 14 최지영 15 정민수
private const val SETTLING = 102L

class ManageTest {
    private lateinit var s: V3Store
    private fun room(id: Long = OPEN) = s.state.rooms.getValue(id)
    private fun inbox(userId: Long) = s.state.notifications.filter { it.userId == userId }

    @BeforeTest fun setUp() {
        s = V3Store()
    }

    @Test fun 총무가_면제하면_그_칸이_총무_지정_면제로_잠기고_입력_버전이_오른다() {
        val rev = room().inputRevision
        s.setExempt(OPEN, 13, 1, true)
        assertEquals(RoundResponse(13, 1, ResponseType.EXEMPT, ResponseSource.HOST), room().responseOf(13, 1))
        assertEquals(rev + 1, room().inputRevision)
        assertEquals("박재훈님 1차를 면제했어요 🎁", room().timeline.last().body)
    }

    @Test fun 면제받은_사람에게_알림이_간다() {
        s.setExempt(OPEN, 13, 1, true)
        assertEquals("김동규 총무가 1차를 면제해줬어요 🎁", inbox(3).first().title)
    }

    @Test fun 면제받은_칸은_참여자가_바꿀_수_없다() {
        s.setExempt(OPEN, 13, 1, true)
        s.actAs(3)
        s.respond(OPEN, mapOf(1L to ResponseType.DRANK))
        assertEquals(ResponseType.EXEMPT, room().responseOf(13, 1)?.type)
    }

    @Test fun 면제를_풀면_그_칸은_빈칸으로_돌아간다() {
        s.setExempt(OPEN, 12, 2, false) // 이민지 2차 — 목데이터에서 원래 면제
        assertNull(room().responseOf(12, 2))
        assertFalse(room().hasResponded(12))
        assertEquals("이민지님 2차 면제를 풀었어요", room().timeline.last().body)
    }

    @Test fun 이미_같은_상태면_아무것도_바뀌지_않는다() {
        val before = room()
        s.setExempt(OPEN, 12, 2, true)
        assertSame(before, room())
    }

    @Test fun 총무가_아니거나_정산한_뒤에는_면제를_바꿀_수_없다() {
        s.actAs(2)
        s.setExempt(OPEN, 13, 1, true)
        assertEquals(ResponseType.SOBER, room().responseOf(13, 1)?.type)
        val before = room(SETTLING) // 이민지가 총무인 정산 뒤 술자리
        s.setExempt(SETTLING, 22, 11, true)
        assertSame(before, room(SETTLING))
    }

    @Test fun 정산_전에는_내보낼_수_있고_그_사람의_응답도_같이_사라진다() {
        assertNull(s.removeParticipant(OPEN, 13))
        assertNull(room().participantOfUser(3))
        assertFalse(room().responses.any { it.participantId == 13L })
        assertEquals("박재훈님이 빠졌어요", room().timeline.last().body)
    }

    @Test fun 빠진_사람에게_알림이_가고_누르면_내_술자리로_간다() {
        s.removeParticipant(OPEN, 13)
        val n = inbox(3).first()
        assertEquals("9/28 술자리에서 빠졌어요", n.title)
        assertEquals(Target.Home, n.target)
    }

    @Test fun 명단이_바뀌면_총무가_보던_미리보기로는_정산할_수_없다() {
        val rev = room().inputRevision
        s.removeParticipant(OPEN, 14)
        assertEquals(SettleResult.STALE, s.settle(OPEN, rev))
    }

    @Test fun 결제자는_내보낼_수_없다() {
        val g = room().let { it.copy(rounds = it.rounds.map { r -> if (r.id == 2L) r.copy(payerParticipantId = 12) else r }) }
        assertTrue(removeBlockedReason(g, 12)!!.contains("결제자는 내보낼 수 없어요"))
    }

    @Test fun 총무_자신_정산_뒤_총무가_아닌_사람은_내보낼_수_없다() {
        assertEquals("총무는 내보낼 수 없어요", s.removeParticipant(OPEN, 11))
        s.actAs(2)
        assertEquals("총무만 내보낼 수 있어요", s.removeParticipant(OPEN, 13))
        assertEquals("정산한 뒤에는 내보낼 수 없어요", s.removeParticipant(SETTLING, 24))
        assertNotNull(room(SETTLING).participantOfUser(6))
    }
}
