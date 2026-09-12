package app.jeongsan.util

/**
 * 금액 표시. 웹의 `api.ts` `won()` 을 옮긴 것.
 *
 * `java.text.NumberFormat` 을 안 쓴다 — JVM 전용이라 iOS 에서 못 쓴다.
 * 세 자리마다 콤마를 끊는 건 문자열 조작만으로 충분해서, 굳이 플랫폼 API 에 기대지 않는다.
 */
fun won(amount: Long): String {
    val negative = amount < 0
    val digits = kotlin.math.abs(amount).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-" else "") + grouped + "원"
}
