package app.jeongsan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jeongsan.ui.JeongsanTheme
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface

/**
 * 앱 진입점. **화면은 웹 목업이 명세다** — `C:\dev\profile\src\jeongsan` 의
 * 화면들을 Compose 로 옮긴다. 무엇을 보여줄지는 거기서 이미 정해졌으므로
 * 여기서 다시 정하지 않는다.
 */
@Composable
fun App() {
    JeongsanTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(JsColor.bg)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RetroSurface {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("정산어택", color = JsColor.ink, textAlign = TextAlign.Center)
                    Text(
                        "KMP 골격 · 안드로이드/iOS 공용",
                        color = JsColor.ink3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
