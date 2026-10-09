package app.jeongsan.v3.api

import android.content.Context
import app.jeongsan.BuildConfig

actual val apiBaseUrl: String = BuildConfig.API_BASE_URL

/** MainActivity 가 시작할 때 넣는다 — 토큰 보관소가 SharedPreferences 를 열 때 필요하다 */
object AndroidAppContext {
    lateinit var context: Context
}

private const val PREFS = "jeongsan_auth"
private const val KEY_TOKEN = "token"

actual fun platformTokenStore(): TokenStore = prefsStore(KEY_TOKEN)

actual fun platformVerifierStore(): TokenStore = prefsStore("verifier")

private fun prefsStore(key: String): TokenStore = object : TokenStore {
    private val prefs get() = AndroidAppContext.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString(key, null)
    override fun set(token: String?) {
        // commit — 바로 브라우저로 넘어가므로 비동기 저장(apply)이 끝나기 전에 앱이 내려가면 안 된다
        prefs.edit().apply { if (token == null) remove(key) else putString(key, token) }.commit()
    }
}

actual val platformIsIos: Boolean = false
