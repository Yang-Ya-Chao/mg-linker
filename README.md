

# MG Linker

## 项目介绍

MG Linker 是一款专为名爵(MG)和荣威(RW)汽车打造的Android桌面小组件应用。通过配合HttpCanary抓包工具获取车辆数据Token，实现车辆状态信息的实时展示。主要功能包括车辆续航里程、剩余油量/电量、车门锁状态、车内温度等关键信息的桌面快捷查看。

**适用车型：** MG7、MG5、MG6、荣威D7、荣威D5X 等上汽名爵/荣威系列车型

## 核心功能

- **实时车辆状态监控**：在桌面小组件中展示车门开关状态、锁车状态、续航里程、油耗/电量信息
- **多尺寸组件支持**：提供图标型、标准型等多种尺寸的桌面小组件
- **品牌智能适配**：支持MG和荣威两个品牌的车辆配置切换
- **自动更新机制**：通过Gitee Release自动检测并下载最新版本
- **完善日志系统**：内置详细的运行日志记录，便于问题排查
- **深色主题适配**：自动跟随系统深色/浅色主题切换

## 技术架构

### 技术栈

| 技术 | 说明 |
|------|------|
| **开发语言** | Kotlin |
| **UI框架** | Jetpack Compose |
| **目标SDK** | Android API 34 |
| **构建工具** | Gradle with Kotlin DSL |
| **包名** | com.my.mg |

### 项目结构

```
mg-linker/
├── app/src/main/java/com/my/mg/
│   ├── MainActivity.kt        # 主应用界面与更新逻辑
│   ├── MGWidget.kt            # 主桌面小组件实现
│   ├── MGWidgetSmall.kt       # 小尺寸小组件
│   ├── config/
│   │   └── CarConfig.kt       # 车辆配置数据模型
│   ├── log/
│   │   └── LogcatHelper.kt    # 日志记录管理
│   ├── receiver/
│   │   └── ScreenOnReceiver.kt # 屏幕亮起广播接收
│   ├── ui/theme/              # Compose UI主题配置
│   └── worker/
│       └── WidgetUpdateWorker.kt # 小组件定时更新
├── HttpCanary/                # 抓包工具及教程资源
└── gradle/                    # Gradle构建配置
```

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK 34
- Gradle 8.4+
- JDK 17

### 安装步骤

1. **下载安装应用**
   - 从 [Gitee Releases](https://gitee.com/yangyachao-X/mg-linker/releases) 下载最新版本的MG Linker APK
   - 在手机上允许安装未知来源应用后完成安装

2. **准备抓包工具**
   - 下载并安装 [HttpCanary](https://pan.quark.cn/s/c3582382b19d) 抓包工具
   - 安装上汽名爵或荣威官方App

3. **获取车辆Token**（详细步骤见下方使用教程）

4. **配置应用**
   - 打开MG Linker应用
   - 选择您的车辆品牌(MG/荣威)
   - 输入VIN码和抓取到的Token
   - 保存配置

5. **添加桌面小组件**
   - 长按手机桌面 → 添加小部件 → 找到MG Linker
   - 选择合适的组件尺寸添加到桌面
   - 配置成功后小组件将自动开始显示车辆数据

## 使用教程


### Token抓取步骤

使用HttpCanary抓取车辆数据的Token是配置成功的关键。请按照以下步骤操作：

1. **启动HttpCanary**
   - 打开HttpCanary应用
   - 开始抓包
   - 切换到MG Live APP/上汽荣威APP进行登录操作
   - 抓包示例图：
   - 步骤1
![输入图片说明](HttpCanary/1.jpg)
   - 步骤2
![输入图片说明](HttpCanary/2.jpg)
   - 步骤3
![输入图片说明](HttpCanary/3.jpg)
   - 步骤4
![输入图片说明](HttpCanary/4.jpg)
   - 步骤5
![输入图片说明](HttpCanary/5.jpg)
   - 步骤6
![输入图片说明](HttpCanary/6.jpg)
   - 步骤7
![输入图片说明](HttpCanary/7.jpg)
   - 步骤8
![输入图片说明](HttpCanary/8.jpg)
   - 步骤9
![输入图片说明](HttpCanary/9.jpg)
   - 步骤10
![输入图片说明](HttpCanary/10.jpg)
2. **定位请求**
   - 在抓包记录中找到形如示例请求:
   - 名爵车系: `https://social.saicmg.com/XXXXXXXXXXXXXXXX`
   - 荣威车系：`https://mq.ebanma.com/app-xxxxxxxxxxxx`
   - 记录请求URL中的token参数值

3. **获取VIN码**
   - 在同一请求或App相关页面中找到车辆VIN码
   - VIN码通常以LSJ开头，共17位

### 应用配置界面说明

应用主界面提供以下配置项：

| 配置项 | 说明 |
|--------|------|
| **品牌选择** | 选择MG或荣威品牌 |
| **车型选择** | 选择具体车型如MG7、D7等 |
| **颜色选择** | 选择车辆外观颜色(影响展示图标) |
| **Token输入** | 粘贴从抓包获取的token |
| **VIN码输入** | 输入17位车辆识别码 |
| **检查更新** | 手动触发版本更新检查 |

## API接口说明

应用通过以下接口获取车辆状态信息：

```
POST https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus
```

**请求参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| timestamp | 是 | 时间戳(毫秒) |
| token | 是 | 身份令牌 |
| vin | 是 | 车辆识别码 |

**返回数据字段说明：**

```kotlin
// 车辆位置信息
data class VehiclePosition(
    val latitude: String?,      // 纬度
    val longitude: String?,     // 经度
    val gps_status: Int?,       // GPS状态
    val satellites: Int?,       // 卫星数量
    val update_time: Long?      // 更新时间
)

// 车辆数值数据
data class VehicleValue(
    val fuel_level_prc: Int?,        // 燃油百分比
    val fuel_range: Int?,            // 燃油续航(km)
    val odometer: Int?,              // 里程表(km)
    val battery_pack_prc: Int?,      // 电池百分比
    val battery_pack_range: Int?,    // 电池续航(km)
    val interior_temperature: Double?, // 车内温度
    val exterior_temperature: Int?,    // 车外温度
    val chrgng_rmnng_time: Int?,       // 充电剩余时间
    val charge_status: Int?            // 充电状态
)

// 车辆状态数据
data class VehicleState(
    val lock: Boolean?,            // 锁车状态
    val door: Boolean?,            // 车门状态
    val driver_door: Boolean?,     // 驾驶位车门
    val passenger_door: Boolean?,  // 副驾驶车门
    val boot: Boolean?,            // 后备箱状态
    val sunroof: Boolean?          // 天窗状态
)
```

## 常见问题

### Q: 小组件显示"获取数据失败"
A: 请检查Token和VIN码是否正确填写；确认车辆App登录状态有效；尝试重新抓取Token（Token可能过期）

### Q: 小组件不刷新
A: 检查应用是否授予后台运行权限；在设置-应用管理-MG Linker中开启"自启动"权限

### Q: 抓包获取不到Token
A: 确保使用正确版本的官方App；荣威车型需要抓取特定接口；尝试完整操作一遍App登录流程

### Q: 日志文件位置
A: 调试版日志保存在 `Android/data/com.my.mg/files/logs/MGLinker_log.txt`

## 版本历史

| 版本 | 更新内容 |
|------|--------|
| 2.7 | 最新稳定版本 |
| 1.0 | 调试版本，包含详细日志输出 |

完整版本历史请访问 [Gitee Releases](https://gitee.com/yangyachao-X/mg-linker/releases)

## 问题反馈

如遇到问题，欢迎通过以下方式获取帮助：

- 加入抖音群聊交流
![输入图片说明](douyin.jpg)
- 查看日志文件中的详细报错信息
- 下载调试版本复现问题后提交反馈

## 许可证

本项目遵循 Apache License 2.0 许可证。