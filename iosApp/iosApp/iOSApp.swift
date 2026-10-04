import SwiftUI

/// iOS 진입점. 화면은 전부 Kotlin(Compose Multiplatform)에 있다 — 여기서는 띄우기만 한다.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
