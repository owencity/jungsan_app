package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * P1 참여 입구 — 웹 `entry.test.ts`와 같은 케이스. 링크를 연 것만으로는 참여되지 않고,
 * [참여]를 눌러야 명단에 들어간다.
 */
private const val OPEN_TOKEN = "k7Qx2" // 101 — 동규 총무, 응답 받는 중
private const val SETTLING_TOKEN = "Pw9mL" // 102 — 정산 뒤

class EntryTest {
    private lateinit var s: V3Store
    private fun room() = s.state.rooms.getValue(101)
    private val both = mapOf(1L to ResponseType.DRANK, 2L to ResponseType.DRANK)

    @BeforeTest fun setUp() {
        s = V3Store()
        s.actAs(6) // 서연 — 101에 없는 사람
    }

    @Test fun 참여하면_명단에_들어가고_고른_응답이_본인_응답으로_남는다() {
        assertEquals(101L, s.joinGathering(OPEN_TOKEN, mapOf(1L to ResponseType.DRANK, 2L to ResponseType.ABSENT)))
        val me = assertNotNull(room().participantOfUser(6))
        assertEquals("서연", me.displayName)
        assertTrue(room().hasResponded(me.id))
        assertEquals(RoundResponse(me.id, 2, ResponseType.ABSENT, ResponseSource.SELF), room().responseOf(me.id, 2))
    }

    @Test fun 타임라인에_들어온_소식과_응답_소식이_남는다() {
        s.joinGathering(OPEN_TOKEN, both)
        assertEquals(listOf("서연님이 들어왔어요", "서연님이 응답했어요"), room().timeline.takeLast(2).map { it.body })
    }

    @Test fun 총무에게_참여하고_응답했어요_알림이_간다() {
        s.joinGathering(OPEN_TOKEN, both)
        assertEquals("서연님이 참여하고 응답했어요", s.state.notifications.first { it.userId == 1L && it.roomId == 101L }.title)
    }

    @Test fun 명단이_바뀌면_총무가_보던_정산_미리보기로는_정산할_수_없다() {
        val rev = room().inputRevision
        s.joinGathering(OPEN_TOKEN, both)
        s.actAs(1)
        assertEquals(SettleResult.STALE, s.settle(101, rev))
    }

    @Test fun 새로_온_사람은_면제를_고를_수_없다() {
        s.joinGathering(OPEN_TOKEN, mapOf(1L to ResponseType.EXEMPT))
        assertNull(room().responseOf(room().participantOfUser(6)!!.id, 1))
    }

    @Test fun 이미_참여_중이면_아무것도_바꾸지_않고_그_술자리로_보낸다() {
        s.actAs(3) // 재훈 — 이미 101에 있다
        val before = room()
        assertEquals(101L, s.joinGathering(OPEN_TOKEN, emptyMap()))
        assertSame(before, room())
    }

    @Test fun 정산된_술자리와_없는_링크에는_참여할_수_없다() {
        s.actAs(4) // 지영 — 102에 없다
        assertNull(s.joinGathering(SETTLING_TOKEN, emptyMap()))
        assertNull(s.state.rooms.getValue(102).participantOfUser(4))
        assertNull(s.joinGathering("nope", emptyMap()))
    }
}
