package app.jeongsan.v3.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

private enum class ReceiptStep { SOURCE, PHOTO, READING, REVIEW, FAILED }

/** 확인 전에는 외부 차수 상태에 접근하지 않는다. 취소하면 사진과 후보 참조를 버린다. */
@Composable
fun ReceiptInputScreen(
    devBar: @Composable () -> Unit,
    label: String,
    extract: suspend (ByteArray) -> ReceiptCandidate,
    onCancel: () -> Unit,
    onApply: (ReceiptInput) -> Unit,
    example: ReceiptCandidate? = null,
) {
    var step by remember { mutableStateOf(if (example == null) ReceiptStep.SOURCE else ReceiptStep.REVIEW) }
    var photo by remember { mutableStateOf<ReceiptPhoto?>(null) }
    var candidate by remember { mutableStateOf(example) }
    var draft by remember { mutableStateOf(example?.toReviewDraft()) }
    var error by remember { mutableStateOf<String?>(null) }
    var originalOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberReceiptPhotoPicker()

    fun choose(source: ReceiptSource) {
        if (picking || step == ReceiptStep.READING) return
        picking = true
        error = null
        picker(source) { result ->
            picking = false
            when (result) {
                is ReceiptPhotoResult.Selected -> { photo = result.photo; candidate = null; draft = null; step = ReceiptStep.PHOTO }
                is ReceiptPhotoResult.Failed -> { error = result.message }
                ReceiptPhotoResult.Cancelled -> Unit
            }
        }
    }

    fun readPhoto() {
        val selected = photo ?: return
        if (step == ReceiptStep.READING) return
        step = ReceiptStep.READING
        error = null
        job = scope.launch {
            try {
                val result = extract(selected.bytes)
                candidate = result
                draft = result.toReviewDraft()
                originalOpen = false
                step = ReceiptStep.REVIEW
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ApiError) { error = receiptErrorMessage(failure); step = ReceiptStep.FAILED }
            catch (_: Exception) { error = "영수증을 읽지 못했어요. 다시 시도하거나 직접 입력해 주세요."; step = ReceiptStep.FAILED }
        }
    }

    fun cancel() { job?.cancel(); photo = null; candidate = null; draft = null; onCancel() }

    V3Screen(devBar = devBar, top = { BackButton { cancel() }; TopTitle("$label 영수증 입력") }) {
        if (example != null) Text("개발용 예시 · 실제 영수증 인식 결과가 아닙니다", color = JsColor.ink2, fontSize = 13.sp)
        when (step) {
            ReceiptStep.SOURCE -> {
                Label("영수증을 가져올까요?")
                Text("영수증 전체가 보이도록 정면에서 찍어주세요. 상품명·수량·금액과 맨 아래 합계가 필요해요.", color = JsColor.ink2)
                CtaButton(if (picking) "사진을 가져오는 중이에요" else "사진 촬영", enabled = !picking) { choose(ReceiptSource.CAMERA) }
                if (!picking) SubButton("사진에서 선택") { choose(ReceiptSource.PHOTOS) }
                SubButton("직접 입력하기") { cancel() }
            }
            ReceiptStep.PHOTO -> {
                Label("글자가 잘 보이나요?")
                Text("작은 수량과 최종 결제 금액까지 확인해 주세요.", color = JsColor.ink2)
                photo?.let { ReceiptPhotoPreview(it) }
                CtaButton("이 사진으로 읽기") { readPhoto() }
                SubButton("다시 촬영·선택") { step = ReceiptStep.SOURCE; photo = null }
                SubButton("직접 입력하기") { cancel() }
            }
            ReceiptStep.READING -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(color = JsColor.p600)
                    Text("영수증을 읽고 있어요", color = JsColor.ink, fontWeight = FontWeight.Bold)
                    Text("잠시 기다려 주세요. 결과가 나오면 금액을 확인할 수 있어요.", color = JsColor.ink2)
                }
                SubButton("취소하고 직접 입력하기") { cancel() }
            }
            ReceiptStep.FAILED -> {
                Label("영수증 읽기를 마치지 못했어요")
                if (photo != null) SubButton("다시 읽기") { readPhoto() }
                SubButton("다시 촬영·선택") { photo = null; error = null; step = ReceiptStep.SOURCE }
                SubButton("직접 입력하기") { cancel() }
            }
            ReceiptStep.REVIEW -> {
                val current = draft ?: return@V3Screen
                Label("영수증 인식 결과")
                Text("잘못 읽은 값과 빠진 술이 있는지 확인해 주세요. 확인 전에는 차수에 반영되지 않아요.", color = JsColor.ink2)
                candidate?.warnings.orEmpty().map(::receiptWarning).distinct().forEach { ReceiptNotice(it) }
                ReceiptField("최종 결제 금액", current.total, number = true) { draft = current.copy(total = it, confirmed = false) }
                Label("술 항목")
                if (current.items.isEmpty()) Text("술 항목이 없어요. 빠진 항목은 추가해 주세요.", color = JsColor.ink2)
                current.items.forEachIndexed { index, item ->
                    RetroSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item.original?.let { original ->
                                val unit = when (original.unit) { "BOTTLE" -> "병"; "CAN" -> "캔"; "GLASS" -> "잔"; "PITCHER" -> "피처"; "PACK" -> "팩"; else -> "단위 미상" }
                                Text("읽은 값: ${original.quantity?.toString() ?: "수량 미상"} $unit · 항목 금액 ${original.lineAmount?.let(::won) ?: "확인 필요"}", color = JsColor.ink2, fontSize = 12.sp)
                                if (original.unit != "BOTTLE") ReceiptNotice("현재 술 입력은 병 기준이에요. 다른 단위는 자동 변환하지 않아요. 병 기준 단가·수량을 직접 확인하거나 이 항목을 빼고 직접 입력해 주세요.")
                                if (original.reviewReasons.isNotEmpty()) Text("단가·수량·분류를 확인해 주세요.", color = JsColor.warn, fontSize = 12.sp)
                            }
                            fun change(next: ReceiptItemDraft) { draft = current.copy(items = current.items.mapIndexed { i, old -> if (i == index) next else old }, confirmed = false) }
                            ReceiptField("술 이름 ${index + 1}", item.name) { change(item.copy(name = it)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { ReceiptField("병당 가격 ${index + 1}", item.price, number = true) { change(item.copy(price = it)) } }
                                Box(Modifier.weight(1f)) { ReceiptField("병 수 ${index + 1}", item.quantity, number = true) { change(item.copy(quantity = it)) } }
                            }
                            SubButton("이 술 항목 빼기") { draft = current.copy(items = current.items.filterIndexed { i, _ -> i != index }, confirmed = false) }
                        }
                    }
                }
                SubButton("+ 빠진 술 추가") { draft = current.copy(items = current.items + ReceiptItemDraft("", "", ""), confirmed = false) }
                val validNumbers = current.problems().isEmpty()
                Text("술 합계 ${if (validNumbers) won(current.alcoholTotal()) else "확인 필요"}", color = JsColor.ink, fontWeight = FontWeight.Bold)
                if (validNumbers) Text("안주 ${won(current.total.toLong() - current.alcoholTotal())}", color = JsColor.ink2)
                if (photo != null) {
                    SubButton(if (originalOpen) "원본 영수증 접기" else "원본 영수증 보기") { originalOpen = !originalOpen }
                    if (originalOpen) photo?.let { ReceiptPhotoPreview(it) }
                }
                current.problems().forEach { ReceiptNotice(it) }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { draft = current.copy(confirmed = !current.confirmed) }
                        .semantics(mergeDescendants = true) { toggleableState = if (current.confirmed) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = current.confirmed, onCheckedChange = null)
                    Text("총액·술 단가·병 수를 확인했어요", color = JsColor.ink, fontSize = 13.sp)
                }
                CtaButton("확인한 내용으로 ${label}에 반영", enabled = current.checkedInput() != null) { current.checkedInput()?.let(onApply) }
                Text("반영 후 기존 차수 화면에서 저장해 주세요.", color = JsColor.ink2, fontSize = 12.sp)
                SubButton("다시 촬영·선택") { photo = null; candidate = null; draft = null; error = null; step = ReceiptStep.SOURCE }
                SubButton("취소하고 기존 입력 유지") { cancel() }
            }
        }
        error?.let { ReceiptNotice(it) }
    }
}

@Composable
private fun ReceiptPhotoPreview(photo: ReceiptPhoto) {
    Image(photo.preview, "선택한 영수증 원본", Modifier.fillMaxWidth().heightIn(max = 360.dp).border(2.dp, JsColor.line), contentScale = ContentScale.Fit)
}

@Composable
private fun ReceiptNotice(text: String) {
    Text(text, Modifier.fillMaxWidth().background(JsColor.p50).border(2.dp, JsColor.line).padding(12.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, color = JsColor.ink2, fontSize = 13.sp)
}

@Composable
private fun ReceiptField(label: String, value: String, number: Boolean = false, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = JsColor.ink2, fontSize = 13.sp)
        BasicTextField(value, onValueChange = onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            textStyle = TextStyle(color = JsColor.ink, fontSize = 17.sp), cursorBrush = SolidColor(JsColor.p600),
            modifier = Modifier.fillMaxWidth().background(JsColor.surface).border(2.dp, JsColor.line).padding(12.dp).semantics { contentDescription = label },
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("확인 필요", color = JsColor.ink3); inner() } })
    }
}
