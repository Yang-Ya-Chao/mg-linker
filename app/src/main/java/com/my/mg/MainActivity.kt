package com.my.mg

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.mg.ui.theme.MGLinkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MGLinkerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MGConfigScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MGConfigScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("mg_config", Context.MODE_PRIVATE) }

    var vin by remember { mutableStateOf(sharedPreferences.getString("vin", "") ?: "") }
    var color by remember { mutableStateOf(sharedPreferences.getString("color", "") ?: "") }
    var carName by remember { mutableStateOf(sharedPreferences.getString("car_name", "") ?: "") }
    var plateNumber by remember { mutableStateOf(sharedPreferences.getString("plate_number", "") ?: "") }
    var accessToken by remember { mutableStateOf(sharedPreferences.getString("access_token", "") ?: "") }
    var isConfigured by remember { mutableStateOf(sharedPreferences.getBoolean("is_configured", false)) }

    val vinFocusRequester = remember { FocusRequester() }
    val accessTokenFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "MG7 小组件配置",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(20.dp))

        InputField(
            label = "请输入您的车架号(VIN):",
            value = vin,
            onValueChange = { vin = it },
            modifier = Modifier.focusRequester(vinFocusRequester)
        )

        ColorDropdownField(
            label = "请选择车辆颜色:",
            selectedColor = color,
            onColorSelected = { color = it }
        )

        InputField(label = "请输入您的车辆名称 (选填) :", value = carName, onValueChange = { carName = it })
        InputField(label = "请输入您的车牌号 (选填) :", value = plateNumber, onValueChange = { plateNumber = it })
        InputField(
            label = "请输入您的 ACCESS_TOKEN:",
            value = accessToken,
            onValueChange = { accessToken = it },
            modifier = Modifier.focusRequester(accessTokenFocusRequester),
            singleLine = false
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (vin.isBlank()) {
                    Toast.makeText(context, "请输入车架号", Toast.LENGTH_SHORT).show()
                    vinFocusRequester.requestFocus()
                    return@Button
                }
                if (accessToken.isBlank()) {
                    Toast.makeText(context, "请输入ACCESS_TOKEN", Toast.LENGTH_SHORT).show()
                    accessTokenFocusRequester.requestFocus()
                    return@Button
                }
                val editor = sharedPreferences.edit()
                editor.putString("vin", vin)
                editor.putString("color", color)
                editor.putString("car_name", carName)
                editor.putString("plate_number", plateNumber)
                editor.putString("access_token", accessToken)
                editor.putBoolean("is_configured", true)
                editor.apply()
                isConfigured = true
                Toast.makeText(context, "保存成功，小组件将在稍后更新", Toast.LENGTH_SHORT).show()

                // 触发小组件更新
                val intent = Intent(context, MGWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, MGWidget::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
        ) {
            Text(text = "保存并更新小组件", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "当前状态: ${if (isConfigured) "已配置" else "未配置"}",
            modifier = Modifier.align(Alignment.Start),
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Power By 杨家三郎\n纯属娱乐免费，请勿较真",
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDropdownField(
    label: String,
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val options = listOf("墨玉黑", "釉瓷白", "山茶红", "雾凇灰", "翡冷翠", "冰晶蓝")
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
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
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onColorSelected(selectionOption)
                            expanded = false
                        },
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
    singleLine: Boolean = true
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MGConfigScreenPreview() {
    MGLinkerTheme {
        MGConfigScreen()
    }
}
