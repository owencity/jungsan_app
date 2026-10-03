package app.jeongsan.v3

/**
 * 화면에서 잠시 숨겨 둔 기능의 스위치 — 웹 `v3/features.ts`와 같은 이름·같은 값.
 * 기능 코드(스토어 동작·테스트)는 살아 있고 **화면에서만** 가린다.
 */
object Features {
    /** R4 차수별 [면제] 버튼 — 보류(CTO 결정 2026-10-03). 다시 켤 때 true로 */
    const val SHOW_EXEMPT = false
}
