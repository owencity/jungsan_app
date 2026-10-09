package app.jeongsan.v3

import app.jeongsan.v3.api.ServerGathering
import app.jeongsan.v3.api.ServerJoinPreview
import app.jeongsan.v3.api.ServerPreview
import app.jeongsan.v3.api.entryChoiceLabel
import app.jeongsan.v3.api.toEntryRooms
import app.jeongsan.v3.api.toPreview
import app.jeongsan.v3.api.toRoom
import app.jeongsan.v3.api.toRooms
import app.jeongsan.v3.api.viewedUnitIds
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 응답(API v5) → 화면 정산방 — 웹 `serverModel.test.ts`와 같은 규칙·같은 픽스처 */
class ServerModelTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val asB = json.decodeFromString<ServerGathering>(Fixtures.DETAIL_B) // 이민지(userId 2) — 두 단위 모두에
    private val asC = json.decodeFromString<ServerGathering>(Fixtures.DETAIL_C) // 박재훈(userId 3) — 김동규 단위에만

    @Test fun 내가_들어_있는_단위마다_정산방이_생기고_들어_있지_않은_단위는_보이지_않는다() {
        assertEquals(listOf(1L, 2L), asB.toRooms().map { it.id })
        assertEquals(listOf(1L), asC.toRooms().map { it.id })
    }

    @Test fun 정산방의_총무_상태_입력_버전은_그_단위의_것이다() {
        val (a, b) = asB.toRooms()
        assertEquals(1L, a.gatheringId); assertEquals(1L, a.hostUserId); assertEquals(GatheringStatus.SETTLING, a.status); assertEquals(5, a.inputRevision)
        assertEquals(1L, b.gatheringId); assertEquals(2L, b.hostUserId); assertEquals(GatheringStatus.OPEN, b.status); assertEquals(1, b.inputRevision)
    }

    @Test fun 차수_응답_송금은_그_단위_것만_3차는_이민지_정산방에만_있다() {
        val (a, b) = asB.toRooms()
        assertEquals(listOf("1차", "2차"), a.rounds.map { it.label })
        assertEquals(listOf("3차"), b.rounds.map { it.label })
        assertEquals(2, a.transfers.size)
        assertEquals(0, b.transfers.size)
        assertTrue(a.responses.all { it.roundId == 1L || it.roundId == 2L })
    }

    @Test fun 명단은_그_단위에_든_사람만_이민지_단위에는_박재훈이_없다() {
        assertEquals(listOf("김동규", "이민지"), asB.toRooms()[1].participants.map { it.displayName })
    }

    @Test fun 총무가_둘이면_이름에_담당_차수를_붙여_H1에서_두_줄을_구분한다() {
        val (a, b) = asB.toRooms()
        assertEquals("10/9 술자리 · 1차부터", a.title)
        assertEquals("10/9 술자리 · 3차부터", b.title)
        assertEquals("10/9 술자리", asC.copy(settlementUnits = asC.settlementUnits.take(1)).toRooms()[0].title)
    }

    @Test fun 타임라인은_술자리_하나에_하나라_어느_정산방에서_봐도_같다() {
        val (a, b) = asB.toRooms()
        assertEquals(a.timeline, b.timeline)
        assertTrue(a.timeline.any { it.body == "2차는 먼저 들어가요!" })
    }

    @Test fun 계좌는_서버가_보여준_만큼만_보낼_사람의_계좌는_있고_그_밖은_없다() {
        val a = asB.toRooms()[0]
        assertEquals("3333012345678", a.participants.first { it.userId == 1L }.payout?.accountNo)
        assertNull(a.participants.first { it.userId == 3L }.payout)
    }

    @Test fun 변환한_정산방으로_지금_할_일이_그대로_나온다() {
        val (a, b) = asB.toRooms()
        assertTrue(nextAction(a, 2).banner.contains("김동규님께"))
        assertNotNull(nextAction(b, 2).action)
    }

    @Test fun 정산금액을_열어봤는지는_단위별이다() {
        assertFalse(1L in asB.viewedUnitIds())
    }

    @Test fun 서버_미리보기를_R3_모양으로_inputHash를_정산하기에_그대로_넘긴다() {
        val p = json.decodeFromString<ServerPreview>(Fixtures.PREVIEW).toPreview()
        assertEquals(5, p.inputRevision)
        assertTrue(Regex("^[a-f0-9]{64}$").matches(p.inputHash!!))
        val t = p.transfers[0]
        assertEquals(2L, t.fromParticipantId); assertEquals(1L, t.toParticipantId); assertEquals(93_334L, t.amount)
        assertTrue(p.lines.first { it.participantId == 3L }.auto)
    }

    @Test fun 새_차수_이름은_술자리_전체_번호를_잇는다() {
        val empty = asB.settlementUnits[1].copy(id = 9)
        val room = asB.copy(settlementUnits = asB.settlementUnits + empty).toRoom(empty)
        assertTrue(room.rounds.isEmpty())
        assertEquals("4차", room.nextRoundLabel())
    }

    @Test fun 링크_입구에서는_정산_전_단위만_고를_수_있다() {
        val pub = json.decodeFromString<ServerJoinPreview>(Fixtures.JOIN_PUBLIC)
        assertEquals("김동규님 · 금액 넣는 중", pub.toEntryRooms("tok")[0].entryChoiceLabel())
        val two = pub.copy(settlementUnits = listOf(
            pub.settlementUnits[0].copy(status = "SETTLING"),
            app.jeongsan.v3.api.SJoinUnit(2, "OPEN", app.jeongsan.v3.api.SJoinHost("이민지", 3), listOf(app.jeongsan.v3.api.SJoinRound(9, 3, 60_000))),
        ))
        val rooms = two.toEntryRooms("tok")
        assertEquals(listOf(2L), rooms.map { it.id })
        assertEquals("이민지님 · 3차", rooms[0].entryChoiceLabel())
    }
}
