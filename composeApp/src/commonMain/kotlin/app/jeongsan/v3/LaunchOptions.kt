package app.jeongsan.v3

/**
 * 실행 인자 — 스토어 스크린샷을 자동으로 찍기 위한 "스크린샷 모드".
 *
 * iOS: `xcrun simctl launch <기기> app.jeongsan -jsScreen room` (MainViewController 가 읽는다)
 * Android: `adb shell am start -n app.jeongsan/.MainActivity -e jsScreen room` (MainActivity 가 읽는다)
 *
 * 값이 있으면 로그인·이름 확인을 건너뛰고 목데이터로 그 화면을 바로 띄우며, 개발용 바를 숨긴다.
 * 화면 이름과 보는 사람은 [SCREENSHOT_SCREENS] — 워크플로 `screenshots-ios.yml`이 같은 이름을 쓴다.
 */
object LaunchOptions {
    var screenshot: String? = null
}

/** 스크린샷 화면 이름 → (보는 사람 userId, 띄울 술자리 id 또는 null=내 술자리) — 스토어에 올리는 순서 */
val SCREENSHOT_SCREENS: Map<String, Pair<Long, Long?>> = linkedMapOf(
    "room" to (1L to 101L),     // R1 정산방 — 총무 시점, 응답 현황·타임라인
    "respond" to (4L to 101L),  // P2 응답 — 아직 응답 안 한 참여자(최지영)
    "settle" to (1L to 101L),   // R3 정산하기 — 1인당 금액 미리보기
    "pay" to (1L to 102L),      // P3 보낼 돈 — 금액·근거·계좌 복사
    "home" to (1L to null),     // H1 내 술자리
    "login" to (0L to null),    // L1 로그인(석양 배경) — 아무 것도 하지 않는다
)
