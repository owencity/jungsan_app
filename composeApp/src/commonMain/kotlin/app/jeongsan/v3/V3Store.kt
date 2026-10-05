package app.jeongsan.v3

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.jeongsan.domain.Id
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random

/**
 * v3 목데이터 저장소 — 웹 `v3/store.ts`(zustand)를 옮긴 것. 같은 동작·같은 거부 조건·같은 타임라인 문구.
 *
 * 목업이라도 **누른 게 남아야** 흐름을 끝까지 걸어볼 수 있다. 백엔드 v3 API가 붙으면 각 동작이
 * API 호출로 바뀌고, 상태 전이는 서버가 결정한다(여기 전이 규칙은 `DOMAIN_DB_DESIGN_V2.md` §3 흉내).
 *
 * 상태는 불변 [V3State] 하나로 들고 통째로 바꾼다 — Compose가 `state`를 읽는 화면만 다시 그리고,
 * 테스트는 Compose 없이 동작 → 결과 상태만 본다.
 */
data class V3State(
    val me: User,
    val rooms: Map<Id, Gathering>,
    /** 모든 사람의 알림 — 화면은 지금 보는 사람 것만 거른다 */
    val notifications: List<AppNotification>,
    /** 정산금액(P3)을 열어본 `방:사람` — [정산금액 확인] 뱃지를 떼는 기준 */
    val paySeen: Set<String>,
    /** 목데이터의 사람들 — 보는 사람 전환 때 여기서 꺼낸다(등록한 계좌가 전환 뒤에도 남게) */
    val users: List<User> = MockV3.USERS,
) {
    fun isPaySeen(roomId: Id) = seenKey(roomId, me.id) in paySeen
    fun myNotifications() = notifications.filter { it.userId == me.id }

    companion object {
        fun initial() = V3State(
            me = MockV3.ME,
            rooms = MockV3.rooms().associateBy { it.id },
            notifications = MockV3.notifications(),
            paySeen = emptySet(),
        )

        /** API 모드 — 목데이터 없이 빈 상태로 시작한다(실제 사용자에게 가짜 술자리가 보이면 안 된다). 웹 store 와 같다 */
        fun empty() = V3State(
            me = User(id = 0, displayName = "", spoonCount = 0),
            rooms = emptyMap(),
            notifications = emptyList(),
            paySeen = emptySet(),
            users = emptyList(),
        )
    }
}

fun seenKey(roomId: Id, userId: Id) = "$roomId:$userId"

enum class SettleResult { OK, STALE, DENIED }

class V3Store(
    initial: V3State = V3State.initial(),
    private val clock: () -> Instant = { Clock.System.now() },
) {
    var state by mutableStateOf(initial)
        private set

    private val me get() = state.me

    // ── 내부 도우미 ──

    private fun Gathering.push(type: TimelineType, body: String, author: Id? = null): Gathering {
        val id = (timeline.maxOfOrNull { it.id } ?: 0) + 1
        return copy(timeline = timeline + TimelineEntry(id, type, body, clock(), author))
    }

    /** 모든 송금이 확인되면 완료. 참여자 2명 이상이면 총무 기본 스푼 1 — §5.3 */
    private fun Gathering.completeIfDone(): Gathering {
        if (status != GatheringStatus.SETTLING || !transfers.all { it.status == TransferStatus.CONFIRMED }) return this
        val hostId = host().id
        val bonus = if (participants.size >= 2) 1 else 0
        return copy(
            status = GatheringStatus.COMPLETED,
            completedAt = clock(),
            participants = participants.map { if (it.id == hostId) it.copy(spoonCount = it.spoonCount + bonus) else it },
        ).push(TimelineType.SYSTEM, "모두 입금 완료! 🎉")
    }

    /** 술자리를 바꾸고, 바뀐 만큼 알림을 쌓는다(행동한 본인은 빼고) — 서버의 이벤트 → 알림 흉내 */
    private fun commit(prev: Gathering, next: Gathering) {
        if (next == prev) return
        val s = state
        val startId = (s.notifications.maxOfOrNull { it.id } ?: 0) + 1
        val fresh = notificationsFor(prev, next)
            .filter { it.userId != s.me.id }
            .mapIndexed { i, n -> AppNotification(startId + i, n.userId, n.roomId, n.title, n.body, n.target, clock(), read = false) }
        state = s.copy(rooms = s.rooms + (next.id to next), notifications = fresh + s.notifications)
    }

    /** 내가 참여한 술자리에만 동작한다. fn은 (술자리, 나의 참여자 id) → 바뀐 술자리 */
    private fun update(roomId: Id, fn: (Gathering, Id) -> Gathering) {
        val g = state.rooms[roomId] ?: return
        val mine = g.participantOfUser(me.id) ?: return
        commit(g, fn(g, mine.id))
    }

    // ── 정산방 ──

    fun sendMessage(roomId: Id, text: String) =
        update(roomId) { g, meId -> g.push(TimelineType.MESSAGE, text.take(500), meId) }

    fun giveSpoon(roomId: Id) = update(roomId) { g, meId ->
        val host = g.host()
        if (meId in g.spoonGivers || host.id == meId || g.status == GatheringStatus.OPEN) return@update g
        g.copy(
            spoonGivers = g.spoonGivers + meId,
            participants = g.participants.map { if (it.id == host.id) it.copy(spoonCount = it.spoonCount + 1) else it },
        ).push(TimelineType.SPOON, "${g.nameOf(meId)}님이 총무에게 한 스푼 줬어요", meId)
    }

    fun markSent(roomId: Id, transferId: Id) = update(roomId) { g, meId ->
        val t = g.transfers.find { it.id == transferId }
        if (t == null || t.fromParticipantId != meId || t.status != TransferStatus.WAITING) return@update g
        g.copy(transfers = g.transfers.map { if (it.id == transferId) it.copy(status = TransferStatus.SENT, sentAt = clock()) else it })
            .push(TimelineType.SYSTEM, "${g.nameOf(meId)}님이 보냈어요")
    }

    fun confirmIncoming(roomId: Id, transferId: Id) = update(roomId) { g, meId ->
        val t = g.transfers.find { it.id == transferId }
        if (t == null || t.toParticipantId != meId || t.status == TransferStatus.CONFIRMED) return@update g
        g.copy(transfers = g.transfers.map { if (it.id == transferId) it.copy(status = TransferStatus.CONFIRMED, confirmedAt = clock()) else it })
            .push(TimelineType.SYSTEM, "${g.nameOf(meId)}님이 ${g.nameOf(t.fromParticipantId)}님 입금을 확인했어요")
            .completeIfDone()
    }

    fun notReceived(roomId: Id, transferId: Id) = update(roomId) { g, meId ->
        val t = g.transfers.find { it.id == transferId }
        if (t == null || t.toParticipantId != meId || t.status != TransferStatus.SENT) return@update g
        g.copy(transfers = g.transfers.map { if (it.id == transferId) it.copy(status = TransferStatus.WAITING, notReceivedAt = clock()) else it })
            .push(TimelineType.SYSTEM, "${g.nameOf(meId)}님이 아직 ${g.nameOf(t.fromParticipantId)}님 입금을 확인 못 했어요")
    }

    // ── 차수 (총무만, 정산 전에만 — 정산하기 이후 계산 입력은 고정이다, REQUIREMENTS 불변식 5) ──

    /** 새 차수면 만든 차수의 id, 거부되거나 기존 차수 수정이면 null */
    fun saveRound(roomId: Id, draft: RoundDraft): Id? {
        var created: Id? = null
        update(roomId) { g, _ ->
            if (g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
            val exists = draft.id != null && g.rounds.any { it.id == draft.id }
            val rounds = if (exists) {
                g.rounds.map {
                    if (it.id == draft.id) it.copy(total = draft.total, drinks = draft.drinks, payerParticipantId = draft.payerParticipantId) else it
                }
            } else {
                val newId = (g.rounds.maxOfOrNull { it.id } ?: 0) + 1
                created = newId
                relabel(g.rounds + Round(newId, g.rounds.size + 1, "", draft.total, draft.payerParticipantId, draft.drinks))
            }
            val label = rounds.first { it.id == (if (exists) draft.id else created) }.label
            g.copy(rounds = rounds, inputRevision = g.inputRevision + 1)
                .push(TimelineType.SYSTEM, "$label ${app.jeongsan.util.won(draft.total)}을 ${if (exists) "고쳤어요" else "넣었어요"}")
        }
        return created
    }

    fun deleteRound(roomId: Id, roundId: Id) = update(roomId) { g, _ ->
        val target = g.rounds.find { it.id == roundId }
        if (target == null || g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
        g.copy(
            rounds = relabel(g.rounds.filter { it.id != roundId }),
            responses = g.responses.filter { it.roundId != roundId },
            inputRevision = g.inputRevision + 1,
        ).push(TimelineType.SYSTEM, "${target.label}를 지웠어요")
    }

    /** 입력 없이 새 술자리를 만들고 id를 돌려준다. 만든 사람이 총무이자 첫 참여자다 */
    fun createGathering(): Id {
        val s = state
        val all = s.rooms.values
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val pid = (all.flatMap { g -> g.participants.map { it.id } }.maxOrNull() ?: 0) + 1
        val now = clock()
        val g = Gathering(
            id = id, title = autoTitle(now), date = now, hostUserId = s.me.id, status = GatheringStatus.OPEN,
            // 목데이터용 링크 토큰. 실제로는 서버가 발급한다
            shareToken = List(5) { "abcdefghijkmnpqrstuvwxyz23456789".random(Random) }.joinToString(""),
            inputRevision = 0,
            participants = listOf(Participant(pid, s.me.id, s.me.displayName, s.me.spoonCount, s.me.payout, s.me.nickname)),
            rounds = emptyList(), responses = emptyList(), transfers = emptyList(),
            timeline = listOf(TimelineEntry(1, TimelineType.SYSTEM, "${s.me.displayName}님이 술자리를 만들었어요", now)),
            spoonGivers = emptyList(),
        )
        state = s.copy(rooms = s.rooms + (id to g))
        return id
    }

    // ── 응답 ──

    /** 내 응답 저장(P2). 총무가 면제로 지정한 칸은 건너뛴다 */
    fun respond(roomId: Id, answers: Map<Id, ResponseType>) = update(roomId) { g, meId ->
        if (g.status != GatheringStatus.OPEN) return@update g
        val first = !g.hasResponded(meId)
        var responses = g.responses
        for ((roundId, type) in answers) {
            // 참여자는 면제를 고를 수 없고, 총무가 면제로 정한 칸은 못 바꾼다
            if (type == ResponseType.EXEMPT || g.responseOf(meId, roundId).isLockedByHost()) continue
            if (g.rounds.none { it.id == roundId }) continue
            responses = responses.setResponse(RoundResponse(meId, roundId, type, ResponseSource.SELF))
        }
        g.copy(responses = responses, inputRevision = g.inputRevision + 1)
            .push(TimelineType.SYSTEM, "${g.nameOf(meId)}님이 ${if (first) "응답했어요" else "응답을 고쳤어요"}")
    }

    /** 총무가 R3에서 미응답자 응답을 대신 넣는다 */
    fun respondAsHost(roomId: Id, participantId: Id, roundId: Id, type: ResponseType) = update(roomId) { g, _ ->
        if (g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
        if (g.participants.none { it.id == participantId } || g.rounds.none { it.id == roundId }) return@update g
        g.copy(
            responses = g.responses.setResponse(RoundResponse(participantId, roundId, type, ResponseSource.HOST)),
            inputRevision = g.inputRevision + 1,
        )
    }

    /**
     * 정산하기(R3). 미리보기 때의 `inputRevision`을 같이 보낸다 — 그 사이 입력이 바뀌었으면
     * 서버가 409로 거절하는 것을 흉내 내 STALE을 돌려준다.
     */
    fun settle(roomId: Id, inputRevision: Int): SettleResult {
        val g = state.rooms[roomId] ?: return SettleResult.DENIED
        if (g.hostUserId != me.id || g.status != GatheringStatus.OPEN || g.rounds.isEmpty() || g.participants.size < 2) {
            return SettleResult.DENIED
        }
        if (g.inputRevision != inputRevision) return SettleResult.STALE

        val preview = mockPreview(g)
        val autoNames = preview.lines.filter { it.auto }.map { g.nameOf(it.participantId) }
        val hostName = g.host().displayName
        val body = if (autoNames.isNotEmpty()) {
            "${hostName}님이 정산했어요 · ${autoNames.joinToString("·")}님은 응답이 없어 전 차수 참석·알코올로 계산됐어요"
        } else {
            "${hostName}님이 정산했어요"
        }
        val settled = g.copy(
            status = GatheringStatus.SETTLING,
            responses = withAutoResponses(g),
            transfers = preview.transfers.mapIndexed { i, t ->
                Transfer(i + 1L, t.fromParticipantId, t.toParticipantId, t.amount, TransferStatus.WAITING, basis = t.basis)
            },
        )
        // 보낼 돈이 하나도 없으면(총무 혼자 다 냈고 나머지가 모두 면제 등) 바로 완료
        commit(g, settled.push(TimelineType.SYSTEM, body).completeIfDone())
        return SettleResult.OK
    }

    /**
     * 링크로 들어와 참여하고 응답까지 한 번에(P1). 링크를 연 것만으로는 참여되지 않는다 — 이 동작을
     * 해야 명단에 들어간다. 참여된 술자리 id, 참여할 수 없으면(없는 링크·정산 뒤) null.
     * 이미 참여 중이면 아무것도 바꾸지 않고 id만 돌려준다.
     */
    fun joinGathering(shareToken: String, answers: Map<Id, ResponseType>): Id? {
        val s = state
        val g = s.rooms.values.find { it.shareToken == shareToken } ?: return null
        if (g.participantOfUser(s.me.id) != null) return g.id
        if (g.status != GatheringStatus.OPEN) return null

        val pid = (s.rooms.values.flatMap { r -> r.participants.map { it.id } }.maxOrNull() ?: 0) + 1
        var next = g.copy(participants = g.participants + Participant(pid, s.me.id, s.me.displayName, s.me.spoonCount, s.me.payout, s.me.nickname))
            .push(TimelineType.SYSTEM, "${s.me.displayName}님이 들어왔어요")
        var responses = next.responses
        for ((roundId, type) in answers) {
            // 새로 온 사람은 면제를 고를 수 없다 — 면제는 총무만 정한다
            if (type == ResponseType.EXEMPT || g.rounds.none { it.id == roundId }) continue
            responses = responses.setResponse(RoundResponse(pid, roundId, type, ResponseSource.SELF))
        }
        if (responses != next.responses) {
            next = next.copy(responses = responses).push(TimelineType.SYSTEM, "${s.me.displayName}님이 응답했어요")
        }
        // 명단이 바뀌면 미리보기 결과도 바뀐다 — 총무가 보던 미리보기로는 정산할 수 없게
        commit(g, next.copy(inputRevision = next.inputRevision + 1))
        return g.id
    }

    /**
     * 총무가 한 사람의 한 차수를 면제하거나 해제한다(R4). 면제는 `EXEMPT`·`HOST`로 잠기고, 해제하면 그 칸은
     * **빈칸(응답 전)**으로 돌아간다 — 참여자가 다시 고르고, 안 고르면 정산 때 자동응답(flow-changes FC-010).
     */
    fun setExempt(roomId: Id, participantId: Id, roundId: Id, exempt: Boolean) = update(roomId) { g, _ ->
        if (g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
        val round = g.rounds.find { it.id == roundId } ?: return@update g
        if (g.participants.none { it.id == participantId }) return@update g
        if (exempt == (g.responseOf(participantId, roundId)?.type == ResponseType.EXEMPT)) return@update g
        val responses = if (exempt) {
            g.responses.setResponse(RoundResponse(participantId, roundId, ResponseType.EXEMPT, ResponseSource.HOST))
        } else {
            g.responses.filterNot { it.participantId == participantId && it.roundId == roundId }
        }
        val who = g.nameOf(participantId)
        g.copy(responses = responses, inputRevision = g.inputRevision + 1)
            .push(TimelineType.SYSTEM, if (exempt) "${who}님 ${round.label}를 면제했어요 🎁" else "${who}님 ${round.label} 면제를 풀었어요")
    }

    /**
     * 총무가 정산 전 한 사람을 내보낸다(R4). 결제자는 내보낼 수 없다 — 그 차수의 돈을 받을 사람이
     * 사라지기 때문이다. 거부되면 이유 문구, 성공하면 null.
     */
    fun removeParticipant(roomId: Id, participantId: Id): String? {
        val g = state.rooms[roomId]
        if (g == null || g.hostUserId != me.id) return "총무만 내보낼 수 있어요"
        removeBlockedReason(g, participantId)?.let { return it }
        val next = g.copy(
            participants = g.participants.filterNot { it.id == participantId },
            responses = g.responses.filterNot { it.participantId == participantId },
            inputRevision = g.inputRevision + 1,
        ).push(TimelineType.SYSTEM, "${g.nameOf(participantId)}님이 빠졌어요")
        commit(g, next)
        return null
    }

    // ── 알림 · 뱃지 · 개발용 ──

    fun markRead(notificationId: Id) {
        state = state.copy(notifications = state.notifications.map { if (it.id == notificationId) it.copy(read = true) else it })
    }

    fun markAllRead() {
        val meId = me.id
        state = state.copy(notifications = state.notifications.map { if (it.userId == meId) it.copy(read = true) else it })
    }

    fun markPaySeen(roomId: Id) {
        val key = seenKey(roomId, me.id)
        if (key !in state.paySeen) state = state.copy(paySeen = state.paySeen + key)
    }

    /** 개발용 — 다른 사람 시점으로 보기 */
    fun actAs(userId: Id) {
        state.users.find { it.id == userId }?.let { state = state.copy(me = it) }
    }

    /**
     * 표시 이름 확인·변경(L2). 사람 단위라 내가 들어간 **진행 중인** 술자리의 내 이름도 같이 바꾼다
     * (완료된 술자리는 그대로 — 끝난 기록이다). 확인하면 `needsName`이 풀린다.
     */
    fun confirmName(name: String) {
        val displayName = name.trim()
        if (validateName(displayName).isNotEmpty()) return
        val me = state.me.copy(displayName = displayName, needsName = false)
        // 서버는 참여자 이름을 따로 저장하지 않고 users.display_name을 읽는다 — 완료된 방까지 모두 바뀐다
        val rooms = state.rooms.mapValues { (_, g) ->
            g.copy(participants = g.participants.map { if (it.userId == me.id) it.copy(displayName = displayName) else it })
        }
        state = state.copy(me = me, users = state.users.map { if (it.id == me.id) me else it }, rooms = rooms)
    }

    /** 서버가 준 내 정보로 바꾼다(API 모드, [app.jeongsan.v3.api.V3Gateway]) */
    fun setMe(me: User) {
        val users = if (state.users.any { it.id == me.id }) state.users.map { if (it.id == me.id) me else it } else state.users + me
        state = state.copy(me = me, users = users)
    }

    /**
     * 내 받을 계좌 등록·변경(A1). 사람 단위라 내가 들어간 **진행 중인** 술자리 모두에 반영한다
     * (완료된 술자리는 바꾸지 않는다 — 이미 끝난 송금의 기록이다).
     */
    fun registerPayout(payout: Payout) {
        val p = Payout(payout.bank, payout.accountNo.trim(), payout.holder.trim())
        val me = state.me.copy(payout = p)
        state = state.copy(me = me, users = state.users.map { if (it.id == me.id) me else it })
        for (g in state.rooms.values.toList()) {
            val mine = g.participantOfUser(me.id) ?: continue
            if (g.status == GatheringStatus.COMPLETED) continue
            val first = mine.payout == null
            val next = g.copy(participants = g.participants.map { if (it.id == mine.id) it.copy(payout = p) else it })
                .push(TimelineType.SYSTEM, "${me.displayName}님이 받을 계좌를 ${if (first) "등록했어요" else "바꿨어요"}")
            commit(g, next)
        }
    }
}

/** R4 내보내기를 막는 이유. 없으면 null — 화면이 버튼을 끄고 이유를 보여주는 데도 쓴다 */
fun removeBlockedReason(g: Gathering, participantId: Id): String? {
    if (g.status != GatheringStatus.OPEN) return "정산한 뒤에는 내보낼 수 없어요"
    val p = g.participants.find { it.id == participantId } ?: return "이미 없는 사람이에요"
    if (p.userId == g.hostUserId) return "총무는 내보낼 수 없어요"
    if (g.rounds.any { it.payerParticipantId == participantId }) return "결제자는 내보낼 수 없어요. 차수의 낸 사람을 먼저 바꿔주세요"
    return null
}

/** 한 칸(참여자 × 차수)의 응답을 바꾸거나 새로 넣는다 */
private fun List<RoundResponse>.setResponse(r: RoundResponse): List<RoundResponse> =
    filterNot { it.participantId == r.participantId && it.roundId == r.roundId } + r
