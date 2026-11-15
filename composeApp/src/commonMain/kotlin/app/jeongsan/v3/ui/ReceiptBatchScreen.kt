package app.jeongsan.v3.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.receipt.*
import app.jeongsan.v3.api.ApiError
import app.jeongsan.v3.api.receiptErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class BatchEntry(
    val id: Int, val photo: ReceiptPhoto?, val targetId: Long?,
    val candidate: ReceiptCandidate? = null, val review: ReceiptReviewDraft? = null,
    val error: String? = null, val applied: Boolean = false,
)

/** 사진·후보를 기기에 저장하지 않는다. 검토 취소와 화면 종료는 기존 차수 금액을 건드리지 않는다. */
@Composable
fun ReceiptBatchScreen(
    devBar: @Composable () -> Unit,
    targets: List<ReceiptTarget>,
    extract: suspend (ByteArray) -> ReceiptCandidate,
    onBack: () -> Unit,
    onApply: suspend (Long, ReceiptInput) -> String?,
    saveToServer: Boolean = false,
    totalOnly: Boolean = false,
    examples: List<ReceiptCandidate> = emptyList(),
) {
    // 저장 뒤 서버 재조회로 기존값이 바뀌어도 같은 영수증을 다시 더하지 않는다.
    val originalTargets = remember { targets }
    var entries by remember { mutableStateOf(examples.mapIndexed { i, candidate -> BatchEntry(i + 1, null, originalTargets.singleOrNull()?.id, candidate, candidate.toReviewDraft().let { if (totalOnly) it.copy(items = emptyList()) else it }) }) }
    var nextId by remember { mutableStateOf(examples.size + 1) }
    var picking by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf<Int?>(null) }
    var reviewing by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<Long?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val modes = remember { mutableStateMapOf<Long, ReceiptApplyMode>() }
    val scope = rememberCoroutineScope()
    val picker = rememberReceiptPhotoPicker()
    val busy = picking || reading || saving != null

    fun close() { if (saving == null) { job?.cancel(); onBack() } }
    ReceiptBackHandler { close() }

    fun choose(source: ReceiptSource) {
        if (busy) return
        picking = true
        message = null
        picker(source) { result ->
            picking = false
            when (result) {
                is ReceiptPhotoResult.Selected -> {
                    var duplicate = 0
                    var overflow = 0
                    val updated = entries.toMutableList()
                    for (photo in result.photos) {
                        if (isDuplicateReceipt(updated.mapNotNull { it.photo?.bytes }, photo.bytes)) duplicate++
                        else if (updated.size >= MAX_RECEIPT_PHOTOS) overflow++
                        else updated += BatchEntry(nextId++, photo, originalTargets.singleOrNull()?.id)
                    }
                    entries = updated
                    message = listOfNotNull(result.warning,
                        if (duplicate > 0) "이미 선택한 사진 ${duplicate}장은 제외했어요." else null,
                        if (overflow > 0) "한 번에 최대 5장까지 넣을 수 있어요." else null).joinToString(" ").ifBlank { null }
                }
                is ReceiptPhotoResult.Failed -> message = result.message
                ReceiptPhotoResult.Cancelled -> Unit
            }
        }
    }

    fun read(ids: Set<Int>) {
        if (busy) return
        val pending = entries.filter { it.id in ids && !it.applied && it.candidate == null }.mapNotNull { entry -> entry.photo?.let { entry.id to it.bytes } }
        reading = true
        message = null
        job = scope.launch {
            try {
                readReceiptQueue(pending, extract,
                    onStart = { id -> activeId = id; entries = entries.map { if (it.id == id) it.copy(error = null) else it } },
                    onResult = { id, candidate, error ->
                        entries = entries.map { entry -> if (entry.id != id) entry else entry.copy(candidate = candidate,
                            review = candidate?.toReviewDraft()?.let { if (totalOnly) it.copy(items = emptyList()) else it }, error = error?.let { if (it is ApiError) receiptErrorMessage(it) else "읽지 못했어요. 사진을 확인한 뒤 다시 시도해 주세요." }) }
                    })
            } finally { reading = false; activeId = null }
        }
    }

    val selected = entries.find { it.id == reviewing }
    if (selected?.candidate != null) {
        ReceiptInputScreen(devBar, "영수증 ${selected.id}", extract,
            onCancel = { reviewing = null }, onApply = {}, initialPhoto = selected.photo,
            initialCandidate = selected.candidate, initialDraft = selected.review, totalOnly = totalOnly,
            example = if (examples.isNotEmpty()) selected.candidate else null,
            onReview = { checked -> entries = entries.map { if (it.id == selected.id) it.copy(review = checked) else it }; reviewing = null })
        return
    }

    V3Screen(devBar, top = { BackButton { close() }; TopTitle("영수증으로 입력") }) {
        if (examples.isNotEmpty()) Text("개발용 예시 · 실제 영수증 인식 결과가 아닙니다", color = JsColor.ink2, fontSize = 13.sp)
        Text(if (totalOnly) "최대 5장을 한 장씩 읽어요. 결제 총액을 확인하면 합쳐서 입력해요." else "최대 5장을 한 장씩 읽어요. 영수증마다 금액을 확인하고 차수를 정해 주세요.", color = JsColor.ink2)
        Text("사진은 정산어택 서버에서 잠시 처리하며 서버에 보관하지 않아요. 인식 결과는 틀리거나 빠질 수 있으니 반영 전에 확인해 주세요.", color = JsColor.ink2, fontSize = 12.sp)
        if (!busy && entries.size < MAX_RECEIPT_PHOTOS) {
            CtaButton("사진 촬영") { choose(ReceiptSource.CAMERA) }
            SubButton("사진 여러 장 선택") { choose(ReceiptSource.PHOTOS) }
        }
        if (picking) { CircularProgressIndicator(color = JsColor.p600); Text("사진을 한 장씩 가져오고 있어요", color = JsColor.ink2) }
        if (reading) {
            CircularProgressIndicator(color = JsColor.p600)
            Text("영수증 ${activeId ?: ""} 읽는 중 · 결과는 아래에서 확인해 주세요", color = JsColor.ink, fontWeight = FontWeight.Bold)
            SubButton("읽기 중단") { job?.cancel() }
        }
        message?.let { Text(it, color = JsColor.warn) }
        for (entry in entries) {
            RetroSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("영수증 ${entry.id}" + if (entry.applied) " · 반영 완료" else "", color = JsColor.ink, fontWeight = FontWeight.ExtraBold)
                    entry.photo?.let { Image(it.preview, "영수증 ${entry.id} 미리보기", Modifier.fillMaxWidth().height(150.dp)) }
                    entry.error?.let { Text(it, color = JsColor.warn, fontSize = 13.sp) }
                    if (!entry.applied && !busy) {
                        if (entry.candidate != null) {
                            Text(if (entry.review?.checkedInput() != null) "금액 확인 완료 · ${won(entry.review.checkedInput()!!.total)}" else "금액 확인이 필요해요", color = JsColor.ink2)
                            SubButton("영수증 ${entry.id} 금액 확인·수정") { reviewing = entry.id }
                        } else SubButton(if (entry.error != null) "영수증 ${entry.id} 다시 읽기" else "영수증 ${entry.id} 읽기") { read(setOf(entry.id)) }
                        if (!totalOnly) {
                        Text("반영할 차수", color = JsColor.ink2, fontSize = 13.sp)
                        for (target in originalTargets) {
                            SubButton((if (entry.targetId == target.id) "✓ " else "") + target.label) {
                                entries = entries.map { if (it.id == entry.id) it.copy(targetId = target.id) else it }
                            }
                        }
                        }
                        SubButton("이 사진 제외") { entries = entries.filterNot { it.id == entry.id } }
                    }
                }
            }
        }
        if (entries.any { !it.applied && it.candidate == null }) CtaButton("아직 읽지 못한 사진 순서대로 읽기", enabled = !busy) { read(entries.map { it.id }.toSet()) }
        for (target in originalTargets) {
            val assigned = entries.filter { it.targetId == target.id && !it.applied }
            if (assigned.isEmpty()) continue
            Label("${target.label}에 반영할 내용")
            if (entries.any { it.applied && it.targetId == target.id }) Text("이 차수는 이미 반영했어요. 추가 영수증은 차수 화면으로 돌아가 다시 입력해 주세요.", color = JsColor.ink2)
            val allChecked = assigned.all { it.review?.checkedInput() != null }
            val inputs = assigned.mapNotNull { it.review?.checkedInput() }
            Text("영수증 ${assigned.size}장 · 확인한 합계 ${won(inputs.sumOf { it.total })}", color = JsColor.ink)
            Text("기존 결제 금액 ${won(target.existing.total)}", color = JsColor.ink2)
            val hasExisting = target.existing.total > 0 || target.existing.drinks.isNotEmpty()
            val mode = if (hasExisting) modes[target.id] else ReceiptApplyMode.REPLACE
            if (hasExisting && !busy) {
                SubButton((if (mode == ReceiptApplyMode.REPLACE) "✓ " else "") + if (totalOnly) "기존 금액을 교체" else "기존 금액·술 항목을 교체") { modes[target.id] = ReceiptApplyMode.REPLACE }
                SubButton((if (mode == ReceiptApplyMode.ADD) "✓ " else "") + if (totalOnly) "기존 금액에 추가" else "기존 금액·술 항목에 추가") { modes[target.id] = ReceiptApplyMode.ADD }
            }
            val combined = mode?.let { combineReceiptInputs(target.existing, inputs, it) }
            if (!allChecked) Text(if (totalOnly) "모든 영수증의 결제 총액을 먼저 확인해 주세요." else "이 차수의 모든 영수증 금액을 먼저 확인해 주세요.", color = JsColor.warn)
            else if (mode == null) Text("기존 값을 교체할지 추가할지 선택해 주세요.", color = JsColor.warn)
            else if (combined == null) Text("차수 합계는 1,000만원, 술 항목은 100개까지예요. 영수증 내용을 확인해 주세요.", color = JsColor.warn)
            combined?.let { Text("반영 후 결제 금액 ${won(it.total)}", color = JsColor.ink, fontWeight = FontWeight.Bold) }
            CtaButton(if (saving == target.id) "저장 중이에요" else "${target.label}에 ${if (saveToServer) "저장" else "반영"}",
                enabled = !busy && allChecked && combined != null && entries.none { it.applied && it.targetId == target.id }) {
                if (busy || !allChecked || combined == null) return@CtaButton
                saving = target.id
                scope.launch {
                    try {
                        val error = onApply(target.id, combined)
                        if (error == null) {
                            val ids = assigned.map { it.id }.toSet()
                            entries = entries.map { if (it.id in ids) it.copy(applied = true) else it }
                            message = "${target.label}에 ${if (saveToServer) "저장했어요" else "반영했어요"}."
                        } else message = error
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "저장 결과를 확인하지 못했어요. 기존 값에 다시 더하지 말고 차수를 확인해 주세요." }
                    finally { saving = null }
                }
            }
        }
        Text(if (saveToServer) "차수별로 저장해요. 완료된 차수는 다시 반영하지 않아요." else if (totalOnly) "총금액에 반영한 뒤 인원을 넣고 N빵 계산하기를 눌러 주세요." else "반영 후 차수 화면의 저장 버튼을 눌러 주세요.", color = JsColor.ink2, fontSize = 12.sp)
        SubButton(if (entries.any { it.applied }) "차수로 돌아가기" else "취소하고 기존 입력 유지") { close() }
    }
}
