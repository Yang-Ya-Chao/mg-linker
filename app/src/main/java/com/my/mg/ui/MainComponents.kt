package com.my.mg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.my.mg.MainUiState
import com.my.mg.data.GiteeRelease

@Composable
fun MGConfigScreen(
    uiState: MainUiState,
    onUpdateInput: (brand: String?, model: String?, name: String?, vin: String?, color: String?, plate: String?, token: String?) -> Unit,
    onSave: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val vinFocusRequester = remember { FocusRequester() }
    val accessTokenFocusRequester = remember { FocusRequester() }

    // 辅助计算
    val brandCode = if (uiState.carBrand == "名爵") "MG" else "RW"
    val availableModels = uiState.carConfigList.filter { it.brand == brandCode }.map { it.model }

    val currentModelConfig =
        uiState.carConfigList.find { it.brand == brandCode && it.model == uiState.carModel }
    val availableColors = currentModelConfig?.colors?.map { it.name } ?: emptyList()

    val isVinValid = uiState.vin.length == 17
    val isVinError = uiState.vin.isNotEmpty() && !isVinValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "上汽小组件配置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        BrandSelector(
            selectedBrand = uiState.carBrand,
            onBrandSelected = { onUpdateInput(it, null, null, null, null, null, null) })

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoadingConfig) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(
                    "正在加载车型数据...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                ModelDropdownField(
                    label = "请选择车型",
                    selectedModel = uiState.carModel,
                    onModelSelected = { onUpdateInput(null, it, null, null, null, null, null) },
                    models = availableModels
                )
            }

            InputField(
                label = "车辆名称 (选填)",
                value = uiState.carName,
                onValueChange = { onUpdateInput(null, null, it, null, null, null, null) })

            InputField(
                label = "请输入您的车架号(VIN):",
                value = uiState.vin,
                onValueChange = { input ->
                    val formatted =
                        input.uppercase().filter { it.isDigit() || it in 'A'..'Z' }.take(17)
                    onUpdateInput(null, null, null, formatted, null, null, null)
                },
                modifier = Modifier.focusRequester(vinFocusRequester),
                isError = isVinError,
                supportingText = {
                    if (isVinError) Text(
                        "需输入17位，当前: ${uiState.vin.length}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            )

            ColorDropdownField(
                label = "请选择车辆颜色:",
                selectedColor = uiState.color,
                onColorSelected = { onUpdateInput(null, null, null, null, it, null, null) },
                options = availableColors
            )

            InputField(
                label = "请输入您的车牌号 (选填) :",
                value = uiState.plateNumber,
                onValueChange = { onUpdateInput(null, null, null, null, null, it, null) }
            )

            InputField(
                label = "请输入您的 ACCESS_TOKEN:",
                value = uiState.accessToken,
                onValueChange = { input ->
                    val formatted = input.filterNot { it == ' ' || it == '\n' || it == '\r' }
                    onUpdateInput(null, null, null, null, null, null, formatted)
                },
                modifier = Modifier.focusRequester(accessTokenFocusRequester),
                singleLine = false,
                onHelpClick = { uriHandler.openUri("https://gitee.com/yangyachao-X/mg-linker/blob/master/README.md") }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Footer
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(text = "保存并更新小组件", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "当前状态: ${if (uiState.isConfigured) "已配置" else "未配置"}",
                modifier = Modifier.align(Alignment.Start),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val footerText =
                if (uiState.isUpdateAvailable) "Power By 杨家三郎\n发现更新，点击下载" else "Power By 杨家三郎\n纯属娱乐 请勿较真"
            Text(
                text = footerText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCheckUpdate() }
            )
        }
    }
}

// --- 以下组件保持原样，仅提取出来 ---

@Composable
fun BrandSelector(selectedBrand: String, onBrandSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("名爵", "荣威").forEach { brand ->
            val isSelected = selectedBrand == brand
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onBrandSelected(brand) }
            ) {
                Text(
                    text = brand,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdownField(
    label: String,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    models: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                value = selectedModel,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = { onModelSelected(model); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDropdownField(
    label: String,
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    options: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                value = selectedColor,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = { onColorSelected(selectionOption); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onHelpClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onHelpClick() }
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = isError,
            supportingText = supportingText,
        )
    }
}

@Composable
fun UpdateDialog(
    release: GiteeRelease,
    isDownloading: Boolean,
    progress: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text(if (isDownloading) "正在下载更新..." else "发现新版本: ${release.tag_name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = release.body,
                    lineHeight = 1.5.em,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "下载进度: ${(progress * 100).toInt()}%",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                TextButton(onClick = onConfirm) { Text("立即更新") }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}