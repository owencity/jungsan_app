package app.jeongsan.v3

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toDeprecatedInstant
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * v3 목데이터 — 웹 `v3/mock.ts`와 **같은 사람·같은 술자리·같은 금액**이다. 두 플랫폼이 같은
 * 목데이터를 써야 같은 화면이 나오는지 눈으로 비교할 수 있다.
 *
 * 세 술자리가 각각 다른 단계(응답 받는 중 / 송금 중 / 완료)라서 "지금 할 일"이 단계마다 어떻게
 * 바뀌는지 한 번에 볼 수 있다. 금액은 서버 계산 결과를 흉내 낸 고정값이다.
 */
object MockV3 {
    private val zone = TimeZone.currentSystemDefault()

    /** 오늘 기준 며칠 전 몇 시 */
    fun at(daysAgo: Int, hh: Int = 21, mm: Int = 0, now: Instant = Clock.System.now()): Instant {
        val date = now.toLocalDateTime(zone).date.minus(daysAgo, DateTimeUnit.DAY)
        // datetime 0.7(-0.6.x-compat) 부터 toInstant 가 kotlin.time.Instant 를 준다 — 앱 모델은 아직 kotlinx 쪽이라 바꿔 담는다
        return LocalDateTime(date, LocalTime(hh, mm)).toInstant(zone).toDeprecatedInstant()
    }

    private val myPayout = Payout("카카오뱅크", "3333-01-2345678", "김동규")
    private val minjiPayout = Payout("토스뱅크", "1000-1234-5678", "이민지")

    /**
     * 로그인한 나 — 프로 총무(1,280스푼). 목데이터에선 **첫 로그인처럼** 이름 확인(L2)이 한 번 뜨게 둔다(`needsName`) —
     * 이름을 확인하면 그 뒤로는 안 뜬다. 웹 mock.ts와 같다.
     */
    val ME = User(1, "김동규", 1_280, myPayout, needsName = true, nickname = "동규짱")

    /** 개발용 "보는 사람" 전환에 쓰는 사람들 */
    val USERS = listOf(
        ME,
        User(2, "이민지", 340, minjiPayout, nickname = "밍지🍺"),
        User(3, "박재훈", 12, nickname = "jaehoon"),
        User(4, "최지영", 0, nickname = "지영"),
        User(5, "정민수", 57),
        User(6, "한서연", 3, nickname = "🌸봄이🌸"),
    )

    private fun sys(id: Long, body: String, t: Instant) = TimelineEntry(id, TimelineType.SYSTEM, body, t)
    private fun msg(id: Long, author: Long, body: String, t: Instant) = TimelineEntry(id, TimelineType.MESSAGE, body, t, author)
    private fun res(pid: Long, rid: Long, type: ResponseType, src: ResponseSource = ResponseSource.SELF) = RoundResponse(pid, rid, type, src)

    // 참여자의 닉네임은 사람(USERS)에서 가져온다 — 서버도 users를 조인한다
    fun rooms(): List<Gathering> = listOf(openRoom(), settlingRoom(), completedRoom()).map { g ->
        g.copy(participants = g.participants.map { p -> p.copy(nickname = USERS.find { it.id == p.userId }?.nickname) })
    }

    /** ① 내가 총무, 응답 받는 중 — 두 명 미응답, 한 명은 2차 면제 */
    private fun openRoom() = Gathering(
        id = 101, title = "9/28 술자리", date = at(1), hostUserId = 1, status = GatheringStatus.OPEN,
        shareToken = "k7Qx2", inputRevision = 7,
        participants = listOf(
            Participant(11, 1, "김동규", 1_280, myPayout),
            Participant(12, 2, "이민지", 340),
            Participant(13, 3, "박재훈", 12),
            Participant(14, 4, "최지영", 0),
            Participant(15, 5, "정민수", 57),
        ),
        rounds = listOf(
            Round(1, 1, "1차", 184_000, 11, listOf(DrinkItem("소주", 5_000, 8), DrinkItem("맥주", 5_000, 6))),
            Round(2, 2, "2차", 96_000, 11, listOf(DrinkItem("하이볼", 7_000, 6))),
        ),
        responses = listOf(
            res(11, 1, ResponseType.DRANK), res(11, 2, ResponseType.DRANK),
            res(12, 1, ResponseType.DRANK), res(12, 2, ResponseType.EXEMPT, ResponseSource.HOST),
            res(13, 1, ResponseType.SOBER), res(13, 2, ResponseType.ABSENT),
        ),
        transfers = emptyList(),
        timeline = listOf(
            sys(1, "김동규님이 술자리를 만들었어요", at(1, 19, 2)),
            sys(2, "이민지님이 들어왔어요", at(1, 19, 40)),
            sys(3, "박재훈님이 응답했어요", at(1, 23, 5)),
            msg(4, 13, "2차는 먼저 들어가서 불참으로 했어요!", at(1, 23, 6)),
            sys(5, "이민지님이 응답했어요", at(0, 9, 12)),
            msg(6, 11, "이민지 생일이라 2차는 면제로 해뒀어요 🎂", at(0, 9, 20)),
        ),
        spoonGivers = emptyList(),
    )

    /** ② 이민지가 총무, 송금 중 — 나는 이민지와 박재훈(2차 결제자)에게 각각 보내야 한다 */
    private fun settlingRoom() = Gathering(
        id = 102, title = "9/25 회식", date = at(4), hostUserId = 2, status = GatheringStatus.SETTLING,
        shareToken = "Pw9mL", inputRevision = 12,
        participants = listOf(
            Participant(21, 2, "이민지", 340, minjiPayout),
            Participant(22, 1, "김동규", 1_280, myPayout),
            Participant(23, 3, "박재훈", 12),
            Participant(24, 6, "한서연", 3),
        ),
        rounds = listOf(
            Round(11, 1, "1차", 156_000, 21, listOf(DrinkItem("소주", 5_000, 10))),
            Round(12, 2, "2차", 72_000, 23, listOf(DrinkItem("맥주", 6_000, 8))),
        ),
        responses = listOf(
            res(21, 11, ResponseType.DRANK), res(21, 12, ResponseType.DRANK),
            res(22, 11, ResponseType.DRANK), res(22, 12, ResponseType.DRANK, ResponseSource.AUTO),
            res(23, 11, ResponseType.SOBER), res(23, 12, ResponseType.DRANK),
            res(24, 11, ResponseType.DRANK), res(24, 12, ResponseType.ABSENT),
        ),
        transfers = listOf(
            Transfer(1, 22, 21, 41_000, TransferStatus.WAITING, basis = listOf(TransferBasis(11, ResponseType.DRANK, 41_000))),
            Transfer(2, 22, 23, 24_000, TransferStatus.WAITING, basis = listOf(TransferBasis(12, ResponseType.DRANK, 24_000))),
            Transfer(3, 23, 21, 29_000, TransferStatus.SENT, sentAt = at(2, 11)),
            Transfer(4, 24, 21, 45_000, TransferStatus.CONFIRMED, sentAt = at(3, 10), confirmedAt = at(3, 12)),
            Transfer(5, 21, 23, 24_000, TransferStatus.WAITING),
        ),
        timeline = listOf(
            sys(1, "이민지님이 정산했어요 · 김동규님은 전 차수 참석·알코올로 자동 계산됐어요", at(3, 9)),
            msg(2, 23, "2차는 제가 냈어요! 계좌 등록할게요", at(3, 9, 30)),
            sys(3, "한서연님이 보냈어요", at(3, 10)),
            sys(4, "이민지님이 한서연님 입금을 확인했어요", at(3, 12)),
            TimelineEntry(5, TimelineType.SPOON, "한서연님이 총무에게 한 스푼 줬어요", at(3, 12, 5), 24),
            sys(6, "박재훈님이 보냈어요", at(2, 11)),
        ),
        spoonGivers = listOf(24),
    )

    /** ③ 박재훈이 총무, 완료 — 2일 전 끝나서 5일 뒤 사라진다 */
    private fun completedRoom() = Gathering(
        id = 103, title = "9/20 동기 모임", date = at(9), hostUserId = 3, status = GatheringStatus.COMPLETED,
        shareToken = "Zr3Tq", inputRevision = 5, completedAt = at(2, 14),
        participants = listOf(
            Participant(31, 3, "박재훈", 12, Payout("국민", "123-45-678901", "박재훈")),
            Participant(32, 1, "김동규", 1_280, myPayout),
            Participant(33, 2, "이민지", 340),
        ),
        rounds = listOf(Round(21, 1, "1차", 93_000, 31, emptyList())),
        responses = listOf(res(31, 21, ResponseType.DRANK), res(32, 21, ResponseType.DRANK), res(33, 21, ResponseType.SOBER)),
        transfers = listOf(
            Transfer(1, 32, 31, 34_000, TransferStatus.CONFIRMED, sentAt = at(3), confirmedAt = at(2, 13)),
            Transfer(2, 33, 31, 25_000, TransferStatus.CONFIRMED, sentAt = at(3), confirmedAt = at(2, 14)),
        ),
        timeline = listOf(sys(1, "박재훈님이 정산했어요", at(4)), sys(2, "모두 입금 완료! 🎉", at(2, 14))),
        spoonGivers = listOf(33),
    )

    /** 이미 와 있는 알림 — 목데이터 술자리들이 지금 단계까지 오며 생겼을 것들 */
    fun notifications(): List<AppNotification> = listOf(
        AppNotification(
            1, 1, 102, "정산이 나왔어요! 입금액을 확인해주세요",
            "9/25 회식 · 이민지님께 41,000원 외 1건 · 응답이 없어 전 차수 참석·알코올로 계산됐어요",
            Target.Pay(102), at(3, 9), read = false,
        ),
        AppNotification(2, 2, 102, "박재훈님이 보냈대요. 입금을 확인해주세요", "9/25 회식 · 29,000원", Target.Room(102), at(2, 11), read = false),
        AppNotification(3, 1, 103, "정산 완료! 🎉", "9/20 동기 모임 · 모두 입금했어요. 7일 뒤 사라져요", Target.Room(103), at(2, 14), read = true),
    )
}
