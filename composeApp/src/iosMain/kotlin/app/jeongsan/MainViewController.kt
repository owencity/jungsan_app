package app.jeongsan

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS 진입점. Xcode 프로젝트가 이 함수를 불러 화면을 띄운다.
 *
 * 지금은 macOS 가 없어 **컴파일 검증만** 한다 — CI(macOS 러너)가 맡는다.
 * 이 파일이 있어야 commonMain 에 iOS 불가 코드가 들어왔을 때 바로 잡힌다.
 */
fun MainViewController() = ComposeUIViewController { App() }
