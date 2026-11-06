package com.sub2api.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.sub2api.mobile.AppPhase
import com.sub2api.mobile.AppUiState
import com.sub2api.mobile.MainViewModel
import com.sub2api.mobile.ui.components.ServerManagerDialog
import com.sub2api.mobile.ui.components.AppUpdateDialog
import com.sub2api.mobile.ui.icons.AppIcon
import com.sub2api.mobile.ui.icons.Icon
import com.sub2api.mobile.ui.icons.Icons
import com.sub2api.mobile.ui.screens.AccountsScreen
import com.sub2api.mobile.ui.screens.ChannelStatusScreen
import com.sub2api.mobile.ui.screens.DashboardScreen
import com.sub2api.mobile.ui.screens.KeysScreen
import com.sub2api.mobile.ui.screens.LoginScreen
import com.sub2api.mobile.ui.screens.ProfileScreen
import com.sub2api.mobile.ui.screens.ServerSetupScreen
import com.sub2api.mobile.ui.screens.SubscriptionsScreen
import com.sub2api.mobile.ui.screens.TwoFactorScreen
import com.sub2api.mobile.ui.screens.UsageScreen
import com.sub2api.mobile.ui.screens.AdminUsageScreen

internal enum class MainTab(val label: String, val title: String, val icon: AppIcon) {
    DASHBOARD("首页", "概览", Icons.Outlined.Dashboard),
    ACCOUNTS("AI 账号", "AI 平台账号", Icons.Outlined.Memory),
    KEYS("密钥", "API Keys", Icons.Outlined.Key),
    USAGE("用量", "我的用量记录", Icons.Outlined.BarChart),
    ALL_USAGE("所有用量", "所有用量", Icons.Outlined.BarChart),
    SUBSCRIPTIONS("订阅", "订阅与兑换", Icons.Outlined.CreditCard),
    CHANNEL_STATUS("渠道状态", "渠道状态", Icons.Outlined.NetworkCheck),
    PROFILE("我的", "账户", Icons.Outlined.AccountCircle),
}

internal fun mainTabsForRole(role: String?): List<MainTab> =
    if (role == "admin") {
        listOf(MainTab.DASHBOARD, MainTab.ACCOUNTS, MainTab.USAGE, MainTab.ALL_USAGE, MainTab.PROFILE)
    } else {
        listOf(MainTab.DASHBOARD, MainTab.KEYS, MainTab.USAGE, MainTab.SUBSCRIPTIONS, MainTab.PROFILE)
    }

@Composable
fun Sub2ApiApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val path = pendingInstallPath ?: return@rememberLauncherForActivityResult
        if (canInstallApks(context)) {
            launchApkInstaller(context, path).onFailure { error ->
                viewModel.reportUpdateInstallError("无法打开安装程序：${error.message ?: "未知错误"}")
            }
        } else {
            viewModel.reportUpdateInstallError("未获得安装应用权限，可稍后再次点击“安装更新”")
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        key(state.sessionKey) {
            when (state.phase) {
                AppPhase.CHECKING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                AppPhase.SERVER -> ServerSetupScreen(state, viewModel::configureServer, viewModel::showServerManager)
                AppPhase.LOGIN -> LoginScreen(
                    state = state,
                    onLogin = viewModel::login,
                    onChangeServer = viewModel::changeServer,
                    onOpenUrl = { runCatching { uriHandler.openUri(it) } },
                )
                AppPhase.TWO_FACTOR -> TwoFactorScreen(state, viewModel::submitTwoFactor, viewModel::cancelTwoFactor)
                AppPhase.MAIN -> MainShell(state, viewModel, onOpenUrl = { runCatching { uriHandler.openUri(it) } })
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (state.serverManagerVisible) {
        ServerManagerDialog(state, viewModel::selectServer, viewModel::saveServer, viewModel::removeServer, viewModel::dismissServerManager)
    }
    if (state.updateDialogVisible) {
        state.availableUpdate?.let { release ->
            AppUpdateDialog(
                release = release,
                download = state.updateDownload,
                onDismiss = viewModel::dismissUpdateDialog,
                onDownload = viewModel::downloadUpdate,
                onInstall = { path ->
                    pendingInstallPath = path
                    if (canInstallApks(context)) {
                        launchApkInstaller(context, path).onFailure { error ->
                            viewModel.reportUpdateInstallError("无法打开安装程序：${error.message ?: "未知错误"}")
                        }
                    } else {
                        runCatching { unknownSourcesLauncher.launch(unknownSourcesSettingsIntent(context)) }
                            .onFailure { viewModel.reportUpdateInstallError("无法打开“允许安装应用”设置") }
                    }
                },
                onOpenReleasePage = { runCatching { uriHandler.openUri(it) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(state: AppUiState, viewModel: MainViewModel, onOpenUrl: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    val isAdmin = state.user?.role == "admin"
    val tabs = mainTabsForRole(state.user?.role)
    val isProfileChild = selected == MainTab.CHANNEL_STATUS || (isAdmin && selected in setOf(MainTab.SUBSCRIPTIONS, MainTab.KEYS))
    LaunchedEffect(isAdmin) {
        if (selected in setOf(MainTab.ACCOUNTS, MainTab.ALL_USAGE) && !isAdmin) selected = MainTab.DASHBOARD
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(selected.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(state.activeServerName, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    if (isProfileChild) {
                            IconButton(
                                onClick = { selected = MainTab.PROFILE },
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回账户", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    when (selected) {
                        MainTab.PROFILE, MainTab.ALL_USAGE -> Unit
                        MainTab.USAGE -> {
                            IconButton(onClick = { viewModel.loadUsage() }, enabled = !state.personalUsage.loading) {
                                Icon(Icons.Outlined.Refresh, "刷新个人用量")
                            }
                        }
                        MainTab.CHANNEL_STATUS -> {
                            IconButton(
                                onClick = viewModel::refreshChannelMonitors,
                                enabled = !state.channelMonitorsLoading,
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            ) {
                                if (state.channelMonitorsLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Refresh, "刷新渠道状态")
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = viewModel::refreshAll,
                                enabled = !state.refreshing,
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            ) {
                                if (state.refreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Refresh, "刷新")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab || (tab == MainTab.PROFILE && isProfileChild),
                            onClick = { selected = tab },
                            icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(21.dp)) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                MainTab.DASHBOARD -> DashboardScreen(state)
                MainTab.ACCOUNTS -> if (isAdmin) {
                    AccountsScreen(
                        state = state,
                        onFilter = viewModel::refreshAiAccounts,
                        onCreate = viewModel::createAiAccount,
                        onUpdate = viewModel::updateAiAccount,
                        onTest = viewModel::testAiAccount,
                        onLoadModels = viewModel::loadAccountModels,
                        onClearError = viewModel::clearAiAccountError,
                        onToggle = viewModel::toggleAiAccountScheduling,
                        onDelete = viewModel::deleteAiAccount,
                        onRefreshQuotas = viewModel::refreshAiQuotas,
                    )
                } else {
                    DashboardScreen(state)
                }
                MainTab.KEYS -> KeysScreen(
                    state = state,
                    onCreate = viewModel::createKey,
                    onToggle = viewModel::toggleKey,
                    onDelete = viewModel::deleteKey,
                )
                MainTab.USAGE -> UsageScreen(state, viewModel::setUsagePeriod,
                    onRefresh = { viewModel.loadUsage() }, onPageChange = { viewModel.loadUsage(page = it) })
                MainTab.ALL_USAGE -> AdminUsageScreen(state,
                    onLoad = { period, page, filters -> viewModel.loadUsage(true, period, page, filters) },
                    onRanking = { period, metric, force -> viewModel.loadRanking(period, metric, force) },
                    onCancelRanking = viewModel::cancelRanking)
                MainTab.SUBSCRIPTIONS -> SubscriptionsScreen(state, viewModel::redeem)
                MainTab.CHANNEL_STATUS -> ChannelStatusScreen(
                    state = state,
                    onRefresh = viewModel::refreshChannelMonitors,
                    onLoadDetail = viewModel::loadChannelMonitorDetail,
                )
                MainTab.PROFILE -> ProfileScreen(
                    state = state,
                    onUpdateUsername = viewModel::updateUsername,
                    onLogout = viewModel::logout,
                    onChangeServer = viewModel::changeServer,
                    onOpenUrl = onOpenUrl,
                    onOpenKeys = if (isAdmin) ({ selected = MainTab.KEYS }) else null,
                    onOpenSubscriptions = if (isAdmin) ({ selected = MainTab.SUBSCRIPTIONS }) else null,
                    onOpenChannelStatus = { selected = MainTab.CHANNEL_STATUS },
                    onCheckForUpdate = viewModel::checkForUpdate,
                )
            }
        }
    }
}
