package app.jeongsan.v3

import app.jeongsan.domain.Id
import app.jeongsan.domain.Money

/**
 * 차수 편집(R2)의 규칙 — 웹 `v3/round.ts`와 같은 프리셋·같은 검증·같은 문구.
 *
 * ⚠ 여기서 하는 덧셈(술 합계)은 **입력값 검증용**이다. 1인당 금액 계산이 아니다 —
 * 정산 계산은 서버 `core`만 한다.
 */

/** 술 프리셋 — docs/SCREENS.md §5 R2. 가격은 병당 기본값이고 탭해서 고친다. */
val DRINK_PRESETS = listOf(
    DrinkItem("소주", 5_000, 1),
    DrinkItem("맥주", 5_000, 1),
    DrinkItem("막걸리", 4_000, 1),
    DrinkItem("하이볼", 7_000, 1),
    DrinkItem("와인", 30_000, 1),
)

/** 한 차수 금액 상한 — 오타(0 하나 더)를 잡기 위한 값. 서버 검증과 별개로 둔다. */
const val MAX_ROUND_TOTAL: Money = 10_000_000

data class RoundDraft(
    /** 새 차수면 null */
    val id: Id?,
    val total: Money,
    val drinks: List<DrinkItem>,
    val payerParticipantId: Id,
)

/** 입력칸 문자열 → 원. 숫자 외 글자는 버린다("184,000원" → 184000). 너무 길면 상한 위 값으로 묶는다. */
fun parseAmount(text: String): Money {
    val digits = text.filter { it.isDigit() }.trimStart('0')
    if (digits.isEmpty()) return 0
    // Long 넘침을 막는다 — 12자리면 이미 상한(1천만)을 한참 넘으니 검증이 잡는다
    return if (digits.length > 12) 999_999_999_999 else digits.toLong()
}

fun drinksTotal(drinks: List<DrinkItem>): Money = drinks.sumOf { it.unitPrice * it.quantity }

/** 저장 전에 막아야 하는 것. 빈 리스트면 저장해도 된다. */
fun validateRound(d: RoundDraft, g: Gathering): List<String> {
    val errors = mutableListOf<String>()
    if (d.total <= 0) errors += "금액을 넣어주세요"
    else if (d.total > MAX_ROUND_TOTAL) errors += "금액이 너무 커요. 0을 하나 더 넣지 않았는지 확인해주세요"
    if (d.drinks.any { it.unitPrice <= 0 }) errors += "술 가격을 넣어주세요"
    if (d.drinks.any { it.quantity < 1 }) errors += "병 수는 1 이상이어야 해요"
    if (d.drinks.any { it.name.isBlank() }) errors += "술 이름을 넣어주세요"
    if (d.total > 0 && drinksTotal(d.drinks) > d.total) errors += "술값 합계가 이 차수 금액보다 커요"
    if (g.participants.none { it.id == d.payerParticipantId }) errors += "낸 사람을 골라주세요"
    return errors
}

/** 차수 이름은 순서로 정한다. 중간 차수를 지우면 뒤 차수가 당겨진다(1·2·3차 → 2차 삭제 → 1·2차). */
fun relabel(rounds: List<Round>, start: Int = 1): List<Round> =
    rounds.sortedBy { it.seq }.mapIndexed { i, r -> r.copy(seq = start + i, label = "${start + i}차") }
