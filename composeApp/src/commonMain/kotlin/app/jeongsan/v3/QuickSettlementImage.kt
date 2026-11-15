package app.jeongsan.v3

import app.jeongsan.util.won
import app.jeongsan.v3.api.QuickSettlementResult

/** 계산은 서버 결과를 그대로 쓴다. 이름·계좌를 받지 않는 간단 정산에 가상의 송금 대상을 붙이지 않는다. */
fun QuickSettlementResult.imagePlan() = SettlementImagePlan(
    width = 800,
    columnWidths = listOf(380, 380),
    rows = listOf(
        SettlementImageRow(listOf("정산어택 · 간단 N빵"), fullWidth = true, highlighted = true),
        SettlementImageRow(listOf("결제자에게 보낼 금액 · 한 사람당"), fullWidth = true),
        SettlementImageRow(listOf(won(participantAmount)), fullWidth = true, highlighted = true),
        SettlementImageRow(listOf("보내는 사람", "결제자를 제외한 ${headcount - 1}명")),
        SettlementImageRow(listOf("전체 결제액", won(total))),
        SettlementImageRow(listOf("전체 인원", "${headcount}명 · 결제자 포함")),
        SettlementImageRow(listOf("결제자가 받을 합계", won(incomingTotal)), highlighted = true),
        SettlementImageRow(listOf("결제자 본인 부담", won(payerAmount))),
        SettlementImageRow(listOf("송금액은 1원 단위로 올리고, 차액은 결제자 본인 몫에서 조정했어요."), fullWidth = true),
        SettlementImageRow(listOf("이 결과는 입력한 금액과 인원 기준이에요. 송금은 사용하시는 은행 앱에서 해주세요."), fullWidth = true),
    ),
)
