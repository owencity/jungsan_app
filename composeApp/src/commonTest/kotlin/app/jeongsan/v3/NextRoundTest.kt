package app.jeongsan.v3

import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 다음 차를 다른 사람이 계산 — 그 사람이 총무인 **완전히 별개의** 새 술자리(CTO 결정 2026-10-06).
 * 웹 `nextround.test.ts`와 같은 케이스.
 */
class NextRoundTest {
    private lateinit var s: V3Store

    @BeforeTest fun setUp() {
        s = V3Store()
    }

    @Test fun 원래_이름_뒤에_다음_차를_붙인다() {
        assertEquals("9/28 술자리 다음 차", nextTitle("9/28 술자리"))
    }

    @Test fun 최대_길이를_넘으면_글자_단위로_자른다() {
        assertEquals(MAX_TITLE, nameLength(nextTitle("가".repeat(18))))
    }

    @Test fun 누른_사람이_총무이자_유일한_참여자인_새_술자리가_생긴다() {
        s.actAs(2) // 이민지 — 101의 참여자, 총무 아님
        val before = s.state.rooms.size
        val id = s.createGathering(nextTitle(s.state.rooms.getValue(101).title))
        val g = s.state.rooms.getValue(id)
        assertEquals(before + 1, s.state.rooms.size)
        assertEquals(2L, g.hostUserId)
        assertEquals(listOf(2L), g.participants.map { it.userId })
        assertEquals("9/28 술자리 다음 차", g.title)
        assertEquals(GatheringStatus.OPEN, g.status)
        assertTrue(g.rounds.isEmpty())
        assertNotEquals(s.state.rooms.getValue(101).shareToken, g.shareToken)
    }

    @Test fun 원래_술자리는_그대로다_완전히_분리() {
        val orig = s.state.rooms.getValue(101)
        s.actAs(2)
        s.createGathering(nextTitle(orig.title))
        assertEquals(orig, s.state.rooms.getValue(101))
    }

    @Test fun 제목을_주지_않으면_날짜_제목이다() {
        val id = s.createGathering()
        assertEquals(autoTitle(Clock.System.now()), s.state.rooms.getValue(id).title)
    }
}
