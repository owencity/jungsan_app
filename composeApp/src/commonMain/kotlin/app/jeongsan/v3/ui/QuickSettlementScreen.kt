package app.jeongsan.v3.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.*
import app.jeongsan.v3.api.ApiError
import app.jeongsan.v3.api.QuickSettlementResult
import app.jeongsan.v3.api.V3Gateway
import app.jeongsan.v3.receipt.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun QuickSettlementScreen(gateway: V3Gateway, onBack: () -> Unit) {
    val example = isDebugBuild && LaunchOptions.screenshot == "quick-receipt"
    var amount by rememberSaveable { mutableStateOf("") }
    var people by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<QuickSettlementResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ocrOpen by remember { mutableStateOf(example) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    if (ocrOpen) {
        ReceiptBatchScreen(devBar = {},
            targets = listOf(ReceiptTarget(0L, "총금액", ReceiptInput(amount.toLongOrNull() ?: 0, emptyList()))),
            extract = { gateway.extractQuickReceipt(it) },
            onBack = { ocrOpen = false },
            onApply = { _, input ->
                amount = input.total.toString(); result = null; error = null; ocrOpen = false; null
            }, totalOnly = true, examples = if (example) listOf(ReceiptCandidate(2, 10_000, emptyList(), emptyList(), true)) else emptyList())
        return
    }
    ReceiptBackHandler(onBack)
    V3Screen(devBar = {}, top = { BackButton(onBack); TopTitle("간단 N빵") }, bottom = {
        CtaButton(if (busy) "계산 중…" else "N빵 계산하기", enabled = !busy) {
            focus.clearFocus()
            val total = amount.toLongOrNull()
            val count = people.toIntOrNull()
            result = null
            error = when {
                total == null || total !in 1..MAX_ROUND_TOTAL -> "총금액을 1원부터 1,000만 원 사이로 입력해주세요"
                count == null || count !in 2..50 -> "결제자 본인을 포함해 2명부터 50명까지 입력해주세요"
                else -> null
            }
            if (error == null && total != null && count != null) {
                busy = true
                scope.launch {
                    try {
                        result = if (example) {
                            require(total == 10_000L && count == 3)
                            QuickSettlementResult(10_000, 3, 3334, 3332, 6668)
                        } else gateway.quickSettlement(total, count)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: ApiError) { error = V3Gateway.messageOf(failure) }
                    catch (_: Exception) { error = "계산하지 못했어요. 입력한 값은 그대로 있으니 다시 시도해주세요" }
                    finally { busy = false }
                }
            }
        }
    }) {
        if (example) Text("개발용 예시 · 실제 서버 계산이 아닙니다", color = JsColor.ink2, fontSize = 13.sp)
        Text("금액과 인원만 넣으면 끝", color = JsColor.ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text("한 사람이 결제한 금액을 모두 똑같이 나눠요. 술값이나 차수별로 나누려면 새 술자리를 만들어주세요.",
            color = JsColor.ink2, fontSize = 14.sp)
        QuickNumberField("총금액 (원)", amount, "예: 100000", !busy) {
            amount = it; result = null; error = null
        }
        SubButton("영수증 촬영 · 여러 장 선택") { if (!busy) { focus.clearFocus(); ocrOpen = true } }
        Text("사진을 읽은 뒤 금액을 확인하면 채워드려요. 여러 장은 확인한 총액을 합쳐요.", color = JsColor.ink2, fontSize = 12.sp)
        QuickNumberField("인원 (결제자 본인 포함)", people, "예: 7", !busy) {
            people = it; result = null; error = null
        }
        error?.let { Text(it, color = JsColor.warn, fontSize = 14.sp) }
        result?.let { calculated ->
            QuickSettlementResultCard(calculated)
            QuickSettlementImageButton(calculated)
        }
    }
}

@Composable
private fun QuickNumberField(label: String, value: String, placeholder: String, enabled: Boolean, onChange: (String) -> Unit) {
    Text(label, color = JsColor.ink, fontWeight = FontWeight.Bold)
    RetroSurface(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(value, onValueChange = { raw ->
            if (raw.length <= 13 && raw.all { it in '0'..'9' }) onChange(raw)
        }, enabled = enabled, singleLine = true,
            textStyle = TextStyle(color = JsColor.ink, fontSize = 20.sp), cursorBrush = SolidColor(JsColor.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(14.dp).semantics { contentDescription = label },
            decorationBox = { input ->
                Box { if (value.isEmpty()) Text(placeholder, color = JsColor.ink2, fontSize = 20.sp); input() }
            })
    }
}

@Composable
internal fun QuickSettlementResultCard(result: QuickSettlementResult) {
    RetroSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("한 사람당 결제자에게 보낼 금액", color = JsColor.ink, fontWeight = FontWeight.Bold)
            Text(won(result.participantAmount), color = JsColor.p600, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("결제자를 제외한 ${result.headcount - 1}명이 각각 보내면 돼요.", color = JsColor.ink2, fontSize = 13.sp)
            Text("전체 ${won(result.total)} · ${result.headcount}명", color = JsColor.ink)
            Text("결제자가 받을 합계  ${won(result.incomingTotal)}", color = JsColor.ink, fontWeight = FontWeight.Bold)
            Text("결제자 본인 부담  ${won(result.payerAmount)}", color = JsColor.ink)
            Text("송금액은 1원 단위로 올리고 차액은 결제자 몫에서 조정해요.", color = JsColor.ink2, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickSettlementImageButton(result: QuickSettlementResult) {
    val measurer = rememberTextMeasurer()
    val save = rememberSaveSettlementImage()
    val scope = rememberCoroutineScope()
    var busy by remember(result) { mutableStateOf(false) }
    var message by remember(result) { mutableStateOf<String?>(null) }
    SubButton(if (busy) "이미지 준비 중…" else "N빵 결과 이미지 저장") {
        if (!busy) {
            busy = true; message = null
            scope.launch {
                try {
                    yield()
                    val bytes = encodeSettlementPng(renderSettlementImage(result.imagePlan(), measurer))
                    save(bytes, "jeongsan-nbbang-${result.total}-${result.headcount}.png") { saved ->
                        busy = false
                        message = when (saved) {
                            SettlementImageSaveResult.SAVED -> "N빵 결과 이미지를 저장했어요"
                            SettlementImageSaveResult.HANDED_OFF -> "선택한 저장·공유 작업을 마쳤어요"
                            SettlementImageSaveResult.CANCELLED -> null
                            SettlementImageSaveResult.FAILED -> "저장하지 못했어요. 다시 시도해주세요"
                        }
                    }
                } catch (cancelled: CancellationException) { busy = false; throw cancelled }
                catch (_: Exception) { busy = false; message = "이미지를 만들지 못했어요. 다시 시도해주세요" }
            }
        }
    }
    message?.let { Text(it, color = JsColor.ink2, fontSize = 12.sp) }
    Text("결과는 따로 보관하지 않아요. 이미지로 저장해 단톡방에 공유해주세요.", color = JsColor.ink2, fontSize = 12.sp)
}
