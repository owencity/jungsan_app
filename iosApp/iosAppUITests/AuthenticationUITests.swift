import XCTest

/// 공개 로그인 버튼을 그대로 누른다. 계정 입력·가입·동의·토큰 발급은 수행하지 않는다.
final class AuthenticationUITests: XCTestCase {
    private let application = XCUIApplication()
    private let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    private let safariService = XCUIApplication(bundleIdentifier: "com.apple.SafariViewService")
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    func testAppleAuthenticationPresentationAndCancel() {
        verifyAuthentication(buttonSuffix: "Apple로 계속하기", provider: "apple")
    }

    func testKakaoAuthenticationPresentationAndCancel() {
        verifyAuthentication(buttonSuffix: "카카오로 3초만에 시작하기", provider: "kakao")
    }

    private func verifyAuthentication(buttonSuffix: String, provider: String) {
        continueAfterFailure = false
        executionTimeAllowance = 150
        application.launch()
        defer { application.terminate() }
        let login = application.descendants(matching: .any)
            .matching(NSPredicate(format: "label ENDSWITH %@", buttonSuffix)).firstMatch
        XCTAssertTrue(login.waitForExistence(timeout: 20), "공개 로그인 버튼이 보여야 한다")

        // 두 번째 시도까지 검사해 취소 후 session/PKCE 상태가 남는 회귀도 잡는다.
        for attempt in 1...2 {
            login.tap()
            acceptAuthenticationPromptIfPresented()
            let presented = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in
                authenticationCancelButton() != nil || safari.state == .runningForeground
            }, object: nil)
            let result = XCTWaiter.wait(for: [presented], timeout: 30)
            capture("\(provider)-auth-\(attempt)")
            print("Authentication presentation: provider=\(provider), attempt=\(attempt), appState=\(application.state.rawValue), safariState=\(safari.state.rawValue)")
            XCTAssertEqual(result, .completed, "실제 시스템 인증 화면이 표시돼야 한다")
            XCTAssertNotEqual(safari.state, .runningForeground, "기본 Safari 앱으로 전환되면 안 된다")
            let cancel = authenticationCancelButton()
            XCTAssertNotNil(cancel, "앱 안 인증 화면에 닫을 수 있는 취소 버튼이 있어야 한다")
            cancel?.tap()
            let returned = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in
                application.state == .runningForeground && login.isEnabled && login.isHittable
                    && authenticationCancelButton() == nil
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [returned], timeout: 15), .completed,
                           "취소하면 로그인 화면에서 다시 시작할 수 있어야 한다")
            capture("\(provider)-cancel-\(attempt)")
        }
    }

    private func acceptAuthenticationPromptIfPresented() {
        let appeared = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in
            application.alerts.firstMatch.exists || springboard.alerts.firstMatch.exists
                || authenticationCancelButton() != nil || safari.state == .runningForeground
        }, object: nil)
        _ = XCTWaiter.wait(for: [appeared], timeout: 10)
        for owner in [application, springboard] {
            let alert = owner.alerts.firstMatch
            if !alert.exists { continue }
            let content = alert.staticTexts.allElementsBoundByIndex.map(\.label).joined(separator: " ")
            XCTAssertTrue(content.contains("devkdk.com"), "예상한 운영 인증 도메인의 안내만 처리한다")
            let proceed = alert.buttons.matching(NSPredicate(format: "label IN %@", ["Continue", "계속"])).firstMatch
            XCTAssertTrue(proceed.waitForExistence(timeout: 5), "시스템 인증 안내의 계속 버튼이 있어야 한다")
            proceed.tap()
            break
        }
    }

    private func authenticationCancelButton() -> XCUIElement? {
        // 웹 본문 속 취소/가짜 화면으로 통과하지 않게 실제 웹뷰와 시스템 도구 막대를 함께 검사한다.
        for owner in [application, safariService] {
            if !owner.webViews.firstMatch.exists { continue }
            let cancel = owner.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "취소"])).firstMatch
            if cancel.exists && cancel.isHittable { return cancel }
        }
        return nil
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
