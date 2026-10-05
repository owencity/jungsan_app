package app.jeongsan.v3.api

import app.jeongsan.v3.V3Store
import app.jeongsan.v3.validateName

/**
 * 화면이 부르는 동작의 입구 — 웹 `v3/gateway.ts`와 같다. 목데이터 모드([client]가 null)면 스토어를 바로 바꾸고,
 * API 모드면 서버를 부른 뒤 **서버 응답으로** 스토어를 채운다. 서버 PR이 들어올 때마다 동작을 하나씩 옮긴다(FC-014 §14).
 *
 * 반환값 규칙: 성공이면 null, 실패면 **사용자에게 보여줄 문구**.
 */
class V3Gateway(private val store: V3Store, private val client: ApiClient?) {
    val isApiMode get() = client != null

    /** 로그인돼 있으면 내 정보를 스토어에 넣고 true. 토큰이 없거나 401이면 false(로그인 화면). 목데이터 모드는 false */
    suspend fun loadMe(): Boolean {
        val c = client ?: return false
        return try {
            store.setMe(c.me().toUser(store.state.me))
            true
        } catch (e: ApiError) {
            if (e.status == 401) c.logout() // 만료된 토큰은 버린다 — 다음엔 로그인부터
            false
        }
    }

    /** L2·P1 실명 등록(FC-013). 서버와 같은 2~10자 규칙이라 서버까지 가기 전에 막는다 */
    suspend fun confirmName(name: String): String? {
        validateName(name).firstOrNull()?.let { return it }
        val c = client ?: run {
            store.confirmName(name)
            return null
        }
        return try {
            store.setMe(c.putDisplayName(name.trim()).toUser(store.state.me))
            null
        } catch (e: ApiError) {
            messageOf(e)
        }
    }

    companion object {
        /** 서버 오류 코드 → 화면 문구. 없는 코드는 서버 message 그대로(API.md §1.2) — 웹 gateway.ts 와 같은 표 */
        private val MESSAGES = mapOf(
            "DISPLAY_NAME_ALREADY_SET" to "이미 이름을 정했어요. 정한 이름은 바꿀 수 없어요",
            "UNAUTHENTICATED" to "로그인이 풀렸어요. 다시 로그인해주세요",
            "TOKEN_EXPIRED" to "로그인이 풀렸어요. 다시 로그인해주세요",
        )

        fun messageOf(e: ApiError): String = MESSAGES[e.code] ?: e.message ?: "잠시 뒤 다시 시도해주세요"
    }
}
