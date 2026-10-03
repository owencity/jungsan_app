package app.jeongsan.v3

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android 공유 시트 — ACTION_SEND 텍스트. 카카오톡이 깔려 있으면 목록에 바로 뜬다 */
@Composable
actual fun rememberShareSheet(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, "술자리 링크 보내기").apply {
                // 액티비티가 아닌 컨텍스트에서 띄우면 새 태스크 플래그가 필요하다
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
