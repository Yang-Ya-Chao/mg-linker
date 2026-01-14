package com.my.mg.log

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogcatHelper {

    private var logProcess: Process? = null

    /**
     * 开始录制日志
     * @param context 上下文
     * @param fileName 日志文件名，例如 "widget_debug.txt"
     */
    /**
     * 开始录制日志
     */
    fun startRecording(context: Context, fileName: String = "MGLinker_log.txt") {
        // 1. 确定路径
        val logDir = context.getExternalFilesDir("logs")
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }
        val logFile = File(logDir, fileName)
        logFile.delete()
        // 【关键修复代码 Start】
        // 检查：如果进程对象存在，但对应的文件被删除了
        if (logProcess != null && !logFile.exists()) {
            // 说明用户手动删了日志，我们需要重启 logcat 进程
            stopRecording()
        }
        // 【关键修复代码 End】

        if (logProcess != null) return // 如果一切正常，直接返回，不重复启动

        // 下面是启动逻辑 (保持不变)
        val cmd = "logcat -f ${logFile.absolutePath} -v time"
        try {
            logProcess = Runtime.getRuntime().exec(cmd)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 停止录制
     */
    fun stopRecording() {
        logProcess?.destroy()
        logProcess = null
    }

    /**
     * 获取日志文件路径（方便你查看）
     */
    fun getLogFilePath(context: Context, fileName: String = "app_log.txt"): String {
        return File(context.getExternalFilesDir("logs"), fileName).absolutePath
    }
}