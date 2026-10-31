# FC-030 자체 검토·스터디

학습 상태: UNRECORDED. 자체 검토는 외부 리뷰나 학습 완료가 아니다.

- PR 전체 base: da6a1fd06f40daba2d9944f108bc4860fafc33b3
- 이번 변경 base: 1d5217830d90420a22258511a414bfd6ba32696a
- 코드 head: a0b9321e8ccb4cf3e44e394a0463b9af94be037b
- 전체 diff: pr.patch. 이번 변경 diff: fc030.patch. 뒤따르는 문서 커밋과 구분한다.

## 자체 검토

운영 계정에서 홈/알림 목록과 이전 참여 경로를 모두 검토했다. 같은 gatheringId라도 다른 총무의 단위를 표시하지 않는다.
화면 숨김은 보안 경계가 아니다. API 권한·인증을 바꾸지 않았고 총무 Round/Settle/Table 검증을 유지한다.
총무 자신의 참석/음주는 RoundEditScreen에 남아 있으며 확정은 기존 revision/hash API로만 진행한다.
저장 이미지는 서버 금액을 그대로 사용한다. 금액 확정과 입금 완료, 예상 표와 확정 표를 구분한다.
이미지 오류 시 일부 인원을 잘라내지 않는 기존 제한도 유지한다. 카카오 메시지는 사용자가 직접 첨부/전송한다.
데모는 API 없는 가상 역할 전환 검증 도구로 유지하므로 운영 UI와 다르다. 심사 체험 개편은 후속이다.

## 실행 결과와 한계

Android compileDebugKotlinAndroid·testDebugUnitTest 성공, 229개/실패0/오류0/건너뜀0.
목록/완료/알림 소유자와 단위 필터, 이전 Target 처리, 확정 이미지 조건, 공유 안내 회귀 테스트를 추가했다.
기존 이미지 테스트의 25명·복수 수취인·동명이인·조정액·예상 저장 금지·크기 한도도 통과했다.
기존 Kotlin datetime/Gradle 경고는 남아 있다. Windows에서 iOS 실행과 실기기 저장을 검증했다고 주장하지 않는다.
최신 iOS CI·TestFlight·운영 연결은 PR에서 별도 상태를 기록한다. 이번 변경으로 서버/웹이 배포되지는 않는다.

## 기술별 학습 포인트 · UNMATCHED

1. `HostApp.kt:6` hostedRooms / `:9` hostNotifications / `:17` hostAppTarget: 화면 정책을 순수 함수로 분리해 같은 술자리의 다른 단위와 다른 사용자를 구분한다. UI 필터와 서버 인가의 차이를 리뷰한다.
2. `ui/HomeScreen.kt:83` HomeScreen: 목록 데이터와 탭 노출을 함께 바꿔야 완료 기록에 참여자 방이 남지 않는다. `remember` 키에 화면 모드를 포함하는 이유를 검토한다.
3. `ui/V3Nav.kt:212` Join, `:275` Room, `:416` Respond, `:493` Pay: 버튼을 숨겨도 예전 경로는 살아 있다. 직접 진입 시 웹 안내/총무 관리로 제한하는지 확인한다.
4. `ui/RoomScreen.kt:122` saveImage: 저장 동작은 확정 여부와 총무 역할을 함께 확인한다. 서버 원본 송금액을 재계산하지 않는 기존 SettlementImageTest와 연결해 본다.

책/강의의 정확한 목차를 대조하지 않은 기술별 후보이므로 임의로 목차 번호를 붙이지 않았다.
