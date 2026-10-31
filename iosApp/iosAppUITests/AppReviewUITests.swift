import XCTest

final class AppReviewUITests: XCTestCase {
    private let application = XCUIApplication()

    private func element(_ label: String) -> XCUIElement {
        application.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func expect(_ label: String) {
        XCTAssertTrue(element(label).waitForExistence(timeout: 20), "Missing visible UI: \(label)")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: application.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func expectValue(_ value: String, in field: XCUIElement) {
        let entered = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", value), object: field)
        XCTAssertEqual(XCTWaiter.wait(for: [entered], timeout: 10), .completed, "입력한 전체 이름이 반영되어야 한다")
    }

    private func dismissKeyboardIntroduction() {
        // 새 시뮬레이터의 첫 영문 입력에서 뜨는 iOS 안내를 닫는다. 앱 검증을 대신하는 처리는 아니다.
        let introduction = application.staticTexts["Speed up your typing by sliding your finger across the letters to compose a word."]
        if introduction.exists {
            application.buttons["Continue"].tap()
        }
    }

    func testPublicDemoRolesResetAndExit() {
        continueAfterFailure = false
        executionTimeAllowance = 180
        application.launch()
        XCTAssertTrue(application.descendants(matching: .any)
            .matching(NSPredicate(format: "label ENDSWITH %@", "Apple로 계속하기"))
            .firstMatch.waitForExistence(timeout: 20))
        expect("로그인 없이 데모 체험")
        capture("ios-login")
        element("로그인 없이 데모 체험").tap()
        expect("데모 체험 · 가상 데이터 · 실제 송금 없음")
        expect("정산방에서 쓸 이름")
        element("참여자A").tap()
        expect("내 술자리")
        expect("체험 · 응답 중")
        expect("체험 · 입금 확인 중")
        capture("ios-demo-role-a")
        element("참여자B").tap()
        expect("✓ 참여자B")
        expect("1차 응답을 남겨주세요")
        capture("ios-demo-role-b")
        element("처음부터").tap()
        expect("✓ 체험총무")
        expect("정산방에서 쓸 이름")
        capture("ios-demo-reset")
        element("체험 종료").tap()
        expect("로그인 없이 데모 체험")
        XCTAssertFalse(element("데모 체험 · 가상 데이터 · 실제 송금 없음").exists)
    }

    private func openShareDialog() {
        continueAfterFailure = false
        executionTimeAllowance = 180
        application.launch()
        expect("로그인 없이 데모 체험")
        element("로그인 없이 데모 체험").tap()
        expect("정산방에서 쓸 이름")
        // Compose의 단일 줄 BasicTextField도 iOS 접근성 트리에는 TextView로 노출된다.
        let name = application.textViews
            .matching(NSPredicate(format: "label BEGINSWITH %@", "정산방에서 쓸 이름"))
            .firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        name.typeText("ShareHost")
        dismissKeyboardIntroduction()
        expectValue("ShareHost", in: name)
        application.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "이용약관에 동의합니다"))
            .firstMatch.tap()
        element("이 이름으로 시작").tap()
        expect("체험 · 응답 중")
        element("체험 · 응답 중").tap()
        expect("링크 공유")
        element("링크 공유").tap()
        expect("복사되었습니다")
        expect("카카오톡 앱을 열까요?")
        capture("ios-share-copied")
    }

    private func retryEmptyName(keyboardFirst: Bool) {
        continueAfterFailure = false
        executionTimeAllowance = 180
        application.launch()
        expect("로그인 없이 데모 체험")
        element("로그인 없이 데모 체험").tap()
        let name = application.textViews
            .matching(NSPredicate(format: "label BEGINSWITH %@", "정산방에서 쓸 이름"))
            .firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        if keyboardFirst {
            name.tap()
            XCTAssertTrue(application.keyboards.firstMatch.waitForExistence(timeout: 10))
            dismissKeyboardIntroduction()
        }
        application.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "이용약관에 동의합니다"))
            .firstMatch.tap()
        element("이 이름으로 시작").tap()
        expect("이름을 넣어주세요")
        capture(keyboardFirst ? "ios-name-empty-keyboard" : "ios-name-empty")
        print("Name retry: name=\(name.frame), hittable=\(name.isHittable), keyboardVisible=\(application.keyboards.firstMatch.exists)")
        XCTAssertFalse(name.frame.intersects(element("이름을 넣어주세요").frame), "오류 문구가 이름칸을 덮지 않아야 한다")
        XCTAssertTrue(name.isHittable, "빈 이름 검증 뒤 이름칸을 다시 누를 수 있어야 한다")
        name.tap()
        XCTAssertTrue(application.keyboards.firstMatch.waitForExistence(timeout: 10))
        dismissKeyboardIntroduction()
        XCTAssertTrue(name.isHittable, "키보드가 열린 뒤에도 이름칸을 누를 수 있어야 한다")
        XCTAssertTrue(element("이름을 넣어주세요").isHittable, "입력칸 아래 오류 안내도 키보드 위에 보여야 한다")
        XCTAssertFalse(name.frame.intersects(element("이름을 넣어주세요").frame), "키보드가 열린 뒤에도 오류와 이름칸이 겹치지 않아야 한다")
        XCTAssertLessThanOrEqual(name.frame.maxY, element("이 이름으로 시작").frame.minY, "고정 시작 버튼이 이름칸을 덮지 않아야 한다")
        XCTAssertLessThanOrEqual(element("이름을 넣어주세요").frame.maxY, element("이 이름으로 시작").frame.minY, "고정 시작 버튼이 오류 안내를 덮지 않아야 한다")
        name.typeText("RetryHost")
        dismissKeyboardIntroduction()
        expectValue("RetryHost", in: name)
        XCTAssertFalse(element("이름을 넣어주세요").exists)
        capture("ios-name-corrected")
        element("이 이름으로 시작").tap()
        expect("내 술자리")
        expect("체험 · 응답 중")
    }

    func testEmptyNameCanBeCorrected() {
        retryEmptyName(keyboardFirst: false)
    }

    func testEmptyNameCanBeCorrectedWithKeyboard() {
        retryEmptyName(keyboardFirst: true)
    }

    // 실제 값 대조는 각 테스트 직후 호스트의 simctl pbpaste로 독립 수행한다.
    func testShareCancelKeepsClipboard() {
        openShareDialog()
        element("나중에").tap()
        XCTAssertFalse(element("카카오톡 앱을 열까요?").exists)
        capture("ios-share-cancelled")
    }

    func testShareUnavailableKakaoKeepsClipboard() {
        openShareDialog()
        element("카카오톡 열기").tap()
        expect("카카오톡을 열 수 없어요. 복사한 내용을 직접 붙여넣어 주세요.")
        capture("ios-share-kakao-unavailable")
    }
}
