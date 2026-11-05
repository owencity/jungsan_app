package app.jeongsan.v3.api

import kotlinx.serialization.Serializable

sealed interface KakaoTalkResult {
    class Token(val accessToken: String) : KakaoTalkResult {
        override fun toString() = "KakaoTalkResult.Token([redacted])"
    }
    data object UseApple : KakaoTalkResult
    data object Cancelled : KakaoTalkResult
    data object NotConfigured : KakaoTalkResult
    data object Failed : KakaoTalkResult
}

expect fun platformKakaoTalkLogin(onResult: (KakaoTalkResult) -> Unit)
expect fun cancelPlatformKakaoTalkLogin()

@Serializable
data class NativeKakaoBody(val accessToken: String) {
    override fun toString() = "NativeKakaoBody(accessToken=[redacted])"
}

/** 취소/재시도 사이에 늦게 끝난 HTTP 요청이 다른 로그인 상태를 덮어쓰지 않게 한다. */
class NativeKakaoSession(private val client: ApiClient) {
    private var generation = 0
    private var pending: Int? = null
    private var inFlight: Int? = null
    fun start(): Int = (++generation).also { pending = it; inFlight = null }
    fun isCurrent(attempt: Int) = generation == attempt
    fun cancel() { generation++; pending = null; inFlight = null }
    suspend fun finish(attempt: Int, accessToken: String): Boolean {
        if (pending != attempt || inFlight == attempt) return false
        inFlight = attempt
        try {
            val response = client.exchangeNativeKakao(accessToken)
            if (pending != attempt || !isCurrent(attempt)) return false
            require(response.token.isNotBlank() && response.token.none { it.isWhitespace() })
            client.acceptNativeKakaoToken(response)
            pending = null
            return true
        } finally { if (inFlight == attempt) inFlight = null }
    }
}
