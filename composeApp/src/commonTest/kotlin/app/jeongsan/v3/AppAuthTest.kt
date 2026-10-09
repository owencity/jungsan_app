package app.jeongsan.v3

import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.AppAuth
import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.Pkce
import app.jeongsan.v3.api.Sha256
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 앱 로그인(PKCE + 앱 스킴 복귀 + 티켓 교환) — 서버 `feat/auth-release` 계약 */
class AppAuthTest {
    @Test fun SHA_256은_NIST_시험값과_같다() {
        // FIPS 180-2 부록 B.1 — "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest("abc".encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
        )
    }

    @Test fun PKCE_challenge는_BASE64URL_SHA256_패딩_없음이다() {
        // 기대값은 Python hashlib + base64.urlsafe_b64encode 로 따로 계산한 값 — 서버(Java MessageDigest)와 같은 식
        assertEquals("fOEAlx9k5wAej-WlGXPs3-HO1Cvv5-6NX9YhlQa1OTw", Pkce.challenge("x".repeat(64)))
        assertEquals("qK5ubukpq-o6_PxSWMjM1vhSc-DUYm0mxyefMlD3fI4", Pkce.challenge("0123456789abcdef".repeat(4)))
    }

    @Test fun challenge는_서버가_받는_43자_URL_안전_문자다() {
        assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(Pkce.challenge("x".repeat(64))))
    }

    @Test fun 앱으로_돌아온_주소에서_티켓만_꺼낸다_로그인_주소가_아니면_무시한다() {
        assertEquals("abc-123", AppAuth.ticketOf("jeongsan://auth?ticket=abc-123"))
        assertNull(AppAuth.ticketOf("jeongsan://other?ticket=abc"))
        assertNull(AppAuth.ticketOf("https://evil.test/auth?ticket=abc"))
        assertNull(AppAuth.ticketOf("jeongsan://auth?ticket="))
    }

    @Test fun 로그인은_client_app과_codeChallenge를_붙여_브라우저로_열고_돌아오면_같은_verifier로_교환한다() = runTest {
        val calls = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            calls += req
            respond("""{"token":"tok-9","expiresAt":"2026-11-09T00:00:00Z"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val tokens = MemoryTokenStore()
        val client = ApiClient("https://api.test", tokens, engine)

        val url = AppAuth.start(client, AppAuth.Provider.KAKAO)
        assertTrue(url.startsWith("https://api.test/api/v1/auth/kakao/login?client=app&codeChallenge="))
        val challenge = url.substringAfter("codeChallenge=")

        AppAuth.handle("jeongsan://auth?ticket=T1")
        val back = assertNotNull(AppAuth.returned)
        assertNull(AppAuth.finish(client, back))

        val body = (calls.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertEquals("https://api.test/api/v1/auth/app/exchange", calls.single().url.toString())
        assertTrue(body.contains(""""ticket":"T1""""))
        // 서버는 SHA256(verifier) 가 처음 보낸 challenge 와 같은지 본다
        val verifier = Regex(""""codeVerifier":"([^"]+)"""").find(body)!!.groupValues[1]
        assertEquals(challenge, Pkce.challenge(verifier))
        assertEquals("tok-9", tokens.get())
    }

    @Test fun 브라우저에_있는_동안_앱이_종료돼_verifier가_없으면_처음부터_다시_안내한다() = runTest {
        val ok = """{"token":"t","expiresAt":"2026-11-09T00:00:00Z"}"""
        val client = ApiClient("https://api.test", MemoryTokenStore(), MockEngine { respond(ok, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        AppAuth.start(client, AppAuth.Provider.APPLE)
        AppAuth.handle("jeongsan://auth?ticket=T1")
        AppAuth.finish(client, AppAuth.returned!!) // verifier 를 쓴다
        assertEquals("로그인을 처음부터 다시 해주세요", AppAuth.finish(client, "jeongsan://auth?ticket=T2"))
    }
}
