package app.jeongsan.v3

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A1 계좌 등록 — 웹 `account.test.ts`와 같은 케이스. */
private const val SETTLING = 102L // 박재훈(3, 참여자 23)은 2차 결제자인데 계좌가 없다

class AccountTest {
    private lateinit var s: V3Store
    private fun room(id: Long = SETTLING) = s.state.rooms.getValue(id)
    private val kb = Payout("국민", "123-45-678901", "박재훈")

    @BeforeTest fun setUp() {
        s = V3Store()
        s.actAs(3)
    }

    @Test fun 정상_입력은_막지_않는다() {
        assertEquals(emptyList(), validatePayout(kb))
    }

    @Test fun 은행_계좌번호_예금주가_비면_막는다() {
        assertEquals(
            listOf("은행을 골라주세요", "계좌번호를 확인해주세요 (숫자 8~16자리)", "예금주를 넣어주세요"),
            validatePayout(Payout("", "", " ")),
        )
    }

    @Test fun 계좌번호는_하이픈을_빼고_8에서_16자리여야_한다() {
        assertTrue("계좌번호를 확인해주세요 (숫자 8~16자리)" in validatePayout(kb.copy(accountNo = "1234-567")))
        assertEquals(emptyList(), validatePayout(kb.copy(accountNo = "1234-5678")))
    }

    @Test fun 계좌_복사는_숫자만_복사한다() {
        // 이체 화면에 하이픈·은행 이름이 섞이면 잘린다
        assertEquals("100012345678", copyableAccountNo("1000-1234-5678"))
        assertEquals("3333012345678", copyableAccountNo("3333-01-2345678"))
    }

    @Test fun 계좌번호_칸은_숫자와_하이픈만_받는다() {
        assertEquals("123-45678901", cleanAccountNo("국민 123-45 678901 "))
    }

    @Test fun 계좌_없는_결제자의_줄엔_계좌_등록_뱃지가_먼저_붙고_누르면_계좌_등록으로_간다() {
        assertEquals("계좌 등록", rowBadge(room(), 3, false))
        assertEquals(Target.Account(SETTLING), entryTarget(room(), 3, false))
    }

    @Test fun 등록하고_나면_뱃지와_진입이_원래_순서로_돌아간다() {
        s.registerPayout(kb)
        assertEquals("정산금액 확인", rowBadge(room(), 3, false))
        assertEquals(Target.Pay(SETTLING), entryTarget(room(), 3, false))
    }

    @Test fun 등록하면_결제자의_첫_할_일이_사라진다() {
        assertEquals(ActionKind.REGISTER_ACCOUNT, nextAction(room(), 3).action?.kind)
        s.registerPayout(kb)
        assertNotEquals(ActionKind.REGISTER_ACCOUNT, nextAction(room(), 3).action?.kind)
    }

    @Test fun 진행_중인_술자리_모두에_같은_계좌가_들어가고_완료된_기록은_그대로다() {
        val done = room(103).participantOfUser(3)?.payout
        s.registerPayout(kb)
        assertEquals(kb, room().participantOfUser(3)?.payout)
        assertEquals(kb, room(101).participantOfUser(3)?.payout)
        assertEquals(done, room(103).participantOfUser(3)?.payout)
    }

    @Test fun 보는_사람을_바꿨다_돌아와도_등록한_계좌가_남는다() {
        s.registerPayout(kb)
        s.actAs(1)
        s.actAs(3)
        assertEquals(kb, s.state.me.payout)
    }

    @Test fun 처음_등록하면_보낼_사람에게_알림이_가고_누르면_보낼_돈으로_간다() {
        s.registerPayout(kb)
        val n = s.state.notifications.first { it.userId == 1L && it.roomId == SETTLING }
        assertEquals("박재훈님이 계좌를 등록했어요. 이제 보낼 수 있어요", n.title)
        assertEquals(Target.Pay(SETTLING), n.target)
        assertEquals("박재훈님이 받을 계좌를 등록했어요", room().timeline.last().body)
    }
}
