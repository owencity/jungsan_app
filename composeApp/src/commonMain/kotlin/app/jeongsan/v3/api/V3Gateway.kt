package app.jeongsan.v3.api

import app.jeongsan.domain.Id
import app.jeongsan.v3.AppNotification
import app.jeongsan.v3.Payout
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.RoundDraft
import app.jeongsan.v3.SettlePreview
import app.jeongsan.v3.SettleResult
import app.jeongsan.v3.Target
import app.jeongsan.v3.V3Store
import app.jeongsan.v3.mockPreview
import app.jeongsan.v3.participantOfUser
import app.jeongsan.v3.validateName
import kotlinx.datetime.Clock
import kotlinx.datetime.toDeprecatedInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 만든 것의 id, 또는 사용자에게 보여줄 문구 */
sealed interface Made {
    data class Ok(val id: Id) : Made
    data class Err(val message: String) : Made
}

/** 정산하기 결과 — [SettleResult] 셋 + 그 밖의 서버 오류 문구 */
sealed interface Settled {
    data class Done(val result: SettleResult) : Settled
    data class Err(val message: String) : Settled
}

/**
 * 화면이 부르는 동작의 입구 — 웹 `v3/gateway.ts`와 같다. 목데이터 모드([client]가 null)면 스토어를 바로 바꾸고,
 * API 모드면 서버를 부른 뒤 **서버 응답으로** 스토어를 채운다.
 *
 * API 모드의 원칙: 바꾸는 요청을 보낸 뒤 그 술자리를 **다시 읽어** 스토어를 통째로 바꾼다([refresh]). 서버가 상태 전이·
 * 알림·타임라인을 만들므로 앱이 결과를 흉내 내지 않는다. 서버 계약은 `jungsan_attack` `docs/SETTLEMENT_UNITS.md` §4.
 *
 * 반환값 규칙: 성공이면 null(또는 결과 값), 실패면 **사용자에게 보여줄 문구**.
 */
class V3Gateway(private val store: V3Store, private val client: ApiClient?) {
    val isApiMode get() = client != null

    // ── 도우미 ──

    /** 정산방 → 서버 경로에 쓰는 (술자리 id, 정산 단위 id) */
    private fun ctx(roomId: Id): Pair<Id, Id> = (store.state.rooms[roomId]?.gatheringId ?: roomId) to roomId

    /** 서버 술자리 하나를 스토어에 — 그 술자리의 정산방들을 통째로 바꾼다 */
    private fun put(g: ServerGathering) = store.replaceGathering(g.id, g.toRooms(), g.viewedUnitIds())

    private suspend fun refresh(c: ApiClient, gid: Id) = put(c.gathering(gid))

    /** 서버 호출 하나를 감싸 실패를 문구로 바꾼다 */
    private inline fun <T> attempt(block: () -> T): Result<T> =
        try { Result.success(block()) } catch (e: ApiError) { Result.failure(e) }

    private fun Throwable.text() = (this as? ApiError)?.let(::messageOf) ?: "잠시 뒤 다시 시도해주세요"

    /** 바꾸는 요청 → 그 술자리 다시 읽기. 실패면 문구 */
    private suspend fun mutate(c: ApiClient, gid: Id, call: suspend () -> Unit): String? =
        try { call(); refresh(c, gid); null } catch (e: ApiError) { messageOf(e) }

    private fun answers(a: Map<Id, ResponseType>) = a.map { (rid, t) -> AnswerBody(rid, t.name) }

    // ── 로그인·내 정보 ──

    /** 로그인돼 있으면 내 정보를 스토어에 넣고 true. 토큰이 없거나 401이면 false(로그인 화면). 목데이터 모드는 false */
    suspend fun loadMe(): Boolean {
        val c = client ?: return false
        return try {
            store.setMe(c.me().toUser(store.state.me))
            true
        } catch (e: ApiError) {
            if (e.status == 401) c.logout() // 만료된 토큰은 버린다 — 다음엔 로그인부터
            false
        }
    }

    /** L2·P1 실명 등록(FC-013). 서버와 같은 2~10자 규칙이라 서버까지 가기 전에 막는다 */
    suspend fun confirmName(name: String): String? {
        validateName(name).firstOrNull()?.let { return it }
        val c = client ?: run {
            store.confirmName(name)
            return null
        }
        return try {
            store.setMe(c.putDisplayName(name.trim()).toUser(store.state.me))
            null
        } catch (e: ApiError) {
            messageOf(e)
        }
    }

    /** 받을 계좌(A1) — 사람 단위. 저장 뒤 내 정보와 내 술자리를 다시 읽는다(계좌 공개가 바뀐다) */
    suspend fun registerPayout(p: Payout): String? {
        val c = client ?: run { store.registerPayout(p); return null }
        return try {
            c.putPayout(PayoutBody(p.bank, p.accountNo, p.holder))
            loadMe()
            loadMine()
        } catch (e: ApiError) {
            messageOf(e)
        }
    }

    /** 로그아웃 — 서버 세션을 끊고 기기의 토큰·화면 데이터를 지운다. 목데이터는 할 일 없음 */
    suspend fun logout() {
        val c = client ?: return
        c.signOut()
        store.reset(app.jeongsan.v3.V3State.empty())
    }

    /** 회원 탈퇴(App Store 5.1.1(v)) — 성공하면 null. 서버가 계정·연결을 지우고, 기기의 토큰·화면 데이터도 지운다 */
    suspend fun deleteAccount(): String? {
        val c = client ?: return "목데이터 모드에서는 탈퇴할 수 없어요"
        return try {
            c.deleteAccount()
            store.reset(app.jeongsan.v3.V3State.empty())
            null
        } catch (e: ApiError) {
            messageOf(e)
        }
    }

    // ── 읽기 ──

    /** 내 술자리 전부 + 알림(H1 들어올 때). 목데이터는 할 일 없음 */
    suspend fun loadMine(): String? {
        val c = client ?: return null
        return try {
            val list = c.myGatherings()
            val notes = c.notifications()
            store.replaceAllRooms(list.flatMap { it.toRooms() }, list.flatMap { it.viewedUnitIds() })
            store.setNotifications(notes.map { toNotification(it) })
            null
        } catch (e: ApiError) {
            messageOf(e)
        }
    }

    /** 정산방 하나를 서버에서 다시 읽는다. 스토어에 없는 정산방(알림·링크로 바로 들어옴)이면 내 술자리 전부를 읽는다 */
    suspend fun loadRoom(roomId: Id): String? {
        val c = client ?: return null
        if (store.state.rooms[roomId] == null) return loadMine()
        return try { refresh(c, ctx(roomId).first); null } catch (e: ApiError) { messageOf(e) }
    }

    /** 알림 종류 → 누르면 갈 곳(FC-005). 송금 관련은 보낼 돈(P3), 그 밖은 정산방. 웹과 같은 표 */
    private fun toNotification(n: ServerNotification): AppNotification {
        val rooms = store.state.rooms.values
        val roomId = n.settlementUnitId ?: rooms.find { (it.gatheringId ?: it.id) == n.gatheringId }?.id ?: 0
        return AppNotification(
            id = n.id, userId = store.state.me.id, roomId = roomId,
            // 서버 title 은 아직 종류 코드다(FC-018) — 고쳐질 때까지 사람이 읽는 body 를 제목으로 쓴다
            title = n.body, body = "",
            target = if (n.type in PAY_TYPES) Target.Pay(roomId) else Target.Room(roomId),
            createdAt = kotlin.time.Instant.parse(n.createdAt).toDeprecatedInstant(),
            read = n.readAt != null,
        )
    }

    // ── 만들기 ──

    /** 새 술자리(H1 [+ 새 술자리]) */
    suspend fun createGathering(): Made {
        val c = client ?: return Made.Ok(store.createGathering())
        return try {
            val g = c.createGathering()
            put(g)
            Made.Ok(g.settlementUnits.first().id)
        } catch (e: ApiError) {
            Made.Err(messageOf(e))
        }
    }

    /** 다음 차는 내가 계산했어요 — 같은 술자리 안에 내가 총무인 정산 단위(FC-015). 계산 대상은 고른 사람 + 나 */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createUnit(roomId: Id, participantIds: List<Id>): Made {
        val c = client ?: return store.createUnit(roomId, participantIds)?.let { Made.Ok(it) } ?: Made.Err("이 술자리에 참여 중이 아니에요")
        val (gid, _) = ctx(roomId)
        val me = store.state.rooms[roomId]?.participantOfUser(store.state.me.id)
        return try {
            // requestId 는 멱등 키 — 같은 요청을 다시 보내도 단위가 둘 생기지 않는다(SETTLEMENT_UNITS §4.2)
            val ids = (listOfNotNull(me?.id) + participantIds).distinct()
            // 다음 차 인원의 시작값은 고른 사람 + 나(FC-020) — R2에서 바꿀 수 있다
            val u = c.createUnit(gid, Uuid.random().toString(), ids, ids.size)
            refresh(c, gid)
            Made.Ok(u.id)
        } catch (e: ApiError) {
            Made.Err(messageOf(e))
        }
    }

    /**
     * 차수 저장(R2) + 총무 본인 응답(FC-019 A). 응답은 새 차수거나 값이 바뀌었을 때만 보낸다.
     * 서버 변경 없이 기존 `PUT U/responses/me`를 차수 저장 뒤에 한 번 더 부른다
     */
    suspend fun saveRound(roomId: Id, draft: RoundDraft, mine: ResponseType, headcount: Int? = null): Made {
        val before = store.state.rooms[roomId]
        // 인원(FC-020)은 바뀌었을 때만 — 응답을 넣은 뒤에 바꿔야 "모두 모였다" 판정이 새 응답까지 본다
        val headcountChanged = headcount != null && headcount != before?.headcount
        val c = client ?: run {
            val id = store.saveRoundAsHost(roomId, draft, mine) ?: draft.id ?: 0
            if (headcountChanged) store.setHeadcount(roomId, headcount!!)
            return Made.Ok(id)
        }
        val meP = before?.participantOfUser(store.state.me.id)
        val prev = draft.id?.let { rid -> before?.responses?.find { it.participantId == meP?.id && it.roundId == rid }?.type }
        val (gid, uid) = ctx(roomId)
        val body = RoundBody(draft.total, draft.payerParticipantId, draft.drinks.map { SDrink(it.name, it.unitPrice, it.quantity) })
        return try {
            val r = draft.id?.let { c.putRound(gid, uid, it, body) } ?: c.addRound(gid, uid, body)
            // 차수는 들어갔는데 응답만 실패하면 정산 때 "응답 없음"으로 보일 뿐이라 차수 저장을 실패로 돌리지 않는다
            if (mine != prev) attempt { c.respond(gid, uid, listOf(AnswerBody(r.id, mine.name))) }
            // 인원은 서버가 아직 모르면(FC-020 배포 전) 실패해도 차수 저장은 성공으로 둔다
            if (headcountChanged) attempt { c.putHeadcount(gid, uid, headcount!!) }
            refresh(c, gid)
            Made.Ok(r.id)
        } catch (e: ApiError) {
            Made.Err(messageOf(e))
        }
    }

    suspend fun deleteRound(roomId: Id, roundId: Id): String? {
        val c = client ?: run { store.deleteRound(roomId, roundId); return null }
        val (gid, uid) = ctx(roomId)
        return mutate(c, gid) { c.deleteRound(gid, uid, roundId) }
    }

    // ── 응답·정산 ──

    /** 내 응답(P2) */
    suspend fun respond(roomId: Id, a: Map<Id, ResponseType>): String? {
        val c = client ?: run { store.respond(roomId, a); return null }
        val (gid, uid) = ctx(roomId)
        return mutate(c, gid) { c.respond(gid, uid, answers(a)) }
    }

    /** 총무 대리 응답(R3) */
    suspend fun respondAsHost(roomId: Id, participantId: Id, roundId: Id, type: ResponseType): String? {
        val c = client ?: run { store.respondAsHost(roomId, participantId, roundId, type); return null }
        val (gid, uid) = ctx(roomId)
        return mutate(c, gid) { c.respondFor(gid, uid, participantId, listOf(AnswerBody(roundId, type.name))) }
    }

    /** 정산 미리보기(R3) — 금액은 서버 Core 가 계산한다. 목데이터는 MockServer 흉내. 실패면 문구 */
    suspend fun preview(roomId: Id): Result<SettlePreview> {
        val c = client ?: return store.state.rooms[roomId]?.let { Result.success(mockPreview(it)) }
            ?: Result.failure(IllegalStateException("없는 술자리예요"))
        val (gid, uid) = ctx(roomId)
        return attempt { c.preview(gid, uid).toPreview() }
    }

    fun previewError(e: Throwable) = e.text()

    /** 정산하기 — 미리보기 때의 입력 버전·해시를 같이 보낸다. 그 사이 바뀌었으면 STALE */
    suspend fun settle(roomId: Id, preview: SettlePreview): Settled {
        val c = client ?: return Settled.Done(store.settle(roomId, preview.inputRevision))
        val (gid, uid) = ctx(roomId)
        return try {
            put(c.settle(gid, uid, preview.inputRevision, preview.inputHash.orEmpty()))
            Settled.Done(SettleResult.OK)
        } catch (e: ApiError) {
            when {
                e.code == "SETTLEMENT_INPUT_CHANGED" -> Settled.Done(SettleResult.STALE)
                e.status == 403 -> Settled.Done(SettleResult.DENIED)
                else -> Settled.Err(messageOf(e))
            }
        }
    }

    /** 정산금액(P3)을 열어봤다(D5) — 뱃지를 뗀다. 실패해도 화면은 막지 않는다 */
    suspend fun markPaySeen(roomId: Id) {
        store.markPaySeen(roomId)
        val c = client ?: return
        val (gid, uid) = ctx(roomId)
        attempt { c.markViewed(gid, uid) }
    }

    // ── 송금 ──

    suspend fun markSent(roomId: Id, transferId: Id): String? {
        val c = client ?: run { store.markSent(roomId, transferId); return null }
        return mutate(c, ctx(roomId).first) { c.sent(transferId) }
    }

    suspend fun confirmIncoming(roomId: Id, transferId: Id): String? {
        val c = client ?: run { store.confirmIncoming(roomId, transferId); return null }
        return mutate(c, ctx(roomId).first) { c.confirm(transferId) }
    }

    suspend fun notReceived(roomId: Id, transferId: Id): String? {
        val c = client ?: run { store.notReceived(roomId, transferId); return null }
        return mutate(c, ctx(roomId).first) { c.notReceived(transferId) }
    }

    // ── 정산방 ──

    /** 타임라인 메시지 — 술자리 하나에 하나라 어느 정산방에서 써도 같은 곳에 남는다 */
    suspend fun sendMessage(roomId: Id, text: String): String? {
        val c = client ?: run { store.sendMessage(roomId, text); return null }
        val (gid, _) = ctx(roomId)
        return mutate(c, gid) { c.sendMessage(gid, text) }
    }

    /** 인원(FC-020) — R1 [포함하기]. 서버는 바꾼 뒤 자동 정산 판정을 한 번 돈다 */
    suspend fun setHeadcount(roomId: Id, headcount: Int): String? {
        val c = client ?: run { store.setHeadcount(roomId, headcount); return null }
        val (gid, uid) = ctx(roomId)
        return mutate(c, gid) { c.putHeadcount(gid, uid, headcount) }
    }

    /** 계산 대상에서 빼기(R4) — 그 정산 단위에서만. 공유 참여자는 남는다 */
    suspend fun removeParticipant(roomId: Id, participantId: Id): String? {
        val c = client ?: return store.removeParticipant(roomId, participantId)
        val (gid, uid) = ctx(roomId)
        return mutate(c, gid) { c.removeMember(gid, uid, participantId) }
    }

    /** 스푼(🥄) — 복수 총무 지급 규칙이 서버에서 미결(SETTLEMENT_UNITS §7)이라 API 모드에선 아직 열지 않는다 */
    fun giveSpoon(roomId: Id): String? {
        if (client == null) { store.giveSpoon(roomId); return null }
        return "스푼은 곧 열려요"
    }

    // ── 알림 ──

    suspend fun markRead(id: Id) {
        store.markRead(id)
        client?.let { c -> attempt { c.readNotification(id) } }
    }

    suspend fun markAllRead() {
        store.markAllRead()
        client?.let { c -> attempt { c.readAllNotifications() } }
    }

    // ── 링크 참여(P1) ──

    /** 링크 미리보기(로그인 없이) — API 모드만. 목데이터는 화면이 스토어에서 찾는다 */
    suspend fun joinPreview(token: String): Result<ServerJoinPreview> {
        val c = client ?: return Result.failure(IllegalStateException("목데이터 모드"))
        return attempt { c.joinPreview(token) }
    }

    /** 링크로 참여 + 응답 — 고른 정산 단위에. 들어간 정산방 id */
    suspend fun join(token: String, unitId: Id?, a: Map<Id, ResponseType>): Made {
        val c = client ?: return store.joinGathering(token, a)?.let { Made.Ok(it) } ?: Made.Err("참여할 수 없는 링크예요")
        if (unitId == null) return Made.Err("참여할 차수를 골라주세요")
        return try {
            val r = c.join(token, unitId, answers(a))
            refresh(c, r.gatheringId)
            Made.Ok(unitId)
        } catch (e: ApiError) {
            Made.Err(messageOf(e))
        }
    }

    companion object {
        /** 서버 오류 코드 → 화면 문구. 없는 코드는 서버 message 그대로(API.md §1.2) — 웹 gateway.ts 와 같은 표 */
        private val MESSAGES = mapOf(
            "DISPLAY_NAME_ALREADY_SET" to "이미 이름을 정했어요. 정한 이름은 바꿀 수 없어요",
            "UNAUTHENTICATED" to "로그인이 풀렸어요. 다시 로그인해주세요",
            "TOKEN_EXPIRED" to "로그인이 풀렸어요. 다시 로그인해주세요",
            "SETTLEMENT_INPUT_CHANGED" to "그 사이 응답이 바뀌었어요. 금액을 다시 확인해주세요",
            "SETTLEMENT_UNIT_NOT_OPEN" to "이미 정산한 차수예요",
            "NOT_SETTLEMENT_UNIT_HOST" to "이 차수의 총무만 할 수 있어요",
            "NOT_SETTLEMENT_UNIT_MEMBER" to "이 차수의 계산 대상이 아니에요",
            "REMOVE_HOST" to "총무는 뺄 수 없어요",
            "REMOVE_PAYER" to "결제자는 뺄 수 없어요. 차수의 낸 사람을 먼저 바꿔주세요",
            "TRANSFER_ALREADY_SENT" to "이미 보낸 사람이 있어서 되돌릴 수 없어요",
            "NO_ROUNDS" to "차수를 먼저 넣어주세요",
        )

        /** 알림 중 누르면 보낼 돈(P3)으로 가는 종류 */
        private val PAY_TYPES = setOf("SETTLED", "NOT_RECEIVED", "PAYOUT_REGISTERED")

        fun messageOf(e: ApiError): String = MESSAGES[e.code] ?: e.message ?: "잠시 뒤 다시 시도해주세요"
    }
}
