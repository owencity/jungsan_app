package app.jeongsan.v3.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.jeongsan.v3.api.*

/** 디버그 실행 인자로만 진입한다. 서버 데이터·계좌·인증을 사용하지 않는 25명 저장 창 검증용이다. */
@Composable
fun SettlementImagePreview() {
    val view = remember {
        val people = (1L..25L).map { TablePerson(it, "가상참여자", 1000, if (it == 1L) 25000 else 0) }
        ServerSettlementTable(1, 2, "저장 검증 · 가상 데이터", "SETTLING", false, "2026-10-31T00:00:00Z", 1, "fixture",
            TableData(people, listOf(TableRound(3, 1, 1, 25000, 25000, 0, 25000,
                people.map { TableCell(it.participantId, "SOBER", 1000, 0, 1000, "SELF") })),
                emptyList(), people.drop(1).map { TableTransfer(it.participantId, 1, 1000) }, 25000))
    }
    V3Screen(devBar = {}, top = { TopTitle("가상 데이터 · 이미지 저장 검증") }) { SettlementImageButton(view) }
}
