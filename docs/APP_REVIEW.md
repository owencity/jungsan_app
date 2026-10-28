# FC-027 · iOS 로그인과 공개 데모 (2026-10-28)

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
- macOS CI에서 실기기 타깃 Kotlin 컴파일, Swift/시뮬레이터 앱 빌드, 실행·데모 딥링크와 스크린샷을 확인한다. CI 성공을 실제 iPhone 로그인 성공으로 간주하지 않는다.
- 새 TestFlight 빌드는 실제 API 주소 `https://jungsan-api.devkdk.com`를 지정한다. API 주소 공백은 기존 전체 목데이터 모드이므로 출시 설정으로 사용하지 않는다.
- 제출 전 실제 iPhone에서 양쪽 제공자 로그인·취소·재시도, 데모 역할 전환/초기화/공유 링크, 실제 계정 로그아웃·탈퇴를 확인한다.
- App Store Connect에는 새 빌드 번호, 위 진입·역할 전환 절차와 실제로 제공되는 기능/제한을 적는다. 제출·심사 통과는 빌드 성공과 별개다.

근거: [Apple 인증 세션](https://developer.apple.com/documentation/authenticationservices/authenticating-a-user-through-a-web-service), [심사 정보](https://developer.apple.com/help/app-store-connect/reference/app-information/platform-version-information#app-review-information).
