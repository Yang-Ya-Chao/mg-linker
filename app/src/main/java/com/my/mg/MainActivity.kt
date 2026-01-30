package com.my.mg

import android.app.DownloadManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.my.mg.data.GiteeAsset
import com.my.mg.ui.LogViewerScreen
import com.my.mg.ui.MGConfigScreen
import com.my.mg.ui.UpdateDialog
import com.my.mg.ui.theme.MGLinkerTheme
import com.my.mg.worker.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

// 切换 App 图标
fun changeAppIcon(context: Context, isMg: Boolean) {
    val pm = context.packageManager
    val componentNameMG = ComponentName(context, "com.my.mg.MainActivityMG")
    val componentNameRW = ComponentName(context, "com.my.mg.MainActivityRW")

    val isMgEnabled =
        pm.getComponentEnabledSetting(componentNameMG) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val isRwEnabled =
        pm.getComponentEnabledSetting(componentNameRW) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    if (isMg && isMgEnabled) return
    if (!isMg && isRwEnabled) return

    if (isMg) {
        pm.setComponentEnabledSetting(
            componentNameMG,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            componentNameRW,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    } else {
        pm.setComponentEnabledSetting(
            componentNameRW,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            componentNameMG,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleWidgetWork()
        enableEdgeToEdge()

        setContent {
            MGLinkerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background // 确保背景色一致
                ) { innerPadding ->

                    // 收集 ViewModel 状态
                    val uiState by viewModel.uiState.collectAsState()
                    var showDialog by remember { mutableStateOf(false) }

                    // --- Pager Setup ---
                    val pagerState = rememberPagerState(pageCount = { 2 })

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {

                        // 1. 滑动页面主体
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> {
                                    // 原始配置页面
                                    MGConfigScreen(
                                        uiState = uiState,
                                        onUpdateInput = viewModel::updateInput,
                                        onSave = {
                                            if (viewModel.saveConfig(this@MainActivity)) {
                                                updateWidgetsAndIcon(uiState.carBrand == "名爵")
                                            }
                                        },
                                        onCheckUpdate = {
                                            if (uiState.isUpdateAvailable) {
                                                showDialog = true
                                            } else {
                                                viewModel.checkUpdate(manual = true)
                                            }
                                        }
                                    )
                                }

                                1 -> {
                                    // 新增日志页面
                                    LogViewerScreen()
                                }
                            }
                        }
                    }

                    // 更新弹窗逻辑 (保持不变)
                    if (showDialog && uiState.releaseInfo != null) {
                        UpdateDialog(
                            release = uiState.releaseInfo!!,
                            isDownloading = uiState.isDownloading,
                            progress = uiState.downloadProgress,
                            onConfirm = {
                                val apkAsset =
                                    uiState.releaseInfo!!.assets.firstOrNull { it.name.endsWith(".apk") }
                                if (apkAsset != null) {
                                    startDownload(apkAsset)
                                }
                            },
                            onDismiss = { showDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun updateWidgetsAndIcon(isMg: Boolean) {
        try {
            changeAppIcon(this, isMg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Toast.makeText(this, "保存成功，app将在稍后更新", Toast.LENGTH_SHORT).show()

        // 发送 Widget 更新广播
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val myProvider = ComponentName(this, MGWidget::class.java)
        if (appWidgetManager.getAppWidgetIds(myProvider).isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
                try {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                } catch (e: Exception) {
                }
            }
        }
        val intent = Intent(this, MGWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = appWidgetManager.getAppWidgetIds(myProvider)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun startDownload(asset: GiteeAsset) {
        val downloadId = downloadAndInstallApk(asset)
        viewModel.updateDownloadState(true, 0f)

        lifecycleScope.launch(Dispatchers.IO) {
            observeDownloadProgress(
                downloadId = downloadId,
                onProgress = { p -> viewModel.updateDownloadState(true, p) },
                onFinish = { viewModel.updateDownloadState(false, 1f) },
                onFailed = {
                    viewModel.updateDownloadState(false, 0f)
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "下载失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    private fun scheduleWidgetWork() {
        val request =
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MGWidgetPeriodicUpdate", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    private suspend fun observeDownloadProgress(
        downloadId: Long,
        onProgress: (Float) -> Unit,
        onFinish: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val downloadManager =
            getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloading = true

        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val downloadedIndex =
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex =
                    cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                if (downloadedIndex != -1 && totalIndex != -1 && statusIndex != -1) {
                    val bytesDownloaded = cursor.getInt(downloadedIndex)
                    val bytesTotal = cursor.getInt(totalIndex)
                    val status = cursor.getInt(statusIndex)

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false
                        withContext(Dispatchers.Main) {
                            onProgress(1.0f)
                            onFinish()
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                        withContext(Dispatchers.Main) { onFailed("下载失败") }
                    } else {
                        if (bytesTotal > 0) {
                            val progress =
                                bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }
            cursor?.close()
            if (downloading) delay(500)
        }
    }

    private fun downloadAndInstallApk(asset: GiteeAsset): Long {
        Toast.makeText(this, "开始下载更新...", Toast.LENGTH_SHORT).show()
        val downloadManager =
            getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destination = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            asset.name
        )
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(asset.browser_download_url))
            .setTitle("MG Linker 更新")
            .setDescription("正在下载 ${asset.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                asset.name
            )
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        unregisterReceiver(this)
                    } catch (e: Exception) {
                    }
                    val uri = downloadManager.getUriForDownloadedFile(id)
                    if (uri != null) installApk(uri)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        return downloadId
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