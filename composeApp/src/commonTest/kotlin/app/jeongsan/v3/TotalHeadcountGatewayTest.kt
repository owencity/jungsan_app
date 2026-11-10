package app.jeongsan.v3

import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.ServerGathering
import app.jeongsan.v3.api.V3Gateway
import app.jeongsan.v3.api.toRooms
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotalHeadcountGatewayTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val server = json.decodeFromString<ServerGathering>(Fixtures.DETAIL_B)
    private val calls = mutableListOf<HttpRequestData>()
    private fun gateway(
        writeStatus: HttpStatusCode = HttpStatusCode.NoContent,
        readStatus: HttpStatusCode = HttpStatusCode.OK,
        total: Int? = 7,
    ): Pair<V3Gateway, V3Store> {
        val updated = server.copy(settlementUnits = server.settlementUnits.map { if (it.id == 2L) it.copy(totalHeadcount = total) else it })
        val store = V3Store(V3State.empty().copy(me = User(2, "이민지", 0), rooms = server.toRooms().associateBy { it.id }))
        val engine = MockEngine { request ->
            calls += request
            when ("${request.method.value} ${request.url.encodedPath}") {
                "PUT /api/v1/gatherings/1/settlement-units/2/total-headcount" ->
                    respond(if (writeStatus == HttpStatusCode.NoContent) "" else """{"code":"FORBIDDEN","message":"수정할 수 없어요"}""", writeStatus)
                "GET /api/v1/gatherings/1" -> respond(json.encodeToString(ServerGathering.serializer(), updated), readStatus,
                    headersOf(HttpHeaders.ContentType, "application/json"))
                else -> error("unexpected request")
            }
        }
        return V3Gateway(store, ApiClient("https://api.test", MemoryTokenStore("test"), engine)) to store
    }

    @Test fun 정확한_경로로_총인원만_보내고_204뒤_재조회된_값을_표시한다() = runTest {
        val (gw, store) = gateway()
        assertNull(gw.setTotalHeadcount(2, 7))
        assertEquals(listOf("PUT /api/v1/gatherings/1/settlement-units/2/total-headcount", "GET /api/v1/gatherings/1"),
            calls.map { "${it.method.value} ${it.url.encodedPath}" })
        val body = (calls.first().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertEquals("""{"totalHeadcount":7}""", body)
        assertEquals(7, store.state.rooms.getValue(2).totalHeadcount)
        assertNull(store.state.rooms.getValue(1).totalHeadcount)
    }

    @Test fun 저장거절은_실패를_돌려주고_화면값을_미리_바꾸지_않는다() = runTest {
        val (gw, store) = gateway(writeStatus = HttpStatusCode.Forbidden)
        assertNotNull(gw.setTotalHeadcount(2, 7))
        assertNull(store.state.rooms.getValue(2).totalHeadcount)
        assertEquals(1, calls.size)
    }

    @Test fun 저장요청후_새상태를_확인하지_못하면_성공으로_닫지_않는다() = runTest {
        val (gw, store) = gateway(readStatus = HttpStatusCode.ServiceUnavailable)
        assertNotNull(gw.setTotalHeadcount(2, 7))
        assertNull(store.state.rooms.getValue(2).totalHeadcount)
        assertEquals(2, calls.size)
    }

    @Test fun 서버가_204뒤_요청한_총인원을_돌려주지_않아도_성공으로_꾸미지_않는다() = runTest {
        val (gw, store) = gateway(total = null)
        assertNotNull(gw.setTotalHeadcount(2, 7))
        assertNull(store.state.rooms.getValue(2).totalHeadcount)
    }

    @Test fun 다른총무방_확정방_범위오류는_전송하지_않는다() = runTest {
        val (gw, store) = gateway()
        assertNotNull(gw.setTotalHeadcount(1, 7))
        assertNotNull(gw.setTotalHeadcount(2, 1))
        assertNotNull(gw.setTotalHeadcount(2, 51))
        store.replaceGathering(1, server.toRooms().map { it.copy(status = GatheringStatus.SETTLING) }, emptyList())
        assertNotNull(gw.setTotalHeadcount(2, 7))
        assertTrue(calls.isEmpty())
    }
}
