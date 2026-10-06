package app.jeongsan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.jeongsan.v3.LaunchOptions
import app.jeongsan.v3.api.AndroidAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidAppContext.context = applicationContext
        // 스토어 스크린샷: adb shell am start -n app.jeongsan/.MainActivity -e jsScreen room
        LaunchOptions.screenshot = intent?.getStringExtra("jsScreen")
        setContent { App() }
    }
}
