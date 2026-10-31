# FC-028 총무 앱의 비회원 명단 호환

기준: 앱 PR3 `da6a1fd06f40daba2d9944f108bc4860fafc33b3` 위 별도 브랜치. 기존 PR은 병합하지 않는다.

백엔드 PR50/51은 비회원 참여자를 기존 명단에 `userId:null`로 반환한다. 기존 non-null Long 역직렬화는
명단 전체 조회를 실패시키므로 서버·웹 공개 전에 앱 호환 버전이 필요하다.

SParticipant와 Participant의 userId를 nullable로 유지한다. 가짜 계정 ID나 이름 매칭으로 비회원을
회원에 연결하지 않는다. 명단·응답·송금은 participantId로 유지하고 회원 총무 권한은 변경하지 않는다.
로컬 데모 알림은 후보를 만든 뒤 회원 ID 없는 대상은 제외한다. 회원 차단 목록에도 null을 넣지 않는다.

검증: Android testDebugUnitTest 212개, 실패/오류/건너뜀 0. null이 포함된 실제 계약 모양 JSON의
역직렬화, 총무/응답/송금 유지, 동명이인 분리, 회원 알림 제외를 추가했다. iOS 빌드는 CI에서 별도 확인한다.
기존 Kotlin datetime·Gradle/Android 플러그인 경고는 이번 수정에서 숨기거나 버전을 바꾸지 않았다.

이 PR에는 앱 안의 캡처용 정산표 화면이나 새 TestFlight 업로드가 포함되지 않는다. 비회원 웹/서버 배포와
앱 실기기 명단 검증은 별도다. 학습 상태 UNRECORDED. 고정 diff와 CI 결과는 PR 설명에 남긴다.
