package app.jeongsan.v3

/**
 * L2 이름(실명) 확인의 규칙 — 웹 `v3/name.ts`와 같은 길이·같은 문구.
 * 실명을 받는 이유: 총무가 은행 앱의 입금자명과 참여자를 맞춰 봐야 한다.
 * 형식(한글 2~5자 등)은 검사하지 않는다 — 외국인·복성·영문 이름을 막지 않기 위해서다.
 * 10자는 참여자 줄·타임라인·알림 문구("○○님이 보냈대요")에서 깨지지 않는 길이다.
 */
const val MIN_NAME = 2
const val MAX_NAME = 10
const val NAME_GUIDE = "실명을 정확하게 성까지 적어주세요! 그래야 총무가 헷갈리지 않아요!"

/** 글자 수 — 이모지 같은 서로게이트 쌍도 한 글자로 센다(웹의 [...str].length와 같게) */
fun nameLength(name: String): Int = name.codePointList().size

// JVM String.codePoints()와 이름이 겹치지 않게 따로 짓는다(공통 코드라 iOS에도 같은 구현이 필요하다)
private fun String.codePointList(): List<Int> {
    val out = mutableListOf<Int>()
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
            out += ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00) + 0x10000
            i += 2
        } else {
            out += c.code
            i += 1
        }
    }
    return out
}

/** 저장 전에 막아야 하는 것. 빈 리스트면 저장해도 된다 */
fun validateName(name: String): List<String> {
    val n = name.trim()
    if (n.isEmpty()) return listOf("이름을 넣어주세요")
    if (nameLength(n) < MIN_NAME) return listOf("성까지 적어주세요")
    if (nameLength(n) > MAX_NAME) return listOf("이름은 ${MAX_NAME}자까지예요")
    return emptyList()
}

/**
 * 목록용 이름 — `김동규(동규짱)`. 참여자 줄·관리 시트·정산 확인처럼 "누구인지 알아보는" 곳에 쓴다.
 * 타임라인·알림 같은 문장에는 이름만 쓴다(괄호까지 붙으면 한 줄을 넘는다). 웹 `nameWithNick`과 같다.
 */
fun nameWithNick(displayName: String, nickname: String?): String =
    if (!nickname.isNullOrEmpty() && nickname != displayName) "$displayName($nickname)" else displayName

fun Participant.nameWithNick(): String = nameWithNick(displayName, nickname)

/** 아바타 한 글자 — 한글 세 글자 실명은 이름 첫 글자(김동규 → 동). 성으로 하면 김씨가 여럿일 때 구분이 안 된다 */
fun initialOf(name: String): String {
    val hangul = name.isNotEmpty() && name.all { it in '가'..'힣' }
    if (hangul && name.length == 3) return name.substring(1, 2)
    // 이모지(서로게이트 쌍)를 반으로 자르지 않게
    return if (name.firstOrNull()?.isHighSurrogate() == true) name.take(2) else name.take(1)
}
