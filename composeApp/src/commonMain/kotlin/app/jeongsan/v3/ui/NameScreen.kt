package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.MAX_NAME
import app.jeongsan.v3.User
import app.jeongsan.v3.nameLength
import app.jeongsan.v3.validateName

/**
 * L2 이름 확인 — 웹 `NamePage.tsx`. 첫 로그인 때 한 번. 카카오 닉네임을 기본값으로 채워 두고, 친구들이 정산방에서
 * 알아볼 이름인지 확인만 받는다. 대부분은 그대로 [이 이름으로 시작]을 누른다. 키보드의 [완료]로도 시작한다.
 */
@Composable
fun NameScreen(devBar: @Composable () -> Unit, me: User, onBack: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(me.id) { mutableStateOf(me.displayName) }
    var tried by remember(me.id) { mutableStateOf(false) }
    val errors = validateName(name)
    val preview = name.trim().ifEmpty { "이름" }
    val submit = {
        tried = true
        if (errors.isEmpty()) onConfirm(name.trim())
    }

    V3Screen(
        devBar = devBar,
        top = { BackButton(onBack) },
        bottom = {
            if (tried && errors.isNotEmpty()) {
                Text(
                    errors.first(), Modifier.fillMaxWidth().background(JsColor.warnBg).border(2.dp, JsColor.warn).padding(10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                )
            }
            CtaButton("이 이름으로 시작", onClick = submit)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("처음 오셨네요", color = JsColor.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text("정산방에서 쓸 이름", color = JsColor.ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("카카오 닉네임을 가져왔어요. 친구들이 알아볼 이름으로 바꿔도 돼요.", color = JsColor.ink2, fontSize = 13.sp, lineHeight = 19.sp)
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Label("이름")
            Text("  ${nameLength(name.trim())}/$MAX_NAME", color = JsColor.ink3, fontSize = 12.sp)
        }
        NameField(name, { name = it }, 20.sp, onDone = submit)

        Column(
            Modifier.fillMaxWidth().background(JsColor.p50).border(2.dp, JsColor.line).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label("친구들에겐 이렇게 보여요")
            Text("⚙ ${preview}님이 들어왔어요", color = JsColor.ink2, fontSize = 12.5.sp)
            Text("⚙ ${preview}님이 보냈대요. 입금을 확인해주세요", color = JsColor.ink2, fontSize = 12.5.sp)
        }
    }
}

/** 이름 입력칸 — L2와 P1(첫 로그인)이 같이 쓴다 */
@Composable
fun NameField(value: String, onChange: (String) -> Unit, size: TextUnit, onDone: () -> Unit = {}) {
    BasicTextField(
        value = value,
        // 상한보다 조금 더 받아 두고 검증 문구로 알린다 — 붙여 넣은 이름이 말없이 잘리지 않게
        onValueChange = { if (it.length <= MAX_NAME + 4) onChange(it) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = TextStyle(color = JsColor.ink, fontSize = size, fontWeight = FontWeight.ExtraBold),
        cursorBrush = SolidColor(JsColor.p600),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFFBFDFF), RectangleShape).border(2.dp, JsColor.line, RectangleShape)
            .padding(horizontal = 13.dp, vertical = 12.dp).semantics { contentDescription = "정산방에서 쓸 이름" },
        decorationBox = { inner -> Box { inner() } },
    )
}
