package app.jeongsan.v3.api

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/** Info.plist 의 `JSApiBaseUrl` — 빌드 설정 `JS_API_BASE_URL`에서 채운다(비면 목데이터 모드) */
actual val apiBaseUrl: String = (NSBundle.mainBundle.objectForInfoDictionaryKey("JSApiBaseUrl") as? String).orEmpty()

private const val KEY_TOKEN = "jeongsan.token"

// 출시 1차는 UserDefaults. 토큰은 정산 정보만 여는 열쇠라 송금 권한이 없다(API.md §2.3) — Keychain 은 출시 뒤 옮긴다
actual fun platformTokenStore(): TokenStore = object : TokenStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults
    override fun get(): String? = defaults.stringForKey(KEY_TOKEN)
    override fun set(token: String?) {
        if (token == null) defaults.removeObjectForKey(KEY_TOKEN) else defaults.setObject(token, KEY_TOKEN)
    }
}
