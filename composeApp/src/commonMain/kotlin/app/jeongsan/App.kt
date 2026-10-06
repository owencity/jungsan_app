package app.jeongsan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.jeongsan.nav.Routes
import app.jeongsan.screens.LoginScreen
import app.jeongsan.ui.JeongsanTheme
import app.jeongsan.v3.LaunchOptions
import app.jeongsan.v3.SCREENSHOT_SCREENS
import app.jeongsan.v3.V3State
import app.jeongsan.v3.V3Store
import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.V3Gateway
import app.jeongsan.v3.api.apiBaseUrl
import app.jeongsan.v3.api.platformTokenStore
import app.jeongsan.v3.ui.V3Routes
import app.jeongsan.v3.ui.v3Graph

/**
 * 앱 진입점. **화면은 웹 목업이 명세다** — `profile/src/jeongsan/v3`의 화면을 Compose로 옮긴다.
 *
 * 제품 v3(일회용 술자리, 모임 없음): 로그인 → 내 술자리(H1) → 정산방(R1) → 차수(R2)·정산하기(R3)·
 * 응답(P2)·보낼 돈(P3)·알림함(N1). 화면 목록과 흐름은 `docs/SCREENS.md`.
 *
 * 옛 모임 구조 화면은 2026-10-04에 지웠다. 남은 `screens/`는 로그인 화면뿐이다.
 */
@Composable
fun App() {
    JeongsanTheme {
        // 서버 주소가 없으면 목데이터 모드 — 웹과 같은 스위치(`apiBaseUrl`)
        // 스크린샷 모드는 서버 주소가 있어도 목데이터로 찍는다(LaunchOptions)
        val shot = LaunchOptions.screenshot
        val client = remember { if (apiBaseUrl.isEmpty() || shot != null) null else ApiClient(apiBaseUrl, platformTokenStore()) }
        val v3 = remember { V3Store(if (client == null) V3State.initial() else V3State.empty()) }
        val gateway = remember { V3Gateway(v3, client) }
        val navController = rememberNavController()
        val uri = LocalUriHandler.current

        LaunchedEffect(Unit) {
            // 스크린샷 모드: 이름 확인을 끝낸 상태에서 그 화면의 보는 사람으로 바꾸고 바로 띄운다
            val target = shot?.let { SCREENSHOT_SCREENS[it] }
            if (target != null) {
                val (viewer, roomId) = target
                if (viewer == 0L) return@LaunchedEffect // 로그인 화면 그대로
                v3.confirmName("김동규")
                if (viewer != v3.state.me.id) v3.actAs(viewer)
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                if (roomId != null) navController.navigate(
                    when (shot) {
                        "respond" -> V3Routes.respond(roomId)
                        "settle" -> V3Routes.settle(roomId)
                        "pay" -> V3Routes.pay(roomId)
                        else -> V3Routes.room(roomId)
                    },
                )
                return@LaunchedEffect
            }
            // 저장된 토큰이 살아 있으면 로그인 화면을 건너뛴다(API 모드). 실명이 없으면 Home 이 L2를 띄운다
            if (gateway.loadMe()) navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
        }

        NavHost(navController = navController, startDestination = Routes.Login) {
            composable(Routes.Login) {
                LoginScreen(
                    onLogin = {
                        if (client != null) {
                            // API 모드: SDK 없이 서버 OAuth 를 브라우저로 연다(FC-014 1-1). 앱 스킴 복귀·티켓 교환은
                            // 서버 1-1 이 나오면 붙인다 — ApiClient.exchangeTicket
                            uri.openUri(client.kakaoLoginUrl())
                        } else {
                            navController.navigate(V3Routes.Home) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    },
                )
            }

            v3Graph(
                nav = navController,
                store = v3,
                gateway = gateway,
                // 내 술자리(첫 화면)의 뒤로가기 — 로그인 화면으로. 서버 로그아웃 API가 생기면 여기서 같이 부른다
                onLeave = {
                    navController.navigate(Routes.Login) {
                        popUpTo(V3Routes.Home) { inclusive = true }
                    }
                },
            )
        }
    }
}
