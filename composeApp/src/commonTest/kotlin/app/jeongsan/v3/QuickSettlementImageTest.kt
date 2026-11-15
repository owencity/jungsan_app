package app.jeongsan.v3

import app.jeongsan.v3.api.QuickSettlementResult
import kotlin.test.*

class QuickSettlementImageTest {
    @Test fun 보낼금액과_결제자본인몫_받을합계를_서버응답그대로_구분한다() {
        val rows = QuickSettlementResult(10_000, 3, 3334, 3332, 6668).imagePlan().rows
        assertEquals("3,334원", rows[2].values.single())
        assertEquals("결제자를 제외한 2명", rows.first { it.values.first() == "보내는 사람" }.values[1])
        assertEquals("6,668원", rows.first { it.values.first() == "결제자가 받을 합계" }.values[1])
        assertEquals("3,332원", rows.first { it.values.first() == "결제자 본인 부담" }.values[1])
        assertEquals("10,000원", rows.first { it.values.first() == "전체 결제액" }.values[1])
    }
}
