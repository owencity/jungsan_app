package app.jeongsan.domain

import kotlinx.serialization.Serializable

/**
 * 정산어택 도메인 타입 — 웹 목업(`profile/src/jeongsan/types.ts`)을 그대로 옮긴 것.
 * **화면 이식의 명세는 저 파일이다.** 필드를 늘리거나 줄이려면 저기부터 바꾼다.
 *
 * ⚠ 앱은 금액을 계산하지 않는다. 계산 엔진(백엔드 `core` 모듈)이 유일한 계산 주체이고,
 * 앱은 [Settlement]을 받아 그린다 — 웹과 같은 이유(ADR-005)다.
 */

typealias Id = Long
typealias Money = Long

@Serializable
enum class PaymentStatus { NONE, SENT, RECEIVED }

@Serializable
enum class GatheringStatus { COLLECTING, CONFIRMED }

@Serializable
enum class Provider { KAKAO, GOOGLE }

/* ── 모임(Group) ───────────────────────────────
   용어 주의: 모임 = Group · 술자리 = Gathering. 모임 하나가 술자리 여러 개를 담는다.
   번개(FLASH)만 예외로 딱 하나를 담는다. */

@Serializable
enum class GroupType { FLASH, RECURRING }

/** 모임에서 내 역할. 총무(OWNER)와 참여자(MEMBER)를 화면에서 갈라 보여준다. */
@Serializable
enum class GroupRole { OWNER, MEMBER }

/** 로그인한 사용자. `GET /api/v1/auth/me` — 토큰에는 id 만 담기고 나머지는 DB 에서 온다. */
@Serializable
data class Me(
    val id: Id,
    val nickname: String,
    val profileImageUrl: String?,
)

/** H0 목록용 모임 요약. `GET /api/v1/groups` */
@Serializable
data class GroupSummary(
    val id: Id,
    val name: String,
    val groupType: GroupType,
    val role: GroupRole,
    /** 총무 닉네임. 참여 중인 모임에서 누가 총무인지 보여준다. */
    val ownerName: String,
    val memberCount: Int,
    val gatheringCount: Int,
)

@Serializable
data class GroupMember(
    val userId: Id,
    val nickname: String,
    val role: GroupRole,
)

/** 모임 안의 술자리 한 줄. */
@Serializable
data class GroupGathering(
    val id: Id,
    val name: String,
    val date: String,
    val status: GatheringStatus,
)

/** 모임 상세. `GET /api/v1/groups/{id}` */
@Serializable
data class GroupDetail(
    val id: Id,
    val name: String,
    val groupType: GroupType,
    val shareToken: String?,
    /**
     * 참가 비밀번호가 걸려 있나. **비밀번호 자체는 절대 내려보내지 않는다** —
     * 링크는 단톡방에 뿌려지는 순간 아무나 보고, 검색은 이름만 알면 누구나 찾는다.
     * 확인은 서버가 한다.
     */
    val hasPassword: Boolean,
    val members: List<GroupMember>,
    val gatherings: List<GroupGathering>,
)

@Serializable
data class Payout(
    val bankName: String,
    val accountNo: String,
    val accountHolder: String,
)

/** 술자리 참여 방식. 총무가 명단을 짜지 않고 **각자 들어온다**. */
@Serializable
enum class JoinMode { SELF, INVITED }

@Serializable
data class Participant(
    val id: Id,
    /** 로그인 계정. 목업에선 모임 멤버의 userId 와 맞춘다. */
    val userId: Id,
    /** 참여자가 직접 적은 이름. 계정 닉네임은 기본값일 뿐이다. */
    val name: String,
    /**
     * 어떻게 들어왔나. **총무가 명단을 짜지 않는다** — 참여자가 술자리를 열어
     * 스스로 정산에 참여한다(SELF). 총무가 굳이 고를 이유가 없고,
     * 고르게 하면 빠뜨린 사람이 생긴다.
     */
    val joinMode: JoinMode,
    val exempt: Boolean,
    val responded: Boolean,
    val paymentStatus: PaymentStatus,
    /** SENT/RECEIVED 로 표시된 시점의 부담액. 차액 계산 근거. */
    val paidAmount: Money?,
    val isHost: Boolean,
    val provider: Provider,
    val profileImage: String? = null,
    /** 결제자로 지정된 사람이 직접 등록한다. 미등록이면 null. */
    val payout: Payout? = null,
)

@Serializable
data class DrinkItem(
    val name: String,
    val bottleCount: Int,
    val unitPrice: Money,
)

@Serializable
data class Round(
    val id: Id,
    val seq: Int,
    val label: String,
    val total: Money,
    /** DrinkItem 이 있으면 그 합계로 덮어써진 값이다. */
    val alcohol: Money,
    val payerId: Id,
    val drinkItems: List<DrinkItem>? = null,
)

@Serializable
data class ExtraItem(
    val id: Id,
    val label: String,
    val amount: Money,
    val payerId: Id,
    val bearerIds: List<Id>,
)

@Serializable
data class Attendance(val attended: Boolean, val drank: Boolean)

/** `"participantId:roundId"` 키. */
fun attKey(participantId: Id, roundId: Id) = "$participantId:$roundId"

@Serializable
data class Gathering(
    val id: Id,
    val name: String,
    /** ISO date (YYYY-MM-DD) */
    val date: String,
    val status: GatheringStatus,
    /** 이 술자리를 담은 모임. 모임 없이 열린 술자리면 null. */
    val groupId: Id?,
    /**
     * **이 술자리의 총무.** 모임 개설자와 다를 수 있다 —
     * 이번엔 내가 계산하고 다음엔 네가 계산하는 게 실제 모습이다.
     */
    val hostUserId: Id,
    val hostName: String,
    val shareToken: String,
    /** 본인 포함 예상 인원. 확정 전 인원 불일치 경고에만 쓰고 계산에는 쓰지 않는다. */
    val expectedCount: Int?,
    val roundingUnit: Int,
    val revision: Int,
    val hostParticipantId: Id,
    val participants: List<Participant>,
    val rounds: List<Round>,
    val extras: List<ExtraItem>,
    val attendance: Map<String, Attendance>,
)

// ── 계산 결과 (백엔드가 준다) ──────────────────────────────

@Serializable
data class RoundBreakdown(
    val roundId: Id,
    val seq: Int,
    val label: String,
    val attended: Boolean,
    val drank: Boolean,
    val foodTotal: Money,
    val attendeeCount: Int,
    val alcoholTotal: Money,
    val drinkerCount: Int,
    /** 원 단위로 반올림한 표시용 금액. 세로합이 맞도록 서버가 계산해 내려준다. */
    val amount: Money,
)

@Serializable
data class ExtraBreakdown(
    val extraId: Id,
    val label: String,
    val amount: Money,
    val bearerCount: Int,
    /** 이 사람이 부담자인가. 결제만 하고 부담하지 않는 경우가 있다. */
    val bears: Boolean,
    /** 부담하지 않으면 0. */
    val share: Money,
)

@Serializable
data class ParticipantBreakdown(
    val participantId: Id,
    val name: String,
    val isExempt: Boolean,
    val isMainPayer: Boolean,
    val rounds: List<RoundBreakdown>,
    val extras: List<ExtraBreakdown>,
    /** 최종금액 − 표시된 항목들의 합. 0이면 화면에서 행을 숨긴다. */
    val roundingAdjustment: Money,
    /** 내가 내야 할 몫. */
    val finalAmount: Money,
    /** 내가 결제한 총액. */
    val paidTotal: Money,
    /** paidTotal − finalAmount. 양수면 받을 돈, 음수면 보낼 돈. */
    val netAmount: Money,
)

@Serializable
data class Transfer(val fromId: Id, val toId: Id, val amount: Money)

@Serializable
data class Settlement(
    val mainPayerId: Id,
    val grandTotal: Money,
    val amounts: Map<Id, Money>,
    val breakdown: Map<Id, ParticipantBreakdown>,
    val transfers: List<Transfer>,
    val appliedRoundingUnit: Int,
    /** 100원을 골랐지만 대표결제자가 음수가 되어 10원으로 강등된 경우. */
    val roundingUnitDowngraded: Boolean,
    /** 미리보기와 수락 사이의 경합을 막는 지문. ADR-004. */
    val inputHash: String,
)

/** H0 목록용 요약. */
@Serializable
data class GatheringSummary(
    val id: Id,
    val name: String,
    val date: String,
    val status: GatheringStatus,
    val grandTotal: Money,
    val participantCount: Int,
    val expectedCount: Int?,
    val respondedCount: Int,
    /** 확정 후에만 의미 있다. */
    val paidCount: Int,
    val payableCount: Int,
)

/* ══ 모임 참가 ═══════════════════════════════════════════════
   참가 방법이 둘이다 — 검색해서 들어오거나, 링크를 받아 들어온다.
   둘 다 비밀번호를 묻는다. 링크는 단톡방에 뿌려지는 순간 아무나 볼 수 있고,
   검색은 이름만 알면 누구나 찾을 수 있기 때문이다. */

/** 검색 결과 한 줄. 참가 전이라 멤버 명단은 안 보여준다. */
@Serializable
data class GroupSearchResult(
    val id: Id,
    val name: String,
    val groupType: GroupType,
    val ownerName: String,
    val memberCount: Int,
    /** 마지막 술자리 날짜. 죽은 모임인지 가늠하는 단서. */
    val lastGatheringDate: String?,
)

/* ══ 이의제기 ════════════════════════════════════════════════
   참여자가 착각하거나 양심 없이 고를 수 있어서 총무가 걸 수 있고,
   반대로 총무가 금액을 잘못 매겼을 수 있어서 참여자도 걸 수 있다.
   한쪽만 걸 수 있으면 힘의 균형이 무너진다. */

@Serializable
enum class DisputeKind {
    /** 총무 → 참여자. "1차 왔다면서 안 왔잖아" */
    ATTENDANCE,

    /** 총무 → 참여자. 입금이 안 됐거나 금액이 모자람 */
    PAYMENT,

    /** 참여자 → 총무. 나한테 매긴 금액이 이상하다 */
    AMOUNT,
}

@Serializable
enum class DisputeStatus { OPEN, RESOLVED, WITHDRAWN }

@Serializable
data class ChatMessage(
    val id: Id,
    val senderId: Id,
    val senderName: String,
    val text: String,
    val createdAt: String,
)

@Serializable
data class Dispute(
    val id: Id,
    val gatheringId: Id,
    val kind: DisputeKind,
    val status: DisputeStatus,
    /** 건 사람 */
    val raisedBy: Id,
    /** 걸린 사람 */
    val against: Id,
    val reason: String,
    val createdAt: String,
    /** 이의제기 하나에 채팅방 하나. 조율은 여기서 한다. */
    val messages: List<ChatMessage>,
)

/* ══ 알림 ════════════════════════════════════════════════════
   정산은 금액이 나왔다고 끝이 아니라 입금까지 돼야 끝난다.
   그래서 알림이 두 번 이상 간다 — 금액 확정 때 한 번, 입금이 밀리면 또 한 번. */

@Serializable
enum class NotificationKind {
    /** 모임에 새 술자리가 열림 */
    GATHERING_OPENED,

    /** 차수 체크해 달라 */
    CHECK_REQUEST,

    /** 금액이 확정됐다 */
    SETTLED,

    /** 아직 입금 안 했다 */
    PAYMENT_REMINDER,

    /** 이의제기가 걸렸다 */
    DISPUTE_OPENED,

    /** 이의제기 채팅에 새 글 */
    DISPUTE_MESSAGE,

    /** 입금이 확인됐다 */
    PAYMENT_RECEIVED,
}

@Serializable
data class AppNotification(
    val id: Id,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean,
    /** 눌렀을 때 갈 곳. 웹은 URL 경로 문자열이지만, 앱은 [app.jeongsan.nav.Route] 로 바꿔 쓴다. */
    val link: String,
)
