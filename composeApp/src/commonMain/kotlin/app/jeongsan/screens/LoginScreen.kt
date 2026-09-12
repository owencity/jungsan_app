package app.jeongsan.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.PixelSunsetBackground
import app.jeongsan.ui.PrimaryButton

/**
 * 주최자 로그인 겸 소개 화면. 웹의 `LoginPage.tsx` 를 옮긴 것.
 *
 * 받는 정보는 회원번호·닉네임·프로필 사진 셋뿐이다(카카오 기본 동의항목).
 * "N빵이 아니다"를 첫 문장에 세운다 — 무엇이 다른지부터 말해야 한다.
 *
 * **다른 화면과 달리 공용 `Screen()` 을 안 쓴다.** 로그인만 도트 석양 시티뷰를
 * 화면 전체 배경으로 선명하게 쓴다(웹의 `js-login-mode`) — 나머지 화면은 강변을
 * 옅게 깐다. 배경이 화려한 만큼 글자에는 웹의 `text-shadow` 와 같은 방식으로
 * 그림자를 줘서 대비를 지킨다.
 *
 * ⚠ 아이콘 로고(SVG 11갈래 버스트)는 아직 안 옮겼다 — 벡터를 Compose Canvas 로
 * 새로 그려야 해서 별도 작업으로 미룬다.
 */
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 10f)

    Box(Modifier.fillMaxSize()) {
        PixelSunsetBackground(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "정산어택",
                    style = TextStyle(shadow = textShadow),
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black,
                )

                Text(
                    "같이 놀고 같이 먹는데 왜 총무만 고생을 해야하냐!",
                    modifier = Modifier.padding(top = 18.dp),
                    style = TextStyle(shadow = textShadow),
                    color = JsColor.accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    "그래서 총무들의 고생을 위해 만든 정산 앱입니다.\n총무를 위해 당신의 클릭 1스푼을 얹어주세요.",
                    modifier = Modifier.padding(top = 10.dp),
                    style = TextStyle(shadow = textShadow),
                    color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                PrimaryButton("카카오로 3초만에 시작하기", onClick = onLogin)
            }
        }
    }
}
