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
