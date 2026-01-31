package com.my.mg.net

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.my.mg.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [功能]：通用 AI 服务模块 (非流式/完整输出版)
 * [支持模型]：DeepSeek, 智谱GLM, 通义千问Qwen
 */
object AiService {

    // 定义支持的 AI 助手配置
    enum class AiModel(val label: String, val apiKey: String, val apiUrl: String, val modelName: String) {
        // 1. 硅基流动 - DeepSeek V3
        DeepSeek(
            label = "DeepSeek V3 (硅基)",
            apiKey = BuildConfig.DS_API_KEY, // ⚠️ 请替换为你的 Key
            apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
            modelName = "deepseek-ai/DeepSeek-V3"
        ),

        // 2. 硅基流动 - 通义千问 Qwen2.5 (免费)
        Qwen(
            label = "通义千问 Qwen2.5",
            apiKey =  BuildConfig.DS_API_KEY, // ⚠️ 请替换为你的 Key
            apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
            modelName = "Qwen/Qwen2.5-72B-Instruct"
        ),

        // 3. 智谱清言 - GLM-4-Flash (免费)
        ZhipuGLM(
            label = "智谱 GLM-4-Flash",
            apiKey = BuildConfig.ZP_API_KEY, // ⚠️ 请替换为你的 Key
            apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            modelName = "glm-4-flash"
        );
    }

    // 当前选中的模型
    var currentModel = AiModel.DeepSeek

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // [修改] 完整输出模式下，读取超时建议设长一点
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * 发送对话请求 (等待完整响应)
     * @return 完整的 AI 回复字符串
     */
    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        val modelConfig = currentModel

        // 简单的 Key 校验
        if (modelConfig.apiKey.isNullOrEmpty()) {
            throw IllegalArgumentException("请在 AiService.kt 中配置 [${modelConfig.label}] 的 API Key")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        val jsonBody = JsonObject().apply {
            addProperty("model", modelConfig.modelName)
            addProperty("stream", false) // [关键] 关闭流式，一次性返回

            // 智谱 GLM 的可选参数
            if (modelConfig == AiModel.ZhipuGLM) {
                addProperty("temperature", 0.95)
                addProperty("top_p", 0.7)
            }

            val messages = JsonArray()
            val userMsg = JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", prompt)
            }
            messages.add(userMsg)
            add("messages", messages)
        }

        val request = Request.Builder()
            .url(modelConfig.apiUrl)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer ${modelConfig.apiKey}")
            .build()

        // 执行请求
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val msg = when(response.code) {
                    401 -> "API Key 无效"
                    402 -> "余额不足 (免费额度已用完)"
                    504 -> "生成超时，请重试"
                    else -> errorBody ?: "未知错误"
                }
                throw IOException("[${modelConfig.label}] 请求失败 (${response.code}): $msg")
            }

            val responseBody = response.body?.string() ?: throw IOException("响应体为空")

            try {
                // 解析完整的 JSON 响应
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                val choices = json.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    // 非流式返回结构：choices[0].message.content
                    val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                    return@withContext message.get("content").asString
                } else {
                    return@withContext "API 返回内容为空"
                }
            } catch (e: Exception) {
                throw IOException("数据解析失败: ${e.message}", e)
            }
        }
    }
}