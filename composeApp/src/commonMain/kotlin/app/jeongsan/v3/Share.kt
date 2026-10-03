package app.jeongsan.v3

import androidx.compose.runtime.Composable

/**
 * 링크 공유 — 웹 `v3/share.ts`와 같은 주소·같은 문구. 받는 사람이 링크만 보고 뭘 하면 되는지 알게 한 줄로 쓰고,
 * 단톡방은 공개된 자리라 금액은 넣지 않는다.
 */

/** 앱이 만드는 공유 주소의 기준 — 링크는 웹 참여 입구(P1)로 연다. 앱이 깔려 있으면 딥링크로 앱이 받는다(후속) */
const val SHARE_BASE = "https://jungsan.devkdk.com"

fun shareUrl(base: String, token: String): String = "${base.trimEnd('/')}/jungsan/j/$token"

fun shareMessage(g: Gathering, url: String): String {
    val host = g.host().displayName
    val ask = when {
        g.status != GatheringStatus.OPEN -> "정산 금액을 확인하고 보내주세요"
        g.rounds.isNotEmpty() -> "차수마다 마셨는지만 눌러주세요"
        else -> "먼저 들어와 있으면 금액이 나올 때 알려드려요"
    }
    return "[정산어택] ${host}님의 ${g.title}\n$ask 👉 $url"
}

/**
 * OS 공유 시트를 띄우는 함수를 돌려준다 — Android는 공유 인텐트(카카오톡이 목록 맨 위에 뜬다),
 * iOS는 UIActivityViewController. 화면 코드는 이 함수 하나만 부른다.
 */
@Composable
expect fun rememberShareSheet(): (String) -> Unit
