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
            participants = listOf(Participant(pid, s.me.id, s.me.displayName, s.me.spoonCount, s.me.payout)),
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
        MockV3.USERS.find { it.id == userId }?.let { state = state.copy(me = it) }
    }
}

/** 한 칸(참여자 × 차수)의 응답을 바꾸거나 새로 넣는다 */
private fun List<RoundResponse>.setResponse(r: RoundResponse): List<RoundResponse> =
    filterNot { it.participantId == r.participantId && it.roundId == r.roundId } + r
