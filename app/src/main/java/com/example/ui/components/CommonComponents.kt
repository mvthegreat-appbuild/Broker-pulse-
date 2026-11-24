package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallType
import com.example.data.local.RecommendationEntity
import com.example.data.local.TradeStatus
import com.example.data.remote.LiveQuote
import com.example.domain.TradeEvaluator
import com.example.ui.theme.BrokerPulseTheme
import com.example.ui.theme.MonoStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarketIndexPill(
    label: String,
    quote: LiveQuote?,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    val isUp = (quote?.dayChangePercent ?: 0.0) >= 0.0
    val accent = if (quote == null) MaterialTheme.colorScheme.onSurfaceVariant else if (isUp) ext.bullish else ext.bearish

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = quote?.let { TradeEvaluator.formatPrice(it.cmp) } ?: "Loading...",
                    style = MonoStyles.priceMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (quote != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isUp) ext.bullishContainer else ext.bearishContainer
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s%.2f%%",
                            if (isUp) "+" else "",
                            quote.dayChangePercent
                        ),
                        style = MonoStyles.priceSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: TradeStatus, modifier: Modifier = Modifier) {
    val ext = BrokerPulseTheme.extendedColors
    val (bg, fg, icon) = when (status) {
        TradeStatus.WATCHING_ENTRY -> Triple(ext.amberContainer, ext.amber, Icons.Filled.Schedule)
        TradeStatus.ACTIVE -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            ext.cyanAccent,
            Icons.AutoMirrored.Filled.TrendingUp
        )
        TradeStatus.TARGET_HIT -> Triple(ext.bullishContainer, ext.bullish, Icons.Filled.CheckCircle)
        TradeStatus.STOPLOSS_HIT -> Triple(ext.bearishContainer, ext.bearish, Icons.Filled.Warning)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SparklineCanvas(
    points: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    val lineColor = if (isPositive) ext.bullish else ext.bearish

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: return@Canvas
        val maxVal = points.maxOrNull() ?: return@Canvas
        val range = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0

        val path = Path()
        val fillPath = Path()
        val stepX = size.width / (points.size - 1).toFloat()

        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val normalizedY = ((value - minVal) / range).toFloat()
            val y = size.height - (normalizedY * size.height * 0.8f + size.height * 0.1f)
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PriceRangeProgressBar(
    rec: RecommendationEntity,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    val progress = TradeEvaluator.computeProgressBetweenSlAndTarget(rec)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = when (rec.parsedStatus) {
        TradeStatus.TARGET_HIT -> ext.bullish
        TradeStatus.STOPLOSS_HIT -> ext.bearish
        else -> if (progress >= 0.5f) ext.bullish else ext.amber
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Base track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
            )
            // Filled span from SL toward CMP
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(ext.bearish, ext.amber, ext.bullish)
                        )
                    )
            )
            // Entry marker
            val denominator = (rec.targetPrice - rec.stopLoss).takeIf { kotlin.math.abs(it) > 0.001 } ?: 1.0
            val entryPos = ((rec.entryPrice - rec.stopLoss) / denominator)
                .toFloat().coerceIn(0.02f, 0.98f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val entryX = size.width * entryPos
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(entryX, 0f),
                    end = Offset(entryX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                val cmpX = size.width * progress.coerceIn(0.03f, 0.97f)
                drawCircle(
                    color = activeColor,
                    radius = 5.dp.toPx(),
                    center = Offset(cmpX, size.height / 2f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SL ₹${TradeEvaluator.formatPrice(rec.stopLoss)}",
                style = MonoStyles.priceSmall,
                color = ext.bearish
            )
            Text(
                text = "Entry ₹${TradeEvaluator.formatPrice(rec.entryPrice)}",
                style = MonoStyles.priceSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tgt ₹${TradeEvaluator.formatPrice(rec.targetPrice)}",
                style = MonoStyles.priceSmall,
                color = ext.bullish
            )
        }
    }
}

@Composable
fun RecommendationCard(
    rec: RecommendationEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAlerts: () -> Unit,
    onTriggerEntryNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = BrokerPulseTheme.extendedColors
    val isDayUp = rec.dayChangePercent >= 0.0
    val effectivePnl = TradeEvaluator.computePnlAmount(rec)
    val effectiveRetPct = TradeEvaluator.computeReturnPercent(rec)
    val upsidePct = TradeEvaluator.computeUpsidePotentialPercent(rec)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recommendation_card_${rec.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top header: Symbol, Brokerage, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = TradeEvaluator.shortSymbol(rec.symbol),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (rec.parsedCallType == CallType.BUY) ext.bullishContainer else ext.bearishContainer
                        ) {
                            Text(
                                text = rec.parsedCallType.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (rec.parsedCallType == CallType.BUY) ext.bullish else ext.bearish,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = rec.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rec.brokerage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " • ${rec.sourceName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(status = rec.parsedStatus)
                    if (rec.sparklinePoints.size >= 2) {
                        Spacer(Modifier.height(6.dp))
                        SparklineCanvas(
                            points = rec.sparklinePoints,
                            isPositive = isDayUp,
                            modifier = Modifier
                                .width(72.dp)
                                .height(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Live CMP & P&L / Upside Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE CMP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "₹${TradeEvaluator.formatPrice(rec.currentPrice)}",
                            style = MonoStyles.priceLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isDayUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isDayUp) ext.bullish else ext.bearish,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%s%.2f%%", if (isDayUp) "+" else "", rec.dayChangePercent),
                            style = MonoStyles.priceSmall,
                            color = if (isDayUp) ext.bullish else ext.bearish
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (rec.parsedStatus == TradeStatus.WATCHING_ENTRY) {
                        Text(
                            text = "TARGET UPSIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", upsidePct),
                            style = MonoStyles.priceLarge,
                            color = if (upsidePct >= 0) ext.bullish else ext.bearish
                        )
                    } else {
                        Text(
                            text = if (rec.parsedStatus == TradeStatus.ACTIVE) "UNREALIZED P&L (${rec.quantity} qty)" else "REALIZED P&L (${rec.quantity} qty)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val pnlPositive = effectivePnl >= 0.0
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s₹%s (%+.2f%%)",
                                if (pnlPositive) "+" else "-",
                                TradeEvaluator.formatPrice(kotlin.math.abs(effectivePnl)),
                                effectiveRetPct
                            ),
                            style = MonoStyles.priceMedium,
                            color = if (pnlPositive) ext.bullish else ext.bearish
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            PriceRangeProgressBar(rec = rec)

            if (rec.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = rec.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Updated ${formatTimeShort(rec.lastPriceUpdatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rec.parsedStatus == TradeStatus.WATCHING_ENTRY) {
                        TextButton(
                            onClick = onTriggerEntryNow,
                            modifier = Modifier.testTag("trigger_entry_btn_${rec.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Enter Now", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(
                        onClick = onToggleAlerts,
                        modifier = Modifier.testTag("toggle_alert_btn_${rec.id}")
                    ) {
                        Icon(
                            imageVector = if (rec.alertsEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = stringResource(R.string.cd_notifications),
                            tint = if (rec.alertsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_rec_btn_${rec.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_edit_recommendation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_rec_btn_${rec.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_recommendation),
                            tint = ext.bearish,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatTimeShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
