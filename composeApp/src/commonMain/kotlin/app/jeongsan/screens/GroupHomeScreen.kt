package app.jeongsan.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.jeongsan.domain.GroupRole
import app.jeongsan.domain.GroupSummary
import app.jeongsan.domain.GroupType
import app.jeongsan.domain.Id
import app.jeongsan.ui.DashedAddButton
import app.jeongsan.ui.EmptyState
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.RetroSurface
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.Tag
import app.jeongsan.ui.TagTone

/**
 * H0 — 내 모임. 로그인하면 처음 나오는 화면. 웹의 `host/GroupHomePage.tsx` 를 옮긴 것.
 *
 * **총무인 모임과 참여자로 있는 모임을 갈라서 보여준다.** 둘은 할 수 있는 일이
 * 다르다 — 총무는 금액을 넣고 확정하지만, 참여자는 자기 체크만 한다.
 *
 * 용어 — 모임(Group) 안에 술자리(Gathering) 가 들어간다.
 *   주기(RECURRING)  계속 만나는 고정 멤버. 술자리를 여러 개 담는다
 *   번개(FLASH)      1회성. 술자리 딱 하나
 */
@Composable
fun GroupHomeScreen(
    groups: List<GroupSummary>,
    meName: String?,
    onOpen: (Id) -> Unit,
    onCreate: () -> Unit,
    onSearch: () -> Unit,
    onAlerts: () -> Unit,
) {
    val owned = groups.filter { it.role == GroupRole.OWNER }
    val joined = groups.filter { it.role == GroupRole.MEMBER }

    Screen {
        JsBar("내 모임", step = meName)

        // 알림함·이의제기 채팅은 다음 단계에서 붙인다 — 지금은 홈에서 넘어갈 자리만 만든다.
        SecondaryButton("알림", onClick = onAlerts)

        Spacer(Modifier.height(8.dp))

        // 모임에 들어오는 길이 둘이다 — 링크를 받거나, 이름으로 찾거나.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashedAddButton("+ 새 모임", modifier = Modifier.weight(1f), onClick = onCreate)
            DashedAddButton("모임 찾기", modifier = Modifier.weight(1f), onClick = onSearch)
        }

        if (groups.isEmpty()) {
            EmptyState("아직 모임이 없습니다", "새 모임을 만들거나, 총무가 보내준 링크로 들어오세요.")
        }

        if (owned.isNotEmpty()) {
            SectionLabel("내가 총무인 모임", owned.size)
            Column {
                owned.forEach { GroupRow(it, onOpen) }
            }
        }

        if (joined.isNotEmpty()) {
            SectionLabel("참여 중인 모임", joined.size)
            Column {
                joined.forEach { GroupRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun GroupRow(g: GroupSummary, onOpen: (Id) -> Unit) {
    RetroSurface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(g.id) },
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(
                    if (g.groupType == GroupType.FLASH) "번개" else "주기",
                    tone = if (g.groupType == GroupType.FLASH) TagTone.FLASH else TagTone.NEUTRAL,
                )
                Text(
                    g.name,
                    modifier = Modifier.padding(start = 8.dp),
                    color = JsColor.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            // 참여 중인 모임에서는 **누가 총무인지**가 멤버 수보다 먼저 궁금하다 —
            // 뭘 물어보거나 돈을 보낼 상대이기 때문이다.
            Text(
                buildAnnotatedString {
                    if (g.role == GroupRole.MEMBER) {
                        append("총무 ")
                        withStyle(SpanStyle(color = JsColor.accentStrong, fontWeight = FontWeight.ExtraBold)) {
                            append(g.ownerName)
                        }
                        append(" · ")
                    }
                    append("멤버 ${g.memberCount}명 · ")
                    append(if (g.gatheringCount > 0) "술자리 ${g.gatheringCount}회" else "아직 술자리 없음")
                },
                color = JsColor.ink3,
                fontSize = 12.sp,
            )
        }
    }
}
