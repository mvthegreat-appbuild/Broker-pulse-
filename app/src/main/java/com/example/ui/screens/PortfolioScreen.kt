package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.domain.TradeEvaluator
import com.example.ui.DashboardStats
import com.example.ui.components.RecommendationCard
import com.example.ui.theme.BrokerPulseTheme
import com.example.ui.theme.MonoStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    stats: DashboardStats,
    recommendations: List<RecommendationEntity>,
    onEdit: (RecommendationEntity) -> Unit,
    onDelete: (RecommendationEntity) -> Unit,
    onToggleAlerts: (RecommendationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active Holdings, 1 = Closed (Target/SL Hit)

    val activePositions = remember(recommendations) {
        recommendations.filter { it.parsedStatus == TradeStatus.ACTIVE }
    }
    val closedTrades = remember(recommendations) {
        recommendations.filter {
            it.parsedStatus == TradeStatus.TARGET_HIT || it.parsedStatus == TradeStatus.STOPLOSS_HIT
        }.sortedByDescending { it.closedAt ?: it.lastPriceUpdatedAt }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Portfolio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Auto-updates when Entry triggers and closes automatically on Target or Stop-Loss Hit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Capital & P&L Summary Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ext.cardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE INVESTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "₹${TradeEvaluator.formatPrice(stats.totalInvested)}",
                                style = MonoStyles.priceLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column {
                            Text(
                                text = "CURRENT VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "₹${TradeEvaluator.formatPrice(stats.totalCurrentValue)}",
                                style = MonoStyles.priceLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val unrealPos = stats.unrealizedPnl >= 0.0
                        Column {
                            Text(
                                text = "UNREALIZED P&L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s₹%s",
                                    if (unrealPos) "+" else "-",
                                    TradeEvaluator.formatPrice(kotlin.math.abs(stats.unrealizedPnl))
                                ),
                                style = MonoStyles.priceMedium,
                                color = if (unrealPos) ext.bullish else ext.bearish
                            )
                        }

                        val realPos = stats.realizedPnl >= 0.0
                        Column {
                            Text(
                                text = "REALIZED P&L (CLOSED)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s₹%s",
                                    if (realPos) "+" else "-",
                                    TradeEvaluator.formatPrice(kotlin.math.abs(stats.realizedPnl))
                                ),
                                style = MonoStyles.priceMedium,
                                color = if (realPos) ext.bullish else ext.bearish
                            )
                        }
                    }
                }
            }
        }

        // Segmented Toggle
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("portfolio_tab_active")
                ) {
                    Text("Active (${activePositions.size})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("portfolio_tab_closed")
                ) {
                    Text("Target / SL Closed (${closedTrades.size})")
                }
            }
        }

        val displayed = if (selectedTab == 0) activePositions else closedTrades
        if (displayed.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Text(
                        text = if (selectedTab == 0) {
                            stringResource(R.string.msg_no_portfolio_active)
                        } else {
                            stringResource(R.string.msg_no_portfolio_closed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(displayed, key = { it.id }) { rec ->
                RecommendationCard(
                    rec = rec,
                    onEdit = { onEdit(rec) },
                    onDelete = { onDelete(rec) },
                    onToggleAlerts = { onToggleAlerts(rec) },
                    onTriggerEntryNow = {}
                )
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}
