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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.jeongsan.v3.NAME_GUIDE
import app.jeongsan.v3.User
import app.jeongsan.v3.nameLength
import app.jeongsan.v3.nameWithNick
import app.jeongsan.v3.validateName
import kotlinx.coroutines.launch

/**
 * L2 이름 확인 — 웹 `NamePage.tsx`. 첫 로그인 때 한 번 **실명을 성까지** 받는다 — 총무가 은행 앱의 입금자명과
 * 참여자를 맞춰 보기 때문이다. 칸은 비워 둔다(카카오 닉네임을 채워 두면 그대로 넘어가 실명이 안 모인다).
 * 한 번 정하면 바뀌지 않는다. 카카오 닉네임은 목록에서 `이름(닉네임)`으로 옆에 붙는다. 키보드의 [완료]로도 시작한다.
 */
@Composable
fun NameScreen(
    devBar: @Composable () -> Unit,
    me: User,
    onBack: () -> Unit,
    /** 실패면 보여줄 문구, 성공이면 null([app.jeongsan.v3.api.V3Gateway.confirmName]) */
    onConfirm: suspend (String) -> String?,
) {
    var name by remember(me.id) { mutableStateOf("") }
    var tried by remember(me.id) { mutableStateOf(false) }
    // 서버가 거절한 이유(예: 이미 이름을 정함). 입력을 고치면 지운다
    var serverError by remember(me.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val errors = serverError?.let { listOf(it) } ?: validateName(name)
    val preview = name.trim().ifEmpty { "김동규" }
    val submit = {
        tried = true
        if (!busy && validateName(name).isEmpty()) {
            busy = true
            scope.launch {
                serverError = onConfirm(name.trim())
                busy = false
            }
        }
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
            CtaButton(if (busy) "저장하는 중…" else "이 이름으로 시작", enabled = !busy, onClick = submit)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("처음 오셨네요", color = JsColor.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text("정산방에서 쓸 이름", color = JsColor.ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("한 번 정하면 바꿀 수 없어요.", color = JsColor.ink2, fontSize = 13.sp, lineHeight = 19.sp)
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Label("실명")
            Text("  ${nameLength(name.trim())}/$MAX_NAME", color = JsColor.ink3, fontSize = 12.sp)
        }
        NameGuide()
        NameField(name, { name = it; serverError = null }, 20.sp, onDone = submit)

        // 실제로 어떻게 보이는지 — 목록엔 이름(닉네임), 알림·타임라인 문장엔 이름만
        Column(
            Modifier.fillMaxWidth().background(JsColor.p50).border(2.dp, JsColor.line).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label("친구들에겐 이렇게 보여요")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("👤 ${nameWithNick(preview, me.nickname)}", color = JsColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("  참여자 목록", color = JsColor.ink3, fontSize = 11.5.sp)
            }
            Text("⚙ ${preview}님이 보냈대요. 입금을 확인해주세요", color = JsColor.ink2, fontSize = 12.5.sp)
        }
    }
}

/** 실명 안내 — 왜 실명인지까지 한 줄로. L2와 P1(첫 로그인)이 같이 쓴다 */
@Composable
fun NameGuide() {
    Text(NAME_GUIDE, color = JsColor.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
}

/** 이름 입력칸 — L2와 P1(첫 로그인)이 같이 쓴다. 비어 있으면 "예: 김동규" */
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
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("예: 김동규", color = JsColor.ink3, fontSize = size)
                inner()
            }
        },
    )
}
