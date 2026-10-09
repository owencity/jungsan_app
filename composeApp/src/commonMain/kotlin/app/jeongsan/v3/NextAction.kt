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
            // 인원보다 더 들어왔으면 총무 확인이 먼저(CTO 결정 2026-10-09) — 그대로면 인원만큼만 계산되고 뒤에 온 사람은 빠진다
            val extras = g.extraParticipants()
            if (extras.isNotEmpty()) {
                return NextAction(
                    "현재 ${g.participants.size}명이 참여했어요 · 인원(${g.headcount}명)이 맞는지 확인해주세요", Tone.TODO,
                    Action(ActionKind.INCLUDE_EXTRA, "${g.participants.size}명 모두 포함하기"),
                    "그대로면 ${g.headcount}명으로 계산되고 ${extras.joinToString("·") { it.displayName }}님은 빠져요",
                )
            }
            val waiting = g.unrespondedParticipants().size
            // 인원을 넣었으면 다 모일 때 서버가 자동 정산한다(FC-020) — 총무는 링크만 돌리면 된다.
            // 안 들어오는 사람이 있을 때의 [지금 계산하기]는 R1 배너 아래 작은 버튼(canSettleNow)
            val n = g.headcount
            if (n != null && waiting + maxOf(0, n - g.participants.size) > 0) {
                return NextAction("${n}명 중 ${g.respondedCount()}명 응답했어요 · 다 모이면 자동으로 계산돼요", Tone.WAIT, Action(ActionKind.SHARE, "링크 공유"))
            }
            return if (waiting > 0) {
                NextAction("${waiting}명이 아직 응답 안 했어요 · 준비되면 정산하세요", Tone.WAIT, Action(ActionKind.SETTLE, "지금 계산하기"))
            } else {
                NextAction("모두 응답했어요", Tone.TODO, Action(ActionKind.SETTLE, "지금 계산하기"))
            }
        }
        val labels = g.rounds.joinToString("·") { it.label }
        // 인원 밖에 들어온 사람 — 총무가 포함하면 함께, 아니면 이번 정산에서 빠진다
        if (g.extraParticipants().any { it.id == me.id }) {
            val full = "인원이 다 찼어요 · 총무가 포함하면 함께 정산돼요"
            return if (g.hasResponded(me.id)) {
                NextAction(full, Tone.WAIT, Action(ActionKind.EDIT_RESPONSE, "응답 고치기"))
            } else {
                NextAction("$labels 응답을 남겨주세요", Tone.TODO, Action(ActionKind.RESPOND, "응답하기"), full)
            }
        }
        return if (g.hasResponded(me.id)) {
            NextAction(
                if (g.headcount != null) "응답 완료! 다 모이면 자동으로 계산돼요" else "응답 완료! 총무가 정산하면 알려드릴게요",
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

/**
 * R1 배너 아래 작은 [지금 계산하기](FC-020) — 인원을 넣어 자동 정산을 기다리는 중인데, 끝까지 안 들어오는 사람이 있으면
 * 총무가 직접 마무리한다. 하단 버튼은 [링크 공유]라 이 버튼이 따로 있어야 한다. 웹 `canSettleNow`
 */
fun canSettleNow(g: Gathering, meUserId: Id): Boolean =
    g.status == GatheringStatus.OPEN && g.hostUserId == meUserId && g.headcount != null && g.rounds.isNotEmpty() && g.participants.size >= 2 &&
        // 인원보다 더 들어왔으면 "안 들어온 사람"이 없다 — 그때는 [포함하기]만
        g.extraParticipants().isEmpty()
