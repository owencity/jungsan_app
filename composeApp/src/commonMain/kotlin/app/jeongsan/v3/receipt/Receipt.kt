package app.jeongsan.v3.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.jeongsan.v3.DrinkItem
import app.jeongsan.v3.MAX_ROUND_TOTAL
import kotlinx.serialization.Serializable

const val MAX_RECEIPT_BYTES = 8 * 1024 * 1024

/** 로그/저장 대상이 아니다. 화면을 닫으면 참조를 버린다. */
class ReceiptPhoto(val bytes: ByteArray, val preview: ImageBitmap) {
    init { require(bytes.isNotEmpty() && bytes.size <= MAX_RECEIPT_BYTES) }
}

enum class ReceiptSource { CAMERA, PHOTOS }
sealed interface ReceiptPhotoResult {
    class Selected(val photo: ReceiptPhoto) : ReceiptPhotoResult
    data object Cancelled : ReceiptPhotoResult
    data class Failed(val message: String) : ReceiptPhotoResult
}

@Composable
expect fun rememberReceiptPhotoPicker(): (ReceiptSource, (ReceiptPhotoResult) -> Unit) -> Unit

@Serializable
data class ReceiptCandidate(
    val schemaVersion: Int,
    val total: Long?,
    val drinks: List<ReceiptDrinkCandidate>,
    val warnings: List<String>,
    val requiresConfirmation: Boolean,
)

@Serializable
data class ReceiptDrinkCandidate(
    val name: String,
    val category: String,
    val unit: String,
    val unitPrice: Long?,
    val quantity: Int?,
    val lineAmount: Long?,
    val servingType: String,
    val volumeMl: Int?,
    val bottleCount: Int?,
    val confidence: Double,
    val reviewReasons: List<String>,
)

data class ReceiptItemDraft(
    val name: String,
    val price: String,
    val quantity: String,
    val original: ReceiptDrinkCandidate? = null,
)

data class ReceiptReviewDraft(
    val total: String,
    val items: List<ReceiptItemDraft>,
    val confirmed: Boolean = false,
) {
    fun problems(): List<String> = buildList {
        val amount = receiptInteger(total)
        if (amount == null || amount !in 1..MAX_ROUND_TOTAL) add("결제 금액을 1원~1,000만원 사이로 입력해 주세요.")
        if (items.any { it.name.isBlank() || it.name.trim().length > 20 }) add("술 이름을 1~20자로 입력해 주세요.")
        if (items.any { receiptInteger(it.price)?.let { n -> n in 1..9_999_999 } != true }) add("술 단가를 1원~9,999,999원 사이로 입력해 주세요.")
        if (items.any { receiptInteger(it.quantity)?.let { n -> n in 1..1000 } != true }) add("각 술의 병 수를 1~1,000 사이로 확인해 주세요.")
        if (isNumeric() && amount != null && alcoholTotal() > amount) add("술값 합계가 결제 금액보다 커요. 금액을 다시 확인해 주세요.")
    }

    private fun isNumeric() = items.all { receiptInteger(it.price)?.let { n -> n in 1..9_999_999 } == true && receiptInteger(it.quantity)?.let { n -> n in 1..1000 } == true }

    fun alcoholTotal(): Long = if (isNumeric()) items.sumOf { receiptInteger(it.price)!! * receiptInteger(it.quantity)!! } else 0

    /** null이면 반영하지 않는다. 정산 계산이 아니라 기존 차수 입력 자료만 만든다. */
    fun checkedInput(): ReceiptInput? = if (!confirmed || problems().isNotEmpty()) null else ReceiptInput(
        receiptInteger(total)!!,
        items.map { DrinkItem(it.name.trim(), receiptInteger(it.price)!!, receiptInteger(it.quantity)!!.toInt()) },
    )
}

data class ReceiptInput(val total: Long, val drinks: List<DrinkItem>)

/** 잔/캔/미상을 병으로 채우거나 읽히지 않은 수량을 1로 만들지 않는다. */
fun ReceiptCandidate.toReviewDraft() = ReceiptReviewDraft(
    total?.toString().orEmpty(),
    drinks.map { item ->
        ReceiptItemDraft(item.name, if (item.unit == "BOTTLE") item.unitPrice?.toString().orEmpty() else "",
            if (item.unit == "BOTTLE") item.quantity?.toString().orEmpty() else "", item)
    },
)

internal fun receiptInteger(text: String): Long? = text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toLongOrNull()

fun receiptWarning(code: String): String = when (code) {
    "TOTAL_NOT_FOUND", "TOTAL_NEEDS_REVIEW", "TOTAL_CONFLICT" -> "결제 총액을 확인해 주세요."
    "OCR_NO_TEXT" -> "글자를 읽지 못했어요. 다시 촬영하거나 직접 입력해 주세요."
    "NO_DRINK_ITEMS" -> "술 항목을 찾지 못했어요. 빠진 술이 있는지 확인해 주세요."
    "DRINK_AMOUNT_EXCEEDS_TOTAL" -> "술 금액과 결제 총액을 확인해 주세요."
    "MULTIPLE_RECEIPTS_SUSPECTED" -> "영수증 한 장씩 촬영해 주세요."
    else -> "술 이름·단가·수량과 빠진 항목을 확인해 주세요."
}

/** 기존 디버그 스크린샷 진입에서만 사용한다. 실제 촬영/공개 데모 API의 대체 응답이 아니다. */
internal fun receiptScreenExample() = ReceiptCandidate(2, 50_000, listOf(
    ReceiptDrinkCandidate("소주", "SOJU", "BOTTLE", 5_000, 3, 15_000, "UNKNOWN", 360, 3, 0.98, emptyList()),
), emptyList(), true)
