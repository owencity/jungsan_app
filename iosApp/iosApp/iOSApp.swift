import SwiftUI
import ComposeApp

/// iOS 진입점. 화면은 전부 Kotlin(Compose Multiplatform)에 있다 — 여기서는 띄우기만 한다.
@main
struct iOSApp: App {
    @StateObject private var authenticationBrowser = AuthenticationBrowser()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    let browser = authenticationBrowser
                    IosAuthSessionBridge.shared.launcher = { [weak browser] address in
                        browser?.start(address)
                        return KotlinUnit()
                    }
                    IosAuthSessionBridge.shared.cancel = { [weak browser] in
                        browser?.cancel()
                        return KotlinUnit()
                    }
                }
                // 로그인 복귀(jeongsan://auth?ticket=…) — Kotlin 쪽 AppAuth 가 티켓을 받아 토큰으로 바꾼다
                .onOpenURL { url in AppLinks.shared.handle(url: url.absoluteString) }
        }
    }
}
