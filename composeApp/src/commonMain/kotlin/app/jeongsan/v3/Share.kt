package app.jeongsan.v3

import androidx.compose.runtime.Composable

/**
 * 링크 공유 — 웹 `v3/share.ts`와 같은 주소·같은 문구. 받는 사람이 링크만 보고 뭘 하면 되는지 알게 한 줄로 쓰고,
 * 단톡방은 공개된 자리라 금액은 넣지 않는다.
 */

/** 설치 여부와 무관하게 모바일웹 응답 입구로 연다. 앱 딥링크로 가로채지 않는다. */
const val SHARE_BASE = "https://jungsan.devkdk.com"

fun shareUrl(base: String, token: String): String = "${base.trimEnd('/')}/jungsan/j/$token"

fun shareMessage(g: Gathering, url: String): String {
    val host = g.host().displayName
    val ask = when {
        g.status != GatheringStatus.OPEN -> "정산 결과는 총무가 공유한 이미지에서 확인해주세요"
        g.rounds.isNotEmpty() -> "가입 없이 이름과 차수별 참석·음주만 알려주세요"
        else -> "총무가 차수를 입력하면 이 링크에서 응답해주세요"
    }
    return "[정산어택] ${host}님의 ${g.title}\n$ask 👉 $url"
}

/**
 * 입금 요청(FC-020) — 정산 뒤 총무가 단톡방에 보낸다. 사람별 보낼 금액과 받을 계좌를 한 번에 담는다.
 * 받는 사람이 둘 이상이면(차수마다 낸 사람이 다름) 받는 사람별로 묶는다. 이미 확인된 송금은 뺀다. 웹 `paymentRequestMessage`와 같은 문구
 */
fun paymentRequestMessage(g: Gathering): String {
    val open = g.transfers.filter { it.status != TransferStatus.CONFIRMED }
    val blocks = open.map { it.toParticipantId }.distinct().map { to ->
        val p = g.participants.find { it.id == to }
        val account = p?.payout?.let { "${it.bank} ${it.accountNo} (${it.holder})" } ?: "계좌는 총무에게 확인해주세요"
        val lines = open.filter { it.toParticipantId == to }.map { "· ${g.nameOf(it.fromParticipantId)} ${app.jeongsan.util.won(it.amount)}" }
        (listOf("→ ${g.nameOf(to)}님께 보내주세요") + lines + "   $account").joinToString("\n")
    }
    return (listOf("[정산어택] ${g.title} 계산 끝!") + blocks + "입금 후 총무에게 알려주세요. 자세한 계산은 총무가 공유한 정산 이미지를 확인해주세요.").joinToString("\n")
}

/** 복사 뒤 사용자가 선택했을 때만 카카오톡을 연다. 성공은 앱 실행이며 메시지 전송 완료가 아니다. */
@Composable
expect fun rememberOpenKakaoTalk(): (onResult: (Boolean) -> Unit) -> Unit
