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
}
