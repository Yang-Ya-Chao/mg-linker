package com.my.mg.config

// ================== 新增：JSON 数据模型 ==================
// 远程配置 JSON 数据的模型类

data class RemoteConfig(
    val saic: List<CarConfig>
)

data class CarConfig(
    val brand: String, // "MG" 或 "RW"
    val model: String, // "MG7", "D7" 等
    val fuel: Double, // "65.0", "55.0" 等
    val battery: Double, // "19.2", "0.0" 等
    val colors: List<CarColor>
)

data class CarColor(
    val name: String,
    val imageUrl: String
)