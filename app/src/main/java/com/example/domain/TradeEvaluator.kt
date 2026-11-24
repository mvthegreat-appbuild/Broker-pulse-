package com.example.domain

import com.example.data.local.AlertEventEntity
import com.example.data.local.AlertEventType
import com.example.data.local.CallType
import com.example.data.local.EntryTriggerDirection
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import java.util.Locale

data class EvaluationResult(
    val updatedRecommendation: RecommendationEntity,
    val emittedEvents: List<AlertEventEntity>
)

object TradeEvaluator {

    fun determineInitialStatusAndDirection(
        callType: CallType,
        entryPrice: Double,
        targetPrice: Double,
        stopLoss: Double,
        liveCmp: Double,
        enterImmediatelyIfTriggered: Boolean = true
    ): Pair<TradeStatus, EntryTriggerDirection> {
        if (liveCmp <= 0.0) {
            val defaultDir = if (callType == CallType.BUY) {
                EntryTriggerDirection.BELOW_OR_EQUAL
            } else {
                EntryTriggerDirection.ABOVE_OR_EQUAL
            }
            return TradeStatus.WATCHING_ENTRY to defaultDir
        }

        // Direction is inferred relative to current live CMP:
        // If user sets Entry <= CMP for BUY (buy on dip) -> BELOW_OR_EQUAL;
        // If user sets Entry > CMP for BUY (breakout buy) -> ABOVE_OR_EQUAL.
        val direction = if (liveCmp > entryPrice) {
            EntryTriggerDirection.BELOW_OR_EQUAL
        } else {
            EntryTriggerDirection.ABOVE_OR_EQUAL
        }

        if (!enterImmediatelyIfTriggered) {
            return TradeStatus.WATCHING_ENTRY to direction
        }

        val withinTolerance = kotlin.math.abs(liveCmp - entryPrice) / entryPrice <= 0.0025
        return if (withinTolerance) {
            val initial = evaluateTargetOrStop(callType, liveCmp, targetPrice, stopLoss)
                ?: TradeStatus.ACTIVE
            initial to direction
        } else {
            TradeStatus.WATCHING_ENTRY to direction
        }
    }

    fun evaluatePriceTick(
        rec: RecommendationEntity,
        liveCmp: Double,
        previousClose: Double = rec.previousClose,
        dayChangePercent: Double = rec.dayChangePercent,
        sparklineCsv: String = rec.sparklineCsv,
        nowMillis: Long = System.currentTimeMillis()
    ): EvaluationResult {
        if (liveCmp <= 0.0) {
            return EvaluationResult(rec, emptyList())
        }

        var working = rec.copy(
            currentPrice = liveCmp,
            previousClose = if (previousClose > 0.0) previousClose else rec.previousClose,
            dayChangePercent = dayChangePercent,
            sparklineCsv = sparklineCsv.ifBlank { rec.sparklineCsv },
            lastPriceUpdatedAt = nowMillis
        )
        val events = mutableListOf<AlertEventEntity>()

        if (working.parsedStatus == TradeStatus.WATCHING_ENTRY) {
            val entryHit = when (working.parsedEntryDirection) {
                EntryTriggerDirection.BELOW_OR_EQUAL -> liveCmp <= working.entryPrice
                EntryTriggerDirection.ABOVE_OR_EQUAL -> liveCmp >= working.entryPrice
            }
            if (entryHit) {
                working = working.copy(
                    status = TradeStatus.ACTIVE.name,
                    entryTriggeredAt = nowMillis
                )
                events += AlertEventEntity(
                    recommendationId = working.id,
                    symbol = working.symbol,
                    companyName = working.companyName,
                    brokerage = working.brokerage,
                    eventType = AlertEventType.ENTRY_TRIGGERED.name,
                    triggerPrice = liveCmp,
                    message = "${working.companyName} (${shortSymbol(working.symbol)}) hit Entry ₹${formatPrice(working.entryPrice)} at CMP ₹${formatPrice(liveCmp)}. Added to Portfolio.",
                    timestamp = nowMillis
                )
            }
        }

        if (working.parsedStatus == TradeStatus.ACTIVE) {
            val exitStatus = evaluateTargetOrStop(
                callType = working.parsedCallType,
                liveCmp = liveCmp,
                targetPrice = working.targetPrice,
                stopLoss = working.stopLoss
            )
            if (exitStatus == TradeStatus.TARGET_HIT) {
                val pnlAmount = computePnlAmount(working, liveCmp)
                val pnlPct = computeReturnPercent(working, liveCmp)
                working = working.copy(
                    status = TradeStatus.TARGET_HIT.name,
                    closedAt = nowMillis,
                    exitPrice = liveCmp
                )
                events += AlertEventEntity(
                    recommendationId = working.id,
                    symbol = working.symbol,
                    companyName = working.companyName,
                    brokerage = working.brokerage,
                    eventType = AlertEventType.TARGET_HIT.name,
                    triggerPrice = liveCmp,
                    pnlAmount = pnlAmount,
                    pnlPercent = pnlPct,
                    message = "TARGET HIT: ${working.companyName} (${working.brokerage}) reached ₹${formatPrice(liveCmp)} (+${String.format(Locale.US, "%.2f", pnlPct)}%). Portfolio updated.",
                    timestamp = nowMillis
                )
            } else if (exitStatus == TradeStatus.STOPLOSS_HIT) {
                val pnlAmount = computePnlAmount(working, liveCmp)
                val pnlPct = computeReturnPercent(working, liveCmp)
                working = working.copy(
                    status = TradeStatus.STOPLOSS_HIT.name,
                    closedAt = nowMillis,
                    exitPrice = liveCmp
                )
                events += AlertEventEntity(
                    recommendationId = working.id,
                    symbol = working.symbol,
                    companyName = working.companyName,
                    brokerage = working.brokerage,
                    eventType = AlertEventType.STOPLOSS_HIT.name,
                    triggerPrice = liveCmp,
                    pnlAmount = pnlAmount,
                    pnlPercent = pnlPct,
                    message = "STOP LOSS HIT: ${working.companyName} (${working.brokerage}) hit SL ₹${formatPrice(liveCmp)} (${String.format(Locale.US, "%.2f", pnlPct)}%). Portfolio closed.",
                    timestamp = nowMillis
                )
            }
        }

        return EvaluationResult(working, events)
    }

    private fun evaluateTargetOrStop(
        callType: CallType,
        liveCmp: Double,
        targetPrice: Double,
        stopLoss: Double
    ): TradeStatus? {
        return when (callType) {
            CallType.BUY -> when {
                liveCmp >= targetPrice -> TradeStatus.TARGET_HIT
                liveCmp <= stopLoss -> TradeStatus.STOPLOSS_HIT
                else -> null
            }
            CallType.SELL -> when {
                liveCmp <= targetPrice -> TradeStatus.TARGET_HIT
                liveCmp >= stopLoss -> TradeStatus.STOPLOSS_HIT
                else -> null
            }
        }
    }

    fun computeEffectivePrice(rec: RecommendationEntity): Double {
        return when (rec.parsedStatus) {
            TradeStatus.TARGET_HIT, TradeStatus.STOPLOSS_HIT -> rec.exitPrice ?: rec.currentPrice
            else -> rec.currentPrice
        }
    }

    fun computePnlAmount(rec: RecommendationEntity, priceOverride: Double? = null): Double {
        if (rec.parsedStatus == TradeStatus.WATCHING_ENTRY && priceOverride == null) return 0.0
        val refPrice = priceOverride ?: computeEffectivePrice(rec)
        if (refPrice <= 0.0 || rec.entryPrice <= 0.0) return 0.0
        val diff = if (rec.parsedCallType == CallType.BUY) {
            refPrice - rec.entryPrice
        } else {
            rec.entryPrice - refPrice
        }
        return diff * rec.quantity
    }

    fun computeReturnPercent(rec: RecommendationEntity, priceOverride: Double? = null): Double {
        val refPrice = priceOverride ?: computeEffectivePrice(rec)
        if (refPrice <= 0.0 || rec.entryPrice <= 0.0) return 0.0
        val diff = if (rec.parsedCallType == CallType.BUY) {
            refPrice - rec.entryPrice
        } else {
            rec.entryPrice - refPrice
        }
        return (diff / rec.entryPrice) * 100.0
    }

    fun computeUpsidePotentialPercent(rec: RecommendationEntity): Double {
        val base = if (rec.currentPrice > 0.0) rec.currentPrice else rec.entryPrice
        if (base <= 0.0) return 0.0
        return if (rec.parsedCallType == CallType.BUY) {
            ((rec.targetPrice - base) / base) * 100.0
        } else {
            ((base - rec.targetPrice) / base) * 100.0
        }
    }

    fun computeProgressBetweenSlAndTarget(rec: RecommendationEntity): Float {
        val price = computeEffectivePrice(rec)
        val span = rec.targetPrice - rec.stopLoss
        if (kotlin.math.abs(span) < 0.0001) return 0.5f
        val raw = ((price - rec.stopLoss) / span).toFloat()
        return raw.coerceIn(0f, 1f)
    }

    fun shortSymbol(symbol: String): String =
        symbol.removeSuffix(".NS").removeSuffix(".BO")

    fun formatPrice(value: Double): String =
        String.format(Locale.US, "%,.2f", value)
}
