package app.jeongsan.v3

import app.jeongsan.pixelart.HostSprite
import app.jeongsan.util.won
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** 웹 `model.test.ts`·`round.test.ts`와 같은 케이스 — 모델 도우미·칭호·차수 입력 규칙. */
class RulesTest {
    // ── 삭제까지 남은 날 ──

    @Test fun 완료_순간에는_7일이_남는다() {
        val now = Instant.parse("2026-09-30T12:00:00Z")
        assertEquals(7, room(103).copy(completedAt = now).daysUntilDelete(now))
    }

    @Test fun 일곱_날이_지나면_음수가_아니라_0이다() {
        val now = Instant.parse("2026-09-30T12:00:00Z")
        assertEquals(0, room(103).copy(completedAt = now - 9.days).daysUntilDelete(now))
    }

    @Test fun 완료되지_않은_술자리에는_남은_날이_없다() {
        assertNull(room(101).daysUntilDelete(Clock.System.now()))
    }

    // ── 정산 되돌리기 가능 여부 — DOMAIN_DB_DESIGN_V2.md §5.2 ──

    @Test fun 아무도_보내거나_확인한_적_없으면_되돌릴_수_있다() {
        val g = room(102).let { it.copy(transfers = it.transfers.map { t -> t.copy(status = TransferStatus.WAITING, sentAt = null, confirmedAt = null) }) }
        assertTrue(g.canUndoSettle())
    }

    @Test fun 한_번이라도_보냈어요가_눌렸으면_지금_대기_상태여도_되돌릴_수_없다() {
        val now = Clock.System.now()
        val g = room(102).let {
            it.copy(transfers = it.transfers.mapIndexed { i, t -> t.copy(status = TransferStatus.WAITING, confirmedAt = null, sentAt = if (i == 0) now else null) })
        }
        assertFalse(g.canUndoSettle())
    }

    @Test fun 모든_차수에_응답이_있어야_응답한_것이다() {
        val g = room(101)
        assertTrue(g.hasResponded(13)) // 박재훈: 1차·2차 모두
        assertFalse(g.copy(responses = g.responses.filterNot { it.participantId == 13L && it.roundId == 2L }).hasResponded(13))
    }

    // ── 칭호 구간 — REQUIREMENTS.md §8.3, 경계값은 위 단계 ──

    @Test fun 칭호는_스푼_수로_정해지고_경계값은_위_단계다() {
        val cases = listOf(
            Triple(0, 1, "새내기 총무"), Triple(99, 1, "새내기 총무"),
            Triple(100, 2, "믿음직한 총무"), Triple(999, 2, "믿음직한 총무"),
            Triple(1_000, 3, "프로 총무"), Triple(9_999, 3, "프로 총무"),
            Triple(10_000, 4, "전설의 총무"), Triple(1_000_000, 4, "전설의 총무"),
        )
        for ((spoons, lv, title) in cases) {
            assertEquals(lv, HostSprite.levelOf(spoons), "스푼 $spoons")
            assertEquals(title, HostSprite.titleOf(spoons), "스푼 $spoons")
        }
    }

    @Test fun 금액은_천_단위_쉼표와_원을_붙인다() {
        assertEquals("41,000원", won(41000))
        assertEquals("0원", won(0))
    }

    // ── R2 차수 입력 ──

    private val ok = RoundDraft(null, 100_000, listOf(DrinkItem("소주", 5_000, 4)), 11)

    @Test fun 금액_입력에서_숫자_외_글자는_버린다() {
        val cases = mapOf("184,000" to 184_000L, "184000원" to 184_000L, "1a2b3" to 123L, "" to 0L, "원" to 0L, "007" to 7L)
        for ((text, expected) in cases) assertEquals(expected, parseAmount(text), "\"$text\"")
    }

    @Test fun 정상_입력과_술_없는_차수는_막지_않는다() {
        assertEquals(emptyList(), validateRound(ok, room(101)))
        assertEquals(emptyList(), validateRound(ok.copy(drinks = emptyList()), room(101)))
    }

    @Test fun 금액이_0이거나_0을_하나_더_친_것_같으면_막는다() {
        assertTrue("금액을 넣어주세요" in validateRound(ok.copy(total = 0), room(101)))
        assertTrue(validateRound(ok.copy(total = MAX_ROUND_TOTAL + 1), room(101))[0].contains("너무 커요"))
    }

    @Test fun 술값_합계가_차수_금액보다_크면_막고_같으면_통과한다() {
        assertTrue("술값 합계가 이 차수 금액보다 커요" in validateRound(ok.copy(drinks = listOf(DrinkItem("와인", 30_000, 4))), room(101)))
        assertEquals(emptyList(), validateRound(ok.copy(drinks = listOf(DrinkItem("와인", 25_000, 4))), room(101)))
    }

    @Test fun 직접_입력한_술의_이름_가격이_비어_있으면_막는다() {
        val errs = validateRound(ok.copy(drinks = listOf(DrinkItem(" ", 0, 1))), room(101))
        assertTrue("술 이름을 넣어주세요" in errs)
        assertTrue("술 가격을 넣어주세요" in errs)
    }

    @Test fun 참여자가_아닌_사람을_낸_사람으로_고를_수_없다() {
        assertTrue("낸 사람을 골라주세요" in validateRound(ok.copy(payerParticipantId = 999), room(101)))
    }

    @Test fun 술_합계는_병당_가격_곱하기_병_수를_더한다() {
        assertEquals(70_000L, drinksTotal(listOf(DrinkItem("소주", 5_000, 8), DrinkItem("맥주", 5_000, 6))))
    }

    @Test fun 중간_차수가_빠지면_뒤_차수가_당겨진다() {
        val out = relabel(listOf(Round(1, 1, "1차", 1, 11, emptyList()), Round(3, 3, "3차", 1, 11, emptyList())))
        assertEquals(listOf(1L to "1차", 3L to "2차"), out.map { it.id to it.label })
    }
}
