package app.jeongsan.v3.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.SecondaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 기존 로그인 복원 시 동의 기록이 없는 계정만 보며, 이름은 다시 묻지 않는다. */
@Composable
fun TermsConsentScreen(userId: Long, onAgree: () -> Unit, onLogout: suspend () -> Unit) {
    var agreed by remember(userId) { mutableStateOf(false) }
    var busy by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    V3Screen(devBar = {}, top = null, bottom = {
        TermsAgree(agreed) { if (!busy) agreed = it }
        CtaButton("동의하고 계속", enabled = agreed && !busy, onClick = onAgree)
        SecondaryButton(if (busy) "로그아웃 중이에요" else "로그아웃", enabled = !busy) {
            busy = true
            scope.launch {
                try { onLogout() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "로그아웃하지 못했어요. 다시 시도해주세요" }
                finally { busy = false }
            }
        }
    }) {
        Text("이용약관을 확인해주세요", color = JsColor.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("모두가 편하게 정산할 수 있도록 이용약관에 동의한 뒤 계속해주세요.", color = JsColor.ink2,
            fontSize = 14.sp, lineHeight = 21.sp)
        error?.let { Text(it, color = JsColor.warn, fontSize = 14.sp) }
    }
}
