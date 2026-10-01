package app.jeongsan.v3

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

/**
 * "지금 할 일" 판정 — 웹 `v3/__tests__/nextAction.test.ts`를 **같은 케이스·같은 기대값**으로 옮겼다.
 * 두 플랫폼이 같은 입력에 같은 배너를 내는지가 곧 일치 검증이다.
 */
private const val OPEN = 101L // 동규(1)가 총무, 지영(4)·민수(5) 미응답
private const val SETTLING = 102L // 민지(2)가 총무, 동규(1)는 민지·재훈에게 보낼 돈
private const val COMPLETED = 103L // 재훈(3)이 총무, 민지는 스푼 줬고 동규는 아직

internal fun room(id: Long): Gathering = MockV3.rooms().first { it.id == id }

private fun Gathering.withTransfer(id: Long, fn: (Transfer) -> Transfer) =
    copy(transfers = transfers.map { if (it.id == id) fn(it) else it })

class NextActionTest {
    // ── 총무 — 정산 전 ──

    @Test fun 차수가_없으면_1차_금액_입력이_먼저_뜬다() {
        val g = room(OPEN).copy(rounds = emptyList(), responses = emptyList())
        assertEquals(ActionKind.EDIT_FIRST_ROUND, nextAction(g, 1).action?.kind)
    }

    @Test fun 참여자가_총무뿐이면_링크_공유를_권한다() {
        val g = room(OPEN).let { it.copy(participants = it.participants.filter { p -> p.userId == 1L }) }
        assertEquals(ActionKind.SHARE, nextAction(g, 1).action?.kind)
    }

    @Test fun 미응답자가_있으면_몇_명인지_알려주고_기다리지_않고_정산할_수_있게_정산하기를_준다() {
        val a = nextAction(room(OPEN), 1)
        assertContains(a.banner, "2명이 아직 응답 안 했어요")
        assertEquals(ActionKind.SETTLE, a.action?.kind)
        assertEquals(Tone.WAIT, a.tone)
    }

    @Test fun 모두_응답하면_모두_응답했어요와_정산하기를_준다() {
        val extra = listOf(14L, 15L).flatMap { pid -> listOf(1L, 2L).map { rid -> RoundResponse(pid, rid, ResponseType.DRANK, ResponseSource.SELF) } }
        val g = room(OPEN).let { it.copy(responses = it.responses + extra) }
        val a = nextAction(g, 1)
        assertEquals("모두 응답했어요", a.banner)
        assertEquals(ActionKind.SETTLE, a.action?.kind)
    }

    // ── 참여자 — 정산 전 ──

    @Test fun 참여자가_아니면_참여하고_체크하기를_먼저_권한다() {
        assertEquals("참여하고 체크하기", nextAction(room(OPEN), 99).action?.label)
    }

    @Test fun 응답_전이면_차수_이름을_넣어_응답을_요청한다() {
        val a = nextAction(room(OPEN), 4)
        assertEquals("1차·2차 응답을 남겨주세요", a.banner)
        assertEquals(ActionKind.RESPOND, a.action?.kind)
    }

    @Test fun 응답했으면_기다리는_상태로_바뀌고_응답을_고칠_수_있다() {
        val a = nextAction(room(OPEN), 3)
        assertEquals(Tone.WAIT, a.tone)
        assertEquals(ActionKind.EDIT_RESPONSE, a.action?.kind)
    }

    // ── 송금 중 ──

    @Test fun 보낼_돈이_있으면_받는_사람과_금액을_알려준다() {
        val a = nextAction(room(SETTLING), 1)
        assertEquals("민지님께 41,000원을 보내주세요", a.banner)
        assertEquals(ActionKind.VIEW_PAY, a.action?.kind)
    }

    @Test fun 자동응답으로_계산된_사람에게는_안내_한_줄이_붙는다() {
        assertEquals("응답이 없어 전 차수 참석·알코올로 계산됐어요", nextAction(room(SETTLING), 1).note)
    }

    @Test fun 아직_안_들어왔어요를_받으면_보낼_돈_안내보다_먼저_뜬다() {
        val now = Clock.System.now()
        val g = room(SETTLING).withTransfer(1) { it.copy(sentAt = now, notReceivedAt = now) }
        assertEquals("민지님이 아직 입금을 확인 못 했어요", nextAction(g, 1).banner)
    }

    @Test fun 계좌_없는_결제자에게는_계좌_등록이_무엇보다_먼저_뜬다() {
        // 재훈은 2차 결제자인데 계좌가 없다
        assertEquals(ActionKind.REGISTER_ACCOUNT, nextAction(room(SETTLING), 3).action?.kind)
    }

    @Test fun 남을_막고_있는_일이_먼저다_총무에게_보낼_돈이_남아_있어도_보냈어요_받은_확인이_먼저_뜬다() {
        // 민지(총무)는 재훈에게 보낼 24,000원이 있지만, 재훈이 보낸 29,000원이 확인을 기다린다
        val a = nextAction(room(SETTLING), 2)
        assertEquals("재훈님이 보냈대요. 확인해주세요", a.banner)
        assertEquals(ActionKind.CONFIRM_INCOMING, a.action?.kind)
    }

    @Test fun 확인할_게_없으면_총무도_다른_결제자에게_보낼_돈을_안내받는다() {
        val g = room(SETTLING).withTransfer(3) { it.copy(status = TransferStatus.CONFIRMED) } // 재훈 → 민지 확인 끝
        assertEquals("재훈님께 24,000원을 보내주세요", nextAction(g, 2).banner)
    }

    @Test fun 결제자에게도_같은_원칙_보냈어요_받은_확인이_내_송금의_아직_안_들어왔어요보다_먼저다() {
        val now = Clock.System.now()
        val g = room(SETTLING)
            .let { it.copy(participants = it.participants.map { p -> if (p.id == 23L) p.copy(payout = Payout("국민", "1", "재훈")) else p }) }
            .withTransfer(2) { it.copy(status = TransferStatus.SENT, sentAt = now) } // 동규 → 재훈 보냄
            .withTransfer(3) { it.copy(status = TransferStatus.WAITING, notReceivedAt = now) } // 재훈 → 민지: 안 들어왔대요
        assertEquals("동규님이 보냈대요. 확인해주세요", nextAction(g, 3).banner)
    }

    @Test fun 내_송금이_모두_확인되면_총무에게_한_스푼을_권하고_이미_줬으면_권하지_않는다() {
        val g = room(SETTLING).let {
            it.copy(transfers = it.transfers.map { t -> if (t.fromParticipantId == 22L) t.copy(status = TransferStatus.CONFIRMED) else t })
        }
        assertEquals(ActionKind.GIVE_SPOON, nextAction(g, 1).action?.kind)
        assertNull(nextAction(g.copy(spoonGivers = g.spoonGivers + 22L), 1).action)
    }

    // ── 완료 ──

    @Test fun 삭제까지_남은_날을_보여준다_완료_7일_뒤_삭제() {
        val now = Clock.System.now()
        val g = room(COMPLETED).copy(completedAt = now - 2.days)
        assertEquals("정산 끝! 5일 뒤 사라져요", nextAction(g, 1, now).banner)
    }

    @Test fun 스푼을_아직_안_준_참여자에게만_한_스푼_버튼을_준다() {
        assertEquals(ActionKind.GIVE_SPOON, nextAction(room(COMPLETED), 1).action?.kind) // 동규: 아직
        assertNull(nextAction(room(COMPLETED), 2).action) // 민지: 이미 줌
    }

    @Test fun 총무_자신에게는_스푼_버튼이_없다() {
        assertNull(nextAction(room(COMPLETED), 3).action)
    }
}
