package app.jeongsan.v3

import app.jeongsan.domain.Id
import app.jeongsan.util.won
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 정산방(R1) 맨 위 "지금 할 일" 한 줄과 하단 버튼 하나 — `docs/SCREENS.md` §4.
 *
 * **웹 `v3/nextAction.ts`가 원본이다.** 같은 조건·같은 순서·같은 문구. 웹 테스트 케이스를
 * `commonTest`의 `NextActionTest`로 옮겨 두 플랫폼이 같은 입력에 같은 배너를 내는지 지킨다.
 */
enum class ActionKind {
    EDIT_FIRST_ROUND, SHARE, SETTLE, VIEW_PAY, CONFIRM_INCOMING,
    REGISTER_ACCOUNT, RESPOND, EDIT_RESPONSE, RESEND, GIVE_SPOON,
    /** 정산 뒤 총무가 단톡방에 사람별 금액·계좌를 보낸다(FC-020) */
    REQUEST_PAYMENT,
    /** 인원보다 더 들어온 사람까지 함께 정산 — 인원을 들어온 사람 수로 늘린다(FC-020) */
    INCLUDE_EXTRA,
}

enum class Tone { TODO, WAIT, DONE }

data class Action(val kind: ActionKind, val label: String)

data class NextAction(
    /** 배너 한 줄 */
    val banner: String,
    val tone: Tone,
    /** 하단 버튼. 없으면 버튼을 그리지 않는다 */
    val action: Action? = null,
    /** 배너 아래 보조 한 줄 (자동응답 안내 등) */
    val note: String? = null,
)

fun nextAction(g: Gathering, meUserId: Id, now: Instant = Clock.System.now()): NextAction {
    val me = g.participantOfUser(meUserId)
        ?: return NextAction("참여하고 체크해 주세요", Tone.TODO, Action(ActionKind.RESPOND, "참여하고 체크하기"))

    val isHost = g.hostUserId == meUserId
    val autoNote = if (g.responses.any { it.participantId == me.id && it.source == ResponseSource.AUTO }) {
        "응답이 없어 전 차수 참석·알코올로 계산됐어요"
    } else {
        null
    }

    if (g.status == GatheringStatus.COMPLETED) {
        val days = g.daysUntilDelete(now) ?: 7
        val canSpoon = !isHost && me.id !in g.spoonGivers
        return NextAction(
            "정산 끝! ${days}일 뒤 사라져요", Tone.DONE,
            if (canSpoon) Action(ActionKind.GIVE_SPOON, "🥄 한 스푼") else null, autoNote,
        )
    }

    // 결제자인데 계좌가 없으면 무엇보다 먼저
    if (g.roundsPaidBy(me.id).isNotEmpty() && me.payout == null) {
        return NextAction("받을 계좌를 등록해주세요", Tone.TODO, Action(ActionKind.REGISTER_ACCOUNT, "계좌 등록"), autoNote)
    }

    if (g.status == GatheringStatus.OPEN) {
        if (isHost) {
            if (g.rounds.isEmpty()) return NextAction("1차 금액을 넣어주세요", Tone.TODO, Action(ActionKind.EDIT_FIRST_ROUND, "1차 입력"))
            if (g.participants.size == 1) {
                return NextAction("링크를 보내서 사람들을 불러주세요", Tone.TODO, Action(ActionKind.SHARE, "링크 공유"))
            }
            val waiting = g.unrespondedParticipants().size
            val banner = when {
                g.autoSettlementError != null -> "예상 금액을 계산하지 못했어요 · 입력을 확인해주세요"
                g.headcount != null && g.participants.size > g.headcount -> "예상 ${g.headcount}명보다 많은 ${g.participants.size}명이 응답 명단에 있어요"
                waiting > 0 -> "${waiting}명이 아직 응답 안 했어요 · 명단을 확인해주세요"
                else -> "모두 응답했어요"
            }
            return NextAction(banner, if (waiting > 0) Tone.WAIT else Tone.TODO,
                Action(ActionKind.SETTLE, "명단 확인 · 정산 확정"),
                "명단에서 잘못된 응답자를 제외하고 예상 금액을 확인한 뒤 확정해주세요.")
        }
        val labels = g.rounds.joinToString("·") { it.label }
        return if (g.hasResponded(me.id)) {
            NextAction(
                "응답을 총무에게 보냈어요. 총무가 확인 후 정산해요",
                Tone.WAIT, Action(ActionKind.EDIT_RESPONSE, "응답 고치기"),
            )
        } else {
            NextAction("$labels 응답을 남겨주세요", Tone.TODO, Action(ActionKind.RESPOND, "응답하기"))
        }
    }

    // ── SETTLING ──
    // 순서 원칙: 남을 막고 있는 일이 먼저다(SCREENS.md §4). [보냈어요]를 누른 사람은 내 확인만
    // 기다리며 멈춰 있지만, 내가 보낼 돈은 나만 늦어진다.
    val outgoing = g.transfers.filter { it.fromParticipantId == me.id }
    val incoming = g.transfers.filter { it.toParticipantId == me.id }

    incoming.find { it.status == TransferStatus.SENT }?.let { sentIn ->
        return NextAction(
            "${g.nameOf(sentIn.fromParticipantId)}님이 보냈대요. 확인해주세요", Tone.TODO,
            Action(ActionKind.CONFIRM_INCOMING, "입금 확인하기"),
        )
    }
    outgoing.find { it.status == TransferStatus.WAITING && it.notReceivedAt != null }?.let { bounced ->
        return NextAction(
            "${g.nameOf(bounced.toParticipantId)}님이 아직 입금을 확인 못 했어요", Tone.TODO,
            Action(ActionKind.VIEW_PAY, "다시 확인 요청"), autoNote,
        )
    }
    outgoing.find { it.status == TransferStatus.WAITING }?.let { toSend ->
        return NextAction(
            "${g.nameOf(toSend.toParticipantId)}님께 ${won(toSend.amount)}을 보내주세요", Tone.TODO,
            Action(ActionKind.VIEW_PAY, "보낼 돈 보기"), autoNote,
        )
    }
    val waitingIn = incoming.count { it.status == TransferStatus.WAITING }
    if (waitingIn > 0) {
        // 총무는 계산이 끝나면 단톡방에 입금 요청을 돌린다(FC-020) — 사람별 금액·계좌가 담긴 문구
        return if (isHost) {
            NextAction("계산 끝! 단톡방에 입금 요청을 보내주세요", Tone.TODO, Action(ActionKind.REQUEST_PAYMENT, "입금 요청 보내기"))
        } else {
            NextAction("${waitingIn}명 입금 기다리는 중", Tone.WAIT, Action(ActionKind.SHARE, "링크 다시 공유"))
        }
    }
    if (outgoing.any { it.status == TransferStatus.SENT }) {
        return NextAction("확인 기다리는 중이에요", Tone.WAIT, note = autoNote)
    }
    // 내 송금이 모두 확인됨
    if (!isHost && me.id !in g.spoonGivers) {
        return NextAction("끝! ${g.host().displayName} 총무에게 한 스푼 어때요?", Tone.DONE, Action(ActionKind.GIVE_SPOON, "🥄 한 스푼"))
    }
    return NextAction("다른 사람들 입금을 기다리는 중이에요", Tone.WAIT)
}

/** 미리보기 진입 권한. 목표 인원은 제한이 아니다. */
fun canSettleNow(g: Gathering, meUserId: Id): Boolean =
    g.status == GatheringStatus.OPEN && g.hostUserId == meUserId && g.rounds.isNotEmpty() && g.participants.size >= 2
