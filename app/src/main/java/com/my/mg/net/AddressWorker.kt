package com.my.mg.net

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import java.util.Locale


object AddressWorker {
    /**
     * 同步获取地址 (在 Worker 线程中直接调用，无需再开协程)
     */
    internal fun getAddressSync(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // 1. 请求多个候选地址（3 个），提高解析成功率
            val addresses = geocoder.getFromLocation(lat, lng, 3)
            Log.d("getAddressSync", "Geocoder addr: ${addresses}")
            // 如果没有任何地址，返回空字符串
            if (addresses.isNullOrEmpty()) return ""

            // 2. 从候选地址中挑选“信息最丰富”的一个 (原版优选算法)
            // 权重规则：有门牌号+20分，有道路名+10分，有子区域(区/县)+5分
            val bestAddr = addresses.sortedWith(compareByDescending<Address> {
                // 权重计算
                var score = 0
                val feature = it.featureName ?: ""
                val line0 = it.getAddressLine(0) ?: ""

                // 规则A：如果是具体门牌号（包含数字和号），权重最高
                if (feature.matches(Regex(".*\\d+号.*"))) score += 20

                // 规则B：避免选中纯地标名称（如"德润精品酒店"），优先选包含省市信息的完整描述
                if (line0.contains("市") && line0.contains("路")) score += 5

                // 规则C：如果 subLocality (区/县) 不为空，加分 (应对某些数据源)
                if (it.subLocality != null) score += 5

                score
            }).firstOrNull() ?: addresses[0] // 兜底使用第一个

            // 3. 格式化地址
            formatAddressSmart(bestAddr)

        } catch (e: Exception) {
            Log.e("getAddressSync", "Geocoder Error: ${e.message}")
            ""
        }
    }

    private fun formatAddressSmart(addr: Address): String {
        val sb = StringBuilder()
        val admin = addr.adminArea ?: ""
        val city = addr.locality ?: ""
        val district = addr.subLocality ?: ""

        sb.append(admin)
        if (city != admin) sb.append(city)
        sb.append(district)

        val road = addr.thoroughfare ?: ""
        val feature = addr.featureName ?: ""

        if (road.isNotEmpty()) {
            sb.append(road)
            if (feature.contains(road) && feature != road) {
                sb.append(feature.substringAfter(road))
            } else if (feature != road && feature != district) {
                sb.append(feature)
            }
        } else {
            if (feature != district) sb.append(feature)
        }
        val result = sb.toString()
        return if (result.length < 3) addr.getAddressLine(0) ?: "未知地址" else result
    }

}