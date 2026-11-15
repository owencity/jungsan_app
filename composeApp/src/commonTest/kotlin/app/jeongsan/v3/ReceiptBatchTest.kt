package app.jeongsan.v3

import app.jeongsan.v3.api.*
import app.jeongsan.v3.receipt.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReceiptBatchTest {
    private val candidate = ReceiptCandidate(2, 10_000, emptyList(), emptyList(), true)

    @Test fun 같은차수의_영수증을_합산하고_교체와_추가를_구분한다() {
        val original = ReceiptInput(50_000, listOf(DrinkItem("소주", 5_000, 2)))
        val photos = listOf(ReceiptInput(20_000, listOf(DrinkItem("맥주", 6_000, 1))), ReceiptInput(30_000, emptyList()))
        assertEquals(50_000, combineReceiptInputs(original, photos, ReceiptApplyMode.REPLACE)!!.total)
        assertEquals(listOf("맥주"), combineReceiptInputs(original, photos, ReceiptApplyMode.REPLACE)!!.drinks.map { it.name })
        val added = combineReceiptInputs(original, photos, ReceiptApplyMode.ADD)!!
        assertEquals(100_000, added.total)
        assertEquals(listOf("소주", "맥주"), added.drinks.map { it.name })
        assertEquals(50_000, original.total)
    }

    @Test fun 각사진이_유효해도_합계한도를_넘으면_반영하지않는다() {
        assertNull(combineReceiptInputs(ReceiptInput(0, emptyList()),
            listOf(ReceiptInput(9_000_000, emptyList()), ReceiptInput(2_000_000, emptyList())), ReceiptApplyMode.REPLACE))
        assertNull(combineReceiptInputs(ReceiptInput(0, emptyList()), emptyList(), ReceiptApplyMode.REPLACE))
    }

    @Test fun 술항목_100개제한은_사진별과_합산후_모두_검사한다() {
        val row = ReceiptItemDraft("소주", "1", "1")
        assertNotNull(ReceiptReviewDraft("1000", List(100) { row }, true).checkedInput())
        assertNull(ReceiptReviewDraft("1000", List(101) { row }, true).checkedInput())
        val partial = ReceiptInput(1000, List(60) { DrinkItem("소주", 1, 1) })
        assertNull(combineReceiptInputs(ReceiptInput(0, emptyList()), listOf(partial, partial), ReceiptApplyMode.REPLACE))
        assertNull(combineReceiptInputs(partial, listOf(partial), ReceiptApplyMode.ADD))
    }

    @Test fun 동일파일은_내용으로_중복을_확인한다() {
        assertTrue(isDuplicateReceipt(listOf(byteArrayOf(1, 2, 3)), byteArrayOf(1, 2, 3)))
        assertFalse(isDuplicateReceipt(listOf(byteArrayOf(1, 2, 3)), byteArrayOf(1, 2, 4)))
    }

    @Test fun 한장씩_처리하고_중간사진실패는_다음사진을_막지않는다() = runTest {
        val events = mutableListOf<String>()
        var running = 0
        var maxRunning = 0
        readReceiptQueue((1..3).map { it to byteArrayOf(it.toByte()) }, extract = { bytes ->
            running++; maxRunning = maxOf(running, maxRunning)
            delay(10)
            running--
            if (bytes[0] == 2.toByte()) throw ApiError(422, "IMAGE_INVALID", "invalid")
            candidate
        }, onStart = { events += "start$it" }, onResult = { id, result, error -> events += if (result != null) "ok$id" else "error$id"; if (id == 2) assertNotNull(error) })
        assertEquals(1, maxRunning)
        assertEquals(listOf("start1", "ok1", "start2", "error2", "start3", "ok3"), events)
    }

    @Test fun 서버가_바쁘거나_제한되면_남은사진은_자동재시도하지않는다() = runTest {
        for (status in listOf(429, 503)) {
            val started = mutableListOf<Int>()
            readReceiptQueue((1..3).map { it to byteArrayOf(it.toByte()) },
                extract = { throw ApiError(status, "OCR_BUSY", "busy", retryAfterSeconds = 5) },
                onStart = { started += it }, onResult = { _, result, failure -> assertNull(result); assertNotNull(failure) })
            assertEquals(listOf(1), started)
        }
    }

    @Test fun 중단된사진뒤에는_추가요청하지않는다() = runTest {
        var calls = 0
        assertFailsWith<CancellationException> {
            readReceiptQueue(listOf(1 to byteArrayOf(1), 2 to byteArrayOf(2)),
                extract = { calls++; throw CancellationException() }, onStart = {}, onResult = { _, _, _ -> fail("취소를 실패 응답으로 바꾸지 않는다") })
        }
        assertEquals(1, calls)
    }

    @Test fun OCR차수저장은_다른기기수정을_검사할버전을_명시한다() {
        val json = Json { explicitNulls = false }
        assertContains(json.encodeToString(RoundBody(10_000, 1, emptyList(), 7, 12)), "\"expectedInputRevision\":12")
        assertFalse(json.encodeToString(RoundBody(10_000, 1, emptyList(), 7)).contains("expectedInputRevision"))
    }

    @Test fun 영수증_반영은_음주응답과_결제자와_인원을_바꾸지않는다() = runTest {
        val initial = V3State.initial()
        val before = initial.rooms.values.first { it.status == GatheringStatus.OPEN && it.hostUserId == initial.me.id }
        val round = before.rounds.first()
        // 응답이 없는 총무를 자동으로 알코올 참석으로 만들면 안 된다.
        val original = before.copy(responses = before.responses.filterNot { it.participantId == before.host().id }, totalHeadcount = 7)
        val store = V3Store(initial.copy(rooms = initial.rooms + (original.id to original)))
        val gateway = V3Gateway(store, null)
        assertNull(gateway.applyReceiptToRound(original.id, round.id, ReceiptInput(round.total, round.drinks), ReceiptInput(round.total + 100, round.drinks)))
        val updated = store.state.rooms.getValue(original.id)
        assertEquals(original.responses, updated.responses)
        assertEquals(7, updated.totalHeadcount)
        assertEquals(round.payerParticipantId, updated.rounds.first { it.id == round.id }.payerParticipantId)
        assertEquals(round.expectedHeadcount, updated.rounds.first { it.id == round.id }.expectedHeadcount)
    }

    @Test fun 다른화면에서_금액을_바꿨으면_영수증으로_덮어쓰지않는다() = runTest {
        val store = V3Store(V3State.initial())
        val before = store.state.rooms.values.first { it.status == GatheringStatus.OPEN && it.hostUserId == store.state.me.id }
        val round = before.rounds.first()
        val message = V3Gateway(store, null).applyReceiptToRound(before.id, round.id,
            ReceiptInput(round.total - 100, round.drinks), ReceiptInput(round.total + 100, round.drinks))
        assertNotNull(message)
        assertEquals(before, store.state.rooms.getValue(before.id))
    }

    @Test fun 간단OCR은_술자리생성없이_로그인인증으로_총액후보를받는다() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/receipts/extract", request.url.encodedPath)
            assertEquals("Bearer host", request.headers[HttpHeaders.Authorization])
            respond("""{"schemaVersion":2,"total":10000,"drinks":[],"warnings":[],"requiresConfirmation":true}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        assertEquals(candidate, ApiClient("https://api.test", MemoryTokenStore("host"), engine).extractQuickReceipt(byteArrayOf(1)))
    }

    @Test fun 간단정산은_서버의_1원조정결과를_그대로_받는다() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/settlements/quick", request.url.encodedPath)
            assertEquals("Bearer host", request.headers[HttpHeaders.Authorization])
            respond("""{"total":10000,"headcount":3,"participantAmount":3334,"payerAmount":3332,"incomingTotal":6668}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = ApiClient("https://api.test", MemoryTokenStore("host"), engine).quickSettlement(10_000, 3)
        assertEquals(QuickSettlementResult(10_000, 3, 3334, 3332, 6668), result)
    }

    @Test fun 간단정산도_데모계정을_운영계정으로_취급하지않는다() = runTest {
        val gateway = V3Gateway(V3Store(V3State.empty()), null, isDemoMode = true)
        assertFailsWith<ApiError> { gateway.quickSettlement(10_000, 3) }
        assertFailsWith<ApiError> { gateway.extractQuickReceipt(byteArrayOf(1)) }
    }
}
