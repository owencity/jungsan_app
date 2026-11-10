import ComposeApp
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser
import SwiftUI

/// 공식 SDK만 카카오톡 실행/복귀를 해석한다. REST Client Secret과 사용자 ID는 앱에서 다루지 않는다.
@MainActor
final class KakaoTalkLogin: ObservableObject {
    private let configured: Bool
    private var requestID: UUID?

    init() {
        let key = Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String ?? ""
        configured = key.range(of: "^[a-fA-F0-9]{32}$", options: .regularExpression) != nil
        if configured { KakaoSDK.initSDK(appKey: key, loggingEnable: false) }
    }

    func start() {
        guard configured else { complete(status: "NOT_CONFIGURED"); return }
        guard requestID == nil else { return }
        let identifier = UUID()
        requestID = identifier
        // 카카오톡이 없어도 같은 카카오 계정으로 로그인할 수 있어야 한다.
        // 계정 로그인은 공통 코드의 PKCE와 앱 안 ASWebAuthenticationSession을 사용한다.
        guard UserApi.isKakaoTalkLoginAvailable() else { complete(status: "ACCOUNT"); return }
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

    func cancel() {
        requestID = nil
    }

    private func complete(accessToken: String? = nil, status: String) {
        requestID = nil
        IosKakaoTalkBridge.shared.complete(accessToken: accessToken, status: status)
    }
}
