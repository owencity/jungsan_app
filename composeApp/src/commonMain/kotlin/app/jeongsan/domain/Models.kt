package app.jeongsan.domain

/**
 * 공통 타입 별칭. 모임(v2) 도메인 타입은 화면과 함께 지웠다(2026-10-04) — v3 데이터 모양은 `v3/Model.kt`.
 *
 * ⚠ 앱은 금액을 계산하지 않는다. 계산 엔진(백엔드 `core` 모듈)이 유일한 계산 주체다(ADR-005).
 */
typealias Id = Long
typealias Money = Long
