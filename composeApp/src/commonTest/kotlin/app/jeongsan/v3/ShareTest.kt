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

    @Test fun 응답_링크는_가입_없이_이름과_참석_음주만_요청한다() {
        assertEquals("[정산어택] 김동규님의 9/28 술자리\n가입 없이 이름과 차수별 참석·음주만 알려주세요 👉 $url", shareMessage(room(101), url))
    }

    @Test fun 차수_입력_전은_응답을_기다리고_확정_뒤는_이미지로_안내한다() {
        assertTrue(shareMessage(room(101).copy(rounds = emptyList()), url).contains("총무가 차수를 입력하면"))
        assertTrue(shareMessage(room(102), url).contains("총무가 공유한 이미지"))
    }

    @Test fun 문구에_금액을_넣지_않는다() {
        val money = Regex("""\d{1,3},\d{3}|원""")
        for (id in listOf(101L, 102L)) assertFalse(money.containsMatchIn(shareMessage(room(id), url)))
    }

    @Test fun 금액_복사도_숫자만_복사한다() {
        assertEquals("41000", copyableAmount(41_000))
        assertEquals("1234567", copyableAmount(1_234_567))
    }

    @Test fun 송금_안내는_참여자의_앱_사용을_요구하지_않는다() {
        val message = paymentRequestMessage(room(102))
        assertFalse(message.contains("앱에서"))
        assertFalse(message.contains("보냈어요"))
        assertTrue(message.contains("총무에게 알려주세요"))
    }
}
