package com.my.mg

import android.app.DownloadManager
import android.appwidget.AppWidgetManager
import android.content.*
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
import androidx.compose.foundation.text.KeyboardOptions
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
import com.google.gson.Gson
import com.my.mg.log.LogcatHelper
import com.my.mg.ui.theme.MGLinkerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

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
        enableEdgeToEdge()
        setContent {
            MGLinkerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MGConfigScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCheckUpdate = { checkUpdate(manual = true) }
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
        checkUpdate(manual = false) // Automatic check
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
    val modelsByBrand = mapOf(
        "名爵" to listOf("MG7", "MG4"),
        "荣威" to listOf("D7")
    )

    val colorsByModel = mapOf(
        "MG7" to listOf("墨玉黑", "釉瓷白", "山茶红", "雾凇灰", "翡冷翠", "冰晶蓝"),
        "MG4" to listOf("车来紫", "清波绿", "海岛蓝", "珊瑚红", "星野灰", "月光白"),
        "D7" to listOf("安第斯灰", "光速银", "晨曦金", "亮白", "珠光黑")
    )

    LaunchedEffect(carBrand) {
        modelsByBrand[carBrand]?.firstOrNull()?.let { firstModel ->
            if (carModel.isEmpty() || modelsByBrand[carBrand]?.contains(carModel) == false) {
                carModel = firstModel
            }
        }
    }
    
    LaunchedEffect(carModel) {
        val availableColors = colorsByModel[carModel] ?: emptyList()
        if (color.isEmpty() || !availableColors.contains(color)) {
            color = availableColors.firstOrNull() ?: ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "上汽小组件配置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrandSelector(selectedBrand = carBrand, onBrandSelected = { carBrand = it })

        ModelDropdownField(
            label = "请选择车型",
            selectedModel = carModel,
            onModelSelected = { carModel = it },
            models = modelsByBrand[carBrand] ?: emptyList()
        )

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
            options = colorsByModel[carModel] ?: emptyList()
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

        Spacer(modifier = Modifier.weight(1f))

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
                val editor = sharedPreferences.edit()
                editor.putString("car_brand", carBrand)
                editor.putString("car_model", carModel)
                editor.putString("car_name", carName)
                editor.putString("vin", vin)
                editor.putString("color", color)
                editor.putString("plate_number", plateNumber)
                editor.putString("access_token", accessToken)
                editor.putBoolean("is_configured", true)
                editor.apply()
                isConfigured = true
                Toast.makeText(context, "保存成功，小组件将在稍后更新", Toast.LENGTH_SHORT).show()
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
    }
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
            modifier = Modifier.padding(bottom = 4.dp)
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
