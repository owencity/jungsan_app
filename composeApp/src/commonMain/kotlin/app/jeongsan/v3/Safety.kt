package app.jeongsan.v3

import app.jeongsan.domain.Id

/**
 * 정산방 메시지 신고·차단(FC-024, App Store 1.2 사용자 생성 콘텐츠) — 웹 `v3/safety.ts`와 같은 규칙.
 *
 * - 내가 신고한 메시지 → 나에게서 숨김
 * - 서로 다른 2명이 신고한 메시지 → 모두에게서 숨김(CTO 결정 2026-10-20)
 * - 내가 차단한 사람이 쓴 메시지 → 모든 술자리에서 나에게서 숨김. 명단·금액·송금은 그대로(돈 계산을 깨지 않는다)
 * 시스템 소식(SYSTEM·SPOON)은 숨기지 않고 신고·차단 메뉴도 없다. 서버도 같은 기준으로 타임라인을 걸러 준다.
 */
enum class ReportReason(val label: String) {
    ABUSE("욕설·비방"),
    SEXUAL("음란·불쾌한 내용"),
    SPAM("스팸·광고"),
    OTHER("기타"),
}

/** 서로 다른 신고자가 이만큼 모이면 모두에게서 숨긴다 */
const val HIDE_FOR_ALL_REPORTS = 2

data class Report(val key: String, val reporterUserId: Id)
data class Block(val blockerUserId: Id, val blockedUserId: Id, val displayName: String)
data class Moderation(val reports: List<Report> = emptyList(), val blocks: List<Block> = emptyList())

/** 메시지의 전역 열쇠 — 한 술자리의 정산방들은 타임라인을 같이 쓴다 */
fun Gathering.entryKey(entryId: Id) = "${gatheringId ?: id}:$entryId"

private fun Gathering.authorUserId(e: TimelineEntry) = participants.find { it.id == e.authorParticipantId }?.userId

/** 이 사람이 이 메시지를 신고·차단할 수 있나 — 남이 쓴 메시지만 */
fun Gathering.canModerate(e: TimelineEntry, viewerUserId: Id): Boolean {
    if (e.type != TimelineType.MESSAGE) return false
    val author = authorUserId(e) ?: return false
    return author != viewerUserId
}

/** 이 사람에게 보여 줄 타임라인 */
fun Gathering.visibleTimeline(viewerUserId: Id, m: Moderation): List<TimelineEntry> {
    val blocked = m.blocks.filter { it.blockerUserId == viewerUserId }.map { it.blockedUserId }.toSet()
    return timeline.filter { e ->
        if (e.type != TimelineType.MESSAGE) return@filter true
        val key = entryKey(e.id)
        val reporters = m.reports.filter { it.key == key }.map { it.reporterUserId }.toSet()
        if (viewerUserId in reporters || reporters.size >= HIDE_FOR_ALL_REPORTS) return@filter false
        val author = authorUserId(e)
        author == null || author !in blocked
    }
}

/** 내가 차단한 사람 목록(계정 화면) */
fun Moderation.myBlocks(viewerUserId: Id) = blocks.filter { it.blockerUserId == viewerUserId }
