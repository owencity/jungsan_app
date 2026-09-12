package app.jeongsan.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.Id
import app.jeongsan.domain.Provider
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.MiniButton
import app.jeongsan.ui.PersonRow
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.TagTone

/**
 * H5 — 명단. **제거와 링크 재발급만** 한다. 웹의 `host/RosterPage.tsx` 를 옮긴 것.
 *
 * 주최자가 이름을 대신 적는 기능은 없다. 참여는 로그인으로만 이뤄지고, 확정 후
 * 나타난 사람은 되돌리기로 받는다.
 *
 * 제거가 남은 이유는 링크만 알면 아무나 들어올 수 있으므로 주최자가 빼낼 수단이
 * 있어야 한다는 것 — 확정 경고의 **초과** 케이스에서 쓰인다.
 */
@Composable
fun RosterScreen(
    g: Gathering,
    onBack: () -> Unit,
    onReissue: () -> Unit,
    onRemove: (Id) -> Unit,
) {
    val expected = g.expectedCount

    Screen {
        JsBar(
            "명단",
            onBack = onBack,
            step = if (expected != null) "${g.participants.size} / ${expected}명" else "${g.participants.size}명",
        )

        Hint("링크로 들어와 로그인한 사람만 목록에 있습니다. 주최자가 이름을 대신 적는 기능은 없습니다.")

        g.participants.forEach { p ->
            PersonRow(
                name = p.name,
                isHost = p.isHost,
                sub = (if (p.provider == Provider.KAKAO) "카카오" else "구글") + if (p.payout != null) " · 계좌 등록됨" else "",
                right = if (!p.isHost) {
                    { MiniButton("제거", tone = TagTone.WARN, onClick = { onRemove(p.id) }) }
                } else {
                    null
                },
            )
        }

        Hint("링크만 알면 아무나 들어올 수 있으니 모르는 사람은 여기서 뺍니다.")

        // 공유 링크는 백엔드 호스트다. 이 앱 화면 안에서는 경로만 보여준다 —
        // 실제 공유(카카오톡 등)는 참여자 화면을 붙일 때 마저 연결한다.
        SectionLabel("공유 링크")
        Text(
            "/g/${g.shareToken}",
            modifier = Modifier
                .fillMaxWidth()
                .background(JsColor.p50, RectangleShape)
                .padding(horizontal = 13.dp, vertical = 9.dp),
            color = JsColor.p600, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Hint("링크에는 참석자 이름과 금액이 담깁니다. 엉뚱한 곳에 공유했다면 재발급하세요. 기존 링크는 즉시 무효가 됩니다.")

        Spacer(Modifier.height(20.dp))
        SecondaryButton("링크 재발급", onClick = onReissue)
    }
}
