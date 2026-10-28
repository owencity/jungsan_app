package app.jeongsan.v3.api

/** FC-024 서버 목록과 2026-10-28 기준으로 맞춘 체험 필터. 운영 판정은 계속 서버가 담당한다. */
internal object DemoMessagePolicy {
    private val words = listOf("씨발", "시발", "개새끼", "병신", "좆", "지랄", "섹스", "강간", "fuck", "motherfucker")
    private fun normalized(text: String) = compatibilityNormalize(text).lowercase().filter { it.isLetterOrDigit() }

    fun violation(text: String): String? = when {
        text.trim().isEmpty() || text.trim().length > 200 -> "메시지는 1~200자까지 입력해주세요"
        words.any { normalized(text).contains(normalized(it)) } -> "부적절한 표현이 있어요"
        else -> null
    }
}

internal expect fun compatibilityNormalize(text: String): String
