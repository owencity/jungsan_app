package app.jeongsan.v3.api

import app.jeongsan.v3.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * v3 백엔드 호출 — 웹 `v3/api.ts`와 같은 계약(`jungsan_attack` `docs/API.md` v4~, FC-014).
 *
 * 웹과 다른 점: 앱은 쿠키를 받을 수 없어 **Bearer 토큰**으로 인증한다(FC-014 1-1). 토큰은 [TokenStore]에 둔다.
 * 서버 주소([apiBaseUrl])가 비어 있으면 목데이터 모드이고 이 클래스는 쓰이지 않는다.
 */
class ApiClient(
    private val baseUrl: String,
    private val tokens: TokenStore,
    engine: HttpClientEngine? = null,
) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @PublishedApi internal val http: HttpClient = (engine?.let { HttpClient(it) { setup() } } ?: HttpClient { setup() })

    private fun io.ktor.client.HttpClientConfig<*>.setup() {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    /** 오류면 [ApiError]를 던진다. 서버에 닿지 못하면 status 0 · `NETWORK_ERROR` */
    @PublishedApi
    internal suspend fun send(method: HttpMethod, path: String, body: Any? = null): HttpResponse {
        val res = try {
            http.request("$baseUrl$path") {
                this.method = method
                tokens.get()?.let { bearerAuth(it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (e: Exception) {
            throw ApiError(0, NETWORK_ERROR, "연결이 불안정해요. 잠시 뒤 다시 시도해주세요")
        }
        if (!res.status.isSuccess()) {
            val text = res.bodyAsText()
            val err = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
            throw ApiError(res.status.value, err?.code ?: "HTTP_${res.status.value}", err?.message ?: "잠시 뒤 다시 시도해주세요")
        }
        return res
    }

    @PublishedApi
    internal suspend inline fun <reified T> call(method: HttpMethod, path: String, body: Any? = null): T =
        json.decodeFromString(send(method, path, body).bodyAsText())

    // ── 엔드포인트 ────────────────────────────────────

    /** 로그인 여부 + 내 정보. 401이면 로그아웃 상태 */
    suspend fun me(): MeResponse = call(HttpMethod.Get, "/api/v1/auth/me")

    /** 실명 최초 등록(FC-013). 400 형식 오류 · 409 `DISPLAY_NAME_ALREADY_SET` */
    suspend fun putDisplayName(displayName: String): MeResponse =
        call(HttpMethod.Put, "/api/v1/users/me/display-name", DisplayNameRequest(displayName))

    /** 앱 로그인 마지막 단계(FC-014 1-1) — 앱 스킴으로 돌아온 1회용 티켓을 토큰으로 바꾸고 저장한다 */
    suspend fun exchangeTicket(ticket: String): TokenResponse =
        call<TokenResponse>(HttpMethod.Post, "/api/v1/auth/app/exchange", TicketRequest(ticket)).also { tokens.set(it.token) }

    /** 앱 안 브라우저로 여는 카카오 로그인 주소(FC-014 1-1). 서버가 `jeongsan://auth?ticket=…`으로 돌려보낸다 */
    fun kakaoLoginUrl(): String = "$baseUrl/api/v1/auth/kakao/login?client=app"

    fun logout() = tokens.set(null)

    companion object {
        const val NETWORK_ERROR = "NETWORK_ERROR"
    }
}

/** API.md §1.2 오류. `code`로 화면 문구를 고르고, 없으면 서버 `message`를 그대로 보여준다 */
class ApiError(val status: Int, val code: String, message: String) : Exception(message)

/** Bearer 토큰 보관소 — Android SharedPreferences, iOS UserDefaults([platformTokenStore]) */
interface TokenStore {
    fun get(): String?
    fun set(token: String?)
}

/** 테스트·목데이터 모드용 */
class MemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun get() = token
    override fun set(token: String?) { this.token = token }
}

// ── 서버 DTO (server `UserDto.kt` 등과 같은 모양) ─────────────

@Serializable
data class MeResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
    /** 등록한 실명. 아직 안 받았으면 null(FC-013) */
    val displayName: String? = null,
    val needsName: Boolean,
)

@Serializable data class DisplayNameRequest(val displayName: String)
@Serializable data class TicketRequest(val ticket: String)
@Serializable data class TokenResponse(val token: String, val expiresAt: String)
@Serializable internal data class ErrorBody(val code: String? = null, val message: String? = null)

/**
 * 서버의 내 정보를 화면 모델로 — 웹 `toUser`와 같다. 실명이 없으면 이름은 빈칸(L2가 먼저 뜬다).
 * 스푼·계좌는 이 응답에 아직 없어서 같은 사람이면 이전 값을 이어 쓴다.
 */
fun MeResponse.toUser(prev: User?): User {
    val same = prev?.id == id
    return User(
        id = id,
        displayName = displayName.orEmpty(),
        spoonCount = if (same) prev!!.spoonCount else 0,
        payout = if (same) prev!!.payout else null,
        needsName = needsName,
        nickname = nickname,
    )
}
