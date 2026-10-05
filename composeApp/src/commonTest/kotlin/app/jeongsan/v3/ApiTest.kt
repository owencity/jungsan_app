package app.jeongsan.v3

import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.ApiError
import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.MeResponse
import app.jeongsan.v3.api.V3Gateway
import app.jeongsan.v3.api.toUser
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** API 연결 계층 — 웹 `api.test.ts`와 같은 규칙. 서버는 Ktor MockEngine 으로 흉내 낸다 */
class ApiTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val me = """{"id":7,"nickname":"🌸봄이🌸","profileImageUrl":null,"displayName":null,"needsName":true}"""
    private val named = """{"id":7,"nickname":"🌸봄이🌸","profileImageUrl":null,"displayName":"김동규","needsName":false}"""

    /** 가짜 서버 — 받은 요청을 기록하고, 정해 둔 응답을 차례로 돌려준다 */
    private fun server(vararg responses: Pair<HttpStatusCode, String>): Pair<MockEngine, MutableList<HttpRequestData>> {
        val calls = mutableListOf<HttpRequestData>()
        val queue = ArrayDeque(responses.toList())
        val engine = MockEngine { req ->
            calls += req
            val (status, body) = queue.removeFirstOrNull() ?: (HttpStatusCode.InternalServerError to "")
            respond(body, status, json)
        }
        return engine to calls
    }

    private fun bodyText(req: HttpRequestData) = (req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test fun 앱은_쿠키_대신_Bearer_토큰으로_인증한다() = runTest {
        val (engine, calls) = server(HttpStatusCode.OK to me)
        ApiClient("https://api.test", MemoryTokenStore("tok-1"), engine).me()
        assertEquals("https://api.test/api/v1/auth/me", calls[0].url.toString())
        assertEquals("Bearer tok-1", calls[0].headers[HttpHeaders.Authorization])
    }

    @Test fun 토큰이_없으면_Authorization_헤더를_보내지_않는다() = runTest {
        val (engine, calls) = server(HttpStatusCode.Unauthorized to """{"code":"UNAUTHENTICATED","message":"x"}""")
        assertFailsWith<ApiError> { ApiClient("https://api.test", MemoryTokenStore(), engine).me() }
        assertNull(calls[0].headers[HttpHeaders.Authorization])
    }

    @Test fun 실명_등록은_PUT_JSON_본문으로_보낸다() = runTest {
        val (engine, calls) = server(HttpStatusCode.OK to named)
        ApiClient("https://api.test", MemoryTokenStore("t"), engine).putDisplayName("김동규")
        assertEquals(HttpMethod.Put, calls[0].method)
        assertEquals("https://api.test/api/v1/users/me/display-name", calls[0].url.toString())
        assertEquals("""{"displayName":"김동규"}""", bodyText(calls[0]))
    }

    @Test fun 오류_응답은_code와_status를_담은_ApiError가_된다() = runTest {
        val (engine, _) = server(HttpStatusCode.Conflict to """{"code":"DISPLAY_NAME_ALREADY_SET","message":"이미 등록","errors":null}""")
        val e = assertFailsWith<ApiError> { ApiClient("https://api.test", MemoryTokenStore("t"), engine).putDisplayName("김동규") }
        assertEquals(409, e.status)
        assertEquals("DISPLAY_NAME_ALREADY_SET", e.code)
    }

    @Test fun 티켓을_교환하면_받은_토큰을_보관하고_다음_요청부터_쓴다() = runTest {
        val tokens = MemoryTokenStore()
        val (engine, calls) = server(HttpStatusCode.OK to """{"token":"tok-new","expiresAt":"2026-11-05T00:00:00Z"}""", HttpStatusCode.OK to me)
        val c = ApiClient("https://api.test", tokens, engine)
        c.exchangeTicket("ticket-1")
        assertEquals("tok-new", tokens.get())
        c.me()
        assertEquals("Bearer tok-new", calls[1].headers[HttpHeaders.Authorization])
    }

    @Test fun 실명이_없으면_이름은_빈칸이고_needsName은_그대로다() {
        val u = MeResponse(7, "🌸봄이🌸", null, null, true).toUser(null)
        assertEquals("", u.displayName)
        assertEquals("🌸봄이🌸", u.nickname)
        assertTrue(u.needsName)
    }

    @Test fun 같은_사람이면_응답에_없는_스푼과_계좌는_이전_값을_이어_쓴다() {
        val prev = User(7, "", 12, Payout("국민", "1", "김"))
        assertEquals(12, MeResponse(7, "n", null, "김동규", false).toUser(prev).spoonCount)
        assertEquals(0, MeResponse(7, "n", null, "김동규", false).toUser(prev.copy(id = 8)).spoonCount)
    }

    @Test fun 로그인_확인은_내_정보를_스토어에_넣고_true() = runTest {
        val (engine, _) = server(HttpStatusCode.OK to me)
        val store = V3Store(V3State.empty())
        assertTrue(V3Gateway(store, ApiClient("https://api.test", MemoryTokenStore("t"), engine)).loadMe())
        assertEquals(7L, store.state.me.id)
        assertTrue(store.state.me.needsName)
    }

    @Test fun 로그인_확인이_401이면_만료된_토큰을_버리고_false() = runTest {
        val tokens = MemoryTokenStore("old")
        val (engine, _) = server(HttpStatusCode.Unauthorized to """{"code":"TOKEN_EXPIRED","message":"x"}""")
        assertFalse(V3Gateway(V3Store(V3State.empty()), ApiClient("https://api.test", tokens, engine)).loadMe())
        assertNull(tokens.get())
    }

    @Test fun 실명_등록_성공은_서버_응답으로_needsName을_푼다() = runTest {
        val (engine, _) = server(HttpStatusCode.OK to named)
        val store = V3Store(V3State.empty())
        assertNull(V3Gateway(store, ApiClient("https://api.test", MemoryTokenStore("t"), engine)).confirmName("  김동규  "))
        assertEquals("김동규", store.state.me.displayName)
        assertFalse(store.state.me.needsName)
    }

    @Test fun 이미_등록한_사람이면_바꿀_수_없다는_문구를_돌려준다() = runTest {
        val (engine, _) = server(HttpStatusCode.Conflict to """{"code":"DISPLAY_NAME_ALREADY_SET","message":"conflict"}""")
        assertEquals(
            "이미 이름을 정했어요. 정한 이름은 바꿀 수 없어요",
            V3Gateway(V3Store(V3State.empty()), ApiClient("https://api.test", MemoryTokenStore("t"), engine)).confirmName("김동규"),
        )
    }

    @Test fun 규칙에_안_맞는_이름은_서버까지_보내지_않는다() = runTest {
        val (engine, calls) = server()
        assertEquals("성까지 적어주세요", V3Gateway(V3Store(V3State.empty()), ApiClient("https://api.test", MemoryTokenStore("t"), engine)).confirmName("규"))
        assertTrue(calls.isEmpty())
    }

    @Test fun 목데이터_모드는_서버_없이_스토어가_바로_바꾼다() = runTest {
        val store = V3Store()
        val gw = V3Gateway(store, null)
        assertFalse(gw.isApiMode)
        assertNull(gw.confirmName("김동구"))
        assertEquals("김동구", store.state.me.displayName)
    }

    @Test fun API_모드는_가짜_술자리_없이_빈_상태로_시작한다() {
        val s = V3State.empty()
        assertTrue(s.rooms.isEmpty())
        assertTrue(s.users.isEmpty())
        assertTrue(s.notifications.isEmpty())
    }
}
