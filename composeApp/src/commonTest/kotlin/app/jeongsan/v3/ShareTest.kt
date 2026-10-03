package app.jeongsan.v3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 링크 공유 문구·주소와 금액 복사 — 웹 `share.test.ts`·`account.test.ts`와 같은 케이스. */
class ShareTest {
    private val url = "https://x/jungsan/j/k7Qx2"

    @Test fun 공유_주소는_참여_입구다() {
        assertEquals("https://jungsan.devkdk.com/jungsan/j/k7Qx2", shareUrl("https://jungsan.devkdk.com/", "k7Qx2"))
    }

    @Test fun 응답_받는_중이면_차수마다_마셨는지만_눌러달라고_한다() {
        assertEquals("[정산어택] 동규님의 9/28 술자리\n차수마다 마셨는지만 눌러주세요 👉 $url", shareMessage(room(101), url))
    }

    @Test fun 차수가_없으면_먼저_들어오라고_하고_정산_뒤면_금액_확인을_권한다() {
        assertTrue(shareMessage(room(101).copy(rounds = emptyList()), url).contains("먼저 들어와 있으면 금액이 나올 때 알려드려요"))
        assertTrue(shareMessage(room(102), url).contains("정산 금액을 확인하고 보내주세요"))
    }

    @Test fun 문구에_금액을_넣지_않는다() {
        val money = Regex("""\d{1,3},\d{3}|원""")
        for (id in listOf(101L, 102L)) assertFalse(money.containsMatchIn(shareMessage(room(id), url)))
    }

    @Test fun 금액_복사도_숫자만_복사한다() {
        assertEquals("41000", copyableAmount(41_000))
        assertEquals("1234567", copyableAmount(1_234_567))
    }
}
