package com.my.mg

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.my.mg.log.LogcatHelper
import com.my.mg.worker.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Power by 杨家三郎 & Optimized by Android Expert
 * MGWidget - 车辆状态桌面小组件
 *
 * 核心优化：
 * 1. 采用 WorkManager + Suspend Function 机制，确保后台刷新任务不被系统强杀。
 * 2. 增加图片采样加载 (Sampled Bitmap)，彻底解决 RemoteViews 内存溢出 (OOM) 问题。
 * 3. 优化 Geocoder 和网络请求流程，提升稳定性。
 */
class MGWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        if (BuildConfig.DEBUG) {
            LogcatHelper.startRecording(context)
        }
        log(context, "onUpdate called for IDs: ${appWidgetIds.joinToString()}")

        // 使用 WorkManager 触发更新，保证任务能够执行完成
        startUpdateWorker(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        log(context, "onAppWidgetOptionsChanged for ID: $appWidgetId")

        // 尺寸变化时触发刷新以适配字体
        startUpdateWorker(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        log(context, "onReceive called with action: ${intent.action}")

        if (ACTION_WIDGET_FLIP == intent.action) {
            // 处理翻页逻辑
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val views = RemoteViews(context.packageName, R.layout.mg_widget)
                views.showNext(R.id.view_flipper_center)
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            }
        } else if (ACTION_REFRESH == intent.action) {
            // 处理手动刷新逻辑
            // 1. 立即给用户反馈 "正在更新..."
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MGWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.mg_widget)
                views.setTextViewText(R.id.tv_update_time, "正在更新...")
                appWidgetManager.updateAppWidget(id, views)
            }

            // 2. 启动 Worker 进行真正的网络请求 (相比直接协程，Worker 更可靠)
            startUpdateWorker(context)
        }
    }

    /**
     * 启动一次性后台任务来更新小组件
     */
    private fun startUpdateWorker(context: Context) {
        val request = OneTimeWorkRequest.Builder(WidgetUpdateWorker::class.java).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        private const val PREFS_NAME = "mg_config"
        private const val LOG_TAG = "MGWidget"
        private const val ACTION_REFRESH = "com.my.mg.ACTION_REFRESH"
        private const val ACTION_WIDGET_FLIP = "ACTION_WIDGET_FLIP"

        private fun log(context: Context, message: String) {
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
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            log(context, "updateWidgetSynchronously called for ID: $appWidgetId")

            val views = RemoteViews(context.packageName, R.layout.mg_widget)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // 1. 读取配置
            val vin = prefs.getString("vin", "") ?: ""
            val carBrand = prefs.getString("car_brand", "名爵") ?: "名爵"
            val carModel = prefs.getString("car_model", "") ?: ""
            val carName = prefs.getString("car_name", "") ?: ""
            val plateNumber = prefs.getString("plate_number", "") ?: ""
            val token = prefs.getString("access_token", "") ?: ""
            val carImageUrl = prefs.getString("car_image_url", "") ?: ""

            // 2. 设置静态 UI (车名、车牌、Logo)
            views.setTextViewText(
                R.id.tv_car_name,
                if (carName.isNullOrEmpty()) carModel else carName
            )
            views.setTextViewText(R.id.tv_plate_number, plateNumber)
            val logoResId = if (carBrand == "荣威") R.drawable.rw_logo else R.drawable.mg_logo
            views.setImageViewResource(R.id.iv_brand_logo, logoResId)

            // 3. 动态调整字体大小
            adjustFontSizes(context, appWidgetManager, appWidgetId, views)

            // 4. 设置点击事件 (打开APP、刷新、翻页)
            setupClickEvents(context, views, appWidgetId)

            // 5. 加载车辆图片 (挂起操作，含缓存和采样)
            loadCarImageSuspended(context, views, carImageUrl)

            // 6. 获取并更新车辆数据 (挂起操作)
            if (vin.isNotEmpty() && token.isNotEmpty()) {
                if (isNetworkAvailable(context)) {
                    // 获取车辆状态数据
                    val data = fetchVehicleDataSuspended(context, vin, token)
                    if (data != null) {
                        // 获取地理位置 (如果经纬度存在)
                        var address: String? = null
                        val pos = data.data?.vehicle_position
                        if (pos?.latitude != null && pos.longitude != null) {
                            val lat = pos.latitude.toDoubleOrNull()
                            val lng = pos.longitude.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                address = getAddressSync(context, lat, lng)
                                log(context, address)
                            }
                        }
                        // 更新所有 UI 字段
                        updateWidgetUI(context, views, data, address)
                    }
                } else {
                    // 无网络时保持原样，或者可以显示无网络提示
                    // views.setTextViewText(R.id.tv_location, "网络不可用")
                }
            } else {
                views.setTextViewText(R.id.tv_update_time, "请配置 App")
            }

            // 7. 提交更新到系统
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // ========================================================================
        // 辅助逻辑：网络请求与数据处理
        // ========================================================================

        /**
         * 挂起函数：获取车辆数据
         */
        private suspend fun fetchVehicleDataSuspended(
            context: Context, vin: String, token: String
        ): VehicleStatusResponse? = withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis() / 1000
                val url =
                    "https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=$timestamp&token=$token&vin=$vin"
                log(context, "Fetch Data url:  $url")
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                log(context, "Fetch Data response:  $body")
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    Gson().fromJson(body, VehicleStatusResponse::class.java)
                } else {
                    log(context, "Fetch Data Failed: Code ${response.code}")
                    null
                }
            } catch (e: Exception) {
                log(context, "Fetch Data Error: ${e.message}")
                e.printStackTrace()
                null
            }
        }

        /**
         * 同步获取地址 (在 Worker 线程中直接调用，无需再开协程)
         */
        private fun getAddressSync(context: Context, lat: Double, lng: Double): String {
            return try {
                val geocoder = Geocoder(context, Locale.getDefault())

                // 1. 请求多个候选地址（3 个），提高解析成功率
                val addresses = geocoder.getFromLocation(lat, lng, 3)
                log(context, "Geocoder addr: ${addresses}")
                // 如果没有任何地址，返回空字符串
                if (addresses.isNullOrEmpty()) return ""

                // 2. 从候选地址中挑选“信息最丰富”的一个 (原版优选算法)
                // 权重规则：有道路名+10分，有子区域(区/县)+5分
                val bestAddr = addresses.maxByOrNull {
                    (if (it.thoroughfare != null) 10 else 0) + (if (it.subLocality != null) 5 else 0)
                } ?: addresses[0] // 兜底：如果排序失败，使用第一个

                // 3. 格式化地址
                formatAddressSmart(bestAddr)
            } catch (e: Exception) {
                log(context, "Geocoder Error: ${e.message}")
                ""
            }
        }

        /**
         * 挂起函数：加载图片
         * 包含 Bitmap 采样，防止 OOM (Out Of Memory)
         */
        private suspend fun loadCarImageSuspended(
            context: Context, views: RemoteViews, carImageUrl: String
        ) = withContext(Dispatchers.IO) {
            if (carImageUrl.isEmpty()) {
                views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                return@withContext
            }

            try {
                val cacheDir = File(context.filesDir, "car_image_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                // 使用 hash 保证文件名合法
                val fileName = carImageUrl.hashCode().toString() + ".img"
                val imageFile = File(cacheDir, fileName)

                // 缓存策略：如果文件不存在，才下载
                if (!imageFile.exists()) {
                    // 清理旧缓存 (删除该目录下所有非当前图片的文件)
                    cacheDir.listFiles()?.forEach { if (it.name != fileName) it.delete() }
                    downloadAndSaveImage(carImageUrl, imageFile)
                }

                if (imageFile.exists()) {
                    // 关键修复：使用采样解码，限制图片大小
                    // 300x200 足够小组件显示，避免加载几兆的原图
                    val bitmap = decodeSampledBitmap(imageFile.absolutePath, 300, 200)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.iv_car_image, bitmap)
                    } else {
                        // 解码失败回退
                        views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
            }
        }

        private fun downloadAndSaveImage(urlStr: String, outFile: File) {
            val url = URL(urlStr)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            connection.getInputStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // 防止 OOM 的关键方法：计算采样率
        private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true // 只读取尺寸，不加载内存
            }
            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false // 真正加载

            return BitmapFactory.decodeFile(path, options)
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(network) ?: return false
            return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        // ========================================================================
        // UI 更新逻辑 (完整保留)
        // ========================================================================

        private fun updateWidgetUI(
            context: Context,
            views: RemoteViews,
            data: VehicleStatusResponse,
            address: String?
        ) {
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
            views.setTextViewText(R.id.tv_update_time, "${displaySdf.format(updateDate)} 更新")

            // 2. 总里程
            val mileage = vehicleValue?.odometer ?: 0
            views.setTextViewText(R.id.tv_total_mileage, "总里程: $mileage km")

            // 3. 油量、电量、续航
            val fuelLevel = vehicleValue?.fuel_level_prc ?: 0
            val fuelRange = vehicleValue?.fuel_range ?: 0
            val batteryPackRange = vehicleValue?.battery_pack_range ?: 0
            val batteryPackPrc = (vehicleValue?.battery_pack_prc ?: 0) / 10
            val chrgngRmnngTime = vehicleValue?.chrgng_rmnng_time ?: 0
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
                R.id.chrgng_rmnng_time,
                if (showChargng) View.VISIBLE else View.GONE
            )

            // 3.1 油量
            if (showFuel) {
                views.setTextViewText(R.id.tv_range, "$fuelRange")
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
                views.setTextViewText(R.id.chrgng_rmnng_time, "$timeText")
                views.setTextColor(R.id.chrgng_rmnng_time, context.getColor(R.color.status_green))
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
            views.setTextViewText(R.id.tv_battery_info, spannableBatteryInfo)

            // 5. 门锁状态
            val isLocked = vehicleState?.lock == true
            views.setTextViewText(R.id.tv_lock_status, if (isLocked) "已上锁" else "未上锁")
            views.setTextColor(
                R.id.tv_lock_status,
                context.getColor(if (isLocked) R.color.status_green else R.color.status_red)
            )
//            views.setTextViewText(R.id.tv_lock_status, if (isLocked) "已上锁" else "未上锁")
//            views.setTextColor(
//                R.id.tv_lock_status, if (isLocked) context.getColor(R.color.status_green)
//                else context.getColor(R.color.status_red)
//            )

            // 6. 车内温度
            val interiorTemp = vehicleValue?.interior_temperature ?: 0.0
            views.setTextViewText(R.id.tv_temp_value, String.format("%.1f°C", interiorTemp))
            val tempColorRes =
                if (interiorTemp <= 27.0) R.color.status_green else R.color.status_red
            views.setTextColor(R.id.tv_temp_value, context.getColor(tempColorRes))

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
                views.setTextViewText(
                    R.id.tv_window_value,
                    if (allWindowsClosed) "已关闭" else "未关闭"
                )
                views.setTextColor(
                    R.id.tv_window_value,
                    context.getColor(if (allWindowsClosed) R.color.status_green else R.color.status_red)
                )
//                views.setTextViewText(
//                    R.id.tv_window_value, if (allWindowsClosed) "已关闭" else "未关闭"
//                )
//                views.setTextColor(
//                    R.id.tv_window_value,
//                    if (allWindowsClosed) context.getColor(R.color.status_green)
//                    else context.getColor(R.color.status_red)
//                )

                val allDoorsClosed =
                    !(vehicleState.driver_door == true || vehicleState.passenger_door == true ||
                            vehicleState.rear_left_door == true || vehicleState.rear_right_door == true || vehicleState.bonnet == true || vehicleState.boot == true)
                views.setTextViewText(
                    R.id.tv_door_value,
                    if (allDoorsClosed) "已关闭" else "未关闭"
                )
                views.setTextColor(
                    R.id.tv_door_value,
                    context.getColor(if (allDoorsClosed) R.color.status_green else R.color.status_red)
                )
//                views.setTextColor(
//                    R.id.tv_door_value, if (allDoorsClosed) context.getColor(R.color.status_green)
//                    else context.getColor(R.color.status_red)
//                )

                // 详细 (mg_lock_widget)
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.driver_door == true,
                    R.id.fl_door_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.passenger_door == true,
                    R.id.fr_door_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.rear_left_door == true,
                    R.id.rl_door_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.rear_right_door == true,
                    R.id.rr_door_value
                )

                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.driver_window == true,
                    R.id.fl_window_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.passenger_window == true,
                    R.id.fr_window_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.rear_left_window == true,
                    R.id.rl_window_value
                )
                updateDoorOrWindowStatus(
                    context,
                    views,
                    vehicleState.rear_right_window == true,
                    R.id.rr_window_value
                )
            }

            // 9. 地址
            if (!address.isNullOrEmpty()) {
                views.setTextViewText(R.id.tv_location, address)
            }
        }

        private fun updateDoorOrWindowStatus(
            context: Context,
            views: RemoteViews,
            isOpen: Boolean,
            viewId: Int
        ) {
            val text = if (isOpen) "开启" else "关闭"
            val color =
                if (isOpen) context.getColor(R.color.status_red) else context.getColor(R.color.status_green)
            views.setTextViewText(viewId, text)
            views.setTextColor(viewId, color)
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
                if (pressure < 2.0 || pressure > 3.0) R.color.status_red else R.color.status_green
            spannable.setSpan(
                ForegroundColorSpan(context.getColor(colorRes)),
                0,
                pressureString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            views.setTextViewText(textViewId, spannable)
        }

        private fun formatAddressSmart(addr: Address): String {
            val sb = StringBuilder()
            val admin = addr.adminArea ?: ""
            val city = addr.locality ?: ""
            val district = addr.subLocality ?: ""

            sb.append(admin)
            if (city != admin) sb.append(city)
            sb.append(district)

            val road = addr.thoroughfare ?: ""
            val feature = addr.featureName ?: ""

            if (road.isNotEmpty()) {
                sb.append(road)
                if (feature.contains(road) && feature != road) {
                    sb.append(feature.substringAfter(road))
                } else if (feature != road && feature != district) {
                    sb.append(feature)
                }
            } else {
                if (feature != district) sb.append(feature)
            }
            val result = sb.toString()
            return if (result.length < 3) addr.getAddressLine(0) ?: "未知地址" else result
        }

        // ========================================================================
        // 字体与事件设置 (通用逻辑)
        // ========================================================================

        private fun setupClickEvents(context: Context, views: RemoteViews, appWidgetId: Int) {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_brand_logo, openAppPendingIntent)

            val refreshIntent =
                Intent(context, MGWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_update_time, refreshPendingIntent)

            val flipIntent = Intent(context, MGWidget::class.java).apply {
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
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews
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
            setTextSizeForIds(
                views,
                fontSizes[4],
                R.id.tv_total_mileage,
                R.id.tv_battery_info,
                R.id.tv_lock_status,
                R.id.tv_update_time
            )

            // 4. 详细信息 (门窗、温度、胎压) - 批量处理
            val detailIds = intArrayOf(
                R.id.tv_temp_label, R.id.tv_temp_value,
                R.id.tv_window_label, R.id.tv_window_value,
                R.id.tv_door_label, R.id.tv_door_value, R.id.tv_location,
                R.id.tv_front_left, R.id.tv_front_left_val,
                R.id.tv_rear_left, R.id.tv_rear_left_val,
                R.id.tv_front_right, R.id.tv_front_right_val,
                R.id.tv_rear_right, R.id.tv_rear_right_val,

                // 门窗详情 ID
                R.id.fl_window_label, R.id.fl_window_value, R.id.fl_door_label, R.id.fl_door_value,
                R.id.fr_window_label, R.id.fr_window_value, R.id.fr_door_label, R.id.fr_door_value,
                R.id.rl_window_label, R.id.rl_window_value, R.id.rl_door_label, R.id.rl_door_value,
                R.id.rr_window_label, R.id.rr_window_value, R.id.rr_door_label, R.id.rr_door_value,

                // 门窗组合 FrameLayout 容器不需要设字体，但 label/value 需要
                R.id.fl_window_door, R.id.fr_window_door, R.id.rl_window_door, R.id.rr_window_door
            )

            for (id in detailIds) {
                // 部分 ID 可能不是 TextView (如 Layout)，setTextViewTextSize 可能会忽略或报错，
                // 但为了代码简洁，通常 RemoteViews 宽容度较高。
                // 若要严谨，可排除 Layout ID。此处 fl_window_door 等是布局 ID，
                // 实际上 RemoteViews.setTextViewTextSize 对非 TextView 无效但不崩，可保留以防 XML 结构变更
                try {
                    views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
                } catch (e: Exception) {
                    // 忽略非 TextView 的 ID
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
    val rear_left_tyre_pressure: Int?,
    val battery_pack_range: Int?,
    val battery_pack_prc: Int?,
    val chrgng_rmnng_time: Int?,
    val charge_status: Int?
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