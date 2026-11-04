package app.jeongsan.v3

import app.jeongsan.v3.api.*
import app.jeongsan.v3.receipt.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReceiptTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val candidate = ReceiptCandidate(2,50_000,listOf(
        ReceiptDrinkCandidate("소주","SOJU","BOTTLE",5_000,3,15_000,"UNKNOWN",360,3,0.98,emptyList())
    ),emptyList(),true)
    private val response = """{"schemaVersion":2,"total":50000,"drinks":[{"name":"소주","category":"SOJU","unit":"BOTTLE","unitPrice":5000,"quantity":3,"lineAmount":15000,"servingType":"UNKNOWN","volumeMl":360,"bottleCount":3,"confidence":0.98,"reviewReasons":[]}],"warnings":[],"requiresConfirmation":true}"""

    @Test fun 사진은_기존_앱_인증으로_Spring에_바이너리만_보낸다() = runTest {
        val jpeg = byteArrayOf(-1,-40,-1,0,1)
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            assertEquals(HttpMethod.Post,request.method)
            assertEquals("/api/v1/gatherings/11/settlement-units/22/receipts/extract",request.url.encodedPath)
            assertEquals("Bearer host",request.headers[HttpHeaders.Authorization])
            assertNull(request.headers["X-Receipt-Service-Key"])
            assertEquals(ContentType.Image.JPEG,request.body.contentType)
            assertContentEquals(jpeg,(request.body as OutgoingContent.ByteArrayContent).bytes())
            respond(response,HttpStatusCode.OK,headers)
        }
        assertEquals(candidate,ApiClient("https://api.test",MemoryTokenStore("host"),engine).extractReceipt(11,22,jpeg))
        assertEquals(1,calls)
    }

    @Test fun 요청_제한은_RetryAfter를_보존하고_자동_재시도하지_않는다() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("""{"code":"OCR_REQUEST_LIMIT","message":"limit"}""",HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "Retry-After" to listOf("37")))
        }
        val error = assertFailsWith<ApiError> { ApiClient("https://api.test",MemoryTokenStore("host"),engine).extractReceipt(1,2,byteArrayOf(1)) }
        assertEquals("OCR_REQUEST_LIMIT",error.code)
        assertEquals(37,error.retryAfterSeconds)
        assertContains(receiptErrorMessage(error),"37초")
        assertEquals(1,calls)
    }

    @Test fun 운영_미연결_404를_예시_인식_성공으로_바꾸지_않는다() = runTest {
        val engine = MockEngine { respond("",HttpStatusCode.NotFound) }
        val error = assertFailsWith<ApiError> { ApiClient("https://api.test",MemoryTokenStore(),engine).extractReceipt(1,2,byteArrayOf(1)) }
        assertContains(receiptErrorMessage(error),"직접 입력")
    }

    @Test fun 알수없는_스키마와_자동확정_응답은_거절한다() = runTest {
        for (body in listOf(response.replace("\"schemaVersion\":2","\"schemaVersion\":3"),response.replace("\"requiresConfirmation\":true","\"requiresConfirmation\":false"),"not json")) {
            val engine = MockEngine { respond(body,HttpStatusCode.OK,headers) }
            val error = assertFailsWith<ApiError> { ApiClient("https://api.test",MemoryTokenStore(),engine).extractReceipt(1,2,byteArrayOf(1)) }
            assertEquals("OCR_BAD_RESPONSE",error.code)
        }
    }

    @Test fun 화면_취소는_네트워크_오류로_바꾸지_않는다() = runTest {
        val engine = MockEngine { throw CancellationException("screen left") }
        assertFailsWith<CancellationException> { ApiClient("https://api.test",MemoryTokenStore(),engine).extractReceipt(1,2,byteArrayOf(1)) }
    }

    @Test fun 빈사진과_초과사진은_전송하지_않는다() = runTest {
        val engine = MockEngine { error("전송하지 않아야 함") }
        val api = ApiClient("https://api.test",MemoryTokenStore(),engine)
        for (bytes in listOf(byteArrayOf(),ByteArray(MAX_RECEIPT_BYTES+1))) assertFailsWith<IllegalArgumentException> { api.extractReceipt(1,2,bytes) }
    }

    @Test fun 데모에서는_운영_OCR을_호출하지_않는다() = runTest {
        val gateway = V3Gateway(V3Store(V3State.empty()),null,isDemoMode=true)
        assertEquals("OCR_UNAVAILABLE",assertFailsWith<ApiError> { gateway.extractReceipt(1,byteArrayOf(1)) }.code)
    }

    @Test fun 사람의_확인_전에는_입력을_반영할수없다() {
        val draft = candidate.toReviewDraft()
        assertNull(draft.checkedInput())
        assertEquals(ReceiptInput(50_000,listOf(DrinkItem("소주",5_000,3))),draft.copy(confirmed=true).checkedInput())
    }

    @Test fun 미인식_총액과_수량은_0이나_1로_채우지_않는다() {
        val draft = candidate.copy(total=null,drinks=listOf(candidate.drinks.single().copy(quantity=null,unitPrice=null))).toReviewDraft()
        assertEquals("",draft.total)
        assertEquals("",draft.items.single().quantity)
        assertEquals("",draft.items.single().price)
        assertNull(draft.copy(confirmed=true).checkedInput())
    }

    @Test fun 병_이외와_미상_단위는_병수와_단가로_자동변환하지_않는다() {
        for (unit in listOf("CAN","GLASS","PITCHER","PACK","UNKNOWN")) {
            val original = candidate.drinks.single().copy(unit=unit)
            val draft = candidate.copy(drinks=listOf(original)).toReviewDraft()
            assertEquals(original,draft.items.single().original)
            assertEquals("",draft.items.single().quantity)
            assertEquals("",draft.items.single().price)
            assertNull(draft.copy(confirmed=true).checkedInput())
        }
    }

    @Test fun 잘못된_숫자와_음수_소수_오버플로는_부분매칭하지_않는다() {
        for (number in listOf("-100","1.5","1,000","100원","999999999999999999999999","")) {
            assertNull(candidate.toReviewDraft().copy(total=number,confirmed=true).checkedInput())
        }
        for (quantity in listOf("0","-2","1.5","1001")) {
            val draft = candidate.toReviewDraft()
            assertNull(draft.copy(items=listOf(draft.items.single().copy(quantity=quantity)),confirmed=true).checkedInput())
        }
    }

    @Test fun 안주가_있으므로_술합계는_총액과_같을_필요가없다() {
        val draft = candidate.toReviewDraft().copy(confirmed=true)
        assertEquals(15_000,draft.alcoholTotal())
        assertTrue(draft.problems().isEmpty())
        assertNotNull(draft.checkedInput())
        assertNull(draft.copy(total="10000").checkedInput())
    }

    @Test fun 같은이름의_다른_단가_행을_합치지_않는다() {
        val draft = candidate.copy(drinks=candidate.drinks+candidate.drinks.single().copy(unitPrice=6_000)).toReviewDraft().copy(confirmed=true)
        assertEquals(listOf(5_000L,6_000L),draft.checkedInput()!!.drinks.map { it.unitPrice })
    }

    @Test fun 술없는_영수증도_총무확인후_총액만_반영한다() {
        assertEquals(emptyList(),candidate.copy(drinks=emptyList()).toReviewDraft().copy(confirmed=true).checkedInput()!!.drinks)
    }
}
