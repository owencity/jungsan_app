package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L2 이름(실명) 확인 · 이름(닉네임) 표시 — 웹 `name.test.ts`와 같은 케이스. */
class NameTest {
    private lateinit var s: V3Store

    @BeforeTest fun setUp() {
        s = V3Store()
    }

    @Test fun 빈_이름과_공백만_있는_이름은_막는다() {
        assertEquals(listOf("이름을 넣어주세요"), validateName(""))
        assertEquals(listOf("이름을 넣어주세요"), validateName("   "))
    }

    @Test fun 한_글자는_성이_빠진_것으로_보고_막는다() {
        assertEquals(listOf("성까지 적어주세요"), validateName("규"))
        assertEquals(emptyList(), validateName("김규"))
    }

    @Test fun 열_자까지_받는다() {
        assertEquals(emptyList(), validateName("가".repeat(MAX_NAME)))
        assertEquals(listOf("이름은 ${MAX_NAME}자까지예요"), validateName("가".repeat(MAX_NAME + 1)))
    }

    @Test fun 형식은_묻지_않는다_복성_영문_이름도_받는다() {
        assertEquals(emptyList(), validateName("남궁민수"))
        assertEquals(emptyList(), validateName("John Kim"))
    }

    @Test fun 이모지도_한_글자로_센다() {
        assertEquals(emptyList(), validateName("🍺".repeat(MAX_NAME)))
    }

    @Test fun 목록에선_실명_뒤에_카카오_닉네임을_괄호로_붙인다() {
        assertEquals("한서연(🌸봄이🌸)", nameWithNick("한서연", "🌸봄이🌸"))
    }

    @Test fun 닉네임이_없거나_실명과_같으면_이름만_쓴다() {
        assertEquals("정민수", nameWithNick("정민수", null))
        assertEquals("최지영", nameWithNick("최지영", "최지영"))
    }

    @Test fun 목데이터_정산방의_참여자도_사람의_닉네임을_가진다() {
        assertEquals("이민지(밍지🍺)", s.state.rooms.getValue(101).participantOfUser(2)!!.nameWithNick())
    }

    @Test fun 한글_세_글자_실명은_이름_첫_글자를_아바타에_쓴다() {
        assertEquals("동", initialOf("김동규"))
        assertEquals("남", initialOf("남궁민수"))
        assertEquals("J", initialOf("John Kim"))
    }

    @Test fun 첫_로그인은_이름_확인이_남아_있고_확인하면_다시_묻지_않는다() {
        assertTrue(s.state.me.needsName)
        s.confirmName("  김동구  ")
        assertEquals("김동구", s.state.me.displayName)
        assertFalse(s.state.me.needsName)
        s.actAs(2); s.actAs(1)
        assertFalse(s.state.me.needsName)
    }

    @Test fun 실명을_정해도_카카오_닉네임은_그대로_남는다() {
        s.confirmName("김동구")
        assertEquals("동규짱", s.state.me.nickname)
    }

    @Test fun 내가_들어간_모든_정산방의_내_이름이_바뀐다_완료된_방도() {
        s.confirmName("김동구")
        assertEquals("김동구", s.state.rooms.getValue(101).participantOfUser(1)?.displayName)
        assertEquals("김동구", s.state.rooms.getValue(102).participantOfUser(1)?.displayName)
        assertEquals("김동구", s.state.rooms.getValue(103).participantOfUser(1)?.displayName)
    }

    @Test fun 규칙에_안_맞는_이름은_저장하지_않는다() {
        s.confirmName("규")
        assertEquals("김동규", s.state.me.displayName)
        assertTrue(s.state.me.needsName)
    }

    @Test fun 링크로_처음_들어와_실명을_적고_참여하면_그_이름과_닉네임으로_들어간다() {
        s.actAs(6) // 한서연 — 101에 없다
        s.confirmName("한서윤")
        s.joinGathering("k7Qx2", mapOf(1L to ResponseType.DRANK, 2L to ResponseType.DRANK))
        assertEquals("한서윤(🌸봄이🌸)", s.state.rooms.getValue(101).participantOfUser(6)!!.nameWithNick())
        // 타임라인 문장엔 이름만
        assertEquals("한서윤님이 들어왔어요", s.state.rooms.getValue(101).timeline.dropLast(1).last().body)
    }
}
