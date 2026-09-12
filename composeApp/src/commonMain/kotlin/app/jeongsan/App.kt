package app.jeongsan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.jeongsan.domain.GroupRole
import app.jeongsan.nav.Routes
import app.jeongsan.screens.CreateGroupScreen
import app.jeongsan.screens.GroupDetailScreen
import app.jeongsan.screens.GroupHomeScreen
import app.jeongsan.screens.LoginScreen
import app.jeongsan.ui.JeongsanTheme

/**
 * 앱 진입점. **화면은 웹 목업이 명세다** — `profile/src/jeongsan` 의 화면들을 그대로
 * Compose 로 옮긴다. 무엇을 보여줄지는 거기서 이미 정해졌으므로 여기서 다시 정하지 않는다.
 *
 * 지금 옮긴 것은 핵심 루프뿐이다 — 로그인 → 모임 목록 → 모임 만들기 → 모임 상세.
 * 술자리 안(금액 입력·수집·확정·결과)과 참여자 화면·이의제기·알림은 다음 단계에서 붙인다.
 * 그래서 아래 콜백 중 일부(onOpenGathering, onNewGathering, onInvite, onSearch, onAlerts)는
 * 아직 갈 곳이 없어 비워 두었다 — 화면 자체가 없어 지금 연결해도 어차피 막힌다.
 */
@Composable
fun App() {
    JeongsanTheme {
        val store = remember { AppStore() }
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.Login) {
            composable(Routes.Login) {
                LoginScreen(
                    onLogin = {
                        store.login()
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.Home) {
                GroupHomeScreen(
                    groups = store.groups,
                    meName = store.authUser?.nickname,
                    onOpen = { id -> navController.navigate(Routes.groupDetail(id)) },
                    onCreate = { navController.navigate(Routes.NewGroup) },
                    // 모임 검색·알림함은 다음 단계에서 화면을 만들고 여기 연결한다.
                    onSearch = {},
                    onAlerts = {},
                )
            }

            composable(Routes.NewGroup) {
                CreateGroupScreen(
                    onCreate = { name, type, date, count ->
                        val id = store.createGroup(name, type, date, count)
                        // 방금 만든 모임으로 간다. "새 모임" 화면은 뒤로가기 스택에서 지운다 —
                        // 상세에서 뒤로가면 방금 지나온 입력 폼이 아니라 홈으로 가야 한다.
                        navController.navigate(Routes.groupDetail(id)) {
                            popUpTo(Routes.Home)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.GroupDetail) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val detail = id?.let { store.groupDetails[it] }
                if (detail != null) {
                    val role = store.groups.find { it.id == id }?.role
                    GroupDetailScreen(
                        group = detail,
                        isOwner = role == GroupRole.OWNER,
                        gatheringInfo = { gid ->
                            store.gatherings[gid]?.let { g -> g.hostName to store.hasJoined(gid) }
                        },
                        onSelfJoin = { gid -> store.selfJoinGathering(gid) },
                        // 술자리 상세(금액·수집·확정)와 새 술자리 만들기, 초대 화면은
                        // 다음 단계에서 붙인다.
                        onOpenGathering = {},
                        onNewGathering = {},
                        onInvite = {},
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
