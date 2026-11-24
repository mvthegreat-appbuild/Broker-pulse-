package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CallType
import com.example.data.remote.StockSearchResult
import com.example.domain.TradeEvaluator
import com.example.ui.AddSheetDraft
import com.example.ui.theme.BrokerPulseTheme
import com.example.ui.theme.MonoStyles

private val POPULAR_BROKERAGES = listOf(
    "Motilal Oswal",
    "SBI Securities",
    "Anand Rathi",
    "ICICI Direct",
    "HDFC Securities",
    "Sharekhan",
    "Prabhudas Lilladher",
    "JM Financial"
)

private val RELIABLE_SOURCES = listOf(
    "Economic Times",
    "NDTV Profit",
    "LiveMint",
    "Moneycontrol",
    "Business Standard",
    "Broker Research Report"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRecommendationSheet(
    draft: AddSheetDraft,
    searchResults: List<StockSearchResult>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchResult: (StockSearchResult) -> Unit,
    onFetchCmp: () -> Unit,
    onUpdateDraft: ((AddSheetDraft) -> AddSheetDraft) -> Unit,
    onSave: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = BrokerPulseTheme.extendedColors
    var searchQuery by remember(draft.existingId) { mutableStateOf(draft.companyName.ifBlank { draft.symbol }) }
    var enterImmediately by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (draft.existingId == null) "Track Brokerage Call" else "Edit Recommendation",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Search any NSE/BSE stock to fetch live CMP and set Entry, Target & Stop Loss alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            // Live Indian Stock Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                label = { Text("Search Indian Stock (e.g. Reliance, Tata Motors, SBIN)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stock_search_input")
            )

            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, ext.cardBorder)
                ) {
                    Column {
                        searchResults.take(5).forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = result.companyName
                                        onSelectSearchResult(result)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.companyName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = result.symbol,
                                        style = MonoStyles.priceSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = result.exchange,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Symbol & Live CMP readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft.symbol,
                    onValueChange = { sym -> onUpdateDraft { it.copy(symbol = sym.uppercase()) } },
                    label = { Text("NSE/BSE Symbol") },
                    placeholder = { Text("RELIANCE.NS") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onFetchCmp) {
                            Icon(Icons.Filled.Sync, contentDescription = "Fetch live CMP")
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("symbol_input")
                )

                Surface(
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = "LIVE CMP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = draft.liveCmp?.let { "₹${TradeEvaluator.formatPrice(it)}" } ?: "--",
                            style = MonoStyles.priceMedium,
                            color = ext.bullish
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // BUY / SELL Segmented Control
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CallType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = draft.callType == type,
                        onClick = { onUpdateDraft { it.copy(callType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index, CallType.entries.size),
                        modifier = Modifier.testTag("call_type_${type.name}")
                    ) {
                        Text(type.label)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Entry, Target, Stop Loss, Quantity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.entryPrice,
                    onValueChange = { v -> onUpdateDraft { it.copy(entryPrice = v) } },
                    label = { Text("Entry (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("entry_price_input")
                )
                OutlinedTextField(
                    value = draft.targetPrice,
                    onValueChange = { v -> onUpdateDraft { it.copy(targetPrice = v) } },
                    label = { Text("Target (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("target_price_input")
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.stopLoss,
                    onValueChange = { v -> onUpdateDraft { it.copy(stopLoss = v) } },
                    label = { Text("Stop Loss (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stoploss_price_input")
                )
                OutlinedTextField(
                    value = draft.quantity.toString(),
                    onValueChange = { v ->
                        val qty = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onUpdateDraft { it.copy(quantity = qty) }
                    },
                    label = { Text("Qty (Shares)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quantity_input")
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Brokerage House",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                POPULAR_BROKERAGES.forEach { broker ->
                    FilterChip(
                        selected = draft.brokerage.equals(broker, ignoreCase = true),
                        onClick = { onUpdateDraft { it.copy(brokerage = broker) } },
                        label = { Text(broker, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Published Source",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RELIABLE_SOURCES.forEach { src ->
                    FilterChip(
                        selected = draft.sourceName.equals(src, ignoreCase = true),
                        onClick = { onUpdateDraft { it.copy(sourceName = src) } },
                        label = { Text(src, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdateDraft { it.copy(notes = v) } },
                label = { Text("Analysis Notes / Rationale") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alert Notifications on Entry / Target / SL",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = draft.alertsEnabled,
                    onCheckedChange = { enabled -> onUpdateDraft { it.copy(alertsEnabled = enabled) } }
                )
            }

            if (draft.existingId == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enter Portfolio immediately if CMP ≈ Entry",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enterImmediately,
                        onCheckedChange = { enterImmediately = it }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(enterImmediately) },
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("save_recommendation_button")
                ) {
                    Text(if (draft.existingId == null) "Start Tracking" else "Save Changes")
                }
            }
        }
    }
}
