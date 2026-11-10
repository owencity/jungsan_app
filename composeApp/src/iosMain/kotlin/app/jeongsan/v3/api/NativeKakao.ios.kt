package app.jeongsan.v3.api

/** Swift가 카카오톡을 실행하고, 미설치이면 앱 안 계정 로그인으로 이어가도록 알려준다. */
object IosKakaoTalkBridge {
    var launcher: (() -> Unit)? = null
    var cancel: (() -> Unit)? = null
    private var completion: ((KakaoTalkResult) -> Unit)? = null

    fun start(onResult: (KakaoTalkResult) -> Unit) {
        val launch = launcher
        if (launch == null || completion != null) { onResult(KakaoTalkResult.Failed); return }
        completion = onResult
        launch()
    }

    fun complete(accessToken: String?, status: String) {
        val callback = completion ?: return
        completion = null
        callback(when (status) {
            "TOKEN" -> accessToken?.takeIf { it.isNotBlank() }?.let { KakaoTalkResult.Token(it) } ?: KakaoTalkResult.Failed
            "ACCOUNT" -> KakaoTalkResult.UseAccount
            "CANCELLED" -> KakaoTalkResult.Cancelled
            "NOT_CONFIGURED" -> KakaoTalkResult.NotConfigured
            else -> KakaoTalkResult.Failed
        })
    }

    fun cancelPending() { cancel?.invoke(); complete(null, "CANCELLED") }
}

actual fun platformKakaoTalkLogin(onResult: (KakaoTalkResult) -> Unit) = IosKakaoTalkBridge.start(onResult)
actual fun cancelPlatformKakaoTalkLogin() = IosKakaoTalkBridge.cancelPending()
