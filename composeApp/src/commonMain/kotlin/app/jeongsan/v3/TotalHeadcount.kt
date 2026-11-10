package app.jeongsan.v3

import app.jeongsan.domain.Id

/** 명단에 없는 예상 참여자까지 기다릴 수 있도록 차수 인원과 별도로 보여준다. */
fun Gathering.responseProgress(): String = totalHeadcount?.let { "${respondedCount()} / ${it}명 응답" }
    ?: "${respondedCount()}명 응답 · 총 인원 미설정"

fun Gathering.totalHeadcountNotice(): String = when {
    totalHeadcount != null && respondedCount() > totalHeadcount ->
        "총 인원 ${totalHeadcount}명보다 많은 ${respondedCount()}명이 응답했어요. 명단과 총 인원을 확인해주세요."
    else -> "총무가 정산을 확정할 때까지 추가 응답을 받을 수 있어요."
}

fun Gathering.canEditTotalHeadcount(meUserId: Id): Boolean =
    hostUserId == meUserId && status == GatheringStatus.OPEN

fun Gathering.totalHeadcountError(meUserId: Id, value: Int): String? = when {
    hostUserId != meUserId -> "총 정산인원은 총무만 바꿀 수 있어요."
    status != GatheringStatus.OPEN -> "정산 확정 후에는 총 정산인원을 바꿀 수 없어요."
    value !in HEADCOUNT_MIN..HEADCOUNT_MAX -> "총 정산인원은 나를 포함해 2~50명으로 입력해주세요."
    else -> null
}
