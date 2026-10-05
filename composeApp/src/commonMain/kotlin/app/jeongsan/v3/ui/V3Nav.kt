package app.jeongsan.v3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.jeongsan.domain.Id
import app.jeongsan.ui.JsColor
import app.jeongsan.v3.ActionKind
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.MockV3
import app.jeongsan.v3.isDebugBuild
import app.jeongsan.v3.api.V3Gateway
import androidx.savedstate.read
import app.jeongsan.v3.SettleResult
import app.jeongsan.v3.Target
import app.jeongsan.v3.V3Store
import app.jeongsan.v3.entryTarget
import app.jeongsan.v3.mockPreview
import app.jeongsan.v3.participantOfUser
import app.jeongsan.v3.roundsPaidBy
import app.jeongsan.v3.SHARE_BASE
import app.jeongsan.v3.rememberShareSheet
import app.jeongsan.v3.shareMessage
import app.jeongsan.v3.shareUrl

/**
 * v3 화면 경로와 이동 — 웹 `AppV3.tsx`의 라우터를 옮긴 것.
 *
 * **뒤로가기 규칙(웹과 같게):** 하위 화면(차수·정산하기·응답·보낼 돈)의 뒤로가기는 정산방, 정산방의
 * 뒤로가기는 내 술자리, 내 술자리의 뒤로가기는 로그인. Android 시스템 뒤로가기도 같은 곳으로 가도록
 * 화면 기록을 늘 `내 술자리 → 정산방 → 하위 화면` 모양으로 맞춘다 — 목록·알림에서 하위 화면으로
 * 바로 갈 때도 정산방을 한 칸 끼워 넣는다.
 */
object V3Routes {
    const val Home = "v3/home"
    const val Room = "v3/r/{id}"
    const val Round = "v3/r/{id}/round/{rid}"
    const val Settle = "v3/r/{id}/settle"
    const val Respond = "v3/r/{id}/respond"
    const val Pay = "v3/r/{id}/pay"
    /** A1 — 계좌는 사람 단위지만, 저장 뒤 왔던 정산방으로 돌아가도록 정산방 아래에 둔다 */
    const val Account = "v3/r/{id}/account"
    /** 내 술자리에서 여는 계좌 화면 — 저장하면 내 술자리로 */
    const val MyAccount = "v3/me/account"
    const val Notifications = "v3/notifications"
    /** 참여 입구(P1) — 공유 링크의 토큰으로 연다. 딥링크가 붙으면 이 경로로 들어온다 */
    const val Join = "v3/j/{token}"

    fun join(token: String) = "v3/j/$token"

    fun room(id: Id) = "v3/r/$id"
    /** rid가 null이면 새 차수 */
    fun round(id: Id, rid: Id?) = "v3/r/$id/round/${rid ?: "new"}"
    fun settle(id: Id) = "v3/r/$id/settle"
    fun respond(id: Id) = "v3/r/$id/respond"
    fun pay(id: Id) = "v3/r/$id/pay"
    fun account(id: Id) = "v3/r/$id/account"
}

private fun NavHostController.toHome() = navigate(V3Routes.Home) { popUpTo(V3Routes.Home) { inclusive = true } }

/** 기록을 `내 술자리 → 정산방`으로 정리하고 정산방을 연다 */
private fun NavHostController.toRoom(id: Id) = navigate(V3Routes.room(id)) { popUpTo(V3Routes.Home) }

/** 정산방을 한 칸 끼워 하위 화면을 연다 — 시스템 뒤로가기가 정산방으로 가게 */
private fun NavHostController.toSub(id: Id, route: String) {
    toRoom(id)
    navigate(route)
}

private fun NavHostController.toTarget(t: Target) = when (t) {
    Target.Home -> toHome()
    is Target.Room -> toRoom(t.roomId)
    is Target.Respond -> toSub(t.roomId, V3Routes.respond(t.roomId))
    is Target.Pay -> toSub(t.roomId, V3Routes.pay(t.roomId))
    is Target.Account -> toSub(t.roomId, V3Routes.account(t.roomId))
}

// navigation 2.9 부터 arguments 가 플랫폼 공통 SavedState 다 — Bundle.getString 대신 read { } 로 읽는다
private fun androidx.navigation.NavBackStackEntry.strArg(name: String): String? = arguments?.read { getStringOrNull(name) }
private fun androidx.navigation.NavBackStackEntry.idArg(name: String = "id"): Id? = strArg(name)?.toLongOrNull()

fun NavGraphBuilder.v3Graph(nav: NavHostController, store: V3Store, gateway: V3Gateway, onLeave: () -> Unit) {
    // 개발용 바 — 지금 보는 술자리(있으면)에서 각 사람의 역할을 같이 보여준다. 릴리스 빌드에는 없다
    val devBar: (Id?) -> @Composable () -> Unit = { roomId ->
        { if (isDebugBuild) {
            val s = store.state
            val room = roomId?.let { s.rooms[it] }
            DevBar(
                users = s.users.map { it.id to it.displayName },
                current = s.me.id,
                roleOf = { uid ->
                    val p = room?.participantOfUser(uid)
                    when {
                        room == null || p == null -> ""
                        room.hostUserId == uid -> "총무"
                        room.roundsPaidBy(p.id).isNotEmpty() -> "결제"
                        else -> "참여"
                    }
                },
                onPick = { uid ->
                    store.actAs(uid)
                    // 그 사람이 없는 술자리를 보고 있었다면 그 사람의 첫 화면으로
                    if (room != null && room.participantOfUser(uid) == null) nav.toHome()
                },
                links = s.rooms.values.filter { it.participantOfUser(s.me.id) == null }.map { it.title to it.shareToken },
                onLink = { token -> nav.navigate(V3Routes.join(token)) },
            )
        } }
    }

    composable(V3Routes.Home) {
        val s = store.state
        // L2 이름 확인 — 첫 로그인이면 내 술자리 대신 한 번
        if (s.me.needsName) {
            NameScreen(devBar = devBar(null), me = s.me, onBack = onLeave, onConfirm = { gateway.confirmName(it) })
            return@composable
        }
        HomeScreen(
            devBar = devBar(null),
            me = s.me,
            rooms = s.rooms.values,
            paySeen = { s.isPaySeen(it) },
            unread = s.myNotifications().count { !it.read },
            onBack = onLeave,
            // 참여자는 정산방보다 할 일 화면(응답하기·내 금액)을 먼저
            onOpen = { id -> nav.toTarget(entryTarget(s.rooms.getValue(id), s.me.id, s.isPaySeen(id))) },
            onCreate = {
                // 입력 없이 바로 만들고 1차 입력으로 — SCREENS.md §3.1
                val id = store.createGathering()
                nav.toSub(id, V3Routes.round(id, null))
            },
            onOpenAlerts = { nav.navigate(V3Routes.Notifications) },
            onEditAccount = { nav.navigate(V3Routes.MyAccount) },
        )
    }

    composable(V3Routes.Join) { entry ->
        val token = entry.strArg("token").orEmpty()
        val s = store.state
        val g = s.rooms.values.find { it.shareToken == token }
        EntryScreen(
            devBar = devBar(null),
            g = g,
            meUserId = s.me.id,
            // 첫 로그인이면 P1에서 이름 확인(L2)을 같이 받는다 — 확인한 이름으로 참여한다
            askName = s.me.needsName,
            onJoin = { answers, name ->
                // 실명 등록이 성공한 뒤에 참여한다 — 실패하면 그 문구를 돌려주고 참여하지 않는다(FC-014 §5)
                val err = if (name != null) gateway.confirmName(name) else null
                if (err == null) store.joinGathering(token, answers)?.let { nav.toRoom(it) }
                err
            },
            onOpenRoom = { g?.let { nav.toRoom(it.id) } },
            onHome = { nav.toHome() },
        )
    }

    composable(V3Routes.Notifications) {
        NotificationsScreen(
            devBar = devBar(null),
            items = store.state.myNotifications(),
            onBack = { nav.popBackStack() },
            onReadAll = { store.markAllRead() },
            onOpen = { n ->
                store.markRead(n.id)
                nav.toTarget(n.target)
            },
        )
    }

    composable(V3Routes.Room) { entry ->
        val id = entry.idArg()
        val s = store.state
        val share = rememberShareSheet()
        val g = id?.let { s.rooms[it] }
        if (g == null) return@composable Soon(devBar(null), "없는 술자리예요") { nav.toHome() }
        RoomScreen(
            devBar = devBar(g.id),
            g = g,
            meUserId = s.me.id,
            onBack = { nav.toHome() },
            onAction = { kind ->
                when (kind) {
                    ActionKind.GIVE_SPOON -> store.giveSpoon(g.id)
                    ActionKind.SETTLE -> nav.navigate(V3Routes.settle(g.id))
                    ActionKind.EDIT_FIRST_ROUND -> nav.navigate(V3Routes.round(g.id, null))
                    ActionKind.RESPOND, ActionKind.EDIT_RESPONSE -> nav.navigate(V3Routes.respond(g.id))
                    ActionKind.VIEW_PAY, ActionKind.RESEND -> nav.navigate(V3Routes.pay(g.id))
                    // 받을 돈 목록이 화면 안에 있다. 링크 공유·계좌 등록은 아직 화면이 없다(SCREENS.md §9 순서)
                    ActionKind.REGISTER_ACCOUNT -> nav.navigate(V3Routes.account(g.id))
                    ActionKind.SHARE -> share(shareMessage(g, shareUrl(SHARE_BASE, g.shareToken)))
                    // 받을 돈 목록이 화면 안에 있다
                    ActionKind.CONFIRM_INCOMING -> Unit
                }
            },
            onEditRound = { rid -> nav.navigate(V3Routes.round(g.id, rid)) },
            onAddRound = { nav.navigate(V3Routes.round(g.id, null)) },
            onSend = { store.sendMessage(g.id, it) },
            onConfirm = { store.confirmIncoming(g.id, it) },
            onNotReceived = { store.notReceived(g.id, it) },
            onExempt = { pid, rid, ex -> store.setExempt(g.id, pid, rid, ex) },
            onRemove = { pid -> store.removeParticipant(g.id, pid) },
            onShare = { share(shareMessage(g, shareUrl(SHARE_BASE, g.shareToken))) },
        )
    }

    composable(V3Routes.Round) { entry ->
        val id = entry.idArg()
        val ridArg = entry.strArg("rid")
        val s = store.state
        val g = id?.let { s.rooms[it] }
        val back = { id?.let { nav.toRoom(it) } ?: nav.toHome() }
        val round = g?.roundById(ridArg?.toLongOrNull())
        when {
            g == null -> Soon(devBar(null), "없는 술자리예요") { nav.toHome() }
            g.hostUserId != s.me.id -> Soon(devBar(g.id), "차수는 총무만 고칠 수 있어요", back)
            g.status != GatheringStatus.OPEN -> Soon(devBar(g.id), "정산한 뒤에는 차수를 고칠 수 없어요", back)
            ridArg != "new" && round == null -> Soon(devBar(g.id), "없는 차수예요", back)
            // 새 차수를 연달아 넣을 때 같은 경로라도 입력칸을 비우도록 차수 수로 새로 그린다
            else -> key(roundEditKey(round, g)) {
                RoundEditScreen(
                    devBar = devBar(g.id),
                    g = g,
                    round = round,
                    onBack = back,
                    onSave = { draft, andNext ->
                        store.saveRound(g.id, draft)
                        if (andNext) nav.toSub(g.id, V3Routes.round(g.id, null)) else nav.toRoom(g.id)
                    },
                    onDelete = round?.let { r -> { store.deleteRound(g.id, r.id); nav.toRoom(g.id) } },
                )
            }
        }
    }

    composable(V3Routes.Settle) { entry ->
        val s = store.state
        val g = entry.idArg()?.let { s.rooms[it] }
        when {
            g == null -> Soon(devBar(null), "없는 술자리예요") { nav.toHome() }
            g.hostUserId != s.me.id -> Soon(devBar(g.id), "정산은 총무만 할 수 있어요") { nav.toRoom(g.id) }
            g.status != GatheringStatus.OPEN -> Soon(devBar(g.id), "이미 정산한 술자리예요") { nav.toRoom(g.id) }
            g.rounds.isEmpty() -> Soon(devBar(g.id), "차수를 먼저 넣어주세요") { nav.toRoom(g.id) }
            g.participants.size < 2 -> Soon(devBar(g.id), "혼자서는 정산할 수 없어요. 링크를 먼저 보내주세요") { nav.toRoom(g.id) }
            else -> SettleScreen(
                devBar = devBar(g.id),
                g = g,
                preview = mockPreview(g),
                onBack = { nav.toRoom(g.id) },
                onRespondFor = { pid, rid, t -> store.respondAsHost(g.id, pid, rid, t) },
                onSettle = { rev -> store.settle(g.id, rev).also { if (it == SettleResult.OK) nav.toRoom(g.id) } },
            )
        }
    }

    composable(V3Routes.Respond) { entry ->
        val s = store.state
        val g = entry.idArg()?.let { s.rooms[it] }
        val mine = g?.participantOfUser(s.me.id)
        when {
            g == null || mine == null -> Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
            g.status != GatheringStatus.OPEN -> Soon(devBar(g.id), "정산된 뒤에는 응답을 고칠 수 없어요") { nav.toRoom(g.id) }
            g.rounds.isEmpty() -> Soon(devBar(g.id), "아직 차수가 없어요") { nav.toRoom(g.id) }
            else -> RespondScreen(
                devBar = devBar(g.id),
                g = g,
                meId = mine.id,
                onBack = { nav.toRoom(g.id) },
                onSubmit = { answers -> store.respond(g.id, answers); nav.toRoom(g.id) },
            )
        }
    }

    composable(V3Routes.MyAccount) {
        AccountScreen(
            devBar = devBar(null),
            me = store.state.me,
            onBack = { nav.popBackStack() },
            onSave = { p ->
                store.registerPayout(p)
                nav.popBackStack()
            },
        )
    }

    composable(V3Routes.Account) { entry ->
        val s = store.state
        val g = entry.idArg()?.let { s.rooms[it] }
        if (g == null || g.participantOfUser(s.me.id) == null) {
            Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
        } else {
            AccountScreen(
                devBar = devBar(g.id),
                me = s.me,
                onBack = { nav.toRoom(g.id) },
                onSave = { p ->
                    store.registerPayout(p)
                    nav.toRoom(g.id)
                },
            )
        }
    }

    composable(V3Routes.Pay) { entry ->
        val s = store.state
        val g = entry.idArg()?.let { s.rooms[it] }
        val mine = g?.participantOfUser(s.me.id)
        when {
            g == null || mine == null -> Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
            g.status == GatheringStatus.OPEN -> Soon(devBar(g.id), "아직 정산 전이에요") { nav.toRoom(g.id) }
            else -> PayScreen(
                devBar = devBar(g.id),
                g = g,
                meId = mine.id,
                onBack = { nav.toRoom(g.id) },
                onSeen = { store.markPaySeen(g.id) },
                onSent = { store.markSent(g.id, it) },
            )
        }
    }
}

/** 갈 수 없는 화면 안내 — 웹 `Soon` */
@Composable
private fun Soon(devBar: @Composable () -> Unit, title: String, onBack: () -> Unit) {
    V3Screen(devBar = devBar, top = { BackButton(onBack) }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, color = JsColor.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            SubButton("돌아가기", onBack)
        }
    }
}
