package app.jeongsan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import app.jeongsan.v3.api.platformIsIos
import app.jeongsan.v3.api.AppAuth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
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
import app.jeongsan.v3.DemoFixtures
import app.jeongsan.v3.api.AppLinks
import app.jeongsan.v3.api.AuthBrowserResult
import app.jeongsan.v3.api.platformAuthenticate
import app.jeongsan.v3.api.cancelPlatformAuthentication
import app.jeongsan.v3.ui.DemoControls
import app.jeongsan.v3.api.ApiClient
import app.jeongsan.v3.api.V3Gateway
import app.jeongsan.v3.api.apiBaseUrl
import app.jeongsan.v3.api.platformTokenStore
import app.jeongsan.v3.api.platformVerifierStore
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
    val realClient = remember {
        if (apiBaseUrl.isEmpty() || LaunchOptions.screenshot != null) null
        else ApiClient(apiBaseUrl, platformTokenStore()).also { AppAuth.store = platformVerifierStore() }
    }
    var demo by remember { mutableStateOf(AppLinks.demoRequest > 0) }
    var demoGeneration by remember { mutableStateOf(0) }
    fun enterDemo() {
        if (platformIsIos) cancelPlatformAuthentication()
        AppAuth.cancel()
        demoGeneration++
        demo = true
    }
    LaunchedEffect(AppLinks.demoRequest) {
        if (AppLinks.demoRequest > 0) enterDemo()
    }
    key(demo, demoGeneration) {
        AppSession(realClient, demo, onEnterDemo = { enterDemo() }, onResetDemo = { demoGeneration++ },
            onExitDemo = { demo = false })
    }
}

@Composable
private fun AppSession(realClient: ApiClient?, demo: Boolean, onEnterDemo: () -> Unit,
    onResetDemo: () -> Unit, onExitDemo: () -> Unit) {
    JeongsanTheme {
        // 서버 주소가 없으면 목데이터 모드 — 웹과 같은 스위치(`apiBaseUrl`)
        // 스크린샷 모드는 서버 주소가 있어도 목데이터로 찍는다(LaunchOptions)
        val shot = LaunchOptions.screenshot
        val client = if (demo) null else realClient
        val v3 = remember { V3Store(if (demo) DemoFixtures.state() else if (client == null) V3State.initial() else V3State.empty(),
            awardSpoons = !demo) }
        val gateway = remember { V3Gateway(v3, client, isDemoMode = demo) }
        val navController = rememberNavController()
        val uri = LocalUriHandler.current

        LaunchedEffect(Unit) {
            if (demo) {
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                return@LaunchedEffect
            }
            // 스크린샷 모드: 이름 확인을 끝낸 상태에서 그 화면의 보는 사람으로 바꾸고 바로 띄운다
            val target = shot?.let { SCREENSHOT_SCREENS[it] }
            if (target != null) {
                val (viewer, roomId) = target
                if (viewer == 0L) return@LaunchedEffect // 로그인 화면 그대로
                if (shot != "name") v3.confirmName("김동규") // name: 이름 확인(L2)을 띄우려고 확인 전 상태로 둔다
                if (viewer != v3.state.me.id) v3.actAs(viewer)
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                val route = when (shot) {
                    "respond" -> roomId?.let(V3Routes::respond)
                    "settle" -> roomId?.let(V3Routes::settle)
                    "pay" -> roomId?.let(V3Routes::pay)
                    "round-new" -> roomId?.let { V3Routes.round(it, null) }
                    "round-edit" -> roomId?.let { id -> V3Routes.round(id, v3.state.rooms.getValue(id).rounds.first().id) }
                    "join" -> roomId?.let { V3Routes.join(v3.state.rooms.getValue(it).shareToken) }
                    "payout" -> V3Routes.MyAccount
                    "notifications" -> V3Routes.Notifications
                    else -> roomId?.let(V3Routes::room)
                }
                if (route != null) navController.navigate(route)
                return@LaunchedEffect
            }
            // 저장된 토큰이 살아 있으면 로그인 화면을 건너뛴다(API 모드). 실명이 없으면 Home 이 L2를 띄운다
            if (gateway.loadMe()) navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
        }

        // 브라우저 로그인에서 jeongsan://auth?ticket=… 으로 돌아오면 토큰으로 바꾸고 내 술자리로(AppAuth)
        var loginError by remember { mutableStateOf<String?>(null) }
        var loginBusy by remember { mutableStateOf(false) }
        val returned = AppAuth.returned
        LaunchedEffect(returned) {
            val url = returned ?: return@LaunchedEffect
            val c = client ?: return@LaunchedEffect
            loginError = AppAuth.finish(c, url)
            if (loginError == null && !gateway.loadMe()) loginError = "로그인하지 못했어요. 다시 시도해주세요"
            // 다 끝난 뒤에 지운다 — 지우는 순간 이 효과가 다시 시작돼 진행 중인 작업이 취소되기 때문(AppAuth.finish)
            AppAuth.consumed(url)
            loginBusy = false
            if (loginError == null) navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
        }

        // 데모 바와 본문이 각각 상태바 여백을 쓰면 작은 호환 화면의 입력 공간을 두 번 줄인다.
        Column(Modifier.fillMaxSize().then(if (demo)
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier)) {
            if (demo) DemoControls(v3.state.users, v3.state.me.id,
                onPick = { userId ->
                    v3.actAs(userId)
                    navController.navigate(V3Routes.Home) { popUpTo(V3Routes.Home) { inclusive = true } }
                }, onReset = onResetDemo, onExit = onExitDemo)
            Box(Modifier.weight(1f).fillMaxSize()) {
              NavHost(navController = navController, startDestination = Routes.Login, modifier = Modifier.fillMaxSize()) {
            composable(Routes.Login) {
                LoginScreen(
                    showApple = platformIsIos && client != null,
                    error = loginError,
                    busy = loginBusy,
                    onDemo = if (shot == null) onEnterDemo else null,
                    onLogin = { provider ->
                        if (client != null && !loginBusy) {
                            // API 모드: SDK 없이 서버 OAuth 를 브라우저로 연다(FC-014 1-1) — PKCE 로 시작해 앱 스킴으로 돌아온다
                            loginError = null
                            val address = AppAuth.start(client, provider, authSession = platformIsIos)
                            if (platformIsIos) {
                                loginBusy = true
                                platformAuthenticate(address) { result ->
                                    when (result) {
                                        is AuthBrowserResult.Completed -> {
                                            if (AppAuth.ticketOf(result.url) == null) {
                                                AppAuth.cancel()
                                                loginBusy = false
                                                loginError = "로그인 주소가 올바르지 않아요. 다시 시도해주세요"
                                            } else AppAuth.handle(result.url)
                                        }
                                        AuthBrowserResult.Cancelled -> { AppAuth.cancel(); loginBusy = false }
                                        AuthBrowserResult.Failed -> {
                                            AppAuth.cancel()
                                            loginBusy = false
                                            loginError = "로그인 화면을 열지 못했어요. 다시 시도해주세요"
                                        }
                                    }
                                }
                            } else uri.openUri(address)
                        } else if (client == null) {
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
                // 내 술자리(첫 화면)의 뒤로가기·로그아웃·탈퇴 뒤 — 로그인 화면으로
                onLeave = {
                    if (demo) onExitDemo() else navController.navigate(Routes.Login) {
                        popUpTo(V3Routes.Home) { inclusive = true }
                    }
                },
            )
              }
            }
        }
    }
}
