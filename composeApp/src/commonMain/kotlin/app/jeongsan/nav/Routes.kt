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

    private const val GroupDetailPattern = "group/{id}"
    const val GroupDetail = GroupDetailPattern

    fun groupDetail(id: Id) = "group/$id"
}
