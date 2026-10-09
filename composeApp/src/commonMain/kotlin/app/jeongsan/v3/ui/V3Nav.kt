package app.jeongsan.v3.ui

import app.jeongsan.v3.api.entryChoiceLabel
import app.jeongsan.v3.api.toEntryRooms
import app.jeongsan.v3.api.ServerJoinPreview
import app.jeongsan.v3.api.Settled
import app.jeongsan.v3.api.Made
import app.jeongsan.v3.SettlePreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import app.jeongsan.v3.LaunchOptions
import app.jeongsan.v3.api.V3Gateway
import androidx.savedstate.read
import app.jeongsan.v3.SettleResult
import app.jeongsan.v3.Target
import app.jeongsan.v3.V3Store
import app.jeongsan.v3.entryTarget
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

/**
 * 서버에서 읽기(API 모드) — 화면에 들어올 때마다 다시 읽는다. 이미 스토어에 있으면 먼저 그대로 그리고 뒤에서 새로 고친다.
 * 돌려주는 값이 false 인 동안은 "아직 못 읽음" — 스토어에 없는 정산방이면 "없는 술자리"가 아니라 "불러오는 중"을 띄운다.
 * 목데이터 모드는 읽을 것이 없어 처음부터 true.
 */
@Composable
private fun rememberLoaded(gateway: V3Gateway, key: Any?, load: suspend () -> String?): Boolean {
    var done by remember(key) { mutableStateOf(!gateway.isApiMode) }
    LaunchedEffect(key) {
        load()?.let(V3Toast::show)
        done = true
    }
    return done
}

/** gateway 결과가 문구면 토스트로 띄운다 */
private fun CoroutineScope.fire(block: suspend () -> String?) = launch { block()?.let(V3Toast::show) }

fun NavGraphBuilder.v3Graph(nav: NavHostController, store: V3Store, gateway: V3Gateway, onLeave: () -> Unit) {
    // 개발용 바 — 지금 보는 술자리(있으면)에서 각 사람의 역할을 같이 보여준다. 릴리스 빌드·API 모드에는 없다
    val devBar: (Id?) -> @Composable () -> Unit = { roomId ->
        // 스크린샷 모드(스토어 사진)에는 개발용 바를 그리지 않는다
        { if (isDebugBuild && LaunchOptions.screenshot == null && !gateway.isApiMode) {
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

    /** 주소로 바로 들어와 아직 서버에서 못 읽은 정산방이면 "불러오는 중", 다 읽었는데 없으면 "없는 술자리" */
    @Composable
    fun Missing(loaded: Boolean) = Soon(devBar(null), if (loaded) "없는 술자리예요" else "불러오는 중이에요") { nav.toHome() }

    composable(V3Routes.Home) {
        val s = store.state
        val scope = rememberCoroutineScope()
        rememberLoaded(gateway, Unit) { gateway.loadMine() }
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
                scope.launch {
                    when (val r = gateway.createGathering()) {
                        is Made.Ok -> nav.toSub(r.id, V3Routes.round(r.id, null))
                        is Made.Err -> V3Toast.show(r.message)
                    }
                }
            },
            onOpenAlerts = { nav.navigate(V3Routes.Notifications) },
            onEditAccount = { nav.navigate(V3Routes.MyAccount) },
        )
    }

    composable(V3Routes.Join) { entry ->
        val token = entry.strArg("token").orEmpty()
        val s = store.state
        // 이미 들어가 있는 정산방이 있으면 입구 대신 그리로 안내한다(EntryScreen 이 "이미 참여 중"을 띄운다)
        val mineHere = s.rooms.values.find { it.shareToken == token && it.participantOfUser(s.me.id) != null }
        // API 모드: 로그인 전에도 보이는 서버 미리보기. 총무가 둘 이상이면 참여할 차수 묶음을 고른다(FC-015)
        val preview by produceState<Result<ServerJoinPreview>?>(null, token) { value = gateway.joinPreview(token) }
        var picked by remember(token) { mutableStateOf<Id?>(null) }
        val entryRooms = if (gateway.isApiMode && mineHere == null) preview?.getOrNull()?.toEntryRooms(token) else null
        if (gateway.isApiMode && mineHere == null && preview == null) {
            return@composable Soon(devBar(null), "링크를 확인하는 중이에요") { nav.toHome() }
        }
        val g = when {
            !gateway.isApiMode -> s.rooms.values.find { it.shareToken == token }
            mineHere != null -> mineHere
            else -> entryRooms?.find { it.id == picked } ?: entryRooms?.firstOrNull()
        }
        EntryScreen(
            devBar = devBar(null),
            g = g,
            meUserId = s.me.id,
            // 첫 로그인이면 P1에서 이름 확인(L2)을 같이 받는다 — 확인한 이름으로 참여한다
            askName = s.me.needsName,
            participantCount = preview?.getOrNull()?.participantCount?.takeIf { mineHere == null },
            choices = entryRooms.orEmpty().map { it.id to it.entryChoiceLabel() },
            onPick = { picked = it },
            onJoin = { answers, name ->
                // 실명 등록이 성공한 뒤에 참여한다 — 실패하면 그 문구를 돌려주고 참여하지 않는다(FC-014 §5)
                val err = if (name != null) gateway.confirmName(name) else null
                if (err != null) err
                else when (val r = gateway.join(token, g?.id, answers)) {
                    is Made.Ok -> { nav.toRoom(r.id); null }
                    is Made.Err -> r.message
                }
            },
            onOpenRoom = { g?.let { nav.toRoom(it.id) } },
            onHome = { nav.toHome() },
        )
    }

    composable(V3Routes.Notifications) {
        val scope = rememberCoroutineScope()
        rememberLoaded(gateway, Unit) { gateway.loadMine() }
        NotificationsScreen(
            devBar = devBar(null),
            items = store.state.myNotifications(),
            onBack = { nav.popBackStack() },
            onReadAll = { scope.launch { gateway.markAllRead() } },
            onOpen = { n ->
                scope.launch { gateway.markRead(n.id) }
                nav.toTarget(n.target)
            },
        )
    }

    composable(V3Routes.Room) { entry ->
        val id = entry.idArg()
        val s = store.state
        val scope = rememberCoroutineScope()
        val share = rememberShareSheet()
        val loaded = rememberLoaded(gateway, id) { id?.let { gateway.loadRoom(it) } }
        val g = id?.let { s.rooms[it] } ?: return@composable Missing(loaded)
        // 같은 술자리에 내가 총무인 정산방이 이미 있으면(다음 차를 이미 맡음) 새로 만들지 않고 그리로
        val myNext = s.rooms.values.find {
            it.id != g.id && it.hostUserId == s.me.id && it.gatheringId != null && it.gatheringId == g.gatheringId
        }
        RoomScreen(
            devBar = devBar(g.id),
            g = g,
            meUserId = s.me.id,
            onBack = { nav.toHome() },
            onAction = { kind ->
                when (kind) {
                    ActionKind.GIVE_SPOON -> gateway.giveSpoon(g.id)?.let(V3Toast::show)
                    ActionKind.SETTLE -> nav.navigate(V3Routes.settle(g.id))
                    ActionKind.EDIT_FIRST_ROUND -> nav.navigate(V3Routes.round(g.id, null))
                    ActionKind.RESPOND, ActionKind.EDIT_RESPONSE -> nav.navigate(V3Routes.respond(g.id))
                    ActionKind.VIEW_PAY, ActionKind.RESEND -> nav.navigate(V3Routes.pay(g.id))
                    ActionKind.REGISTER_ACCOUNT -> nav.navigate(V3Routes.account(g.id))
                    ActionKind.SHARE -> share(shareMessage(g, shareUrl(SHARE_BASE, g.shareToken)))
                    // 받을 돈 목록이 화면 안에 있다
                    ActionKind.CONFIRM_INCOMING -> Unit
                }
            },
            onEditRound = { rid -> nav.navigate(V3Routes.round(g.id, rid)) },
            onAddRound = { nav.navigate(V3Routes.round(g.id, null)) },
            onSend = { text -> scope.fire { gateway.sendMessage(g.id, text) } },
            onConfirm = { tid -> scope.fire { gateway.confirmIncoming(g.id, tid) } },
            onNotReceived = { tid -> scope.fire { gateway.notReceived(g.id, tid) } },
            // 면제는 서버 API 보류(SETTLEMENT_UNITS §4.2) — 화면에서도 꺼져 있다(Features.SHOW_EXEMPT)
            onExempt = { pid, rid, ex -> store.setExempt(g.id, pid, rid, ex) },
            onRemove = { pid -> scope.fire { gateway.removeParticipant(g.id, pid) } },
            onShare = { share(shareMessage(g, shareUrl(SHARE_BASE, g.shareToken))) },
            onStartNext = { ids ->
                // 같은 술자리 안에 내가 총무인 정산 단위 → 바로 다음 차 금액 입력(FC-015)
                scope.launch {
                    when (val r = gateway.createUnit(g.id, ids)) {
                        is Made.Ok -> { V3Toast.show("다음 차 총무가 됐어요. 금액을 넣어주세요"); nav.toSub(r.id, V3Routes.round(r.id, null)) }
                        is Made.Err -> V3Toast.show(r.message)
                    }
                }
            },
            onOpenMyNext = myNext?.let { n -> { nav.toRoom(n.id) } },
        )
    }

    composable(V3Routes.Round) { entry ->
        val id = entry.idArg()
        val ridArg = entry.strArg("rid")
        val s = store.state
        val scope = rememberCoroutineScope()
        val loaded = rememberLoaded(gateway, id) { id?.let { gateway.loadRoom(it) } }
        val g = id?.let { s.rooms[it] }
        val back = { id?.let { nav.toRoom(it) } ?: nav.toHome() }
        val round = g?.roundById(ridArg?.toLongOrNull())
        when {
            g == null -> Missing(loaded)
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
                    onSave = { draft, andNext, mine ->
                        scope.launch {
                            when (val r = gateway.saveRound(g.id, draft, mine)) {
                                is Made.Ok -> if (andNext) nav.toSub(g.id, V3Routes.round(g.id, null)) else nav.toRoom(g.id)
                                is Made.Err -> V3Toast.show(r.message)
                            }
                        }
                    },
                    onDelete = round?.let { r ->
                        { scope.launch { gateway.deleteRound(g.id, r.id)?.let(V3Toast::show) ?: nav.toRoom(g.id) } }
                    },
                )
            }
        }
    }

    composable(V3Routes.Settle) { entry ->
        val s = store.state
        val scope = rememberCoroutineScope()
        val id = entry.idArg()
        val loaded = rememberLoaded(gateway, id) { id?.let { gateway.loadRoom(it) } }
        val g = id?.let { s.rooms[it] }
        // 미리보기는 지금 입력 버전의 것만 쓴다 — 응답을 대신 넣으면 버전이 바뀌어 다시 받는다(옛 금액으로 정산하지 않게)
        val preview by produceState<Result<SettlePreview>?>(null, g?.id, g?.inputRevision) {
            value = null
            value = g?.let { gateway.preview(it.id) }
        }
        when {
            g == null -> Missing(loaded)
            g.hostUserId != s.me.id -> Soon(devBar(g.id), "정산은 총무만 할 수 있어요") { nav.toRoom(g.id) }
            g.status != GatheringStatus.OPEN -> Soon(devBar(g.id), "이미 정산한 술자리예요") { nav.toRoom(g.id) }
            g.rounds.isEmpty() -> Soon(devBar(g.id), "차수를 먼저 넣어주세요") { nav.toRoom(g.id) }
            g.participants.size < 2 -> Soon(devBar(g.id), "혼자서는 정산할 수 없어요. 링크를 먼저 보내주세요") { nav.toRoom(g.id) }
            preview == null -> Soon(devBar(g.id), "금액을 계산하는 중이에요") { nav.toRoom(g.id) }
            preview!!.isFailure -> Soon(devBar(g.id), gateway.previewError(preview!!.exceptionOrNull()!!)) { nav.toRoom(g.id) }
            else -> SettleScreen(
                devBar = devBar(g.id),
                g = g,
                preview = preview!!.getOrThrow(),
                onBack = { nav.toRoom(g.id) },
                onRespondFor = { pid, rid, t -> scope.fire { gateway.respondAsHost(g.id, pid, rid, t) } },
                onSettle = { p ->
                    gateway.settle(g.id, p).also {
                        if (it == Settled.Done(SettleResult.OK)) nav.toRoom(g.id)
                        // 그 사이 바뀌었으면 정산방을 다시 읽는다 — 입력 버전이 바뀌어 새 미리보기를 받는다
                        if (it == Settled.Done(SettleResult.STALE)) gateway.loadRoom(g.id)
                    }
                },
            )
        }
    }

    composable(V3Routes.Respond) { entry ->
        val s = store.state
        val scope = rememberCoroutineScope()
        val id = entry.idArg()
        val loaded = rememberLoaded(gateway, id) { id?.let { gateway.loadRoom(it) } }
        val g = id?.let { s.rooms[it] }
        val mine = g?.participantOfUser(s.me.id)
        when {
            g == null -> Missing(loaded)
            mine == null -> Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
            g.status != GatheringStatus.OPEN -> Soon(devBar(g.id), "정산된 뒤에는 응답을 고칠 수 없어요") { nav.toRoom(g.id) }
            g.rounds.isEmpty() -> Soon(devBar(g.id), "아직 차수가 없어요") { nav.toRoom(g.id) }
            else -> RespondScreen(
                devBar = devBar(g.id),
                g = g,
                meId = mine.id,
                onBack = { nav.toRoom(g.id) },
                onSubmit = { answers -> scope.launch { gateway.respond(g.id, answers)?.let(V3Toast::show) ?: nav.toRoom(g.id) } },
            )
        }
    }

    composable(V3Routes.MyAccount) {
        val scope = rememberCoroutineScope()
        AccountScreen(
            devBar = devBar(null),
            me = store.state.me,
            onBack = { nav.popBackStack() },
            onSave = { p ->
                scope.launch {
                    gateway.registerPayout(p)?.let(V3Toast::show) ?: run { V3Toast.show("받을 계좌를 저장했어요"); nav.popBackStack() }
                }
            },
        )
    }

    composable(V3Routes.Account) { entry ->
        val s = store.state
        val scope = rememberCoroutineScope()
        val g = entry.idArg()?.let { s.rooms[it] }
        if (g == null || g.participantOfUser(s.me.id) == null) {
            Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
        } else {
            AccountScreen(
                devBar = devBar(g.id),
                me = s.me,
                onBack = { nav.toRoom(g.id) },
                onSave = { p -> scope.launch { gateway.registerPayout(p)?.let(V3Toast::show) ?: nav.toRoom(g.id) } },
            )
        }
    }

    composable(V3Routes.Pay) { entry ->
        val s = store.state
        val scope = rememberCoroutineScope()
        val id = entry.idArg()
        val loaded = rememberLoaded(gateway, id) { id?.let { gateway.loadRoom(it) } }
        val g = id?.let { s.rooms[it] }
        val mine = g?.participantOfUser(s.me.id)
        when {
            g == null -> Missing(loaded)
            mine == null -> Soon(devBar(null), "이 술자리에 참여하지 않았어요") { nav.toHome() }
            g.status == GatheringStatus.OPEN -> Soon(devBar(g.id), "아직 정산 전이에요") { nav.toRoom(g.id) }
            else -> PayScreen(
                devBar = devBar(g.id),
                g = g,
                meId = mine.id,
                onBack = { nav.toRoom(g.id) },
                onSeen = { scope.launch { gateway.markPaySeen(g.id) } },
                onSent = { tid -> scope.fire { gateway.markSent(g.id, tid) } },
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
