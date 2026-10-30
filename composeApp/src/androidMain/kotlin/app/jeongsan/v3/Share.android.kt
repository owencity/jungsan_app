package app.jeongsan.v3

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 메시지를 보내지 않고 카카오톡의 시작 화면만 연다. 붙여넣기와 전송은 사용자가 한다. */
@Composable
actual fun rememberOpenKakaoTalk(): (onResult: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { onResult ->
            val opened = try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.kakao.talk")
                if (intent == null) false
                else {
                    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
            onResult(opened)
        }
    }
}
