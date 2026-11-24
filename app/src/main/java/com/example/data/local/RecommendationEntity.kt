package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallType(val label: String) {
    BUY("BUY"),
    SELL("SELL")
}

enum class TradeStatus(val label: String) {
    WATCHING_ENTRY("Pending Entry"),
    ACTIVE("Active Position"),
    TARGET_HIT("Target Hit"),
    STOPLOSS_HIT("Stop Loss Hit")
}

enum class EntryTriggerDirection {
    BELOW_OR_EQUAL,
    ABOVE_OR_EQUAL
}

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val companyName: String,
    val brokerage: String,
    val sourceName: String,
    val sourceUrl: String = "",
    val callType: String = CallType.BUY.name,
    val quantity: Int = 10,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLoss: Double,
    val currentPrice: Double,
    val previousClose: Double = 0.0,
    val dayChangePercent: Double = 0.0,
    val status: String = TradeStatus.WATCHING_ENTRY.name,
    val entryTriggerDirection: String = EntryTriggerDirection.BELOW_OR_EQUAL.name,
    val alertsEnabled: Boolean = true,
    val notes: String = "",
    val sparklineCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastPriceUpdatedAt: Long = System.currentTimeMillis(),
    val entryTriggeredAt: Long? = null,
    val closedAt: Long? = null,
    val exitPrice: Double? = null
) {
    val parsedCallType: CallType
        get() = CallType.entries.firstOrNull { it.name == callType } ?: CallType.BUY

    val parsedStatus: TradeStatus
        get() = TradeStatus.entries.firstOrNull { it.name == status } ?: TradeStatus.WATCHING_ENTRY

    val parsedEntryDirection: EntryTriggerDirection
        get() = EntryTriggerDirection.entries.firstOrNull { it.name == entryTriggerDirection }
            ?: EntryTriggerDirection.BELOW_OR_EQUAL

    val sparklinePoints: List<Double>
        get() = sparklineCsv.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
}
