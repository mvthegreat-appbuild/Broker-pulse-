package com.example

import com.example.data.local.AlertEventType
import com.example.data.local.CallType
import com.example.data.local.EntryTriggerDirection
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.domain.TradeEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TradeEvaluatorTest {

    private fun sampleBuy(
        entry: Double = 1000.0,
        target: Double = 1150.0,
        sl: Double = 940.0,
        status: TradeStatus = TradeStatus.WATCHING_ENTRY,
        direction: EntryTriggerDirection = EntryTriggerDirection.BELOW_OR_EQUAL
    ) = RecommendationEntity(
        id = 1L,
        symbol = "RELIANCE.NS",
        companyName = "Reliance Industries",
        brokerage = "Motilal Oswal",
        sourceName = "Economic Times",
        callType = CallType.BUY.name,
        quantity = 10,
        entryPrice = entry,
        targetPrice = target,
        stopLoss = sl,
        currentPrice = 1020.0,
        status = status.name,
        entryTriggerDirection = direction.name
    )

    @Test
    fun buyTransitionsFromWatchingToActiveWhenEntryPriceHit() {
        val watching = sampleBuy()
        val result = TradeEvaluator.evaluatePriceTick(watching, liveCmp = 998.0, nowMillis = 2000L)

        assertEquals(TradeStatus.ACTIVE, result.updatedRecommendation.parsedStatus)
        assertNotNull(result.updatedRecommendation.entryTriggeredAt)
        assertEquals(1, result.emittedEvents.size)
        assertEquals(AlertEventType.ENTRY_TRIGGERED, result.emittedEvents.first().parsedType)
    }

    @Test
    fun activeBuyTransitionsToTargetHitAndUpdatesRealizedPnl() {
        val active = sampleBuy(status = TradeStatus.ACTIVE)
        val result = TradeEvaluator.evaluatePriceTick(active, liveCmp = 1155.0, nowMillis = 5000L)

        assertEquals(TradeStatus.TARGET_HIT, result.updatedRecommendation.parsedStatus)
        assertEquals(1155.0, result.updatedRecommendation.exitPrice ?: 0.0, 0.01)
        assertEquals(1, result.emittedEvents.size)
        assertEquals(AlertEventType.TARGET_HIT, result.emittedEvents.first().parsedType)
        assertEquals(1550.0, TradeEvaluator.computePnlAmount(result.updatedRecommendation), 0.01)
        assertEquals(15.5, TradeEvaluator.computeReturnPercent(result.updatedRecommendation), 0.01)
    }

    @Test
    fun activeBuyTransitionsToStopLossHitAutomatically() {
        val active = sampleBuy(status = TradeStatus.ACTIVE)
        val result = TradeEvaluator.evaluatePriceTick(active, liveCmp = 935.0, nowMillis = 6000L)

        assertEquals(TradeStatus.STOPLOSS_HIT, result.updatedRecommendation.parsedStatus)
        assertEquals(935.0, result.updatedRecommendation.exitPrice ?: 0.0, 0.01)
        assertEquals(1, result.emittedEvents.size)
        assertEquals(AlertEventType.STOPLOSS_HIT, result.emittedEvents.first().parsedType)
        assertEquals(-650.0, TradeEvaluator.computePnlAmount(result.updatedRecommendation), 0.01)
    }

    @Test
    fun sellCallHitsTargetWhenPriceDropsBelowTarget() {
        val sellActive = RecommendationEntity(
            id = 2L,
            symbol = "INFY.NS",
            companyName = "Infosys",
            brokerage = "SBI Securities",
            sourceName = "NDTV Profit",
            callType = CallType.SELL.name,
            quantity = 5,
            entryPrice = 1500.0,
            targetPrice = 1400.0,
            stopLoss = 1550.0,
            currentPrice = 1490.0,
            status = TradeStatus.ACTIVE.name
        )
        val result = TradeEvaluator.evaluatePriceTick(sellActive, liveCmp = 1395.0, nowMillis = 9000L)

        assertEquals(TradeStatus.TARGET_HIT, result.updatedRecommendation.parsedStatus)
        assertEquals(525.0, TradeEvaluator.computePnlAmount(result.updatedRecommendation), 0.01)
        assertEquals(7.0, TradeEvaluator.computeReturnPercent(result.updatedRecommendation), 0.01)
    }
}
