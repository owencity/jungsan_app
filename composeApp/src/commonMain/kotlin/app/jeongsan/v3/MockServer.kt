package app.jeongsan.v3

import app.jeongsan.domain.Id
import app.jeongsan.domain.Money

/**
 * ⚠ 서버 흉내 — 정산 미리보기와 정산하기의 결과를 만든다. 웹 `v3/mockServer.ts`와 같은 규칙.
 *
 * 앱은 금액을 계산하지 않는다(SCREENS.md §8). 그런데 목데이터 단계에서 R3 미리보기와 정산 뒤
 * 송금 목록이 응답에 따라 움직여야 흐름을 끝까지 걸어볼 수 있다. 그래서 **계산을 이 파일 하나에
 * 가두고**, 화면은 이 결과를 "서버가 준 값"으로만 다룬다. 백엔드 v3 API가 붙으면 이 파일을 통째로
 * 지우고 호출부를 API로 바꾼다.
 *
 * 규칙은 실제 계산(CALC_RULES_V2)이 아니라 흉내다: 차수마다 술값은 알코올끼리, 나머지는
 * 참석자(논알코올·알코올)끼리 나누고 100원 아래는 버린다. 버린 자투리는 결제자가 떠안는다.
 * 면제·불참은 0원.
 */

data class PreviewLine(
    val participantId: Id,
    /** 이 사람이 부담하는 합계(결제자 본인 몫 포함) */
    val total: Money,
    /** 응답이 없어 자동응답(전 차수 참석·알코올)으로 계산되는가 */
    val auto: Boolean,
    val rounds: List<TransferBasis>,
)

data class PreviewTransfer(val fromParticipantId: Id, val toParticipantId: Id, val amount: Money, val basis: List<TransferBasis>)

data class SettlePreview(
    /** 정산하기 요청에 같이 보낸다 — 그 사이 입력이 바뀌었으면 서버가 409로 거절한다 */
    val inputRevision: Int,
    val lines: List<PreviewLine>,
    val transfers: List<PreviewTransfer>,
)

/** 응답이 빈 칸은 정산 때 "참석·알코올"로 채운다 — REQUIREMENTS 자동응답 규칙 */
fun withAutoResponses(g: Gathering): List<RoundResponse> {
    val filled = g.responses.toMutableList()
    for (p in g.participants) for (r in g.rounds) {
        if (g.responseOf(p.id, r.id) == null) filled += RoundResponse(p.id, r.id, ResponseType.DRANK, ResponseSource.AUTO)
    }
    return filled
}

private fun floor100(n: Long): Long = n / 100 * 100

fun mockPreview(g: Gathering): SettlePreview {
    val responses = withAutoResponses(g)
    fun typeOf(pid: Id, rid: Id) = responses.first { it.participantId == pid && it.roundId == rid }.type

    // share[pid][roundId] = 그 차수 부담액
    val share = g.participants.associate { it.id to mutableMapOf<Id, Money>() }

    for (r in g.rounds) {
        val eaters = g.participants.filter { typeOf(it.id, r.id) == ResponseType.SOBER || typeOf(it.id, r.id) == ResponseType.DRANK }
        val drinkers = eaters.filter { typeOf(it.id, r.id) == ResponseType.DRANK }
        val drinkPool = if (drinkers.isNotEmpty()) minOf(drinksTotal(r.drinks), r.total) else 0
        val foodPool = r.total - drinkPool

        var othersSum = 0L
        for (p in g.participants) {
            if (p.id == r.payerParticipantId) continue
            var amount = 0L
            if (p in eaters) amount += floor100(foodPool / eaters.size)
            if (typeOf(p.id, r.id) == ResponseType.DRANK) amount += floor100(drinkPool / drinkers.size)
            share.getValue(p.id)[r.id] = amount
            othersSum += amount
        }
        // 결제자는 나머지를 떠안는다(자기 몫 + 자투리). 아무도 참석 안 했으면 전부.
        share[r.payerParticipantId]?.set(r.id, r.total - othersSum)
    }

    val lines = g.participants.map { p ->
        val rounds = g.rounds.map { r -> TransferBasis(r.id, typeOf(p.id, r.id), share.getValue(p.id)[r.id] ?: 0) }
        PreviewLine(
            participantId = p.id,
            total = rounds.sumOf { it.amount },
            auto = responses.any { it.participantId == p.id && it.source == ResponseSource.AUTO },
            rounds = rounds,
        )
    }

    // 송금: 결제자가 아닌 사람 → 그 차수 결제자. 같은 사람에게 보낼 건 한 번으로 묶는다.
    val byPair = LinkedHashMap<Pair<Id, Id>, PreviewTransfer>()
    for (r in g.rounds) for (p in g.participants) {
        if (p.id == r.payerParticipantId) continue
        val amount = share.getValue(p.id)[r.id] ?: 0
        if (amount <= 0) continue
        val key = p.id to r.payerParticipantId
        val t = byPair[key] ?: PreviewTransfer(p.id, r.payerParticipantId, 0, emptyList())
        byPair[key] = t.copy(amount = t.amount + amount, basis = t.basis + TransferBasis(r.id, typeOf(p.id, r.id), amount))
    }

    return SettlePreview(g.inputRevision, lines, byPair.values.toList())
}
