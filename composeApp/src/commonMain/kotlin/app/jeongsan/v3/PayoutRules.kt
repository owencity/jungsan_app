package app.jeongsan.v3

/** A1 계좌 등록의 규칙 — 웹 `v3/payout.ts`와 같은 은행 목록·같은 검증 문구. */

/** 은행 — 버튼으로 고른다(직접 치지 않게). 많이 쓰는 순서 */
val BANKS = listOf(
    "카카오뱅크", "토스뱅크", "국민", "신한", "우리", "하나", "농협", "기업",
    "케이뱅크", "SC제일", "새마을금고", "우체국", "수협", "신협", "부산", "대구",
)

/** 계좌번호 입력 정리 — 숫자와 하이픈만 남긴다. 은행 앱에서 복사해 온 "3333-01-…"를 그대로 받으려고 하이픈은 둔다 */
fun cleanAccountNo(text: String): String = text.filter { it.isDigit() || it == '-' }.take(24)

/**
 * [계좌 복사]로 클립보드에 넣을 값 — **숫자만.** 화면엔 하이픈을 넣어 읽기 쉽게 보여주지만, 이체 화면의
 * 계좌번호 칸은 하이픈·은행 이름이 섞이면 잘리거나 막힌다(CTO 실사용 피드백 2026-10-03). 은행 이름은 화면에 따로 보인다.
 */
fun copyableAccountNo(accountNo: String): String = accountNo.filter { it.isDigit() }

/** [금액 복사]로 클립보드에 넣을 값 — 숫자만("41,000원" 아니고 "41000"). 이체 화면 금액 칸에 그대로 붙게 */
fun copyableAmount(amount: Long): String = amount.toString()

/** 저장 전에 막아야 하는 것. 빈 리스트면 저장해도 된다 */
fun validatePayout(p: Payout): List<String> {
    val errors = mutableListOf<String>()
    if (p.bank.isBlank()) errors += "은행을 골라주세요"
    val digits = p.accountNo.count { it.isDigit() }
    if (digits < 8 || digits > 16 || p.accountNo.any { !it.isDigit() && it != '-' }) errors += "계좌번호를 확인해주세요 (숫자 8~16자리)"
    if (p.holder.isBlank()) errors += "예금주를 넣어주세요"
    else if (p.holder.trim().length > 20) errors += "예금주는 20자까지예요"
    return errors
}
