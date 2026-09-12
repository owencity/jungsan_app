package app.jeongsan.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.domain.DrinkItem
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.JsTextField
import app.jeongsan.ui.KvRow
import app.jeongsan.ui.MiniButton
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.util.won

private data class Preset(val name: String, val unitPrice: Long)
private val PRESETS = listOf(
    Preset("소주", 5_000), Preset("맥주", 6_000), Preset("막걸리", 6_000), Preset("하이볼", 9_000),
)

/**
 * 차수별 술 입력 — 총무가 영수증을 보고 적는다. 웹의 `host/DrinkInputPage.tsx` 를 옮긴 것.
 *
 * **총무는 총액과 술병 갯수만 적는다.** 누가 몇 잔 마셨는지는 안 적는다 — 그건 알 수도
 * 없고, 적으라고 하면 총무가 정산을 포기한다. 술값을 누가 나눠 가질지는 참여자가
 * 각자 "마셨다/안 마셨다"를 찍어서 정해진다.
 *
 * 술은 **종류별로** 받는다. 소주와 위스키를 한 줄로 합치면 논알콜인 사람이 억울해도
 * 근거를 못 본다.
 */
@Composable
fun DrinkInputScreen(
    roundLabel: String,
    total: Long,
    initial: List<DrinkItem>,
    onSave: (List<DrinkItem>) -> Unit,
    onBack: () -> Unit,
) {
    val items = remember { mutableStateListOf(*initial.toTypedArray()) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }

    val sum = items.sumOf { it.bottleCount.toLong() * it.unitPrice }
    val over = sum > total
    val canAdd = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && (count.toIntOrNull() ?: 0) > 0

    Screen {
        JsBar("$roundLabel 술 입력", onBack = onBack)

        // 영수증 OCR 자리. 아직 없다는 걸 숨기지 않는다 — "곧 됩니다"라고만 써두면
        // 사용자가 눌러보고 아무 일도 없어 더 답답하다.
        SecondaryButton("📷 영수증으로 자동 입력 · 준비 중", onClick = {})
        Hint("지금은 직접 적어주세요. 영수증 사진에서 술 종류와 금액을 읽어오는 기능을 준비하고 있습니다.")

        SectionLabel("$roundLabel 총액 · 영수증 합계")
        KvRow("전체", won(total))

        SectionLabel("술 종류별로 적어주세요")
        if (items.isEmpty()) {
            Hint("아직 없습니다. 안 적으면 술값 구분 없이 전원이 똑같이 나눕니다.")
        }
        items.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "  ${d.bottleCount}병 × ${d.unitPrice}",
                    modifier = Modifier.weight(1f),
                    color = JsColor.ink3,
                    fontSize = 11.5.sp,
                )
                Text(won(d.bottleCount * d.unitPrice), color = JsColor.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                MiniButton("삭제", modifier = Modifier.padding(start = 8.dp), onClick = { items.removeAt(i) })
            }
        }
        if (items.isNotEmpty()) {
            KvRow("술값 합계", won(sum))
        }
        if (over) {
            Hint("술값이 총액보다 큽니다. 병수나 단가를 다시 봐주세요.")
        }

        SectionLabel("추가")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            PRESETS.forEach { p ->
                Text(
                    p.name,
                    modifier = Modifier
                        .background(JsColor.surface, RectangleShape)
                        .border(JsShape.borderWidth, JsColor.line, RectangleShape)
                        .clickable { name = p.name; price = p.unitPrice.toString() }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    color = JsColor.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        JsTextField(name, { name = it }, placeholder = "술 이름 (예: 참이슬, 카스, 하이볼)")
        Spacer(Modifier.height(8.dp))
        Row {
            JsTextField(
                price, { price = it.filter(Char::isDigit) },
                placeholder = "병당 가격", keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            JsTextField(
                count, { count = it.filter(Char::isDigit) },
                placeholder = "병수", keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            "+ 추가",
            enabled = canAdd,
            onClick = {
                items.add(DrinkItem(name.trim(), count.toIntOrNull() ?: 1, price.toLongOrNull() ?: 0))
                name = ""; price = ""; count = "1"
            },
        )

        Spacer(Modifier.height(16.dp))
        PrimaryButton("저장", onClick = { onSave(items.toList()) })
    }
}
