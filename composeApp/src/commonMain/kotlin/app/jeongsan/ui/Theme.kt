package app.jeongsan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 정산어택 디자인 토큰 — **웹(`jeongsan.css`)과 같은 값**이다.
 *
 * 두 곳에 색을 따로 적으면 반드시 갈라진다. 웹을 고칠 때 여기도 같이 고쳐야 한다.
 * 색은 아이콘 시안 C(딥블루 + 오렌지)에서 나왔다.
 */
object JsColor {
    val p700 = Color(0xFF1B4F9C)
    val p600 = Color(0xFF2C74D6)
    val p500 = Color(0xFF4A8CE8)
    val p100 = Color(0xFFD6E6FB)
    val p50 = Color(0xFFEFF6FF)

    /** 오렌지 — "내 금액"과 "미입금" 두 곳에만 쓴다. 남발하면 강조가 죽는다. */
    val accent = Color(0xFFFF9A3D)
    val accentStrong = Color(0xFFE07B1C)
    val accentBg = Color(0xFFFFF4E8)

    val warn = Color(0xFFD93025)
    val warnBg = Color(0xFFFDECEA)
    val ok = Color(0xFF1E9E6A)
    val okBg = Color(0xFFEAF7F1)
    /** "보냈다고 함"처럼 완료도 미완료도 아닌 애매한 상태. 경고(warn)와는 톤을 갈라야 한다. */
    val wait = Color(0xFFB8860B)
    val waitBg = Color(0xFFFFF7E8)

    val ink = Color(0xFF0F2540)
    val ink2 = Color(0xFF41597C)
    val ink3 = Color(0xFF8AA0BE)
    val line = Color(0xFFC9D9EF)

    val surface = Color(0xFFFFFFFF)
    val bg = Color(0xFFF6FAFF)
}

/**
 * 8비트 셰이프 토큰.
 *
 * **각진 모서리 + 번짐 없는 그림자**가 레트로 느낌의 전부다.
 * 둥근 모서리나 블러 그림자를 쓰는 순간 평범한 머티리얼 UI 로 돌아간다.
 */
object JsShape {
    /** 모서리 — 전부 각지게. Compose 기본은 둥그니 명시적으로 덮는다. */
    val corner = RectangleShape
    val borderWidth: Dp = 2.dp
    val shadowOffset: Dp = 3.dp
    val shadowOffsetSmall: Dp = 2.dp
}

/**
 * 그림자가 있는 상자.
 *
 * `Modifier.shadow` 는 **블러가 들어가서 못 쓴다.** 8비트 느낌은 번짐 없는
 * 오프셋 그림자에서 나오므로, 뒤에 같은 크기의 검은 사각형을 어긋나게 깔아 직접 만든다.
 * Modifier 체인만으로는 "뒤에 깔기"가 안 돼서 컴포저블로 감싼다.
 */
@Composable
fun RetroSurface(
    modifier: Modifier = Modifier,
    background: Color = JsColor.surface,
    border: Color = JsColor.ink,
    shadowOffset: Dp = JsShape.shadowOffset,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        // 뒤에 깔리는 솔리드 그림자
        Box(
            Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(JsColor.ink, JsShape.corner),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(background, JsShape.corner)
                .border(JsShape.borderWidth, border, JsShape.corner),
        )
        Box(Modifier.padding(1.dp)) { content() }
    }
}

private val JsTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun JeongsanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = JsColor.p600,
            onPrimary = Color.White,
            secondary = JsColor.accent,
            background = JsColor.bg,
            surface = JsColor.surface,
            onSurface = JsColor.ink,
            error = JsColor.warn,
        ),
        typography = JsTypography,
        content = content,
    )
}
