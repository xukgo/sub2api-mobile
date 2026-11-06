package com.sub2api.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class QuotaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        QuotaWidgetRenderer.update(context, manager, widgetIds)
        QuotaWidgetWork.schedulePeriodic(context)
        QuotaWidgetWork.enqueueRefresh(context, force = false)
    }

    override fun onEnabled(context: Context) {
        QuotaWidgetWork.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        QuotaWidgetWork.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            TotalQuotaWidget.publishRefreshing(context)
            QuotaWidgetWork.enqueueRefresh(context, force = true)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.sub2api.mobile.widget.REFRESH_TOTAL_QUOTA"
    }
}
