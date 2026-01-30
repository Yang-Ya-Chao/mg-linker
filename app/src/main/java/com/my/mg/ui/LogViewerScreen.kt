package com.my.mg.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.mg.log.LogcatHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // [修改 1]：从 LogcatHelper 收集录制状态，这才是“事实来源”
    val isRecording by LogcatHelper.recordingState.collectAsState()

    // 日志内容状态
    var logContent by remember { mutableStateOf("点击刷新或开始录制...") }

    // [修改 2]：自动刷新逻辑改为监听 isRecording 的变化
    // 当 isRecording 变为 true 时，自动启动刷新循环
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                // 读取日志
                val path = LogcatHelper.getLogFilePath(context)
                val file = File(path)
                if (file.exists()) {
                    val content = withContext(Dispatchers.IO) {
                        file.readLines().takeLast(500).joinToString("\n")
                    }
                    logContent = content
                    // 可选：自动滚动到底部
                    // listState.scrollToItem(0) // 需配合 reverseLayout 或计算 index
                }
                delay(2000) // 每2秒刷新一次
            }
        }
    }

    // 手动刷新函数
    fun manualRefresh() {
        scope.launch(Dispatchers.IO) {
            val path = LogcatHelper.getLogFilePath(context)
            val file = File(path)
            val content = if (file.exists()) {
                file.readLines().takeLast(500).joinToString("\n")
            } else {
                "暂无日志文件"
            }
            withContext(Dispatchers.Main) {
                logContent = content
            }
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
                        // [修改 3]：直接调用 Helper 方法，状态会自动通过 Flow 更新回来
                        if (isRecording) {
                            LogcatHelper.stopRecording()
                        } else {
                            LogcatHelper.startRecording(context)
                        }
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
                    Text(text = if (isRecording) "停止录制" else "开始录制")
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
}