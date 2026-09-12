package app.jeongsan.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.PrimaryButton
import app.jeongsan.ui.Screen

/**
 * 주최자 로그인 겸 소개 화면. 웹의 `LoginPage.tsx` 를 옮긴 것.
 *
 * 받는 정보는 회원번호·닉네임·프로필 사진 셋뿐이다(카카오 기본 동의항목).
 * "N빵이 아니다"를 첫 문장에 세운다 — 무엇이 다른지부터 말해야 한다.
 *
 * ⚠ 아이콘 로고(SVG 11갈래 버스트)는 아직 안 옮겼다 — 벡터를 Compose Canvas 로
 * 새로 그려야 해서 별도 작업으로 미룬다. 지금은 텍스트만으로 화면을 채운다.
 */
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Screen {
        Column(
            Modifier.fillMaxWidth().padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("정산어택", color = JsColor.ink, fontSize = 28.sp, fontWeight = FontWeight.Black)

            Text(
                "같이 놀고 같이 먹는데 왜 총무만 고생을 해야하냐!",
                modifier = Modifier.padding(top = 18.dp),
                color = JsColor.accentStrong,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Text(
                "그래서 총무들의 고생을 위해 만든 정산 앱입니다.\n총무를 위해 당신의 클릭 1스푼을 얹어주세요.",
                modifier = Modifier.padding(top = 10.dp),
                color = JsColor.ink3,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            PrimaryButton("카카오로 3초만에 시작하기", onClick = onLogin)
        }
    }
}
