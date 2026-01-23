package com.my.mg.worker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.my.mg.MGWidget
import com.my.mg.MGWidgetIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 通用型组件更新 Worker
 * * 核心优势：
 * 1. 易扩展：新增组件只需在 widgetUpdaters 列表中加一行代码。
 * 2. 自动化：自动查找每种组件的所有实例（1个或100个都能处理）。
 * 3. 高性能：所有组件并行刷新，互不阻塞。
 */
class WidgetUpdateWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    // 定义一个简单的配置类，用来把 Widget类 和 它的更新方法 绑定在一起
    private data class WidgetConfig(
        val widgetClass: Class<out AppWidgetProvider>,
        val updateAction: suspend (Context, AppWidgetManager, Int) -> Unit
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // =================================================================
        // 1. 【配置区】在这里注册你所有的组件
        //    以后每新增一个布局，只需要在这里加一行！
        // =================================================================
        val widgetConfigs = listOf(
            // 配置大组件
            WidgetConfig(MGWidget::class.java) { ctx, mgr, id ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id)
            },

            // 配置小组件 (如果没有这个类，请先注释掉这一行)
            WidgetConfig(MGWidgetIcon::class.java) { ctx, mgr, id ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id)
            }

            // 将来如果有中型组件，直接加：
            // WidgetConfig(MGWidgetMedium::class.java) { ctx, mgr, id ->
            //     MGWidgetMedium.updateWidgetSynchronously(ctx, mgr, id)
            // }
        )

        Log.d("WidgetUpdateWorker", "Starting update loop for ${widgetConfigs.size} widget types")

        // =================================================================
        // 2. 【逻辑区】全自动循环处理
        //    flatMap 会把所有类型的组件任务合并到一个大列表中
        // =================================================================
        val allUpdateJobs = widgetConfigs.flatMap { config ->
            try {
                // 1. 获取该类型组件的所有实例 ID (比如用户放了 2 个大组件，3 个小组件)
                val componentName = ComponentName(context, config.widgetClass)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (widgetIds.isNotEmpty()) {
                    Log.d("WidgetUpdateWorker", "Found ${widgetIds.size} instances of ${config.widgetClass.simpleName}")

                    // 2. 为每个实例 ID 启动一个并发协程
                    widgetIds.map { widgetId ->
                        async {
                            try {
                                // 执行你在配置区定义的 updateAction
                                config.updateAction(context, appWidgetManager, widgetId)
                            } catch (e: Exception) {
                                Log.e("WidgetUpdateWorker", "Error updating ${config.widgetClass.simpleName} (ID: $widgetId)", e)
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // 防止某种组件类找不到导致整个 Worker 崩溃
                Log.e("WidgetUpdateWorker", "Failed to process widget type: ${config.widgetClass.simpleName}", e)
                emptyList()
            }
        }

        // =================================================================
        // 3. 【等待区】等待所有并发任务完成
        // =================================================================
        if (allUpdateJobs.isNotEmpty()) {
            allUpdateJobs.awaitAll()
        }

        return@withContext Result.success()
    }
}