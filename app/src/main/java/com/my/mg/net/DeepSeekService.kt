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
 * [功能]：DeepSeek AI 服务模块
 * [实现目的]：独立封装 AI 接口调用逻辑，提供通用的对话能力，将网络层与 UI 层解耦。
 */
object DeepSeekService {


    private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"
    private const val MODEL_NAME = "deepseek-ai/DeepSeek-V3"

    // 配置 OkHttp，考虑到 AI 生成较慢，超时时间设置长一些
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // 读取超时设为 60秒
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * 发送 AI 对话请求
     * @param prompt 提示词
     * @return AI 的回复内容
     * @throws IOException 网络或解析异常
     */
    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        if (BuildConfig.DS_API_KEY.startsWith("sk-") && BuildConfig.DS_API_KEY.length < 10) {
            throw IllegalArgumentException("请先在 DeepSeekService 中配置有效的 API Key")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        // 构建请求体
        val jsonBody = JsonObject().apply {
            addProperty("model", MODEL_NAME)
            addProperty("stream", false)

            val messages = JsonArray()
            val userMsg = JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", prompt)
            }
            messages.add(userMsg)

            add("messages", messages)
        }

        val request = Request.Builder()
            .url(API_URL)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer ${BuildConfig.DS_API_KEY}")
            .build()

        // 执行请求
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw IOException("请求失败 Code: ${response.code}, Msg: $errorBody")
            }

            val responseBody = response.body?.string() ?: throw IOException("响应体为空")

            // 解析响应
            try {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                return@withContext jsonResponse.getAsJsonArray("choices")
                    .get(0).asJsonObject
                    .getAsJsonObject("message")
                    .get("content").asString
            } catch (e: Exception) {
                throw IOException("数据解析失败: ${e.message}", e)
            }
        }
    }

}