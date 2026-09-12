package app.jeongsan.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsCard
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsTextField
import app.jeongsan.ui.KvRow
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SectionLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** 이 화면이 모으는 값. 웹의 `host/CreatePage.tsx` 의 `NewGathering` 타입과 같다. */
data class NewGatheringInput(
    val name: String,
    val date: String,
    val myName: String,
    val bank: String,
    val account: String,
    val expectedCount: Int,
)

/**
 * H1 — 새 술자리 만들기. 웹의 `host/CreatePage.tsx` 를 옮긴 것.
 *
 * **명단을 입력하지 않는다.** 참여자 수만 받고 사람은 링크로 들어온다.
 * 그 숫자는 확정 전 인원 불일치 경고에만 쓰고 **계산에는 쓰지 않는다.**
 */
@Composable
fun CreateGatheringScreen(
    onNext: (NewGatheringInput) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("8월 팀 회식") }
    var date by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()) }
    var myName by remember { mutableStateOf("동규") }
    var bank by remember { mutableStateOf("국민은행") }
    var account by remember { mutableStateOf("123456-78-901234") }
    var count by remember { mutableStateOf("5") }
    val valid = (count.toIntOrNull() ?: 0) >= 2

    Screen {
        JsBar("새 술자리", onBack = onBack, step = "1 / 2")

        SectionLabel("술자리 이름")
        JsTextField(name, { name = it }, placeholder = "술자리 이름")

        SectionLabel("술자리 날짜")
        JsTextField(date, { date = it }, placeholder = "YYYY-MM-DD")

        SectionLabel("내 이름 · 참여자에게 보입니다")
        JsTextField(myName, { myName = it }, placeholder = "내 이름")
        Hint("카카오 닉네임을 가져왔습니다. 바꿀 수 있어요")

        SectionLabel("내 계좌 · 내가 받을 때 쓰입니다")
        JsTextField(bank, { bank = it }, placeholder = "은행")
        Spacer(Modifier.height(6.dp))
        JsTextField(account, { account = it }, placeholder = "계좌번호")
        Hint("다른 사람이 결제한 차수가 있으면, 그 사람 계좌는 본인이 등록합니다.")

        // 참여자 수는 필수다. 이 숫자가 "정원을 넘는 참여는 주최자 승인" 게이트의
        // 기준이라, 비어 있으면 방어가 통째로 사라진다. 사후 개설이라 주최자는 인원을
        // 알고, 수집 현황에서 나중에 고칠 수 있으니 틀려도 된다.
        SectionLabel("참여자 수 · 본인 포함 · 필수")
        JsTextField(count, { count = it.filter(Char::isDigit) }, placeholder = "5", keyboardType = KeyboardType.Number)
        Hint("${myName.ifBlank { "주최자" }}님을 포함한 인원을 적어주세요. 명단은 적지 않아도 됩니다 — 링크로 각자 들어옵니다.")

        JsCard(modifier = Modifier.padding(top = 10.dp), title = "이 숫자가 하는 일") {
            KvRow("확정 전에 빠진 사람이 있으면 알려드립니다")
            KvRow("모르는 사람이 들어오면 주최자 확인을 받습니다", v = "${count.toIntOrNull() ?: 0}명 초과 시", sub = true)
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            "술자리 만들고 링크 받기",
            enabled = valid,
            onClick = {
                onNext(
                    NewGatheringInput(
                        name = name.trim().ifBlank { "새 술자리" },
                        date = date,
                        myName = myName.trim().ifBlank { "나" },
                        bank = bank, account = account,
                        expectedCount = count.toIntOrNull() ?: 0,
                    ),
                )
            },
        )
        if (!valid) {
            Text(
                "참여자 수를 2명 이상으로 적어주세요",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = JsColor.warn,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
