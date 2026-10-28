package app.jeongsan.v3.api

actual fun platformAuthenticate(url: String, onResult: (AuthBrowserResult) -> Unit) {
    onResult(AuthBrowserResult.Failed)
}

actual fun cancelPlatformAuthentication() = Unit
