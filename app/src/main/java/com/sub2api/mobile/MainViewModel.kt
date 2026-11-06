package com.sub2api.mobile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sub2api.mobile.data.ApiException
import com.sub2api.mobile.data.ApiKey
import com.sub2api.mobile.data.AiAccount
import com.sub2api.mobile.data.AiAccountQuota
import com.sub2api.mobile.data.AiAccountTestResult
import com.sub2api.mobile.data.AppRelease
import com.sub2api.mobile.data.ChannelMonitorDetail
import com.sub2api.mobile.data.ChannelMonitorView
import com.sub2api.mobile.data.CreateAiAccountRequest
import com.sub2api.mobile.data.DashboardStats
import com.sub2api.mobile.data.Group
import com.sub2api.mobile.data.LoginResult
import com.sub2api.mobile.data.PublicSettings
import com.sub2api.mobile.data.RedeemHistory
import com.sub2api.mobile.data.ServerUrl
import com.sub2api.mobile.data.ServerSummary
import com.sub2api.mobile.data.AccountModel
import com.sub2api.mobile.data.UsageFilters
import com.sub2api.mobile.data.UsageRanking
import com.sub2api.mobile.data.ApiRepository
import com.sub2api.mobile.data.SubscriptionProgress
import com.sub2api.mobile.data.SubscriptionSummary
import com.sub2api.mobile.data.UsageLog
import com.sub2api.mobile.data.UsageStats
import com.sub2api.mobile.data.User
import com.sub2api.mobile.data.UserSubscription
import com.sub2api.mobile.data.UpdateAiAccountRequest
import com.sub2api.mobile.data.isVersionNewer
import com.sub2api.mobile.data.summarizeAiQuotas
import com.sub2api.mobile.widget.TotalQuotaWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

enum class AppPhase { CHECKING, SERVER, LOGIN, TWO_FACTOR, MAIN }

enum class UpdateDownloadStatus { IDLE, DOWNLOADING, READY, FAILED }

data class UpdateDownloadState(
    val status: UpdateDownloadStatus = UpdateDownloadStatus.IDLE,
    val progressPercent: Int? = null,
    val apkPath: String? = null,
    val error: String? = null,
)

data class UsageViewState(
    val stats: UsageStats? = null,
    val logs: List<UsageLog> = emptyList(),
    val period: String = "today",
    val filters: UsageFilters = UsageFilters(),
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

data class AppUiState(
    val phase: AppPhase = AppPhase.CHECKING,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val serverInput: String = "",
    val serverError: String? = null,
    val servers: List<ServerSummary> = emptyList(),
    val activeServerId: String = "",
    val activeServerName: String = "",
    val sessionKey: String = "",
    val serverManagerVisible: Boolean = false,
    val serverManagerError: String? = null,
    val storageError: String? = null,
    val settings: PublicSettings? = null,
    val user: User? = null,
    val dashboard: DashboardStats? = null,
    val subscriptionSummary: SubscriptionSummary? = null,
    val keys: List<ApiKey> = emptyList(),
    val groups: List<Group> = emptyList(),
    val personalUsage: UsageViewState = UsageViewState(),
    val adminUsage: UsageViewState = UsageViewState(),
    val ranking: UsageRanking? = null,
    val rankingPeriod: String = "today",
    val rankingMetric: String = "total_tokens",
    val rankingLoading: Boolean = false,
    val rankingScanned: Int = 0,
    val rankingError: String? = null,
    val accountModels: Map<Long, List<AccountModel>> = emptyMap(),
    val accountModelsLoading: Set<Long> = emptySet(),
    val accountModelErrors: Map<Long, String> = emptyMap(),
    val accountTestResults: Map<Long, AiAccountTestResult> = emptyMap(),
    val subscriptions: List<UserSubscription> = emptyList(),
    val subscriptionProgress: List<SubscriptionProgress> = emptyList(),
    val redeemHistory: List<RedeemHistory> = emptyList(),
    val channelMonitors: List<ChannelMonitorView> = emptyList(),
    val channelMonitorsLoading: Boolean = false,
    val channelMonitorsError: String? = null,
    val channelMonitorDetails: Map<Long, ChannelMonitorDetail> = emptyMap(),
    val channelMonitorDetailsLoading: Set<Long> = emptySet(),
    val channelMonitorDetailErrors: Map<Long, String> = emptyMap(),
    val aiAccounts: List<AiAccount> = emptyList(),
    val allAiAccounts: List<AiAccount> = emptyList(),
    val aiAccountQuotas: Map<Long, AiAccountQuota> = emptyMap(),
    val adminGroups: List<Group> = emptyList(),
    val accountsLoading: Boolean = false,
    val quotaLoading: Boolean = false,
    val updateChecking: Boolean = false,
    val availableUpdate: AppRelease? = null,
    val updateDialogVisible: Boolean = false,
    val updateDownload: UpdateDownloadState = UpdateDownloadState(),
    val twoFactorToken: String = "",
    val twoFactorHint: String = "",
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as Sub2ApiApplication
    private val updateRepository = app.updateRepository
    private val sessionStore = app.sessionStore
    private var quotaLoadGeneration = 0L
    private var personalUsageJob: Job? = null
    private var adminUsageJob: Job? = null
    private var rankingJob: Job? = null
    private val accountModelJobs = mutableMapOf<Long, Job>()
    private var sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
    private var observedSession = sessionStore.state.value

    private class SessionWork(val repository: ApiRepository)

    private fun launchSession(block: suspend SessionWork.() -> Unit): Job {
        val work = SessionWork(app.repository())
        return sessionScope.launch { work.block() }
    }
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        resetUiState()
        initialize()
        checkForUpdate(manual = false)
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                if (session.profileId != observedSession.profileId || session.revision != observedSession.revision ||
                    session.selectionVersion != observedSession.selectionVersion
                ) restartSession()
                _uiState.update {
                    val current = sessionStore.state.value
                    it.copy(servers = sessionStore.profiles.value, activeServerId = current.profileId,
                        activeServerName = current.serverName, storageError = sessionStore.storageError)
                }
            }
        }
        viewModelScope.launch {
            sessionStore.profiles.collect { profiles -> _uiState.update { it.copy(servers = profiles) } }
        }
    }

    fun checkForUpdate(manual: Boolean = true) {
        if (_uiState.value.updateChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateChecking = true) }
            runCatching { updateRepository.latestRelease() }
                .onSuccess { release ->
                    val newer = isVersionNewer(release.versionName, BuildConfig.VERSION_NAME)
                    _uiState.update { current ->
                        current.copy(
                            updateChecking = false,
                            availableUpdate = release.takeIf { newer },
                            updateDialogVisible = newer,
                            updateDownload = when {
                                !newer -> UpdateDownloadState()
                                current.availableUpdate?.versionName == release.versionName -> current.updateDownload
                                else -> UpdateDownloadState()
                            },
                            message = if (manual && !newer) "当前已是最新版本" else current.message,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            updateChecking = false,
                            message = if (manual) "检查更新失败：${error.userMessage()}" else it.message,
                        )
                    }
                }
        }
    }

    fun dismissUpdateDialog() = _uiState.update { it.copy(updateDialogVisible = false) }

    fun downloadUpdate() {
        val release = _uiState.value.availableUpdate ?: return
        if (_uiState.value.updateDownload.status == UpdateDownloadStatus.DOWNLOADING) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateDialogVisible = true,
                    updateDownload = UpdateDownloadState(status = UpdateDownloadStatus.DOWNLOADING),
                )
            }
            runCatching {
                updateRepository.downloadApk(
                    release = release,
                    directory = File(getApplication<Application>().filesDir, "updates"),
                    onProgress = { progress ->
                        _uiState.update { current ->
                            if (current.availableUpdate?.versionName != release.versionName) current
                            else current.copy(
                                updateDownload = current.updateDownload.copy(progressPercent = progress),
                            )
                        }
                    },
                )
            }.onSuccess { apk ->
                _uiState.update { current ->
                    if (current.availableUpdate?.versionName != release.versionName) current
                    else current.copy(
                        updateDownload = UpdateDownloadState(
                            status = UpdateDownloadStatus.READY,
                            progressPercent = 100,
                            apkPath = apk.absolutePath,
                        ),
                        updateDialogVisible = true,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (current.availableUpdate?.versionName != release.versionName) current
                    else current.copy(
                        updateDownload = UpdateDownloadState(
                            status = UpdateDownloadStatus.FAILED,
                            error = error.userMessage(),
                        ),
                        updateDialogVisible = true,
                    )
                }
            }
        }
    }

    fun reportUpdateInstallError(message: String) = showMessage(message)

    private fun initialize() = launchSession {
        val session = sessionStore.state.value
        if (!session.isConfigured) {
            TotalQuotaWidget.publishServerRequired(app)
            _uiState.update { it.copy(phase = AppPhase.SERVER) }
            return@launchSession
        }
        _uiState.update { it.copy(serverInput = ServerUrl.webRoot(session.serverUrl), loading = true) }
        runSessionCatching { repository.testServer() }
            .onSuccess { settings ->
                _uiState.update { it.copy(settings = settings, loading = false) }
                if (session.isAuthenticated) {
                    loadAll()
                } else {
                    TotalQuotaWidget.publishLoginRequired(app)
                    _uiState.update { it.copy(phase = AppPhase.LOGIN) }
                }
            }
            .onFailure { error ->
                TotalQuotaWidget.publishServerRequired(app)
                _uiState.update {
                    it.copy(phase = AppPhase.SERVER, loading = false, serverError = error.userMessage())
                }
            }
    }

    fun configureServer(rawUrl: String) {
        runCatching {
            val url = ServerUrl.normalize(rawUrl)
            val existing = sessionStore.profiles.value.firstOrNull { it.serverUrl == url }
            if (existing != null) {
                sessionStore.selectServer(existing.id)
                restartSession()
            } else {
                saveServer(null, java.net.URI(url).host, url)
                _uiState.update { it.copy(serverError = it.serverManagerError) }
            }
        }.onFailure { error -> _uiState.update { it.copy(serverError = error.userMessage()) } }
    }

    fun showServerManager() = _uiState.update { it.copy(serverManagerVisible = true, serverManagerError = null) }
    fun dismissServerManager() = _uiState.update { it.copy(serverManagerVisible = false, serverManagerError = null) }

    fun saveServer(id: String?, name: String, url: String): Boolean = runCatching {
        val savedId = sessionStore.saveServer(id, name, url)
        if (id == null) sessionStore.selectServer(savedId)
        if (!sessionStore.isActive(observedSession)) restartSession()
        _uiState.update { it.copy(servers = sessionStore.profiles.value,
            activeServerName = sessionStore.state.value.serverName, serverManagerError = null) }
        com.sub2api.mobile.widget.QuotaWidgetRenderer.updateAll(app)
    }.fold(
        onSuccess = { true },
        onFailure = { error -> _uiState.update { it.copy(serverManagerError = error.userMessage()) }; false },
    )

    fun selectServer(id: String) {
        runCatching {
            sessionStore.selectServer(id)
            if (!sessionStore.isActive(observedSession) || _uiState.value.phase == AppPhase.SERVER) restartSession()
            dismissServerManager()
        }.onFailure { error -> _uiState.update { it.copy(serverManagerError = error.userMessage()) } }
    }

    fun removeServer(id: String) {
        runCatching {
            sessionStore.removeServer(id)
            if (!sessionStore.isActive(observedSession)) restartSession()
            _uiState.update { it.copy(servers = sessionStore.profiles.value, serverManagerError = null) }
        }.onFailure { error -> _uiState.update { it.copy(serverManagerError = error.userMessage()) } }
    }

    private fun restartSession() {
        observedSession = sessionStore.state.value
        sessionJob.cancel()
        accountModelJobs.clear()
        sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
        quotaLoadGeneration++
        resetUiState()
        TotalQuotaWidget.publishLoginRequired(app)
        if (observedSession.isAuthenticated) com.sub2api.mobile.widget.QuotaWidgetWork.enqueueRefresh(app, force = false)
        initialize()
    }

    private fun resetUiState() {
        val session = sessionStore.state.value
        _uiState.update { current ->
            AppUiState(
                serverInput = ServerUrl.webRoot(session.serverUrl),
                servers = sessionStore.profiles.value,
                activeServerId = session.profileId,
                activeServerName = session.serverName,
                sessionKey = "${session.profileId}:${session.revision}:${session.selectionVersion}",
                serverManagerVisible = current.serverManagerVisible,
                storageError = sessionStore.storageError,
                updateChecking = current.updateChecking, availableUpdate = current.availableUpdate,
                updateDialogVisible = current.updateDialogVisible, updateDownload = current.updateDownload,
            )
        }
    }

    fun login(email: String, password: String) = launchSession {
        if (email.isBlank() || password.isBlank()) {
            showMessage("请输入邮箱和密码")
            return@launchSession
        }
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.login(email, password) }
            .onSuccess(::handleLoginResult)
            .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.userMessage()) } }
    }

    fun submitTwoFactor(code: String) = launchSession {
        if (code.length != 6) {
            showMessage("请输入 6 位验证码")
            return@launchSession
        }
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.login2Fa(_uiState.value.twoFactorToken, code) }
            .onSuccess(::handleLoginResult)
            .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.userMessage()) } }
    }

    private fun handleLoginResult(result: LoginResult) {
        if (result.requires2fa) {
            _uiState.update {
                it.copy(
                    phase = AppPhase.TWO_FACTOR,
                    loading = false,
                    twoFactorToken = result.tempToken.orEmpty(),
                    twoFactorHint = result.userEmailMasked.orEmpty(),
                )
            }
        } else {
            _uiState.update { it.copy(phase = AppPhase.MAIN, loading = false, user = result.user) }
            loadAll()
        }
    }

    fun cancelTwoFactor() = _uiState.update { it.copy(phase = AppPhase.LOGIN, twoFactorToken = "") }

    fun refreshAll() = loadAll(refreshing = true)

    private fun loadAll(refreshing: Boolean = false) = launchSession {
        _uiState.update { it.copy(phase = AppPhase.MAIN, refreshing = refreshing, loading = !refreshing && it.user == null) }
        val user = runSessionCatching { repository.currentUser() }.getOrElse { error ->
            if (error is ApiException && error.status == 401) {
                repository.clearSession()
                quotaLoadGeneration++
                TotalQuotaWidget.publishLoginRequired(app)
                _uiState.update { it.copy(phase = AppPhase.LOGIN, loading = false, refreshing = false, message = error.userMessage()) }
                return@launchSession
            }
            _uiState.update { it.copy(loading = false, refreshing = false, message = error.userMessage()) }
            return@launchSession
        }

        if (user.role != "admin") {
            adminUsageJob?.cancel()
            rankingJob?.cancel()
            accountModelJobs.values.forEach { it.cancel() }
            accountModelJobs.clear()
            TotalQuotaWidget.publishAdminRequired(app)
        }

        var aiAccountsError: Throwable? = null
        coroutineScope {
            val dashboard = async { runSessionCatching { repository.dashboardStats() } }
            val summary = async { runSessionCatching { repository.subscriptionSummary() } }
            val keys = async { runSessionCatching { repository.keys() } }
            val groups = async { runSessionCatching { repository.groups() } }
            val subscriptions = async { runSessionCatching { repository.subscriptions() } }
            val history = async { runSessionCatching { repository.redeemHistory() } }
            val aiAccounts = async {
                if (user.role == "admin") runSessionCatching { repository.aiAccounts() }
                else Result.success(emptyList())
            }
            val adminGroups = async {
                if (user.role == "admin") runSessionCatching { repository.adminGroups() }
                else Result.success(emptyList())
            }

            val dashboardResult = dashboard.await()
            val summaryResult = summary.await()
            val keysResult = keys.await()
            val groupsResult = groups.await()
            val subscriptionsResult = subscriptions.await()
            val historyResult = history.await()
            val aiAccountsResult = aiAccounts.await()
            val adminGroupsResult = adminGroups.await()
            aiAccountsError = aiAccountsResult.exceptionOrNull()

            _uiState.update { state ->
                val loadedAccounts = aiAccountsResult.getOrNull() ?: state.allAiAccounts
                val loadedAccountIds = loadedAccounts.mapTo(mutableSetOf()) { it.id }
                state.copy(
                    user = user,
                    dashboard = dashboardResult.getOrNull() ?: state.dashboard,
                    subscriptionSummary = summaryResult.getOrNull() ?: state.subscriptionSummary,
                    keys = keysResult.getOrNull() ?: state.keys,
                    groups = groupsResult.getOrNull() ?: state.groups,
                    adminUsage = if (user.role == "admin") state.adminUsage else UsageViewState(),
                    ranking = if (user.role == "admin") state.ranking else null,
                    rankingLoading = user.role == "admin" && state.rankingLoading,
                    rankingError = if (user.role == "admin") state.rankingError else null,
                    accountModels = if (user.role == "admin") state.accountModels else emptyMap(),
                    accountModelsLoading = if (user.role == "admin") state.accountModelsLoading else emptySet(),
                    accountModelErrors = if (user.role == "admin") state.accountModelErrors else emptyMap(),
                    accountTestResults = if (user.role == "admin") state.accountTestResults else emptyMap(),
                    subscriptions = subscriptionsResult.getOrNull()?.first ?: state.subscriptions,
                    subscriptionProgress = subscriptionsResult.getOrNull()?.second ?: state.subscriptionProgress,
                    redeemHistory = historyResult.getOrNull() ?: state.redeemHistory,
                    aiAccounts = loadedAccounts,
                    allAiAccounts = loadedAccounts,
                    aiAccountQuotas = if (user.role == "admin") {
                        state.aiAccountQuotas.filterKeys { it in loadedAccountIds }
                    } else {
                        emptyMap()
                    },
                    adminGroups = adminGroupsResult.getOrNull() ?: state.adminGroups,
                    loading = false,
                    refreshing = false,
                )
            }
        }
        if (user.role == "admin") {
            val error = aiAccountsError
            if (error == null) {
                loadAiAccountQuotas(repository, _uiState.value.allAiAccounts)
            } else {
                TotalQuotaWidget.publishError(app, error.message)
            }
        }
    }

    fun refreshKeys() = launchSession {
        _uiState.update { it.copy(refreshing = true) }
        runSessionCatching { repository.keys() }
            .onSuccess { keys -> _uiState.update { it.copy(keys = keys, refreshing = false) } }
            .onFailure { error -> _uiState.update { it.copy(refreshing = false, message = error.userMessage()) } }
    }

    fun createKey(name: String, groupId: Long?, quota: Double?, expires: Int?, onDone: () -> Unit) = launchSession {
        if (name.isBlank()) {
            showMessage("请输入密钥名称")
            return@launchSession
        }
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.createKey(name, groupId, quota, expires) }
            .onSuccess { key ->
                _uiState.update { it.copy(loading = false, keys = listOf(key) + it.keys, message = "API Key 已创建") }
                onDone()
            }
            .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.userMessage()) } }
    }

    fun toggleKey(key: ApiKey) = launchSession {
        runSessionCatching { repository.setKeyStatus(key, key.status != "active") }
            .onSuccess { updated ->
                _uiState.update { state -> state.copy(keys = state.keys.map { if (it.id == updated.id) updated else it }) }
            }
            .onFailure { showMessage(it.userMessage()) }
    }

    fun deleteKey(key: ApiKey) = launchSession {
        runSessionCatching { repository.deleteKey(key.id) }
            .onSuccess { _uiState.update { it.copy(keys = it.keys.filterNot { item -> item.id == key.id }, message = "API Key 已删除") } }
            .onFailure { showMessage(it.userMessage()) }
    }

    fun loadUsage(
        admin: Boolean = false,
        period: String = (if (admin) _uiState.value.adminUsage else _uiState.value.personalUsage).period,
        page: Int = 1,
        filters: UsageFilters = (if (admin) _uiState.value.adminUsage else _uiState.value.personalUsage).filters,
    ) {
        if (admin && _uiState.value.user?.role != "admin") return
        if (admin) adminUsageJob?.cancel() else personalUsageJob?.cancel()
        fun updateUsage(transform: (UsageViewState) -> UsageViewState) = _uiState.update {
            if (admin) it.copy(adminUsage = transform(it.adminUsage)) else it.copy(personalUsage = transform(it.personalUsage))
        }
        updateUsage { previous ->
            val changed = previous.period != period || previous.filters != filters
            previous.copy(period = period, filters = filters, page = page,
                logs = emptyList(), stats = if (changed) null else previous.stats, loading = true, error = null)
        }
        val useAdminEndpoint = _uiState.value.user?.role == "admin"
        if (!admin && useAdminEndpoint && (_uiState.value.user?.id ?: 0) <= 0) {
            updateUsage { it.copy(loading = false, stats = null, error = "无法确定当前用户，请刷新账户信息") }
            return
        }
        val effectiveFilters = if (!admin && useAdminEndpoint) UsageFilters(userId = _uiState.value.user!!.id) else filters
        val job = launchSession {
            runSessionCatching { repository.usage(period, page, admin = useAdminEndpoint, filters = effectiveFilters) }
                .onSuccess { result -> updateUsage { it.copy(stats = result.stats, logs = result.records.items,
                    page = result.records.page, pages = result.records.pages.coerceAtLeast(1), total = result.records.total,
                    loading = false) } }
                .onFailure { error -> updateUsage { it.copy(loading = false, error = error.userMessage()) } }
        }
        if (admin) adminUsageJob = job else personalUsageJob = job
    }

    fun setUsagePeriod(period: String) = loadUsage(period = period)

    fun loadRanking(
        period: String = _uiState.value.rankingPeriod,
        metric: String = _uiState.value.rankingMetric,
        force: Boolean = false,
    ) {
        if (_uiState.value.user?.role != "admin") return
        if (!force && period == "24h" && _uiState.value.rankingPeriod == period && _uiState.value.ranking != null && !_uiState.value.rankingLoading) {
            _uiState.update { it.copy(rankingMetric = metric) }
            return
        }
        rankingJob?.cancel()
        _uiState.update { it.copy(rankingPeriod = period, rankingMetric = metric, ranking = null,
            rankingLoading = true, rankingScanned = 0, rankingError = null) }
        rankingJob = launchSession {
            runSessionCatching {
                repository.userRanking(period, metric) { count -> _uiState.update { it.copy(rankingScanned = count) } }
            }.onSuccess { result -> _uiState.update { it.copy(ranking = result, rankingLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(rankingLoading = false, rankingError = error.userMessage()) } }
        }
    }

    fun cancelRanking() {
        rankingJob?.cancel()
        _uiState.update { it.copy(rankingLoading = false) }
    }

    fun redeem(code: String, onDone: () -> Unit) = launchSession {
        if (code.isBlank()) {
            showMessage("请输入兑换码")
            return@launchSession
        }
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.redeem(code) }
            .onSuccess { result ->
                _uiState.update { it.copy(loading = false, message = result.message.ifBlank { "兑换成功" }) }
                onDone()
                loadAll(refreshing = true)
            }
            .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.userMessage()) } }
    }

    fun updateUsername(username: String, onDone: () -> Unit) = launchSession {
        if (username.isBlank()) return@launchSession
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.updateUsername(username) }
            .onSuccess { user ->
                _uiState.update { it.copy(user = user, loading = false, message = "资料已更新") }
                onDone()
            }
            .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.userMessage()) } }
    }

    fun refreshChannelMonitors() = launchSession {
        _uiState.update { it.copy(channelMonitorsLoading = true, channelMonitorsError = null) }
        runSessionCatching { repository.channelMonitors() }
            .onSuccess { monitors ->
                val ids = monitors.mapTo(mutableSetOf()) { it.id }
                _uiState.update { state ->
                    state.copy(
                        channelMonitors = monitors,
                        channelMonitorsLoading = false,
                        channelMonitorDetails = emptyMap(),
                        channelMonitorDetailsLoading = state.channelMonitorDetailsLoading.filterTo(mutableSetOf()) { it in ids },
                        channelMonitorDetailErrors = emptyMap(),
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(channelMonitorsLoading = false, channelMonitorsError = error.userMessage())
                }
            }
    }

    fun loadChannelMonitorDetail(id: Long, force: Boolean = false) = launchSession {
        val state = _uiState.value
        if (!force && (id in state.channelMonitorDetails || id in state.channelMonitorDetailsLoading)) return@launchSession
        _uiState.update {
            it.copy(
                channelMonitorDetailsLoading = it.channelMonitorDetailsLoading + id,
                channelMonitorDetailErrors = it.channelMonitorDetailErrors - id,
            )
        }
        runSessionCatching { repository.channelMonitorStatus(id) }
            .onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        channelMonitorDetails = it.channelMonitorDetails + (id to detail),
                        channelMonitorDetailsLoading = it.channelMonitorDetailsLoading - id,
                        channelMonitorDetailErrors = it.channelMonitorDetailErrors - id,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        channelMonitorDetailsLoading = it.channelMonitorDetailsLoading - id,
                        channelMonitorDetailErrors = it.channelMonitorDetailErrors + (id to error.userMessage()),
                    )
                }
            }
    }

    fun refreshAiAccounts(platform: String? = null, status: String? = null, search: String? = null) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        _uiState.update { it.copy(accountsLoading = true) }
        val filtered = !platform.isNullOrBlank() || !status.isNullOrBlank() || !search.isNullOrBlank()
        runSessionCatching {
            val visibleAccounts = repository.aiAccounts(platform, status, search)
            val allAccounts = if (filtered) repository.aiAccounts() else visibleAccounts
            visibleAccounts to allAccounts
        }
            .onSuccess { (visibleAccounts, allAccounts) ->
                _uiState.update {
                    it.copy(
                        aiAccounts = visibleAccounts,
                        allAiAccounts = allAccounts,
                        accountsLoading = false,
                    )
                }
                loadAiAccountQuotas(repository, allAccounts)
            }
            .onFailure { error -> _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) } }
    }

    fun createAiAccount(
        request: CreateAiAccountRequest,
        cookieSessionKey: String,
        exchangeAnthropicCookie: Boolean,
        onDone: () -> Unit,
    ) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        if (request.name.isBlank()) {
            showMessage("请输入账号名称")
            return@launchSession
        }
        if (request.credentials.isEmpty() && cookieSessionKey.isBlank()) {
            showMessage("请至少填写一种凭据")
            return@launchSession
        }
        _uiState.update { it.copy(accountsLoading = true) }
        runSessionCatching {
            val credentials = if (exchangeAnthropicCookie && cookieSessionKey.isNotBlank()) {
                request.credentials + repository.exchangeAnthropicCookie(cookieSessionKey, request.type == "setup-token")
            } else {
                request.credentials + cookieSessionKey.takeIf { it.isNotBlank() }?.let { mapOf("cookie" to it.trim()) }.orEmpty()
            }
            repository.createAiAccount(request.copy(credentials = credentials))
        }.onSuccess { account ->
            _uiState.update {
                it.copy(
                    aiAccounts = listOf(account) + it.aiAccounts.filterNot { existing -> existing.id == account.id },
                    allAiAccounts = listOf(account) + it.allAiAccounts.filterNot { existing -> existing.id == account.id },
                    accountsLoading = false,
                    message = "AI 平台账号已创建",
                )
            }
            onDone()
            loadAiAccountQuotas(repository, _uiState.value.allAiAccounts)
        }.onFailure { error -> _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) } }
    }

    fun updateAiAccount(account: AiAccount, request: UpdateAiAccountRequest, onDone: () -> Unit) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        _uiState.update { it.copy(accountsLoading = true) }
        runSessionCatching { repository.updateAiAccount(account.id, request) }
            .onSuccess { updated ->
                replaceAiAccount(updated, "账号设置已保存")
                onDone()
            }
            .onFailure { error -> _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) } }
    }

    fun updateAiCredential(
        account: AiAccount,
        field: String,
        value: String,
        exchangeAnthropicCookie: Boolean,
        onDone: () -> Unit,
    ) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        if (value.isBlank()) {
            showMessage("凭据不能为空")
            return@launchSession
        }
        _uiState.update { it.copy(accountsLoading = true) }
        runSessionCatching {
            if (exchangeAnthropicCookie && account.platform == "anthropic") {
                val credentials = repository.exchangeAnthropicCookie(value, account.type == "setup-token")
                repository.applyOAuthCredentials(account, credentials)
            } else {
                repository.updateCredentialField(account, field, value)
            }
        }.onSuccess { updated ->
            replaceAiAccount(updated, "凭据已安全更新")
            onDone()
            loadAiAccountQuotas(repository, _uiState.value.allAiAccounts, force = true)
        }.onFailure { error -> _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) } }
    }

    fun loadAccountModels(account: AiAccount) {
        if (_uiState.value.user?.role != "admin" || account.id in _uiState.value.accountModelsLoading) return
        _uiState.update { it.copy(accountModelsLoading = it.accountModelsLoading + account.id,
            accountModelErrors = it.accountModelErrors - account.id) }
        accountModelJobs[account.id] = launchSession {
            runSessionCatching { repository.accountModels(account.id) }
                .onSuccess { models -> _uiState.update { it.copy(accountModels = it.accountModels + (account.id to models),
                    accountModelsLoading = it.accountModelsLoading - account.id) } }
                .onFailure { error -> _uiState.update { it.copy(accountModelErrors = it.accountModelErrors + (account.id to error.userMessage()),
                    accountModelsLoading = it.accountModelsLoading - account.id) } }
        }
    }

    fun testAiAccount(account: AiAccount, modelId: String) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        _uiState.update { it.copy(accountsLoading = true, accountTestResults = it.accountTestResults - account.id) }
        runSessionCatching { repository.testAiAccount(account.id, modelId) }
            .onSuccess { result ->
                val latency = result.latencyMs?.let { " · ${it}ms" }.orEmpty()
                _uiState.update {
                    it.copy(accountsLoading = false, accountTestResults = it.accountTestResults + (account.id to result),
                        message = (if (result.success) "连接测试通过" else result.message.ifBlank { "连接测试失败" }) + latency)
                }
            }
            .onFailure { error ->
                Log.e("Sub2API", "AI account connection test failed", error)
                _uiState.update { it.copy(accountsLoading = false, message = error.userMessage(),
                    accountTestResults = it.accountTestResults + (account.id to AiAccountTestResult(message = error.userMessage(), model = modelId))) }
            }
    }

    fun refreshAiCredential(account: AiAccount) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        _uiState.update { it.copy(accountsLoading = true) }
        runSessionCatching { repository.refreshAiAccount(account.id) }
            .onSuccess { result ->
                replaceAiAccount(result.account, result.warning ?: "账号凭据已刷新")
                loadAiAccountQuotas(repository, _uiState.value.allAiAccounts, force = true)
            }
            .onFailure { error -> _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) } }
    }

    fun clearAiAccountError(account: AiAccount) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        runSessionCatching { repository.clearAiAccountError(account.id) }
            .onSuccess { replaceAiAccount(it, "错误状态已清除") }
            .onFailure { showMessage(it.userMessage()) }
    }

    fun toggleAiAccount(account: AiAccount) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        runSessionCatching {
            repository.updateAiAccount(
                account.id,
                UpdateAiAccountRequest(status = if (account.status == "active") "inactive" else "active"),
            )
        }.onSuccess { replaceAiAccount(it) }
            .onFailure { showMessage(it.userMessage()) }
    }

    fun deleteAiAccount(account: AiAccount) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        _uiState.update { it.copy(accountsLoading = true) }
        runSessionCatching { repository.deleteAiAccount(account.id) }
            .onSuccess {
                quotaLoadGeneration++
                _uiState.update { state ->
                    state.copy(
                        aiAccounts = state.aiAccounts.filterNot { it.id == account.id },
                        allAiAccounts = state.allAiAccounts.filterNot { it.id == account.id },
                        aiAccountQuotas = state.aiAccountQuotas - account.id,
                        accountsLoading = false,
                        quotaLoading = false,
                        message = "AI 平台账号已删除",
                    )
                }
                loadAiAccountQuotas(repository, _uiState.value.allAiAccounts)
            }
            .onFailure { error ->
                _uiState.update { it.copy(accountsLoading = false, message = error.userMessage()) }
            }
    }

    private fun replaceAiAccount(account: AiAccount, message: String? = null) {
        accountModelJobs.remove(account.id)?.cancel()
        _uiState.update { state ->
            state.copy(
                aiAccounts = state.aiAccounts.map { if (it.id == account.id) account else it },
                allAiAccounts = state.allAiAccounts.map { if (it.id == account.id) account else it },
                accountsLoading = false,
                message = message ?: state.message,
                accountModels = state.accountModels - account.id,
                accountModelsLoading = state.accountModelsLoading - account.id,
                accountModelErrors = state.accountModelErrors - account.id,
                accountTestResults = state.accountTestResults - account.id,
            )
        }
    }

    fun refreshAiQuotas(platform: String? = null, status: String? = null, search: String? = null) = launchSession {
        if (_uiState.value.user?.role != "admin") return@launchSession
        quotaLoadGeneration++
        _uiState.update { it.copy(quotaLoading = true) }
        val filtered = !platform.isNullOrBlank() || !status.isNullOrBlank() || !search.isNullOrBlank()
        runSessionCatching {
            val visibleAccounts = repository.aiAccounts(platform, status, search)
            val allAccounts = if (filtered) repository.aiAccounts() else visibleAccounts
            visibleAccounts to allAccounts
        }.onSuccess { (visibleAccounts, allAccounts) ->
            val accountIds = allAccounts.mapTo(mutableSetOf()) { it.id }
            _uiState.update { state ->
                state.copy(
                    aiAccounts = visibleAccounts,
                    allAiAccounts = allAccounts,
                    aiAccountQuotas = state.aiAccountQuotas.filterKeys { it in accountIds },
                )
            }
            loadAiAccountQuotas(repository, allAccounts, force = true)
        }.onFailure { error ->
            _uiState.update { it.copy(quotaLoading = false, message = error.userMessage()) }
        }
    }

    private suspend fun loadAiAccountQuotas(repository: ApiRepository, accounts: List<AiAccount>, force: Boolean = false) {
        currentCoroutineContext().ensureActive()
        val generation = ++quotaLoadGeneration
        val accountIds = accounts.mapTo(mutableSetOf()) { it.id }
        if (accounts.isEmpty()) {
            _uiState.update { it.copy(aiAccountQuotas = emptyMap(), quotaLoading = false) }
            TotalQuotaWidget.publishEmpty(app)
            return
        }
        TotalQuotaWidget.publishRefreshing(app)
        _uiState.update { state ->
            state.copy(
                aiAccountQuotas = state.aiAccountQuotas.filterKeys { it in accountIds },
                quotaLoading = true,
            )
        }
        val semaphore = Semaphore(4)
        coroutineScope {
            accounts.map { account ->
                async {
                    val quota = semaphore.withPermit { repository.aiAccountQuota(account, force) }
                    currentCoroutineContext().ensureActive()
                    if (generation == quotaLoadGeneration) {
                        _uiState.update { state ->
                            state.copy(aiAccountQuotas = state.aiAccountQuotas + (account.id to quota))
                        }
                    }
                }
            }.awaitAll()
        }
        if (generation == quotaLoadGeneration) {
            _uiState.update { it.copy(quotaLoading = false) }
            TotalQuotaWidget.publishSummary(
                app,
                summarizeAiQuotas(accounts, _uiState.value.aiAccountQuotas),
                expected = repository.session,
            )
        }
    }

    fun logout() = launchSession {
        _uiState.update { it.copy(loading = true) }
        runSessionCatching { repository.logout() }
            .onFailure { error -> showMessage(error.userMessage()) }
        if (!sessionStore.isActive(observedSession)) restartSession()
    }

    fun changeServer() = showServerManager()

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    private fun showMessage(message: String) = _uiState.update { it.copy(message = message) }
    private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"
}

internal suspend inline fun <T> runSessionCatching(block: () -> T): Result<T> {
    currentCoroutineContext().ensureActive()
    return try {
        val result = block()
        currentCoroutineContext().ensureActive()
        Result.success(result)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        currentCoroutineContext().ensureActive()
        Result.failure(error)
    }
}
