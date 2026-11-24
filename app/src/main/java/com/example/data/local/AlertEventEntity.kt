package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertEventType(val label: String) {
    CREATED("Tracking Started"),
    ENTRY_TRIGGERED("Entry Triggered"),
    TARGET_HIT("Target Hit"),
    STOPLOSS_HIT("Stop Loss Hit")
}

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recommendationId: Long,
    val symbol: String,
    val companyName: String,
    val brokerage: String,
    val eventType: String,
    val triggerPrice: Double,
    val pnlAmount: Double = 0.0,
    val pnlPercent: Double = 0.0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    val parsedType: AlertEventType
        get() = AlertEventType.entries.firstOrNull { it.name == eventType } ?: AlertEventType.CREATED
}
