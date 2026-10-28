package app.jeongsan.v3

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** 실제 계정·계좌·API를 사용하지 않는 공개 체험 데이터. 상태 전이는 기존 스토어를 그대로 거친다. */
object DemoFixtures {
    fun state(now: Instant = Clock.System.now()): V3State {
        val users = listOf(
            User(1, "체험총무", 0, Payout("카카오뱅크", "0000000000", "체험총무"), nickname = "총무"),
            User(2, "참여자A", 0, nickname = "참여자A"),
            User(3, "참여자B", 0, nickname = "참여자B"),
        )
        fun room(id: Long, title: String, automatic: Boolean): Gathering {
            val participants = users.map { user ->
                Participant(id * 10 + user.id, user.id, user.displayName, 0, user.payout, user.nickname)
            }
            val roundId = id * 10
            return Gathering(
                id = id, title = title, date = now, hostUserId = 1, status = GatheringStatus.OPEN,
                shareToken = "demo$id", inputRevision = 0, participants = participants,
                rounds = listOf(Round(roundId, 1, "1차", 30_000, participants[0].id, emptyList())),
                responses = participants.take(2).map { RoundResponse(it.id, roundId, ResponseType.SOBER, ResponseSource.SELF) },
                transfers = emptyList(), spoonGivers = emptyList(), headcount = if (automatic) 3 else null,
                timeline = listOf(
                    TimelineEntry(1, TimelineType.SYSTEM, "체험용 술자리를 만들었어요", now),
                    TimelineEntry(2, TimelineType.MESSAGE, "참여자 역할을 바꿔 응답과 정산을 체험해보세요", now, participants[1].id),
                    TimelineEntry(3, TimelineType.MESSAGE, "이 연습 메시지로 신고와 차단을 확인할 수 있어요", now, participants[2].id),
                ),
            )
        }
        val rooms = listOf(room(101, "체험 · 응답 중", true), room(102, "체험 · 입금 확인 중", false),
            room(103, "체험 · 정산 완료", false)).associateBy { it.id }
        val store = V3Store(V3State(users[0], rooms, emptyList(), emptySet(), users), clock = { now }, awardSpoons = false)
        store.settle(102, 0)
        store.settle(103, 0)
        store.state.rooms.getValue(103).transfers.forEach { transfer ->
            val sender = store.state.rooms.getValue(103).participants.first { it.id == transfer.fromParticipantId }
            store.actAs(sender.userId)
            store.markSent(103, transfer.id)
            store.actAs(1)
            store.confirmIncoming(103, transfer.id)
        }
        return store.state.copy(me = users[0].copy(needsName = true),
            users = users.map { if (it.id == 1L) it.copy(needsName = true) else it })
    }
}
