package com.my.mg.log

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * [功能]：日志录制助手 (StateFlow 增强版)
 * [实现逻辑]：
 * 1. 使用 StateFlow 暴露录制状态，确保 UI 状态在页面切换时保持同步。
 * 2. 增加 isProcessAlive 检查，防止状态误判。
 */
object LogcatHelper {

    private var logProcess: Process? = null
    private const val TAG = "LogcatHelper"

    // [新增]：使用 StateFlow 管理录制状态
    private val _recordingState = MutableStateFlow(false)
    val recordingState = _recordingState.asStateFlow()

    // Tag 白名单
    private val INTERESTING_TAGS = arrayOf(
        "MGWidget", "WidgetUpdateWorker", "UpdateCheck", "MGConfigScreen",
        "fetchVehicleDataSuspended", "getAddressSync", "ImageWorker",
        "AndroidRuntime", "FATAL", "System.err"
    )

    fun startRecording(context: Context, fileName: String = "MGLinker_log.txt") {
        // 防止重复启动
        if (isProcessAlive()) {
            _recordingState.value = true
            return
        }

        val logDir = context.getExternalFilesDir("logs")
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }
        val logFile = File(logDir, fileName)

        // 清理旧缓存
        if (logFile.exists()) logFile.delete()
        clearLogcatBuffer()

        val cmdBuilder = StringBuilder("logcat -f ${logFile.absolutePath} -v time -s")
        for (tag in INTERESTING_TAGS) {
            cmdBuilder.append(" $tag")
        }

        try {
            Log.d(TAG, "Starting log recording...")
            logProcess = Runtime.getRuntime().exec(cmdBuilder.toString())
            // [关键]：更新状态为 true
            _recordingState.value = true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start logcat process", e)
            _recordingState.value = false
        }
    }

    fun stopRecording() {
        if (logProcess != null) {
            logProcess?.destroy()
            logProcess = null
            Log.d(TAG, "Log recording stopped.")
        }
        // [关键]：更新状态为 false
        _recordingState.value = false
    }

    /**
     * [新增]：检查进程是否存活
     */
    private fun isProcessAlive(): Boolean {
        val p = logProcess ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                p.isAlive
            } else {
                try {
                    p.exitValue()
                    false // 有退出值说明已结束
                } catch (e: IllegalThreadStateException) {
                    true // 抛异常说明还在运行
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun clearLogcatBuffer() {
        try {
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getLogFilePath(context: Context, fileName: String = "MGLinker_log.txt"): String {
        val dir = context.getExternalFilesDir("logs")
        return File(dir, fileName).absolutePath
    }
}