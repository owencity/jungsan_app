package app.jeongsan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import app.jeongsan.data.CreatedGroup
import app.jeongsan.data.makeGroup
import app.jeongsan.data.mockGroupDetails
import app.jeongsan.data.mockGroups
import app.jeongsan.domain.Gathering
import app.jeongsan.domain.GroupDetail
import app.jeongsan.domain.GroupRole
import app.jeongsan.domain.GroupType
import app.jeongsan.domain.Id
import app.jeongsan.domain.JoinMode
import app.jeongsan.domain.Me
import app.jeongsan.domain.Participant
import app.jeongsan.domain.PaymentStatus
import app.jeongsan.domain.Provider

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
}
