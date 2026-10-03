package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L2 이름 확인 — 웹 `name.test.ts`와 같은 케이스. */
class NameTest {
    private lateinit var s: V3Store

    @BeforeTest fun setUp() {
        s = V3Store()
    }

    @Test fun 빈_이름과_공백만_있는_이름은_막는다() {
        assertEquals(listOf("이름을 넣어주세요"), validateName(""))
        assertEquals(listOf("이름을 넣어주세요"), validateName("   "))
    }

    @Test fun 열_자까지_받는다() {
        assertEquals(emptyList(), validateName("가".repeat(MAX_NAME)))
        assertEquals(listOf("이름은 ${MAX_NAME}자까지예요"), validateName("가".repeat(MAX_NAME + 1)))
    }

    @Test fun 이모지도_한_글자로_센다() {
        assertEquals(emptyList(), validateName("🍺".repeat(MAX_NAME)))
    }

    @Test fun 첫_로그인은_이름_확인이_남아_있고_확인하면_다시_묻지_않는다() {
        assertTrue(s.state.me.needsName)
        s.confirmName("  동규짱  ")
        assertEquals("동규짱", s.state.me.displayName)
        assertFalse(s.state.me.needsName)
        s.actAs(2); s.actAs(1)
        assertFalse(s.state.me.needsName)
    }

    @Test fun 내가_들어간_모든_정산방의_내_이름이_바뀐다_완료된_방도() {
        s.confirmName("동규짱")
        assertEquals("동규짱", s.state.rooms.getValue(101).participantOfUser(1)?.displayName)
        assertEquals("동규짱", s.state.rooms.getValue(102).participantOfUser(1)?.displayName)
        assertEquals("동규짱", s.state.rooms.getValue(103).participantOfUser(1)?.displayName)
    }

    @Test fun 규칙에_안_맞는_이름은_저장하지_않는다() {
        s.confirmName("가".repeat(MAX_NAME + 1))
        assertEquals("동규", s.state.me.displayName)
        assertTrue(s.state.me.needsName)
    }

    @Test fun 링크로_처음_들어와_이름을_확인하고_참여하면_그_이름으로_들어간다() {
        s.actAs(6) // 서연 — 101에 없다
        s.confirmName("서연이")
        s.joinGathering("k7Qx2", mapOf(1L to ResponseType.DRANK, 2L to ResponseType.DRANK))
        assertEquals("서연이", s.state.rooms.getValue(101).participantOfUser(6)?.displayName)
        assertEquals("서연이님이 들어왔어요", s.state.rooms.getValue(101).timeline.dropLast(1).last().body)
    }
}
