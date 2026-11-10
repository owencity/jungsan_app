package app.jeongsan.v3

import app.jeongsan.v3.api.MemoryTokenStore
import app.jeongsan.v3.api.TermsConsent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsConsentTest {
    @Test fun Apple_이름이_있고_로그인을_복원해도_동의기록이_없으면_약관확인이_필요하다() {
        val saved = mutableMapOf<Long, MemoryTokenStore>()
        val terms = TermsConsent { saved.getOrPut(it) { MemoryTokenStore() } }
        val restored = User(id = 7, displayName = "Apple 이름", spoonCount = 10, needsName = false)
        assertFalse(restored.needsName)
        assertFalse(terms.accepted(restored.id))
        assertFalse(terms.accepted(0))
    }

    @Test fun 확인한_계정의_동의만_저장하며_앱을_다시_열어도_같은_계정은_다시_묻지_않는다() {
        val saved = mutableMapOf<Long, MemoryTokenStore>()
        fun reopen() = TermsConsent { saved.getOrPut(it) { MemoryTokenStore() } }
        reopen().accept(7)
        assertTrue(reopen().accepted(7))
        assertFalse(reopen().accepted(8))
        reopen().accept(8)
        assertTrue(reopen().accepted(7))
        assertTrue(reopen().accepted(8))
    }

    @Test fun 이름을_고쳐도_동의는_계정_ID별로_유지하며_동명이인에게_넘기지_않는다() {
        val saved = mutableMapOf<Long, MemoryTokenStore>()
        val terms = TermsConsent { saved.getOrPut(it) { MemoryTokenStore() } }
        val first = User(id = 7, displayName = "김민지", spoonCount = 0)
        terms.accept(first.id)
        val renamed = first.copy(displayName = "민지")
        val sameNameOtherAccount = first.copy(id = 8)
        assertTrue(terms.accepted(renamed.id))
        assertFalse(terms.accepted(sameNameOtherAccount.id))
    }

    @Test fun 이전_버전의_동의와_잘못된_저장값은_현재_약관에_동의한_것으로_보지_않는다() {
        val saved = MemoryTokenStore("previous-terms")
        val terms = TermsConsent { saved }
        assertFalse(terms.accepted(7))
        saved.set("")
        assertFalse(terms.accepted(7))
        terms.accept(7)
        assertTrue(terms.accepted(7))
    }

    @Test fun 인증된_사용자_ID가_없으면_동의를_기록할_수_없다() {
        var storageAccesses = 0
        val terms = TermsConsent { storageAccesses++; MemoryTokenStore() }
        assertFailsWith<IllegalArgumentException> { terms.accept(0) }
        assertFailsWith<IllegalArgumentException> { terms.accept(-1) }
        assertEquals(0, storageAccesses)
    }
}
