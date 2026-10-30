package app.jeongsan.v3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface
import app.jeongsan.v3.rememberOpenKakaoTalk

/** 복사가 완료돼야 안내와 앱 열기 선택을 보여준다. 취소·실행 실패로 클립보드를 지우지 않는다. */
@Suppress("DEPRECATION")
@Composable
fun rememberCopyAndOfferKakao(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val openKakao = rememberOpenKakaoTalk()
    var askToOpen by remember { mutableStateOf(false) }

    if (askToOpen) {
        Dialog(onDismissRequest = { askToOpen = false }) {
            RetroSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("복사되었습니다", color = JsColor.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("카카오톡 앱을 열까요?", color = JsColor.ink, fontWeight = FontWeight.Bold)
                    Text("보낼 대화방을 고른 뒤 붙여넣어 주세요.", color = JsColor.ink2)
                    CtaButton("카카오톡 열기") {
                        askToOpen = false
                        openKakao { opened ->
                            if (!opened) V3Toast.show("카카오톡을 열 수 없어요. 복사한 내용을 직접 붙여넣어 주세요.")
                        }
                    }
                    SubButton("나중에") { askToOpen = false }
                }
            }
        }
    }

    return remember(clipboard) {
        { text ->
            try {
                clipboard.setText(AnnotatedString(text))
                V3Toast.show("복사되었습니다")
                askToOpen = true
            } catch (_: Exception) {
                askToOpen = false
                V3Toast.show("복사하지 못했어요. 다시 눌러주세요.")
            }
        }
    }
}
