package app.jeongsan.v3

import app.jeongsan.v3.api.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NativeKakaoTest {
    private val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val response = """{"token":"our-app-token","expiresAt":"2026-12-01T00:00:00Z"}"""

    @Test fun 제공자_토큰은_서버로만_보내고_우리_토큰만_저장한다() = runTest {
        val tokens = MemoryTokenStore()
        val client = ApiClient("https://api.test", tokens, MockEngine { request ->
            assertEquals("https://api.test/api/v1/auth/kakao/native", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            assertEquals("""{"accessToken":"provider-token"}""", (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            assertNull(tokens.get())
            respond(response, HttpStatusCode.OK, responseHeaders)
        })
        val session = NativeKakaoSession(client)
        assertTrue(session.finish(session.start(), "provider-token"))
        assertEquals("our-app-token", tokens.get())
        assertFalse(NativeKakaoBody("provider-token").toString().contains("provider-token"))
        assertFalse(KakaoTalkResult.Token("provider-token").toString().contains("provider-token"))
    }

    @Test fun 취소_전달_뒤에는_서버_호출도_저장도_하지않는다() = runTest {
        val client = ApiClient("https://api.test", MemoryTokenStore(), MockEngine { error("취소한 시도의 서버 호출") })
        val session = NativeKakaoSession(client)
        val attempt = session.start()
        session.cancel()
        assertFalse(session.finish(attempt, "provider-token"))
    }

    @Test fun 취소_후_늦은_응답은_새_시도의_토큰을_덮어쓰지_않는다() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val tokens = MemoryTokenStore()
        val client = ApiClient("https://api.test", tokens, MockEngine {
            calls++
            if (calls == 1) {
                entered.complete(Unit); release.await()
                respond(response.replace("our-app-token", "old-token"), HttpStatusCode.OK, responseHeaders)
            } else respond(response, HttpStatusCode.OK, responseHeaders)
        })
        val session = NativeKakaoSession(client)
        val first = session.start()
        val oldResult = async(start = CoroutineStart.UNDISPATCHED) { session.finish(first, "old-provider-token") }
        entered.await()
        session.cancel()
        assertTrue(session.finish(session.start(), "new-provider-token"))
        release.complete(Unit)
        assertFalse(oldResult.await())
        assertEquals("our-app-token", tokens.get())
    }

    @Test fun 중복_SDK_콜백은_동시에_교환하지_않는다() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val client = ApiClient("https://api.test", MemoryTokenStore(), MockEngine {
            calls++; entered.complete(Unit); release.await()
            respond(response, HttpStatusCode.OK, responseHeaders)
        })
        val session = NativeKakaoSession(client)
        val attempt = session.start()
        val result = async(start = CoroutineStart.UNDISPATCHED) { session.finish(attempt, "provider-token") }
        entered.await()
        assertFalse(session.finish(attempt, "provider-token"))
        release.complete(Unit)
        assertTrue(result.await())
        assertFalse(session.finish(attempt, "provider-token"))
        assertEquals(1, calls)
    }

    @Test fun 서버의_인증_거절에는_자격을_저장하지_않는다() = runTest {
        val tokens = MemoryTokenStore()
        val client = ApiClient("https://api.test", tokens, MockEngine {
            respond("""{"code":"UNAUTHENTICATED","message":"인증이 필요합니다."}""", HttpStatusCode.Unauthorized, responseHeaders)
        })
        val session = NativeKakaoSession(client)
        assertFailsWith<ApiError> { session.finish(session.start(), "bad-provider-token") }
        assertNull(tokens.get())
    }
}
