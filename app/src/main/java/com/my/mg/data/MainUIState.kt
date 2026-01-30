package com.my.mg.data
// UI 状态数据类
data class MainUiState(
    val isLoadingConfig: Boolean = true,
    val carConfigList: List<CarConfig> = emptyList(),
    // 用户输入相关
    val carBrand: String = "名爵",
    val carModel: String = "",
    val carName: String = "",
    val vin: String = "",
    val color: String = "",
    val plateNumber: String = "",
    val accessToken: String = "",
    val isConfigured: Boolean = false,
    // 更新相关
    val isUpdateAvailable: Boolean = false,
    val releaseInfo: GiteeRelease? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
)
