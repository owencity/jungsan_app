package app.jeongsan.v3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.canEditTotalHeadcount
import app.jeongsan.v3.responseProgress
import app.jeongsan.v3.totalHeadcountError
import app.jeongsan.v3.totalHeadcountNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalHeadcountCard(g: Gathering, meUserId: Id, onSave: suspend (Int) -> String?) {
    var editing by remember(g.id) { mutableStateOf(false) }
    var value by remember(g.id) { mutableStateOf("") }
    var error by remember(g.id) { mutableStateOf<String?>(null) }
    var saving by remember(g.id) { mutableStateOf(false) }
    val canEdit = g.canEditTotalHeadcount(meUserId)
    val scope = rememberCoroutineScope()

    RetroSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("총 정산인원 (나 포함)", color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(g.totalHeadcount?.let { "${it}명" } ?: "미설정", color = JsColor.p700, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                if (canEdit) MiniButton(if (g.totalHeadcount == null) "인원 설정" else "인원 수정") {
                    value = g.totalHeadcount?.toString().orEmpty()
                    error = null
                    editing = true
                }
            }
            if (g.status == GatheringStatus.OPEN) {
                Text(g.responseProgress(), color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(g.totalHeadcountNotice(), color = JsColor.ink3, fontSize = 12.sp)
            }
        }
    }

    if (editing && canEdit) {
        ModalBottomSheet(
            onDismissRequest = { if (!saving) editing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RectangleShape, containerColor = Color.White,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("총 정산인원 (나 포함)", color = JsColor.ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("차수별 참석 인원과 별개예요. 전체 몇 명이 응답할지 총무를 포함해 입력해주세요.", color = JsColor.ink3, fontSize = 13.sp)
                OutlinedTextField(
                    value = value, onValueChange = { value = it; error = null },
                    label = { Text("총 정산인원 · 2~50명") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = !saving, shape = RectangleShape,
                    isError = error != null, modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = JsColor.warn, fontSize = 13.sp) }
                CtaButton(if (saving) "저장 중…" else "인원 저장", enabled = !saving) {
                    val count = value.trim().toIntOrNull()
                    val invalid = if (count == null) "총 정산인원은 나를 포함해 2~50명으로 입력해주세요."
                    else g.totalHeadcountError(meUserId, count)
                    if (invalid != null) error = invalid
                    else if (count != null && !saving) {
                        saving = true
                        scope.launch {
                            try {
                                error = onSave(count)
                                if (error == null) editing = false
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = "저장하지 못했어요. 연결을 확인하고 다시 시도해주세요." }
                            finally { saving = false }
                        }
                    }
                }
                SubButton("취소") { if (!saving) editing = false }
            }
        }
    }
}
