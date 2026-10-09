package app.jeongsan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.jeongsan.v3.LaunchOptions
import app.jeongsan.v3.api.AndroidAppContext
import app.jeongsan.v3.api.AppAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidAppContext.context = applicationContext
        // 스토어 스크린샷: adb shell am start -n app.jeongsan/.MainActivity -e jsScreen room
        LaunchOptions.screenshot = intent?.getStringExtra("jsScreen")
        // 앱이 꺼져 있다가 로그인 복귀 주소로 열린 경우
        intent?.dataString?.let(AppAuth::handle)
        setContent { App() }
    }

    /** 로그인 복귀(jeongsan://auth?ticket=…) — 앱이 떠 있는 동안 브라우저에서 돌아온 경우 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(AppAuth::handle)
    }
}
