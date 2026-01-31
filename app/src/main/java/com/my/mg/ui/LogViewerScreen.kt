package com.my.mg.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.my.mg.log.LogcatHelper
import com.my.mg.net.DeepSeekService
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


/**
 * [功能]：日志查看与 AI 分析页面
 * [修改说明]：
 * 1. analyzeLogs 函数增加了正则匹配，只提取 'Fetch Data response' 的 JSON 数据。
 * 2. 优化了 Prompt，专门让 AI 分析车辆状态（电量、续航、车门、胎压等）。
 */
@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    // 状态管理
    val isRecording by LogcatHelper.recordingState.collectAsState()
    var logContent by remember { mutableStateOf("点击刷新或开始录制...") }

    // 分析相关状态
    var showAnalysisDialog by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // 自动刷新逻辑
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                val path = LogcatHelper.getLogFilePath(context)
                val file = File(path)
                if (file.exists()) {
                    val content = withContext(Dispatchers.IO) {
                        file.readLines()
                            .takeLast(500)
                            .filter { !it.contains("Pinning is deprecated") }
                            .joinToString("\n")
                    }
                    logContent = content
                }
                delay(2000)
            }
        }
    }
    // 分析逻辑
    fun analyzeLogs() {
        // 1. 提取数据
        val jsonRegex = "Fetch Data response:\\s*(\\{.*\\})".toRegex()
        val lastMatch = logContent.lineSequence()
            .mapNotNull { line -> jsonRegex.find(line)?.groupValues?.get(1) }
            .lastOrNull()

        if (lastMatch == null) {
            Toast.makeText(
                context,
                "未在日志中找到 'Fetch Data response' 车辆数据",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showAnalysisDialog = true
        isAnalyzing = true
        analysisResult = "正在请求 DeepSeek 分析车辆状态..."

        scope.launch {
            try {
                // 2. 构建 Prompt
                val prompt = """
                    $lastMatch
                    请帮我写一份这个数据的完整翻译
                """.trimIndent()

                // 3. [修改]：调用独立的 Service 模块
                // 所有的网络细节都被封装在 DeepSeekService 中
                val responseText = DeepSeekService.chat(prompt)

                analysisResult = responseText
            } catch (e: Exception) {
                e.printStackTrace()
                // 友好展示错误信息（去掉 API Key 等敏感信息）
                val errorMsg =
                    e.message?.replace("Bearer sk-\\w+".toRegex(), "Bearer ***") ?: "未知错误"
                analysisResult = "分析失败: $errorMsg\n请检查网络连接或 API Key 配置。"
            } finally {
                isAnalyzing = false
            }
        }
    }

    // 手动刷新函数
    fun manualRefresh() {
        scope.launch(Dispatchers.IO) {
            val path = LogcatHelper.getLogFilePath(context)
            val file = File(path)
            val content = if (file.exists()) {
                file.readLines()
                    .takeLast(500)
                    .filter { !it.contains("Pinning is deprecated") }
                    .joinToString("\n")
            } else {
                "暂无日志文件"
            }
            withContext(Dispatchers.Main) { logContent = content }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "日志中心",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isRecording) LogcatHelper.stopRecording()
                        else LogcatHelper.startRecording(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isRecording) "停止" else "录制")
                }

                Row {
                    // Gemini 分析按钮
                    IconButton(onClick = { analyzeLogs() }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "车辆数据分析",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    IconButton(onClick = { manualRefresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = logContent,
                        color = Color(0xFF00FF00),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Text(
            text = "日志状态: ${if (isRecording) "正在录制..." else "已停止"}\n日志路径: Android/data/com.my.mg/files/logs/",
            fontSize = 10.sp,
            color = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 8.dp, start = 4.dp)
        )
    }

    // 分析结果弹窗
    if (showAnalysisDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAnalyzing) showAnalysisDialog = false },
            icon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("车辆状态智能分析") },
            text = {
                if (isAnalyzing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在提取车辆数据并分析...", fontSize = 14.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // [核心修改]：使用 MarkdownText 替代 Text
                        MarkdownText(
                            markdown = analysisResult,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 1.6.em,
                            // 样式配置：让代码块看起来像网页效果
                            style = TextStyle(
                                fontFamily = FontFamily.Default,
                                fontSize = 13.sp,
                                lineHeight = 1.6.em,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            // 关键：启用代码块背景支持（部分库版本默认支持，若不支持需手动处理 Box）
                            // 注意：这个库对基础 Markdown 支持很好，但对复杂的代码块高亮支持有限。
                            // 如果只需要简单的灰色背景块，它能自动识别 ``` 包裹的内容。
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (!isAnalyzing) {
                    TextButton(onClick = { showAnalysisDialog = false }) {
                        Text("关闭")
                    }
                }
            },
            dismissButton = {
                if (!isAnalyzing) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(analysisResult))
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}