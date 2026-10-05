package app.jeongsan.v3

import app.jeongsan.domain.Id
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** H1 내 술자리의 규칙 — docs/SCREENS.md §5 H1. 웹 `v3/home.ts`와 같다. */

/** 새 술자리 이름 — 입력받지 않고 날짜로 채운다("9/30 술자리"). 정산방 메뉴에서 바꾼다. */
fun autoTitle(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val d = at.toLocalDateTime(zone)
    return "${d.monthNumber}/${d.dayOfMonth} 술자리"
}

/** 술자리 제목 최대 길이(FC-014 §4 PATCH 규칙과 같다) */
const val MAX_TITLE = 20

/**
 * 다음 차를 다른 사람이 계산해 새로 만드는 술자리의 이름 — "9/28 술자리 다음 차". 단톡방에서 어느 자리에서
 * 이어진 정산인지 알아보게 하는 이름일 뿐, 두 술자리는 데이터로 이어지지 않는다(완전히 분리). 웹 `nextTitle`과 같다.
 */
fun nextTitle(prev: String): String {
    val s = "$prev 다음 차"
    var i = 0
    var n = 0
    // 글자(코드포인트) 단위로 자른다 — 이모지를 반으로 자르지 않게
    while (i < s.length && n < MAX_TITLE) {
        i += if (s[i].isHighSurrogate() && i + 1 < s.length) 2 else 1
        n++
    }
    return s.substring(0, i)
}

enum class HomeTab(val label: String) { HOSTING("내가 총무"), JOINED("참여 중"), DONE("완료") }

/**
 * 내가 참여한 술자리를 탭별로 나눈다. 진행 중인 방은 내 역할(총무/참여)로, 끝난 방은 역할과
 * 상관없이 [완료]로. 각 탭 안에서는 최근 날짜가 위로.
 */
fun myRoomTabs(rooms: Collection<Gathering>, userId: Id): Map<HomeTab, List<Gathering>> {
    val mine = rooms.filter { it.participantOfUser(userId) != null }.sortedByDescending { it.date }
    val active = mine.filter { it.status != GatheringStatus.COMPLETED }
    return mapOf(
        HomeTab.HOSTING to active.filter { it.hostUserId == userId },
        HomeTab.JOINED to active.filter { it.hostUserId != userId },
        HomeTab.DONE to mine.filter { it.status == GatheringStatus.COMPLETED },
    )
}

/** 이 탭에 내가 지금 손대야 하는 방이 있는가 — 탭 옆 점 표시 */
fun tabHasTodo(rooms: List<Gathering>, userId: Id): Boolean = rooms.any { nextAction(it, userId).tone == Tone.TODO }

/**
 * 처음 열 탭. 할 일이 있는 탭이 먼저(총무 → 참여 순), 없으면 방이 있는 탭, 다 비었으면 [내가 총무]
 * — 거기에 [+ 새 술자리] 안내가 있다.
 */
fun initialTab(tabs: Map<HomeTab, List<Gathering>>, userId: Id): HomeTab {
    val hosting = tabs[HomeTab.HOSTING].orEmpty()
    val joined = tabs[HomeTab.JOINED].orEmpty()
    return when {
        tabHasTodo(hosting, userId) -> HomeTab.HOSTING
        tabHasTodo(joined, userId) -> HomeTab.JOINED
        hosting.isNotEmpty() -> HomeTab.HOSTING
        joined.isNotEmpty() -> HomeTab.JOINED
        else -> HomeTab.HOSTING
    }
}

/**
 * 목록에서 방을 눌렀을 때 바로 갈 곳(CTO 결정 2026-10-01). 참여자는 정산방보다 **할 일 화면을
 * 먼저** 본다 — 응답 전이면 응답하기, 정산이 나왔는데 금액을 아직 안 봤으면 내 금액.
 * 그 밖에는 정산방. 총무는 늘 정산방(할 일 버튼이 거기 있다).
 */
fun entryTarget(g: Gathering, userId: Id, paySeen: Boolean): Target {
    val me = g.participantOfUser(userId)
    if (me == null || g.hostUserId == userId) return Target.Room(g.id)
    // 결제자인데 계좌가 없으면 무엇보다 먼저 — 그 계좌가 있어야 남이 돈을 보낼 수 있다(배너 규칙과 같음)
    if (needsAccount(g, me.id)) return Target.Account(g.id)
    if (g.status == GatheringStatus.OPEN && g.rounds.isNotEmpty() && !g.hasResponded(me.id)) return Target.Respond(g.id)
    if (g.status == GatheringStatus.SETTLING && !paySeen &&
        g.transfers.any { it.fromParticipantId == me.id && it.status != TransferStatus.CONFIRMED }
    ) {
        return Target.Pay(g.id)
    }
    return Target.Room(g.id)
}

/** 이 술자리에서 내가 결제자인데 받을 계좌가 없는가(완료된 방 제외) — nextAction의 첫 조건과 같다 */
fun needsAccount(g: Gathering, participantId: Id): Boolean =
    g.status != GatheringStatus.COMPLETED && g.roundsPaidBy(participantId).isNotEmpty() &&
        g.participants.find { it.id == participantId }?.payout == null

/** 목록 줄에 붙일 뱃지 — 내가 아직 확인 안 한 일. 없으면 null. 순서는 배너와 같다(계좌 등록 → 입금 확인 → …) */
fun rowBadge(g: Gathering, userId: Id, paySeen: Boolean): String? {
    val me = g.participantOfUser(userId) ?: return null
    val isHost = g.hostUserId == userId
    if (needsAccount(g, me.id)) return "계좌 등록"
    if (g.status == GatheringStatus.OPEN) {
        return if (!isHost && g.rounds.isNotEmpty() && !g.hasResponded(me.id)) "응답하기" else null
    }
    if (g.status != GatheringStatus.SETTLING) return null
    // 배너와 같은 순서 원칙: 남을 막고 있는 일(보냈다는 돈 확인)이 먼저 — SCREENS.md §4
    if (g.transfers.any { it.toParticipantId == me.id && it.status == TransferStatus.SENT }) return "입금 확인"
    if (!paySeen && g.transfers.any { it.fromParticipantId == me.id && it.status != TransferStatus.CONFIRMED }) return "정산금액 확인"
    return null
}
