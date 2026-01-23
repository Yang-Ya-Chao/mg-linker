package com.my.mg.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.my.mg.MGWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 功能：后台更新小组件 Worker
 * 修复：解决了原版 Worker 提前返回导致协程被杀、网络请求中断的问题。
 * 机制：现在 Worker 会挂起并等待 updateOneWidget 完成后再返回。
 */
class WidgetUpdateWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MGWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        // 并发更新所有小组件，提高效率
        val jobs = appWidgetIds.map { id ->
            async {
                try {
                    // 调用 MGWidget 中的挂起函数，等待执行完成
                    MGWidget.updateWidgetSynchronously(context, appWidgetManager, id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 等待所有更新任务完成
        jobs.awaitAll()

        return@withContext Result.success()
    }
}