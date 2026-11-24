package com.example.data.repository

import com.example.data.local.AlertEventEntity
import com.example.data.local.AlertEventType
import com.example.data.local.BrokerPulseDao
import com.example.data.local.CallType
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.BrokerageFeedItem
import com.example.data.remote.BrokerageFeedService
import com.example.data.remote.LiveQuote
import com.example.data.remote.MarketQuoteService
import com.example.data.remote.StockSearchResult
import com.example.domain.TradeEvaluator
import com.example.notifications.AlertNotificationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class MarketIndicesSnapshot(
    val nifty50: LiveQuote? = null,
    val sensex: LiveQuote? = null
)

class BrokerPulseRepository(
    private val dao: BrokerPulseDao,
    private val quoteService: MarketQuoteService,
    private val feedService: BrokerageFeedService,
    private val prefsRepo: UserPreferencesRepository,
    private val notificationManager: AlertNotificationManager
) {
    val allRecommendations: Flow<List<RecommendationEntity>> = dao.observeAllRecommendations()
    val allAlerts: Flow<List<AlertEventEntity>> = dao.observeAllAlertEvents()
    val unreadAlertCount: Flow<Int> = dao.observeUnreadAlertCount()

    suspend fun refreshMarketIndices(): MarketIndicesSnapshot = coroutineScope {
        val niftyDeferred = async { quoteService.fetchQuote("^NSEI") }
        val sensexDeferred = async { quoteService.fetchQuote("^BSESN") }
        MarketIndicesSnapshot(
            nifty50 = niftyDeferred.await(),
            sensex = sensexDeferred.await()
        )
    }

    suspend fun refreshAllTrackedQuotes(): List<AlertEventEntity> = coroutineScope {
        val items = dao.getAllRecommendationsOnce()
        if (items.isEmpty()) return@coroutineScope emptyList()

        val prefs = prefsRepo.preferences.first()
        val uniqueSymbols = items.map { it.symbol }.distinct()
        val quotesMap = uniqueSymbols.map { sym ->
            async { sym to quoteService.fetchQuote(sym) }
        }.awaitAll().toMap()

        val newEvents = mutableListOf<AlertEventEntity>()

        for (item in items) {
            val quote = quotesMap[item.symbol] ?: continue
            val sparkCsv = if (quote.sparkline.isNotEmpty()) {
                quote.sparkline.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }
            } else {
                item.sparklineCsv
            }

            if (prefs.autoEvaluateTriggers) {
                val result = TradeEvaluator.evaluatePriceTick(
                    rec = item,
                    liveCmp = quote.cmp,
                    previousClose = quote.previousClose,
                    dayChangePercent = quote.dayChangePercent,
                    sparklineCsv = sparkCsv
                )
                dao.updateRecommendation(result.updatedRecommendation)
                for (event in result.emittedEvents) {
                    dao.insertAlertEvent(event)
                    newEvents += event
                    if (prefs.notificationsEnabled && item.alertsEnabled) {
                        notificationManager.dispatchAlert(event)
                    }
                }
            } else {
                dao.updateRecommendation(
                    item.copy(
                        currentPrice = quote.cmp,
                        previousClose = quote.previousClose,
                        dayChangePercent = quote.dayChangePercent,
                        sparklineCsv = sparkCsv,
                        lastPriceUpdatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        newEvents
    }

    suspend fun fetchLiveQuote(symbol: String): LiveQuote? = quoteService.fetchQuote(symbol)

    suspend fun searchStocks(query: String): List<StockSearchResult> = quoteService.searchIndianStocks(query)

    suspend fun fetchBrokerageFeed(): List<BrokerageFeedItem> = feedService.fetchLiveBrokerageRecommendations()

    suspend fun addRecommendation(
        symbol: String,
        companyName: String,
        brokerage: String,
        sourceName: String,
        sourceUrl: String,
        callType: CallType,
        quantity: Int,
        entryPrice: Double,
        targetPrice: Double,
        stopLoss: Double,
        alertsEnabled: Boolean,
        notes: String,
        enterImmediatelyIfAtCmp: Boolean
    ): Long {
        val cleanSymbol = normalizeIndianSymbol(symbol)
        val liveQuote = quoteService.fetchQuote(cleanSymbol)
        val cmp = liveQuote?.cmp ?: entryPrice
        val (initialStatus, direction) = TradeEvaluator.determineInitialStatusAndDirection(
            callType = callType,
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            liveCmp = cmp,
            enterImmediatelyIfTriggered = enterImmediatelyIfAtCmp
        )
        val now = System.currentTimeMillis()
        val sparkCsv = liveQuote?.sparkline?.joinToString(",") {
            String.format(java.util.Locale.US, "%.2f", it)
        } ?: ""

        val entity = RecommendationEntity(
            symbol = cleanSymbol,
            companyName = companyName.ifBlank { TradeEvaluator.shortSymbol(cleanSymbol) },
            brokerage = brokerage.ifBlank { "Motilal Oswal" },
            sourceName = sourceName.ifBlank { "Economic Times" },
            sourceUrl = sourceUrl,
            callType = callType.name,
            quantity = quantity.coerceAtLeast(1),
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            currentPrice = cmp,
            previousClose = liveQuote?.previousClose ?: cmp,
            dayChangePercent = liveQuote?.dayChangePercent ?: 0.0,
            status = initialStatus.name,
            entryTriggerDirection = direction.name,
            alertsEnabled = alertsEnabled,
            notes = notes,
            sparklineCsv = sparkCsv,
            createdAt = now,
            lastPriceUpdatedAt = now,
            entryTriggeredAt = if (initialStatus != TradeStatus.WATCHING_ENTRY) now else null,
            closedAt = if (initialStatus == TradeStatus.TARGET_HIT || initialStatus == TradeStatus.STOPLOSS_HIT) now else null,
            exitPrice = if (initialStatus == TradeStatus.TARGET_HIT || initialStatus == TradeStatus.STOPLOSS_HIT) cmp else null
        )

        val id = dao.insertRecommendation(entity)

        val eventType = when (initialStatus) {
            TradeStatus.WATCHING_ENTRY -> AlertEventType.CREATED
            TradeStatus.ACTIVE -> AlertEventType.ENTRY_TRIGGERED
            TradeStatus.TARGET_HIT -> AlertEventType.TARGET_HIT
            TradeStatus.STOPLOSS_HIT -> AlertEventType.STOPLOSS_HIT
        }
        val msg = when (eventType) {
            AlertEventType.CREATED -> "Tracking ${entity.companyName} (${entity.brokerage}): Entry ₹${TradeEvaluator.formatPrice(entryPrice)}, Target ₹${TradeEvaluator.formatPrice(targetPrice)}, SL ₹${TradeEvaluator.formatPrice(stopLoss)}."
            AlertEventType.ENTRY_TRIGGERED -> "${entity.companyName} entered Portfolio immediately at CMP ₹${TradeEvaluator.formatPrice(cmp)}."
            AlertEventType.TARGET_HIT -> "${entity.companyName} already at Target ₹${TradeEvaluator.formatPrice(cmp)}."
            AlertEventType.STOPLOSS_HIT -> "${entity.companyName} hit Stop Loss ₹${TradeEvaluator.formatPrice(cmp)}."
        }
        val event = AlertEventEntity(
            recommendationId = id,
            symbol = entity.symbol,
            companyName = entity.companyName,
            brokerage = entity.brokerage,
            eventType = eventType.name,
            triggerPrice = cmp,
            message = msg,
            timestamp = now,
            isRead = eventType == AlertEventType.CREATED
        )
        dao.insertAlertEvent(event)

        val prefs = prefsRepo.preferences.first()
        if (prefs.notificationsEnabled && alertsEnabled && eventType != AlertEventType.CREATED) {
            notificationManager.dispatchAlert(event)
        }
        return id
    }

    suspend fun updateRecommendation(updated: RecommendationEntity) {
        dao.updateRecommendation(updated)
    }

    suspend fun triggerEntryManually(rec: RecommendationEntity) {
        if (rec.parsedStatus != TradeStatus.WATCHING_ENTRY) return
        val now = System.currentTimeMillis()
        val liveCmp = if (rec.currentPrice > 0.0) rec.currentPrice else rec.entryPrice
        val activeRec = rec.copy(
            status = TradeStatus.ACTIVE.name,
            entryPrice = liveCmp,
            entryTriggeredAt = now
        )
        dao.updateRecommendation(activeRec)
        val event = AlertEventEntity(
            recommendationId = rec.id,
            symbol = rec.symbol,
            companyName = rec.companyName,
            brokerage = rec.brokerage,
            eventType = AlertEventType.ENTRY_TRIGGERED.name,
            triggerPrice = liveCmp,
            message = "Manually triggered Entry for ${rec.companyName} at CMP ₹${TradeEvaluator.formatPrice(liveCmp)}. Added to Portfolio.",
            timestamp = now
        )
        dao.insertAlertEvent(event)
    }

    suspend fun deleteRecommendation(id: Long) {
        dao.deleteRecommendationById(id)
        dao.deleteAlertsForRecommendation(id)
    }

    suspend fun markAllAlertsRead() {
        dao.markAllAlertsRead()
    }

    private fun normalizeIndianSymbol(raw: String): String {
        val trimmed = raw.trim().uppercase()
        return if (trimmed.endsWith(".NS") || trimmed.endsWith(".BO") || trimmed.startsWith("^")) {
            trimmed
        } else {
            "$trimmed.NS"
        }
    }
}
