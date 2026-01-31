package com.my.mg

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.my.mg.net.ImageWorker.loadCarImageSuspended
import com.my.mg.data.WidgetContextData
import com.my.mg.worker.startUpdateWorker

open class MGWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 同样使用 Worker 来触发更新
        startUpdateWorker(context)
    }

    companion object {
        /**
         * 供 Worker 调用的同步更新方法 (针对 Small 组件)
         */
        suspend fun updateWidgetSynchronously(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            ctxData: WidgetContextData // 使用封装的数据对象
        ) {
            val views = RemoteViews(context.packageName, R.layout.mg_widget_small)

            // 这里复用 MGWidget 的数据获取逻辑
            // 注意：你需要将 MGWidget 中的 fetchVehicleDataSuspended 改为 internal 或 public 才能调用
            // 或者将这些逻辑抽取到单独的 Helper 类中。
            // 假设我们现在直接复用 MGWidget 的逻辑：



            // 1. 基础 UI
            views.setTextViewText(R.id.tv_car_name, ctxData.carName)

            try {
                loadCarImageSuspended(context, views, ctxData.carImageUrl)
            } catch (e: Exception) {
                Log.e("MGWidget", "Image load failed for widgetSmall $appWidgetId: ${e.message}")
                // 可选：加载失败时显示默认图或隐藏
                // views.setImageViewResource(R.id.iv_car, R.drawable.default_car)
            }
            // 2. 使用传入的数据
            if (ctxData.vehicleData != null) {
                val vehicleValue = ctxData.vehicleData?.data?.vehicle_value
                // 3. 油量、电量、续航
                val fuelLevel = vehicleValue?.fuel_level_prc ?: 0
                val fuelRange = vehicleValue?.fuel_range ?: 0
                val batteryPackRange = vehicleValue?.battery_pack_range ?: 0
                val batteryPackPrc = (vehicleValue?.battery_pack_prc ?: 0) / 10

                val showFuel = fuelRange > 0
                val showBattery = batteryPackRange > 0
                val range = fuelRange + batteryPackRange
                views.setViewVisibility(
                    R.id.pb_fuel_info,
                    if (showFuel) View.VISIBLE else View.GONE
                )
                views.setViewVisibility(
                    R.id.pb_battery_info,
                    if (showBattery) View.VISIBLE else View.GONE
                )
                views.setTextViewText(R.id.tv_range, "$range")
                // 3.1 油量
                if (showFuel) {
                    views.setProgressBar(R.id.pb_fuel, 100, fuelLevel, false)
                    val fuelColor = when {
                        fuelLevel < 20 -> R.color.status_red
                        showBattery -> R.color.status_blue
                        else -> R.color.status_green
                    }
                    views.setTextViewText(R.id.tv_fuel, "$fuelRange km / $fuelLevel %")
                    views.setColorStateList(
                        R.id.pb_fuel,
                        "setProgressTintList",
                        ColorStateList.valueOf(context.getColor(fuelColor))
                    )
                }

                // 3.2 电量
                if (showBattery) {
                    views.setProgressBar(R.id.pb_battery, 100, batteryPackPrc, false)
                    views.setTextViewText(
                        R.id.tv_battery,
                        "$batteryPackRange km / $batteryPackPrc %"
                    )
                    val batteryColor =
                        if (batteryPackPrc < 20) R.color.status_red else R.color.status_green
                    views.setColorStateList(
                        R.id.pb_battery,
                        "setProgressTintList",
                        ColorStateList.valueOf(context.getColor(batteryColor))
                    )
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}