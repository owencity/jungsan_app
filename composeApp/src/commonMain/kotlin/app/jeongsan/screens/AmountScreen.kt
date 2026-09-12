package app.jeongsan.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.Id
import app.jeongsan.ui.DashedAddButton
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsCard
import app.jeongsan.ui.KvRow
import app.jeongsan.ui.MiniButton
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.SelectableCard
import app.jeongsan.util.won

/**
 * H2 — 금액 입력. **사람 없이 되는 것만** 받는다. 웹의 `host/AmountPage.tsx` 를 옮긴 것.
 *
 * 결제자는 "전부 내가"가 기본이라 참여자가 0명이어도 차수 입력이 완결된다.
 * "항목마다 달라요"를 고르면 사람이 모인 뒤 수집 현황에서 지정한다.
 *
 * ⚠ "+ 차수 추가"/"+ 기타 항목 추가" 는 웹에서도 아직 안 붙어 있다 — 화면만 있고
 * 동작은 없다. 목데이터에 고정된 차수/기타항목을 보여주는 단계까지만 옮긴 것이라,
 * 실제 추가 기능은 별도로 붙여야 한다.
 */
@Composable
fun AmountScreen(
    g: Gathering,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onEditDrinks: (roundId: Id) -> Unit,
) {
    var allMine by remember { mutableStateOf(false) }
    fun nameOf(id: Id) = g.participants.find { it.id == id }?.name ?: "?"

    Screen {
        JsBar("금액 입력", onBack = onBack, step = "2 / 2")

        g.rounds.forEach { r ->
            JsCard(title = r.label) {
                KvRow("총액", won(r.total))
                val drinkItems = r.drinkItems
                if (drinkItems != null) {
                    drinkItems.forEach { d ->
                        KvRow("${d.name} ${d.bottleCount}병 × ${d.unitPrice}", won(d.bottleCount * d.unitPrice), sub = true)
                    }
                    KvRow("술값 합계", won(r.alcohol))
                } else {
                    KvRow("그중 술값", won(r.alcohol))
                }
                // 종류별로 적어야 나중에 "이게 왜 이 금액이냐"에 답할 수 있다.
                MiniButton(
                    if (drinkItems?.isNotEmpty() == true) "술 종류 수정" else "술 종류 적기",
                    modifier = Modifier,
                    onClick = { onEditDrinks(r.id) },
                )
            }
        }
        DashedAddButton("+ 차수 추가", onClick = {})

        SectionLabel("기타 항목")
        g.extras.forEach { e ->
            JsCard(title = e.label) { KvRow("금액", won(e.amount)) }
        }
        DashedAddButton("+ 기타 항목 추가", onClick = {})

        SectionLabel("누가 결제했나요 · 차수와 기타 항목 전부")
        SelectableCard("전부 내가 냈어요", "", selected = allMine, onClick = { allMine = true })
        Spacer(Modifier.height(6.dp))
        SelectableCard("항목마다 달라요", "", selected = !allMine, onClick = { allMine = false })

        if (!allMine) {
            JsCard(modifier = Modifier) {
                g.rounds.forEach { r -> KvRow(r.label, nameOf(r.payerId)) }
                g.extras.forEach { e -> KvRow(e.label, nameOf(e.payerId)) }
                KvRow("참여자가 들어온 뒤 수집 현황에서 지정합니다", sub = true)
            }
        }

        Hint("결제자 · 부담자 · 면제자는 사람이 모인 뒤 수집 현황에서 지정합니다.")

        Spacer(Modifier.height(16.dp))
        PrimaryButton("링크 만들기", onClick = onNext)
    }
}
