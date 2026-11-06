package com.sub2api.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sub2api.mobile.Sub2ApiApplication
import com.sub2api.mobile.MainActivity
import com.sub2api.mobile.R

internal object QuotaWidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QuotaWidgetProvider::class.java))
        if (ids.isNotEmpty()) update(context, manager, ids)
    }

    fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val store = (context.applicationContext as Sub2ApiApplication).sessionStore
        val session = store.state.value
        val saved = QuotaWidgetStateStore(context).read()
        val state = if (saved.profileId == session.profileId && saved.revision == session.revision &&
            saved.selectionVersion == session.selectionVersion
        ) saved.copy(serverName = session.serverName) else QuotaWidgetState(serverName = session.serverName)
        val views = createRemoteViews(context, state)
        widgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    internal fun createRemoteViews(context: Context, state: QuotaWidgetState): RemoteViews {
        val presentation = QuotaWidgetFormatter.present(state)
        return RemoteViews(context.packageName, R.layout.widget_total_quota).apply {
            setTextViewText(R.id.widget_title, state.serverName.takeIf { it.isNotBlank() }?.let { "$it · 总剩余额度" } ?: "总剩余额度")
            setTextViewText(R.id.widget_value, presentation.value)
            setTextViewText(R.id.widget_detail, presentation.detail)
            setProgressBar(R.id.widget_progress, 1000, presentation.progress, false)
            setContentDescription(R.id.widget_root, presentation.contentDescription)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REFRESH,
        Intent(context, QuotaWidgetProvider::class.java).setAction(QuotaWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_OPEN_APP = 7401
    private const val REQUEST_REFRESH = 7402
}
