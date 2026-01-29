package com.my.mg.net

import android.util.Log
import com.google.gson.Gson
import com.my.mg.VehicleStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object VehicleDataWorker {
    // ========================================================================
    // 辅助逻辑：网络请求与数据处理
    // ========================================================================
    /**
     * 挂起函数：获取车辆数据
     */
    suspend fun fetchVehicleDataSuspended(
        vin: String, token: String
    ): VehicleStatusResponse? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis() / 1000
            val url =
                "https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=$timestamp&token=$token&vin=$vin"
            Log.d("fetchVehicleDataSuspended", "Fetch Data url:  $url")
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            Log.d("fetchVehicleDataSuspended", "Fetch Data response:  $body")
            if (response.isSuccessful && !body.isNullOrEmpty()) {
                Gson().fromJson(body, VehicleStatusResponse::class.java)
            } else {
                Log.e("fetchVehicleDataSuspended", "Fetch Data Failed: Code ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("fetchVehicleDataSuspended", "Fetch Data Error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}