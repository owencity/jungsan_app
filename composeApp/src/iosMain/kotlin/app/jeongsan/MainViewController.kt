package app.jeongsan

import androidx.compose.ui.window.ComposeUIViewController
import app.jeongsan.v3.LaunchOptions
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * iOS 진입점. Xcode 프로젝트가 이 함수를 불러 화면을 띄운다.
 *
 * 지금은 macOS 가 없어 **컴파일 검증만** 한다 — CI(macOS 러너)가 맡는다.
 * 이 파일이 있어야 commonMain 에 iOS 불가 코드가 들어왔을 때 바로 잡힌다.
 */
fun MainViewController(): UIViewController {
    // 스토어 스크린샷: xcrun simctl launch <기기> app.jeongsan -jsScreen room — 화면을 만들기 전에 한 번 읽는다
    val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
    LaunchOptions.screenshot = args.indexOf("-jsScreen").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    return ComposeUIViewController { App() }
}
