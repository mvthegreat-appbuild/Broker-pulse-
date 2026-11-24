package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.BrokerPulseDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.BrokerageFeedService
import com.example.data.remote.MarketQuoteService
import com.example.data.repository.BrokerPulseRepository
import com.example.notifications.AlertNotificationManager
import com.example.ui.BrokerPulseViewModel
import com.example.ui.components.AddEditRecommendationSheet
import com.example.ui.screens.AnalyticsAlertsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.ResearchFeedScreen
import com.example.ui.theme.BrokerPulseTheme

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_FEED = "research_feed"
const val ROUTE_PORTFOLIO = "portfolio"
const val ROUTE_ANALYTICS = "analytics_alerts"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navTabs = listOf(
    NavTab(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    NavTab(ROUTE_FEED, R.string.nav_feed, Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    NavTab(ROUTE_PORTFOLIO, R.string.nav_portfolio, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    NavTab(ROUTE_ANALYTICS, R.string.nav_analytics, Icons.Filled.Analytics, Icons.Outlined.Analytics)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlertNotificationManager(applicationContext).ensureChannelCreated()

        setContent {
            val viewModel: BrokerPulseViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = BrokerPulseDatabase.getInstance(app)
                        val prefs = UserPreferencesRepository(app)
                        val notifier = AlertNotificationManager(app)
                        val repo = BrokerPulseRepository(
                            dao = db.brokerPulseDao(),
                            quoteService = MarketQuoteService(),
                            feedService = BrokerageFeedService(),
                            prefsRepo = prefs,
                            notificationManager = notifier
                        )
                        BrokerPulseViewModel(repo, prefs)
                    }
                }
            )
            BrokerPulseRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun BrokerPulseRoot(viewModel: BrokerPulseViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val useDark = when (preferences.themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    BrokerPulseTheme(darkTheme = useDark) {
        val context = LocalContext.current
        var hasNotificationPermission by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermission = granted
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.snackbarMessages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val allRecommendations by viewModel.recommendations.collectAsStateWithLifecycle()
        val filteredRecommendations by viewModel.filteredRecommendations.collectAsStateWithLifecycle()
        val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
        val indices by viewModel.indices.collectAsStateWithLifecycle()
        val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
        val isRefreshingQuotes by viewModel.isRefreshingQuotes.collectAsStateWithLifecycle()
        val isLoadingFeed by viewModel.isLoadingFeed.collectAsStateWithLifecycle()
        val feedError by viewModel.feedError.collectAsStateWithLifecycle()
        val lastSync by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
        val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
        val brokerFilter by viewModel.brokerFilter.collectAsStateWithLifecycle()
        val alerts by viewModel.alertEvents.collectAsStateWithLifecycle()
        val unreadCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
        val leaderboard by viewModel.brokerageLeaderboard.collectAsStateWithLifecycle()
        val sheetDraft by viewModel.sheetDraft.collectAsStateWithLifecycle()
        val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
        val isSearchingStocks by viewModel.isSearchingStocks.collectAsStateWithLifecycle()

        val allBrokerages = remember(allRecommendations) {
            allRecommendations.map { it.brokerage }.distinct().sorted()
        }

        fun navigateTo(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (currentRoute == ROUTE_DASHBOARD || currentRoute == ROUTE_PORTFOLIO) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.openNewRecommendationSheet() },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("Track Call") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_recommendation")
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            navTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(tab.route) },
                                    icon = {
                                        if (tab.route == ROUTE_ANALYTICS && unreadCount > 0) {
                                            BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail {
                            navTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(tab.route) },
                                    icon = {
                                        if (tab.route == ROUTE_ANALYTICS && unreadCount > 0) {
                                            BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    stats = stats,
                                    indices = indices,
                                    recommendations = filteredRecommendations,
                                    allBrokerages = allBrokerages,
                                    selectedStatus = statusFilter,
                                    selectedBroker = brokerFilter,
                                    isRefreshing = isRefreshingQuotes,
                                    lastSyncTimestamp = lastSync,
                                    onSelectStatus = viewModel::setStatusFilter,
                                    onSelectBroker = viewModel::setBrokerFilter,
                                    onRefreshQuotes = viewModel::refreshMarketDataManual,
                                    onOpenResearchFeed = { navigateTo(ROUTE_FEED) },
                                    onAddNew = viewModel::openNewRecommendationSheet,
                                    onEdit = viewModel::openEditRecommendationSheet,
                                    onDelete = viewModel::deleteRecommendation,
                                    onToggleAlerts = viewModel::toggleAlertsForRecommendation,
                                    onTriggerEntryNow = viewModel::triggerEntryNow
                                )
                            }
                            composable(ROUTE_FEED) {
                                ResearchFeedScreen(
                                    feedItems = feedItems,
                                    isLoading = isLoadingFeed,
                                    errorMessage = feedError,
                                    onRefreshFeed = viewModel::refreshFeed,
                                    onTrackCall = viewModel::openSheetFromFeedItem
                                )
                            }
                            composable(ROUTE_PORTFOLIO) {
                                PortfolioScreen(
                                    stats = stats,
                                    recommendations = allRecommendations,
                                    onEdit = viewModel::openEditRecommendationSheet,
                                    onDelete = viewModel::deleteRecommendation,
                                    onToggleAlerts = viewModel::toggleAlertsForRecommendation
                                )
                            }
                            composable(ROUTE_ANALYTICS) {
                                AnalyticsAlertsScreen(
                                    stats = stats,
                                    recommendations = allRecommendations,
                                    leaderboard = leaderboard,
                                    alerts = alerts,
                                    preferences = preferences,
                                    hasNotificationPermission = hasNotificationPermission,
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                                    onSetPollingInterval = viewModel::setPollingIntervalSeconds,
                                    onSetAutoEvaluate = viewModel::setAutoEvaluateTriggers,
                                    onSetThemeMode = viewModel::setThemeMode,
                                    onMarkAllRead = viewModel::markAllAlertsRead
                                )
                            }
                        }
                    }
                }
            }

            sheetDraft?.let { draft ->
                AddEditRecommendationSheet(
                    draft = draft,
                    searchResults = searchResults,
                    isSearching = isSearchingStocks,
                    onSearchQueryChange = viewModel::searchStocks,
                    onSelectSearchResult = viewModel::selectSearchedStock,
                    onFetchCmp = viewModel::fetchCmpForCurrentSymbol,
                    onUpdateDraft = viewModel::updateDraft,
                    onSave = viewModel::saveDraft,
                    onDismiss = viewModel::dismissSheet
                )
            }
        }
    }
}
