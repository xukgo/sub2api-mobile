package com.sub2api.mobile.widget

import android.content.Context
import com.sub2api.mobile.Sub2ApiApplication
import com.sub2api.mobile.data.SessionState
import com.sub2api.mobile.data.AiQuotaSummary
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

internal enum class QuotaWidgetStatus {
    INITIAL,
    REFRESHING,
    READY,
    EMPTY,
    SERVER_REQUIRED,
    LOGIN_REQUIRED,
    ADMIN_REQUIRED,
    ERROR,
}

internal data class QuotaWidgetState(
    val status: QuotaWidgetStatus = QuotaWidgetStatus.INITIAL,
    val totalRemainingPercent: Double = 0.0,
    val totalCapacityPercent: Double = 0.0,
    val measuredAccounts: Int = 0,
    val totalAccounts: Int = 0,
    val updatedAtMillis: Long = 0L,
    val message: String = "",
    val serverName: String = "",
    val profileId: String = "",
    val selectionVersion: Long = 0,
    val revision: Long = 0,
) {
    val hasNumericSnapshot: Boolean
        get() = updatedAtMillis > 0L && measuredAccounts > 0

    val capacityRemainingPercent: Double
        get() = if (totalCapacityPercent > 0.0) {
            (totalRemainingPercent / totalCapacityPercent * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
}

internal data class QuotaWidgetPresentation(
    val value: String,
    val detail: String,
    val progress: Int,
    val contentDescription: String,
)

internal object QuotaWidgetFormatter {
    fun present(
        state: QuotaWidgetState,
        timeLabel: String = formatTime(state.updatedAtMillis),
    ): QuotaWidgetPresentation {
        val value = if (state.hasNumericSnapshot) formatPercent(state.totalRemainingPercent) else "--"
        val detail = when (state.status) {
            QuotaWidgetStatus.INITIAL -> "等待首次刷新"
            QuotaWidgetStatus.SERVER_REQUIRED -> "请先连接服务器"
            QuotaWidgetStatus.LOGIN_REQUIRED -> "请先登录"
            QuotaWidgetStatus.ADMIN_REQUIRED -> "仅管理员可查看"
            QuotaWidgetStatus.EMPTY -> "暂无 AI 账号"
            QuotaWidgetStatus.REFRESHING -> if (state.hasNumericSnapshot) {
                "正在刷新 · 上次 $timeLabel · ${state.measuredAccounts}/${state.totalAccounts}"
            } else {
                "正在刷新全部账号"
            }
            QuotaWidgetStatus.ERROR -> if (state.hasNumericSnapshot) {
                "刷新失败 · 上次 $timeLabel · ${state.measuredAccounts}/${state.totalAccounts}"
            } else {
                "刷新失败，点按重试"
            }
            QuotaWidgetStatus.READY -> readyDetail(state, timeLabel)
        }
        val progress = if (state.hasNumericSnapshot) {
            (state.capacityRemainingPercent * 10.0).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        return QuotaWidgetPresentation(
            value = value,
            detail = detail,
            progress = progress,
            contentDescription = "总剩余额度 $value，$detail",
        )
    }

    private fun readyDetail(state: QuotaWidgetState, timeLabel: String): String = when {
        state.totalAccounts == 0 -> "暂无 AI 账号"
        state.measuredAccounts == 0 -> "未读取到额度 · 0/${state.totalAccounts} · $timeLabel"
        state.measuredAccounts < state.totalAccounts -> {
            "已合计 ${state.measuredAccounts}/${state.totalAccounts} · 满额 ${formatPercent(state.totalCapacityPercent)} · $timeLabel"
        }
        else -> "满额 ${formatPercent(state.totalCapacityPercent)} · ${state.measuredAccounts}/${state.totalAccounts} · $timeLabel"
    }

    private fun formatPercent(value: Double): String =
        "${DecimalFormat("0.#").format(value.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0)}%"

    private fun formatTime(timestamp: Long): String =
        timestamp.takeIf { it > 0L }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }.orEmpty()
}

internal class QuotaWidgetStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): QuotaWidgetState = QuotaWidgetState(
        status = runCatching {
            QuotaWidgetStatus.valueOf(preferences.getString(KEY_STATUS, null).orEmpty())
        }.getOrDefault(QuotaWidgetStatus.INITIAL),
        totalRemainingPercent = Double.fromBits(preferences.getLong(KEY_TOTAL_REMAINING, 0L)),
        totalCapacityPercent = Double.fromBits(preferences.getLong(KEY_TOTAL_CAPACITY, 0L)),
        measuredAccounts = preferences.getInt(KEY_MEASURED_ACCOUNTS, 0),
        totalAccounts = preferences.getInt(KEY_TOTAL_ACCOUNTS, 0),
        updatedAtMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
        message = preferences.getString(KEY_MESSAGE, "").orEmpty(),
        serverName = preferences.getString("server_name", "").orEmpty(),
        profileId = preferences.getString("profile_id", "").orEmpty(),
        selectionVersion = preferences.getLong("selection_version", 0),
        revision = preferences.getLong("session_revision", 0),
    )

    fun save(state: QuotaWidgetState) {
        preferences.edit()
            .putString(KEY_STATUS, state.status.name)
            .putLong(KEY_TOTAL_REMAINING, state.totalRemainingPercent.toBits())
            .putLong(KEY_TOTAL_CAPACITY, state.totalCapacityPercent.toBits())
            .putInt(KEY_MEASURED_ACCOUNTS, state.measuredAccounts)
            .putInt(KEY_TOTAL_ACCOUNTS, state.totalAccounts)
            .putLong(KEY_UPDATED_AT, state.updatedAtMillis)
            .putString(KEY_MESSAGE, state.message)
            .putString("server_name", state.serverName)
            .putString("profile_id", state.profileId)
            .putLong("selection_version", state.selectionVersion)
            .putLong("session_revision", state.revision)
            .apply()
    }

    companion object {
        private const val PREFERENCES = "total_quota_widget"
        private const val KEY_STATUS = "status"
        private const val KEY_TOTAL_REMAINING = "total_remaining"
        private const val KEY_TOTAL_CAPACITY = "total_capacity"
        private const val KEY_MEASURED_ACCOUNTS = "measured_accounts"
        private const val KEY_TOTAL_ACCOUNTS = "total_accounts"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_MESSAGE = "message"
    }
}

object TotalQuotaWidget {
    fun publishSummary(context: Context, summary: AiQuotaSummary, expected: SessionState = currentSession(context)) {
        val state = QuotaWidgetState(
            status = if (summary.totalAccounts == 0) QuotaWidgetStatus.EMPTY else QuotaWidgetStatus.READY,
            totalRemainingPercent = summary.totalRemainingPercent.sanitized(),
            totalCapacityPercent = summary.totalCapacityPercent.sanitized(),
            measuredAccounts = summary.measuredAccounts.coerceAtLeast(0),
            totalAccounts = summary.totalAccounts.coerceAtLeast(0),
            updatedAtMillis = System.currentTimeMillis(),
        )
        saveAndRender(context, state, expected)
    }

    fun publishRefreshing(context: Context, expected: SessionState = currentSession(context)) {
        val saved = QuotaWidgetStateStore(context).read()
        val current = if (saved.profileId == expected.profileId && saved.revision == expected.revision &&
            saved.selectionVersion == expected.selectionVersion
        ) saved else QuotaWidgetState()
        saveAndRender(context, current.copy(status = QuotaWidgetStatus.REFRESHING, message = ""), expected)
    }

    fun publishError(context: Context, message: String? = null, expected: SessionState = currentSession(context)) {
        val saved = QuotaWidgetStateStore(context).read()
        val current = if (saved.profileId == expected.profileId && saved.revision == expected.revision &&
            saved.selectionVersion == expected.selectionVersion
        ) saved else QuotaWidgetState()
        saveAndRender(
            context,
            current.copy(
                status = QuotaWidgetStatus.ERROR,
                message = message?.lineSequence()?.firstOrNull().orEmpty(),
            ),
            expected,
        )
    }

    fun publishServerRequired(context: Context, expected: SessionState = currentSession(context)) = publishCleared(context, QuotaWidgetStatus.SERVER_REQUIRED, expected)

    fun publishLoginRequired(context: Context, expected: SessionState = currentSession(context)) = publishCleared(context, QuotaWidgetStatus.LOGIN_REQUIRED, expected)

    fun publishAdminRequired(context: Context, expected: SessionState = currentSession(context)) = publishCleared(context, QuotaWidgetStatus.ADMIN_REQUIRED, expected)

    fun publishEmpty(context: Context, expected: SessionState = currentSession(context)) = publishCleared(context, QuotaWidgetStatus.EMPTY, expected)

    private fun publishCleared(context: Context, status: QuotaWidgetStatus, expected: SessionState) {
        saveAndRender(context, QuotaWidgetState(status = status), expected)
    }

    private fun currentSession(context: Context) = (context.applicationContext as Sub2ApiApplication).sessionStore.state.value

    private fun saveAndRender(context: Context, state: QuotaWidgetState, expected: SessionState) {
        val store = (context.applicationContext as Sub2ApiApplication).sessionStore
        store.whileActive(expected) {
            QuotaWidgetStateStore(context).save(state.copy(
                serverName = store.state.value.serverName, profileId = expected.profileId,
                selectionVersion = expected.selectionVersion, revision = expected.revision,
            ))
            QuotaWidgetRenderer.updateAll(context)
        }
    }

    private fun Double.sanitized(): Double = takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
}
