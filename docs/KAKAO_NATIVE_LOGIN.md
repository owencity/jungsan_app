# FC-034 · iOS 카카오톡 로그인 설정과 검증

## 결정과 범위

카카오톡 설치 기기는 공식 Kakao iOS SDK의 `loginWithKakaoTalk(launchMethod: .CustomScheme)`을 쓴다.
미설치 기기는 `카카오톡 설치하기 / Apple로 계속하기 / 취소` 안내를 보며 자동 웹 로그인을 하지 않는다.
설치 버튼만 카카오톡 공식 App Store 페이지를 연다. Apple 인증·Android OAuth는 기존 동작이다.
공개 데모도 유지한다. OCR·웹 참여자·정산 계산은 이 브랜치의 변경 범위가 아니다.

SDK는 공식 저장소 `kakao/kakao-ios-sdk`의 **2.30.0**으로 고정하고 `KakaoSDKUser`를 사용한다.
Auth/Common과 Alamofire(공식 패키지 범위5.9 이상6 미만)가 간접 의존한다. 서버에 라이브러리를
추가하지 않으며 새 권한/스코프도 요청하지 않는다. SDK 로깅은 끈다. 임의 URL 호출/웹 스크래핑보다
제공자가 관리하는 앱 전환·콜백 검증을 선택한 대신 네이티브 키/SDK 업데이트를 관리해야 한다.

## 운영 설정

1. **기존 정산어택 카카오 개발자 앱**에서 숫자 앱 ID, 공개 Native app key를 확인한다. 새 개발자 앱을
   만들면 기존 사용자 ID 연결이 달라질 수 있으므로 같은 앱을 사용한다. iOS 플랫폼의 번들 ID는
   `app.jeongsan`이어야 한다. 로그인 활성화/동의 항목은 기존 범위를 확인하며 권한을 넓히지 않는다.
2. 서버 `.env`에 양수 `KAKAO_APP_ID`를 넣고 FC-034 서버를 검증·배포한다. 토큰 정보의 app_id와
   `/v2/user/me`의 id/만료를 대조한 후 기존 계정에 APP JWT를 발급한다. 운영 설정 누락은 기동 실패다.
3. 앱 저장소 GitHub Actions **Variables**에 `KAKAO_NATIVE_APP_KEY`를 넣는다. 공개 앱 식별용 키이며
   Client Secret/Admin key/REST API 키를 넣지 않는다. Release 업로드는 누락/가짜 키를 거절한다.
4. URL scheme `kakao<Native app key>`, 조회 스킴 `kakaokompassauth`, Bundle의 `KakaoNativeAppKey`는
   Xcode 빌드 설정에서 생성된다. 기존 `jeongsan` 복귀 스킴도 유지한다.
5. TestFlight workflow에 운영 API 주소를 지정하고 새 빌드를 올린다. 기존 제출17에는 이 변경이 없다.

Debug와 인증 검사 Release 빌드는 공개 가짜 키를 쓴다. 이는 카카오톡이 없는 시뮬레이터에서
안내 화면과 Apple 대안만 검사하기 위한 값이며 실제 카카오 인증 성공 증거로 사용하지 않는다.

## 자격과 취소

- SDK callback의 access token만 서버로 보내고 SDK의 로컬 토큰 저장소를 바로 비운다.
  원격 토큰 폐기는 아니며 제공자 refresh token을 앱의 자체 저장소에 복사하지 않는다.
- 서버가 반환한 우리 APP JWT만 기존 TokenStore에 저장한다. 제공자 토큰·프로필·예외 본문을 로그에
  쓰지 않는다. 기존 JWT 로그아웃/회원 탈퇴 계약을 유지하며, 다른 제공자로 로그인한 계정을 SDK가
  임의로 연결 해제하지 않는다. 카카오 외부 연결 해제 기능을 이번에 추가했다고 주장하지 않는다.
- 취소·화면 종료·새 시도 뒤의 늦은 callback/HTTP 결과는 인증 상태를 덮어쓰지 못한다.
  중복 callback도 교환 한 번만 수행한다. 사용자는 서버 응답을 기다리는 동안 로그인 취소를 누를 수 있다.

## 확인할 것

- 공통 단위 테스트: HTTP 계약, APP JWT만 저장, 서버401, 취소 전/후 늦은 결과, 중복 callback.
- macOS CI: 공식 SDK 포함 Swift/기기 타깃 컴파일, iPhone·iPad 미설치 안내, 취소/재시도,
  안내에서 Apple 인증창 연결. 기존 데모·이름 수정·이미지 UI 검사도 유지한다.
- 실제 iPad/iPhone: 카카오톡 설치 상태에서 카카오 앱 전환→동의/취소→정산어택 복귀→로그인 완료,
  로그아웃→재로그인, 기존 계정 이름/술자리/스푼 유지. 미설치 상태에서 설치 안내와 명시적 App Store
  이동/Apple 대안도 확인한다. 실제 로그인과 심사 승인 결과는 각각 기록한다.

코드/공통 테스트 완료와 설정/배포/실기기 완료를 구분해 PR에 최신 결과를 남긴다.

근거: [공식 SDK 설치](https://developers.kakao.com/docs/ko/ios/getting-started),
[공식 로그인](https://developers.kakao.com/docs/ko/kakaologin/ios),
[공식 토큰 정보](https://developers.kakao.com/docs/ko/kakaologin/rest-api#access-token-info),
[SDK 2.30.0](https://github.com/kakao/kakao-ios-sdk/tree/2.30.0).
