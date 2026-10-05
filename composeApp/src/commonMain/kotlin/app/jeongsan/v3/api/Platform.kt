package app.jeongsan.v3.api

/**
 * 서버 주소. 비어 있으면 목데이터 모드 — 웹의 `VITE_JEONGSAN_API_BASE_URL`과 같은 역할.
 * Android: 빌드 시 `JS_API_BASE_URL`(환경변수·local.properties) → BuildConfig. iOS: Info.plist `JSApiBaseUrl`.
 */
expect val apiBaseUrl: String

/** 기기에 남는 토큰 보관소 — 앱을 껐다 켜도 로그인이 유지되게 */
expect fun platformTokenStore(): TokenStore
