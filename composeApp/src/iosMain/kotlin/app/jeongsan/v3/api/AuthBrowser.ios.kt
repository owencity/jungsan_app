package app.jeongsan.v3.api

/** Swift의 ASWebAuthenticationSession과 연결한다. 화면·콜백 모두 main thread에서 호출한다. */
object IosAuthSessionBridge {
    var launcher: ((String) -> Unit)? = null
    var cancel: (() -> Unit)? = null
    private var completion: ((AuthBrowserResult) -> Unit)? = null

    fun start(url: String, onResult: (AuthBrowserResult) -> Unit) {
        val launch = launcher
        if (launch == null || completion != null) {
            onResult(AuthBrowserResult.Failed)
            return
        }
        completion = onResult
        launch(url)
    }

    fun complete(url: String?, cancelled: Boolean) {
        val callback = completion ?: return
        completion = null
        callback(when {
            cancelled -> AuthBrowserResult.Cancelled
            url != null -> AuthBrowserResult.Completed(url)
            else -> AuthBrowserResult.Failed
        })
    }

    fun cancelPending() {
        cancel?.invoke()
        complete(null, true)
    }
}

actual fun platformAuthenticate(url: String, onResult: (AuthBrowserResult) -> Unit) =
    IosAuthSessionBridge.start(url, onResult)

actual fun cancelPlatformAuthentication() = IosAuthSessionBridge.cancelPending()
