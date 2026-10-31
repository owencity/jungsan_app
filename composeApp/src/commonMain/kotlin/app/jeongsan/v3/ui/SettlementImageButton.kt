package app.jeongsan.v3.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.*
import app.jeongsan.v3.api.ServerSettlementTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun SettlementImageButton(view: ServerSettlementTable) {
    if (!view.canExportImage()) return
    val measurer = rememberTextMeasurer()
    val save = rememberSaveSettlementImage()
    val scope = rememberCoroutineScope()
    var busy by remember(view) { mutableStateOf(false) }
    var message by remember(view) { mutableStateOf<String?>(null) }
    Text("정산 금액이 확정되었습니다!", color = JsColor.ink, fontWeight = FontWeight.Bold)
    Text("전원이 가로로 들어간 이미지를 저장해 단톡방에 공유하세요. 입금 완료 여부는 정산방에서 확인해주세요.", color = JsColor.ink2, fontSize = 12.sp)
    SubButton(if (busy) "이미지 준비 중…" else "전체 정산표 이미지 저장") {
        if (!busy) {
            busy = true; message = null
            scope.launch {
                try {
                    yield()
                    val bytes = encodeSettlementPng(renderSettlementImage(view.imagePlan(), measurer))
                    save(bytes, "jeongsan-${view.settlementUnitId}-v${view.inputRevision}.png") { result ->
                        busy = false
                        message = when (result) {
                            SettlementImageSaveResult.SAVED -> "정산표 이미지를 저장했어요"
                            SettlementImageSaveResult.HANDED_OFF -> "선택한 저장·공유 작업을 마쳤어요"
                            SettlementImageSaveResult.CANCELLED -> null
                            SettlementImageSaveResult.FAILED -> "저장하지 못했어요. 다시 시도해주세요"
                        }
                    }
                } catch (e: CancellationException) { busy = false; throw e }
                catch (_: Exception) { busy = false; message = "이미지를 만들지 못했어요. 표가 너무 크거나 기기 메모리가 부족할 수 있어요" }
            }
        }
    }
    message?.let { Text(it, color = JsColor.ink2, fontSize = 12.sp) }
}

/** 화면 밀도·글자 확대 설정과 별개인 이미지. 글자 높이를 먼저 재서 이름과 금액을 자르지 않는다. */
private fun renderSettlementImage(plan: SettlementImagePlan, measurer: TextMeasurer): ImageBitmap {
    val measured = plan.rows.map { row ->
        val widths = if (row.fullWidth) listOf(plan.width - 40) else plan.columnWidths
        row.values.mapIndexed { index, value ->
            measurer.measure(AnnotatedString(value), style = TextStyle(color = Color(0xFF172338), fontSize = 18.sp,
                fontWeight = if (row.highlighted) FontWeight.Bold else FontWeight.Normal),
                constraints = Constraints(maxWidth = widths[index] - 16), density = Density(1f), layoutDirection = LayoutDirection.Ltr)
        }
    }
    val heights = measured.map { cells -> maxOf(44, cells.maxOf { it.size.height } + 20) }
    val height = heights.sum() + 40
    validateSettlementImageSize(plan.width, height)
    val bitmap = ImageBitmap(plan.width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(plan.width.toFloat(), height.toFloat())) {
        drawRect(Color.White)
        var y = 20f
        plan.rows.forEachIndexed { rowIndex, row ->
            var x = 20f
            val widths = if (row.fullWidth) listOf(plan.width - 40) else plan.columnWidths
            measured[rowIndex].forEachIndexed { column, text ->
                val size = Size(widths[column].toFloat(), heights[rowIndex].toFloat())
                drawRect(if (row.highlighted) Color(0xFFFFF0CC) else if (column == 0 && !row.fullWidth) Color(0xFFEAF1FD) else Color.White, Offset(x, y), size)
                drawRect(Color(0xFFAAB8CD), Offset(x, y), size, style = Stroke(1f))
                drawText(text, topLeft = Offset(x + 8, y + 10))
                x += widths[column]
            }
            y += heights[rowIndex]
        }
    }
    return bitmap
}
