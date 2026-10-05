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

actual fun platformTokenStore(): TokenStore = object : TokenStore {
    private val prefs get() = AndroidAppContext.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString(KEY_TOKEN, null)
    override fun set(token: String?) {
        prefs.edit().apply { if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token) }.apply()
    }
}
