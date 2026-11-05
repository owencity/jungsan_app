# FC-027 · iOS 로그인과 공개 데모 (2026-10-28)

## 2026-11-05 · Guideline 4 재심사 점검

- Apple 회신에서 2.1은 해소됐고 외부 기본 브라우저 로그인(4.0)은 재지적됐다. 현재 증거만으로 특정 제공자 결함이나 심사자의 오해를 확정하지 않는다.
- 빌드17 업로드 실행41299620103은 a475264 소스이며 iOS 양쪽 로그인 모두 `ASWebAuthenticationSession`·`appReturn=session`을 포함한다. 실제 심사에 선택된 빌드는 App Store Connect에서 별도로 확인해야 한다.
- CTO는 TestFlight 업데이트 후 Apple은 앱 로그인 화면, 카카오는 웹 화면으로 보인다고 제보했다. 카카오 페이지가 웹 모양이라는 사실과 별도 Safari 앱 전환은 구분한다. 외부 전환 여부 및 로그인 완료까지는 확인 중이다.
- `iOS authentication review`는 동일 빌드17 기반 소스를 Release 구성으로 빌드하고 iPhone·iPad에서 실제 공개 Apple/카카오 버튼을 누른다. 시스템 인증창 표시, 기본 Safari가 전면으로 오지 않는지, 취소 후 재시도를 검사하고 캡처·녹화를 남긴다. 계정 입력·가입·약관 수락·토큰 발급은 하지 않는다.
- 운영 시작 점검은 제공자별 302·예상 HTTPS 제공자/콜백·Secure/HttpOnly/SameSite·no-store만 보존한다. state·쿠키 값·인가 URL은 기록하지 않는다. 두 개의 만료되는 시작 state만 만들며 콜백 성공이나 DB 세션표식 소비까지 검증했다고 주장하지 않는다.
- Windows 로컬에서는 iOS 앱을 실행할 수 없어 macOS CI의 시뮬레이터가 UI 점검을 맡는다. TestFlight는 운영 서버에 연결되므로 CTO가 로컬 Spring/Python을 켤 필요가 없다. 최종 확인은 실제 iPhone/iPad에서 제출 빌드의 양쪽 로그인 완료·취소·복귀를 녹화한다.
- 이 점검의 CI 성공·실기기 성공·Apple의 4.0 해소를 별개로 기록한다. 실제 외부 전환이 재현되면 해당 경로를 수정하고 새 빌드를 제출하며, 앱 안 인증창만 재현되면 빌드 번호/영상과 함께 심사팀에 재현 경로를 문의한다.

App Review의 외부 브라우저 로그인(Guideline 4)과 다중 사용자 기능 접근(2.1a) 피드백에 대응한다. CTO 요청으로 Codex가 앱 구현도 담당한다. 백엔드 의존 PR: https://github.com/owencity/jungsan_attack/pull/48

## 로그인

- iOS는 `ASWebAuthenticationSession`으로 카카오·Apple 인가를 시작한다. 세션 객체와 presentation anchor를 완료까지 유지한다.
- 시작 URL은 `client=app&appReturn=session&codeChallenge=...`. 서버는 선택한 state의 성공 콜백만303 `jeongsan://auth?ticket=...`로 돌려준다. 기존 PKCE 교환·토큰 저장을 사용한다.
- 중복 시작은 막고, 취소·시작 실패는 verifier를 지운다. completion/onOpenURL 중복 전달은 한 번만 소비한다. Android의 외부 브라우저·HTML 수동 복귀 계약은 유지한다.
- 백엔드 PR48 배포 전에 새 iOS 로그인을 출시하지 않는다. 이전 서버는 appReturn을 무시해 HTML로 돌아오므로 인증 세션이 완료되지 않는다.

## 데모 진입과 조작

1. 로그인 화면의 **로그인 없이 데모 체험**을 누른다. 별도 계정·비밀번호는 필요 없다.
2. 이름 확인 화면에서 2~10자 이름을 입력한다. 화면 상단에는 **데모 체험 · 가상 데이터 · 실제 송금 없음**이 계속 표시된다.
3. 상단에서 총무·참여자A·참여자B를 전환한다. `체험 · 응답 중`, `체험 · 입금 확인 중`, `체험 · 정산 완료` 세 예시를 제공한다.
4. 참여자B에서 응답 중 술자리에 응답한다. 총무로 돌아와 정산 근거를 확인한다. 참여자는 보냈어요, 총무는 입금 확인을 조작한다. 실제 은행 이체를 실행하는 앱이 아니다.
5. 총무는 새 술자리 생성·차수 입력도 시험할 수 있다. 정산방 메시지 작성, 다른 사람 메시지의 신고·차단, 내 계정의 차단 해제를 조작한다. 다른2명의 신고는 전체 숨김으로 반영된다.
6. **처음부터**는 새 가상 데이터로 초기화한다. **체험 종료**는 기존 실제 로그인 상태로 복귀한다. 내 계정의 **체험 데이터 삭제**는 이 가상 상태만 비운다.

공개 HTTPS 진입 페이지 계약은 `https://jungsan-api.devkdk.com/api/v1/review/demo`이며 버튼이 `jeongsan://demo`를 연다. 앱 설치가 필요하다. 백엔드 PR48 운영 배포·새 앱 설치 전에는 이 URL이 준비됐다고 심사팀에 안내하지 않는다. 데모 공유는 이 진입 링크만 사용하고, 운영에 존재하지 않는 가상 방의 토큰은 공유하지 않는다.

## 실제 기능과의 구분

- 데모에는 API 클라이언트를 연결하지 못하도록 생성자에서 검사한다. 실제 계정의 토큰·서버 데이터는 쓰거나 지우지 않는다. 데이터는 메모리에만 있고 앱 종료/초기화로 없어진다.
- 기존 로컬 스토어의 계산·상태 전이를 사용한다. 서버의 권한·동시성·Liquibase·OAuth 제공자 동작을 검증하는 시험 환경은 아니다. 실제 사용자 간 네트워크 동기화는 실제 로그인으로 별도 확인한다.
- 미출시 스푼 동작은 실제 API와 동일하게 제한하며 정산 완료 보상도 추가하지 않는다. 메시지 금칙어 목록은 FC-024의 현재 기본값을 체험에 반영하되 운영의 판정은 서버가 담당한다.
- 실제 회원 탈퇴는 실제 로그인 후 내 계정 → 회원 탈퇴에서 한다. 기존 진행 중 정산의409 제한은 유지한다. 데모 데이터 삭제를 실제 회원 탈퇴 검증이라고 보고하지 않는다.

## 출시 검증

- 로컬 Android debug APK 빌드와 공통 단위 테스트210건 통과(실패·건너뜀0). 마지막 변경 후 재검증 결과는 PR에 기록한다.
- macOS CI에서 실기기 타깃 Kotlin 컴파일, Swift/시뮬레이터 앱 빌드·실행 후 XCTest가 데모 버튼·역할 전환·초기화·종료를 실제로 누르고 화면을 검사한다. 스크린샷을 결과에 보존한다. `simctl openurl`만으로는 시스템의 앱 열기 확인창에서 멈출 수 있어 이를 데모 진입 성공으로 간주하지 않는다. CI 성공을 실제 iPhone 로그인 성공으로 간주하지 않는다.
- 새 TestFlight 빌드는 실제 API 주소 `https://jungsan-api.devkdk.com`를 지정한다. API 주소 공백은 기존 전체 목데이터 모드이므로 출시 설정으로 사용하지 않는다.
- 제출 전 실제 iPhone에서 양쪽 제공자 로그인·취소·재시도, 데모 역할 전환/초기화/공유 링크, 실제 계정 로그아웃·탈퇴를 확인한다.
- App Store Connect에는 새 빌드 번호, 위 진입·역할 전환 절차와 실제로 제공되는 기능/제한을 적는다. 제출·심사 통과는 빌드 성공과 별개다.

근거: [Apple 인증 세션](https://developer.apple.com/documentation/authenticationservices/authenticating-a-user-through-a-web-service), [심사 정보](https://developer.apple.com/help/app-store-connect/reference/app-information/platform-version-information#app-review-information).
