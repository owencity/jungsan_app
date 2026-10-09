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
    private fun commit(prev: Gathering, after: Gathering) {
        if (after == prev) return
        // 자동 정산(FC-020) — 어떤 동작이든(응답·참여·대리 응답·인원 변경·차수 삭제) "모두 모였다"로 바뀐 순간 정산한다.
        // 알림 규칙처럼 전·후만 보고 정해서, 경로마다 정산 코드를 흩뿌리지 않는다. 웹 store commit 과 같다
        val next = if (!prev.allIn() && after.allIn()) settleNow(after, auto = true) else after
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
                relabel(g.rounds + Round(newId, Int.MAX_VALUE, "", draft.total, draft.payerParticipantId, draft.drinks), g.firstSeq ?: 1)
            }
            val label = rounds.first { it.id == (if (exists) draft.id else created) }.label
            g.copy(rounds = rounds, inputRevision = g.inputRevision + 1)
                .push(TimelineType.SYSTEM, "$label ${app.jeongsan.util.won(draft.total)}을 ${if (exists) "고쳤어요" else "넣었어요"}")
        }
        return created
    }

    /**
     * 차수 저장 + 총무 본인 응답(FC-019 A) — 웹 `gateway.saveRound`. R2의 "나는 이 차수에"를 같이 저장해
     * 정산하기에서 총무 이름이 "응답 없음"에 뜨지 않게 한다. 응답은 새 차수거나 값이 바뀌었을 때만 넣는다 —
     * 같은 값을 또 넣으면 타임라인에 "응답을 고쳤어요"가 쌓인다. 반환은 [saveRound]와 같다.
     */
    fun saveRoundAsHost(roomId: Id, draft: RoundDraft, mine: ResponseType): Id? {
        val before = state.rooms[roomId] ?: return null
        if (before.hostUserId != me.id || before.status != GatheringStatus.OPEN) return null
        val meP = before.participants.find { it.userId == me.id }
        val prev = draft.id?.let { rid -> meP?.let { before.responseOf(it.id, rid)?.type } }
        val created = saveRound(roomId, draft)
        val id = created ?: draft.id ?: return null
        if (mine != prev) respond(roomId, mapOf(id to mine))
        return created
    }

    fun deleteRound(roomId: Id, roundId: Id) = update(roomId) { g, _ ->
        val target = g.rounds.find { it.id == roundId }
        if (target == null || g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
        g.copy(
            rounds = relabel(g.rounds.filter { it.id != roundId }, g.firstSeq ?: 1),
            responses = g.responses.filter { it.roundId != roundId },
            inputRevision = g.inputRevision + 1,
        ).push(TimelineType.SYSTEM, "${target.label}를 지웠어요")
    }

    /** 입력 없이 새 술자리를 만들고 id를 돌려준다. 만든 사람이 총무이자 첫 참여자다 */
    /**
     * 새 술자리를 만들고 id를 돌려준다. 만든 사람이 총무이자 첫 참여자다. 제목이 없으면 `M/d 술자리`.
     * 다음 차를 다른 사람이 계산했을 때도 이걸 쓴다 — 그 사람이 총무인 **완전히 별개의** 술자리다(사람도 새로 들어온다)
     */
    fun createGathering(title: String? = null): Id {
        val s = state
        val all = s.rooms.values
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val pid = (all.flatMap { g -> g.participants.map { it.id } }.maxOrNull() ?: 0) + 1
        val now = clock()
        val g = Gathering(
            id = id, title = title?.trim()?.ifEmpty { null } ?: autoTitle(now), date = now, hostUserId = s.me.id, status = GatheringStatus.OPEN,
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
        commit(g, settleNow(g, auto = false))
        return SettleResult.OK
    }

    /**
     * 정산(서버 흉내) — 미리보기대로 송금을 만들고 금액을 고정한다. 수동([지금 계산하기])과 자동(FC-020)이 같이 쓴다.
     * 응답 없는 칸은 전 차수 참석·알코올(AUTO)로 채운다. 보낼 돈이 하나도 없으면 바로 완료
     */
    private fun settleNow(g: Gathering, auto: Boolean): Gathering {
        val preview = mockPreview(g)
        val autoNames = preview.lines.filter { it.auto }.map { g.nameOf(it.participantId) }
        val who = if (auto) "모두 응답해서 자동으로 계산했어요" else "${g.host().displayName}님이 정산했어요"
        val body = if (autoNames.isNotEmpty()) "$who · ${autoNames.joinToString("·")}님은 응답이 없어 전 차수 참석·알코올로 계산됐어요" else who
        val settled = g.copy(
            status = GatheringStatus.SETTLING,
            responses = withAutoResponses(g),
            transfers = preview.transfers.mapIndexed { i, t ->
                Transfer(i + 1L, t.fromParticipantId, t.toParticipantId, t.amount, TransferStatus.WAITING, basis = t.basis)
            },
        )
        return settled.push(TimelineType.SYSTEM, body).completeIfDone()
    }

    /** 인원(총무 포함, FC-020) — 총무·정산 전. 2~50으로 맞춘다. 이 인원이 모두 응답하면 자동 정산 */
    fun setHeadcount(roomId: Id, headcount: Int) = update(roomId) { g, _ ->
        if (g.hostUserId != me.id || g.status != GatheringStatus.OPEN) return@update g
        val n = headcount.coerceIn(HEADCOUNT_MIN, HEADCOUNT_MAX)
        if (n == g.headcount) g else g.copy(headcount = n)
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
    // ── 서버 응답으로 채우기(API 모드) — 웹 store 의 같은 이름 동작 ──

    /** 서버 술자리 하나의 정산방들을 통째로 바꾼다. [viewed]는 내가 정산금액을 열어본 정산 단위 id */
    fun replaceGathering(gatheringId: Id, rooms: List<Gathering>, viewed: List<Id>) {
        val s = state
        val ids = rooms.map { it.id }.toSet()
        val kept = s.rooms.values.filter { (it.gatheringId ?: it.id) != gatheringId && it.id !in ids }
        val seen = s.paySeen.filter { it.substringBefore(':').toLongOrNull() !in ids }.toSet() + viewed.map { seenKey(it, s.me.id) }
        state = s.copy(rooms = (kept + rooms).associateBy { it.id }, paySeen = seen)
    }

    /** 내 술자리 전부(H1) */
    fun replaceAllRooms(rooms: List<Gathering>, viewed: List<Id>) {
        state = state.copy(rooms = rooms.associateBy { it.id }, paySeen = viewed.map { seenKey(it, state.me.id) }.toSet())
    }

    fun setNotifications(list: List<AppNotification>) {
        state = state.copy(notifications = list)
    }

    /**
     * 다음 차 총무 되기(목데이터, FC-015) — 같은 술자리 안에 내가 총무인 정산방을 만든다. 계산 대상은 고른 사람 + 나.
     * 차수 번호는 술자리 전체에서 이어진다(앞 총무가 2차까지 했으면 내 첫 차수는 3차). 웹 store `createUnit`과 같다
     */
    fun createUnit(roomId: Id, participantIds: List<Id>): Id? {
        val s = state
        val src = s.rooms[roomId] ?: return null
        val mine = src.participantOfUser(s.me.id) ?: return null
        val all = s.rooms.values
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val gatheringId = src.gatheringId ?: src.id
        val lastSeq = all.filter { (it.gatheringId ?: it.id) == gatheringId }.flatMap { g -> g.rounds.map { it.seq } }.maxOrNull() ?: 0
        val picked = (participantIds + mine.id).toSet()
        val g = src.copy(
            id = id, gatheringId = gatheringId, hostUserId = s.me.id, status = GatheringStatus.OPEN, inputRevision = 0,
            completedAt = null, participants = src.participants.filter { it.id in picked },
            rounds = emptyList(), responses = emptyList(), transfers = emptyList(), spoonGivers = emptyList(),
            firstSeq = lastSeq + 1,
            // 다음 차 인원의 시작값은 고른 사람 + 나(FC-020) — R2에서 바꿀 수 있다
            headcount = maxOf(HEADCOUNT_MIN, picked.size),
        ).push(TimelineType.SYSTEM, "${s.me.displayName}님이 추가 차수의 총무가 되었어요")
        state = s.copy(rooms = s.rooms + (id to g) + (src.id to src.copy(gatheringId = gatheringId)))
        return id
    }

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
