package com.sub2api.mobile.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sub2api.mobile.Sub2ApiApplication
import com.sub2api.mobile.data.ApiException
import com.sub2api.mobile.data.summarizeAiQuotas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

class QuotaWidgetWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = refreshMutex.withLock {
        val app = applicationContext as Sub2ApiApplication
        val initialSession = app.sessionStore.state.value
        val repository = app.repository(initialSession)
        if (!initialSession.isConfigured) {
            TotalQuotaWidget.publishServerRequired(app, expected = initialSession)
            return@withLock Result.success()
        }
        if (!initialSession.isAuthenticated) {
            TotalQuotaWidget.publishLoginRequired(app, expected = initialSession)
            return@withLock Result.success()
        }

        TotalQuotaWidget.publishRefreshing(app, expected = initialSession)
        try {
            val user = repository.currentUser()
            if (!app.sessionStore.isActive(initialSession)) return@withLock Result.success()
            if (user.role != "admin") {
                TotalQuotaWidget.publishAdminRequired(app, expected = initialSession)
                return@withLock Result.success()
            }

            val accounts = repository.aiAccounts()
            if (!app.sessionStore.isActive(initialSession)) return@withLock Result.success()
            if (accounts.isEmpty()) {
                TotalQuotaWidget.publishEmpty(app, expected = initialSession)
                return@withLock Result.success()
            }

            val force = inputData.getBoolean(KEY_FORCE, false)
            val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
            val quotas = coroutineScope {
                accounts.map { account ->
                    async {
                        account.id to semaphore.withPermit {
                            repository.aiAccountQuota(account, force)
                        }
                    }
                }.awaitAll().toMap()
            }

            if (!app.sessionStore.isActive(initialSession)) return@withLock Result.success()
            val latestSession = app.sessionStore.state.value
            when {
                !latestSession.isConfigured -> TotalQuotaWidget.publishServerRequired(app, expected = initialSession)
                !latestSession.isAuthenticated || latestSession.serverUrl != initialSession.serverUrl -> {
                    TotalQuotaWidget.publishLoginRequired(app, expected = initialSession)
                }
                else -> TotalQuotaWidget.publishSummary(app, summarizeAiQuotas(accounts, quotas), initialSession)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val current = app.sessionStore.state.value
            if (!app.sessionStore.isActive(initialSession)) {
                if (current.profileId == initialSession.profileId && !current.isAuthenticated &&
                    current.revision == initialSession.revision + 1 && current.selectionVersion == initialSession.selectionVersion + 1
                ) TotalQuotaWidget.publishLoginRequired(app, current)
                return@withLock Result.success()
            }
            when {
                !current.isConfigured -> TotalQuotaWidget.publishServerRequired(app, expected = initialSession)
                !current.isAuthenticated || error is ApiException && error.status == 401 -> {
                    app.sessionStore.whileActive(initialSession) {
                        app.sessionStore.clearTokens(initialSession)
                        TotalQuotaWidget.publishLoginRequired(app)
                    }
                }
                error is ApiException && error.status == 403 -> TotalQuotaWidget.publishAdminRequired(app, expected = initialSession)
                else -> TotalQuotaWidget.publishError(app, error.message, initialSession)
            }
            val retryable = current.isAuthenticated && current.serverUrl == initialSession.serverUrl &&
                !(error is ApiException && error.status in setOf(401, 403))
            if (runAttemptCount < MAX_RETRIES && retryable) Result.retry() else Result.failure()
        }
    }

    companion object {
        internal const val KEY_FORCE = "force"
        private const val MAX_PARALLEL_REQUESTS = 4
        private const val MAX_RETRIES = 2
        private val refreshMutex = Mutex()
    }
}

internal object QuotaWidgetWork {
    private const val PERIODIC_WORK = "total_quota_widget_periodic"
    private const val REFRESH_WORK = "total_quota_widget_refresh"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<QuotaWidgetWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueRefresh(context: Context, force: Boolean) {
        val input: Data = workDataOf(QuotaWidgetWorker.KEY_FORCE to force)
        val request = OneTimeWorkRequestBuilder<QuotaWidgetWorker>()
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }
}
