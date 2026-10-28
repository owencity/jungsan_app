package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.User

@Composable
fun DemoControls(users: List<User>, current: Id, onPick: (Id) -> Unit, onReset: () -> Unit, onExit: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(JsColor.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 10.dp)) {
        Text("데모 체험 · 가상 데이터 · 실제 송금 없음", color = JsColor.ink)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            users.forEach { user ->
                TextButton(onClick = { onPick(user.id) }) {
                    Text((if (current == user.id) "✓ " else "") + user.displayName, color = JsColor.ink)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset) { Text("처음부터", color = JsColor.ink) }
            TextButton(onClick = onExit) { Text("체험 종료", color = JsColor.ink) }
        }
    }
}
