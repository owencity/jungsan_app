import SwiftUI
import ComposeApp

/// iOS 진입점. 화면은 전부 Kotlin(Compose Multiplatform)에 있다 — 여기서는 띄우기만 한다.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // 로그인 복귀(jeongsan://auth?ticket=…) — Kotlin 쪽 AppAuth 가 티켓을 받아 토큰으로 바꾼다
                .onOpenURL { url in AppAuth.shared.handle(url: url.absoluteString) }
        }
    }
}
