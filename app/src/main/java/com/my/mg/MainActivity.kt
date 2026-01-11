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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
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

    private val giteeApiUrl = "https://gitee.com/api/v5/repos/yangyachao-X/mg-liner/releases/latest"
    private val client = OkHttpClient()
    private val gson = Gson()

    private var showUpdateDialog by mutableStateOf(false)
    private var releaseToUpdate by mutableStateOf<GiteeRelease?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
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
                val request = Request.Builder().url(giteeApiUrl).build()
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

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    unregisterReceiver(this)
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), asset.name)
                    if (file.exists()) {
                         val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(this@MainActivity, "${this@MainActivity.packageName}.provider", file)
                        } else {
                            Uri.fromFile(file)
                        }
                        installApk(fileUri)
                    } else {
                         Toast.makeText(this@MainActivity, "下载失败，文件未找到", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
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

    var vin by remember { mutableStateOf(sharedPreferences.getString("vin", "") ?: "") }
    var color by remember { mutableStateOf(sharedPreferences.getString("color", "") ?: "") }
    var carName by remember { mutableStateOf(sharedPreferences.getString("car_name", "") ?: "") }
    var plateNumber by remember { mutableStateOf(sharedPreferences.getString("plate_number", "") ?: "") }
    var accessToken by remember { mutableStateOf(sharedPreferences.getString("access_token", "") ?: "") }
    var isConfigured by remember { mutableStateOf(sharedPreferences.getBoolean("is_configured", false)) }

    val vinFocusRequester = remember { FocusRequester() }
    val accessTokenFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "MG7 小组件配置",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(20.dp))

        InputField(
            label = "请输入您的车架号(VIN):",
            value = vin,
            onValueChange = { vin = it },
            modifier = Modifier.focusRequester(vinFocusRequester)
        )

        ColorDropdownField(
            label = "请选择车辆颜色:",
            selectedColor = color,
            onColorSelected = { color = it }
        )

        InputField(label = "请输入您的车辆名称 (选填) :", value = carName, onValueChange = { carName = it })
        InputField(label = "请输入您的车牌号 (选填) :", value = plateNumber, onValueChange = { plateNumber = it })
        InputField(
            label = "请输入您的 ACCESS_TOKEN:",
            value = accessToken,
            onValueChange = { accessToken = it },
            modifier = Modifier.focusRequester(accessTokenFocusRequester),
            singleLine = false
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (vin.isBlank()) {
                    Toast.makeText(context, "请输入车架号", Toast.LENGTH_SHORT).show()
                    vinFocusRequester.requestFocus()
                    return@Button
                }
                if (accessToken.isBlank()) {
                    Toast.makeText(context, "请输入ACCESS_TOKEN", Toast.LENGTH_SHORT).show()
                    accessTokenFocusRequester.requestFocus()
                    return@Button
                }
                val editor = sharedPreferences.edit()
                editor.putString("vin", vin)
                editor.putString("color", color)
                editor.putString("car_name", carName)
                editor.putString("plate_number", plateNumber)
                editor.putString("access_token", accessToken)
                editor.putBoolean("is_configured", true)
                editor.apply()
                isConfigured = true
                Toast.makeText(context, "保存成功，小组件将在稍后更新", Toast.LENGTH_SHORT).show()

                // 触发小组件更新
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text(text = "保存并更新小组件", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "当前状态: ${if (isConfigured) "已配置" else "未配置"}",
            modifier = Modifier.align(Alignment.Start),
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Power By 杨家三郎\n纯属娱乐免费，请勿较真",
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 16.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckUpdate() }
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDropdownField(
    label: String,
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val options = listOf("墨玉黑", "釉瓷白", "山茶红", "雾凇灰", "翡冷翠", "冰晶蓝")
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
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
    singleLine: Boolean = true
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine
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
