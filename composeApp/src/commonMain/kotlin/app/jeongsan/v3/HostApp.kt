package app.jeongsan.v3

import app.jeongsan.domain.Id

/** FC-030: 같은 술자리에 속해도 내가 총무인 정산 단위만 앱에서 관리한다. */
fun hostedRooms(rooms: Collection<Gathering>, userId: Id): List<Gathering> =
    rooms.filter { it.hostUserId == userId }

fun hostNotifications(
    notifications: List<AppNotification>, rooms: Collection<Gathering>, userId: Id,
): List<AppNotification> {
    val hostedIds = hostedRooms(rooms, userId).map { it.id }.toSet()
    return notifications.filter { it.userId == userId && it.roomId in hostedIds }
}

/** 예전 알림의 응답/송금 대상도 운영 앱에서는 총무 관리 화면으로만 들어간다. */
fun hostAppTarget(target: Target, rooms: Collection<Gathering>, userId: Id): Target {
    val roomId = when (target) {
        Target.Home -> return Target.Home
        is Target.Room -> target.roomId
        is Target.Respond -> target.roomId
        is Target.Pay -> target.roomId
        is Target.Account -> target.roomId
    }
    return if (hostedRooms(rooms, userId).any { it.id == roomId }) Target.Room(roomId) else Target.Home
}

/** 금액 확정과 입금 완료는 다르다. 이미지 저장은 두 상태 모두에서 가능하다. */
fun Gathering.showConfirmedImageAction(userId: Id): Boolean =
    hostUserId == userId && status != GatheringStatus.OPEN
