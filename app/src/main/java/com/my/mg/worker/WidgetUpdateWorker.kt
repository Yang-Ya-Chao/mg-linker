package com.my.mg.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.my.mg.MGWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MGWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        withContext(Dispatchers.Main) {
            for (id in appWidgetIds) {
                MGWidget.updateAppWidget(context, appWidgetManager, id)
            }
        }

        return Result.success()
    }
}
