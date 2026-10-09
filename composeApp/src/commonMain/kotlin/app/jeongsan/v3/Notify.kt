package app.jeongsan.v3

import app.jeongsan.domain.Id
import app.jeongsan.util.won

/**
 * 상태 전이 → 알림. 웹 `v3/notify.ts`와 같은 규칙·같은 문구.
 *
 * 술자리의 전(prev)과 후(next)를 비교해 누구에게 무슨 알림을 보낼지 정한다. 서버에선 도메인
 * 이벤트(정산됨·보냈어요·안 들어왔어요·완료)를 받아 알림·푸시를 만드는 자리다. 행동한 본인을
 * 빼는 건 스토어가 한다.
 */
data class NewNotification(val userId: Id, val roomId: Id, val title: String, val body: String, val target: Target)

fun notificationsFor(prev: Gathering, next: Gathering): List<NewNotification> {
    val out = mutableListOf<NewNotification>()
    val host = next.host()
    fun userOf(participantId: Id) = next.participants.first { it.id == participantId }.userId

    // ── 링크로 새 사람이 참여함: 총무에게 (REQUIREMENTS §10 "참여자가 응답을 남김") ──
    for (p in next.participants) {
        if (prev.participants.any { it.id == p.id }) continue
        val answered = next.responses.any { it.participantId == p.id }
        out += NewNotification(
            host.userId, next.id, "${p.displayName}님이 ${if (answered) "참여하고 응답했어요" else "참여했어요"}", next.title, Target.Room(next.id),
        )
    }

    // ── 총무가 내보냄: 빠진 사람에게 (술자리에 더는 못 들어가니 내 술자리로) ──
    for (p in prev.participants) {
        if (next.participants.any { it.id == p.id }) continue
        // 자동 정산 때 인원 밖이라 빠진 것(FC-020)과 총무가 내보낸 것을 문구로 구분한다
        val why = if (prev.status == GatheringStatus.OPEN && next.status != GatheringStatus.OPEN) {
            "인원(${prev.headcount}명) 밖이라 이번 정산에서 빠졌어요"
        } else {
            "${host.displayName} 총무가 명단에서 뺐어요"
        }
        out += NewNotification(p.userId, next.id, "${next.title}에서 빠졌어요", why, Target.Home)
    }

    // ── 총무가 면제함: 그 사람에게 ──
    for (r in next.responses) {
        if (r.type != ResponseType.EXEMPT || r.source != ResponseSource.HOST) continue
        val before = prev.responses.find { it.participantId == r.participantId && it.roundId == r.roundId }
        if (before?.type == ResponseType.EXEMPT) continue
        val p = next.participants.find { it.id == r.participantId } ?: continue
        if (p.id == host.id) continue
        val label = next.rounds.find { it.id == r.roundId }?.label.orEmpty()
        out += NewNotification(p.userId, next.id, "${host.displayName} 총무가 ${label}를 면제해줬어요 🎁", next.title, Target.Room(next.id))
    }

    // ── 결제자가 계좌를 처음 등록함: 그 사람에게 보낼 돈이 있는 사람에게 (계좌가 없어 [보냈어요]가 꺼져 있었다) ──
    for (p in next.participants) {
        val before = prev.participants.find { it.id == p.id } ?: continue
        if (before.payout != null || p.payout == null) continue
        for (t in next.transfers.filter { it.toParticipantId == p.id && it.status != TransferStatus.CONFIRMED }) {
            out += NewNotification(
                userOf(t.fromParticipantId), next.id, "${p.displayName}님이 계좌를 등록했어요. 이제 보낼 수 있어요",
                "${next.title} · ${won(t.amount)}", Target.Pay(next.id),
            )
        }
    }

    // ── 인원보다 더 들어옴: 총무에게 (CTO 결정 2026-10-09) — 넘는 순간 한 번 ──
    val hc = next.headcount
    if (next.status == GatheringStatus.OPEN && hc != null && next.participants.size > hc &&
        prev.participants.size <= (prev.headcount ?: Int.MAX_VALUE)) {
        out += NewNotification(
            next.host().userId, next.id, "현재 ${next.participants.size}명이 참여했어요. 인원이 맞는지 확인해주세요",
            "그대로면 ${hc}명으로 계산되고 마지막에 들어온 사람은 빠져요", Target.Room(next.id),
        )
    }

    // ── 정산됨: 참여자 모두에게 "입금액을 확인해주세요" ──
    if (prev.status == GatheringStatus.OPEN && next.status != GatheringStatus.OPEN) {
        // 총무에게(FC-020 SETTLED_HOST) — 자동 정산이면 총무는 누가 마지막 응답을 넣었는지 모른다. 직접 정산했으면 본인이라 빠진다
        out += NewNotification(next.host().userId, next.id, "계산이 끝났어요. 단톡방에 입금 요청을 보내주세요", next.title, Target.Room(next.id))
        val autoIds = next.responses.filter { it.source == ResponseSource.AUTO }.map { it.participantId }.toSet()
        for (p in next.participants) {
            if (p.id == host.id) continue
            val outgoing = next.transfers.filter { it.fromParticipantId == p.id }
            val auto = if (p.id in autoIds) " · 응답이 없어 전 차수 참석·알코올로 계산됐어요" else ""
            if (outgoing.isNotEmpty()) {
                val first = outgoing.first()
                val more = if (outgoing.size > 1) " 외 ${outgoing.size - 1}건" else ""
                out += NewNotification(
                    p.userId, next.id, "정산이 나왔어요! 입금액을 확인해주세요",
                    "${next.title} · ${next.nameOf(first.toParticipantId)}님께 ${won(first.amount)}$more$auto",
                    Target.Pay(next.id),
                )
            } else {
                out += NewNotification(p.userId, next.id, "정산이 나왔어요", "${next.title} · 보낼 돈이 없어요$auto", Target.Room(next.id))
            }
        }
        // 자동응답은 총무에게도 알린다(REQUIREMENTS 자동응답 규칙)
        if (autoIds.isNotEmpty()) {
            out += NewNotification(
                host.userId, next.id, "자동응답으로 계산된 사람이 있어요",
                "${next.title} · ${autoIds.joinToString("·") { next.nameOf(it) }}님은 전 차수 참석·알코올로 계산됐어요",
                Target.Room(next.id),
            )
        }
    }

    // ── 송금 상태 변화 ──
    for (t in next.transfers) {
        val before = prev.transfers.find { it.id == t.id } ?: continue
        if (before.status == TransferStatus.WAITING && t.status == TransferStatus.SENT) {
            out += NewNotification(
                userOf(t.toParticipantId), next.id,
                "${next.nameOf(t.fromParticipantId)}님이 보냈대요. 입금을 확인해주세요",
                "${next.title} · ${won(t.amount)}", Target.Room(next.id),
            )
        }
        if (before.status == TransferStatus.SENT && t.status == TransferStatus.WAITING && t.notReceivedAt != before.notReceivedAt) {
            out += NewNotification(
                userOf(t.fromParticipantId), next.id,
                "${next.nameOf(t.toParticipantId)}님이 아직 입금을 확인 못 했대요",
                "${next.title} · ${won(t.amount)} · 보낸 내역을 확인해주세요", Target.Pay(next.id),
            )
        }
    }

    // ── 모두 입금 완료 ──
    if (prev.status != GatheringStatus.COMPLETED && next.status == GatheringStatus.COMPLETED) {
        for (p in next.participants) {
            out += NewNotification(p.userId, next.id, "정산 완료! 🎉", "${next.title} · 모두 입금했어요. 7일 뒤 사라져요", Target.Room(next.id))
        }
    }
    return out
}
