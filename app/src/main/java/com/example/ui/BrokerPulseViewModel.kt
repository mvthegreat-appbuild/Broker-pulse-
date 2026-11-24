package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AlertEventEntity
import com.example.data.local.CallType
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.BrokerageFeedItem
import com.example.data.remote.LiveQuote
import com.example.data.remote.StockSearchResult
import com.example.data.repository.BrokerPulseRepository
import com.example.data.repository.MarketIndicesSnapshot
import com.example.domain.TradeEvaluator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AddSheetDraft(
    val existingId: Long? = null,
    val symbol: String = "",
    val companyName: String = "",
    val brokerage: String = "Motilal Oswal",
    val sourceName: String = "Economic Times",
    val sourceUrl: String = "",
    val callType: CallType = CallType.BUY,
    val quantity: Int = 10,
    val entryPrice: String = "",
    val targetPrice: String = "",
    val stopLoss: String = "",
    val liveCmp: Double? = null,
    val alertsEnabled: Boolean = true,
    val notes: String = ""
)

data class BrokeragePerformance(
    val brokerage: String,
    val totalCalls: Int,
    val activeCalls: Int,
    val targetHits: Int,
    val stopLossHits: Int,
    val winRatePercent: Double,
    val netPnl: Double,
    val avgReturnPercent: Double
)

data class DashboardStats(
    val totalTracked: Int = 0,
    val watchingEntryCount: Int = 0,
    val activeCount: Int = 0,
    val targetHitCount: Int = 0,
    val stopLossHitCount: Int = 0,
    val totalInvested: Double = 0.0,
    val totalCurrentValue: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val winRatePercent: Double = 0.0,
    val avgReturnPercent: Double = 0.0
)

class BrokerPulseViewModel(
    private val repository: BrokerPulseRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val recommendations: StateFlow<List<RecommendationEntity>> = repository.allRecommendations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val alertEvents: StateFlow<List<AlertEventEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadAlertCount: StateFlow<Int> = repository.unreadAlertCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val dashboardStats: StateFlow<DashboardStats> = recommendations.map { list ->
        computeStats(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardStats())

    val brokerageLeaderboard: StateFlow<List<BrokeragePerformance>> = recommendations.map { list ->
        computeBrokerageLeaderboard(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _indices = MutableStateFlow(MarketIndicesSnapshot())
    val indices: StateFlow<MarketIndicesSnapshot> = _indices.asStateFlow()

    private val _feedItems = MutableStateFlow<List<BrokerageFeedItem>>(emptyList())
    val feedItems: StateFlow<List<BrokerageFeedItem>> = _feedItems.asStateFlow()

    private val _isRefreshingQuotes = MutableStateFlow(false)
    val isRefreshingQuotes: StateFlow<Boolean> = _isRefreshingQuotes.asStateFlow()

    private val _isLoadingFeed = MutableStateFlow(false)
    val isLoadingFeed: StateFlow<Boolean> = _isLoadingFeed.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _statusFilter = MutableStateFlow<TradeStatus?>(null)
    val statusFilter: StateFlow<TradeStatus?> = _statusFilter.asStateFlow()

    private val _brokerFilter = MutableStateFlow<String?>(null)
    val brokerFilter: StateFlow<String?> = _brokerFilter.asStateFlow()

    val filteredRecommendations: StateFlow<List<RecommendationEntity>> = combine(
        recommendations,
        _statusFilter,
        _brokerFilter
    ) { items, status, broker ->
        items.filter { rec ->
            (status == null || rec.parsedStatus == status) &&
                (broker == null || rec.brokerage.equals(broker, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _isSearchingStocks = MutableStateFlow(false)
    val isSearchingStocks: StateFlow<Boolean> = _isSearchingStocks.asStateFlow()

    private val _sheetDraft = MutableStateFlow<AddSheetDraft?>(null)
    val sheetDraft: StateFlow<AddSheetDraft?> = _sheetDraft.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var searchJob: Job? = null

    init {
        refreshFeed()
        startLivePollingLoop()
    }

    private fun startLivePollingLoop() {
        viewModelScope.launch {
            preferences
                .map { it.pollingIntervalSeconds }
                .distinctUntilChanged()
                .collect { intervalSec ->
                    while (isActive) {
                        refreshMarketDataInternal(showToastOnNoEvents = false)
                        delay(intervalSec * 1000L)
                    }
                }
        }
    }

    fun refreshMarketDataManual() {
        viewModelScope.launch {
            refreshMarketDataInternal(showToastOnNoEvents = true)
        }
    }

    private suspend fun refreshMarketDataInternal(showToastOnNoEvents: Boolean) {
        if (_isRefreshingQuotes.value) return
        _isRefreshingQuotes.value = true
        try {
            val idx = repository.refreshMarketIndices()
            _indices.value = idx
            val emitted = repository.refreshAllTrackedQuotes()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            if (emitted.isNotEmpty()) {
                _snackbarMessages.tryEmit(emitted.first().message)
            } else if (showToastOnNoEvents) {
                _snackbarMessages.tryEmit("Live NSE/BSE prices updated")
            }
        } catch (e: Exception) {
            if (showToastOnNoEvents) {
                _snackbarMessages.tryEmit("Could not reach market quote server")
            }
        } finally {
            _isRefreshingQuotes.value = false
        }
    }

    fun refreshFeed() {
        if (_isLoadingFeed.value) return
        viewModelScope.launch {
            _isLoadingFeed.value = true
            _feedError.value = null
            try {
                val items = repository.fetchBrokerageFeed()
                _feedItems.value = items
                if (items.isEmpty()) {
                    _feedError.value = "No live articles returned from feeds. Check internet connection and tap Refresh."
                }
            } catch (e: Exception) {
                _feedError.value = "Unable to load feeds: ${e.localizedMessage ?: "Network error"}"
            } finally {
                _isLoadingFeed.value = false
            }
        }
    }

    fun setStatusFilter(status: TradeStatus?) {
        _statusFilter.value = status
    }

    fun setBrokerFilter(broker: String?) {
        _brokerFilter.value = broker
    }

    fun openNewRecommendationSheet() {
        _searchResults.value = emptyList()
        _sheetDraft.value = AddSheetDraft(
            quantity = preferences.value.defaultQuantity
        )
    }

    fun openEditRecommendationSheet(rec: RecommendationEntity) {
        _searchResults.value = emptyList()
        _sheetDraft.value = AddSheetDraft(
            existingId = rec.id,
            symbol = rec.symbol,
            companyName = rec.companyName,
            brokerage = rec.brokerage,
            sourceName = rec.sourceName,
            sourceUrl = rec.sourceUrl,
            callType = rec.parsedCallType,
            quantity = rec.quantity,
            entryPrice = rec.entryPrice.toString(),
            targetPrice = rec.targetPrice.toString(),
            stopLoss = rec.stopLoss.toString(),
            liveCmp = rec.currentPrice.takeIf { it > 0.0 },
            alertsEnabled = rec.alertsEnabled,
            notes = rec.notes
        )
    }

    fun openSheetFromFeedItem(feedItem: BrokerageFeedItem) {
        _searchResults.value = emptyList()
        viewModelScope.launch {
            var resolvedSymbol = feedItem.detectedSymbol.orEmpty()
            var resolvedCompany = feedItem.detectedCompany.orEmpty()

            if (resolvedSymbol.isBlank()) {
                val searchCandidates = repository.searchStocks(feedItem.title.take(36))
                val first = searchCandidates.firstOrNull()
                if (first != null) {
                    resolvedSymbol = first.symbol
                    resolvedCompany = first.companyName
                }
            }

            val quote: LiveQuote? = if (resolvedSymbol.isNotBlank()) {
                repository.fetchLiveQuote(resolvedSymbol)
            } else {
                null
            }

            val cmp = quote?.cmp
            val entry = feedItem.extractedEntryPrice ?: cmp
            val defaultTarget = feedItem.extractedTargetPrice ?: cmp?.let {
                val factor = if (feedItem.callType == CallType.BUY) 1.12 else 0.88
                kotlin.math.round(it * factor * 100.0) / 100.0
            }
            val defaultSl = feedItem.extractedStopLoss ?: (entry ?: cmp)?.let {
                val factor = if (feedItem.callType == CallType.BUY) 0.94 else 1.06
                kotlin.math.round(it * factor * 100.0) / 100.0
            }

            _sheetDraft.value = AddSheetDraft(
                symbol = resolvedSymbol,
                companyName = resolvedCompany.ifBlank { quote?.shortName.orEmpty() },
                brokerage = feedItem.brokerage,
                sourceName = feedItem.sourceName,
                sourceUrl = feedItem.sourceUrl,
                callType = feedItem.callType,
                quantity = preferences.value.defaultQuantity,
                entryPrice = entry?.let { TradeEvaluator.formatPrice(it).replace(",", "") }.orEmpty(),
                targetPrice = defaultTarget?.let { TradeEvaluator.formatPrice(it).replace(",", "") }.orEmpty(),
                stopLoss = defaultSl?.let { TradeEvaluator.formatPrice(it).replace(",", "") }.orEmpty(),
                liveCmp = cmp,
                notes = feedItem.title
            )
        }
    }

    fun dismissSheet() {
        _sheetDraft.value = null
        _searchResults.value = emptyList()
    }

    fun searchStocks(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _isSearchingStocks.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearchingStocks.value = true
            delay(280)
            val results = repository.searchStocks(query)
            _searchResults.value = results
            _isSearchingStocks.value = false
        }
    }

    fun selectSearchedStock(result: StockSearchResult) {
        _searchResults.value = emptyList()
        viewModelScope.launch {
            val quote = repository.fetchLiveQuote(result.symbol)
            val current = _sheetDraft.value ?: return@launch
            val cmp = quote?.cmp
            val entryStr = if (current.entryPrice.isBlank() && cmp != null) {
                String.format(java.util.Locale.US, "%.2f", cmp)
            } else {
                current.entryPrice
            }
            _sheetDraft.value = current.copy(
                symbol = result.symbol,
                companyName = result.companyName,
                liveCmp = cmp,
                entryPrice = entryStr
            )
        }
    }

    fun updateDraft(transform: (AddSheetDraft) -> AddSheetDraft) {
        _sheetDraft.value = _sheetDraft.value?.let(transform)
    }

    fun fetchCmpForCurrentSymbol() {
        val sym = _sheetDraft.value?.symbol?.trim().orEmpty()
        if (sym.isBlank()) return
        viewModelScope.launch {
            val normalized = if (sym.endsWith(".NS") || sym.endsWith(".BO")) sym else "$sym.NS"
            val quote = repository.fetchLiveQuote(normalized)
            if (quote != null) {
                _sheetDraft.value = _sheetDraft.value?.copy(
                    symbol = quote.symbol,
                    companyName = _sheetDraft.value?.companyName?.ifBlank { quote.shortName } ?: quote.shortName,
                    liveCmp = quote.cmp,
                    entryPrice = _sheetDraft.value?.entryPrice?.ifBlank {
                        String.format(java.util.Locale.US, "%.2f", quote.cmp)
                    }.orEmpty()
                )
            } else {
                _snackbarMessages.tryEmit("Symbol $normalized not found on NSE/BSE")
            }
        }
    }

    fun saveDraft(enterImmediatelyIfAtCmp: Boolean = true) {
        val draft = _sheetDraft.value ?: return
        val entry = draft.entryPrice.toDoubleOrNull()
        val target = draft.targetPrice.toDoubleOrNull()
        val sl = draft.stopLoss.toDoubleOrNull()
        if (draft.symbol.isBlank() || entry == null || target == null || sl == null || entry <= 0 || target <= 0 || sl <= 0) {
            _snackbarMessages.tryEmit("Enter valid Stock Symbol, Entry, Target, and Stop Loss prices")
            return
        }

        viewModelScope.launch {
            if (draft.existingId != null) {
                val existing = recommendations.value.firstOrNull { it.id == draft.existingId }
                if (existing != null) {
                    val updated = existing.copy(
                        symbol = draft.symbol.trim().uppercase(),
                        companyName = draft.companyName.ifBlank { existing.companyName },
                        brokerage = draft.brokerage,
                        sourceName = draft.sourceName,
                        sourceUrl = draft.sourceUrl,
                        callType = draft.callType.name,
                        quantity = draft.quantity.coerceAtLeast(1),
                        entryPrice = entry,
                        targetPrice = target,
                        stopLoss = sl,
                        alertsEnabled = draft.alertsEnabled,
                        notes = draft.notes
                    )
                    repository.updateRecommendation(updated)
                    _snackbarMessages.tryEmit("Updated ${updated.companyName}")
                }
            } else {
                repository.addRecommendation(
                    symbol = draft.symbol,
                    companyName = draft.companyName,
                    brokerage = draft.brokerage,
                    sourceName = draft.sourceName,
                    sourceUrl = draft.sourceUrl,
                    callType = draft.callType,
                    quantity = draft.quantity,
                    entryPrice = entry,
                    targetPrice = target,
                    stopLoss = sl,
                    alertsEnabled = draft.alertsEnabled,
                    notes = draft.notes,
                    enterImmediatelyIfAtCmp = enterImmediatelyIfAtCmp
                )
                _snackbarMessages.tryEmit("Tracking ${draft.companyName.ifBlank { draft.symbol }}")
            }
            _sheetDraft.value = null
            refreshMarketDataInternal(showToastOnNoEvents = false)
        }
    }

    fun triggerEntryNow(rec: RecommendationEntity) {
        viewModelScope.launch {
            repository.triggerEntryManually(rec)
            _snackbarMessages.tryEmit("${rec.companyName} moved to Active Portfolio")
        }
    }

    fun toggleAlertsForRecommendation(rec: RecommendationEntity) {
        viewModelScope.launch {
            repository.updateRecommendation(rec.copy(alertsEnabled = !rec.alertsEnabled))
        }
    }

    fun deleteRecommendation(rec: RecommendationEntity) {
        viewModelScope.launch {
            repository.deleteRecommendation(rec.id)
            _snackbarMessages.tryEmit("Removed ${rec.companyName}")
        }
    }

    fun markAllAlertsRead() {
        viewModelScope.launch {
            repository.markAllAlertsRead()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotificationsEnabled(enabled) }
    }

    fun setPollingIntervalSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setPollingIntervalSeconds(seconds) }
    }

    fun setAutoEvaluateTriggers(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoEvaluateTriggers(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    private fun computeStats(list: List<RecommendationEntity>): DashboardStats {
        val watching = list.count { it.parsedStatus == TradeStatus.WATCHING_ENTRY }
        val activeList = list.filter { it.parsedStatus == TradeStatus.ACTIVE }
        val targetHits = list.filter { it.parsedStatus == TradeStatus.TARGET_HIT }
        val slHits = list.filter { it.parsedStatus == TradeStatus.STOPLOSS_HIT }
        val closedList = targetHits + slHits

        val invested = activeList.sumOf { it.entryPrice * it.quantity }
        val currentValue = activeList.sumOf {
            (if (it.currentPrice > 0.0) it.currentPrice else it.entryPrice) * it.quantity
        }
        val unrealized = activeList.sumOf { TradeEvaluator.computePnlAmount(it) }
        val realized = closedList.sumOf { TradeEvaluator.computePnlAmount(it) }

        val winRate = if (closedList.isNotEmpty()) {
            (targetHits.size.toDouble() / closedList.size.toDouble()) * 100.0
        } else {
            0.0
        }
        val portfolioPlusClosed = activeList + closedList
        val avgRet = if (portfolioPlusClosed.isNotEmpty()) {
            portfolioPlusClosed.map { TradeEvaluator.computeReturnPercent(it) }.average()
        } else {
            0.0
        }

        return DashboardStats(
            totalTracked = list.size,
            watchingEntryCount = watching,
            activeCount = activeList.size,
            targetHitCount = targetHits.size,
            stopLossHitCount = slHits.size,
            totalInvested = invested,
            totalCurrentValue = currentValue,
            unrealizedPnl = unrealized,
            realizedPnl = realized,
            winRatePercent = winRate,
            avgReturnPercent = avgRet
        )
    }

    private fun computeBrokerageLeaderboard(list: List<RecommendationEntity>): List<BrokeragePerformance> {
        return list.groupBy { it.brokerage }
            .map { (brokerage, recs) ->
                val active = recs.count { it.parsedStatus == TradeStatus.ACTIVE }
                val targets = recs.count { it.parsedStatus == TradeStatus.TARGET_HIT }
                val stops = recs.count { it.parsedStatus == TradeStatus.STOPLOSS_HIT }
                val closedCount = targets + stops
                val winRate = if (closedCount > 0) (targets.toDouble() / closedCount) * 100.0 else 0.0
                val entered = recs.filter { it.parsedStatus != TradeStatus.WATCHING_ENTRY }
                val netPnl = entered.sumOf { TradeEvaluator.computePnlAmount(it) }
                val avgRet = if (entered.isNotEmpty()) {
                    entered.map { TradeEvaluator.computeReturnPercent(it) }.average()
                } else {
                    0.0
                }
                BrokeragePerformance(
                    brokerage = brokerage,
                    totalCalls = recs.size,
                    activeCalls = active,
                    targetHits = targets,
                    stopLossHits = stops,
                    winRatePercent = winRate,
                    netPnl = netPnl,
                    avgReturnPercent = avgRet
                )
            }
            .sortedWith(
                compareByDescending<BrokeragePerformance> { it.winRatePercent }
                    .thenByDescending { it.netPnl }
                    .thenByDescending { it.totalCalls }
            )
    }
}
