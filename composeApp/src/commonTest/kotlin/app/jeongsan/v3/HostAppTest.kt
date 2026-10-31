package app.jeongsan.v3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostAppTest {
    private val hosted = room(101).copy(hostUserId = 1, gatheringId = 10)
    private val joined = hosted.copy(id = 202, hostUserId = 2)
    private val completed = hosted.copy(id = 303, status = GatheringStatus.COMPLETED)
    private val joinedCompleted = joined.copy(id = 404, status = GatheringStatus.COMPLETED)
    private val rooms = listOf(hosted, joined, completed, joinedCompleted)

    @Test fun 같은_술자리라도_내가_총무인_단위와_완료_기록만_보인다() {
        val visible = hostedRooms(rooms, 1)
        assertEquals(listOf(101L, 303L), visible.map { it.id })
        val tabs = myRoomTabs(visible, 1)
        assertTrue(tabs.getValue(HomeTab.JOINED).isEmpty())
        assertEquals(listOf(101L), tabs.getValue(HomeTab.HOSTING).map { it.id })
        assertEquals(listOf(303L), tabs.getValue(HomeTab.DONE).map { it.id })
        assertTrue(hostedRooms(rooms, 999).isEmpty())
    }

    @Test fun 다른_단위의_참여자_알림과_다른_사용자의_알림은_숨긴다() {
        val notification = AppNotification(1, 1, hosted.id, "알림", "내용", Target.Room(hosted.id), MockV3.at(0), false)
        val notifications = listOf(notification, notification.copy(id = 2, roomId = joined.id),
            notification.copy(id = 3, userId = 2), notification.copy(id = 4, roomId = 999))
        assertEquals(listOf(1L), hostNotifications(notifications, rooms, 1).map { it.id })
    }

    @Test fun 예전_응답_송금_계좌_알림도_총무_관리_화면으로만_연다() {
        for (target in listOf(Target.Room(101), Target.Respond(101), Target.Pay(101), Target.Account(101))) {
            assertEquals(Target.Room(101), hostAppTarget(target, rooms, 1))
        }
        for (target in listOf(Target.Room(202), Target.Respond(202), Target.Pay(202), Target.Account(202), Target.Room(999), Target.Home)) {
            assertEquals(Target.Home, hostAppTarget(target, rooms, 1))
        }
    }

    @Test fun 확정된_총무_정산만_이미지_저장으로_안내한다() {
        assertFalse(hosted.showConfirmedImageAction(1))
        assertTrue(hosted.copy(status = GatheringStatus.SETTLING).showConfirmedImageAction(1))
        assertTrue(completed.showConfirmedImageAction(1))
        assertFalse(completed.showConfirmedImageAction(2))
    }
}
