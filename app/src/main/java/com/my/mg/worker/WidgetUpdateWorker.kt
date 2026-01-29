package com.my.mg.worker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.my.mg.MGWidget
import com.my.mg.MGWidgetIcon
import com.my.mg.MGWidgetSmall
import com.my.mg.MGWidgetStyle1
import com.my.mg.MGWidgetStyle2
import com.my.mg.VehicleStatusResponse
import com.my.mg.net.AddressWorker.getAddressSync
import com.my.mg.net.VehicleDataWorker.fetchVehicleDataSuspended
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

fun startUpdateWorker(context: Context) {
    val request = OneTimeWorkRequest.Builder(WidgetUpdateWorker::class.java).build()
    WorkManager.getInstance(context).enqueue(request)
}

class WidgetUpdateWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    // 定义一个简单的配置类，用来把 Widget类 和 它的更新方法 绑定在一起
    // 定义配置类，现在 updateAction 接收数据对象了
    private data class WidgetConfig(
        val widgetClass: Class<out AppWidgetProvider>,
        // 核心变化：Lambda 接收 fetchedData 和 address
        val updateAction: suspend (Context, AppWidgetManager, Int, VehicleStatusResponse?, String?) -> Unit
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences("mg_config", Context.MODE_PRIVATE)
        // =================================================================
        // 1. 定义支持的组件类型
        // =================================================================
        val widgetConfigs = listOf(
            WidgetConfig(MGWidget::class.java) { ctx, mgr, id, data, addr ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            },
            WidgetConfig(MGWidgetIcon::class.java) { ctx, mgr, id, data, addr ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            },
            WidgetConfig(MGWidgetSmall::class.java) { ctx, mgr, id, data, addr ->
                MGWidgetSmall.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            },
            WidgetConfig(MGWidgetStyle1::class.java) { ctx, mgr, id, data, addr ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            },
            WidgetConfig(MGWidgetStyle2::class.java) { ctx, mgr, id, data, addr ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            }
            // 将来如果有中型组件，直接加：
            // WidgetConfig(MGWidgetMedium::class.java) { ctx, mgr, id, data, addr ->
            //     MGWidgetMedium.updateWidgetSynchronously(ctx, mgr, id, data, addr)
            // }
        )
        // =================================================================
        // 2. 【关键优化】预先筛选：只保留桌面上实际存在的组件
        //    如果没有组件，activeWidgets 列表将为空
        // =================================================================
        val activeWidgets = widgetConfigs.mapNotNull { config ->
            try {
                val componentName = ComponentName(context, config.widgetClass)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    // 返回 "配置" 和 "该类型下的所有ID"
                    config to ids
                } else {
                    null // 过滤掉没有添加在桌面的组件类型
                }
            } catch (e: Exception) {
                Log.e(
                    "WidgetUpdateWorker",
                    "Check widget exist error: ${config.widgetClass.simpleName}",
                    e
                )
                null
            }
        }
        // =================================================================
        // 3. 【早退机制】如果桌面上一个组件都没有，直接结束，不请求网络！
        // =================================================================
        if (activeWidgets.isEmpty()) {
            Log.d(
                "WidgetUpdateWorker",
                "No active widgets found on home screen. Skipping network request."
            )
            return@withContext Result.success()
        }

        Log.d("WidgetUpdateWorker", "Found active widgets, starting data fetch...")

        // =================================================================
        // 4. 【生产者】执行唯一的网络请求 (因为前面通过了检查，说明肯定有组件需要数据)
        // =================================================================
        val vin = prefs.getString("vin", "") ?: ""
        val token = prefs.getString("access_token", "") ?: ""

        // 4.1 请求车辆数据
        val vehicleData: VehicleStatusResponse? = if (vin.isNotEmpty() && token.isNotEmpty()) {
            fetchVehicleDataSuspended(vin, token)
        } else {
            null
        }
        // 4.2 获取地理位置 (如果数据获取成功)
        var address: String? = null
        if (vehicleData?.data?.vehicle_position != null) {
            val pos = vehicleData.data.vehicle_position
            val lat = pos.latitude?.toDoubleOrNull()
            val lng = pos.longitude?.toDoubleOrNull()
            if (lat != null && lng != null) {
                // 复用 MGWidget 中的地址解析逻辑 (需将 getAddressSync 设为 internal/public companion)
                address = getAddressSync(context, lat, lng)
            }
        }
        Log.d(
            "WidgetUpdateWorker",
            "Data fetch complete. Success: ${vehicleData != null}, Address: $address"
        )




        Log.d("WidgetUpdateWorker", "Starting update loop for ${activeWidgets.size} widget types")

        // =================================================================
        // 5. 【消费者】并发刷新 (只刷新 activeWidgets 中记录的 ID)
        // =================================================================
        val updateJobs = activeWidgets.flatMap { (config, ids) ->
            ids.map { widgetId ->
                async {
                    try {
                        config.updateAction(
                            context,
                            appWidgetManager,
                            widgetId,
                            vehicleData,
                            address
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "WidgetUpdateWorker",
                            "Update error for ${config.widgetClass.simpleName} ID: $widgetId",
                            e
                        )
                    }
                }
            }
        }

        // =================================================================
        // 6. 【等待区】等待所有并发任务完成
        // =================================================================
        if (updateJobs.isNotEmpty()) {
            updateJobs.awaitAll()
        }

        return@withContext Result.success()
    }
}