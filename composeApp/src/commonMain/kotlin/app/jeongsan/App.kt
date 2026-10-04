package app.jeongsan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.jeongsan.nav.Routes
import app.jeongsan.screens.LoginScreen
import app.jeongsan.ui.JeongsanTheme
import app.jeongsan.v3.V3Store
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
        val v3 = remember { V3Store() }
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.Login) {
            composable(Routes.Login) {
                LoginScreen(
                    onLogin = {
                        // 목데이터 단계 — 카카오 SDK가 붙으면 여기서 로그인 결과를 받는다
                        navController.navigate(V3Routes.Home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                )
            }

            v3Graph(
                nav = navController,
                store = v3,
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
