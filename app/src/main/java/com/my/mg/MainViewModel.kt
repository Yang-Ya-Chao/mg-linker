package com.my.mg

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.my.mg.data.CarConfig
import com.my.mg.data.GiteeRelease
import com.my.mg.data.MainUiState
import com.my.mg.data.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request



class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val prefs: SharedPreferences =
        application.getSharedPreferences("mg_config", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val gson = Gson()

    init {
        loadLocalConfig()
        fetchCarConfig()
        checkUpdate(manual = false)
    }

    // 加载本地 SP 数据
    private fun loadLocalConfig() {
        _uiState.update {
            it.copy(
                carBrand = prefs.getString("car_brand", "名爵") ?: "名爵",
                carModel = prefs.getString("car_model", "") ?: "",
                carName = prefs.getString("car_name", "") ?: "",
                vin = prefs.getString("vin", "") ?: "",
                color = prefs.getString("color", "") ?: "",
                plateNumber = prefs.getString("plate_number", "") ?: "",
                accessToken = prefs.getString("access_token", "") ?: "",
                isConfigured = prefs.getBoolean("is_configured", false)
            )
        }
    }

    // 获取远程配置
    private fun fetchCarConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configUrl =
                    "https://gitee.com/yangyachao-X/mg-linker/raw/master/other/config.json"
                val request = Request.Builder().url(configUrl).get().build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (!json.isNullOrEmpty()) {
                        val remoteConfig = gson.fromJson(json, RemoteConfig::class.java)
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(carConfigList = remoteConfig.saic, isLoadingConfig = false)
                            }
                            // 数据加载完后，验证当前选中的车型是否依然有效
                            validateSelection()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingConfig = false) }
                }
            }
        }
    }

    // 级联选择校验（品牌->车型->颜色）
    private fun validateSelection() {
        val state = _uiState.value
        val brandCode = if (state.carBrand == "名爵") "MG" else "RW"

        // 1. 校验车型
        val availableModels = state.carConfigList.filter { it.brand == brandCode }.map { it.model }
        var newModel = state.carModel
        if (state.carModel.isNotEmpty() && !availableModels.contains(state.carModel)) {
            newModel = availableModels.firstOrNull() ?: ""
        } else if (state.carModel.isEmpty() && availableModels.isNotEmpty()) {
            newModel = availableModels.firstOrNull() ?: ""
        }

        // 2. 校验颜色
        val modelConfig = state.carConfigList.find { it.brand == brandCode && it.model == newModel }
        val availableColors = modelConfig?.colors?.map { it.name } ?: emptyList()
        var newColor = state.color
        if (state.color.isNotEmpty() && !availableColors.contains(state.color)) {
            newColor = availableColors.firstOrNull() ?: ""
        } else if (state.color.isEmpty() && availableColors.isNotEmpty()) {
            newColor = availableColors.firstOrNull() ?: ""
        }

        if (newModel != state.carModel || newColor != state.color) {
            _uiState.update { it.copy(carModel = newModel, color = newColor) }
        }
    }

    // 更新用户输入（UI 事件处理）
    fun updateInput(
        brand: String? = null,
        model: String? = null,
        name: String? = null,
        vin: String? = null,
        color: String? = null,
        plate: String? = null,
        token: String? = null
    ) {
        _uiState.update { state ->
            // 如果切换品牌，可能需要重置车型
            val nextBrand = brand ?: state.carBrand
            val nextModel =
                if (brand != null && brand != state.carBrand) "" else (model ?: state.carModel)
            // 如果切换车型，可能需要重置颜色
            val nextColor =
                if (model != null && model != state.carModel) "" else (color ?: state.color)

            state.copy(
                carBrand = nextBrand,
                carModel = nextModel,
                carName = name ?: state.carName,
                vin = vin ?: state.vin,
                color = nextColor,
                plateNumber = plate ?: state.plateNumber,
                accessToken = token ?: state.accessToken
            )
        }
        // 如果改变了级联选项，触发校验填充默认值
        if (brand != null || model != null) {
            validateSelection()
        }
    }

    // 保存配置 (返回是否成功)
    fun saveConfig(context: Context): Boolean {
        val state = _uiState.value

        // 基础校验
        if (state.vin.length != 17) {
            Toast.makeText(context, "请输入正确的17位车架号", Toast.LENGTH_SHORT).show()
            return false
        }
        if (state.accessToken.isBlank()) {
            Toast.makeText(context, "请输入 ACCESS_TOKEN", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!state.accessToken.endsWith("-prod_SAIC")) {
            Toast.makeText(context, "ACCESS_TOKEN 格式不正确", Toast.LENGTH_SHORT).show()
            return false
        }

        // 获取详细配置数据
        val brandCode = if (state.carBrand == "名爵") "MG" else "RW"
        val modelConfig =
            state.carConfigList.find { it.brand == brandCode && it.model == state.carModel }
        val colorConfig = modelConfig?.colors?.find { it.name == state.color }

        // 保存到 SP
        prefs.edit().apply {
            putString("car_brand", state.carBrand)
            putString("car_model", state.carModel)
            putString("car_name", state.carName)
            putString("vin", state.vin)
            putString("color", state.color)
            putString("plate_number", state.plateNumber)
            putString("access_token", state.accessToken)
            putString("car_image_url", colorConfig?.imageUrl ?: "")
            putString("car_fuel_capacity", (modelConfig?.fuel ?: 0.0).toString())
            putString("car_battery_capacity", (modelConfig?.battery ?: 0.0).toString())
            putBoolean("is_configured", true)
            apply()
        }

        _uiState.update { it.copy(isConfigured = true) }
        return true
    }

    // 检查更新
    fun checkUpdate(manual: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://gitee.com/api/v5/repos/yangyachao-X/mg-linker/releases/latest"
                val requestBuilder = Request.Builder().url(url).get()
                if (BuildConfig.GITEE_API_TOKEN.isNotEmpty()) {
                    requestBuilder.addHeader(
                        "Authorization",
                        "token ${BuildConfig.GITEE_API_TOKEN}"
                    )
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val release = gson.fromJson(body, GiteeRelease::class.java)
                        val currentVersion = getApplication<Application>().packageManager
                            .getPackageInfo(
                                getApplication<Application>().packageName,
                                0
                            ).versionName

                        if (isNewerVersion(release.tag_name, currentVersion)) {
                            withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(isUpdateAvailable = true, releaseInfo = release)
                                }
                                // 如果是手动检查，这里不需要Toast，UI层会根据状态弹窗
                            }
                        } else if (manual) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    getApplication(),
                                    "已经是最新版本",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else if (manual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "检查更新失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            getApplication(),
                            "检查更新出错: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // 更新下载进度状态
    fun updateDownloadState(isDownloading: Boolean, progress: Float) {
        _uiState.update { it.copy(isDownloading = isDownloading, downloadProgress = progress) }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.replace("v", "").split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.replace("v", "").split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(l.size, c.size)
        for (i in 0 until length) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}