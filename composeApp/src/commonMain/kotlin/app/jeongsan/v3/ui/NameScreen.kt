package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
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
 *
 * 위에 [뒤로가기]를 두지 않는다 — 처음 온 사람에게 "어디로 돌아가나"가 헷갈렸다(2026-10-19 CTO).
 * 다른 계정으로 잘못 들어온 사람만 아래 작은 [로그아웃]으로 나간다. 웹과 같다.
 */
@Composable
fun NameScreen(
    devBar: @Composable () -> Unit,
    me: User,
    /** 잘못 로그인한 사람의 출구 — 서버 로그아웃 뒤 로그인 화면으로 */
    onLogout: () -> Unit,
    /** 실패면 보여줄 문구, 성공이면 null([app.jeongsan.v3.api.V3Gateway.confirmName]) */
    onConfirm: suspend (String) -> String?,
) {
    var name by remember(me.id) { mutableStateOf("") }
    var tried by remember(me.id) { mutableStateOf(false) }
    // 서버가 거절한 이유(예: 이미 이름을 정함). 입력을 고치면 지운다
    var serverError by remember(me.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // 이용약관 동의(FC-024) — 체크해야 시작할 수 있다
    var agreed by remember(me.id) { mutableStateOf(false) }
    val nameFocus = remember(me.id) { FocusRequester() }
    val nameArea = remember(me.id) { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    var invalidAttempts by remember(me.id) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val errors = serverError?.let { listOf(it) } ?: validateName(name)
    val preview = name.trim().ifEmpty { "김동규" }
    val submit = {
        tried = true
        if (!busy && validateName(name).isNotEmpty()) invalidAttempts++
        if (!busy && agreed && validateName(name).isEmpty()) {
            busy = true
            scope.launch {
                serverError = onConfirm(name.trim())
                busy = false
            }
        }
    }
    LaunchedEffect(invalidAttempts) {
        if (invalidAttempts > 0) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(invalidAttempts, keyboardHeight) {
        if (invalidAttempts > 0) {
            // 포커스를 준 직후에는 아직 키보드 전 높이일 수 있어, 새 배치에서 오류까지 함께 보이게 한다.
            withFrameNanos { }
            nameArea.bringIntoView()
        }
    }

    V3Screen(
        devBar = devBar,
        top = {},
        bottom = {
            CtaButton(if (busy) "저장하는 중…" else "이 이름으로 시작", enabled = !busy && agreed, onClick = submit)
            Text(
                "잘못 로그인했나요? 로그아웃",
                Modifier.fillMaxWidth().clickable(onClick = onLogout).semantics { role = Role.Button }.padding(vertical = 4.dp),
                color = JsColor.ink3, fontSize = 12.5.sp, textAlign = TextAlign.Center, textDecoration = TextDecoration.Underline,
            )
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
        // 키보드·데모 바·오류가 동시에 떠도 고정 하단이 입력칸의 높이를 빼앗지 않게 본문에서 함께 스크롤한다.
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(nameArea), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NameField(name, { name = it; serverError = null }, 20.sp, onDone = submit,
                modifier = Modifier.focusRequester(nameFocus))
            if (tried && errors.isNotEmpty()) {
                Text(
                    errors.first(), Modifier.fillMaxWidth().background(JsColor.warnBg).border(2.dp, JsColor.warn).padding(10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        TermsAgree(agreed) { agreed = it }

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
fun NameField(value: String, onChange: (String) -> Unit, size: TextUnit, onDone: () -> Unit = {}, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        // 상한보다 조금 더 받아 두고 검증 문구로 알린다 — 붙여 넣은 이름이 말없이 잘리지 않게
        onValueChange = { if (it.length <= MAX_NAME + 4) onChange(it) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = TextStyle(color = JsColor.ink, fontSize = size, fontWeight = FontWeight.ExtraBold),
        cursorBrush = SolidColor(JsColor.p600),
        modifier = modifier.fillMaxWidth().background(Color(0xFFFBFDFF), RectangleShape).border(2.dp, JsColor.line, RectangleShape)
            .padding(horizontal = 13.dp, vertical = 12.dp).semantics { contentDescription = "정산방에서 쓸 이름" },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("예: 김동규", color = JsColor.ink3, fontSize = size)
                inner()
            }
        },
    )
}
