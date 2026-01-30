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
import com.my.mg.BuildConfig
import com.my.mg.MGWidget
import com.my.mg.MGWidgetIcon
import com.my.mg.MGWidgetSmall
import com.my.mg.MGWidgetStyle1
import com.my.mg.MGWidgetStyle2
import com.my.mg.VehicleStatusResponse
import com.my.mg.log.LogcatHelper
import com.my.mg.net.AddressWorker.getAddressSync
import com.my.mg.net.VehicleDataWorker.fetchVehicleDataSuspended
import com.my.mg.data.WidgetContextData
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
    // [修改] UpdateAction 现在接收 WidgetContextData，包含所有所需数据
    private data class WidgetConfig(
        val widgetClass: Class<out AppWidgetProvider>,
        val updateAction: suspend (Context, AppWidgetManager, Int, WidgetContextData) -> Unit
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        // 1. [优化] 在 Worker 开始时统一读取 SharedPreferences
        // 避免在每个 Widget 更新时重复 IO 操作
        val prefs = context.getSharedPreferences("mg_config", Context.MODE_PRIVATE)
        val vin = prefs.getString("vin", "") ?: ""
        val token = prefs.getString("access_token", "") ?: ""

        // 准备静态配置数据
        val baseData = WidgetContextData(
            carName = prefs.getString("car_name", "") ?: "",
            carBrand = prefs.getString("car_brand", "名爵") ?: "名爵",
            carModel = prefs.getString("car_model", "") ?: "",
            plateNumber = prefs.getString("plate_number", "") ?: "",
            carImageUrl = prefs.getString("car_image_url", "") ?: "",
            // 默认值为 0.0，防止计算奔溃
            fuelCapacity = prefs.getString("car_fuel_capacity", "0.0")?.toDoubleOrNull() ?: 0.0,
            batteryCapacity = prefs.getString("car_battery_capacity", "0.0")?.toDoubleOrNull() ?: 0.0,
            vehicleData = null,
            address = null
        )
        // =================================================================
        // 1. 定义支持的组件类型
        // =================================================================
        val widgetConfigs = listOf(
            WidgetConfig(MGWidget::class.java) { ctx, mgr, id, data ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data)
            },
            WidgetConfig(MGWidgetIcon::class.java) { ctx, mgr, id, data ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data)
            },
            WidgetConfig(MGWidgetSmall::class.java) { ctx, mgr, id, data ->
                MGWidgetSmall.updateWidgetSynchronously(ctx, mgr, id, data)
            },
            WidgetConfig(MGWidgetStyle1::class.java) { ctx, mgr, id, data ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data)
            },
            WidgetConfig(MGWidgetStyle2::class.java) { ctx, mgr, id, data ->
                MGWidget.updateWidgetSynchronously(ctx, mgr, id, data)
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

        // 4. 执行网络请求 (仅当有活跃组件时)
        val vehicleData: VehicleStatusResponse? = if (vin.isNotEmpty() && token.isNotEmpty()) {
            fetchVehicleDataSuspended(vin, token)
        } else {
            null
        }
        //计算油耗
        processEnergyRecord(context, vehicleData, baseData)

        var address: String? = null
        if (vehicleData?.data?.vehicle_position != null) {
            val pos = vehicleData.data.vehicle_position
            val lat = pos.latitude?.toDoubleOrNull()
            val lng = pos.longitude?.toDoubleOrNull()
            if (lat != null && lng != null) {
                address = getAddressSync(context, lat, lng)
            }
        }
        Log.d(
            "WidgetUpdateWorker",
            "Data fetch complete. Success: ${vehicleData != null}, Address: $address"
        )

        // 5. [关键] 组装最终的完整数据对象
        val finalContextData = baseData.copy(
            vehicleData = vehicleData,
            address = address
        )


        Log.d("WidgetUpdateWorker", "Starting update loop for ${activeWidgets.size} widget types")

        // 6. 并发更新所有 Widget
        val updateJobs = activeWidgets.flatMap { (config, ids) ->
            ids.map { widgetId ->
                async {
                    try {
                        // 将准备好的数据传下去
                        config.updateAction(context, appWidgetManager, widgetId, finalContextData)
                    } catch (e: Exception) {
                        e.printStackTrace()
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