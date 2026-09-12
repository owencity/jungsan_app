package app.jeongsan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.jeongsan.domain.DrinkItem
import app.jeongsan.domain.GroupRole
import app.jeongsan.nav.Routes
import app.jeongsan.screens.AmountScreen
import app.jeongsan.screens.CollectScreen
import app.jeongsan.screens.ConfirmScreen
import app.jeongsan.screens.CreateGatheringScreen
import app.jeongsan.screens.CreateGroupScreen
import app.jeongsan.screens.DrinkInputScreen
import app.jeongsan.screens.GroupDetailScreen
import app.jeongsan.screens.GroupHomeScreen
import app.jeongsan.screens.LoginScreen
import app.jeongsan.screens.ResultScreen
import app.jeongsan.screens.RosterScreen
import app.jeongsan.ui.JeongsanTheme

/**
 * 앱 진입점. **화면은 웹 목업이 명세다** — `profile/src/jeongsan` 의 화면들을 그대로
 * Compose 로 옮긴다. 무엇을 보여줄지는 거기서 이미 정해졌으므로 여기서 다시 정하지 않는다.
 *
 * 지금까지 옮긴 것 — 로그인 → 모임 목록 → 모임 만들기 → 모임 상세 → 술자리 만들기
 * → 금액 입력(+ 술 종류 입력) → 수집 현황 → 확정 → 명단 → 결과·입금.
 * 참여자 화면(모임/술자리 참여·체크·결과)·이의제기·알림은 다음 단계에서 붙인다.
 * 그래서 아래 콜백 중 일부(onInvite, onSearch, onAlerts, onShare 일부, onDispute)는
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
                val groupId = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val detail = groupId?.let { store.groupDetails[it] }
                if (detail != null) {
                    val role = store.groups.find { it.id == groupId }?.role
                    GroupDetailScreen(
                        group = detail,
                        isOwner = role == GroupRole.OWNER,
                        gatheringInfo = { gid ->
                            store.gatherings[gid]?.let { g -> g.hostName to store.hasJoined(gid) }
                        },
                        onSelfJoin = { gid -> store.selfJoinGathering(gid) },
                        // 웹과 같다 — 술자리를 열면 곧장 수집 현황으로 간다(금액 입력이 아니라).
                        // 금액 입력은 "새 술자리 만들기" 직후에만 거친다.
                        onOpenGathering = { gid -> navController.navigate(Routes.collect(gid)) },
                        onNewGathering = { navController.navigate(Routes.newGathering(detail.id)) },
                        // 초대 화면(모임 참여)은 다음 단계에서 붙인다.
                        onInvite = {},
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.NewGathering) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId")?.toLongOrNull()
                if (groupId != null) {
                    CreateGatheringScreen(
                        onNext = { input ->
                            val gid = store.createGathering(
                                groupId = groupId, name = input.name, date = input.date,
                                hostName = input.myName, expectedCount = input.expectedCount,
                                bank = input.bank, account = input.account,
                            )
                            // 방금 만든 술자리의 금액 입력으로 간다. "새 술자리" 폼은 스택에서 지운다.
                            navController.navigate(Routes.amount(gid)) {
                                popUpTo(Routes.groupDetail(groupId))
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.Amount) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                if (g != null) {
                    AmountScreen(
                        g = g,
                        onNext = {
                            navController.navigate(Routes.collect(g.id)) {
                                popUpTo(Routes.groupDetail(g.groupId ?: g.id))
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onEditDrinks = { roundId -> navController.navigate(Routes.drink(g.id, roundId)) },
                    )
                }
            }

            composable(Routes.Drink) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val roundId = backStackEntry.arguments?.getString("roundId")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                val round = g?.rounds?.find { it.id == roundId }
                if (g != null && round != null) {
                    DrinkInputScreen(
                        roundLabel = round.label,
                        total = round.total,
                        initial = round.drinkItems ?: emptyList<DrinkItem>(),
                        onSave = { items ->
                            store.saveDrinkItems(g.id, round.id, items)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.Collect) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                if (g != null) {
                    CollectScreen(
                        g = g,
                        onConfirm = { navController.navigate(Routes.confirm(g.id)) },
                        onRoster = { navController.navigate(Routes.roster(g.id)) },
                        // 링크 공유(참여자 화면 보여주기)는 다음 단계에서 붙인다.
                        onShare = {},
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.Confirm) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                if (g != null) {
                    ConfirmScreen(
                        g = g,
                        s = store.settlement,
                        onAccept = {
                            store.confirmGathering(g.id)
                            navController.navigate(Routes.result(g.id)) {
                                popUpTo(Routes.collect(g.id)) { inclusive = true }
                            }
                        },
                        onRoster = { navController.navigate(Routes.roster(g.id)) },
                        onShare = {},
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.Roster) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                if (g != null) {
                    RosterScreen(
                        g = g,
                        onBack = { navController.popBackStack() },
                        onReissue = { store.reissueShareToken(g.id) },
                        onRemove = { participantId -> store.removeParticipant(g.id, participantId) },
                    )
                }
            }

            composable(Routes.Result) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                val g = id?.let { store.gatherings[it] }
                if (g != null) {
                    ResultScreen(
                        g = g,
                        s = store.settlement,
                        // 웹도 지금은 이 값을 고정해 둔다 — 뒤늦은 참여 요청 데이터가
                        // 아직 없어서다. 백엔드가 붙으면 실제 데이터로 바뀐다.
                        joinRequest = "지원",
                        onReopen = {
                            store.reopenGathering(g.id)
                            navController.navigate(Routes.collect(g.id)) {
                                popUpTo(Routes.result(g.id)) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        // 참여자 화면(단톡 공유 대상)은 다음 단계에서 붙인다.
                        onShare = {},
                        // onRemind·onDispute 는 웹에서도 optional 이다 — 알림함·이의제기
                        // 저장소가 아직 없어 null 로 두면 화면이 그 버튼을 아예 숨긴다.
                        onRemind = null,
                        onDispute = null,
                        onMarkReceived = { participantId -> store.markPaymentReceived(g.id, participantId) },
                    )
                }
            }
        }
    }
}
