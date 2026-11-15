package app.jeongsan.v3.receipt

import app.jeongsan.v3.MAX_ROUND_TOTAL
import app.jeongsan.v3.api.ApiError
import kotlinx.coroutines.CancellationException

enum class ReceiptApplyMode { REPLACE, ADD }
data class ReceiptTarget(val id: Long, val label: String, val existing: ReceiptInput)

/** 확인한 영수증만 더한다. 사람별 정산 계산은 기존 서버가 맡는다. */
fun combineReceiptInputs(existing: ReceiptInput, receipts: List<ReceiptInput>, mode: ReceiptApplyMode): ReceiptInput? {
    if (receipts.isEmpty()) return null
    val all = if (mode == ReceiptApplyMode.ADD) listOf(existing) + receipts else receipts
    if (all.any { it.total < 0 || it.total > MAX_ROUND_TOTAL }) return null
    val total = all.sumOf { it.total }
    val drinks = all.flatMap { it.drinks }
    if (total !in 1..MAX_ROUND_TOTAL || drinks.any { it.unitPrice !in 1..9_999_999 || it.quantity !in 1..1000 || it.name.isBlank() }) return null
    if (drinks.sumOf { it.unitPrice * it.quantity } > total) return null
    return ReceiptInput(total, drinks)
}

/** 같은 파일을 다시 골라도 이 화면 안에서는 한 번만 읽는다. 다른 사진의 동일 영수증은 총무가 확인해야 한다. */
fun isDuplicateReceipt(existing: List<ByteArray>, incoming: ByteArray) = existing.any { it.contentEquals(incoming) }

/** 동시 인식을 피한다. 서버가 바쁘거나 제한을 알리면 나머지는 대기시키고 사용자의 재시도를 기다린다. */
suspend fun readReceiptQueue(
    entries: List<Pair<Int, ByteArray>>,
    extract: suspend (ByteArray) -> ReceiptCandidate,
    onStart: (Int) -> Unit,
    onResult: (Int, ReceiptCandidate?, Throwable?) -> Unit,
) {
    for ((id, bytes) in entries) {
        onStart(id)
        try { onResult(id, extract(bytes), null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            onResult(id, null, error)
            if (error is ApiError && error.status in listOf(429, 503)) break
        }
    }
}
