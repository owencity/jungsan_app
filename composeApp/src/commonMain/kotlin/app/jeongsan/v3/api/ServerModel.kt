package app.jeongsan.v3.api

import app.jeongsan.domain.Id
import app.jeongsan.v3.DrinkItem
import app.jeongsan.v3.Gathering
import app.jeongsan.v3.GatheringStatus
import app.jeongsan.v3.Participant
import app.jeongsan.v3.Payout
import app.jeongsan.v3.ResponseSource
import app.jeongsan.v3.ResponseType
import app.jeongsan.v3.Round
import app.jeongsan.v3.RoundResponse
import app.jeongsan.v3.TimelineEntry
import app.jeongsan.v3.TimelineType
import app.jeongsan.v3.Transfer
import app.jeongsan.v3.TransferBasis
import app.jeongsan.v3.TransferStatus
import app.jeongsan.v3.SettlePreview
import app.jeongsan.v3.PreviewLine
import app.jeongsan.v3.PreviewTransfer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable

/*
 * 서버(API v5, `jungsan_attack` `docs/SETTLEMENT_UNITS.md` §4) 응답 → 화면 모델. 웹 `v3/serverModel.ts`와 같은 규칙이다.
 *
 * 서버의 술자리 하나에는 총무별 정산 단위가 여럿 있을 수 있다(FC-015). 정산 단위는 총무·상태·차수·명단·송금을
 * 각자 가져서 화면의 "정산방(Gathering)"과 모양이 같다 — 그래서 **정산 단위 하나 = 정산방 하나**로 바꿔 끼운다.
 * 정산방 id 는 정산 단위 id, 원래 술자리 id 는 [Gathering.gatheringId].
 *
 * 서버 enum 은 문자열로 받는다 — 서버가 새 값을 더해도 앱이 응답 전체를 못 읽는 일이 없게.
 */

@Serializable data class SPayout(val bank: String, val accountNo: String, val holder: String)
@Serializable data class SParticipant(
    val id: Long, val userId: Long, val displayName: String, val nickname: String? = null, val spoonCount: Int = 0,
    val payout: SPayout? = null,
)
@Serializable data class SUnitMe(val included: Boolean = false, val settlementViewed: Boolean = false)
@Serializable data class SUnit(
    val id: Long, val hostParticipantId: Long, val status: String, val inputRevision: Int = 0,
    val completedAt: String? = null, val participantIds: List<Long> = emptyList(), val me: SUnitMe = SUnitMe(),
    /** 총무가 넣은 인원(FC-020). 서버가 아직 안 주면 null */
    val headcount: Int? = null,
    /** 자동 계산이 멈춘 이유(API v8). 정상이면 null */
    val autoSettlementError: String? = null,
)
@Serializable data class SDrink(val name: String, val unitPrice: Long, val quantity: Int)
@Serializable data class SRound(
    val id: Long, val settlementUnitId: Long, val seq: Int, val total: Long, val payerParticipantId: Long,
    val drinks: List<SDrink> = emptyList(),
)
@Serializable data class SResponse(val participantId: Long, val roundId: Long, val type: String, val source: String)
@Serializable data class SBasis(val roundId: Long, val type: String, val amount: Long)
@Serializable data class STransfer(
    val id: Long, val settlementUnitId: Long, val fromParticipantId: Long, val toParticipantId: Long, val amount: Long,
    val status: String, val sentAt: String? = null, val confirmedAt: String? = null, val notReceivedAt: String? = null,
    val basis: List<SBasis> = emptyList(),
)
@Serializable data class STimeline(
    val id: Long, val settlementUnitId: Long? = null, val type: String, val authorParticipantId: Long? = null,
    val body: String, val createdAt: String,
)
@Serializable data class SGatheringMe(val participantId: Long? = null)
@Serializable data class ServerGathering(
    val id: Long, val title: String, val date: String, val shareToken: String, val status: String,
    val completedAt: String? = null,
    val participants: List<SParticipant> = emptyList(),
    val settlementUnits: List<SUnit> = emptyList(),
    val rounds: List<SRound> = emptyList(),
    val responses: List<SResponse> = emptyList(),
    val transfers: List<STransfer> = emptyList(),
    val timeline: List<STimeline> = emptyList(),
    val me: SGatheringMe = SGatheringMe(),
)

/** 공개 미리보기 `GET /join/{token}` — 이름·응답·계좌는 없다 */
@Serializable data class SJoinHost(val displayName: String, val spoonCount: Int = 0)
@Serializable data class SJoinRound(val id: Long, val seq: Int, val total: Long)
@Serializable data class SJoinUnit(val id: Long, val status: String, val host: SJoinHost, val rounds: List<SJoinRound> = emptyList())
@Serializable data class ServerJoinPreview(
    val title: String, val date: String, val status: String, val participantCount: Int = 0,
    val settlementUnits: List<SJoinUnit> = emptyList(),
)

/** 미리보기 `GET …/settlement/preview` */
@Serializable data class SPreviewLine(val participantId: Long, val total: Long, val auto: Boolean = false, val rounds: List<SBasis> = emptyList())
@Serializable data class SPreviewTransfer(val from: Long, val to: Long, val amount: Long, val basis: List<SBasis> = emptyList())
@Serializable data class ServerPreview(
    val settlementUnitId: Long, val inputRevision: Int, val inputHash: String,
    val lines: List<SPreviewLine> = emptyList(), val transfers: List<SPreviewTransfer> = emptyList(),
)

// ── 바꾸기 ────────────────────────────────────────────

private fun instant(s: String) = kotlin.time.Instant.parse(s).toDeprecatedInstant()

/** 서버는 날짜만 준다(LocalDate) — 그날 정오로 둔다(시간대가 달라도 날짜가 밀리지 않게). 웹과 같다 */
private fun noonOf(date: String) =
    LocalDateTime(LocalDate.parse(date), LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault()).toDeprecatedInstant()

private fun SBasis.toBasis() = TransferBasis(roundId, ResponseType.valueOf(type), amount)

private fun SParticipant.toParticipant() = Participant(
    id = id, userId = userId, displayName = displayName, spoonCount = spoonCount,
    payout = payout?.let { Payout(it.bank, it.accountNo, it.holder) }, nickname = nickname,
)

/** 총무가 둘 이상이면 같은 이름이 두 줄 뜨므로 담당 차수를 붙인다("10/9 술자리 · 3차부터") */
private fun ServerGathering.unitTitle(u: SUnit): String {
    if (settlementUnits.size < 2) return title
    val seqs = rounds.filter { it.settlementUnitId == u.id }.map { it.seq }
    return if (seqs.isEmpty()) "$title · 다음 차" else "$title · ${seqs.min()}차부터"
}

/** 서버 술자리 하나 → 내가 들어 있는 정산 단위마다 정산방 하나 */
fun ServerGathering.toRooms(): List<Gathering> =
    settlementUnits.filter { it.me.included || it.hostParticipantId == me.participantId }.map { toRoom(it) }

fun ServerGathering.toRoom(u: SUnit): Gathering {
    val members = u.participantIds.toSet()
    val host = participants.find { it.id == u.hostParticipantId }
    val mine = rounds.filter { it.settlementUnitId == u.id }.sortedBy { it.seq }
    val roundIds = mine.map { it.id }.toSet()
    return Gathering(
        id = u.id,
        gatheringId = id,
        title = unitTitle(u),
        date = noonOf(date),
        hostUserId = host?.userId ?: -1,
        status = GatheringStatus.valueOf(u.status),
        shareToken = shareToken,
        inputRevision = u.inputRevision,
        headcount = u.headcount,
        autoSettlementError = u.autoSettlementError,
        completedAt = u.completedAt?.let(::instant),
        // 명단은 서버가 준 단위 순서 그대로 — 총무 먼저, 그다음 이 단위에 들어온 순서. "인원 안"을 이 순서로 센다(API v8)
        participants = u.participantIds.mapNotNull { id -> participants.find { it.id == id } }.map { it.toParticipant() },
        // 차수 이름은 앱이 붙인다(FC-014 D4). seq 는 술자리 전체 번호라 다음 총무의 첫 차수는 "3차"가 된다
        rounds = mine.map { r ->
            Round(r.id, r.seq, "${r.seq}차", r.total, r.payerParticipantId, r.drinks.map { DrinkItem(it.name, it.unitPrice, it.quantity) })
        },
        responses = responses.filter { it.roundId in roundIds && it.participantId in members }
            .map { RoundResponse(it.participantId, it.roundId, ResponseType.valueOf(it.type), ResponseSource.valueOf(it.source)) },
        transfers = transfers.filter { it.settlementUnitId == u.id }.map { t ->
            Transfer(
                id = t.id, fromParticipantId = t.fromParticipantId, toParticipantId = t.toParticipantId, amount = t.amount,
                status = TransferStatus.valueOf(t.status),
                sentAt = t.sentAt?.let(::instant), confirmedAt = t.confirmedAt?.let(::instant), notReceivedAt = t.notReceivedAt?.let(::instant),
                basis = t.basis.map { it.toBasis() },
            )
        },
        // 타임라인은 술자리 하나에 하나다 — 어느 정산방에서 보든 같은 대화가 보인다
        timeline = timeline.map { TimelineEntry(it.id, TimelineType.valueOf(it.type), it.body, instant(it.createdAt), it.authorParticipantId) },
        // 스푼 기록은 아직 서버 계약에 없다(SETTLEMENT_UNITS §7 미결)
        spoonGivers = emptyList(),
        // 서버가 다음 차수에 붙일 번호 — 술자리 전체에서 가장 큰 번호 다음
        nextSeq = (rounds.maxOfOrNull { it.seq } ?: 0) + 1,
    )
}

/** 이 정산방에서 내가 정산금액을 열어봤나(D5, 단위별) */
fun ServerGathering.viewedUnitIds(): List<Id> = settlementUnits.filter { it.me.settlementViewed }.map { it.id }

/** 서버 미리보기 → 화면 미리보기(R3). inputHash 는 정산하기 요청에 그대로 돌려보낸다 */
fun ServerPreview.toPreview() = SettlePreview(
    inputRevision = inputRevision,
    inputHash = inputHash,
    lines = lines.map { PreviewLine(it.participantId, it.total, it.auto, it.rounds.map { b -> b.toBasis() }) },
    transfers = transfers.map { PreviewTransfer(it.from, it.to, it.amount, it.basis.map { b -> b.toBasis() }) },
)

/**
 * 링크 미리보기(P1, 로그인 전) → 입구 화면이 그리는 정산방들. 아직 정산 전인 단위만 고를 수 있다.
 * 명단은 공개되지 않으므로(인원수만) 총무 한 사람만 넣는다 — 입구 화면은 총무·차수·금액만 쓴다.
 */
fun ServerJoinPreview.toEntryRooms(token: String): List<Gathering> =
    settlementUnits.filter { it.status == "OPEN" }.map { u ->
        Gathering(
            id = u.id, title = title, date = noonOf(date), hostUserId = -1, status = GatheringStatus.OPEN,
            shareToken = token, inputRevision = 0,
            participants = listOf(Participant(-1, -1, u.host.displayName, u.host.spoonCount)),
            rounds = u.rounds.sortedBy { it.seq }.map { Round(it.id, it.seq, "${it.seq}차", it.total, -1, emptyList()) },
            responses = emptyList(), transfers = emptyList(), timeline = emptyList(), spoonGivers = emptyList(),
        )
    }

/** 입구에서 고르는 단위 이름 — "김동규님 · 1·2차", 차수가 아직 없으면 "김동규님 · 금액 넣는 중" */
fun Gathering.entryChoiceLabel(): String {
    val who = participants.firstOrNull()?.displayName ?: "총무"
    return "${who}님 · ${if (rounds.isEmpty()) "금액 넣는 중" else rounds.joinToString("·") { "${it.seq}" } + "차"}"
}
