package app.jeongsan.v3.api

import app.jeongsan.v3.receipt.MAX_RECEIPT_BYTES
import app.jeongsan.v3.receipt.ReceiptCandidate
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod

/** 내부 Python 키/주소는 앱이 알지 않는다. 기존 Spring 인증으로 바이너리만 전송한다. */
suspend fun ApiClient.extractReceipt(gid: Long, sid: Long, jpeg: ByteArray): ReceiptCandidate {
    require(jpeg.isNotEmpty() && jpeg.size <= MAX_RECEIPT_BYTES)
    val response = send(HttpMethod.Post, "/api/v1/gatherings/$gid/settlement-units/$sid/receipts/extract",
        jpeg, ContentType.Image.JPEG, 60_000)
    val candidate = try {
        val text = response.bodyAsText()
        if (text.length > 256 * 1024) throw IllegalArgumentException()
        json.decodeFromString<ReceiptCandidate>(text)
    } catch (_: kotlinx.serialization.SerializationException) {
        throw ApiError(502, "OCR_BAD_RESPONSE", "인식 결과를 읽지 못했어요. 다시 시도하거나 직접 입력해 주세요.")
    } catch (_: IllegalArgumentException) {
        throw ApiError(502, "OCR_BAD_RESPONSE", "인식 결과를 읽지 못했어요. 다시 시도하거나 직접 입력해 주세요.")
    }
    if (candidate.schemaVersion != 2 || !candidate.requiresConfirmation) throw ApiError(502, "OCR_BAD_RESPONSE", "인식 결과를 확인할 수 없어요. 직접 입력해 주세요.")
    return candidate
}

fun receiptErrorMessage(error: ApiError): String {
    val message = when (error.code) {
        "OCR_BUSY" -> "다른 영수증을 읽고 있어요. 잠시 뒤 다시 시도해 주세요."
        "OCR_REQUEST_LIMIT" -> "잠시 영수증 읽기를 쉬어주세요. 직접 입력도 가능해요."
        "OCR_TIMEOUT" -> "영수증 읽기가 오래 걸려요. 잠시 뒤 다시 시도하거나 직접 입력해 주세요."
        "OCR_UNAVAILABLE", "HTTP_404" -> "영수증 읽기를 아직 사용할 수 없어요. 직접 입력해 주세요."
        "IMAGE_TOO_LARGE" -> "사진이 너무 커요. 8MB 이하 사진을 선택해 주세요."
        "IMAGE_FORMAT_UNSUPPORTED", "IMAGE_INVALID", "IMAGE_DIMENSIONS_TOO_LARGE" -> "사진을 읽지 못했어요. 다시 촬영하거나 다른 사진을 선택해 주세요."
        else -> V3Gateway.messageOf(error)
    }
    return error.retryAfterSeconds?.let { "$message (${it}초 후 다시 시도)" } ?: message
}
