package app.jeongsan.v3

import app.jeongsan.domain.Id
import app.jeongsan.domain.Money
import kotlinx.datetime.Instant
import kotlin.math.ceil
import kotlin.math.max

/**
 * 정산어택 v3 앱 데이터 모델 — `docs/SCREENS.md` §8. **웹 `profile/src/jeongsan/v3/model.ts`와
 * 같은 모양**이다. 필드를 바꿀 땐 웹부터 바꾸고 여기를 맞춘다.
 *
 * ⚠ 1인당 금액은 앱이 계산하지 않는다. 송금 금액과 근거는 서버가 준다(목데이터 단계는 MockServer 흉내).
 *
 * 옛 모임 구조 모델(`app.jeongsan.domain`)과 이름이 겹치는 것이 있어 패키지를 따로 둔다.
 * 옛 화면을 다 걷어내면 그쪽을 지운다.
 */

enum class GatheringStatus { OPEN, SETTLING, COMPLETED }
enum class ResponseType { ABSENT, SOBER, DRANK, EXEMPT }

/** SELF 본인 · AUTO 정산 때 자동응답 · HOST 총무가 지정(면제·대리 응답) */
enum class ResponseSource { SELF, AUTO, HOST }
enum class TransferStatus { WAITING, SENT, CONFIRMED }

data class Payout(val bank: String, val accountNo: String, val holder: String)

data class User(
    val id: Id,
    val displayName: String,
    val spoonCount: Int,
    val payout: Payout? = null,
    /** 첫 로그인이라 실명을 아직 받지 않았다(L2). 한 번 받으면 바뀌지 않는다 */
    val needsName: Boolean = false,
    /** 카카오 닉네임. 목록에서 `이름(닉네임)`으로 같이 보여 누군지 알아보게 한다. 사용자가 입력하지 않는다 */
    val nickname: String? = null,
)

data class Participant(
    val id: Id,
    val userId: Id,
    val displayName: String,
    val spoonCount: Int,
    /** 결제자일 때 받을 계좌. 없으면 "계좌 등록을 기다리는 중" */
    val payout: Payout? = null,
    val nickname: String? = null,
)

data class DrinkItem(val name: String, val unitPrice: Money, val quantity: Int)

data class Round(
    val id: Id,
    val seq: Int,
    val label: String,
    val total: Money,
    val payerParticipantId: Id,
    val drinks: List<DrinkItem>,
)

data class RoundResponse(val participantId: Id, val roundId: Id, val type: ResponseType, val source: ResponseSource)

/** 금액 근거 — 차수별로 얼마인지(P3 "근거 펼치기"). 서버가 준다 */
data class TransferBasis(val roundId: Id, val type: ResponseType, val amount: Money)

data class Transfer(
    val id: Id,
    val fromParticipantId: Id,
    val toParticipantId: Id,
    val amount: Money,
    val status: TransferStatus,
    /** 한 번이라도 [보냈어요]가 눌리면 채워지고 지워지지 않는다 — 정산 되돌리기 가능 여부 판단용 */
    val sentAt: Instant? = null,
    val confirmedAt: Instant? = null,
    /** 마지막 [아직 안 들어왔어요] 시각 */
    val notReceivedAt: Instant? = null,
    val basis: List<TransferBasis> = emptyList(),
)

enum class TimelineType { MESSAGE, SYSTEM, SPOON }

data class TimelineEntry(
    val id: Id,
    val type: TimelineType,
    val body: String,
    val createdAt: Instant,
    val authorParticipantId: Id? = null,
)

data class Gathering(
    val id: Id,
    val title: String,
    val date: Instant,
    val hostUserId: Id,
    val status: GatheringStatus,
    val shareToken: String,
    val inputRevision: Int,
    val participants: List<Participant>,
    val rounds: List<Round>,
    val responses: List<RoundResponse>,
    /** 정산 전에는 비어 있다 */
    val transfers: List<Transfer>,
    val timeline: List<TimelineEntry>,
    /** 이 술자리에서 총무에게 스푼을 준 참여자 id */
    val spoonGivers: List<Id>,
    val completedAt: Instant? = null,
)

/** 앱 안 알림(N1). 실제 서비스에선 서버가 만들고 푸시(FCM)도 같이 보낸다 */
data class AppNotification(
    val id: Id,
    val userId: Id,
    val roomId: Id,
    val title: String,
    val body: String,
    /** 누르면 갈 곳 */
    val target: Target,
    val createdAt: Instant,
    val read: Boolean,
)

/** 알림·목록에서 "어느 화면으로 갈지". 웹은 경로 문자열(`/jungsan/r/101/pay`)이고 앱은 이 값으로 고른다 */
sealed interface Target {
    /** 내 술자리 — 명단에서 빠진 사람처럼 술자리에 더는 못 들어갈 때 */
    data object Home : Target
    data class Room(val roomId: Id) : Target
    data class Respond(val roomId: Id) : Target
    data class Pay(val roomId: Id) : Target
    data class Account(val roomId: Id) : Target
}

// ── 조회 도우미 ─────────────────────────────────

fun Gathering.host(): Participant = participants.first { it.userId == hostUserId }

fun Gathering.participantOfUser(userId: Id): Participant? = participants.find { it.userId == userId }

fun Gathering.nameOf(participantId: Id): String = participants.find { it.id == participantId }?.displayName ?: "알 수 없음"

fun Gathering.responseOf(participantId: Id, roundId: Id): RoundResponse? =
    responses.find { it.participantId == participantId && it.roundId == roundId }

/** 모든 차수에 응답이 있는가 */
fun Gathering.hasResponded(participantId: Id): Boolean =
    rounds.isNotEmpty() && rounds.all { responseOf(participantId, it.id) != null }

fun Gathering.unrespondedParticipants(): List<Participant> = participants.filter { !hasResponded(it.id) }

/** 이 참여자가 결제자인 차수 */
fun Gathering.roundsPaidBy(participantId: Id): List<Round> = rounds.filter { it.payerParticipantId == participantId }

/** 아무도 [보냈어요]·[확인]을 누른 적 없으면 정산 되돌리기가 가능하다 — `DOMAIN_DB_DESIGN_V2.md` §5.2 */
fun Gathering.canUndoSettle(): Boolean =
    status == GatheringStatus.SETTLING && transfers.all { it.sentAt == null && it.confirmedAt == null }

private const val DAY_MS = 24L * 3600 * 1000

/** 완료 후 삭제까지 남은 날 (7일 규칙) */
fun Gathering.daysUntilDelete(now: Instant): Int? {
    val done = completedAt
    if (status != GatheringStatus.COMPLETED || done == null) return null
    val left = done.toEpochMilliseconds() + 7 * DAY_MS - now.toEpochMilliseconds()
    return max(0, ceil(left.toDouble() / DAY_MS).toInt())
}

/** 응답 버튼·근거에 쓰는 이름. EXEMPT는 버튼이 아니라 총무 지정 표시로만 나온다 */
val ResponseType.label: String
    get() = when (this) {
        ResponseType.ABSENT -> "불참"
        ResponseType.SOBER -> "논알코올"
        ResponseType.DRANK -> "알코올"
        ResponseType.EXEMPT -> "면제"
    }

/** 참여자가 직접 고를 수 있는 응답 — 순서가 곧 버튼 순서 */
val SELF_CHOICES = listOf(ResponseType.ABSENT, ResponseType.SOBER, ResponseType.DRANK)

/** 총무가 면제로 지정한 칸 — 참여자는 바꿀 수 없다 */
fun RoundResponse?.isLockedByHost(): Boolean = this?.type == ResponseType.EXEMPT && source == ResponseSource.HOST
