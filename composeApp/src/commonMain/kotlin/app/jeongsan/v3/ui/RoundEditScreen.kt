package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.DRINK_PRESETS
import app.jeongsan.v3.DrinkItem
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.Round
import app.jeongsan.v3.RoundDraft
import app.jeongsan.v3.drinksTotal
import app.jeongsan.v3.host
import app.jeongsan.v3.parseAmount
import app.jeongsan.v3.validateRound

/**
 * R2 차수 편집(총무) — 웹 `RoundEditPage.tsx`.
 *
 * 텍스트 입력은 금액과 (직접 입력한) 술 이름·가격뿐이다. 술은 프리셋 칩 한 번 + 병 수 [−][+]로 끝난다.
 * 금액 입력은 숫자 키패드(앱다운 조작). [영수증 찍기]는 앱 전용 기능이지만 처리 방식(서버/기기)이
 * 아직 결정 전이라 자리만 비워 둔다(SCREENS.md §7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundEditScreen(
    devBar: @Composable () -> Unit,
    g: Gathering,
    /** 새 차수면 null */
    round: Round?,
    onBack: () -> Unit,
    onSave: (RoundDraft, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val host = g.host()
    var amountText by remember { mutableStateOf(round?.let { commas(it.total) } ?: "") }
    val drinks = remember { mutableStateListOf<DrinkItem>().apply { addAll(round?.drinks.orEmpty()) } }
    var payer by remember { mutableStateOf(round?.payerParticipantId ?: host.id) }
    var editingPrice by remember { mutableStateOf<Int?>(null) }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val total = parseAmount(amountText)
    val draft = RoundDraft(round?.id, total, drinks.toList(), payer)
    val errors = validateRound(draft, g)
    val label = round?.label ?: "${g.rounds.size + 1}차"

    fun addDrink(d: DrinkItem) {
        // 같은 술 칩을 또 누르면 줄을 늘리지 않고 병 수를 올린다
        val i = drinks.indexOfFirst { it.name == d.name && it.unitPrice == d.unitPrice }
        if (i >= 0) drinks[i] = drinks[i].copy(quantity = drinks[i].quantity + 1) else drinks += d
    }

    fun save(andNext: Boolean) {
        tried = true
        if (errors.isEmpty()) onSave(draft, andNext)
    }

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle(if (round != null) "$label 고치기" else "$label 넣기")
        },
        // 버튼이 셋이라 바닥에 고정하지 않는다 — 고정하면 작은 폰에서 입력칸을 덮는다(웹에서 겪음)
        bottom = null,
    ) {
        Label("얼마 나왔나요?")
        RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = amountText,
                    onValueChange = { v -> parseAmount(v).let { n -> amountText = if (n > 0) commas(n) else "" } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = JsColor.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(JsColor.p600),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "$label 금액" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (amountText.isEmpty()) Text("0", color = JsColor.ink3, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            inner()
                        }
                    },
                )
                Text(" 원", color = JsColor.ink2, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Label("어떤 술을 마셨나요?")
            Text("  안 마신 차수면 비워두세요", color = JsColor.ink3, fontSize = 11.5.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in DRINK_PRESETS) {
                Text(
                    "+ ${p.name}",
                    Modifier.background(JsColor.p50).border(2.dp, JsColor.p500).clickable { addDrink(p) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    color = JsColor.p700, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                "+ 직접 입력",
                Modifier.background(Color.White).border(2.dp, JsColor.line).clickable { drinks += DrinkItem("", 0, 1) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                color = JsColor.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
            )
        }

        drinks.forEachIndexed { i, d ->
            Row(
                Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.line).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicTextField(
                    value = d.name,
                    onValueChange = { drinks[i] = d.copy(name = it.take(20)) },
                    singleLine = true,
                    textStyle = TextStyle(color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(JsColor.p600),
                    modifier = Modifier.weight(1f).padding(4.dp).semantics { contentDescription = "술 이름" },
                    decorationBox = { inner -> Box { if (d.name.isEmpty()) Text("술 이름", color = JsColor.ink3, fontSize = 14.sp); inner() } },
                )
                if (editingPrice == i) {
                    BasicTextField(
                        value = if (d.unitPrice > 0) commas(d.unitPrice) else "",
                        onValueChange = { drinks[i] = d.copy(unitPrice = minOf(parseAmount(it), 9_999_999)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = JsColor.ink, fontSize = 13.sp, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(JsColor.p600),
                        modifier = Modifier.width(88.dp).background(JsColor.p50).border(1.dp, JsColor.p500).padding(6.dp)
                            .semantics { contentDescription = "${d.name.ifBlank { "술" }} 병당 가격" },
                    )
                } else {
                    Text(
                        if (d.unitPrice > 0) won(d.unitPrice) else "가격",
                        Modifier.width(88.dp).background(JsColor.p50).border(1.dp, JsColor.p500).clickable { editingPrice = i }.padding(6.dp)
                            .semantics { role = Role.Button; contentDescription = "${d.name.ifBlank { "술" }} 병당 가격 고치기" },
                        color = JsColor.ink2, fontSize = 13.sp, textAlign = TextAlign.End,
                    )
                }
                Row(Modifier.border(2.dp, JsColor.ink), verticalAlignment = Alignment.CenterVertically) {
                    QtyButton("−", "한 병 빼기", enabled = d.quantity > 1) { drinks[i] = d.copy(quantity = d.quantity - 1) }
                    Text("${d.quantity}", Modifier.width(26.dp), textAlign = TextAlign.Center, color = JsColor.ink, fontWeight = FontWeight.Bold)
                    QtyButton("+", "한 병 더하기", enabled = true) { drinks[i] = d.copy(quantity = d.quantity + 1) }
                }
                Text(
                    "×",
                    Modifier.size(28.dp).clickable {
                        drinks.removeAt(i)
                        editingPrice = null
                    }.semantics { contentDescription = "${d.name.ifBlank { "술" }} 지우기" },
                    color = JsColor.ink3, fontSize = 18.sp, textAlign = TextAlign.Center,
                )
            }
        }
        if (drinks.isNotEmpty()) {
            Text("술 합계 ${won(drinksTotal(drinks))}", Modifier.fillMaxWidth(), color = JsColor.ink2, fontSize = 13.sp, textAlign = TextAlign.End)
        }

        Label("누가 냈나요?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in g.participants) {
                val on = payer == p.id
                Text(
                    if (p.id == host.id) "나" else p.displayName,
                    Modifier.background(if (on) JsColor.p600 else Color.White).border(2.dp, if (on) JsColor.ink else JsColor.line)
                        .clickable { payer = p.id }.semantics { role = Role.RadioButton; selected = on }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (on) Color.White else JsColor.ink2, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        if (g.participants.size == 1) Text("사람들이 들어오면 여기서 고를 수 있어요", color = JsColor.ink3, fontSize = 12.sp)

        if (tried && errors.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(JsColor.warnBg).border(2.dp, JsColor.warn).padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { for (e in errors) Text(e, color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
        }
        CtaButton("저장") { save(false) }
        SubButton("저장하고 다음 차수 넣기") { save(true) }
        if (onDelete != null) {
            Text(
                if (confirmDelete) "한 번 더 누르면 ${label}를 지워요" else "$label 지우기",
                Modifier.align(Alignment.CenterHorizontally).clickable { if (confirmDelete) onDelete() else confirmDelete = true }
                    .padding(6.dp),
                color = if (confirmDelete) JsColor.warn else JsColor.ink3, fontSize = 13.sp,
                fontWeight = if (confirmDelete) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun QtyButton(text: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) JsColor.ink else JsColor.line, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

/** 차수 편집 화면의 키 — 새 차수를 연달아 넣을 때 입력칸을 비우려고 쓴다 */
fun roundEditKey(round: Round?, g: Gathering): String = round?.let { "r${it.id}" } ?: "new${g.rounds.size}"

/** id로 차수를 찾는다(경로 인자용) */
fun Gathering.roundById(id: Id?): Round? = rounds.find { it.id == id }
