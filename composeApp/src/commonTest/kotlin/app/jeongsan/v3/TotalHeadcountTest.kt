package app.jeongsan.v3

import app.jeongsan.v3.api.ServerGathering
import app.jeongsan.v3.api.toRooms
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotalHeadcountTest {
    private fun gathering(answered: Int = 6, members: Int = 7): Gathering {
        val base = room(101)
        val people = (1L..members.toLong()).map { base.host().copy(id = it, userId = it, displayName = "참여자$it") }
        val rounds = listOf(6, 6, 5).mapIndexed { i, n ->
            base.rounds.first().copy(id = i + 1L, seq = i + 1, label = "${i + 1}차", payerParticipantId = 1, expectedHeadcount = n)
        }
        return base.copy(participants = people, rounds = rounds, responses = rounds.flatMap { r ->
            people.take(answered).map { p ->
                val type = if (r.id == 3L && p.id == 6L) ResponseType.ABSENT else ResponseType.SOBER
                RoundResponse(p.id, r.id, type, ResponseSource.SELF)
            }
        }, headcount = 6, totalHeadcount = 7)
    }

    private fun store(g: Gathering): V3Store {
        val users = g.participants.mapNotNull { p -> p.userId?.let { User(it, p.displayName, p.spoonCount, p.payout) } }
        return V3Store(V3State.empty().copy(me = users.first(), users = users, rooms = mapOf(g.id to g)))
    }

    @Test fun 총7명과_차수6_6_5명은_별도이며_6명응답을_완료로_보이지_않는다() {
        val g = gathering()
        assertEquals(listOf(6, 6, 5), g.rounds.map { g.roundAttendeeCount(it.id) })
        assertEquals("6 / 7명 응답", g.responseProgress())
        assertEquals("응답 받는 중 · 6 / 7명 응답", nextAction(g, 1).banner)
        assertEquals(Tone.WAIT, nextAction(g, 1).tone)
    }

    @Test fun 일곱번째_응답과_재전송은_한명으로_세며_자동확정하지_않는다() {
        val s = store(gathering())
        s.actAs(7)
        repeat(2) { s.respond(101, gathering().rounds.associate { it.id to ResponseType.ABSENT }) }
        val g = s.state.rooms.getValue(101)
        assertEquals("7 / 7명 응답", g.responseProgress())
        assertEquals(GatheringStatus.OPEN, g.status)
        assertTrue(g.transfers.isEmpty())
        assertEquals(listOf(6, 6, 5), g.rounds.map { g.roundAttendeeCount(it.id) })
        assertContains(g.totalHeadcountNotice(), "추가 응답")
    }

    @Test fun 목표보다_많이_응답하면_실제수를_보이고_응답과_명단을_남긴다() {
        val s = store(gathering(answered = 7, members = 8))
        s.actAs(8)
        s.respond(101, gathering().rounds.associate { it.id to ResponseType.DRANK })
        val g = s.state.rooms.getValue(101)
        assertEquals("8 / 7명 응답", g.responseProgress())
        assertEquals(8, g.participants.size)
        assertEquals(GatheringStatus.OPEN, g.status)
        assertContains(nextAction(g, 1).note.orEmpty(), "총 인원 7명보다 많은 8명이 응답")
        assertEquals(ActionKind.SETTLE, nextAction(g, 1).action?.kind)
    }

    @Test fun 미설정은_과거headcount나_차수인원으로_채우지_않는다() {
        assertEquals("6명 응답 · 총 인원 미설정", gathering().copy(totalHeadcount = null).responseProgress())
        val server = Json { ignoreUnknownKeys = true }.decodeFromString<ServerGathering>(Fixtures.DETAIL_B)
        val legacy = server.copy(settlementUnits = server.settlementUnits.map { it.copy(headcount = 6) })
        assertTrue(legacy.toRooms().all { it.totalHeadcount == null })
        val current = legacy.copy(settlementUnits = legacy.settlementUnits.map { it.copy(totalHeadcount = 7) })
        assertTrue(current.toRooms().all { it.headcount == 6 && it.totalHeadcount == 7 })
    }

    @Test fun 회원_비회원_동명이인은_ID별로_세고_총무는_답해야_포함된다() {
        val full = gathering(answered = 7)
        val g = full.copy(participants = full.participants.map { if (it.id > 5) it.copy(userId = null, displayName = "김동규") else it },
            responses = full.responses.filterNot { it.participantId == 1L })
        assertEquals(6, g.respondedCount())
        assertFalse(g.hasResponded(1))
        assertEquals(7, g.copy(responses = full.responses).respondedCount())
    }

    @Test fun 부분응답과_AUTO는_제출에_포함하지_않고_전불참과_총무대리는_포함한다() {
        val g = gathering(answered = 7).let { full -> full.copy(responses = full.responses.mapNotNull { r -> when {
            r.participantId == 1L && r.roundId == 3L -> null
            r.participantId == 2L -> r.copy(source = ResponseSource.AUTO)
            r.participantId == 3L -> r.copy(type = ResponseType.ABSENT)
            r.participantId == 4L -> r.copy(source = ResponseSource.HOST)
            else -> r
        } }) }
        assertEquals(5, g.respondedCount())
        assertTrue(g.hasResponded(3))
        assertTrue(g.hasResponded(4))
    }

    @Test fun 제외하면_수가_줄고_다시_참여하면_한명만_추가된다() {
        val s = store(gathering(answered = 7))
        assertNull(s.removeParticipant(101, 7))
        assertEquals(6, s.state.rooms.getValue(101).respondedCount())
        s.actAs(7)
        repeat(2) { s.joinGathering(gathering().shareToken, gathering().rounds.associate { it.id to ResponseType.SOBER }) }
        val g = s.state.rooms.getValue(101)
        assertEquals(7, g.respondedCount())
        assertEquals(7, g.participants.size)
        assertEquals(7, g.totalHeadcount)
        assertEquals(GatheringStatus.OPEN, g.status)
    }

    @Test fun 총인원수정은_총무와_OPEN만_허용하고_차수와_금액을_바꾸지_않는다() {
        val g = gathering()
        val s = store(g)
        assertNull(s.setTotalHeadcount(101, 9))
        val changed = s.state.rooms.getValue(101)
        assertEquals(g.copy(totalHeadcount = 9), changed)
        s.actAs(2)
        assertNotNull(s.setTotalHeadcount(101, 10))
        assertEquals(9, s.state.rooms.getValue(101).totalHeadcount)
        for (status in listOf(GatheringStatus.SETTLING, GatheringStatus.COMPLETED)) {
            val fixed = store(g.copy(status = status))
            assertNotNull(fixed.setTotalHeadcount(101, 8))
            assertEquals(7, fixed.state.rooms.getValue(101).totalHeadcount)
            assertFalse(g.copy(status = status).canEditTotalHeadcount(1))
        }
    }

    @Test fun 인원은_2에서50만_허용하며_범위를_강제로_맞춰저장하지_않는다() {
        val s = store(gathering())
        for (n in listOf(0, 1, 51, Int.MAX_VALUE)) assertNotNull(s.setTotalHeadcount(101, n))
        assertEquals(7, s.state.rooms.getValue(101).totalHeadcount)
        for (n in listOf(2, 50)) {
            assertNull(s.setTotalHeadcount(101, n))
            assertEquals(n, s.state.rooms.getValue(101).totalHeadcount)
        }
    }
}
