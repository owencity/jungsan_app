package app.jeongsan.v3

import app.jeongsan.v3.api.ServerGathering
import app.jeongsan.v3.api.toRooms
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestParticipantTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun 비회원이_있는_서버_JSON을_읽어_회원_총무와_참여자_송금을_보존한다() {
        val response = Fixtures.DETAIL_B.replace("\"userId\":3", "\"userId\":null")
        val room = json.decodeFromString<ServerGathering>(response).toRooms().first()
        val guest = room.participants.first { it.id == 3L }
        assertNull(guest.userId)
        assertEquals("박재훈", guest.displayName)
        assertEquals(1L, room.host().userId)
        assertEquals(93334L, room.transfers.single { it.fromParticipantId == guest.id }.amount)
        assertTrue(room.responses.any { it.participantId == guest.id })
    }

    @Test fun 동명이인_비회원도_참여자_ID로_구분하고_회원_알림을_만들지_않는다() {
        val original = json.decodeFromString<ServerGathering>(Fixtures.DETAIL_B).toRooms().first()
        val room = original.copy(participants = original.participants.map { participant ->
            if (participant.id == 1L) participant else participant.copy(userId = null, displayName = "김하나")
        })
        assertEquals(listOf(2L, 3L), room.participants.filter { it.userId == null }.map { it.id })
        assertNull(room.participantOfUser(2L))
        val completed = room.copy(status = GatheringStatus.COMPLETED)
        val notifications = notificationsFor(room, completed)
        assertEquals(listOf(1L), notifications.map { it.userId })
    }
}
