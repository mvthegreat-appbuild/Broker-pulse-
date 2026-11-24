package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.data.repository.MarketIndicesSnapshot
import com.example.domain.TradeEvaluator
import com.example.ui.DashboardStats
import com.example.ui.components.MarketIndexPill
import com.example.ui.components.RecommendationCard
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.BrokerPulseTheme
import com.example.ui.theme.MonoStyles
import java.util.Locale

@Composable
fun DashboardScreen(
    stats: DashboardStats,
    indices: MarketIndicesSnapshot,
    recommendations: List<RecommendationEntity>,
    allBrokerages: List<String>,
    selectedStatus: TradeStatus?,
    selectedBroker: String?,
    isRefreshing: Boolean,
    lastSyncTimestamp: Long?,
    onSelectStatus: (TradeStatus?) -> Unit,
    onSelectBroker: (String?) -> Unit,
    onRefreshQuotes: () -> Unit,
    onOpenResearchFeed: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (RecommendationEntity) -> Unit,
    onDelete: (RecommendationEntity) -> Unit,
    onToggleAlerts: (RecommendationEntity) -> Unit,
    onTriggerEntryNow: (RecommendationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BrokerPulse",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = lastSyncTimestamp?.let { "Live sync • ${formatTimeShort(it)}" }
                            ?: "Connecting to NSE/BSE market feed...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefreshQuotes,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("refresh_quotes_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_quotes),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Live Indian Market Indices
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MarketIndexPill(
                    label = "NIFTY 50",
                    quote = indices.nifty50,
                    modifier = Modifier.weight(1f)
                )
                MarketIndexPill(
                    label = "SENSEX",
                    quote = indices.sensex,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Personalized Portfolio & Accuracy Hero Summary Card
        item {
            val totalPnl = stats.unrealizedPnl + stats.realizedPnl
            val pnlPositive = totalPnl >= 0.0
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_market_banner),
                        contentDescription = stringResource(R.string.cd_market_banner),
                        contentScale = ContentScale.Crop,
                        alpha = if (ext.isDark) 0.25f else 0.10f,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "NET TRACKED P&L",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%s₹%s",
                                        if (pnlPositive) "+" else "-",
                                        TradeEvaluator.formatPrice(kotlin.math.abs(totalPnl))
                                    ),
                                    style = MonoStyles.priceHero,
                                    color = if (pnlPositive) ext.bullish else ext.bearish
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ext.bullishContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "WIN RATE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.bullish
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.0f%%", stats.winRatePercent),
                                        style = MonoStyles.priceMedium,
                                        color = ext.bullish
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricMiniColumn("Watching", "${stats.watchingEntryCount}", ext.amber)
                            MetricMiniColumn("Active", "${stats.activeCount}", ext.cyanAccent)
                            MetricMiniColumn("Target Hit", "${stats.targetHitCount}", ext.bullish)
                            MetricMiniColumn("SL Hit", "${stats.stopLossHitCount}", ext.bearish)
                        }
                    }
                }
            }
        }

        // Status Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { onSelectStatus(null) },
                        label = { Text("All (${stats.totalTracked})") }
                    )
                }
                TradeStatus.entries.forEach { st ->
                    item {
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { onSelectStatus(if (selectedStatus == st) null else st) },
                            label = { Text(st.label) }
                        )
                    }
                }
            }
        }

        // Brokerage Filter Chips if user tracks multiple brokerages
        if (allBrokerages.size > 1) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedBroker == null,
                            onClick = { onSelectBroker(null) },
                            label = { Text("All Brokers") }
                        )
                    }
                    items(allBrokerages) { broker ->
                        FilterChip(
                            selected = selectedBroker.equals(broker, ignoreCase = true),
                            onClick = { onSelectBroker(if (selectedBroker.equals(broker, ignoreCase = true)) null else broker) },
                            label = { Text(broker) }
                        )
                    }
                }
            }
        }

        // Empty State or Tracked Recommendations List
        if (recommendations.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_market_banner),
                            contentDescription = stringResource(R.string.cd_market_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Start Real-Time Stock Tracking",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.msg_no_recommendations),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenResearchFeed,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("empty_open_feed_btn")
                            ) {
                                Icon(Icons.Filled.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Live Feed")
                            }
                            Button(
                                onClick = onAddNew,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("empty_add_call_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Call")
                            }
                        }
                    }
                }
            }
        } else {
            items(recommendations, key = { it.id }) { rec ->
                RecommendationCard(
                    rec = rec,
                    onEdit = { onEdit(rec) },
                    onDelete = { onDelete(rec) },
                    onToggleAlerts = { onToggleAlerts(rec) },
                    onTriggerEntryNow = { onTriggerEntryNow(rec) }
                )
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}

@Composable
private fun MetricMiniColumn(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoStyles.priceLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
