import ComposeApp
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser
import SwiftUI
import UIKit

/// 공식 SDK만 카카오톡 실행/복귀를 해석한다. REST Client Secret과 사용자 ID는 앱에서 다루지 않는다.
@MainActor
final class KakaoTalkLogin: ObservableObject {
    private let configured: Bool
    private var requestID: UUID?
    private weak var installationAlert: UIAlertController?

    init() {
        let key = Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String ?? ""
        configured = key.range(of: "^[a-fA-F0-9]{32}$", options: .regularExpression) != nil
        if configured { KakaoSDK.initSDK(appKey: key, loggingEnable: false) }
    }

    func start() {
        guard configured else { complete(status: "NOT_CONFIGURED"); return }
        guard requestID == nil else { complete(status: "FAILED"); return }
        let identifier = UUID()
        requestID = identifier
        guard UserApi.isKakaoTalkLoginAvailable() else { showInstallGuide(identifier); return }
        // Universal Link의 브라우저 대체 경로를 사용하지 않고 SDK의 카카오톡 스킴으로 실행한다.
        UserApi.shared.loginWithKakaoTalk(launchMethod: .CustomScheme) { [weak self] token, error in
            DispatchQueue.main.async {
                // 이 앱은 카카오 API를 후속 호출하지 않는다. 로컬 SDK 자격은 즉시 버리고
                // 검증에 필요한 access token만 한 번 서버로 보낸다(원격 토큰 폐기는 아님).
                TokenManager.manager.deleteToken()
                guard let self, self.requestID == identifier else { return }
                if let sdkError = error as? SdkError, sdkError.isClientFailed,
                   sdkError.getClientError().reason == .Cancelled {
                    self.complete(status: "CANCELLED")
                } else if error != nil || token == nil {
                    self.complete(status: "FAILED")
                } else {
                    self.complete(accessToken: token?.accessToken, status: "TOKEN")
                }
            }
        }
    }

    private func showInstallGuide(_ identifier: UUID) {
        guard var presenter = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows }).first(where: { $0.isKeyWindow })?.rootViewController else {
            complete(status: "FAILED"); return
        }
        while let presented = presenter.presentedViewController { presenter = presented }
        let alert = UIAlertController(title: "카카오톡이 필요해요",
            message: "카카오톡 앱을 설치한 뒤 간편 로그인할 수 있어요. Apple 로그인으로도 바로 시작할 수 있어요.", preferredStyle: .alert)
        func choose(_ status: String, openStore: Bool = false) {
            guard self.requestID == identifier else { return }
            // 인증창을 열기 전에 안내의 닫힘이 끝나도록 UIKit 완료 시점에서 콜백한다.
            guard let activeAlert = self.installationAlert else { self.complete(status: "FAILED"); return }
            activeAlert.dismiss(animated: true) {
                guard self.requestID == identifier else { return }
                self.complete(status: status)
                if openStore, let url = URL(string: "https://apps.apple.com/kr/app/id362057947") {
                    UIApplication.shared.open(url)
                }
            }
        }
        alert.addAction(UIAlertAction(title: "카카오톡 설치하기", style: .default) { _ in choose("CANCELLED", openStore: true) })
        alert.addAction(UIAlertAction(title: "Apple로 계속하기", style: .default) { _ in choose("APPLE") })
        alert.addAction(UIAlertAction(title: "취소", style: .cancel) { _ in choose("CANCELLED") })
        installationAlert = alert
        presenter.present(alert, animated: true)
    }

    func cancel() {
        requestID = nil
        installationAlert?.dismiss(animated: true)
        installationAlert = nil
    }

    private func complete(accessToken: String? = nil, status: String) {
        requestID = nil
        installationAlert = nil
        IosKakaoTalkBridge.shared.complete(accessToken: accessToken, status: status)
    }
}
