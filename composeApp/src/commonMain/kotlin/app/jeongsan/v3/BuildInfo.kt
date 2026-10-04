package app.jeongsan.v3

/**
 * 디버그 빌드인가 — 개발용 바(보는 사람 전환·화면 바로가기)를 릴리스에서 숨기는 데 쓴다.
 * 웹의 `import.meta.env.DEV`와 같은 역할. 테스터·스토어 빌드(릴리스)에는 개발용 바가 나가면 안 된다.
 */
expect val isDebugBuild: Boolean
