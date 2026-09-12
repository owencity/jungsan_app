package app.jeongsan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import app.jeongsan.data.CreatedGroup
import app.jeongsan.data.makeGathering
import app.jeongsan.data.makeGroup
import app.jeongsan.data.makeToken
import app.jeongsan.data.mockGroupDetails
import app.jeongsan.data.mockGroups
import app.jeongsan.data.mockSettlement
import app.jeongsan.domain.DrinkItem
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.GatheringStatus
import app.jeongsan.domain.GroupDetail
import app.jeongsan.domain.GroupGathering
import app.jeongsan.domain.GroupRole
import app.jeongsan.domain.GroupType
import app.jeongsan.domain.Id
import app.jeongsan.domain.JoinMode
import app.jeongsan.domain.Me
import app.jeongsan.domain.Participant
import app.jeongsan.domain.Payout
import app.jeongsan.domain.PaymentStatus
import app.jeongsan.domain.Provider
import app.jeongsan.domain.Settlement

/**
 * 앱 전역 상태. 웹의 `JeongsanApp.tsx` 가 들고 있던 `useState` 들을 그대로 옮긴 것 —
 * 라우팅 로직이 아니라 **상태**만 옮긴다. 화면 전환은 Compose Navigation 이 맡는다.
 *
 * 지금은 목업으로만 돈다. 백엔드가 붙으면 `login()`/`createGroup()` 안의 목업 로직을
 * 실제 API 호출로 바꾸되, 화면(Screen)과 네비게이션 코드는 이 클래스의 시그니처만
 * 보고 있어서 그대로 둘 수 있다.
 */
class AppStore {
    var loggedIn by mutableStateOf(false)
        private set

    var authUser by mutableStateOf<Me?>(null)
        private set

    // 목업이라도 만든 것이 실제로 남아야 흐름을 끝까지 걸어볼 수 있다.
    // 그래서 단일 값이 아니라 id 로 찾는 보관함으로 둔다.
    val groups: SnapshotStateList<app.jeongsan.domain.GroupSummary> = mockGroups.toMutableStateList()
    val groupDetails: SnapshotStateMap<Id, GroupDetail> = mutableStateMapOf<Id, GroupDetail>().apply { putAll(mockGroupDetails) }
    val gatherings: SnapshotStateMap<Id, Gathering> = mutableStateMapOf<Id, Gathering>().apply { putAll(app.jeongsan.data.mockGatherings) }

    /**
     * 정산 계산 결과. **웹도 술자리별로 따로 안 두고 전역 하나만 쓴다** — 계산은
     * 서버(core 모듈)만 하고 프론트/앱은 결과를 받아 그리기만 하므로(ADR-005),
     * 지금 목업에는 `core` 가 없어 검증된 시나리오 하나로 어느 술자리를 봐도 같은
     * 결과를 보여준다. 서버가 붙으면 "이 술자리의" 결과를 받아오도록 바뀐다.
     */
    var settlement by mutableStateOf<Settlement>(mockSettlement)
        private set

    /** mock 모드에는 백엔드가 없다 — 화면 확인용으로 상태만 켠다. */
    fun login() {
        loggedIn = true
        authUser = Me(id = app.jeongsan.data.MockIds.동규, nickname = "동규", profileImageUrl = null)
    }

    /**
     * 모임을 만든다. 목업이라도 **만든 모임이 실제로 남아야** 한다 — 그래야 목록으로
     * 돌아왔을 때 방금 만든 게 보이고 흐름이 이어진다. 서버가 붙으면 POST 응답이
     * 이 자리를 대신한다.
     *
     * @return 방금 만든 모임의 id. 화면은 이 id 로 상세 화면을 연다.
     */
    fun createGroup(
        name: String,
        groupType: GroupType,
        gatheringDate: String?,
        expectedCount: Int?,
    ): Id {
        val created: CreatedGroup = makeGroup(
            name = name,
            groupType = groupType,
            ownerName = authUser?.nickname ?: "동규",
            gatheringDate = gatheringDate,
            expectedCount = expectedCount,
        )
        groups.add(0, created.summary)
        groupDetails[created.detail.id] = created.detail
        // 번개면 술자리도 같이 생긴다(FLASH 는 딱 하나) — 보관함에 함께 넣는다.
        created.gathering?.let { gatherings[it.id] = it }
        return created.detail.id
    }

    /** 이 술자리에 내가 총무인지·멤버로 있는지 등을 판단할 때 쓰는 로그인 사용자 id. */
    fun currentUserId(): Id = authUser?.id ?: app.jeongsan.data.MockIds.동규

    fun currentUserName(): String = authUser?.nickname ?: "동규"

    /**
     * 참여자가 스스로 정산에 들어간다. **총무가 명단을 짜지 않는다** — 총무가 고르게
     * 하면 반드시 빠뜨린 사람이 생기고, 그 사람은 정산에서 누락된다.
     */
    fun selfJoinGathering(gatheringId: Id) {
        val g = gatherings[gatheringId] ?: return
        val meId = currentUserId()
        if (g.participants.any { it.userId == meId }) return // 이미 참여함
        val newParticipant = Participant(
            id = meId, userId = meId, name = currentUserName(),
            // 스스로 들어왔다. 총무가 부른 게 아니다.
            joinMode = JoinMode.SELF,
            exempt = false, responded = false,
            paymentStatus = PaymentStatus.NONE, paidAmount = null,
            isHost = false, provider = Provider.KAKAO,
        )
        gatherings[gatheringId] = g.copy(participants = g.participants + newParticipant)
    }

    /** 술자리에서 내가 이미 참여자로 들어가 있는가. */
    fun hasJoined(gatheringId: Id): Boolean {
        val g = gatherings[gatheringId] ?: return false
        return g.participants.any { it.userId == currentUserId() }
    }

    /**
     * 모임 안에 새 술자리를 연다. **지금 보고 있는 모임 안에 넣는다** — 술자리를
     * 만들고 나면 그 모임의 술자리 목록과 개수도 같이 늘어나야 한다.
     */
    fun createGathering(
        groupId: Id,
        name: String,
        date: String,
        hostName: String,
        expectedCount: Int?,
        bank: String,
        account: String,
    ): Id {
        val created = makeGathering(
            name = name, date = date, hostName = hostName, expectedCount = expectedCount,
            groupId = groupId,
            payout = Payout(bankName = bank, accountNo = account, accountHolder = hostName),
        )
        gatherings[created.id] = created

        val detail = groupDetails[groupId]
        if (detail != null) {
            groupDetails[groupId] = detail.copy(
                gatherings = listOf(GroupGathering(created.id, created.name, created.date, created.status)) + detail.gatherings,
            )
        }
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            groups[idx] = groups[idx].copy(gatheringCount = groups[idx].gatheringCount + 1)
        }
        return created.id
    }

    /**
     * 차수별 술 종류를 저장한다. 술값 합계가 곧 그 차수의 alcohol 이다 — `CALC_RULES`가
     * 정한 규칙이라 여기서 다시 계산하지 않고 합계만 넘긴다(ADR-005).
     */
    fun saveDrinkItems(gatheringId: Id, roundId: Id, items: List<DrinkItem>) {
        val g = gatherings[gatheringId] ?: return
        val alcohol = items.sumOf { it.bottleCount.toLong() * it.unitPrice }
        gatherings[gatheringId] = g.copy(
            rounds = g.rounds.map { if (it.id == roundId) it.copy(drinkItems = items, alcohol = alcohol) else it },
        )
    }

    fun confirmGathering(gatheringId: Id) {
        gatherings[gatheringId]?.let { gatherings[gatheringId] = it.copy(status = GatheringStatus.CONFIRMED) }
    }

    /** 확정을 되돌린다 — 뒤늦은 참여자를 추가로 받거나, 잘못된 입력을 고치려 할 때. */
    fun reopenGathering(gatheringId: Id) {
        gatherings[gatheringId]?.let { gatherings[gatheringId] = it.copy(status = GatheringStatus.COLLECTING) }
    }

    /** 링크를 새로 뽑는다. **기존 링크는 즉시 무효**가 돼야 하므로 토큰을 통째로 바꾼다. */
    fun reissueShareToken(gatheringId: Id) {
        gatherings[gatheringId]?.let { gatherings[gatheringId] = it.copy(shareToken = makeToken()) }
    }

    /** 링크만 알면 아무나 들어올 수 있으니, 모르는 사람을 총무가 뺄 수 있어야 한다. */
    fun removeParticipant(gatheringId: Id, participantId: Id) {
        val g = gatherings[gatheringId] ?: return
        gatherings[gatheringId] = g.copy(participants = g.participants.filter { it.id != participantId })
    }

    /**
     * "보냈다고 함" 상태를 총무가 확인해 "받았어요"로 바꾼다. 입금은 **양쪽이 눌러야**
     * 끝난다 — 총무가 안 누르면 참여자 혼자 보냈다고 우기는 꼴이 된다.
     */
    fun markPaymentReceived(gatheringId: Id, participantId: Id) {
        val g = gatherings[gatheringId] ?: return
        gatherings[gatheringId] = g.copy(
            participants = g.participants.map {
                if (it.id != participantId) return@map it
                it.copy(
                    paymentStatus = PaymentStatus.RECEIVED,
                    paidAmount = settlement.amounts[participantId] ?: it.paidAmount,
                )
            },
        )
    }
}
