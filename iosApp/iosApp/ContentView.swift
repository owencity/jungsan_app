import SwiftUI
import UIKit
import ComposeApp

/// Kotlin 쪽 `MainViewController()`(iosMain)를 SwiftUI 화면으로 감싼다.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // 상태바·홈 인디케이터 영역은 Compose 쪽 V3Screen 이 직접 비켜 그린다(안드로이드와 같게)
        ComposeView()
            .ignoresSafeArea(.all)
            // 키보드가 올라올 때 SwiftUI 가 화면을 밀지 않게 — Compose 의 imePadding 이 처리한다
            .ignoresSafeArea(.keyboard)
    }
}
