# FC-032 총무의 비회원 이름 정정

참여자 관리 시트에서 OPEN 비회원 이름을 정정한다. 기존 디자인 안에 이름 입력/저장과 오류 안내를 추가했다.
회원 이름은 이 기능으로 바꾸지 않는다. 실제 비회원 기능이며 가상 데모의 회원 역할 전환과 구분한다.

`PUT /api/v1/gatherings/{gid}/settlement-units/{id}/participants/{pid}/name`
본문 displayName/expectedDisplayName, 성공 204. 앱은 기존 Bearer 인증을 유지한다.
저장 후 술자리를 다시 읽고, 중복 탭을 막으며 실패하면 입력과 안내를 유지한다.
공유 이름이므로 다른 단위에서 이미 확정됐으면 서버가 GUEST_NAME_LOCKED로 거절한다.
동시에 먼저 정정된 이름을 덮지 않도록 시트를 열 때의 이름을 expectedDisplayName으로 보낸다.
participantId·응답·스푼은 서버가 유지하며 앱에서 재계산하지 않는다.

로컬 Android 컴파일 및 단위 테스트 통과. iOS CI·실기기·TestFlight 업로드 상태는 PR에 별도 기록한다.
백엔드 PR54/API v22가 먼저 배포되어야 한다. 스터디 PR은 병합하지 않는다.
