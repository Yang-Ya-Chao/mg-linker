# MG Liner

#### 介绍

MGLive;MG7;android;小组件;名爵;名爵7

有需要的可以加入抖音群聊互相交流
![输入图片说明](2026-01-19-110136.jpg)

### 📋 使用流程

#### 1. **安装步骤**
1. 下载安装[MG Linker](https://gitee.com/yangyachao-X/mg-linker/releases/download/1.8/MG%20Linker.apk)
2. 下载安装[HttpCanary](https://pan.quark.cn/s/c3582382b19d )抓包工具
3. 按照下方教程进行Token抓包
#### 步骤一
![输入图片说明](HttpCanary/1.jpg)



#### 步骤二
![输入图片说明](HttpCanary/2.jpg)



#### 步骤三
![输入图片说明](HttpCanary/3.jpg)



#### 步骤四
![输入图片说明](HttpCanary/4.jpg)



#### 步骤五
![输入图片说明](HttpCanary/5.jpg)



#### 步骤六
![输入图片说明](HttpCanary/6.jpg)



#### 步骤七
![输入图片说明](HttpCanary/7.jpg)



#### 步骤八
![输入图片说明](HttpCanary/8.jpg)



#### 步骤九
![输入图片说明](HttpCanary/9.jpg)



#### 步骤十
![输入图片说明](HttpCanary/10.jpg)



4.  配置MG Linker参数![输入图片说明](https://foruda.gitee.com/images/1768037488980039934/815d254c_8784824.png "屏幕截图")

5.  保存返回桌面，在小组件中找到MG Linker并添加到桌面（如果在手机设置-应用管理-MG Linker权限中，开启‘桌面快捷方式’权限，保存会自动在桌面创建小部件）

6.  按照教程操作，也正确抓取到了token，配置之后小组件刷新异常，请下载[MG Linker调试版](https://gitee.com/yangyachao-X/mg-linker/releases/download/1.0/MG%20Linker_debug.apk)，刷新小组件后在Android/data/com.my.mg/files/logs/MGLinker_log.txt中查看详细报错信息

7. [历史版本下载](https://gitee.com/yangyachao-X/mg-linker/releases)

#### 小部件页面1：

!![输入图片说明](https://foruda.gitee.com/images/1768037498931980640/daa657b2_8784824.png "屏幕截图")


#### 小部件页面2：

![输入图片说明](https://foruda.gitee.com/images/1768037472971862446/56c70158_8784824.png "屏幕截图")





### 📱 项目概述
**MG Linker** 是一个Android应用，主要功能是获取名爵MG7车辆的数据，展示到安卓小部件中，需要配合HttpCanary进行网络抓包使用。

### 🏗️ 核心架构

#### 1. **技术栈**
- **平台**: Android
- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **构建工具**: Gradle with Kotlin DSL
- **目标SDK**: Android API 34
- **包名**: `com.my.mg`

#### 2. **项目结构**
```
mg-linker/
├── app/
│   ├── src/main/java/com/my/mg/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── MGWidget.kt             # 桌面小组件
│   │   ├── log/LogcatHelper.kt     # 日志管理
│   │   └── ui/theme/              # UI主题
│   └── src/main/res/
├── HttpCanary/                     # 抓包工具以及教程
├── gradle/                         # Gradle配置
└── README.md                       # 使用说明
```

### 🚀 核心功能模块

#### 1. **主应用模块 (MainActivity.kt)**
- **功能**: 自动更新和安装管理
- **API集成**: 通过Gitee API检查最新版本
- **自动下载**: 下载最新版本的APK文件
- **静默安装**: 支持应用的自动更新

#### 2. **桌面小组件模块 (MGWidget.kt)**
- **基础**: 继承自AppWidgetProvider
- **功能**: 提供快捷的网络调试功能
- **更新机制**: 定期更新显示信息

#### 3. **日志模块 (LogcatHelper.kt)**
- **功能**: 管理应用运行日志
- **存储**: 日志保存在本地文件系统
- **路径**: `Android/data/com.my.mg/files/logs/MGLinker_log.txt`

### 🔧 关键技术实现

#### 1. **网络请求处理**

```kotlin
// 使用OkHttp进行网络请求
val client = OkHttpClient()
val request = Request.Builder()
    .url("https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=$timestamp&token=$token&vin=$vin")
    .build()
```

#### 2. **Gitee API集成**
- **数据类**: `GiteeRelease` 用于解析API响应
- **版本检查**: 获取最新release信息
- **文件下载**: 通过DownloadManager下载APK

#### 3. **UI设计**
- **现代界面**: 使用Jetpack Compose
- **主题适配**: 支持深色/浅色主题
- **动态配色**: 兼容Material Design 3

### 🔍 项目特点

#### 1. **自动化更新**
- 通过Gitee Release API实现版本检查
- 支持静默下载和安装
- 完整的更新流程管理

#### 2. **调试友好**
- 内置详细的日志记录
- 提供debug版本用于问题排查
- 完善的错误处理机制

#### 3. **用户体验**
- 现代化的Compose UI
- 桌面小组件快速访问
- 简洁的操作流程

### 💡 工程实践亮点

1. **模块化设计**: 功能清晰分离
2. **协程使用**: 异步网络请求处理
3. **现代Android开发**: 遵循最新开发规范
4. **持续集成**: 通过Gitee Releases管理版本


#### 接口说明

--获取车辆状态信息1.1：

POST--------https://mp.ebanma.com/app-mp/vp/1.1/getVehicleStatus?timestamp=XXXX&token=XXXXXXXXX-prod_SAIC&vin=LSJWXXXXXXXXXX
