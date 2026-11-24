package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlertEventEntity
import com.example.data.local.AlertEventType
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.data.preferences.UserPreferences
import com.example.domain.TradeEvaluator
import com.example.ui.BrokeragePerformance
import com.example.ui.DashboardStats
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.BrokerPulseTheme
import com.example.ui.theme.MonoStyles
import java.util.Locale

@Composable
fun AnalyticsAlertsScreen(
    stats: DashboardStats,
    recommendations: List<RecommendationEntity>,
    leaderboard: List<BrokeragePerformance>,
    alerts: List<AlertEventEntity>,
    preferences: UserPreferences,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetPollingInterval: (Int) -> Unit,
    onSetAutoEvaluate: (Boolean) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    val closedTrades = recommendations.filter {
        it.parsedStatus == TradeStatus.TARGET_HIT || it.parsedStatus == TradeStatus.STOPLOSS_HIT
    }.sortedBy { it.closedAt ?: it.createdAt }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "History & Alerts",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Historical brokerage accuracy, closed return curves & live alert log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Performance Overview + Chart Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "HISTORICAL WIN RATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", stats.winRatePercent),
                                style = MonoStyles.priceHero,
                                color = ext.bullish
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVG RETURN / CALL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val avgPos = stats.avgReturnPercent >= 0.0
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", stats.avgReturnPercent),
                                style = MonoStyles.priceLarge,
                                color = if (avgPos) ext.bullish else ext.bearish
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (closedTrades.isNotEmpty()) {
                        Text(
                            text = "Closed Trade Return % Distribution",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val returns = closedTrades.map { TradeEvaluator.computeReturnPercent(it) }
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            val maxAbs = (returns.maxOfOrNull { kotlin.math.abs(it) } ?: 10.0)
                                .coerceAtLeast(5.0)
                            val midY = size.height / 2f
                            drawLine(
                                color = ext.cardBorder,
                                start = Offset(0f, midY),
                                end = Offset(size.width, midY),
                                strokeWidth = 1.dp.toPx()
                            )
                            val slotWidth = size.width / returns.size.coerceAtLeast(1)
                            val barWidth = (slotWidth * 0.55f).coerceIn(8.dp.toPx(), 32.dp.toPx())
                            returns.forEachIndexed { idx, pct ->
                                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                val barHeight = ((kotlin.math.abs(pct) / maxAbs) * (midY * 0.85f)).toFloat()
                                val topY = if (pct >= 0) midY - barHeight else midY
                                drawRoundRect(
                                    color = if (pct >= 0) ext.bullish else ext.bearish,
                                    topLeft = Offset(x, topY),
                                    size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Closed trade chart populates automatically once recommendations hit Target or Stop Loss.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Brokerage Accuracy Leaderboard
        if (leaderboard.isNotEmpty()) {
            item {
                Text(
                    text = "Brokerage Accuracy Leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(leaderboard, key = { it.brokerage }) { row ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.brokerage,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${row.totalCalls} calls • ${row.targetHits} Target • ${row.stopLossHits} SL • ${row.activeCalls} Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "%.0f%% Win", row.winRatePercent),
                                style = MonoStyles.priceMedium,
                                color = ext.bullish
                            )
                            val pnlPos = row.netPnl >= 0.0
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s₹%s",
                                    if (pnlPos) "+" else "-",
                                    TradeEvaluator.formatPrice(kotlin.math.abs(row.netPnl))
                                ),
                                style = MonoStyles.priceSmall,
                                color = if (pnlPos) ext.bullish else ext.bearish
                            )
                        }
                    }
                }
            }
        }

        // Alert & Engine Settings Card
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alert & Real-Time Engine Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Alert Notifications", style = MaterialTheme.typography.bodyMedium)
                            if (!hasNotificationPermission) {
                                Text(
                                    text = "Tap to grant Android notification permission",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.amber
                                )
                            }
                        }
                        Switch(
                            checked = preferences.notificationsEnabled && hasNotificationPermission,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasNotificationPermission) {
                                    onRequestNotificationPermission()
                                }
                                onSetNotificationsEnabled(enabled)
                            },
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-Update Portfolio on Entry / Target / SL",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = preferences.autoEvaluateTriggers,
                            onCheckedChange = onSetAutoEvaluate
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Live Quote Refresh Interval",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = preferences.pollingIntervalSeconds == sec,
                                onClick = { onSetPollingInterval(sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DARK" to "Terminal Dark", "LIGHT" to "Crisp Light", "SYSTEM" to "System").forEach { (mode, label) ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Alert Events Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trigger & Alert Log (${alerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (alerts.any { !it.isRead }) {
                    TextButton(onClick = onMarkAllRead) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark Read")
                    }
                }
            }
        }

        if (alerts.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Text(
                        text = stringResource(R.string.msg_no_alerts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(alerts, key = { it.id }) { event ->
                val (icon, tint) = when (event.parsedType) {
                    AlertEventType.TARGET_HIT -> Icons.Filled.CheckCircle to ext.bullish
                    AlertEventType.STOPLOSS_HIT -> Icons.Filled.Warning to ext.bearish
                    AlertEventType.ENTRY_TRIGGERED -> Icons.Filled.PlayArrow to ext.cyanAccent
                    AlertEventType.CREATED -> Icons.Filled.Notifications to MaterialTheme.colorScheme.primary
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = event.parsedType.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tint,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatTimeShort(event.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}
