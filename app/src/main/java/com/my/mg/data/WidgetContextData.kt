package com.my.mg.data


import com.my.mg.VehicleStatusResponse

/**
 * [功能]：Widget 渲染所需的上下文数据封装
 * [设计意图]：将配置读取逻辑从 Widget 渲染逻辑中剥离，实现“一次读取，多次渲染”。
 * 避免在循环更新 Widget 时重复读取 SharedPreferences。
 */
data class WidgetContextData(
    // 车辆静态配置
    val carName: String,
    val carBrand: String,
    val carModel: String,
    val plateNumber: String,
    val carImageUrl: String,

    // 能源配置 (用于计算能耗)
    val fuelCapacity: Double,
    val batteryCapacity: Double,

    // 动态数据 (网络请求结果)
    val vehicleData: VehicleStatusResponse?,
    val address: String?
)