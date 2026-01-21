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
import com.google.gson.Gson
import com.my.mg.log.LogcatHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**          Power by 杨家三郎
 * MGWidget - 车辆状态桌面小组件的 AppWidgetProvider 实现类
 *
 * 这是 Android 桌面小组件的核心入口类，系统会在以下情况下调用它：
 * 1. 小组件被添加到桌面时（onUpdate）
 * 2. 小组件尺寸变化时（onAppWidgetOptionsChanged）
 * 3. 小组件收到广播事件时（onReceive）
 *
 * 注意：AppWidgetProvider 本质上是一个 BroadcastReceiver，
 *       因此所有回调都运行在主线程，但不能执行耗时操作。
 */
class MGWidget : AppWidgetProvider() {


    /**
     * onUpdate()
     *
     * 【作用】
     * AppWidgetProvider 的核心生命周期方法之一。
     * 系统会在以下情况调用：
     * 1. 小组件首次被添加到桌面
     * 2. 用户手动触发刷新（你在 ACTION_REFRESH 中调用了 onUpdate）
     * 3. 系统周期更新（如果 widget XML 中设置了 updatePeriodMillis）
     *
     * 【注意】
     * - 该方法运行在主线程，不能执行耗时操作（如网络请求）
     * - 因此这里只负责触发 updateAppWidget()，真正的网络请求在内部异步执行
     * - appWidgetIds 可能包含多个实例（用户可能在桌面放了多个相同的小组件）
     *
     * 【你当前逻辑】
     * - Debug 模式下启动日志记录
     * - 遍历所有小组件实例，逐个调用 updateAppWidget()
     * - updateAppWidget() 内部会根据配置决定是否发起网络请求
     */
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        if (BuildConfig.DEBUG) {
            LogcatHelper.startRecording(context)
        }
        log(context, "onUpdate called for IDs: ${appWidgetIds.joinToString()}")

        // 遍历所有小组件实例，逐个更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }


    /**
     * onAppWidgetOptionsChanged()
     *
     * 【作用】
     * 当用户调整桌面小组件的尺寸（例如从 4x2 拉到 4x3）时，
     * 系统会调用此方法，并传入新的尺寸参数 newOptions。
     *
     * 【为什么需要处理？】
     * 你的小组件在不同尺寸下字体大小不同（4x2 / 4x3），
     * 因此当尺寸变化时，需要重新渲染 UI。
     *
     * 【你当前逻辑】
     * - 打印日志
     * - 直接调用 updateAppWidget() 重新绘制整个小组件
     *
     * 【注意】
     * - 该方法运行在主线程
     * - 不适合执行耗时操作（如网络请求）
     * - updateAppWidget() 内部会异步拉取数据，因此这里是安全的
     */
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        // 打印尺寸变化日志
        log(context, "onAppWidgetOptionsChanged for ID: $appWidgetId")

        // 重新渲染小组件（包括字体大小、布局调整等）
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }


    /**
     * onReceive()
     *
     * 【作用】
     * AppWidgetProvider 继承自 BroadcastReceiver，
     * 因此所有广播事件（包括你自定义的 ACTION_REFRESH / ACTION_WIDGET_FLIP）
     * 都会先进入这个方法。
     *
     * 【系统行为】
     * - onReceive() 是小组件交互的核心入口
     * - 所有点击事件（PendingIntent）最终都会回到这里
     * - 运行在主线程，不允许执行耗时操作
     *
     * 【你当前处理的事件】
     * 1. ACTION_WIDGET_FLIP：切换 ViewFlipper（翻页）
     * 2. ACTION_REFRESH：手动刷新小组件
     *
     * 【注意】
     * - super.onReceive() 必须在前面调用，否则系统默认行为可能丢失
     * - RemoteViews 更新必须通过 AppWidgetManager.updateAppWidget()
     */
    override fun onReceive(context: Context, intent: Intent) {
        log(context, "onReceive called with action: ${intent.action}")

        // 调用父类处理（必须）
        super.onReceive(context, intent)

        // ============================
        // 1. 处理小组件翻页事件（ViewFlipper）
        // ============================
        if (ACTION_WIDGET_FLIP == intent.action) {

            // 获取小组件 ID（由 PendingIntent 传入）
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {

                // 获取 AppWidgetManager
                val appWidgetManager = AppWidgetManager.getInstance(context)

                // 创建 RemoteViews（注意：每次都必须重新创建）
                val views = RemoteViews(context.packageName, R.layout.mg_widget)

                // 切换 ViewFlipper 的下一个子布局
                // 这会在 mg_info_widget 与 mg_lock_widget 之间切换
                views.showNext(R.id.view_flipper_center)

                // 更新小组件 UI
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // ============================
            // 2. 处理手动刷新事件
            // ============================
        } else if (ACTION_REFRESH == intent.action) {

            // 获取所有小组件实例
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MGWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            // 用户点击“更新时间”后，先显示“正在更新...”提示
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.mg_widget)

                // 显示加载提示
                views.setTextViewText(R.id.tv_update_time, "正在更新....")

                // 立即更新 UI（让用户看到反馈）
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // 手动刷新最终还是调用 onUpdate()
            // 由 onUpdate() 再调用 updateAppWidget() → fetchVehicleData() → updateWidgetUI()
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

        /**
         * 检查网络连接是否可用
         */
        private fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }

        /**
         * updateAppWidget()
         *
         * 【作用】
         * 这是整个小组件更新流程的核心入口。
         * 每次需要刷新 UI（无论是系统触发、手动刷新、Worker 调用），最终都会走到这里。
         *
         * 【主要职责】
         * 1. 创建 RemoteViews（小组件 UI 的唯一可更新方式）
         * 2. 读取 SharedPreferences 中的车辆配置（VIN、车型、颜色、Token 等）
         * 3. 设置静态 UI（车名、车牌、Logo、车辆图片）
         * 4. 设置点击事件（打开 App、刷新、翻页）
         * 5. 根据配置决定是否发起网络请求
         * 6. 如果 VIN/Token 缺失 → 显示“请配置 App”
         *
         * 【注意】
         * - RemoteViews 必须每次重新创建，否则 UI 不会刷新
         * - 不能在这里执行耗时操作（网络请求），所以你调用 fetchVehicleData() 异步执行
         * - updateAppWidget() 是线程安全的，可以从 Worker 或主线程调用
         */
        internal fun updateAppWidget(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            log(context, "updateAppWidget called for ID: $appWidgetId")

            // 创建 RemoteViews（每次必须重新创建，否则系统可能忽略更新）
            val views = RemoteViews(context.packageName, R.layout.mg_widget)

            // 读取用户配置（VIN、车型、颜色、Token 等）
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val vin = prefs.getString("vin", "") ?: ""
            val carBrand = prefs.getString("car_brand", "名爵") ?: "名爵"
            val carModel = prefs.getString("car_model", "") ?: ""
            val color = prefs.getString("color", "") ?: ""
            val carName = prefs.getString("car_name", "") ?: ""
            val plateNumber = prefs.getString("plate_number", "") ?: ""
            val token = prefs.getString("access_token", "") ?: ""
            // *** 新增：读取图片 URL ***
            val carImageUrl = prefs.getString("car_image_url", "") ?: ""

            log(
                context,
                "Config - VIN: $vin, Brand: $carBrand, Model: $carModel, Color: $color, Token: ${
                    token.take(10)
                }..."
            )

            // ============================
            // 1. 设置静态 UI（车名、车牌）
            // ============================
            views.setTextViewText(
                R.id.tv_car_name, if (carName.isNullOrEmpty()) carModel else carName
            )
            views.setTextViewText(R.id.tv_plate_number, plateNumber)

            // ============================
            // 2. 设置品牌 Logo
            // ============================
            val logoResId = if (carBrand == "荣威") R.drawable.rw_logo else R.drawable.mg_logo
            views.setImageViewResource(R.id.iv_brand_logo, logoResId)

            // ============================
            // 3. 根据车型 + 颜色选择车辆图片
            // ============================
//            val colorMap = mapOf(
//                // MG7
//                "墨玉黑" to "black",
//                "釉瓷白" to "white",
//                "山茶红" to "red",
//                "雾凇灰" to "gray",
//                "翡冷翠" to "green",
//                "冰晶蓝" to "blue",
//                // MG4
//                "车来紫" to "purple",
//                "清波绿" to "green",
//                "海岛蓝" to "blue",
//                "珊瑚红" to "red",
//                "星野灰" to "gray",
//                "月光白" to "white",
//                // D7
//                "安第斯灰" to "gray",
//                "光速银" to "silver",
//                "晨曦金" to "gold",
//                "亮白" to "white",
//                "珠光黑" to "black"
//            )
//            val colorIdentifier = colorMap[color] ?: "default"
//            val modelIdentifier = carModel.lowercase(Locale.getDefault())
//            val imageName = "${colorIdentifier}_${modelIdentifier}"
//
//            // 动态查找 drawable 资源
//            val carImageResId =
//                context.resources.getIdentifier(imageName, "drawable", context.packageName)
//
//            if (carImageResId != 0) {
//                views.setImageViewResource(R.id.iv_car_image, carImageResId)
//            } else {
//                // 找不到对应图片 → 使用默认 Logo
//                views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
//            }

//            if (carImageUrl.isNotEmpty()) {
//                CoroutineScope(Dispatchers.IO).launch {
//                    val bitmap = downloadBitmap(carImageUrl)
//                    if (bitmap != null) {
//                        // 切回主线程更新 RemoteViews
//                        withContext(Dispatchers.Main) {
//                            views.setImageViewBitmap(R.id.iv_car_image, bitmap)
//                            // 必须再次调用 updateAppWidget 才能生效
//                            appWidgetManager.updateAppWidget(appWidgetId, views)
//                        }
//                    }
//                }
//            } else {
//                // 找不到对应图片 → 使用默认图片
//                views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
//            }

            // ============================
            // 4. 根据小组件尺寸调整字体大小
            // ============================
            adjustFontSizes(context, appWidgetManager, appWidgetId, views)

            // ============================
            // 5. 设置点击事件（打开 App）
            // ============================
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_brand_logo, openAppPendingIntent)

            // ============================
            // 6. 设置点击事件（手动刷新）
            // ============================
            val refreshIntent =
                Intent(context, MGWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_update_time, refreshPendingIntent)

            // ============================
            // 7. 设置点击事件（翻页 ViewFlipper）
            // ============================
            val flipIntent = Intent(context, MGWidget::class.java).apply {
                action = ACTION_WIDGET_FLIP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                flipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.view_flipper_center, pendingIntent)

            // ============================
            // 8. 决定是否发起网络请求
            // ============================
            if (vin.isNotEmpty() && token.isNotEmpty()) {
                // 异步拉取车辆数据
                fetchVehicleData(context, views, appWidgetManager, appWidgetId, vin, token)
            } else {
                // 未配置 → 显示提示
                views.setTextViewText(R.id.tv_update_time, "请配置 App")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            // --- 步骤 B: 如果有 URL，异步下载并覆盖 ---
            loadCarImageWithCache(
                context = context,
                views = views,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                carImageUrl = carImageUrl
            )
        }

        /**
         * 从本地缓存加载车辆图片；若本地不存在或 URL 变化则重新下载。
         * 整体流程：
         * 1. 根据 URL 生成唯一文件名（hash）
         * 2. 清理旧缓存，只保留当前 URL 对应的文件
         * 3. 若本地已有 → decodeFile
         * 4. 若本地没有 → 下载并保存 → decodeFile
         * 5. 主线程更新 RemoteViews
         */
        private fun loadCarImageWithCache(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            carImageUrl: String?
        ) {
            // URL 为空 → 使用默认图片
            if (carImageUrl.isNullOrEmpty()) {
                views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // IO 线程执行下载/读取逻辑，避免阻塞主线程
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 缓存目录：/data/data/包名/files/car_image_cache
                    val cacheDir = File(context.filesDir, "car_image_cache")
                    if (!cacheDir.exists()) cacheDir.mkdirs()

                    // 使用 URL 的 hashCode 作为文件名，确保唯一性
                    val fileName = carImageUrl.hashCode().toString() + ".img"
                    val imageFile = File(cacheDir, fileName)

                    // 清理旧文件：只保留当前 URL 对应的文件
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name != fileName) file.delete()
                    }

                    // bitmap：优先从本地读取；若不存在则下载
                    val bitmap = if (imageFile.exists()) {
                        // 本地已有 → 直接 decodeFile（避免 decodeStream 半图问题）
                        BitmapFactory.decodeFile(imageFile.absolutePath)
                    } else {
                        // 本地没有 → 下载并保存
                        downloadAndSaveImage(carImageUrl, imageFile)
                        // 下载完成后再 decodeFile，确保文件完整
                        BitmapFactory.decodeFile(imageFile.absolutePath)
                    }

                    // bitmap 成功 → 主线程更新 RemoteViews
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            views.setImageViewBitmap(R.id.iv_car_image, bitmap)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    } else {
                        // 解码失败 → 使用默认图
                        withContext(Dispatchers.Main) {
                            views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }

                } catch (e: Exception) {
                    // 任意异常 → 回退默认图
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        views.setImageViewResource(R.id.iv_car_image, R.drawable.blue_mg7)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }

        /**
         * 下载网络图片并保存到指定文件。
         * 关键点：
         * - 使用 BufferedInputStream + FileOutputStream
         * - copyTo() 确保完整写入
         * - decodeFile 时不会出现“只显示一半”的问题
         */
        private fun downloadAndSaveImage(urlStr: String, outFile: File) {
            val url = URL(urlStr)
            val connection = url.openConnection()
            connection.connect()

            // 输入流：网络数据
            val input = BufferedInputStream(connection.getInputStream())
            // 输出流：写入本地文件
            val output = FileOutputStream(outFile)

            // use{} 自动关闭流，避免泄漏
            input.use { inp ->
                output.use { out ->
                    // 将网络流完整写入文件
                    inp.copyTo(out)
                }
            }
        }

        /**
         * adjustFontSizes()
         *
         * 【作用】
         * 根据小组件当前尺寸（4x2 / 4x3）动态调整字体大小。
         *
         * 【为什么需要？】
         * - Android 桌面小组件的尺寸是可变的（用户可以拖动改变大小）
         * - 不同尺寸下显示空间不同，需要不同的字体大小以避免 UI 挤压或留白
         *
         * 【系统行为】
         * - 系统会在尺寸变化时调用 onAppWidgetOptionsChanged()
         * - 你在 onAppWidgetOptionsChanged() 中重新调用 updateAppWidget()
         * - updateAppWidget() 再调用本函数进行字体调整
         *
         * 【你当前逻辑】
         * 1. 读取 OPTION_APPWIDGET_MAX_HEIGHT 判断是否为“大尺寸”
         * 2. 根据尺寸选择不同的字体大小列表
         * 3. 逐个 TextView 设置字体大小
         *
         * 【注意】
         * - RemoteViews 不支持 setTextSize()，必须使用 setTextViewTextSize()
         * - 字体单位必须使用 TypedValue.COMPLEX_UNIT_SP
         */
        private fun adjustFontSizes(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews
        ) {
            // 获取当前小组件的尺寸信息
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)

            // 系统提供的最大高度（dp）
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            // 判断是否为“大尺寸”小组件（4x3）
            // 180dp 是你根据实际测试设定的阈值
            val isLarge = height > 180

            // 根据尺寸选择不同的字体大小
            val fontSizes = if (isLarge) {
                log(context, "Using LARGE font sizes for height: $height")
                // 4x3 小组件字体大小（你自定义的）
                listOf(13f, 12f, 11f, 10f, 10f, 10f)
            } else {
                log(context, "Using REGULAR font sizes for height: $height")
                // 4x2 小组件字体大小（来自你的 XML 默认值）
                listOf(18f, 13f, 12f, 11f, 10f, 8f)
            }

            // ============================
            // 以下为逐个 TextView 设置字体大小
            // ============================

            // 18sp → size18（或 13sp）
            views.setTextViewTextSize(R.id.tv_range, TypedValue.COMPLEX_UNIT_SP, fontSizes[0])
            views.setTextViewTextSize(
                R.id.tv_fuel_percent, TypedValue.COMPLEX_UNIT_SP, fontSizes[0]
            )
            views.setTextViewTextSize(
                R.id.tv_battery_range, TypedValue.COMPLEX_UNIT_SP, fontSizes[0]
            )
            views.setTextViewTextSize(
                R.id.tv_battery_percent, TypedValue.COMPLEX_UNIT_SP, fontSizes[0]
            )

            // 13sp → size13
            views.setTextViewTextSize(
                R.id.tv_plate_number, TypedValue.COMPLEX_UNIT_SP, fontSizes[1]
            )

            // 车名字体你强制设为 15sp，不随尺寸变化
            views.setTextViewTextSize(R.id.tv_car_name, TypedValue.COMPLEX_UNIT_SP, 15f)

            // 10sp → size10
            views.setTextViewTextSize(
                R.id.tv_total_mileage, TypedValue.COMPLEX_UNIT_SP, fontSizes[4]
            )
            views.setTextViewTextSize(
                R.id.tv_battery_info, TypedValue.COMPLEX_UNIT_SP, fontSizes[4]
            )
            views.setTextViewTextSize(R.id.tv_lock_status, TypedValue.COMPLEX_UNIT_SP, fontSizes[4])
            views.setTextViewTextSize(R.id.tv_update_time, TypedValue.COMPLEX_UNIT_SP, fontSizes[4])

            // 8sp → size8（或 10sp）
            views.setTextViewTextSize(R.id.tv_temp_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.tv_temp_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.tv_window_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(
                R.id.tv_window_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.tv_door_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.tv_door_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.tv_location, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])

            // ============================
            // mg_info_widget.xml 中的胎压布局
            // ============================
            views.setTextViewTextSize(R.id.tv_front_left, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.tv_front_left_val, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.tv_rear_left, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.tv_rear_left_val, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.tv_front_right, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.tv_front_right_val, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.tv_rear_right, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.tv_rear_right_val, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )

            // ============================
            // mg_lock_widget.xml 中的门窗布局
            // ============================

            // 主驾
            views.setTextViewTextSize(R.id.fl_window_door, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.fl_window_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(
                R.id.fl_window_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.fl_door_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.fl_door_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])

            // 左后
            views.setTextViewTextSize(R.id.rl_window_door, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.rl_window_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(
                R.id.rl_window_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.rl_door_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.rl_door_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])

            // 副驾
            views.setTextViewTextSize(R.id.fr_window_door, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.fr_window_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(
                R.id.fr_window_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.fr_door_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.fr_door_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])

            // 右后
            views.setTextViewTextSize(R.id.rr_window_door, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(
                R.id.rr_window_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(
                R.id.rr_window_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5]
            )
            views.setTextViewTextSize(R.id.rr_door_label, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
            views.setTextViewTextSize(R.id.rr_door_value, TypedValue.COMPLEX_UNIT_SP, fontSizes[5])
        }


        /**
         * fetchVehicleData()
         *
         * 【作用】
         * 这是小组件的数据获取核心函数。
         * 负责：
         * 1. 检查网络状态
         * 2. 构建请求 URL
         * 3. 使用 OkHttp 发起网络请求
         * 4. 解析 JSON（Gson）
         * 5. 切换回主线程更新 UI（updateWidgetUI）
         * 6. 处理各种网络异常并显示错误信息
         *
         * 【线程模型】
         * - 整个函数运行在 IO 线程（CoroutineScope(Dispatchers.IO)）
         * - UI 更新必须切回主线程（withContext(Dispatchers.Main)）
         *
         * 【注意】
         * - RemoteViews 更新必须在主线程执行，否则 UI 刷新可能被系统延迟
         * - OkHttp.execute() 是同步阻塞调用，因此必须放在 IO 线程
         */
        private fun fetchVehicleData(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            vin: String,
            token: String
        ) {
            log(context, "Fetching vehicle data for VIN: $vin")

            // 在 IO 线程执行网络请求
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // ============================
                    // 1. 网络可用性检查
                    // ============================
                    if (!isNetworkAvailable(context)) {
//                        withContext(Dispatchers.Main) {
//                            // 显示无网络提示
//                            views.setTextViewText(R.id.tv_location, "无网络")
//                            appWidgetManager.updateAppWidget(appWidgetId, views)
//                        }
                        return@launch
                    }

                    // ============================
                    // 2. 构建请求 URL
                    // ============================
                    val timestamp = System.currentTimeMillis() / 1000
                    val url =
                        "https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=$timestamp&token=$token&vin=$vin"

                    log(context, "Request URL: $url")

                    // ============================
                    // 3. 配置 OkHttp（超时 10 秒）
                    // ============================
                    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS) // 连接超时
                        .readTimeout(10, TimeUnit.SECONDS)    // 读取超时
                        .writeTimeout(10, TimeUnit.SECONDS)   // 写入超时
                        .build()

                    val request = Request.Builder().url(url).build()

                    // ============================
                    // 4. 发起同步请求（必须在 IO 线程）
                    // ============================
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    log(context, "Response Code: ${response.code}")
                    log(context, "Response Body: $responseBody")

                    // ============================
                    // 5. 解析 JSON
                    // ============================
                    if (responseBody != null) {
                        val gson = Gson()
                        val data = gson.fromJson(responseBody, VehicleStatusResponse::class.java)

                        // ============================
                        // 6. 切回主线程更新 UI
                        // ============================
                        withContext(Dispatchers.Main) {
                            updateWidgetUI(
                                context, views, data, appWidgetManager, appWidgetId
                            )
                        }
                    } else {
                        log(context, "Response body is null")
                    }

                } catch (e: Exception) {
                    // ============================
                    // 7. 异常处理（网络错误、超时等）
                    // ============================
                    log(context, "Error fetching data: ${e.message}")
                    e.printStackTrace()

                    // 根据异常类型显示不同提示
//                    val errorMsg = when (e) {
//                        is UnknownHostException -> "无法解析主机" // DNS 或域名错误
//                        is SocketTimeoutException -> "连接超时"     // 网络慢
//                        is ConnectException -> "连接失败"         // 握手失败
//                        else -> "更新出错"
//                    }
//
//                    // 切回主线程更新 UI
//                    withContext(Dispatchers.Main) {
//                        views.setTextViewText(R.id.tv_update_time, errorMsg)
//
//                        // 显示具体错误信息（方便调试）
//                        views.setTextViewText(R.id.tv_location, "${e.localizedMessage}")
//
//                        appWidgetManager.updateAppWidget(appWidgetId, views)
//                    }
                }
            }
        }

        /**
         * downloadBitmap()
         *
         * 【作用】
         * 下载网络图片并转换为 Bitmap。
         * 用于小组件显示自定义车辆图片。
         */
        private fun downloadBitmap(url: String): Bitmap? {
            return try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val inputStream = response.body?.byteStream()
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * updateWidgetUI()
         *
         * 【作用】
         * 这是整个小组件 UI 渲染的核心函数。
         * 负责将从服务器获取的车辆数据（VehicleStatusResponse）
         * 映射到 RemoteViews 上的所有控件。
         *
         * 【主要职责】
         * 1. 解析车辆状态数据（油量、电量、胎压、门窗、位置等）
         * 2. 根据数据动态显示/隐藏布局
         * 3. 设置颜色（红/绿/蓝）
         * 4. 设置进度条
         * 5. 设置文本内容
         * 6. 最终调用 updateAppWidget() 刷新 UI
         *
         * 【注意】
         * - RemoteViews 的所有 UI 更新必须在主线程执行
         * - RemoteViews 不支持复杂操作（如 setVisibility 动画），只能使用有限 API
         * - updateAppWidget() 是最终刷新 UI 的唯一方式
         */
        private fun updateWidgetUI(
            context: Context,
            views: RemoteViews,
            data: VehicleStatusResponse,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // 从服务器返回的数据结构中提取字段
            val vehicleValue = data.data?.vehicle_value
            val vehicleState = data.data?.vehicle_state
            val vehiclePosition = data.data?.vehicle_position
            val updateTime = data.data?.update_time ?: System.currentTimeMillis()

            log(
                context,
                "Updating UI with data: Mileage=${vehicleValue?.odometer}, Lock=${vehicleState?.lock}"
            )

            // 强制更新时间戳（用于触发 RemoteViews 重绘）
            views.setTextViewText(R.id.tv_update_time, "${System.currentTimeMillis()}")

            // ============================
            // 1. 总里程
            // ============================
            val mileage = vehicleValue?.odometer ?: 0
            views.setTextViewText(R.id.tv_total_mileage, "总里程: $mileage km")

            // ============================
            // 2. 油量、电量、续航
            // ============================
            val fuelLevel = vehicleValue?.fuel_level_prc ?: 0
            val fuelRange = vehicleValue?.fuel_range ?: 0
            val batteryPackRange = vehicleValue?.battery_pack_range ?: 0
            val batteryPackPrc = vehicleValue?.battery_pack_prc?.let { it / 10 } ?: 0
            var chrgngRmnngTime = vehicleValue?.chrgng_rmnng_time ?: 0.0
            var chargeStatus = vehicleValue?.charge_status ?: 0

            val showFuel = fuelRange > 0
            val showBattery = batteryPackRange > 0
            val showChargng = (chargeStatus != 1009) && (chargeStatus > 0)

            // 控制油量、电量布局显示/隐藏
            views.setViewVisibility(R.id.ll_range_fuel, if (showFuel) View.VISIBLE else View.GONE)
            views.setViewVisibility(
                R.id.ll_battery_range, if (showBattery) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.chrgng_rmnng_time, if (showChargng) View.VISIBLE else View.GONE
            )

            // ============================
            // 2.1 油量 UI
            // ============================
            if (showFuel) {
                views.setTextViewText(R.id.tv_range, "⛽$fuelRange")
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

            // ============================
            // 2.2 电量 UI
            // ============================
            if (showBattery) {
                views.setTextViewText(R.id.tv_battery_range, "🔋$batteryPackRange")
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

            // ============================
            // 2.3 充电剩余时间
            // ============================
            if (showChargng) {
                val h = chrgngRmnngTime / 60
                val m = chrgngRmnngTime % 60
                val timeText = when {
                    h > 0 && m > 0 -> "剩余${h}小时${m}分钟"
                    h > 0 -> "剩余${h}小时"
                    m > 0 -> "剩余${m}分钟"
                    else -> ""
                }

                views.setTextViewText(R.id.chrgng_rmnng_time, "⚡充电中$timeText")
                views.setTextColor(R.id.chrgng_rmnng_time, context.getColor(R.color.status_green))
            }

            // ============================
            // 3. 12V 电瓶电压
            // ============================
            val batteryLevelRaw = vehicleValue?.vehicle_battery_prc ?: 0
            val batteryVoltageRaw = vehicleValue?.vehicle_battery ?: 0
            val batteryLevel = batteryLevelRaw / 10
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

            // ============================
            // 4. 车辆锁状态
            // ============================
            val isLocked = vehicleState?.lock == true
            views.setTextViewText(R.id.tv_lock_status, if (isLocked) "已上锁" else "未上锁")
            views.setTextColor(
                R.id.tv_lock_status, if (isLocked) context.getColor(R.color.status_green)
                else context.getColor(R.color.status_red)
            )

            // ============================
            // 5. 更新时间显示（同一天显示 HH:mm）
            // ============================
            val updateDate = Date(updateTime)
            val now = Date()
            val sdfSameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val isSameDay = sdfSameDay.format(updateDate) == sdfSameDay.format(now)

            val displaySdf = if (isSameDay) SimpleDateFormat("HH:mm", Locale.getDefault())
            else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

            views.setTextViewText(R.id.tv_update_time, "${displaySdf.format(updateDate)} 更新")

            // ============================
            // 6. 车内温度
            // ============================
            val interiorTemp = vehicleValue?.interior_temperature ?: 0.0
            views.setTextViewText(R.id.tv_temp_value, String.format("%.1f°C", interiorTemp))
            val tempColorRes =
                if (interiorTemp <= 27.0) R.color.status_green else R.color.status_red
            views.setTextColor(R.id.tv_temp_value, context.getColor(tempColorRes))

            // ============================
            // 7. 胎压（四个轮胎）
            // ============================
            updateTirePressure(
                context, views, vehicleValue?.front_left_tyre_pressure, R.id.tv_front_left_val
            )
            updateTirePressure(
                context, views, vehicleValue?.front_right_tyre_pressure, R.id.tv_front_right_val
            )
            updateTirePressure(
                context, views, vehicleValue?.rear_left_tyre_pressure, R.id.tv_rear_left_val
            )
            updateTirePressure(
                context, views, vehicleValue?.rear_right_tyre_pressure, R.id.tv_rear_right_val
            )

            // ============================
            // 8. 门窗状态（总览 + 详细）
            // ============================
            if (vehicleState != null) {

                // 总览：所有窗是否关闭
                val allWindowsClosed =
                    !(vehicleState.driver_window == true || vehicleState.passenger_window == true || vehicleState.rear_left_window == true || vehicleState.rear_right_window == true || vehicleState.sunroof == true)

                views.setTextViewText(
                    R.id.tv_window_value, if (allWindowsClosed) "已关闭" else "未关闭"
                )
                views.setTextColor(
                    R.id.tv_window_value,
                    if (allWindowsClosed) context.getColor(R.color.status_green)
                    else context.getColor(R.color.status_red)
                )

                // 总览：所有门是否关闭
                val allDoorsClosed =
                    !(vehicleState.driver_door == true || vehicleState.passenger_door == true || vehicleState.rear_left_door == true || vehicleState.rear_right_door == true || vehicleState.bonnet == true || vehicleState.boot == true)

                views.setTextViewText(
                    R.id.tv_door_value, if (allDoorsClosed) "已关闭" else "未关闭"
                )
                views.setTextColor(
                    R.id.tv_door_value, if (allDoorsClosed) context.getColor(R.color.status_green)
                    else context.getColor(R.color.status_red)
                )

                // 详细门窗状态（mg_lock_widget）
                updateDoorOrWindowStatus(
                    context, views, vehicleState.driver_door == true, R.id.fl_door_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.passenger_door == true, R.id.fr_door_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.rear_left_door == true, R.id.rl_door_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.rear_right_door == true, R.id.rr_door_value
                )

                updateDoorOrWindowStatus(
                    context, views, vehicleState.driver_window == true, R.id.fl_window_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.passenger_window == true, R.id.fr_window_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.rear_left_window == true, R.id.rl_window_value
                )
                updateDoorOrWindowStatus(
                    context, views, vehicleState.rear_right_window == true, R.id.rr_window_value
                )
            }

            // ============================
            // 9. 位置（经纬度 → 地址）
            // ============================
            if (vehiclePosition != null) {
                val lat = vehiclePosition.latitude?.toDoubleOrNull()
                val long = vehiclePosition.longitude?.toDoubleOrNull()
                fetchLocation(context, views, appWidgetManager, appWidgetId, lat, long)
            } else {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            // ============================
            // 10. 强制刷新 RemoteViews（解决缓存问题）
            // ============================
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.view_flipper_center)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }


        /**
         * updateDoorOrWindowStatus()
         *
         * 【作用】
         * 用于更新单个门或窗的状态显示（开启 / 关闭）。
         *
         * 【为什么要单独封装？】
         * - 你的 mg_lock_widget.xml 中有 8 个门窗控件（主驾门、副驾门、左后门、右后门、四个窗）
         * - 这些控件的 UI 更新逻辑完全一致：文本 + 颜色
         * - 封装成函数可以避免重复代码，提高可维护性
         *
         * 【参数说明】
         * @param isOpen   Boolean → true 表示开启，false 表示关闭
         * @param viewId   Int → 需要更新的 TextView ID（如 R.id.fl_door_value）
         *
         * 【UI 规则】
         * - 开启 → 显示“开启”，颜色为红色（status_red）
         * - 关闭 → 显示“关闭”，颜色为绿色（status_green）
         *
         * 【注意】
         * - RemoteViews 不支持 setTextColor(int colorRes)，必须使用 context.getColor()
         * - RemoteViews 的所有 UI 更新必须在主线程执行（你已确保）
         */
        private fun updateDoorOrWindowStatus(
            context: Context, views: RemoteViews, isOpen: Boolean, viewId: Int
        ) {
            // 根据状态选择文本
            val text = if (isOpen) "开启" else "关闭"

            // 根据状态选择颜色
            val color = if (isOpen) context.getColor(R.color.status_red)
            else context.getColor(R.color.status_green)

            // 设置文本内容
            views.setTextViewText(viewId, text)

            // 设置文本颜色
            views.setTextColor(viewId, color)
        }


        /**
         * updateTirePressure()
         *
         * 【作用】
         * 渲染单个轮胎的胎压显示，包括：
         * - 数值格式化（xx.x Bar）
         * - 根据胎压范围设置颜色（正常为绿色，异常为红色）
         * - 处理胎压缺失（null）情况
         *
         * 【胎压规则】
         * - 正常范围：2.0 Bar ～ 3.0 Bar
         * - 小于 2.0 或大于 3.0 → 显示红色（异常）
         * - 正常范围 → 显示绿色
         *
         * 【RemoteViews 限制】
         * - 不能直接设置 Spannable，需要通过 setTextViewText() 传入 SpannableString
         * - setTextColor() 只能设置整段颜色，因此你使用 SpannableString 实现局部着色
         *
         * 【参数说明】
         * @param pressureRaw 服务器返回的胎压原始值（整数，单位：kPa × 10）
         * @param textViewId  需要更新的 TextView ID（如 R.id.tv_front_left_val）
         */
        private fun updateTirePressure(
            context: Context, views: RemoteViews, pressureRaw: Int?, textViewId: Int
        ) {
            // ============================
            // 1. 胎压缺失（null） → 显示 "- Bar"
            // ============================
            if (pressureRaw == null) {
                views.setTextViewText(textViewId, "- Bar")
                views.setTextColor(textViewId, context.getColor(R.color.status_red))
                return
            }

            // ============================
            // 2. 将原始胎压值转换为 Bar
            //    服务器返回值单位为：胎压 × 100
            //    例如：235 → 2.35 Bar
            // ============================
            val pressure = pressureRaw / 100.0

            // 格式化为 1 位小数
            val pressureString = String.format("%.1f", pressure)

            // 用于判断范围的 double 值
            val roundedPressure = pressureString.toDouble()

            // 完整显示文本，例如 "2.3 Bar"
            val fullText = "$pressureString Bar"

            // 使用 SpannableString 实现局部着色
            val spannable = SpannableString(fullText)

            // ============================
            // 3. 根据胎压范围设置颜色
            // ============================
            val colorRes = if (roundedPressure < 2.0 || roundedPressure > 3.0) R.color.status_red
            else R.color.status_green

            val color = context.getColor(colorRes)

            // ============================
            // 4. 给数值部分着色（不包括“Bar”）
            // ============================
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                pressureString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // ============================
            // 5. 更新 UI
            // ============================
            views.setTextViewText(textViewId, spannable)
        }

        /**
         * fetchLocation()
         *
         * 【作用】
         * 将车辆位置的经纬度（latitude / longitude）
         * 转换为可读的地址文本（如：四川省成都市武侯区…）。
         *
         * 【为什么单独封装？】
         * - 位置解析属于独立逻辑，与车辆状态无关
         * - 需要异步执行（Geocoder 可能耗时）
         * - 需要在主线程更新 RemoteViews
         *
         * 【注意事项】
         * - Geocoder 在部分国产 ROM 上可能失败（返回 null 或抛异常）
         * - 经纬度可能为空（服务器未上报）
         * - RemoteViews 更新必须在主线程执行
         * - 解析失败时应优雅降级（显示经纬度或“未知位置”）
         */
        private fun fetchLocation(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            latitude: Double?,
            longitude: Double?
        ) {
            // 经纬度为空 → 无法解析地址 → 直接更新 UI 并返回
            if (latitude == null || longitude == null) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // 打印日志，便于调试
            log(context, "Fetching location for lat=$latitude, long=$longitude")

            // 在 IO 线程执行 Geocoder（避免阻塞主线程）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Geocoder 解析地址也需要网络，因此先检查网络状态
                    if (!isNetworkAvailable(context)) {
                        // 网络不可用 → 切回主线程更新 UI（保持原样）
                        withContext(Dispatchers.Main) {
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                        return@launch
                    }

                    // 创建地理解析器（使用系统默认语言环境）
                    val geocoder = Geocoder(context, Locale.getDefault())
                    var address = ""

                    // 请求多个候选地址（3 个），提高解析成功率
                    val addresses = geocoder.getFromLocation(latitude, longitude, 3)

                    // 如果没有任何地址 → 使用默认提示
                    if (addresses.isNullOrEmpty()) address = "无法定位当前位置"

                    // 从候选地址中挑选“信息最丰富”的一个
                    // thoroughfare = 道路名
                    // subLocality = 子区域（如街道、镇）
                    val bestAddr = addresses?.maxByOrNull {
                        (if (it.thoroughfare != null) 10 else 0) + (if (it.subLocality != null) 5 else 0)
                    } ?: addresses?.firstOrNull()

                    // 如果找到最佳地址 → 格式化为更友好的文本
                    if (bestAddr != null) {
                        address = formatAddressSmart(bestAddr)
                        // 你原本的注释：在此处更新 UI 或变量（保持原样）
                    }

                    // 打印解析结果
                    log(context, "Addr result: $address")

                    // 如果地址非空 → 更新 UI
                    if (address != null && address.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            views.setTextViewText(R.id.tv_location, address)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }

                } catch (e: Exception) {
                    // 捕获 Geocoder 异常（国产 ROM 常见）
                    log(context, "Error fetching location: ${e.message}")
                    e.printStackTrace()

                    // 切回主线程更新 UI（保持原样）
                    withContext(Dispatchers.Main) {
                        // 地址解析失败通常不需要强提示，只需保持 UI 更新
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }
    }
}

/**
 * 格式化地址：处理“鹏程路鹏程路111号”这种重复情况
 *
 * 【作用】
 * - 对 Geocoder 返回的 Address 进行智能格式化
 * - 去除重复字段（如道路名重复）
 * - 拼接省、市、区、道路、门牌号
 * - 避免出现“上海市上海市浦东新区”这种重复
 *
 * 【保持原样】
 * - 以下代码完全保持你的逻辑，只添加注释
 */
private fun formatAddressSmart(addr: Address): String {
    val sb = StringBuilder()

    // 1. 省、市、区（加入去重逻辑）
    val admin = addr.adminArea ?: ""      // 省
    val city = addr.locality ?: ""        // 市
    val district = addr.subLocality ?: "" // 区/街道

    sb.append(admin)
    if (city != admin) sb.append(city) // 避免“上海市上海市”
    sb.append(district)

    // 2. 道路与门牌号（核心去重逻辑）
    val road = addr.thoroughfare ?: ""   // 道路名，如“鹏程路”
    val feature = addr.featureName ?: "" // 可能包含门牌号，如“鹏程路111号”

    if (road.isNotEmpty()) {
        sb.append(road)

        // 如果 featureName 包含 road → 截取 road 后面的部分（如“111号”）
        if (feature.contains(road) && feature != road) {
            sb.append(feature.substringAfter(road))
        } else if (feature != road && feature != district) {
            // 如果不重复 → 直接拼接
            sb.append(feature)
        }
    } else {
        // 没有道路名 → 尝试拼接 featureName
        if (feature != district) sb.append(feature)
    }

    val result = sb.toString()

    // 兜底逻辑：如果拼接结果太短（如“上海市”），则返回完整地址行
    return if (result.length < 3) addr.getAddressLine(0) ?: "未知地址"
    else result
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
    val chrgng_rmnng_time: Double?,
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