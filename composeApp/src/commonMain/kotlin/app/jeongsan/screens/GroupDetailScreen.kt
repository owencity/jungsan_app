package app.jeongsan.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.jeongsan.domain.GatheringStatus
import app.jeongsan.domain.GroupDetail
import app.jeongsan.domain.GroupGathering
import app.jeongsan.domain.GroupType
import app.jeongsan.domain.Id
import app.jeongsan.ui.DashedAddButton
import app.jeongsan.ui.EmptyState
import app.jeongsan.ui.Hint
import app.jeongsan.ui.JsBar
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.MemberChip
import app.jeongsan.ui.RetroSurface
import app.jeongsan.ui.Screen
import app.jeongsan.ui.SecondaryButton
import app.jeongsan.ui.SectionLabel
import app.jeongsan.ui.Tag
import app.jeongsan.ui.TagTone
import kotlinx.datetime.LocalDate

/**
 * 모임 상세 — 웹의 `host/GroupDetailPage.tsx` 를 옮긴 것.
 *
 * **모임 안의 술자리 목록**이 본체다. 여기서 새 술자리를 만들거나 지난 정산을 다시 본다.
 *
 * 번개(FLASH)와 주기(RECURRING)가 다르게 동작한다
 *   주기  술자리를 계속 추가한다
 *   번개  술자리가 딱 하나. "새 술자리" 버튼을 아예 보여주지 않는다 —
 *         눌러도 서버가 막을 버튼을 띄우면 사용자는 고장으로 받아들인다
 */
@Composable
fun GroupDetailScreen(
    group: GroupDetail,
    isOwner: Boolean,
    /** 술자리별 총무 이름과 내 참여 여부. 목록 API 에 없는 값이라 따로 넘긴다. */
    gatheringInfo: (Id) -> Pair<String, Boolean>?,
    onOpenGathering: (Id) -> Unit,
    onNewGathering: () -> Unit,
    /** 참여자가 스스로 정산에 들어간다. */
    onSelfJoin: (Id) -> Unit,
    onInvite: () -> Unit,
    onBack: () -> Unit,
) {
    val flash = group.groupType == GroupType.FLASH

    Screen {
        JsBar(group.name, onBack = onBack, step = if (flash) "번개" else "주기")

        // 멤버 — 이름만 훑을 수 있으면 된다. 관리는 총무만.
        SectionLabel("멤버 · ${group.members.size}명")
        MemberChips(group)

        if (isOwner) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("초대 링크 복사", onClick = onInvite)
        }

        // 술자리
        SectionLabel("술자리 · ${group.gatherings.size}회", modifier = Modifier.padding(top = 10.dp))

        if (group.gatherings.isEmpty()) {
            EmptyState(
                title = "아직 술자리가 없습니다",
                body = if (isOwner) "첫 술자리를 만들어보세요." else "총무가 만들면 여기 보입니다.",
            )
        }

        Column {
            group.gatherings.forEach { g ->
                GatheringRow(g, gatheringInfo(g.id), onOpenGathering, onSelfJoin)
            }
        }

        // 번개는 술자리를 더 못 만든다 — 버튼 자체를 숨긴다
        if (isOwner && !flash) {
            DashedAddButton("+ 새 술자리", modifier = Modifier.padding(top = 4.dp), onClick = onNewGathering)
        }

        if (flash) {
            Hint("번개 모임은 술자리 하나로 끝납니다. 정산이 확정되고 2주 뒤 목록에서 사라지지만, 결제 내역에는 남습니다.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChips(group: GroupDetail) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        group.members.forEach { m ->
            MemberChip(name = m.nickname, isOwner = m.role == app.jeongsan.domain.GroupRole.OWNER)
        }
    }
}

@Composable
private fun GatheringRow(
    g: GroupGathering,
    info: Pair<String, Boolean>?,
    onOpenGathering: (Id) -> Unit,
    onSelfJoin: (Id) -> Unit,
) {
    val collecting = g.status == GatheringStatus.COLLECTING
    val joined = info?.second ?: true // 정보가 없으면(예: 목업 미비) 굳이 참여 버튼을 들이밀지 않는다
    val showSelfJoin = info != null && !joined && collecting

    RetroSurface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpenGathering(g.id) },
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.name,
                    modifier = Modifier.weight(1f),
                    color = JsColor.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Tag(
                    if (collecting) "수집 중" else "확정됨",
                    tone = if (collecting) TagTone.OK else TagTone.DONE,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildAnnotatedString {
                    append(dateLabel(g.date))
                    // **총무는 술자리마다 다르다.** 모임 개설자가 아니라 이 자리를 맡은
                    // 사람이다 — 이번엔 내가, 다음엔 네가 계산하는 게 실제 모습이다.
                    if (info != null) {
                        append(" · 총무 ")
                        withStyle(SpanStyle(color = JsColor.accentStrong, fontWeight = FontWeight.ExtraBold)) {
                            append(info.first)
                        }
                    }
                    if (showSelfJoin) {
                        append(" · ")
                        withStyle(SpanStyle(color = JsColor.accentStrong, fontWeight = FontWeight.ExtraBold)) {
                            append("참여 안 함")
                        }
                    }
                },
                color = JsColor.ink3,
                fontSize = 12.sp,
            )
            // **총무가 명단을 짜지 않는다.** 참여자가 스스로 들어온다 — 총무가 고르게
            // 하면 반드시 빠뜨린 사람이 생기고, 그 사람은 정산에서 누락된다.
            if (showSelfJoin) {
                Text(
                    "이 술자리 정산에 참여하기",
                    modifier = Modifier.padding(top = 8.dp)
                        .clickable { onSelfJoin(g.id) },
                    color = JsColor.p600,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 날짜 표시. 웹의 `api.ts` `dateLabel` 을 간략화해 옮긴 것 — "M월 D일" 형식. */
private fun dateLabel(iso: String): String {
    val d = LocalDate.parse(iso)
    return "${d.monthNumber}월 ${d.dayOfMonth}일"
}
