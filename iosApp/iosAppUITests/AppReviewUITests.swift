import XCTest
import UIKit

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

    // CI의 카카오톡 미설치 시뮬레이터에서 복사·취소·실행 실패까지 실제 화면으로 검증한다.
    func testShareCopiesBeforeOfferingKakaoAndKeepsClipboard() {
        continueAfterFailure = false
        executionTimeAllowance = 180
        application.launch()
        expect("로그인 없이 데모 체험")
        element("로그인 없이 데모 체험").tap()
        expect("정산방에서 쓸 이름")
        let name = application.textFields.firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        name.typeText("ShareHost")
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
        let copied = UIPasteboard.general.string
        XCTAssertTrue(copied?.contains("https://jungsan-api.devkdk.com/api/v1/review/demo") == true)
        XCTAssertFalse(copied?.contains("/jungsan/j/demo") == true)
        capture("ios-share-copied")
        element("나중에").tap()
        XCTAssertFalse(element("카카오톡 앱을 열까요?").exists)
        XCTAssertEqual(UIPasteboard.general.string, copied)
        element("링크 공유").tap()
        expect("카카오톡 앱을 열까요?")
        element("카카오톡 열기").tap()
        expect("카카오톡을 열 수 없어요. 복사한 내용을 직접 붙여넣어 주세요.")
        XCTAssertEqual(UIPasteboard.general.string, copied)
        capture("ios-share-kakao-unavailable")
    }
}
