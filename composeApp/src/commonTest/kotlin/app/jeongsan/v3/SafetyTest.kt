package app.jeongsan.v3

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 메시지 신고·차단(FC-024) — 누구에게 어떤 메시지를 숨기나. 웹 safety.test.ts 와 같은 케이스 */
class SafetyTest {
    // 9/28 술자리: 참여자 11(김동규·user 1) 12(이민지·user 2) 13(박재훈·user 3) 14(최지영·user 4)
    private val t = Instant.parse("2026-10-20T00:00:00Z")
    private fun e(id: Long, type: TimelineType, author: Long? = null) = TimelineEntry(id, type, "#$id", t, author)
    private fun room(): Gathering = V3State.initial().rooms.getValue(101).copy(
        timeline = listOf(e(1, TimelineType.MESSAGE, 12), e(2, TimelineType.MESSAGE, 13), e(3, TimelineType.SYSTEM), e(4, TimelineType.MESSAGE, 11), e(5, TimelineType.SPOON, 12)),
    )
    private fun List<TimelineEntry>.ids() = map { it.id }

    @Test fun 신고한_사람에게서는_그_메시지가_바로_사라지고_다른_사람에게는_그대로_보인다() {
        val g = room()
        val m = Moderation(reports = listOf(Report(g.entryKey(1), 1)))
        assertEquals(listOf(2L, 3, 4, 5), g.visibleTimeline(1, m).ids())
        assertEquals(listOf(1L, 2, 3, 4, 5), g.visibleTimeline(3, m).ids())
    }

    @Test fun 서로_다른_두_명이_신고하면_모두에게서_숨긴다() {
        val g = room()
        val m = Moderation(reports = listOf(Report(g.entryKey(1), 1), Report(g.entryKey(1), 3)))
        assertEquals(listOf(2L, 3, 4, 5), g.visibleTimeline(4, m).ids())
    }

    @Test fun 같은_사람이_두_번_신고해도_한_명으로_센다() {
        val g = room()
        val r = Report(g.entryKey(1), 1)
        assertEquals(listOf(1L, 2, 3, 4, 5), g.visibleTimeline(4, Moderation(reports = listOf(r, r))).ids())
    }

    @Test fun 차단한_사람이_쓴_메시지만_나에게서_숨기고_시스템_소식과_스푼은_그대로_둔다() {
        val g = room()
        val m = Moderation(blocks = listOf(Block(1, 2, "이민지")))
        assertEquals(listOf(2L, 3, 4, 5), g.visibleTimeline(1, m).ids())
    }

    @Test fun 차단은_차단한_사람에게만_적용된다() {
        val g = room()
        val m = Moderation(blocks = listOf(Block(1, 2, "이민지")))
        assertEquals(listOf(1L, 2, 3, 4, 5), g.visibleTimeline(2, m).ids())
        assertEquals(listOf(1L, 2, 3, 4, 5), g.visibleTimeline(3, m).ids())
    }

    @Test fun 계정_화면에는_내가_차단한_사람만_나온다() {
        val m = Moderation(blocks = listOf(Block(1, 2, "이민지"), Block(3, 4, "최지영")))
        assertEquals(listOf("이민지"), m.myBlocks(1).map { it.displayName })
    }

    @Test fun 신고_차단_메뉴는_남이_쓴_메시지에만_뜬다() {
        val g = room()
        val (other, _, sys, mine, spoon) = g.timeline
        assertTrue(g.canModerate(other, 1))
        assertFalse(g.canModerate(mine, 1))
        assertFalse(g.canModerate(sys, 1))
        assertFalse(g.canModerate(spoon, 1))
    }

    @Test fun 스토어에서_신고하면_나에게서_사라지고_차단하면_명단은_그대로다() {
        val s = V3Store()
        val g = s.state.rooms.getValue(101)
        val other = g.timeline.first { it.type == TimelineType.MESSAGE && g.canModerate(it, s.state.me.id) }
        s.reportMessage(101, other.id)
        assertFalse(s.state.rooms.getValue(101).visibleTimeline(s.state.me.id, s.state.moderation).any { it.id == other.id })
        s.blockParticipant(101, 12)
        assertEquals(listOf("이민지"), s.state.moderation.myBlocks(s.state.me.id).map { it.displayName })
        assertTrue(s.state.rooms.getValue(101).participants.any { it.id == 12L })
        s.unblockUser(2)
        assertTrue(s.state.moderation.myBlocks(s.state.me.id).isEmpty())
    }
}
