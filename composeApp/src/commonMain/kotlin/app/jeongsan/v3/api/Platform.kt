package app.jeongsan.v3.api

/**
 * 서버 주소. 비어 있으면 목데이터 모드 — 웹의 `VITE_JEONGSAN_API_BASE_URL`과 같은 역할.
 * Android: 빌드 시 `JS_API_BASE_URL`(환경변수·local.properties) → BuildConfig. iOS: Info.plist `JSApiBaseUrl`.
 */
expect val apiBaseUrl: String

/** 기기에 남는 토큰 보관소 — 앱을 껐다 켜도 로그인이 유지되게 */
expect fun platformTokenStore(): TokenStore

/** iOS 면 true — Apple 로그인 버튼은 iOS 에만 둔다(App Store 4.8: 카카오 로그인을 주면 Apple 로그인도) */
expect val platformIsIos: Boolean

/** 로그인 중 verifier 보관소(AppAuth) — 브라우저에 다녀오는 동안 앱이 내려가도 남게 기기에 둔다 */
expect fun platformVerifierStore(): TokenStore
