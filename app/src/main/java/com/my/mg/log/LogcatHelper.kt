package com.my.mg.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * [功能]：日志录制助手 (HyperOS 规范优化版)
 * [实现逻辑]：
 * 1. 采用白名单机制 (Tag Filter)，只记录核心业务 Tag，屏蔽系统噪音。
 * 2. 自动包含 Crash 日志 (AndroidRuntime)，防止漏掉崩溃信息。
 * 3. 启动前自动清理 Logcat 缓存，保证日志文件纯净。
 */
object LogcatHelper {

    private var logProcess: Process? = null
    private const val TAG = "LogcatHelper"

    // [配置]：定义你关心的 Tag 白名单
    // 这里提取了你项目中用到的核心 Tag，以及系统崩溃/错误相关的 Tag
    private val INTERESTING_TAGS = arrayOf(
        // === 核心业务 ===
        "MGWidget",             // 小组件主逻辑
        "WidgetUpdateWorker",   // 后台更新任务
        "UpdateCheck",          // 更新检查
        "MGConfigScreen",       // 配置页面

        // === 网络与数据 ===
        "fetchVehicleDataSuspended", // 车辆数据请求
        "getAddressSync",            // 地址解析
        "ImageWorker",               // 图片加载

        // === 系统关键 ===
        "AndroidRuntime",       // Java 层崩溃
        "FATAL",                //由于某些 Native 崩溃
        "System.err"            // 标准错误输出
    )

    /**
     * [功能]：开始录制日志
     * [参数]：
     * @param context 上下文
     * @param fileName 日志文件名
     */
    fun startRecording(context: Context, fileName: String = "MGLinker_log.txt") {
        // 1. 准备日志文件路径
        val logDir = context.getExternalFilesDir("logs")
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs()
        }
        val logFile = File(logDir, fileName)

        // 2. 状态检查：如果进程还在但文件被删，或者需要重启录制
        if (logProcess != null) {
            if (!logFile.exists()) {
                stopRecording() // 文件丢失，重启进程
            } else {
                return // 正常运行中，无需重复启动
            }
        }

        // 3. 清理旧文件和 Logcat 缓存
        // [逻辑]：删除旧文件是为了防止文件无限增长。执行 logcat -c 是为了不把上次残留的系统缓存写入新文件。
        if (logFile.exists()) {
            logFile.delete()
        }
        clearLogcatBuffer()

        // 4. 构建精准过滤命令
        // 命令格式：logcat -f <path> -v time -s Tag1 Tag2 Tag3 ...
        // -f: 输出到文件
        // -v time: 仅显示时间、进程、Tag、Message，简洁明了
        // -s: 静默模式(Silent)，默认屏蔽所有Tag，只显示后面列出的 Tag
        val cmdBuilder = StringBuilder("logcat -f ${logFile.absolutePath} -v time -s")
        for (tag in INTERESTING_TAGS) {
            cmdBuilder.append(" $tag")
        }

        try {
            Log.d(TAG, "Starting log recording with command: $cmdBuilder")
            logProcess = Runtime.getRuntime().exec(cmdBuilder.toString())
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start logcat process", e)
        }
    }

    /**
     * [功能]：停止录制
     */
    fun stopRecording() {
        if (logProcess != null) {
            logProcess?.destroy()
            logProcess = null
            Log.d(TAG, "Log recording stopped.")
        }
    }

    /**
     * [功能]：清理 Logcat 缓存区
     * [逻辑]：防止上次运行的残留日志污染本次文件
     */
    private fun clearLogcatBuffer() {
        try {
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * [功能]：获取日志文件绝对路径
     */
    fun getLogFilePath(context: Context, fileName: String = "MGLinker_log.txt"): String {
        val dir = context.getExternalFilesDir("logs")
        return File(dir, fileName).absolutePath
    }
}