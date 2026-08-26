package com.lemonkids.kidtask.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lemonkids.kidtask.MainActivity
import com.lemonkids.kidtask.R

/**
 * 供桌面启动器展示的最小任务卡片。
 *
 * 目前使用固定的演示内容，目的是先验证荣耀平板对标准 Android App Widget 的识别与添加。
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                updateWidget(context, manager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: android.os.Bundle = appWidgetManager.getAppWidgetOptions(appWidgetId)
        ) {
            appWidgetManager.updateAppWidget(
                appWidgetId,
                buildRemoteViews(
                    context = context,
                    isWide = options.getInt(
                        AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                        DEFAULT_WIDGET_WIDTH_DP
                    ) >= WIDE_WIDGET_MIN_WIDTH_DP
                )
            )
        }

        private fun buildRemoteViews(context: Context, isWide: Boolean): RemoteViews {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val layoutId = if (isWide) {
                R.layout.widget_today_tasks_wide
            } else {
                R.layout.widget_today_tasks_narrow
            }
            return RemoteViews(context.packageName, layoutId).apply {
                setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
            }
        }

        /** 5 格宽左右开始使用双列，兼顾这台荣耀平板的常见桌面网格。 */
        private const val WIDE_WIDGET_MIN_WIDTH_DP = 320
        private const val DEFAULT_WIDGET_WIDTH_DP = 250
    }
}
