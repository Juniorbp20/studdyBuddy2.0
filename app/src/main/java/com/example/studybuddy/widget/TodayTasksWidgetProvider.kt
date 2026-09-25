package com.example.studybuddy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.studybuddy.MainActivity
import com.example.studybuddy.R

class TodayTasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TodayTasksWidgetProvider::class.java)
        )
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tasks)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openAppPending)

            val serviceIntent = Intent(context, TodayTasksWidgetService::class.java)
            views.setRemoteAdapter(
                R.id.widget_list,
                Intent(context, TodayTasksWidgetService::class.java)
            )
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val itemClickTemplate = Intent(context, MainActivity::class.java)
            val itemClickPending = PendingIntent.getActivity(
                context,
                0,
                itemClickTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, itemClickPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}