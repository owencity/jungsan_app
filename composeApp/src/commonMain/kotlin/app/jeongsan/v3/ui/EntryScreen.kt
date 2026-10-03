package app.jeongsan.v3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.ui.JsShape
import app.jeongsan.ui.RetroSurface
import app.jeongsan.util.won
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.SELF_CHOICES
import app.jeongsan.v3.host
import app.jeongsan.v3.label
import app.jeongsan.v3.participantOfUser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * P1 참여 입구 — 웹 `EntryPage.tsx`. 링크를 누른 사람이 처음 보는 화면.
 *
 * 총무·술자리·차수를 보여주고 **그 자리에서 차수별 응답까지 고르게** 한다(P1+P2 합침, 2026-10-03).
 * 링크를 연 것만으로는 참여되지 않는다 — 아래 버튼을 눌러야 명단에 들어간다.
 *
 * 웹과 다른 점(플랫폼 차이): 앱은 링크(딥링크)로 열리면 로그인부터 거친 뒤 이 화면이 뜬다. 그래서
 * 웹의 "로그인 전 미리보기·카카오로 로그인하고 참여하기" 상태가 없다.
 */
@Composable
fun EntryScreen(
    devBar: @Composable () -> Unit,
    g: Gathering?,
    meUserId: Id,
    onJoin: (Map<Id, ResponseType>) -> Unit,
    onOpenRoom: () -> Unit,
    onHome: () -> Unit,
) {
    val draft = remember(g?.id, meUserId) { mutableStateMapOf<Id, ResponseType>() }

    @Composable
    fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
        V3Screen(devBar = devBar, top = { BackButton(onHome) }) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = JsColor.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(body, color = JsColor.ink2, fontSize = 13.5.sp, textAlign = TextAlign.Center)
                SubButton(action, onAction)
            }
        }
    }

    when {
        g == null -> return Notice("링크가 맞지 않아요", "총무에게 링크를 다시 받아주세요", "내 술자리로", onHome)
        // 이미 참여한 사람은 입구를 다시 거치지 않는다
        g.participantOfUser(meUserId) != null -> return Notice("이미 참여 중인 술자리예요", g.title, "정산방으로", onOpenRoom)
        g.status != GatheringStatus.OPEN -> return Notice("이미 정산된 술자리예요", "정산이 끝난 뒤에는 참여할 수 없어요", "내 술자리로", onHome)
    }
    g!!
    val host = g.host()
    val filled = g.rounds.all { draft[it.id] != null }
    val date = g.date.toLocalDateTime(TimeZone.currentSystemDefault())

    V3Screen(
        devBar = devBar,
        top = { BackButton(onHome) },
        bottom = {
            if (!filled) Text("모든 차수를 골라야 참여할 수 있어요", Modifier.fillMaxWidth(), color = JsColor.ink3, fontSize = 12.sp, textAlign = TextAlign.Center)
            CtaButton(if (g.rounds.isEmpty()) "참여하기" else "참여하고 응답 완료", enabled = filled) {
                onJoin(g.rounds.associate { it.id to draft.getValue(it.id) })
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${host.displayName}님이 정산어택에 초대했어요", color = JsColor.accentStrong, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(g.title, color = JsColor.ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("${date.monthNumber}월 ${date.dayOfMonth}일 · ${g.participants.size}명 참여 중", color = JsColor.ink2, fontSize = 13.sp)
        }

        RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(JsColor.p50).border(2.dp, JsColor.line), contentAlignment = Alignment.Center) {
                    HostCharacter(host.spoonCount, pixel = 2.4.dp)
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(host.displayName, color = JsColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("  총무", color = JsColor.ink3, fontSize = 12.sp)
                    }
                    SpoonChip(host.spoonCount)
                }
            }
        }

        if (g.rounds.isEmpty()) {
            Text("총무가 아직 금액을 넣는 중이에요. 먼저 참여해두면 금액이 들어올 때 알려드릴게요.", color = JsColor.ink2, fontSize = 14.sp, lineHeight = 20.sp)
        } else {
            Text("차수마다 하나만 고르면 참여 끝이에요.", color = JsColor.ink2, fontSize = 14.sp)
            if (g.rounds.size > 1) {
                Label("전 차수 똑같이")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in SELF_CHOICES) MiniButton(t.label) { for (r in g.rounds) draft[r.id] = t }
                }
            }
            for (r in g.rounds) {
                RetroSurface(Modifier.fillMaxWidth(), shadowOffset = JsShape.shadowOffsetSmall) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row {
                            Text(r.label, Modifier.weight(1f), color = JsColor.p700, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(won(r.total), color = JsColor.ink3, fontSize = 13.sp)
                        }
                        ResponseRow(draft[r.id]) { draft[r.id] = it }
                    }
                }
            }
        }
    }
}
