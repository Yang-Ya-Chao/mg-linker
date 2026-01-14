package com.my.mg

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.location.Geocoder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.RemoteViews
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.PowerManager
import com.my.mg.log.LogcatHelper

class MGWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        LogcatHelper.startRecording(context)
        log(context, "onUpdate called for IDs: ${appWidgetIds.joinToString()}")
        // 1. 获取电源管理器
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // 2. 检查屏幕是否交互中 (亮屏状态)
        val isScreenOn = powerManager.isInteractive
        if (isScreenOn){
                // 可能有多个小组件处于活动状态，因此请更新所有这些小组件
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        log(context, "onReceive called with action: ${intent.action}")
        super.onReceive(context, intent)
        if (ACTION_WIDGET_FLIP == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val views = RemoteViews(context.packageName, R.layout.mg_widget)
                // 切换视图
                views.showNext(R.id.view_flipper_center)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        else if (ACTION_REFRESH == intent.action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MGWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            // 显示“正在更新...”状态并重置视图
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.mg_widget)
                // Reset to main view
                //views.setDisplayedChild(R.id.view_flipper_center, 0)
                views.setTextViewText(R.id.tv_update_time, "正在更新....")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        private const val PREFS_NAME = "mg_config"
        private const val LOG_TAG = "MGWidget"
        private const val ACTION_REFRESH = "com.my.mg.ACTION_REFRESH"
        private const val ACTION_WIDGET_FLIP = "ACTION_WIDGET_FLIP"

        private fun log(context: Context, message: String) {
            Log.d(LOG_TAG, message)
        }

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            log(context, "updateAppWidget called for ID: $appWidgetId")
            val views = RemoteViews(context.packageName, R.layout.mg_widget)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val vin = prefs.getString("vin", "") ?: ""
            val color = prefs.getString("color", "") ?: ""
            val carName = prefs.getString("car_name", "")
            val plateNumber = prefs.getString("plate_number", "")
            val token = prefs.getString("access_token", "") ?: ""

            log(context, "Config - VIN: $vin, Color: $color, Token: ${token.take(10)}...")

            // 基本信息
            views.setTextViewText(R.id.tv_car_name, if (carName.isNullOrEmpty()) "" else carName)
            views.setTextViewText(R.id.tv_plate_number, if (plateNumber.isNullOrEmpty()) "" else plateNumber)

            // 根据颜色设置汽车图片
            val carImageResId = when (color) {
                "墨玉黑" -> R.drawable.black_300x257
                "釉瓷白" -> R.drawable.white_300x257
                "山茶红" -> R.drawable.red_300x257
                "雾凇灰" -> R.drawable.gray_300x257
                "翡冷翠" -> R.drawable.green_300x257
                "冰晶蓝" -> R.drawable.blue_300x257
                else -> R.drawable.blue_300x257
            }
            views.setImageViewResource(R.id.iv_car_image, carImageResId)

            // 设置点击logo打开应用程序
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.iv_brand_logo, openAppPendingIntent)

            // 在“更新”文本上设置刷新按钮意图
            val refreshIntent = Intent(context, MGWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_update_time, refreshPendingIntent)
            // 创建一个意图，动作为自定义的切换动作
            val flipIntent = Intent(context, MGWidget::class.java).apply {
                action = ACTION_WIDGET_FLIP
                // 传入当前的 widgetId
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // 使用 appWidgetId 保证唯一性
                flipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 点击图片触发切换
            views.setOnClickPendingIntent(R.id.view_flipper_center, pendingIntent)

            // 获取数据
            if (vin.isNotEmpty() && token.isNotEmpty()) {
                fetchVehicleData(context, views, appWidgetManager, appWidgetId, vin, token)
            } else {
                views.setTextViewText(R.id.tv_update_time, "请配置 App")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun fetchVehicleData(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            vin: String,
            token: String
        ) {
            log(context, "Fetching vehicle data for VIN: $vin")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val timestamp = System.currentTimeMillis() / 1000
                    val url = "https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=$timestamp&token=$token&vin=$vin"
                    log(context, "Request URL: $url")

                    val client = OkHttpClient()
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    log(context, "Response Code: ${response.code}")
                    log(context, "Response Body: $responseBody")

                    if (responseBody != null) {
                        val gson = Gson()
                        val data = gson.fromJson(responseBody, VehicleStatusResponse::class.java)

                        withContext(Dispatchers.Main) {
                            updateWidgetUI(context, views, data, appWidgetManager, appWidgetId)
                        }
                    } else {
                        log(context, "Response body is null")
                    }
                } catch (e: Exception) {
                    log(context, "Error fetching data: ${e.message}")
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.tv_update_time, "更新失败")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }

        private fun updateWidgetUI(
            context: Context,
            views: RemoteViews,
            data: VehicleStatusResponse,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val vehicleValue = data.data?.vehicle_value
            val vehicleState = data.data?.vehicle_state
            val vehiclePosition = data.data?.vehicle_position
            val updateTime = data.data?.update_time ?: System.currentTimeMillis()

            log(context, "Updating UI with data: Mileage=${vehicleValue?.odometer}, Lock=${vehicleState?.lock}")

            // --- Main Info UI (mg_info_widget) ---
            val mileage = vehicleValue?.odometer ?: 0
            val fuelLevel = vehicleValue?.fuel_level_prc ?: 0
            val fuelRange = vehicleValue?.fuel_range ?: 0
            views.setTextViewText(R.id.tv_range, "$fuelRange")
            views.setTextViewText(R.id.tv_fuel_percent, "$fuelLevel")
            views.setTextViewText(R.id.tv_total_mileage, "总里程: $mileage km")
            views.setProgressBar(R.id.pb_fuel,100,fuelLevel,false)

            val fuelColorRes = if (fuelLevel < 20) R.color.process_red else R.color.process_green
            views.setColorStateList(R.id.pb_fuel, "setProgressTintList", ColorStateList.valueOf(context.getColor(fuelColorRes)))

            val batteryLevelRaw = vehicleValue?.vehicle_battery_prc ?: 0
            val batteryVoltageRaw = vehicleValue?.vehicle_battery ?: 0
            val batteryLevel = batteryLevelRaw / 10
            val batteryVoltage = batteryVoltageRaw / 10.0
            val batteryVoltageString = String.format("%.1f", batteryVoltage)
            val batteryInfoText = "电池: $batteryLevel% 电压: ${batteryVoltageString}V"
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
            views.setTextViewText(R.id.tv_battery_info, spannableBatteryInfo)

            val isLocked = vehicleState?.lock == true
            views.setTextViewText(R.id.tv_lock_status, if (isLocked) "已上锁" else "未上锁")
            views.setTextColor(R.id.tv_lock_status, if (isLocked) context.getColor(R.color.status_green) else context.getColor(R.color.status_red))

            val updateDate = Date(updateTime)
            val now = Date()
            val sdfSameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val isSameDay = sdfSameDay.format(updateDate) == sdfSameDay.format(now)
            val displaySdf = if (isSameDay) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            views.setTextViewText(R.id.tv_update_time, "${displaySdf.format(updateDate)} 更新")

            val interiorTemp = vehicleValue?.interior_temperature ?: 0.0
            views.setTextViewText(R.id.tv_temp_value, String.format("%.1f°C", interiorTemp))
            val tempColorRes = if (interiorTemp <= 27.0) R.color.status_green else R.color.status_red
            views.setTextColor(R.id.tv_temp_value, context.getColor(tempColorRes))

            updateTirePressure(context, views, vehicleValue?.front_left_tyre_pressure, R.id.tv_front_left_val)
            updateTirePressure(context, views, vehicleValue?.front_right_tyre_pressure, R.id.tv_front_right_val)
            updateTirePressure(context, views, vehicleValue?.rear_left_tyre_pressure, R.id.tv_rear_left_val)
            updateTirePressure(context, views, vehicleValue?.rear_right_tyre_pressure, R.id.tv_rear_right_val)

            if (vehicleState != null) {
                // Summary for doors and windows (on mg_info_widget)
                val allWindowsClosed = !(vehicleState.driver_window == true || vehicleState.passenger_window == true || vehicleState.rear_left_window == true || vehicleState.rear_right_window == true || vehicleState.sunroof == true)
                views.setTextViewText(R.id.tv_window_value, if (allWindowsClosed) "已关闭" else "未关闭")
                views.setTextColor(R.id.tv_window_value, if (allWindowsClosed) context.getColor(R.color.status_green) else context.getColor(R.color.status_red))
                val allDoorsClosed = !(vehicleState.driver_door == true || vehicleState.passenger_door == true || vehicleState.rear_left_door == true || vehicleState.rear_right_door == true || vehicleState.bonnet == true || vehicleState.boot == true)
                views.setTextViewText(R.id.tv_door_value, if (allDoorsClosed) "已关闭" else "未关闭")
                views.setTextColor(R.id.tv_door_value, if (allDoorsClosed) context.getColor(R.color.status_green) else context.getColor(R.color.status_red))

                // --- Detailed Lock/Window UI (mg_lock_widget) ---
                updateDoorOrWindowStatus(context, views, vehicleState.driver_door == true, R.id.fl_door_value)
                updateDoorOrWindowStatus(context, views, vehicleState.passenger_door == true, R.id.fr_door_value)
                updateDoorOrWindowStatus(context, views, vehicleState.rear_left_door == true, R.id.rl_door_value)
                updateDoorOrWindowStatus(context, views, vehicleState.rear_right_door == true, R.id.rr_door_value)

                updateDoorOrWindowStatus(context, views, vehicleState.driver_window == true, R.id.fl_window_value)
                updateDoorOrWindowStatus(context, views, vehicleState.passenger_window == true, R.id.fr_window_value)
                updateDoorOrWindowStatus(context, views, vehicleState.rear_left_window == true, R.id.rl_window_value)
                updateDoorOrWindowStatus(context, views, vehicleState.rear_right_window == true, R.id.rr_window_value)
            }

            if (vehiclePosition != null) {
                val lat = vehiclePosition.latitude?.toDoubleOrNull()
                val long = vehiclePosition.longitude?.toDoubleOrNull()
                fetchLocation(context, views, appWidgetManager, appWidgetId, lat, long)
            } else {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        
        private fun updateDoorOrWindowStatus(context: Context, views: RemoteViews, isOpen: Boolean, viewId: Int) {
            val text = if (isOpen) "开启" else "关闭"
            val color = if (isOpen) context.getColor(R.color.status_red) else context.getColor(R.color.status_green)
            views.setTextViewText(viewId, text)
            views.setTextColor(viewId, color)
        }

        private fun updateTirePressure(context: Context, views: RemoteViews, pressureRaw: Int?, textViewId: Int) {
            if (pressureRaw != null) {
                val pressure = pressureRaw / 100.0
                val pressureString = String.format("%.1f", pressure)
                val roundedPressure = pressureString.toDouble()
                val fullText = "$pressureString Bar"
                val spannable = SpannableString(fullText)

                val colorRes = if (roundedPressure < 2.0 || roundedPressure > 3.0) {
                    R.color.status_red
                } else {
                    R.color.status_green
                }
                val color = context.getColor(colorRes)

                spannable.setSpan(
                    ForegroundColorSpan(color),
                    0,
                    pressureString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                views.setTextViewText(textViewId, spannable)
            } else {
                views.setTextViewText(textViewId, "- Bar")
                views.setTextColor(textViewId, context.getColor(R.color.status_red))
            }
        }
        
        private fun fetchLocation(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            latitude: Double?,
            longitude: Double?
        ) {
            if (latitude == null || longitude == null) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }
            log(context, "Fetching location for lat=$latitude, long=$longitude")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val address = geocoder.getFromLocation(latitude, longitude, 1)
                    log(context, "Addr result: $address")
                    if (address != null && address.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            views.setTextViewText(R.id.tv_location, address[0].getAddressLine(0))
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                } catch (e: Exception) {
                    log(context, "Error fetching location: ${e.message}")
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }
    }
}

// Data classes (assuming they are correct as per previous context)
data class VehicleStatusResponse(val req_id: String?, val data: VehicleData?)
data class VehicleData(
    val vehicle_position: VehiclePosition?,
    val vehicle_security: Any?,
    val vehicle_alerts: List<Any>?,
    val vehicle_value: VehicleValue?,
    val vehicle_state: VehicleState?,
    val update_time: Long?
)

data class VehiclePosition(
    val satellites: Int?,
    val altitude: Int?,
    val gps_status: Int?,
    val latitude: String?,
    val longitude: String?,
    val update_time: Long?,
    val gps_time: Long?,
    val hdop: Int?
)

data class VehicleValue(
    val fuel_level_prc: Int?,
    val fuel_range: Int?,
    val odometer: Int?,
    val vehicle_battery: Int?,
    val vehicle_battery_prc: Int?,
    val interior_temperature: Double?,
    val exterior_temperature: Int?,
    val rear_right_tyre_pressure: Int?,
    val front_left_tyre_pressure: Int?,
    val front_right_tyre_pressure: Int?,
    val rear_left_tyre_pressure: Int?
)

data class VehicleState(
    val lock: Boolean?,
    val door: Boolean?,
    val driver_door: Boolean?,
    val passenger_door: Boolean?,
    val rear_left_door: Boolean?,
    val rear_right_door: Boolean?,
    val bonnet: Boolean?,
    val boot: Boolean?,
    val driver_window: Boolean?,
    val passenger_window: Boolean?,
    val rear_left_window: Boolean?,
    val rear_right_window: Boolean?,
    val sunroof: Boolean?
)
