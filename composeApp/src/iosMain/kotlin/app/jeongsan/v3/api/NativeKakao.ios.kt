package app.jeongsan.v3.api

/** 실제 카카오톡 실행·미설치 시스템 안내는 Swift의 공식 SDK 어댑터가 담당한다. */
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
            "APPLE" -> KakaoTalkResult.UseApple
            "CANCELLED" -> KakaoTalkResult.Cancelled
            "NOT_CONFIGURED" -> KakaoTalkResult.NotConfigured
            else -> KakaoTalkResult.Failed
        })
    }

    fun cancelPending() { cancel?.invoke(); complete(null, "CANCELLED") }
}

actual fun platformKakaoTalkLogin(onResult: (KakaoTalkResult) -> Unit) = IosKakaoTalkBridge.start(onResult)
actual fun cancelPlatformKakaoTalkLogin() = IosKakaoTalkBridge.cancelPending()
