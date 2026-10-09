package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 다음 차 총무 되기(목데이터, FC-015) — 웹 store `createUnit`과 같은 규칙 */
class NextUnitTest {
    private lateinit var s: V3Store
    private val OPEN = 101L

    @BeforeTest fun setUp() { s = V3Store() }

    @Test fun 같은_술자리_안에_내가_총무인_정산방이_생기고_고른_사람과_나만_들어간다() {
        s.actAs(2) // 101의 참여자 이민지
        val src = s.state.rooms[OPEN]!!
        val other = src.participants.first { it.userId != 2L && it.userId != src.hostUserId }
        val id = assertNotNull(s.createUnit(OPEN, listOf(other.id)))
        val g = s.state.rooms[id]!!
        assertEquals(2L, g.hostUserId)
        assertEquals(src.gatheringId ?: src.id, g.gatheringId)
        assertEquals(setOf(other.id, src.participantOfUser(2)!!.id), g.participants.map { it.id }.toSet())
        assertTrue(g.timeline.last().body.endsWith("추가 차수의 총무가 되었어요"))
    }

    @Test fun 차수_번호는_술자리_전체에서_이어진다() {
        s.actAs(2)
        val last = s.state.rooms[OPEN]!!.rounds.maxOf { it.seq }
        val id = s.createUnit(OPEN, emptyList())!!
        assertEquals("${last + 1}차", s.state.rooms[id]!!.nextRoundLabel())
        s.saveRound(id, RoundDraft(null, 30_000, emptyList(), s.state.rooms[id]!!.host().id))
        assertEquals("${last + 1}차", s.state.rooms[id]!!.rounds.single().label)
    }
}
