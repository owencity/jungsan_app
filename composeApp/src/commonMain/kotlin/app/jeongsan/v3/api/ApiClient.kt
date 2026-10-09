package app.jeongsan.v3.api

import app.jeongsan.v3.Payout
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
import io.ktor.http.encodeURLPathPart
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

    /** 앱 로그인 마지막 단계(FC-014 1-1) — 앱 스킴으로 돌아온 1회용 티켓과 PKCE verifier 로 토큰을 받아 저장한다 */
    suspend fun exchangeTicket(ticket: String, codeVerifier: String): TokenResponse =
        call<TokenResponse>(HttpMethod.Post, "/api/v1/auth/app/exchange", TicketRequest(ticket, codeVerifier)).also { tokens.set(it.token) }

    /** 브라우저로 여는 로그인 주소(FC-014 1-1). 서버가 `jeongsan://auth?ticket=…`으로 돌려보낸다 — [AppAuth] */
    fun loginUrl(provider: AppAuth.Provider, codeChallenge: String): String =
        "$baseUrl/api/v1/auth/${provider.path}/login?client=app&codeChallenge=$codeChallenge"

    /** 이 기기의 토큰만 버린다(서버를 부르지 않음) — 만료된 토큰 정리용 */
    fun logout() = tokens.set(null)

    /** 로그아웃 — 서버 세션을 끊고(실패해도) 기기의 토큰을 버린다 */
    suspend fun signOut() {
        runCatching { exec(HttpMethod.Post, "/api/v1/auth/logout") }
        tokens.set(null)
    }

    /** 회원 탈퇴(App Store 5.1.1(v)) — 성공하면 기기의 토큰도 버린다 */
    suspend fun deleteAccount() {
        exec(HttpMethod.Delete, "/api/v1/users/me")
        tokens.set(null)
    }

    /** 응답 본문이 없거나 쓰지 않는 요청 */
    private suspend fun exec(method: HttpMethod, path: String, body: Any? = null) { send(method, path, body) }

    private fun u(gid: Long, uid: Long) = "/api/v1/gatherings/$gid/settlement-units/$uid"
    private fun enc(token: String) = token.encodeURLPathPart()

    suspend fun putPayout(p: PayoutBody) = exec(HttpMethod.Put, "/api/v1/users/me/payout", p)

    // 술자리 — 응답은 술자리 전체([ServerGathering]). 화면은 toRooms 로 정산방들로 바꾼다
    suspend fun myGatherings(): List<ServerGathering> = call(HttpMethod.Get, "/api/v1/me/gatherings")
    suspend fun gathering(gid: Long): ServerGathering = call(HttpMethod.Get, "/api/v1/gatherings/$gid")
    suspend fun createGathering(): ServerGathering = call(HttpMethod.Post, "/api/v1/gatherings", EmptyBody())
    suspend fun sendMessage(gid: Long, text: String) = exec(HttpMethod.Post, "/api/v1/gatherings/$gid/messages", MessageBody(text))
    suspend fun joinPreview(token: String): ServerJoinPreview = call(HttpMethod.Get, "/api/v1/join/${enc(token)}")
    suspend fun join(token: String, unitId: Long, responses: List<AnswerBody>): JoinResult =
        call(HttpMethod.Post, "/api/v1/join/${enc(token)}", JoinBody(unitId, responses))

    // 정산 단위 — 다음 차 총무(FC-015)
    suspend fun createUnit(gid: Long, requestId: String, participantIds: List<Long>, headcount: Int? = null): IdBody =
        call(HttpMethod.Post, "/api/v1/gatherings/$gid/settlement-units", UnitBody(requestId, participantIds, headcount))
    /** 인원(FC-020) — 총무·정산 전. 바꾼 뒤 서버가 자동 정산 판정을 한 번 돈다 */
    suspend fun putHeadcount(gid: Long, uid: Long, headcount: Int) = exec(HttpMethod.Put, "${u(gid, uid)}/headcount", HeadcountBody(headcount))
    suspend fun removeMember(gid: Long, uid: Long, pid: Long) = exec(HttpMethod.Delete, "${u(gid, uid)}/participants/$pid")
    suspend fun addRound(gid: Long, uid: Long, body: RoundBody): IdBody = call(HttpMethod.Post, "${u(gid, uid)}/rounds", body)
    suspend fun putRound(gid: Long, uid: Long, rid: Long, body: RoundBody): IdBody = call(HttpMethod.Put, "${u(gid, uid)}/rounds/$rid", body)
    suspend fun deleteRound(gid: Long, uid: Long, rid: Long) = exec(HttpMethod.Delete, "${u(gid, uid)}/rounds/$rid")
    suspend fun respond(gid: Long, uid: Long, answers: List<AnswerBody>) = exec(HttpMethod.Put, "${u(gid, uid)}/responses/me", AnswersBody(answers))
    suspend fun respondFor(gid: Long, uid: Long, pid: Long, answers: List<AnswerBody>) =
        exec(HttpMethod.Put, "${u(gid, uid)}/participants/$pid/responses", AnswersBody(answers))
    suspend fun preview(gid: Long, uid: Long): ServerPreview = call(HttpMethod.Get, "${u(gid, uid)}/settlement/preview")
    suspend fun settle(gid: Long, uid: Long, inputRevision: Int, inputHash: String): ServerGathering =
        call(HttpMethod.Post, "${u(gid, uid)}/settlement", SettleBody(inputRevision, inputHash))
    suspend fun markViewed(gid: Long, uid: Long) = exec(HttpMethod.Post, "${u(gid, uid)}/settlement/viewed")

    // 송금 — 송금자·수취인만
    suspend fun sent(tid: Long) = exec(HttpMethod.Post, "/api/v1/transfers/$tid/sent")
    suspend fun confirm(tid: Long) = exec(HttpMethod.Post, "/api/v1/transfers/$tid/confirm")
    suspend fun notReceived(tid: Long) = exec(HttpMethod.Post, "/api/v1/transfers/$tid/not-received")

    // 알림
    suspend fun notifications(): List<ServerNotification> = call(HttpMethod.Get, "/api/v1/me/notifications")
    suspend fun readNotification(id: Long) = exec(HttpMethod.Post, "/api/v1/me/notifications/$id/read")
    suspend fun readAllNotifications() = exec(HttpMethod.Post, "/api/v1/me/notifications/read-all")

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
    /** 본인 계좌(FC-014 §9) — 없으면 null */
    val payout: SPayout? = null,
    val spoonCount: Int? = null,
    val unreadNotificationCount: Int? = null,
)

@Serializable data class ServerNotification(
    val id: Long, val type: String, val gatheringId: Long, val settlementUnitId: Long? = null,
    val title: String = "", val body: String = "", val createdAt: String, val readAt: String? = null,
)

@Serializable data class PayoutBody(val bank: String, val accountNo: String, val holder: String)
@Serializable class EmptyBody
@Serializable data class MessageBody(val text: String)
@Serializable data class AnswerBody(val roundId: Long, val type: String)
@Serializable data class AnswersBody(val answers: List<AnswerBody>)
@Serializable data class JoinBody(val settlementUnitId: Long, val responses: List<AnswerBody>)
@Serializable data class JoinResult(val gatheringId: Long, val participantId: Long)
@Serializable data class UnitBody(val requestId: String, val participantIds: List<Long>, val headcount: Int? = null)
@Serializable data class HeadcountBody(val headcount: Int)
@Serializable data class IdBody(val id: Long)
@Serializable data class RoundBody(val total: Long, val payerParticipantId: Long, val drinks: List<SDrink>)
@Serializable data class SettleBody(val inputRevision: Int, val inputHash: String)

@Serializable data class DisplayNameRequest(val displayName: String)
@Serializable data class TicketRequest(val ticket: String, val codeVerifier: String)
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
        // 서버가 주면 서버 값, 아직 안 주는 서버면 같은 사람의 이전 값
        spoonCount = spoonCount ?: if (same) prev!!.spoonCount else 0,
        payout = payout?.let { Payout(it.bank, it.accountNo, it.holder) } ?: if (same) prev!!.payout else null,
        needsName = needsName,
        nickname = nickname,
    )
}
