package app.jeongsan.v3.api

// FC-034는 iOS 우선이다. Android 로그인 호출부는 기존 OAuth를 계속 사용한다.
actual fun platformKakaoTalkLogin(onResult: (KakaoTalkResult) -> Unit) = onResult(KakaoTalkResult.Failed)
actual fun cancelPlatformKakaoTalkLogin() = Unit
