package com.my.mg

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.my.mg.data.WidgetContextData
import com.my.mg.net.ImageWorker.loadCarImageSuspended
import com.my.mg.worker.startUpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Power by 杨家三郎 & Optimized by Android Expert
 * MGWidget - 车辆状态桌面小组件
 *
 * 核心优化：
 * 1. 采用 WorkManager + Suspend Function 机制，确保后台刷新任务不被系统强杀。
 * 2. 增加图片采样加载 (Sampled Bitmap)，彻底解决 RemoteViews 内存溢出 (OOM) 问题。
 * 3. 优化 Geocoder 和网络请求流程，提升稳定性。
 */

class MGWidgetIcon : MGWidget() //图标版
class MGWidgetStyle1 : MGWidget()//风格1
class MGWidgetStyle2 : MGWidget() //风格2
open class MGWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        log("onUpdate called for IDs: ${appWidgetIds.joinToString()}")

        // 使用 WorkManager 触发更新，保证任务能够执行完成
        startUpdateWorker(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        log("onAppWidgetOptionsChanged for ID: $appWidgetId")

        // 尺寸变化时触发刷新以适配字体
        startUpdateWorker(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        log("onReceive called with action: ${intent.action}")

        val appWidgetManager = AppWidgetManager.getInstance(context)

        if (ACTION_WIDGET_FLIP == intent.action) {
            // 处理翻页逻辑
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 【优化点1】动态获取布局 ID，而不是写死 R.layout.mg_widget
                val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (providerInfo != null) {
                    val views = RemoteViews(context.packageName, providerInfo.initialLayout)
                    // 尝试执行翻页 (try-catch 防止某些布局没有 ViewFlipper)
                    try {
                        views.showNext(R.id.view_flipper_center)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else if (ACTION_REFRESH == intent.action) {
            val componentName = ComponentName(context, this.javaClass)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (id in appWidgetIds) {
                val providerInfo = appWidgetManager.getAppWidgetInfo(id) ?: continue
                val views = RemoteViews(context.packageName, providerInfo.initialLayout)
                // 设置反馈文字 (RemoteViews 会自动忽略不存在的 ID，所以很安全)
                views.setTextViewText(R.id.tv_update_time, "正在更新...")
                appWidgetManager.updateAppWidget(id, views)
            }
            // 启动 Worker 进行真正的网络请求
            startUpdateWorker(context)
        }
    }

    /**
     * 启动一次性后台任务来更新小组件
     */


    companion object {
        private const val LOG_TAG = "MGWidget"
        private const val ACTION_REFRESH = "com.my.mg.ACTION_REFRESH"
        private const val ACTION_WIDGET_FLIP = "ACTION_WIDGET_FLIP"

        private fun log(message: String) {
            Log.d(LOG_TAG, message)
        }

        // ========================================================================
        // 核心逻辑：供 Worker 调用的同步挂起函数
        // ========================================================================

        /**
         * 供 Worker 调用的同步更新方法。
         * 它会挂起直到网络请求完成，确保 Worker 不会提前结束。
         */
        suspend fun updateWidgetSynchronously(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            ctxData: WidgetContextData // 使用封装的数据对象
        ) {
            log("updateWidgetSynchronously called for ID: $appWidgetId")
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (providerInfo == null) {
                log("ProviderInfo is null for ID: $appWidgetId, skip.")
                return
            }

            val layoutId = providerInfo.initialLayout
            val views = RemoteViews(context.packageName, layoutId)

            // 1. 设置更新状态
            //views.setTextViewText(R.id.tv_update_time,"正在更新...")

            // 2. 设置静态 UI (车名、车牌、Logo)
            views.setTextViewText(
                R.id.tv_car_name,
                if (ctxData.carName.isNullOrEmpty()) ctxData.carModel else ctxData.carName
            )
            if ((layoutId != R.layout.mg_widget_style1) && (layoutId != R.layout.mg_widget_style2)) {
                views.setTextViewText(R.id.tv_plate_number, ctxData.plateNumber)
                val logoResId =
                    if (ctxData.carBrand == "荣威") R.drawable.rw_logo else R.drawable.mg_logo
                views.setImageViewResource(R.id.iv_brand_logo, logoResId)


                // 3. 动态调整字体大小
                adjustFontSizes(appWidgetManager, appWidgetId, views, layoutId)

                // 4. 设置点击事件 (打开APP、刷新、翻页)
                setupClickEvents(context, views, appWidgetId, layoutId)

                // 5. 加载车辆图片 (挂起操作，含缓存和采样)
                try {
                    loadCarImageSuspended(context, views, ctxData.carImageUrl)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Image load failed for widget $appWidgetId: ${e.message}")
                    // 可选：加载失败时显示默认图或隐藏
                    // views.setImageViewResource(R.id.iv_car, R.drawable.default_car)
                }
            }
            // 5. 使用传入的数据更新 UI
            if (ctxData.vehicleData != null) {
                // 直接使用 Worker 传过来的地址
                updateWidgetUI(context, views, ctxData, layoutId)
            } else {
                // 如果数据为空（比如无网络），显示提示或保持旧数据
                // views.setTextViewText(R.id.tv_update_time, "等待更新...")
                // 建议：可以在这里判断网络状态，如果也没网，显示“网络异常”
            }

            // 7. 提交更新到系统
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        // ========================================================================
        // UI 更新逻辑 (完整保留)
        // ========================================================================

        private fun updateWidgetUI(
            context: Context,
            views: RemoteViews,
            ctxData: WidgetContextData,
            layoutId: Int
        ) {
            val data = ctxData.vehicleData ?: return
            val vehicleValue = data.data?.vehicle_value
            val vehicleState = data.data?.vehicle_state
            val updateTime = data.data?.update_time ?: System.currentTimeMillis()

            // 1. 更新时间显示
            val updateDate = Date(updateTime)
            val now = Date()
            val sdfSameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val isSameDay = sdfSameDay.format(updateDate) == sdfSameDay.format(now)
            val displaySdf = if (isSameDay) SimpleDateFormat("HH:mm", Locale.getDefault())
            else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            if (layoutId == R.layout.mg_widget) {
                views.setTextViewText(R.id.tv_update_time, "${displaySdf.format(updateDate)} 更新")
            } else if (layoutId == R.layout.mg_widget_icon) {
                views.setTextViewText(
                    R.id.tv_update_time,
                    "更新于${displaySdf.format(updateDate)}"
                )

            }


            // 3. 油量、电量、续航
            val fuelLevel = vehicleValue?.fuel_level_prc ?: 0
            val fuelRange = vehicleValue?.fuel_range ?: 0
            val batteryPackRange = vehicleValue?.battery_pack_range ?: 0
            val batteryPackPrc = (vehicleValue?.battery_pack_prc ?: 0) / 10
            val chrgngRmnngTime = vehicleValue?.chrgng_rmnng_time ?: 0

            // 2. 总里程
            val mileage = vehicleValue?.odometer ?: 0
            // 3. 油耗+能耗
            // 调用计算器
//            var result = EnergyCalculator.calculate(
//                fuelRange = fuelRange.toDouble(),
//                fuelCapacity = ctxData.fuelCapacity,
//                fuelLevel = fuelLevel.toDouble(),
//                batteryRange = batteryPackRange.toDouble(),
//                batteryCapacity = ctxData.batteryCapacity,
//                batteryPackPrc = batteryPackPrc.toDouble()
//            ).displayText
            var result = data.data?.calculator
            if (layoutId == R.layout.mg_widget) {
                if (result != "") result = " / " + result
                views.setTextViewText(R.id.tv_total_mileage, "总里程: ${mileage}km${result}")
            } else if (layoutId == R.layout.mg_widget_icon) {
                views.setTextViewText(R.id.tv_total_mileage, "${mileage}km")
                views.setViewVisibility(
                    R.id.tv_capacity,
                    if (result == "") View.GONE else View.VISIBLE
                )
                views.setViewVisibility(
                    R.id.capacity,
                    if (result == "") View.GONE else View.VISIBLE
                )
                views.setTextViewText(R.id.capacity, "${result}")


            }


            val chargeStatus = vehicleValue?.charge_status ?: 0
            val showFuel = fuelRange > 0
            val showBattery = batteryPackRange > 0
            val showChargng = (chargeStatus != 1009) && (chargeStatus > 0)

            views.setViewVisibility(R.id.ll_range_fuel, if (showFuel) View.VISIBLE else View.GONE)
            views.setViewVisibility(
                R.id.ll_battery_range,
                if (showBattery) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.ll_chrgng,
                if (showChargng) View.VISIBLE else View.GONE
            )

            // 3.1 油量
            if (showFuel) {
                views.setTextViewText(R.id.tv_range, "$fuelRange")
                if ((fuelRange > 999) || (fuelLevel == 100)) {
                    views.setTextViewTextSize(R.id.tv_range, TypedValue.COMPLEX_UNIT_SP, 17f)
                    views.setTextViewTextSize(R.id.tv_fuel_percent, TypedValue.COMPLEX_UNIT_SP, 17f)
                }
                views.setTextViewText(R.id.tv_fuel_percent, "$fuelLevel")
                views.setProgressBar(R.id.pb_fuel, 100, fuelLevel, false)
                val fuelColor = when {
                    fuelLevel < 20 -> R.color.status_red
                    showBattery -> R.color.status_blue
                    else -> R.color.status_green
                }
                views.setColorStateList(
                    R.id.pb_fuel,
                    "setProgressTintList",
                    ColorStateList.valueOf(context.getColor(fuelColor))
                )
            }

            // 3.2 电量
            if (showBattery) {
                views.setTextViewText(R.id.tv_battery_range, "$batteryPackRange")
                if ((batteryPackRange > 999) || (batteryPackPrc == 100)) {
                    views.setTextViewTextSize(
                        R.id.tv_battery_range,
                        TypedValue.COMPLEX_UNIT_SP,
                        17f
                    )
                    views.setTextViewTextSize(
                        R.id.tv_battery_percent,
                        TypedValue.COMPLEX_UNIT_SP,
                        17f
                    )
                }
                views.setTextViewText(R.id.tv_battery_percent, "$batteryPackPrc")
                views.setProgressBar(R.id.pb_battery, 100, batteryPackPrc, false)
                val batteryColor =
                    if (batteryPackPrc < 20) R.color.status_red else R.color.status_green
                views.setColorStateList(
                    R.id.pb_battery,
                    "setProgressTintList",
                    ColorStateList.valueOf(context.getColor(batteryColor))
                )
            }

            // 3.3 充电时间
            if (showChargng) {
                val h = chrgngRmnngTime / 60
                val m = chrgngRmnngTime % 60
                val timeText = when {
                    h > 0 && m > 0 -> "${h}小时${m}分钟"
                    h > 0 -> "${h}小时"
                    m > 0 -> "${m}分钟"
                    else -> ""
                }
                views.setTextViewText(R.id.chrgng_time, "$timeText")
                views.setTextColor(R.id.chrgng_time, context.getColor(R.color.status_green))
            }

            // 4. 小电瓶状态
            val batteryVoltageRaw = vehicleValue?.vehicle_battery ?: 0
            val batteryLevel = (vehicleValue?.vehicle_battery_prc ?: 0) / 10
            val batteryVoltage =
                if (batteryVoltageRaw > 999) batteryVoltageRaw / 100.0 else batteryVoltageRaw / 10.0
            val batteryVoltageString = String.format("%.1f", batteryVoltage)

            val batteryInfoText = "电瓶: $batteryLevel% 电压: ${batteryVoltageString}V"
            val spannableBatteryInfo = SpannableString(batteryInfoText)
            if (batteryVoltage < 11.0) {
                val startIndex = batteryInfoText.indexOf(batteryVoltageString)
                if (startIndex != -1) {
                    spannableBatteryInfo.setSpan(
                        ForegroundColorSpan(context.getColor(R.color.status_red)),
                        startIndex,
                        startIndex + batteryVoltageString.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            if (layoutId == R.layout.mg_widget) {
                views.setTextViewText(R.id.tv_battery_info, spannableBatteryInfo)
            } else if (layoutId == R.layout.mg_widget_icon) {
                views.setTextViewText(R.id.tv_battery_level, "$batteryLevel%")
                views.setTextViewText(R.id.tv_battery_voltage, "${batteryVoltageString}V")
            }

            // 5. 门锁状态
            val isLocked = vehicleState?.lock == true
            if (layoutId == R.layout.mg_widget) {
                views.setTextViewText(R.id.tv_lock_status, if (isLocked) "已上锁" else "未上锁")
                views.setTextColor(
                    R.id.tv_lock_status,
                    context.getColor(if (isLocked) R.color.status_green else R.color.status_red)
                )
            } else if (layoutId == R.layout.mg_widget_icon) {
                views.setImageViewResource(
                    R.id.tv_lock_status,
                    if (isLocked) R.drawable.lock else R.drawable.unlock
                )
            }

            // 6. 车内温度
            val interiorTemp = vehicleValue?.interior_temperature ?: 0.0
            views.setTextViewText(R.id.tv_temp_value, String.format("%.1f°C", interiorTemp))
            val tempColorRes =
                if (interiorTemp <= 27.0) R.color.status_green else R.color.status_red
            views.setTextColor(R.id.tv_temp_value, context.getColor(tempColorRes))
            if (layoutId == R.layout.mg_widget_icon) {
                views.setImageViewResource(
                    R.id.tv_temp_label,
                    if (interiorTemp <= 27.0) R.drawable.tempgreen else R.drawable.tempred
                )
            }
            // 7. 胎压
            updateTirePressure(
                context,
                views,
                vehicleValue?.front_left_tyre_pressure,
                R.id.tv_front_left_val
            )
            updateTirePressure(
                context,
                views,
                vehicleValue?.front_right_tyre_pressure,
                R.id.tv_front_right_val
            )
            updateTirePressure(
                context,
                views,
                vehicleValue?.rear_left_tyre_pressure,
                R.id.tv_rear_left_val
            )
            updateTirePressure(
                context,
                views,
                vehicleValue?.rear_right_tyre_pressure,
                R.id.tv_rear_right_val
            )

            // 8. 门窗详情
            if (vehicleState != null) {
                // 总览
                val allWindowsClosed =
                    !(vehicleState.driver_window == true || vehicleState.passenger_window == true ||
                            vehicleState.rear_left_window == true || vehicleState.rear_right_window == true || vehicleState.sunroof == true)
                if (layoutId == R.layout.mg_widget) {
                    views.setTextViewText(
                        R.id.tv_window_value,
                        if (allWindowsClosed) "已关闭" else "未关闭"
                    )
                    views.setTextColor(
                        R.id.tv_window_value,
                        context.getColor(if (allWindowsClosed) R.color.status_green else R.color.status_red)
                    )
                } else if (layoutId == R.layout.mg_widget_icon) {
                    views.setImageViewResource(
                        R.id.tv_window_value,
                        if (allWindowsClosed) R.drawable.windowclose else R.drawable.windowopen
                    );
                }
                val allDoorsClosed =
                    !(vehicleState.driver_door == true || vehicleState.passenger_door == true ||
                            vehicleState.rear_left_door == true || vehicleState.rear_right_door == true || vehicleState.bonnet == true || vehicleState.boot == true)
                if (layoutId == R.layout.mg_widget) {
                    views.setTextViewText(
                        R.id.tv_door_value,
                        if (allDoorsClosed) "已关闭" else "未关闭"
                    )
                    views.setTextColor(
                        R.id.tv_door_value,
                        context.getColor(if (allDoorsClosed) R.color.status_green else R.color.status_red)
                    )
                } else if (layoutId == R.layout.mg_widget_icon) {
                    views.setImageViewResource(
                        R.id.tv_door_value,
                        if (allDoorsClosed) R.drawable.doorclose else R.drawable.dooropen
                    );
                }

                // 详细 (mg_info_widget)
                updateUltimateVisuals(
                    views,
                    data.data
                )
            }

            // 9. 地址
            if (!ctxData.address.isNullOrEmpty()) {
                views.setTextViewText(R.id.tv_location, ctxData.address)
            }
        }

        // 功能：全量驱动车辆视觉状态。实现目的：通过映射 40 个资源图层，实现小米 HyperOS 级别的极致动效感。
        /**
         * 功能：全量驱动 40 个车辆视觉部件。
         * 实现目的：按照用户提供的 FrameLayout 布局层级，将所有传感器数据精准映射到小组件 UI。
         */


        private fun updateUltimateVisuals(views: RemoteViews, data: VehicleData?) {
            val state = data?.vehicle_state
            val value = data?.vehicle_value

            // ============================================================
            // 1. 胎压驱动层 (底层：绿色指示 vs 红色轮毂)
            // ============================================================
            val flP = (value?.front_left_tyre_pressure ?: 0) / 100.0
            val frP = (value?.front_right_tyre_pressure ?: 0) / 100.0
            val rlP = (value?.rear_left_tyre_pressure ?: 0) / 100.0
            val rrP = (value?.rear_right_tyre_pressure ?: 0) / 100.0

            // 逻辑：胎压正常(2.0-3.8)显示绿色，异常显示红色
            updateWheel(views, R.id.iv_wheel_fl_green, R.id.iv_wheel_fl_red, flP)
            updateWheel(views, R.id.iv_wheel_fr_green, R.id.iv_wheel_fr_red, frP)
            updateWheel(views, R.id.iv_wheel_rl_green, R.id.iv_wheel_rl_red, rlP)
            updateWheel(views, R.id.iv_wheel_rr_green, R.id.iv_wheel_rr_red, rrP)

            // ============================================================
            // 2. 车门状态驱动 (Style A + Style B + 伴随车窗)
            // ============================================================
            // 修复 Pair 语法错误：明确声明 List<Pair<Boolean, List<Int>>>
            val doorConfigs = listOf(
                Pair(
                    state?.driver_door == true,
                    listOf(R.id.iv_door_fl_a, R.id.iv_door_fl_b, R.id.iv_win_door_fl)
                ),
                Pair(
                    state?.passenger_door == true,
                    listOf(R.id.iv_door_fr_a, R.id.iv_door_fr_b, R.id.iv_win_door_fr)
                ),
                Pair(
                    state?.rear_left_door == true,
                    listOf(R.id.iv_door_rl_a, R.id.iv_door_rl_b, R.id.iv_win_door_rl)
                ),
                Pair(
                    state?.rear_right_door == true,
                    listOf(R.id.iv_door_rr_a, R.id.iv_door_rr_b, R.id.iv_win_door_rr)
                )
            )

            doorConfigs.forEach { config ->
                val isDoorOpen = config.first
                val ids = config.second
                ids.forEach { id ->
                    views.setViewVisibility(id, if (isDoorOpen) View.VISIBLE else View.GONE)
                }
            }

            // ============================================================
            // 3. 车窗开启状态驱动
            // ============================================================
            views.setViewVisibility(
                R.id.iv_win_fl_open,
                if (state?.driver_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_fr_open,
                if (state?.passenger_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_rl_open,
                if (state?.rear_left_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_rr_open,
                if (state?.rear_right_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_sky_open,
                if (state?.sunroof == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_fl,
                if (state?.driver_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_fr,
                if (state?.passenger_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_rl,
                if (state?.rear_left_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_rr,
                if (state?.rear_right_window == true) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.iv_win_sky,
                if (state?.sunroof == true) View.VISIBLE else View.GONE
            )

            // ============================================================
            // 4. 箱盖开启驱动 (Style A + Style B)
            // ============================================================
            val isHoodOpen = state?.bonnet == true
            views.setViewVisibility(R.id.iv_hood_a, if (isHoodOpen) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.iv_hood_b, if (isHoodOpen) View.VISIBLE else View.GONE)

            val isTrunkOpen = state?.boot == true
            views.setViewVisibility(R.id.iv_trunk_a, if (isTrunkOpen) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.iv_trunk_b, if (isTrunkOpen) View.VISIBLE else View.GONE)

            // ============================================================
            // 5. 灯光特效驱动 (最上层)
            // ============================================================
            // 假设数据模型中包含灯光字段，此处以逻辑示例
            val isLowBeam = state?.light == true
            val isHighBeam = state?.main_beam == true

            views.setViewVisibility(
                R.id.iv_light,
                if (isLowBeam || isHighBeam) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(R.id.iv_light_low, if (isLowBeam) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.iv_light_high, if (isHighBeam) View.VISIBLE else View.GONE)
            views.setViewVisibility(
                R.id.iv_light_both,
                if (isLowBeam && isHighBeam) View.VISIBLE else View.GONE
            )
        }

        private fun updateWheel(views: RemoteViews, greenId: Int, redId: Int, pressure: Double) {
            val isNormal = pressure in 2.0..3.8
            views.setViewVisibility(greenId, if (isNormal) View.VISIBLE else View.GONE)
            views.setViewVisibility(redId, if (isNormal) View.GONE else View.VISIBLE)
        }

        private fun updateTirePressure(
            context: Context,
            views: RemoteViews,
            pressureRaw: Int?,
            textViewId: Int
        ) {
            if (pressureRaw == null) {
                views.setTextViewText(textViewId, "- Bar")
                views.setTextColor(textViewId, context.getColor(R.color.status_red))
                return
            }
            val pressure = pressureRaw / 100.0
            val pressureString = String.format("%.1f", pressure)
            val fullText = "$pressureString Bar"
            val spannable = SpannableString(fullText)

            val colorRes =
                if (pressure < 2.0 || pressure > 3.8) R.color.status_red else R.color.status_green
            spannable.setSpan(
                ForegroundColorSpan(context.getColor(colorRes)),
                0,
                pressureString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            views.setTextViewText(textViewId, spannable)
        }


        // ========================================================================
        // 字体与事件设置 (通用逻辑)
        // ========================================================================

        private fun setupClickEvents(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            layoutId: Int
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 【关键修复】动态获取当前 Widget ID 到底属于哪个类 (MGWidget0 还是 MGWidget1)
            // 系统会返回实际注册在 AndroidManifest 中的那个组件类
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val realComponent =
                providerInfo?.provider ?: ComponentName(context, MGWidget::class.java)

            // 1. 设置 Logo 点击 -> 打开主页 (保持不变)
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_brand_logo, openAppPendingIntent)

            // 2. 设置刷新点击 -> 发送广播给【实际的子类组件】
            val refreshIntent = Intent().apply {
                component = realComponent // <--- 重点：这里不能写死 MGWidget::class.java
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // 使用 ID 作为 requestCode，防止不同组件的 Intent 混淆
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (layoutId == R.layout.mg_widget) {
                views.setOnClickPendingIntent(R.id.tv_update_time, refreshPendingIntent)
            } else if (layoutId == R.layout.mg_widget_icon) {
                views.setOnClickPendingIntent(R.id.tv_update, refreshPendingIntent)
                views.setOnClickPendingIntent(R.id.tv_update_time, refreshPendingIntent)
            }

            // 3. 设置翻页点击 -> 发送广播给【实际的子类组件】
            val flipIntent = Intent().apply {
                component = realComponent // <--- 重点：同上，动态设置
                action = ACTION_WIDGET_FLIP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val flipPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                flipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.view_flipper_center, flipPendingIntent)
        }

        private fun adjustFontSizes(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews,
            layoutId: Int
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val isLarge = height > 180

            // 字体配置：[主数值, 车牌, 辅助信息(大), 辅助信息(中), 辅助信息(小), 极小]
            val fontSizes = if (isLarge) {
                listOf(15f, 12f, 11f, 10f, 10f, 10f)
            } else {
                listOf(18f, 13f, 12f, 11f, 10f, 8f)
            }

            // 1. 核心数值
            //setTextSizeForIds(views, fontSizes[0],
            //    R.id.tv_range, R.id.tv_fuel_percent, R.id.tv_battery_range, R.id.tv_battery_percent)

            // 2. 车牌
            setTextSizeForIds(views, fontSizes[1], R.id.tv_plate_number)

            // 3. 辅助信息 (里程、电瓶、锁状态)
            if (layoutId == R.layout.mg_widget) {
                setTextSizeForIds(
                    views,
                    fontSizes[4],
                    R.id.tv_total_mileage,
                    R.id.tv_battery_info,
                    R.id.tv_lock_status,
                    R.id.tv_update_time
                )
                setTextSizeForIds(
                    views,
                    fontSizes[5],
                    R.id.tv_temp_value,
                    R.id.tv_window_label,
                    R.id.tv_door_label,
                    R.id.tv_location
                )
            } else if (layoutId == R.layout.mg_widget_icon) {
                setTextSizeForIds(
                    views,
                    fontSizes[4],
                    R.id.tv_total_mileage,
                    R.id.tv_battery_level,
                    R.id.tv_battery_voltage,
                    R.id.tv_update_time
                )
                setTextSizeForIds(
                    views,
                    fontSizes[4],
                    R.id.tv_temp_value,
                    R.id.tv_window_label,
                    R.id.tv_door_label,
                    R.id.tv_location
                )
            }

            // 4. 详细信息 (门窗、温度、胎压) - 批量处理
            val detailIds = intArrayOf(
                R.id.tv_temp_label,
                R.id.tv_window_value,
                R.id.tv_door_value,
                R.id.tv_front_left, R.id.tv_front_left_val,
                R.id.tv_rear_left, R.id.tv_rear_left_val,
                R.id.tv_front_right, R.id.tv_front_right_val,
                R.id.tv_rear_right, R.id.tv_rear_right_val,

                // 门窗详情 ID
//                R.id.fl_window_label, R.id.fl_window_value, R.id.fl_door_label, R.id.fl_door_value,
//                R.id.fr_window_label, R.id.fr_window_value, R.id.fr_door_label, R.id.fr_door_value,
//                R.id.rl_window_label, R.id.rl_window_value, R.id.rl_door_label, R.id.rl_door_value,
//                R.id.rr_window_label, R.id.rr_window_value, R.id.rr_door_label, R.id.rr_door_value,
//
//                // 门窗组合 FrameLayout 容器不需要设字体，但 label/value 需要
//                R.id.fl_window_door, R.id.fr_window_door, R.id.rl_window_door, R.id.rr_window_door
            )

            for (id in detailIds) {
                // 部分 ID 可能不是 TextView (如 Layout)，setTextViewTextSize 可能会忽略或报错，
                // 但为了代码简洁，通常 RemoteViews 宽容度较高。
                // 若要严谨，可排除 Layout ID。此处 fl_window_door 等是布局 ID，
                // 实际上 RemoteViews.setTextViewTextSize 对非 TextView 无效但不崩，可保留以防 XML 结构变更
                val isIconSpecialView = (layoutId == R.layout.mg_widget_icon) &&
                        (id == R.id.tv_window_value || id == R.id.tv_door_value || id == R.id.tv_temp_label)

                // 只有当“不是”特殊情况时，才执行字体设置
                if (!isIconSpecialView) {
                    try {
                        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
                    } catch (e: Exception) {
                        // 忽略非 TextView 的 ID
                    }
                }
            }

            // 特殊处理：车名保持固定
            views.setTextViewTextSize(R.id.tv_car_name, TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        private fun setTextSizeForIds(views: RemoteViews, sizeSp: Float, vararg viewIds: Int) {
            for (id in viewIds) {
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp)
            }
        }
    }
}
