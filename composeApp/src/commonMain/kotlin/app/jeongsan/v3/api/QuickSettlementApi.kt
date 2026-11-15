package app.jeongsan.v3.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
private data class QuickSettlementRequest(val total: Long, val headcount: Int)

@Serializable
data class QuickSettlementResult(
    val total: Long, val headcount: Int, val participantAmount: Long,
    val payerAmount: Long, val incomingTotal: Long,
)

suspend fun ApiClient.quickSettlement(total: Long, headcount: Int): QuickSettlementResult =
    call(HttpMethod.Post, "/api/v1/settlements/quick", QuickSettlementRequest(total, headcount))
