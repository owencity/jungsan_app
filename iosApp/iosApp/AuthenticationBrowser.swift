import AuthenticationServices
import ComposeApp
import SwiftUI

/// 인증 화면을 앱에 표시하고 완료 URL을 기존 PKCE 교환으로 넘긴다.
@MainActor
final class AuthenticationBrowser: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?
    private var anchor: ASPresentationAnchor?
    private var requestID: UUID?

    func start(_ address: String) {
        guard session == nil,
              let url = URL(string: address), url.scheme == "https",
              let window = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .flatMap({ $0.windows }).first(where: { $0.isKeyWindow }) else {
            IosAuthSessionBridge.shared.complete(url: nil, cancelled: false)
            return
        }
        let identifier = UUID()
        requestID = identifier
        anchor = window
        let authentication = ASWebAuthenticationSession(url: url, callbackURLScheme: "jeongsan") { [weak self] callback, error in
            DispatchQueue.main.async {
                guard let self, self.requestID == identifier else { return }
                self.session = nil
                self.anchor = nil
                self.requestID = nil
                let cancelled = (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin
                IosAuthSessionBridge.shared.complete(url: error == nil ? callback?.absoluteString : nil,
                                                     cancelled: cancelled)
            }
        }
        authentication.presentationContextProvider = self
        session = authentication
        if !authentication.start() {
            session = nil
            anchor = nil
            requestID = nil
            IosAuthSessionBridge.shared.complete(url: nil, cancelled: false)
        }
    }

    func cancel() {
        requestID = nil
        session?.cancel()
        session = nil
        anchor = nil
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        anchor ?? ASPresentationAnchor()
    }
}
