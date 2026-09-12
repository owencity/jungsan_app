package app.jeongsan.nav

import app.jeongsan.domain.Id

/**
 * 네비게이션 경로. 웹의 `route` 문자열(`/jungsan/...`)과 같은 발상이지만,
 * Compose Navigation 은 직접 경로 리터럴을 매칭하므로 타입 세이프한 래퍼로만 감싼다.
 */
object Routes {
    const val Login = "login"
    const val Home = "home"
    const val NewGroup = "group/new"

    const val GroupDetail = "group/{id}"
    fun groupDetail(id: Id) = "group/$id"

    const val NewGathering = "group/{groupId}/gathering/new"
    fun newGathering(groupId: Id) = "group/$groupId/gathering/new"

    const val Amount = "gathering/{id}/amount"
    fun amount(id: Id) = "gathering/$id/amount"

    const val Drink = "gathering/{id}/drink/{roundId}"
    fun drink(id: Id, roundId: Id) = "gathering/$id/drink/$roundId"

    const val Collect = "gathering/{id}/collect"
    fun collect(id: Id) = "gathering/$id/collect"

    const val Confirm = "gathering/{id}/confirm"
    fun confirm(id: Id) = "gathering/$id/confirm"

    const val Roster = "gathering/{id}/roster"
    fun roster(id: Id) = "gathering/$id/roster"

    const val Result = "gathering/{id}/result"
    fun result(id: Id) = "gathering/$id/result"
}
