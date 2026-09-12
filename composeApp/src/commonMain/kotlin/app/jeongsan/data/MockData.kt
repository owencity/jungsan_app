package app.jeongsan.data

import app.jeongsan.domain.Attendance
import app.jeongsan.domain.ChatMessage
import app.jeongsan.domain.Dispute
import app.jeongsan.domain.DisputeKind
import app.jeongsan.domain.DisputeStatus
import app.jeongsan.domain.DrinkItem
import app.jeongsan.domain.ExtraBreakdown
import app.jeongsan.domain.ExtraItem
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.GatheringStatus
import app.jeongsan.domain.GroupDetail
import app.jeongsan.domain.GroupGathering
import app.jeongsan.domain.GroupMember
import app.jeongsan.domain.GroupRole
import app.jeongsan.domain.GroupSearchResult
import app.jeongsan.domain.GroupSummary
import app.jeongsan.domain.GroupType
import app.jeongsan.domain.Id
import app.jeongsan.domain.JoinMode
import app.jeongsan.domain.Participant
import app.jeongsan.domain.ParticipantBreakdown
import app.jeongsan.domain.Payout
import app.jeongsan.domain.PaymentStatus
import app.jeongsan.domain.Provider
import app.jeongsan.domain.Round
import app.jeongsan.domain.RoundBreakdown
import app.jeongsan.domain.Settlement
import app.jeongsan.domain.Transfer
import app.jeongsan.domain.attKey

/**
 * 백엔드가 없는 동안 쓰는 시나리오 데이터.
 * **웹 목업(`profile/src/jeongsan/mock.ts`)과 같은 값이다** — 숫자를 다시 검증할 필요가
 * 없도록, 이미 계산 엔진으로 검증된 그 시나리오를 그대로 옮겼다.
 *
 * ⚠ 금액은 여기서 계산하지 않고 하드코딩한다(ADR-005). 웹과 동일한 원칙.
 *
 * 시나리오
 *   5명 · 1차 137,000(술 48,000) 동규 결제 / 2차 62,000(술 44,000) 민지 결제
 *   택시비 21,000 재훈 결제 (수아·지원 부담)
 *   지원 1차 논알콜·2차 불참 · 수아 2차 불참
 *   → 합계 220,000원 · 대표결제자 동규
 */
object MockIds {
    const val 동규: Id = 1
    const val 민지: Id = 2
    const val 재훈: Id = 3
    const val 수아: Id = 4
    const val 지원: Id = 5
}

private object R {
    const val 일차: Id = 1
    const val 이차: Id = 2
}

private object E {
    const val 택시비: Id = 101
}

private val participants: List<Participant> = listOf(
    Participant(
        id = MockIds.동규, userId = MockIds.동규, name = "동규", joinMode = JoinMode.INVITED,
        exempt = false, responded = true,
        paymentStatus = PaymentStatus.NONE, paidAmount = null, isHost = true, provider = Provider.KAKAO,
        payout = Payout("국민은행", "123456-78-901234", "김동규"),
    ),
    Participant(
        id = MockIds.민지, userId = MockIds.민지, name = "민지", joinMode = JoinMode.INVITED,
        exempt = false, responded = true,
        paymentStatus = PaymentStatus.NONE, paidAmount = null, isHost = false, provider = Provider.KAKAO,
        payout = Payout("신한은행", "110-234-567890", "박민지"),
    ),
    Participant(
        id = MockIds.재훈, userId = MockIds.재훈, name = "재훈", joinMode = JoinMode.SELF,
        exempt = false, responded = true,
        paymentStatus = PaymentStatus.SENT, paidAmount = 29_470, isHost = false, provider = Provider.KAKAO,
        payout = Payout("카카오뱅크", "3333-01-2345678", "이재훈"),
    ),
    Participant(
        id = MockIds.수아, userId = MockIds.수아, name = "수아", joinMode = JoinMode.INVITED,
        exempt = false, responded = true,
        paymentStatus = PaymentStatus.RECEIVED, paidAmount = 40_300, isHost = false, provider = Provider.KAKAO,
    ),
    Participant(
        id = MockIds.지원, userId = MockIds.지원, name = "지원", joinMode = JoinMode.SELF,
        exempt = false, responded = true,
        paymentStatus = PaymentStatus.NONE, paidAmount = null, isHost = false, provider = Provider.KAKAO,
    ),
)

private val attendance: Map<String, Attendance> = buildMap {
    // 1차 — 전원 참석, 지원만 논알콜
    put(attKey(MockIds.동규, R.일차), Attendance(true, true))
    put(attKey(MockIds.민지, R.일차), Attendance(true, true))
    put(attKey(MockIds.재훈, R.일차), Attendance(true, true))
    put(attKey(MockIds.수아, R.일차), Attendance(true, true))
    put(attKey(MockIds.지원, R.일차), Attendance(true, false))
    // 2차 — 동규·민지·재훈만
    put(attKey(MockIds.동규, R.이차), Attendance(true, true))
    put(attKey(MockIds.민지, R.이차), Attendance(true, true))
    put(attKey(MockIds.재훈, R.이차), Attendance(true, true))
    put(attKey(MockIds.수아, R.이차), Attendance(false, false))
    put(attKey(MockIds.지원, R.이차), Attendance(false, false))
}

val mockGathering = Gathering(
    id = 1,
    name = "8월 팀 회식",
    date = "2026-08-14",
    status = GatheringStatus.CONFIRMED,
    groupId = 100,
    // 이 술자리의 총무는 동규다. mockGatherings 를 보면 자리마다 총무가 다르다 —
    // 모임 개설자에 총무를 묶지 않는다는 걸 목데이터로 먼저 보여준다.
    hostUserId = MockIds.동규,
    hostName = "동규",
    shareToken = "k3f9dq2",
    expectedCount = 5,
    roundingUnit = 10,
    revision = 1,
    hostParticipantId = MockIds.동규,
    participants = participants,
    rounds = listOf(
        Round(id = R.일차, seq = 1, label = "1차 · 삼겹살집", total = 137_000, alcohol = 48_000, payerId = MockIds.동규),
        Round(
            id = R.이차, seq = 2, label = "2차 · 호프집", total = 62_000, alcohol = 44_000, payerId = MockIds.민지,
            drinkItems = listOf(
                DrinkItem("소주", 4, 5_000),
                DrinkItem("맥주", 4, 6_000),
            ),
        ),
    ),
    extras = listOf(
        ExtraItem(id = E.택시비, label = "택시비", amount = 21_000, payerId = MockIds.재훈, bearerIds = listOf(MockIds.수아, MockIds.지원)),
    ),
    attendance = attendance,
)

/**
 * 검증된 계산 결과. 1차 안주 89,000÷5=17,800 · 술 48,000÷4=12,000 /
 * 2차 안주 18,000÷3=6,000 · 술 44,000÷3=14,666.67(표시 14,667) / 택시 21,000÷2=10,500
 */
val mockSettlement = Settlement(
    mainPayerId = MockIds.동규,
    grandTotal = 220_000,
    amounts = mapOf(
        MockIds.동규 to 50_460L, MockIds.민지 to 50_470L, MockIds.재훈 to 50_470L,
        MockIds.수아 to 40_300L, MockIds.지원 to 28_300L,
    ),
    appliedRoundingUnit = 10,
    roundingUnitDowngraded = false,
    inputHash = "a3f2c81e",
    transfers = listOf(
        Transfer(MockIds.수아, MockIds.동규, 40_300),
        Transfer(MockIds.재훈, MockIds.동규, 29_470),
        Transfer(MockIds.지원, MockIds.동규, 16_770),
        Transfer(MockIds.지원, MockIds.민지, 11_530),
    ),
    breakdown = mapOf(
        MockIds.동규 to ParticipantBreakdown(
            participantId = MockIds.동규, name = "동규", isExempt = false, isMainPayer = true,
            rounds = listOf(
                RoundBreakdown(1, 1, "1차 · 삼겹살집", true, true, 89_000, 5, 48_000, 4, 29_800),
                RoundBreakdown(2, 2, "2차 · 호프집", true, true, 18_000, 3, 44_000, 3, 20_667),
            ),
            extras = listOf(ExtraBreakdown(E.택시비, "택시비", 21_000, 2, bears = false, share = 0)),
            roundingAdjustment = -7, finalAmount = 50_460, paidTotal = 137_000, netAmount = 86_540,
        ),
        MockIds.민지 to ParticipantBreakdown(
            participantId = MockIds.민지, name = "민지", isExempt = false, isMainPayer = false,
            rounds = listOf(
                RoundBreakdown(1, 1, "1차 · 삼겹살집", true, true, 89_000, 5, 48_000, 4, 29_800),
                RoundBreakdown(2, 2, "2차 · 호프집", true, true, 18_000, 3, 44_000, 3, 20_667),
            ),
            extras = listOf(ExtraBreakdown(E.택시비, "택시비", 21_000, 2, bears = false, share = 0)),
            roundingAdjustment = 3, finalAmount = 50_470, paidTotal = 62_000, netAmount = 11_530,
        ),
        MockIds.재훈 to ParticipantBreakdown(
            participantId = MockIds.재훈, name = "재훈", isExempt = false, isMainPayer = false,
            rounds = listOf(
                RoundBreakdown(1, 1, "1차 · 삼겹살집", true, true, 89_000, 5, 48_000, 4, 29_800),
                RoundBreakdown(2, 2, "2차 · 호프집", true, true, 18_000, 3, 44_000, 3, 20_667),
            ),
            // 재훈은 택시비를 결제했지만 부담자가 아니다 — 0원 행으로 드러낸다
            extras = listOf(ExtraBreakdown(E.택시비, "택시비", 21_000, 2, bears = false, share = 0)),
            roundingAdjustment = 3, finalAmount = 50_470, paidTotal = 21_000, netAmount = -29_470,
        ),
        MockIds.수아 to ParticipantBreakdown(
            participantId = MockIds.수아, name = "수아", isExempt = false, isMainPayer = false,
            rounds = listOf(
                RoundBreakdown(1, 1, "1차 · 삼겹살집", true, true, 89_000, 5, 48_000, 4, 29_800),
                RoundBreakdown(2, 2, "2차 · 호프집", false, false, 18_000, 3, 44_000, 3, 0),
            ),
            extras = listOf(ExtraBreakdown(E.택시비, "택시비", 21_000, 2, bears = true, share = 10_500)),
            roundingAdjustment = 0, finalAmount = 40_300, paidTotal = 0, netAmount = -40_300,
        ),
        MockIds.지원 to ParticipantBreakdown(
            participantId = MockIds.지원, name = "지원", isExempt = false, isMainPayer = false,
            rounds = listOf(
                RoundBreakdown(1, 1, "1차 · 삼겹살집", true, false, 89_000, 5, 48_000, 4, 17_800),
                RoundBreakdown(2, 2, "2차 · 호프집", false, false, 18_000, 3, 44_000, 3, 0),
            ),
            extras = listOf(ExtraBreakdown(E.택시비, "택시비", 21_000, 2, bears = true, share = 10_500)),
            roundingAdjustment = 0, finalAmount = 28_300, paidTotal = 0, netAmount = -28_300,
        ),
    ),
)

/**
 * H0 모임 목록 목업.
 *
 * 화면이 갈라 보여줄 두 경우를 **둘 다 넣는다** — 총무(OWNER)인 모임과
 * 참여자(MEMBER)로 들어가 있는 모임. 번개(FLASH)와 주기(RECURRING)도 섞어둔다.
 */
val mockGroups: List<GroupSummary> = listOf(
    GroupSummary(100, "신림팸", GroupType.RECURRING, GroupRole.OWNER, "동규", 5, 3),
    GroupSummary(101, "8월 26일 번개", GroupType.FLASH, GroupRole.OWNER, "동규", 4, 1),
    GroupSummary(102, "대학 동기 모임", GroupType.RECURRING, GroupRole.MEMBER, "태현", 8, 12),
    GroupSummary(103, "수요일 번개", GroupType.FLASH, GroupRole.MEMBER, "해린", 3, 1),
)

/**
 * 모임 상세 목업. **번개와 주기 둘 다** 넣는다 — 번개는 술자리가 1개고
 * "새 술자리" 버튼이 없어야 하는데, 주기 하나만 있으면 그 차이를 확인할 수 없다.
 */
val mockGroupDetails: MutableMap<Id, GroupDetail> = mutableMapOf(
    100L to GroupDetail(
        id = 100, name = "신림팸", groupType = GroupType.RECURRING,
        shareToken = "aB3xY9kL2mNp", hasPassword = true,
        members = listOf(
            GroupMember(1, "동규", GroupRole.OWNER),
            GroupMember(2, "민지", GroupRole.MEMBER),
            GroupMember(3, "재훈", GroupRole.MEMBER),
            GroupMember(4, "수아", GroupRole.MEMBER),
            GroupMember(5, "지원", GroupRole.MEMBER),
        ),
        gatherings = listOf(
            GroupGathering(1, "8월 팀 회식", "2026-08-14", GatheringStatus.CONFIRMED),
            GroupGathering(2, "7월 환영회", "2026-07-22", GatheringStatus.CONFIRMED),
            GroupGathering(3, "6월 첫 모임", "2026-06-15", GatheringStatus.COLLECTING),
        ),
    ),
    101L to GroupDetail(
        id = 101, name = "8월 26일 번개", groupType = GroupType.FLASH,
        shareToken = "kR7mQ2vXwZ1t", hasPassword = true,
        members = listOf(
            GroupMember(1, "동규", GroupRole.OWNER),
            GroupMember(3, "재훈", GroupRole.MEMBER),
            GroupMember(4, "수아", GroupRole.MEMBER),
            GroupMember(5, "지원", GroupRole.MEMBER),
        ),
        gatherings = listOf(GroupGathering(9, "8월 26일 번개", "2026-08-26", GatheringStatus.COLLECTING)),
    ),
    102L to GroupDetail(
        id = 102, name = "대학 동기 모임", groupType = GroupType.RECURRING,
        shareToken = null, hasPassword = false,
        members = listOf(
            GroupMember(7, "태현", GroupRole.OWNER),
            GroupMember(1, "동규", GroupRole.MEMBER),
        ),
        gatherings = listOf(GroupGathering(11, "연말 모임", "2026-12-20", GatheringStatus.COLLECTING)),
    ),
    103L to GroupDetail(
        id = 103, name = "수요일 번개", groupType = GroupType.FLASH,
        shareToken = null, hasPassword = false,
        members = listOf(
            GroupMember(8, "해린", GroupRole.OWNER),
            GroupMember(1, "동규", GroupRole.MEMBER),
        ),
        gatherings = listOf(GroupGathering(12, "수요일 번개", "2026-08-19", GatheringStatus.CONFIRMED)),
    ),
)

/** 기존 목업 술자리들. 모임 상세의 술자리를 열면 **각자 다른 데이터**가 나와야 한다. */
val mockGatherings: MutableMap<Id, Gathering> = mutableMapOf(
    1L to mockGathering,
    // 총무가 자리마다 다르다. 신림팸(100)의 세 자리를 동규·민지·재훈이 나눠 맡는다 —
    // 모임 개설자는 동규 하나지만 총무는 돌아가며 한다.
    2L to mockGathering.copy(
        id = 2, name = "7월 환영회", date = "2026-07-22",
        shareToken = "p7q2ms4", groupId = 100, hostUserId = MockIds.민지, hostName = "민지",
    ),
    3L to mockGathering.copy(
        id = 3, name = "6월 첫 모임", date = "2026-06-15",
        status = GatheringStatus.COLLECTING, shareToken = "z9w1kt6",
        groupId = 100, hostUserId = MockIds.재훈, hostName = "재훈",
    ),
    9L to mockGathering.copy(
        id = 9, name = "8월 26일 번개", date = "2026-08-26",
        status = GatheringStatus.COLLECTING, shareToken = "kR7mQ2vXwZ1t",
        groupId = 101, hostUserId = MockIds.동규, hostName = "동규",
    ),
    11L to mockGathering.copy(
        id = 11, name = "연말 모임", date = "2026-12-20",
        status = GatheringStatus.COLLECTING, shareToken = "c4v8bn2",
        groupId = 102, hostUserId = 7, hostName = "태현",
    ),
    12L to mockGathering.copy(
        id = 12, name = "수요일 번개", date = "2026-08-19",
        shareToken = "h5j3lp9", groupId = 103, hostUserId = 8, hostName = "해린",
    ),
)

/* ══ 검색으로 찾는 모임 ══════════════════════════════════════
   내가 안 속한 모임들. 이름으로 찾아 비밀번호를 넣고 들어간다. */
val mockSearchable: List<GroupSearchResult> = listOf(
    GroupSearchResult(200, "신림 볼링 모임", GroupType.RECURRING, "준호", 12, "2026-08-21"),
    GroupSearchResult(201, "신림동 맛집탐방", GroupType.RECURRING, "서연", 6, "2026-08-09"),
    GroupSearchResult(202, "신림 러닝크루", GroupType.RECURRING, "민수", 23, null),
    GroupSearchResult(203, "금요일 한잔", GroupType.FLASH, "지훈", 4, "2026-08-28"),
)

/** 검색은 서버가 한다. 목업에선 이름 부분일치로 흉내만 낸다. */
fun searchGroups(q: String): List<GroupSearchResult> {
    val k = q.trim()
    if (k.length < 2) return emptyList() // 두 글자는 받아야 검색이 의미 있다
    return mockSearchable.filter { it.name.contains(k) }
}

/* ══ 이의제기 ════════════════════════════════════════════════ */
val mockDisputes: MutableList<Dispute> = mutableListOf(
    Dispute(
        id = 500, gatheringId = 1, kind = DisputeKind.ATTENDANCE, status = DisputeStatus.OPEN,
        raisedBy = MockIds.동규, against = MockIds.지원,
        reason = "2차 불참으로 찍혀 있는데, 호프집에서 같이 나온 것 같아서요. 확인 부탁해요.",
        createdAt = "2026-08-15T21:10:00+09:00",
        messages = listOf(
            ChatMessage(5001, MockIds.동규, "동규", "지원아 2차 불참으로 돼 있는데 맞아? 나올 때 같이 나온 기억이 있어서", "2026-08-15T21:10:00+09:00"),
            ChatMessage(5002, MockIds.지원, "지원", "아 맞다 2차 잠깐 있다가 먼저 갔어요. 술은 안 마셨고요", "2026-08-15T21:14:00+09:00"),
            ChatMessage(5003, MockIds.동규, "동규", "그럼 2차 참석 + 논알콜로 고칠게. 금액 다시 뽑아서 알려줄게", "2026-08-15T21:15:00+09:00"),
        ),
    ),
    Dispute(
        id = 501, gatheringId = 1, kind = DisputeKind.AMOUNT, status = DisputeStatus.OPEN,
        raisedBy = MockIds.수아, against = MockIds.동규,
        reason = "택시비가 저한테 붙어 있는데 저는 택시 안 탔어요.",
        createdAt = "2026-08-16T10:02:00+09:00",
        messages = listOf(
            ChatMessage(5011, MockIds.수아, "수아", "택시비 21,000원 부담자에 제가 들어가 있는데 저는 지하철 탔어요", "2026-08-16T10:02:00+09:00"),
        ),
    ),
)

/* ══ 알림 ════════════════════════════════════════════════════
   입금까지 끝나야 정산이 끝난다. 그래서 금액 확정(SETTLED) 뒤에도
   PAYMENT_REMINDER 가 남아 있다. */
val mockNotifications = mutableListOf(
    app.jeongsan.domain.AppNotification(
        700, app.jeongsan.domain.NotificationKind.PAYMENT_REMINDER,
        "아직 입금이 안 됐어요", "8월 팀 회식 · 29,470원 · 동규에게 보내면 됩니다",
        "2026-08-17T09:00:00+09:00", read = false, link = "/jungsan/1/result",
    ),
    app.jeongsan.domain.AppNotification(
        701, app.jeongsan.domain.NotificationKind.DISPUTE_MESSAGE,
        "이의제기에 답이 왔어요", "동규 · \"그럼 2차 참석 + 논알콜로 고칠게\"",
        "2026-08-15T21:15:00+09:00", read = false, link = "/jungsan/dispute/500",
    ),
    app.jeongsan.domain.AppNotification(
        702, app.jeongsan.domain.NotificationKind.SETTLED,
        "정산 금액이 나왔어요", "8월 팀 회식 · 총 220,000원 · 내 몫 29,470원",
        "2026-08-15T20:40:00+09:00", read = true, link = "/jungsan/1/result",
    ),
    app.jeongsan.domain.AppNotification(
        703, app.jeongsan.domain.NotificationKind.CHECK_REQUEST,
        "차수를 체크해 주세요", "6월 첫 모임 · 재훈이 정산을 시작했습니다",
        "2026-08-14T23:30:00+09:00", read = true, link = "/g/z9w1kt6",
    ),
    app.jeongsan.domain.AppNotification(
        704, app.jeongsan.domain.NotificationKind.GATHERING_OPENED,
        "새 술자리가 열렸어요", "신림팸 · 8월 26일 번개",
        "2026-08-13T18:00:00+09:00", read = true, link = "/jungsan/group/101",
    ),
)

/* ── 목업 팩토리 ──────────────────────────────────────────────
   백엔드가 없어도 만든 것이 실제로 남아야 흐름을 끝까지 걸어볼 수 있다.
   여기 함수들이 새 모임·술자리를 만들어 주고, [app.jeongsan.AppStore] 가 그걸 상태에 넣는다.
   서버가 붙으면 이 자리를 응답이 대신한다. */

/** 목업 id 발급기. 기존 데이터와 안 겹치게 넉넉히 띄운 값에서 시작한다. */
private var seq: Id = 900

private fun nextId(): Id {
    seq += 1
    return seq
}

private val TOKEN_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** 12자 공유 토큰. 서버의 ShareToken.generate() 와 모양만 맞춘다. */
fun makeToken(): String = (1..12).map { TOKEN_ALPHABET.random() }.joinToString("")

/** 방금 만든 술자리 — 아직 아무도 안 들어왔고 차수도 없다. */
fun makeGathering(
    name: String,
    date: String,
    hostName: String,
    expectedCount: Int?,
    /** 어느 모임 안의 술자리인가. 모임 없이 열리면 null. */
    groupId: Id? = null,
    payout: Payout? = null,
): Gathering {
    val hostId = nextId()
    return Gathering(
        id = nextId(),
        name = name,
        date = date,
        status = GatheringStatus.COLLECTING,
        groupId = groupId,
        hostUserId = hostId,
        hostName = hostName,
        shareToken = makeToken(),
        expectedCount = expectedCount,
        roundingUnit = 10,
        revision = 1,
        hostParticipantId = hostId,
        // 총무 혼자 있는 상태에서 시작한다. 나머지는 링크로 들어온다.
        participants = listOf(
            Participant(
                id = hostId, userId = hostId, name = hostName, joinMode = JoinMode.INVITED,
                exempt = false, responded = true,
                paymentStatus = PaymentStatus.NONE, paidAmount = null, isHost = true, provider = Provider.KAKAO,
                payout = payout,
            ),
        ),
        rounds = emptyList(),
        extras = emptyList(),
        attendance = emptyMap(),
    )
}

data class CreatedGroup(val summary: GroupSummary, val detail: GroupDetail, val gathering: Gathering?)

/** 방금 만든 모임. 번개면 술자리 1개가 같이 생긴다(FLASH 는 딱 하나). */
fun makeGroup(
    name: String,
    groupType: GroupType,
    ownerName: String,
    gatheringDate: String? = null,
    expectedCount: Int? = null,
    /** 참가 비밀번호. 목업이라 평문으로 두지만 **서버에서는 해시로만 저장한다.** */
    password: String? = null,
): CreatedGroup {
    val id = nextId()
    val flash = groupType == GroupType.FLASH

    // 번개는 모임을 만드는 순간 술자리가 같이 만들어진다 — API.md §3-b.2 와 같은 규칙.
    val gathering = if (flash) {
        makeGathering(
            name = name,
            date = gatheringDate ?: "2026-01-01",
            hostName = ownerName,
            expectedCount = expectedCount,
            groupId = id,
        )
    } else {
        null
    }

    val detail = GroupDetail(
        id = id,
        name = name,
        groupType = groupType,
        shareToken = makeToken(),
        hasPassword = password != null,
        members = listOf(GroupMember(userId = 1, nickname = ownerName, role = GroupRole.OWNER)),
        gatherings = if (gathering != null) {
            listOf(GroupGathering(gathering.id, gathering.name, gathering.date, gathering.status))
        } else {
            emptyList()
        },
    )

    val summary = GroupSummary(
        id = id, name = name, groupType = groupType, role = GroupRole.OWNER, ownerName = ownerName,
        memberCount = 1,
        gatheringCount = if (gathering != null) 1 else 0,
    )

    return CreatedGroup(summary, detail, gathering)
}

private var disputeSeq: Id = 600

/** 이의제기를 건다. 거는 순간 채팅방이 하나 열린다. */
fun makeDispute(
    gatheringId: Id,
    kind: DisputeKind,
    raisedBy: Id,
    raisedByName: String,
    against: Id,
    reason: String,
    now: String,
): Dispute {
    disputeSeq += 1
    val disputeId = disputeSeq
    disputeSeq += 1
    val messageId = disputeSeq
    return Dispute(
        id = disputeId,
        gatheringId = gatheringId,
        kind = kind,
        status = DisputeStatus.OPEN,
        raisedBy = raisedBy,
        against = against,
        reason = reason,
        createdAt = now,
        // 이의제기 사유가 곧 첫 메시지다. 따로 또 쓰게 하면 같은 말을 두 번 시킨다.
        messages = listOf(ChatMessage(messageId, raisedBy, raisedByName, reason, now)),
    )
}
