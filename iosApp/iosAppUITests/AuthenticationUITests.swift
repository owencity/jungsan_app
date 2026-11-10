import XCTest

/// 앱 이용약관에 동의하고 공개 로그인 버튼을 누른다. 제공자 계정 입력·가입·인증 완료는 수행하지 않는다.
final class AuthenticationUITests: XCTestCase {
    private let application = XCUIApplication()
    private let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
    private let appStore = XCUIApplication(bundleIdentifier: "com.apple.AppStore")
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    func testAppleAuthenticationPresentationAndCancel() {
        verifyAuthentication(buttonSuffix: "Apple로 계속하기", provider: "apple")
    }

    func testKakaoAuthenticationPresentationAndCancel() {
        // 시뮬레이터에는 카카오톡이 없다. 설치 없이 같은 카카오 계정으로 들어갈 수 있어야 한다.
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
        XCTAssertFalse(login.isEnabled, "약관에 명시적으로 동의하기 전에는 로그인을 시작할 수 없어야 한다")
        let agreement = application.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "이용약관에 동의합니다")).firstMatch
        XCTAssertTrue(agreement.waitForExistence(timeout: 10))
        agreement.tap()
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in login.isEnabled }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 10), .completed)

        // 두 번째 시도까지 검사해 취소 후 session/PKCE 상태가 남는 회귀도 잡는다.
        for attempt in 1...2 {
            login.tap()
            acceptAuthenticationPromptIfPresented()
            // 로딩 중인 도구 막대를 먼저 조회하면 iPad의 접근성 snapshot이 20초 이상
            // 멈췄다. 실제 입력란을 먼저 기다리고, 준비된 화면에서 닫기 버튼을 검사한다.
            XCTAssertTrue(application.webViews.textFields.firstMatch.waitForExistence(timeout: 30),
                          "제공자의 실제 계정 입력 화면이 앱 안에 표시돼야 한다")
            capture("\(provider)-auth-\(attempt)")
            print("Authentication presentation: provider=\(provider), attempt=\(attempt), appState=\(application.state.rawValue), safariState=\(safari.state.rawValue)")
            XCTAssertNotEqual(safari.state, .runningForeground, "기본 Safari 앱으로 전환되면 안 된다")
            XCTAssertNotEqual(appStore.state, .runningForeground, "추가 앱 설치를 요구하면 안 된다")
            XCTAssertFalse(application.alerts["카카오톡이 필요해요"].exists)
            let cancel = authenticationCancelButton()
            XCTAssertNotNil(cancel, "앱 안 인증 화면에 닫을 수 있는 취소 버튼이 있어야 한다")
            cancel?.tap()
            capture("\(provider)-after-close-\(attempt)")
            // 첫 검사의 영상은 정상 복귀했지만, 닫힌 SafariViewService의 접근성 조회가
            // 13초씩 지연돼 실패했다. 앱에 보이는 입력 가능 상태와 웹뷰 소멸을 검사한다.
            let returned = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in
                application.state == .runningForeground && login.isEnabled && login.isHittable
                    && !application.webViews.firstMatch.exists
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [returned], timeout: 15), .completed,
                           "취소하면 로그인 화면에서 다시 시작할 수 있어야 한다")
            capture("\(provider)-cancel-\(attempt)")
        }
    }

    private func acceptAuthenticationPromptIfPresented() {
        let appeared = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in
            application.alerts.firstMatch.exists || springboard.alerts.firstMatch.exists
                || application.webViews.firstMatch.exists || safari.state == .runningForeground
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
        guard application.webViews.firstMatch.exists else { return nil }
        // 입력란과 같은 앱의 계층만 조회해 SafariViewService의 늦은 snapshot을 피한다.
        let cancel = application.buttons.matching(NSPredicate(format: "label IN %@", ["Cancel", "취소", "Close", "닫기"])).firstMatch
        return cancel.exists && cancel.isHittable ? cancel : nil
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
