package app.jeongsan.v3

/**
 * L2 이름 확인의 규칙 — 웹 `v3/name.ts`와 같은 길이·같은 문구.
 * 10자는 참여자 줄·타임라인·알림 문구("○○님이 보냈대요")에서 깨지지 않는 길이다.
 */
const val MAX_NAME = 10

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
    if (nameLength(n) > MAX_NAME) return listOf("이름은 ${MAX_NAME}자까지예요")
    return emptyList()
}
