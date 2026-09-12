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
import app.jeongsan.domain.GroupType
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsTextField
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.SelectableCard
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * 모임 만들기 — 웹의 `host/CreateGroupPage.tsx` 를 옮긴 것.
 *
 * **먼저 종류를 고르게 한다.** 번개와 주기는 이후 흐름이 다르다 — 번개는 술자리를
 * 지금 같이 만들어야 하고(날짜·인원이 필요하다), 주기는 모임만 만들고 술자리는
 * 나중에 추가한다. 종류를 나중에 묻거나 설정 안으로 숨기면, 사용자는 자기가 뭘
 * 만들었는지 모른 채 다음 화면에서 헤맨다.
 */
@Composable
fun CreateGroupScreen(
    onCreate: (name: String, groupType: GroupType, gatheringDate: String?, expectedCount: Int?) -> Unit,
    onBack: () -> Unit,
) {
    var groupType by remember { mutableStateOf(GroupType.RECURRING) }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()) }
    var count by remember { mutableStateOf("4") }

    val flash = groupType == GroupType.FLASH
    val countN = count.toIntOrNull() ?: 0
    // 번개만 날짜·인원이 필요하다. 주기는 이름만 있으면 만들 수 있다.
    val valid = name.isNotBlank() && (!flash || countN >= 2)

    Screen {
        JsBar("새 모임", onBack = onBack)

        SectionLabel("어떤 모임인가요?")
        SelectableCard(
            title = "주기 모임",
            description = "계속 만나는 사람들. 술자리를 여러 번 열고 그때마다 정산합니다.",
            selected = !flash,
            onClick = { groupType = GroupType.RECURRING },
        )
        Spacer(Modifier.height(8.dp))
        SelectableCard(
            title = "번개 모임",
            description = "오늘 한 번. 술자리 하나로 끝나고, 정산 후 2주 뒤 목록에서 사라집니다.",
            selected = flash,
            onClick = { groupType = GroupType.FLASH },
        )

        SectionLabel("모임 이름")
        JsTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = if (flash) "예: 8월 26일 번개" else "예: 신림팸",
        )

        // 번개일 때만 나타난다 — 주기 모임에는 날짜가 없다(술자리마다 따로 잡는다)
        if (flash) {
            SectionLabel("언제 만나나요?")
            JsTextField(value = date, onValueChange = { date = it }, placeholder = "YYYY-MM-DD")

            SectionLabel("몇 명 오나요? · 본인 포함")
            JsTextField(
                value = count,
                onValueChange = { count = it.filter(Char::isDigit) },
                placeholder = "4",
                keyboardType = KeyboardType.Number,
            )
            Hint("정확하지 않아도 됩니다. 모르는 사람이 링크로 들어왔을 때 확인받는 기준으로만 씁니다 — 계산에는 쓰지 않습니다.")
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (flash) "번개 만들고 링크 받기" else "모임 만들기",
            enabled = valid,
            onClick = { onCreate(name.trim(), groupType, if (flash) date else null, if (flash) countN else null) },
        )
        if (!valid) {
            Text(
                if (name.isBlank()) "모임 이름을 적어주세요" else "인원을 2명 이상으로 적어주세요",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = JsColor.warn,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
