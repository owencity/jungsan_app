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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import app.jeongsan.v3.api.KakaoTalkResult
import app.jeongsan.v3.api.NativeKakaoSession
import app.jeongsan.v3.api.platformKakaoTalkLogin
import app.jeongsan.v3.api.cancelPlatformKakaoTalkLogin
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
import app.jeongsan.v3.api.platformTermsConsent
import app.jeongsan.v3.ui.TermsConsentScreen
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
    if (app.jeongsan.v3.isDebugBuild && LaunchOptions.screenshot == "settlement-image") {
        JeongsanTheme { app.jeongsan.v3.ui.SettlementImagePreview() }
        return
    }
    val realClient = remember {
        if (apiBaseUrl.isEmpty() || LaunchOptions.screenshot != null) null
        else ApiClient(apiBaseUrl, platformTokenStore()).also { AppAuth.store = platformVerifierStore() }
    }
    var demo by remember { mutableStateOf(AppLinks.demoRequest > 0) }
    var demoGeneration by remember { mutableStateOf(0) }
    fun enterDemo() {
        if (platformIsIos) cancelPlatformAuthentication()
        cancelPlatformKakaoTalkLogin()
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
        val v3 = remember { V3Store(if (LaunchOptions.isStoreScreenshot) app.jeongsan.v3.StoreScreenshotFixtures.state()
            else if (demo) DemoFixtures.state() else if (client == null) V3State.initial() else V3State.empty(),
            awardSpoons = !demo) }
        val gateway = remember { V3Gateway(v3, client, isDemoMode = demo) }
        val navController = rememberNavController()
        val uri = LocalUriHandler.current
        val terms = remember(client) { if (client != null) platformTermsConsent() else null }
        var consentRevision by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            if (demo) {
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                return@LaunchedEffect
            }
            // 스크린샷 모드: 이름 확인을 끝낸 상태에서 그 화면의 보는 사람으로 바꾸고 바로 띄운다
            val target = shot?.let { if (LaunchOptions.isStoreScreenshot) app.jeongsan.v3.STORE_SCREENSHOT_SCREENS[it] else SCREENSHOT_SCREENS[it] }
            if (target != null) {
                val (viewer, roomId) = target
                if (viewer == 0L) return@LaunchedEffect // 로그인 화면 그대로
                if (shot != "name" && !LaunchOptions.isStoreScreenshot) v3.confirmName("김동규") // name: 이름 확인(L2)을 띄우려고 확인 전 상태로 둔다
                if (viewer != v3.state.me.id) v3.actAs(viewer)
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                val route = when (shot) {
                    "store-settle" -> roomId?.let(V3Routes::settle)
                    "store-table" -> roomId?.let(V3Routes::table)
                    "store-round" -> roomId?.let { id -> V3Routes.round(id, v3.state.rooms.getValue(id).rounds.first().id) }
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
        var nativeBusy by remember { mutableStateOf(false) }
        var nativeJob by remember { mutableStateOf<Job?>(null) }
        var loginTermsAccepted by remember { mutableStateOf(false) }
        val loginScope = rememberCoroutineScope()
        val nativeSession = remember(client) { client?.let(::NativeKakaoSession) }

        fun recordLoginConsent() {
            // 체크 시점에는 계정을 모른다. 인증 뒤 /auth/me로 확인한 계정에만 동의를 기록한다.
            if (loginTermsAccepted) {
                terms?.accept(v3.state.me.id)
                consentRevision++
            }
            loginTermsAccepted = false
        }

        fun browserLogin(provider: AppAuth.Provider) {
            if (loginBusy) return
            val c = client ?: run {
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                return
            }
            loginError = null
            val address = AppAuth.start(c, provider, authSession = platformIsIos)
            if (platformIsIos) {
                loginBusy = true
                platformAuthenticate(address) { result ->
                    when (result) {
                        is AuthBrowserResult.Completed -> {
                            if (AppAuth.ticketOf(result.url) == null) {
                                AppAuth.cancel(); loginBusy = false; loginTermsAccepted = false
                                loginError = "로그인 주소가 올바르지 않아요. 다시 시도해주세요"
                            } else AppAuth.handle(result.url)
                        }
                        AuthBrowserResult.Cancelled -> { AppAuth.cancel(); loginBusy = false; loginTermsAccepted = false }
                        AuthBrowserResult.Failed -> {
                            AppAuth.cancel(); loginBusy = false; loginTermsAccepted = false
                            loginError = "로그인 화면을 열지 못했어요. 다시 시도해주세요"
                        }
                    }
                }
            } else uri.openUri(address)
        }

        fun startLogin(provider: AppAuth.Provider) {
            if (loginBusy) return
            // LoginScreen은 이용약관을 체크해야 이 콜백을 호출할 수 있다.
            loginTermsAccepted = true
            if (!platformIsIos || provider != AppAuth.Provider.KAKAO || client == null) {
                browserLogin(provider); return
            }
            val session = nativeSession ?: return
            val attempt = session.start()
            AppAuth.cancel()
            loginError = null
            loginBusy = true
            nativeBusy = true
            platformKakaoTalkLogin { result ->
                if (!session.isCurrent(attempt)) return@platformKakaoTalkLogin
                when (result) {
                    is KakaoTalkResult.Token -> nativeJob = loginScope.launch {
                        try {
                            if (!session.finish(attempt, result.accessToken)) return@launch
                            val loaded = gateway.loadMe()
                            if (!session.isCurrent(attempt)) return@launch
                            if (!loaded) {
                                client.logout()
                                loginError = "로그인하지 못했어요. 다시 시도해주세요"
                            } else if (session.isCurrent(attempt)) {
                                recordLoginConsent()
                                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            if (session.isCurrent(attempt)) loginError = "카카오톡 로그인을 완료하지 못했어요. 다시 시도해 주세요."
                        }
                        finally {
                            if (session.isCurrent(attempt)) { loginBusy = false; nativeBusy = false; loginTermsAccepted = false }
                        }
                    }
                    KakaoTalkResult.UseAccount -> {
                        session.cancel(); nativeBusy = false; loginBusy = false
                        browserLogin(AppAuth.Provider.KAKAO)
                    }
                    else -> {
                        session.cancel(); nativeBusy = false; loginBusy = false
                        loginTermsAccepted = false
                        loginError = when (result) {
                            KakaoTalkResult.Cancelled -> null
                            KakaoTalkResult.NotConfigured -> "카카오 로그인을 사용할 수 없어요. Apple 로그인으로 계속해 주세요."
                            else -> "카카오톡 로그인을 완료하지 못했어요. 다시 시도해 주세요."
                        }
                    }
                }
            }
        }

        fun cancelNativeLogin() {
            nativeSession?.cancel()
            nativeJob?.cancel()
            cancelPlatformKakaoTalkLogin()
            if (nativeBusy) client?.logout()
            nativeBusy = false; loginBusy = false; loginTermsAccepted = false
        }

        DisposableEffect(client) {
            onDispose {
                nativeSession?.cancel(); nativeJob?.cancel(); cancelPlatformKakaoTalkLogin()
                if (nativeBusy) client?.logout()
            }
        }
        val returned = AppAuth.returned
        LaunchedEffect(returned) {
            val url = returned ?: return@LaunchedEffect
            val c = client ?: return@LaunchedEffect
            loginError = AppAuth.finish(c, url)
            if (loginError == null && !gateway.loadMe()) loginError = "로그인하지 못했어요. 다시 시도해주세요"
            if (loginError == null) recordLoginConsent() else loginTermsAccepted = false
            // 다 끝난 뒤에 지운다 — 지우는 순간 이 효과가 다시 시작돼 진행 중인 작업이 취소되기 때문(AppAuth.finish)
            AppAuth.consumed(url)
            loginBusy = false
            if (loginError == null) navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
        }

        val currentUserId = v3.state.me.id
        val needsTerms = remember(terms, currentUserId, consentRevision) {
            terms != null && currentUserId > 0 && !terms.accepted(currentUserId)
        }
        if (needsTerms) {
            // 모든 실제 화면 앞에서 확인한다. 저장 토큰·인증 딥링크도 이름 화면을 우회해 들어올 수 있다.
            TermsConsentScreen(currentUserId, onAgree = {
                terms?.accept(currentUserId)
                consentRevision++
                navController.navigate(V3Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
            }, onLogout = {
                gateway.logout()
                navController.navigate(Routes.Login) { popUpTo(V3Routes.Home) { inclusive = true } }
            })
            return@JeongsanTheme
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
                    onLogin = ::startLogin,
                    onCancelLogin = if (nativeBusy) ({ cancelNativeLogin() }) else null,
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
