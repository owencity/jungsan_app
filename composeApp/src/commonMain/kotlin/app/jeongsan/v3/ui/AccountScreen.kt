package app.jeongsan.v3.ui

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.BANKS
import app.jeongsan.v3.Payout
import app.jeongsan.v3.User
import app.jeongsan.v3.cleanAccountNo
import app.jeongsan.v3.validatePayout

/**
 * A1 계좌 등록 — 웹 `AccountPage.tsx`. 결제자가 돈을 받을 계좌를 **한 번** 등록하면 다음부터 재사용한다.
 * 은행은 버튼으로 고르고, 직접 치는 건 계좌번호(숫자 키패드)와 예금주뿐이다. 예금주는 표시 이름이 기본값.
 */
@Composable
fun AccountScreen(
    devBar: @Composable () -> Unit,
    me: User,
    onBack: () -> Unit,
    onSave: (Payout) -> Unit,
    /** 내 술자리에서 연 경우만 — 계정 메뉴(로그아웃·탈퇴)를 맨 아래에 둔다 */
    onLogout: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null,
) {
    var bank by remember(me.id) { mutableStateOf(me.payout?.bank.orEmpty()) }
    var accountNo by remember(me.id) { mutableStateOf(me.payout?.accountNo.orEmpty()) }
    var holder by remember(me.id) { mutableStateOf(me.payout?.holder ?: me.displayName) }
    var tried by remember(me.id) { mutableStateOf(false) }
    val draft = Payout(bank, accountNo, holder)
    val errors = validatePayout(draft)

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle(if (me.payout != null) "받을 계좌 바꾸기" else "받을 계좌 등록")
        },
        bottom = {
            if (tried && errors.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().background(JsColor.warnBg).border(2.dp, JsColor.warn).padding(10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) { for (e in errors) Text(e, color = JsColor.warn, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
            }
            CtaButton("저장") {
                tried = true
                if (errors.isEmpty()) onSave(draft)
            }
        },
    ) {
        Text("한 번 등록하면 다음 술자리에서도 그대로 써요. 보낼 사람들에게만 보여요.", color = JsColor.ink2, fontSize = 14.sp, lineHeight = 20.sp)

        Label("은행")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in BANKS.chunked(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (b in row) {
                        val on = bank == b
                        Box(
                            Modifier.weight(1f).background(if (on) JsColor.p600 else Color.White)
                                .border(2.dp, if (on) JsColor.ink else JsColor.line)
                                .clickable { bank = b }.semantics { role = Role.RadioButton; selected = on }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(b, color = if (on) Color.White else JsColor.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Label("계좌번호")
        Field(accountNo, { accountNo = cleanAccountNo(it) }, "숫자만 넣어도 돼요", "계좌번호", KeyboardType.Number, 20.sp)

        Label("예금주")
        Field(holder, { if (it.length <= 20) holder = it }, "", "예금주", KeyboardType.Text, 14.sp)

        if (onLogout != null || onDeleteAccount != null) AccountMenu(onLogout, onDeleteAccount)
    }
}

/**
 * 계정 — 로그아웃과 탈퇴. 탈퇴는 되돌릴 수 없어 두 번 눌러야 한다(차수 지우기와 같은 방식).
 * 앱 안에서 탈퇴를 시작할 수 있어야 한다(App Store 5.1.1(v)).
 */
@Composable
private fun AccountMenu(onLogout: (() -> Unit)?, onDeleteAccount: (() -> Unit)?) {
    var armed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("계정")
        if (onLogout != null) SubButton("로그아웃", onLogout)
        if (onDeleteAccount != null) {
            if (armed) {
                Text(
                    "탈퇴하면 계정과 등록한 계좌가 지워지고 되돌릴 수 없어요. 지난 정산 기록에는 ‘탈퇴한 사용자’로 남아요. 진행 중인 정산이 있으면 끝난 뒤에 탈퇴할 수 있어요.",
                    color = JsColor.warn, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }
            Text(
                if (armed) "한 번 더 누르면 탈퇴해요" else "회원 탈퇴",
                Modifier.fillMaxWidth().clickable { if (armed) onDeleteAccount() else armed = true }
                    .semantics { role = Role.Button }.padding(vertical = 8.dp),
                color = if (armed) JsColor.warn else JsColor.ink3, fontSize = 13.5.sp,
                fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, desc: String, type: KeyboardType, size: TextUnit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None),
        textStyle = TextStyle(color = JsColor.ink, fontSize = size, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(JsColor.p600),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFFBFDFF), RectangleShape).border(2.dp, JsColor.line, RectangleShape)
            .padding(horizontal = 13.dp, vertical = 12.dp).semantics { contentDescription = desc },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = JsColor.ink3, fontSize = size, textAlign = TextAlign.Start)
                inner()
            }
        },
    )
}
