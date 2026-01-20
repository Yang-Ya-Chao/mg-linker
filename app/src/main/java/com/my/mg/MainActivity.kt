package com.my.mg

import android.app.DownloadManager
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.my.mg.config.CarConfig
import com.my.mg.config.RemoteConfig
import com.my.mg.log.LogcatHelper
import com.my.mg.ui.theme.MGLinkerTheme
import com.my.mg.worker.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

// Data class for Gitee Release API response
data class GiteeRelease(
    val tag_name: String,
    val body: String,
    val assets: List<GiteeAsset>
)

data class GiteeAsset(
    val browser_download_url: String,
    val name: String
)

/**
 * 切换 App 图标
 * @param context 上下文
 * @param isMg true 为名爵，false 为荣威
 */
fun changeAppIcon(context: Context, isMg: Boolean) {
    val pm = context.packageManager
    // 获取组件名称，必须与 Manifest 中的 android:name 完全一致（包含包名）
    val componentNameMG = ComponentName(context, "com.my.mg.MainActivityMG")
    val componentNameRW = ComponentName(context, "com.my.mg.MainActivityRW")

    // 检查当前状态，避免重复操作（重复操作会导致应用闪退或图标闪烁）
    val isMgEnabled = pm.getComponentEnabledSetting(componentNameMG) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val isRwEnabled = pm.getComponentEnabledSetting(componentNameRW) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    if (isMg && isMgEnabled) return // 已经是名爵图标，无需更改
    if (!isMg && isRwEnabled) return // 已经是荣威图标，无需更改

    // 启用目标图标，禁用另一个
    // DONT_KILL_APP 标志位尽量防止应用被系统强制杀掉，但在某些机型上切换图标应用仍可能会重启
    if (isMg) {
        pm.setComponentEnabledSetting(componentNameMG, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(componentNameRW, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    } else {
        pm.setComponentEnabledSetting(componentNameRW, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(componentNameMG, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}

// 在 MainActivity 内部或外部定义
suspend fun fetchCarConfig(client: OkHttpClient, gson: Gson): List<CarConfig>? {
    // ！！！关键：必须使用 raw 地址，而不是 blob 地址 ！！！
    val configUrl = "https://gitee.com/yangyachao-X/mg-linker/raw/master/other/config.json"

    return withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(configUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (!json.isNullOrEmpty()) {
                        val remoteConfig = gson.fromJson(json, RemoteConfig::class.java)
                        remoteConfig.saic
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class MainActivity : ComponentActivity() {

    private val giteeApiUrl = "https://gitee.com/api/v5/repos/yangyachao-X/mg-linker/releases/latest"
    private val client = OkHttpClient()
    private val gson = Gson()

    private var showUpdateDialog by mutableStateOf(false)
    private var releaseToUpdate by mutableStateOf<GiteeRelease?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程启动时开始记录日志
        if (BuildConfig.DEBUG){
          LogcatHelper.startRecording(this)
        }
        scheduleWidgetWork()
        enableEdgeToEdge()
        setContent {
            MGLinkerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MGConfigScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCheckUpdate = {
                            checkUpdate(manual = true)
                        }
                    )

                    if (showUpdateDialog) {
                        releaseToUpdate?.let { release ->
                            UpdateDialog(
                                release = release,
                                onConfirm = {
                                    val apkAsset = release.assets.firstOrNull { it.name == "MG Linker.apk" }
                                    if (apkAsset != null) {
                                        downloadAndInstallApk(apkAsset)
                                    }
                                    showUpdateDialog = false
                                    releaseToUpdate = null
                                },
                                onDismiss = {
                                    showUpdateDialog = false
                                    releaseToUpdate = null
                                }
                            )
                        }
                    }
                }
            }
        }
        //checkUpdate(manual = false) // Automatic check

    }
    private fun scheduleWidgetWork()
    {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            30, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 需要网络
                .build() )
            .build()
        WorkManager.getInstance(this) .enqueueUniquePeriodicWork(
            "MGWidgetPeriodicUpdate", ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
    private fun checkUpdate(manual: Boolean) {
        Log.d("UpdateCheck", "Starting update check (manual: $manual)")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(giteeApiUrl).get()
                //需要自己配置token，否则请求会被拒
                val token = BuildConfig.GITEE_API_TOKEN
                if (token.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "token $token")
                    Log.d("UpdateCheck", "Authorization token added.")
                }

                val request = requestBuilder.build()
                Log.d("UpdateCheck", "Requesting URL: $giteeApiUrl")
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("UpdateCheck", "Request failed with code: ${response.code}")
                        if (manual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "检查更新失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@launch
                    }

                    val responseBody = response.body?.string()
                    Log.d("UpdateCheck", "Response body: $responseBody")

                    if (responseBody.isNullOrEmpty()) {
                        Log.e("UpdateCheck", "Response body is null or empty.")
                        if(manual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "检查更新失败: 响应为空", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@launch
                    }

                    val release = gson.fromJson(responseBody, GiteeRelease::class.java)
                    val latestVersion = release.tag_name
                    val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
                    Log.d("UpdateCheck", "Latest version: $latestVersion, Current version: $currentVersion")


                    if (isNewerVersion(latestVersion, currentVersion)) {
                        Log.d("UpdateCheck", "Newer version found.")
                        withContext(Dispatchers.Main) {
                            releaseToUpdate = release
                            showUpdateDialog = true
                        }
                    } else {
                        Log.d("UpdateCheck", "Already on the latest version.")
                        if (manual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "已经是最新版本", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateCheck", "An error occurred during update check", e)
                if (manual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "检查更新出错: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latest = latestVersion.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val current = currentVersion.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val maxParts = maxOf(latest.size, current.size)
        for (i in 0 until maxParts) {
            val latestPart = latest.getOrElse(i) { 0 }
            val currentPart = current.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private fun downloadAndInstallApk(asset: GiteeAsset) {
        Toast.makeText(this, "开始下载更新...", Toast.LENGTH_SHORT).show()
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), asset.name)
        if (destination.exists()) {
            destination.delete()
        }
        val request = DownloadManager.Request(Uri.parse(asset.browser_download_url))
            .setTitle("MG Linker 更新")
            .setDescription("正在下载 ${asset.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            .setMimeType("application/vnd.android.package-archive")
            .setRequiresCharging(false)
            .setAllowedOverMetered(true)

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    unregisterReceiver(this)
                    val uri = downloadManager.getUriForDownloadedFile(id)
                    if (uri != null) {
                        installApk(uri)
                    } else {
                        Toast.makeText(this@MainActivity, "下载失败，无法获取文件URI", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        // For Android 8.0 (API 26) and higher, you need to declare the receiver for exported=true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(uri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun UpdateDialog(
    release: GiteeRelease,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本: ${release.tag_name}") },
        text = { Text(release.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun MGConfigScreen(modifier: Modifier = Modifier, onCheckUpdate: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("mg_config", Context.MODE_PRIVATE) }
    val uriHandler = LocalUriHandler.current
    // 网络请求相关依赖
    val client = remember { OkHttpClient() }
    val gson = remember { Gson() }

    // ================== 1. 状态定义 ==================
    // 远程配置数据状态
    var carConfigList by remember { mutableStateOf<List<CarConfig>>(emptyList()) }
    var isLoadingConfig by remember { mutableStateOf(true) }

    //用户选择状态
    var carBrand by remember { mutableStateOf(sharedPreferences.getString("car_brand", "名爵") ?: "名爵") }
    var carModel by remember { mutableStateOf(sharedPreferences.getString("car_model", "") ?: "") }
    var carName by remember { mutableStateOf(sharedPreferences.getString("car_name", "") ?: "") }
    var vin by remember { mutableStateOf(sharedPreferences.getString("vin", "") ?: "") }
    var color by remember { mutableStateOf(sharedPreferences.getString("color", "") ?: "") }
    var plateNumber by remember { mutableStateOf(sharedPreferences.getString("plate_number", "") ?: "") }
    var accessToken by remember { mutableStateOf(sharedPreferences.getString("access_token", "") ?: "") }
    var isConfigured by remember { mutableStateOf(sharedPreferences.getBoolean("is_configured", false)) }

    val vinFocusRequester = remember { FocusRequester() }
    val accessTokenFocusRequester = remember { FocusRequester() }
    // VIN 校验状态
    val isVinValid = vin.length == 17
    // 当有输入但长度不对时，显示错误
    val isVinError = vin.isNotEmpty() && !isVinValid
    // ================== 2. 数据获取与处理逻辑 ==================

    // 初始化加载配置
    LaunchedEffect(Unit) {
        val config = fetchCarConfig(client, gson)
        if (config != null) {
            carConfigList = config
        } else {
            Toast.makeText(context, "获取车型配置失败，请检查网络", Toast.LENGTH_SHORT).show()
        }
        isLoadingConfig = false
    }

    // 辅助函数：将中文品牌映射到 JSON 中的 brand 代码 ("MG", "RW")
    fun getBrandCode(cnName: String): String {
        return if (cnName == "名爵") "MG" else "RW"
    }

    // 计算当前品牌下的所有车型
    val availableModels = remember(carBrand, carConfigList) {
        val code = getBrandCode(carBrand)
        carConfigList.filter { it.brand == code }.map { it.model }
    }

    // 计算当前车型下的所有颜色对象
    val currentModelConfig = remember(carBrand, carModel, carConfigList) {
        val code = getBrandCode(carBrand)
        carConfigList.find { it.brand == code && it.model == carModel }
    }

    val availableColors = remember(currentModelConfig) {
        currentModelConfig?.colors?.map { it.name } ?: emptyList()
    }

    // 级联选择逻辑：品牌变了，重置车型；车型变了，重置颜色
    // 关键修复：加入 isLoadingConfig 判断，防止在数据加载前清空本地已保存的配置
    LaunchedEffect(availableModels, isLoadingConfig) {
        // 如果正在加载配置，不要执行重置逻辑，保留 SharedPreferences 读取的值
        if (isLoadingConfig) return@LaunchedEffect

        if (carModel.isNotEmpty() && !availableModels.contains(carModel)) {
            // 如果当前选中的车型不在列表中（且不是因为正在加载），则重置
            carModel = availableModels.firstOrNull() ?: ""
        } else if (carModel.isEmpty() && availableModels.isNotEmpty()) {
            // 如果没选车型，默认选第一个
            carModel = availableModels.firstOrNull() ?: ""
        }
    }

    LaunchedEffect(availableColors, isLoadingConfig) {
        // 如果正在加载配置，不要执行重置逻辑
        if (isLoadingConfig) return@LaunchedEffect

        if (color.isNotEmpty() && !availableColors.contains(color)) {
            // 如果当前选中的颜色不在列表中（且不是因为正在加载），则重置
            color = availableColors.firstOrNull() ?: ""
        } else if (color.isEmpty() && availableColors.isNotEmpty()) {
            // 如果没选颜色，默认选第一个
            color = availableColors.firstOrNull() ?: ""
        }
    }

    // 获取滚动状态
    val scrollState = rememberScrollState()
    // 最外层容器
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ================== 1. 顶部固定区域 (Header) ==================
        Text(
            text = "上汽小组件配置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // 品牌选择器放在这里，它就不会滚动了
        BrandSelector(selectedBrand = carBrand, onBrandSelected = { carBrand = it })
        // 加一条分割线或者间距，让视觉分离更明显（可选）
       // Spacer(modifier = Modifier.height(4.dp))
        // ================== 2. 中间滚动区域 (Content) ==================
        Column(
            modifier = Modifier
                .weight(1f) // 关键：占据上下固定区域之外的所有剩余空间
                .fillMaxWidth()
                .verticalScroll(scrollState), // 关键：只允许这一块滚动
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoadingConfig) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("正在加载车型数据...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            } else {
                ModelDropdownField(
                    label = "请选择车型",
                    selectedModel = carModel,
                    onModelSelected = { carModel = it },
                    models = availableModels // 使用动态计算的列表
                )
            }

        InputField(label = "车辆名称 (选填)", value = carName, onValueChange = { carName = it })

        // *** VIN 输入框修改 ***
        InputField(
            label = "请输入您的车架号(VIN):",
            value = vin,
            onValueChange = { input ->
                // 1. 转大写
                // 2. 过滤非数字和非大写字母
                // 3. 限制长度为17
                val formatted = input.uppercase()
                    .filter { it.isDigit() || it in 'A'..'Z' }
                    .take(17)
                vin = formatted
            },
            modifier = Modifier.focusRequester(vinFocusRequester),
            isError = isVinError,
            supportingText = {
                if (isVinError) {
                    Text("需输入17位，当前: ${vin.length}", color = MaterialTheme.colorScheme.error)
                }
            }
        )
        ColorDropdownField(
            label = "请选择车辆颜色:",
            selectedColor = color,
            onColorSelected = { color = it },
            options = availableColors // 使用动态计算的列表
        )

        InputField(label = "请输入您的车牌号 (选填) :", value = plateNumber, onValueChange = { plateNumber = it })

        InputField(
            label = "请输入您的 ACCESS_TOKEN:",
            value = accessToken,
            onValueChange = { accessToken = it },
            modifier = Modifier.focusRequester(accessTokenFocusRequester),
            singleLine = false,
            onHelpClick = {
                uriHandler.openUri("https://gitee.com/yangyachao-X/mg-linker/blob/master/README.md")
            }
        )
            // 底部留一点空白，防止滚动到底时内容贴着按钮
            Spacer(modifier = Modifier.height(8.dp))
        }
        // ================== 3. 底部固定区域 (Footer) ==================
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 给按钮上方加一点间距
            Spacer(modifier = Modifier.height(2.dp))
        Button(
            onClick = {
                // *** 增加 VIN 校验 ***
                if (!isVinValid) {
                    Toast.makeText(context, "请输入正确的17位车架号", Toast.LENGTH_SHORT).show()
                    vinFocusRequester.requestFocus()
                    return@Button
                }
                if (accessToken.isBlank()) {
                    Toast.makeText(context, "请输入ACCESS_TOKEN", Toast.LENGTH_SHORT).show()
                    accessTokenFocusRequester.requestFocus()
                    return@Button
                }
                if (!accessToken.endsWith("-prod_SAIC")){
                    Toast.makeText(context, "ACCESS_TOKEN不正确，请点击输入框上方帮助按钮ⓘ查看抓包教程", Toast.LENGTH_SHORT).show()
                    accessTokenFocusRequester.requestFocus()
                    return@Button
                }
                // *** 新增：查找并保存图片 URL ***
                val selectedColorObj = currentModelConfig?.colors?.find { it.name == color }
                val imageUrl = selectedColorObj?.imageUrl ?: ""

                val editor = sharedPreferences.edit()
                editor.putString("car_brand", carBrand)
                editor.putString("car_model", carModel)
                editor.putString("car_name", carName)
                editor.putString("vin", vin)
                editor.putString("color", color)
                editor.putString("plate_number", plateNumber)
                editor.putString("access_token", accessToken)
                // 保存图片地址到本地
                editor.putString("car_image_url", imageUrl)
                editor.putBoolean("is_configured", true)
                editor.apply()
                isConfigured = true
                // --- 新增代码开始 ---
                // 根据选择的品牌切换图标
                // 注意：切换图标可能会导致应用短暂退出或桌面刷新，这是系统机制
                                try {
                                    val isMgBrand = carBrand == "名爵"
                                    changeAppIcon(context, isMgBrand)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // 可以在这里加个 Toast 提示用户图标切换可能需要重启生效
                                }
                // --- 新增代码结束 ---

                Toast.makeText(context, "保存成功，app将在稍后更新", Toast.LENGTH_SHORT).show()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val myProvider = ComponentName(context, MGWidget::class.java)
                if (appWidgetManager.getAppWidgetIds(myProvider).isEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
                        try {
                            appWidgetManager.requestPinAppWidget(myProvider, null, null)
                        } catch (e: Exception) {
                            // Handle exception
                        }
                    }
                }
                val intent = Intent(context, MGWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, MGWidget::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(text = "保存并更新小组件", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "当前状态: ${if (isConfigured) "已配置" else "未配置"}",
            modifier = Modifier.align(Alignment.Start),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Power By 杨家三郎\n娱乐免费，请勿较真",
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckUpdate() }
        )
    }}
}

@Composable
fun BrandSelector(
    selectedBrand: String,
    onBrandSelected: (String) -> Unit
) {
    val brands = listOf("名爵", "荣威")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        brands.forEach { brand ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onBrandSelected(brand) }
                    .padding(horizontal = 16.dp)
            ) {
                RadioButton(
                    selected = selectedBrand == brand,
                    onClick = { onBrandSelected(brand) }
                )
                Text(
                    text = brand,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdownField(
    label: String,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    models: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                value = selectedModel,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDropdownField(
    label: String,
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    options: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                value = selectedColor,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onColorSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) { // Adjusted padding

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp) // Adjusted padding
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onHelpClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onHelpClick() }
                )
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = isError,
            supportingText = supportingText,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MGConfigScreenPreview() {
    MGLinkerTheme {
        MGConfigScreen(onCheckUpdate = {})
    }
}
