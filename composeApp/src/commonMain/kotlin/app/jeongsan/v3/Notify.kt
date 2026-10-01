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

    // ── 정산됨: 참여자 모두에게 "입금액을 확인해주세요" ──
    if (prev.status == GatheringStatus.OPEN && next.status != GatheringStatus.OPEN) {
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
