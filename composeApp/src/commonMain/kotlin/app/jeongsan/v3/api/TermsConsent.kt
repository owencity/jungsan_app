package app.jeongsan.v3.api

/** 로그인 토큰과 별도로 기록해, 과거 이름 입력을 끝내지 않은 계정도 약관을 확인하게 한다. */
class TermsConsent(private val storageForUser: (Long) -> TokenStore) {
    fun accepted(userId: Long): Boolean = userId > 0 && storageForUser(userId).get() == VERSION

    fun accept(userId: Long) {
        require(userId > 0) { "로그인으로 확인한 계정에만 약관 동의를 기록합니다." }
        storageForUser(userId).set(VERSION)
    }

    private companion object { const val VERSION = "ugc-v1" }
}

expect fun platformTermsConsent(): TermsConsent
