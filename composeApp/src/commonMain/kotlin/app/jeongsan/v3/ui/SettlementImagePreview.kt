package app.jeongsan.v3.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import app.jeongsan.v3.api.*

/** 공개 데모와 디버그 검사에서 같은 저장 코드를 사용한다. 실제 계정·편집 중인 방과 무관한 고정 샘플이다. */
@Composable
fun SettlementImagePreview(onBack: (() -> Unit)? = null) {
    val view = remember {
        val people = (1L..25L).map { TablePerson(it, "가상참여자$it", 1000, if (it == 1L) 25000 else 0) }
        ServerSettlementTable(1, 2, "체험용 정산표 · 실제 송금 없음", "SETTLING", false, "2026-10-31T00:00:00Z", 1, "fixture",
            TableData(people, listOf(TableRound(3, 1, 1, 25000, 25000, 0, 25000,
                people.map { TableCell(it.participantId, "SOBER", 1000, 0, 1000, "SELF") })),
                emptyList(), people.drop(1).map { TableTransfer(it.participantId, 1, 1000) }, 25000))
    }
    V3Screen(devBar = {}, top = { onBack?.let { BackButton(it) }; TopTitle("체험용 정산표") }) {
        Text("가상 25명 · 총 25,000원 예시예요. 편집 중인 술자리와 무관하며 실제 송금에 사용하지 마세요.")
        SettlementImageButton(view)
    }
}
