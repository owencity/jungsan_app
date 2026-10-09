package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.HomeTab
import app.jeongsan.v3.Tone
import app.jeongsan.v3.User
import app.jeongsan.v3.daysUntilDelete
import app.jeongsan.v3.host
import app.jeongsan.v3.initialTab
import app.jeongsan.v3.myRoomTabs
import app.jeongsan.v3.nextAction
import app.jeongsan.v3.rowBadge
import app.jeongsan.v3.tabHasTodo
import kotlinx.datetime.Clock

/**
 * H1 내 술자리 — 웹 `HomePage.tsx`. 탭 [내가 총무] [참여 중] [완료], 줄마다 할 일 한 줄과 뱃지.
 */
private val EMPTY = mapOf(
    HomeTab.HOSTING to "총무로 연 술자리가 없어요. 아래 [+ 새 술자리]로 시작해요",
    HomeTab.JOINED to "참여 중인 술자리가 없어요. 친구가 보낸 링크로 들어올 수 있어요",
    HomeTab.DONE to "완료된 술자리가 없어요. 완료되면 7일 동안 여기 남아요",
)

@Composable
fun HomeScreen(
    devBar: @Composable () -> Unit,
    me: User,
    rooms: Collection<Gathering>,
    paySeen: (Id) -> Boolean,
    unread: Int,
    onBack: () -> Unit,
    onOpen: (Id) -> Unit,
    onCreate: () -> Unit,
    onOpenAlerts: () -> Unit,
    onEditAccount: () -> Unit,
    /** 계정 메뉴(로그아웃·탈퇴) — 서버 계정이 있을 때만. 탈퇴를 찾기 쉽게 따로 보인다(App Store 5.1.1(v)) */
    onOpenAccount: (() -> Unit)? = null,
) {
    val tabs = myRoomTabs(rooms, me.id)
    // 보는 사람이 바뀌면 처음 열 탭도 그 사람 기준으로 다시 고른다
    var tab by remember(me.id) { mutableStateOf(initialTab(tabs, me.id)) }
    val neverHosted = me.spoonCount == 0 && rooms.none { it.hostUserId == me.id }
    val list = tabs.getValue(tab)

    V3Screen(
        devBar = devBar,
        top = {
            BackButton(onBack)
            TopTitle("내 술자리")
            Bell(unread, onOpenAlerts)
        },
        bottom = { CtaButton("+ 새 술자리", onClick = onCreate) },
    ) {
        RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(78.dp).background(JsColor.p50).border(2.dp, JsColor.line), contentAlignment = Alignment.Center) {
                    HostCharacter(me.spoonCount, pixel = 3.2.dp)
                }
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(me.displayName, color = JsColor.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    SpoonChip(me.spoonCount)
                    if (neverHosted) Text("첫 정산을 만들어보세요. 총무를 할수록 캐릭터가 자라요", color = JsColor.ink2, fontSize = 12.sp)
                    // 받을 계좌 — 결제자가 됐을 때 매번 묻지 않게 여기서 미리 넣고 바꾼다
                    val payout = me.payout
                    Text(
                        if (payout != null) "받을 계좌 ${payout.bank} ${payout.accountNo} · 바꾸기" else "받을 계좌 등록하기 ›",
                        Modifier.clickable(onClick = onEditAccount).semantics { role = Role.Button }.padding(vertical = 2.dp),
                        color = JsColor.p600, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    )
                    if (onOpenAccount != null) {
                        Text(
                            "내 계정 · 로그아웃 · 탈퇴 ›",
                            Modifier.clickable(onClick = onOpenAccount).semantics { role = Role.Button }.padding(vertical = 2.dp),
                            color = JsColor.ink3, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // 탭 — 세 칸을 똑같이 나눈 각진 버튼. 고른 탭은 진하게
        Row(Modifier.fillMaxWidth().border(2.dp, JsColor.ink, RectangleShape)) {
            HomeTab.entries.forEachIndexed { i, t ->
                val on = tab == t
                val todo = t != HomeTab.DONE && tabHasTodo(tabs.getValue(t), me.id)
                if (i > 0) Box(Modifier.width(2.dp).height(44.dp).background(JsColor.ink))
                Box(
                    Modifier.weight(1f).height(44.dp).background(if (on) JsColor.ink else Color.White)
                        .clickable { tab = t }.semantics { role = Role.Tab; selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label, color = if (on) Color.White else JsColor.ink2, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" ${tabs.getValue(t).size}", color = if (on) JsColor.p100 else JsColor.ink3, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    if (todo) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset((-8).dp, 6.dp).size(8.dp).background(JsColor.accent)
                                .border(1.dp, JsColor.ink).semantics { contentDescription = "할 일 있음" },
                        )
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().background(Color.White).border(2.dp, JsColor.ink3).padding(vertical = 18.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(EMPTY.getValue(tab), color = JsColor.ink2, fontSize = 13.5.sp)
            }
        } else {
            for (g in list) RoomRow(g, me.id, tab, paySeen(g.id)) { onOpen(g.id) }
        }
    }
}

@Composable
private fun Bell(unread: Int, onClick: () -> Unit) {
    Box(Modifier.semantics { contentDescription = if (unread > 0) "알림 ${unread}개 안 읽음" else "알림" }) {
        RetroSurface(Modifier.size(44.dp, 40.dp).clickable(onClick = onClick), shadowOffset = JsShape.shadowOffsetSmall) {
            Box(Modifier.size(42.dp, 38.dp), contentAlignment = Alignment.Center) { Text("🔔", fontSize = 18.sp) }
        }
        if (unread > 0) {
            Text(
                "$unread",
                modifier = Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).background(JsColor.warn).border(2.dp, JsColor.ink)
                    .padding(horizontal = 5.dp),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun RoomRow(g: Gathering, meId: Id, tab: HomeTab, seen: Boolean, onClick: () -> Unit) {
    val isHost = g.hostUserId == meId
    val act = nextAction(g, meId)
    val days = g.daysUntilDelete(Clock.System.now())
    val badge = rowBadge(g, meId, seen)
    // 왼쪽 굵은 선 색 = 내가 지금 손대야 하는 방인가
    val edge = when (act.tone) {
        Tone.TODO -> JsColor.p600
        Tone.WAIT -> JsColor.ink3
        Tone.DONE -> JsColor.ok
    }
    val done = tab == HomeTab.DONE
    RetroSurface(
        Modifier.fillMaxWidth().alpha(if (done) 0.8f else 1f).clickable(onClick = onClick).semantics { role = Role.Button },
        shadowOffset = if (done) 0.dp else JsShape.shadowOffsetSmall,
    ) {
        // 왼쪽 선이 줄 높이(글자 길이에 따라 달라짐)를 그대로 따라가게
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(edge))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        g.title, modifier = Modifier.weight(1f), color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (badge != null) Chip(badge, ChipTone.ALERT)
                    // 탭이 이미 역할을 말해주니, 역할이 섞이는 [완료]에서만 붙인다. [참여 중]엔 누가 총무인지
                    if (done) Chip(if (isHost) "총무" else "참여", if (isHost) ChipTone.HOST else ChipTone.NEUTRAL)
                    if (tab == HomeTab.JOINED) Chip("${g.host().displayName} 총무")
                    if (g.status == GatheringStatus.COMPLETED) Chip("완료", ChipTone.DONE)
                }
                Text(
                    if (days != null) "${days}일 뒤 사라져요" else act.banner,
                    color = if (act.tone == Tone.TODO) JsColor.p700 else JsColor.ink2,
                    fontSize = 13.sp, fontWeight = if (act.tone == Tone.TODO) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
