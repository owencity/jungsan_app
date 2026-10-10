package app.jeongsan.v3

import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.Made
import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.Settled
import app.jeongsan.v3.api.V3Gateway
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
import kotlin.test.assertTrue

/**
 * API 모드 gateway — 어떤 요청을 어떤 순서로 보내고, 서버 응답으로 스토어를 어떻게 채우는지. 웹 gateway 와 같은 규칙.
 * 가짜 서버는 경로별로 답한다. 보는 사람은 이민지(userId 2) — 김동규 정산방(단위 1)의 참여자, 자기 정산방(단위 2)의 총무.
 */
class GatewayTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val calls = mutableListOf<HttpRequestData>()

    /** 경로 → (상태, 본문). 없는 경로는 200 빈 본문 */
    private fun gateway(routes: Map<String, Pair<HttpStatusCode, String>>): Pair<V3Gateway, V3Store> {
        val engine = MockEngine { req ->
            calls += req
            val key = "${req.method.value} ${req.url.encodedPath}"
            val (status, body) = routes[key] ?: (HttpStatusCode.OK to "")
            respond(body, status, jsonHeaders)
        }
        val store = V3Store(V3State.empty())
        store.setMe(User(id = 2, displayName = "이민지", spoonCount = 0))
        return V3Gateway(store, ApiClient("https://api.test", MemoryTokenStore("t"), engine)) to store
    }

    private val base = mapOf(
        "GET /api/v1/me/gatherings" to (HttpStatusCode.OK to "[${Fixtures.DETAIL_B}]"),
        "GET /api/v1/me/notifications" to (HttpStatusCode.OK to "[]"),
        "GET /api/v1/gatherings/1" to (HttpStatusCode.OK to Fixtures.DETAIL_B),
    )
    private fun line(r: HttpRequestData) = "${r.method.value} ${r.url.encodedPath}"
    private fun body(r: HttpRequestData) = (r.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test fun 내_술자리를_읽으면_정산_단위마다_정산방이_스토어에_들어간다() = runTest {
        val (gw, store) = gateway(base)
        gw.loadMine()
        assertEquals(setOf(1L, 2L), store.state.rooms.keys)
        assertEquals(1L, store.state.rooms[2]?.gatheringId)
    }

    @Test fun 정산_단위_경로는_술자리_id와_단위_id를_같이_쓰고_바꾼_뒤에는_술자리를_다시_읽는다() = runTest {
        val (gw, _) = gateway(base + ("POST /api/v1/gatherings/1/settlement-units/2/rounds" to (HttpStatusCode.Created to """{"id":9}""")))
        gw.loadMine(); calls.clear()
        val r = gw.saveRound(2, RoundDraft(null, 50_000, emptyList(), 2), ResponseType.DRANK)
        assertEquals(Made.Ok(9), r)
        assertEquals(
            listOf(
                "POST /api/v1/gatherings/1/settlement-units/2/rounds",
                // 총무 본인 응답(FC-019 A) — 차수 저장 바로 뒤에, 만든 차수 id 로
                "PUT /api/v1/gatherings/1/settlement-units/2/responses/me",
                "GET /api/v1/gatherings/1",
            ),
            calls.map(::line),
        )
        assertTrue(body(calls[1]).contains(""""roundId":9""") && body(calls[1]).contains("DRANK"))
    }

    @Test fun 응답이_바뀌었으면_정산하기는_STALE로_돌려준다() = runTest {
        val (gw, _) = gateway(base + ("POST /api/v1/gatherings/1/settlement-units/2/settlement" to
            (HttpStatusCode.Conflict to """{"code":"SETTLEMENT_INPUT_CHANGED","message":"x"}""")))
        gw.loadMine()
        val res = gw.settle(2, SettlePreview(1, emptyList(), emptyList(), inputHash = "h"))
        assertEquals(Settled.Done(SettleResult.STALE), res)
        assertTrue(body(calls.last()).contains(""""inputHash":"h""""))
    }

    @Test fun 다음_차_총무가_되면_나는_늘_계산_대상에_들어가고_요청마다_멱등_키를_붙인다() = runTest {
        val (gw, _) = gateway(base + ("POST /api/v1/gatherings/1/settlement-units" to (HttpStatusCode.Created to """{"id":5}""")))
        gw.loadMine(); calls.clear()
        assertEquals(Made.Ok(5), gw.createUnit(1, listOf(3)))
        val sent = body(calls.first { it.method == HttpMethod.Post })
        assertTrue(sent.contains(""""participantIds":[2,3]"""), sent)
        assertTrue(Regex(""""requestId":"[0-9a-f-]{36}"""").containsMatchIn(sent), sent)
    }

    @Test fun 서버_오류_코드는_화면_문구로_바뀐다() = runTest {
        val (gw, _) = gateway(base + ("DELETE /api/v1/gatherings/1/settlement-units/2/participants/1" to
            (HttpStatusCode.Conflict to """{"code":"REMOVE_PAYER","message":"x"}""")))
        gw.loadMine()
        assertEquals("결제자는 뺄 수 없어요. 차수의 낸 사람을 먼저 바꿔주세요", gw.removeParticipant(2, 1))
    }

    @Test fun 알림은_서버_제목을_그대로_쓰고_명단에서_빠진_사람은_내_술자리로_보낸다() = runTest {
        val notes = """[
          {"id":1,"type":"MEMBER_EXCLUDED","gatheringId":1,"settlementUnitId":2,"title":"10/9 술자리에서 빠졌어요","body":"인원(4명) 밖이라 이번 정산에서 빠졌어요","createdAt":"2026-10-10T00:00:00Z","readAt":null},
          {"id":2,"type":"SETTLED","gatheringId":1,"settlementUnitId":2,"title":"정산이 나왔어요","body":"김동규님께 15,000원","createdAt":"2026-10-10T00:00:00Z","readAt":null}
        ]"""
        val (gw, store) = gateway(base + ("GET /api/v1/me/notifications" to (HttpStatusCode.OK to notes)))
        gw.loadMine()
        val (gone, settled) = store.state.notifications
        assertEquals("10/9 술자리에서 빠졌어요", gone.title)
        assertEquals("인원(4명) 밖이라 이번 정산에서 빠졌어요", gone.body)
        assertEquals(Target.Home, gone.target)
        assertEquals(Target.Pay(2), settled.target)
    }

    @Test fun 링크로_참여하면_고른_정산_단위로_보내고_그_정산방으로_간다() = runTest {
        val (gw, store) = gateway(base + ("POST /api/v1/join/tok" to (HttpStatusCode.Created to """{"gatheringId":1,"participantId":2}""")))
        assertEquals(Made.Ok(2), gw.join("tok", 2, mapOf(3L to ResponseType.SOBER)))
        assertTrue(body(calls.first()).contains(""""settlementUnitId":2"""))
        assertTrue(2L in store.state.rooms)
    }
}
