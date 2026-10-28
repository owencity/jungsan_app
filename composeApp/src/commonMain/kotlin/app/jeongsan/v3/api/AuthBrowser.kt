package app.jeongsan.v3.api

sealed interface AuthBrowserResult {
    data class Completed(val url: String) : AuthBrowserResult
    data object Cancelled : AuthBrowserResult
    data object Failed : AuthBrowserResult
}

/** iOS 시스템 인증 화면만 사용한다. Android의 기존 브라우저 복귀는 App에서 유지한다. */
expect fun platformAuthenticate(url: String, onResult: (AuthBrowserResult) -> Unit)
expect fun cancelPlatformAuthentication()
