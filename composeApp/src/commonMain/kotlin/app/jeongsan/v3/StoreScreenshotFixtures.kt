package app.jeongsan.v3

import app.jeongsan.v3.api.ServerSettlementTable
import app.jeongsan.v3.api.TableCell
import app.jeongsan.v3.api.TableData
import app.jeongsan.v3.api.TablePerson
import app.jeongsan.v3.api.TableRound
import app.jeongsan.v3.api.TableTransfer
import kotlinx.datetime.Instant

/** 운영 계정 없이 실제 화면을 촬영하기 위한 가상 7명. 반올림 차이가 없도록 모든 몫을 100원 단위로 나눈다. */
object StoreScreenshotFixtures {
    private val now = Instant.parse("2026-11-14T09:00:00Z")

    fun state(): V3State {
        val host = User(1, "김하늘", 140)
        val names = listOf("김하늘", "이서준", "박지우", "최서연", "정도윤", "윤수빈", "한지호")
        val people = names.mapIndexed { index, name ->
            Participant(index + 1L, if (index == 0) 1L else null, name, if (index == 0) 140 else 0)
        }
        val rounds = listOf(
            Round(11, 1, "고깃집", 100_000, 1, listOf(DrinkItem("소주", 5_000, 8)), expectedHeadcount = 6),
            Round(12, 2, "호프집", 84_000, 1, listOf(DrinkItem("맥주", 6_000, 4)), expectedHeadcount = 6),
            Round(13, 3, "포장마차", 58_000, 1, listOf(DrinkItem("소주", 6_000, 3)), expectedHeadcount = 5),
        )
        val attendees = mapOf(11L to setOf(1L, 2L, 3L, 4L, 5L, 6L), 12L to setOf(1L, 2L, 3L, 4L, 5L, 7L),
            13L to setOf(1L, 2L, 3L, 4L, 7L))
        val drinkers = mapOf(11L to setOf(1L, 2L, 3L, 4L), 12L to setOf(1L, 2L, 3L, 7L), 13L to setOf(1L, 2L, 7L))
        val responses = rounds.flatMap { round ->
            people.map { person ->
                RoundResponse(person.id, round.id, when (person.id) {
                    in drinkers.getValue(round.id) -> ResponseType.DRANK
                    in attendees.getValue(round.id) -> ResponseType.SOBER
                    else -> ResponseType.ABSENT
                }, ResponseSource.SELF)
            }
        }
        fun room(id: Long, title: String, pending: Boolean) = Gathering(
            id = id, title = title, date = now, hostUserId = 1, status = GatheringStatus.OPEN,
            shareToken = "store-example-$id", inputRevision = 0, participants = people, rounds = rounds,
            responses = if (pending) responses.filterNot { it.participantId == 7L } else responses,
            transfers = emptyList(), timeline = emptyList(), spoonGivers = emptyList(), totalHeadcount = 7,
        )
        val rooms = listOf(room(101, "금요일 친구 모임", true), room(102, "친구들과 주말 모임", false),
            room(103, "동료들과 저녁", false)).associateBy { it.id }
        val store = V3Store(V3State(host, rooms, emptyList(), emptySet(), listOf(host)), clock = { now }, awardSpoons = false)
        check(store.settle(103, 0) == SettleResult.OK)
        check(store.state.rooms.getValue(101).respondedCount() == 6)
        return store.state
    }

    /** 화면·확정 송금과 같은 미리보기 결과를 표 DTO로 옮긴다. 서버나 운영 저장소에는 접근하지 않는다. */
    fun table(g: Gathering): ServerSettlementTable {
        check(g.status == GatheringStatus.SETTLING)
        val preview = mockPreview(g)
        val foodPreview = mockPreview(g.copy(rounds = g.rounds.map { it.copy(total = it.total - drinksTotal(it.drinks), drinks = emptyList()) }))
        val totals = preview.lines.associateBy { it.participantId }
        val food = foodPreview.lines.associateBy { it.participantId }
        val roundRows = g.rounds.map { round ->
            val cells = g.participants.map { person ->
                val response = checkNotNull(g.responseOf(person.id, round.id))
                val amount = totals.getValue(person.id).rounds.single { it.roundId == round.id }.amount
                val foodAmount = food.getValue(person.id).rounds.single { it.roundId == round.id }.amount
                TableCell(person.id, response.type.name, foodAmount, amount - foodAmount, amount, response.source.name)
            }
            check(cells.sumOf { it.amount } == round.total)
            TableRound(round.id, round.seq, round.payerParticipantId, round.total, round.total - drinksTotal(round.drinks),
                drinksTotal(round.drinks), round.total, cells)
        }
        val people = g.participants.map { person ->
            TablePerson(person.id, person.displayName, totals.getValue(person.id).total,
                g.rounds.filter { it.payerParticipantId == person.id }.sumOf { it.total })
        }
        val transfers = g.transfers.map { TableTransfer(it.fromParticipantId, it.toParticipantId, it.amount) }
        check(people.sumOf { it.total } == g.rounds.sumOf { it.total })
        check(transfers.sumOf { it.amount } == preview.transfers.sumOf { it.amount })
        return ServerSettlementTable(g.id, g.id, g.title, g.status.name, false, now.toString(), g.inputRevision.toLong(),
            "store-screenshot-fixture", TableData(people, roundRows, emptyList(), transfers, g.rounds.sumOf { it.total }))
    }
}
