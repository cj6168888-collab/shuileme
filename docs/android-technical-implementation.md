# 狗熊睡眠 Android 技术实施方案

目标：先交付一个无服务器、可离线安装运行的原生 Android APK MVP。

## 技术选择

- 语言：Java 原生 Android，首版避免外部依赖，确保本机 SDK 可直接打包。
- UI：程序化原生 View，遵循大字体、大按钮、明快配色。
- 存储：SQLiteOpenHelper，本地结构化事件和汇总数据。
- 后台：Foreground Service，负责守护会话、音频采样、动作采样、事件判断。
- 检测：固定安全阈值 + 本机个人基线，基线只保存在 SharedPreferences。
- 唤醒：全屏 Activity + 高优先级通知 + 震动 + 音频播放。
- 紧急通知：电话/短信权限仅在用户启用紧急联系人时请求。

## 包结构

- `com.gouxiong.sleep.MainActivity`：首页、引导、设置、记录入口。
- `com.gouxiong.sleep.SleepMonitorService`：夜间守护前台服务。
- `com.gouxiong.sleep.AlarmActivity`：强唤醒确认页。
- `com.gouxiong.sleep.data.SleepDatabase`：SQLite 数据库。
- `com.gouxiong.sleep.model.SleepEvent`：事件模型。
- `com.gouxiong.sleep.util.PreferenceStore`：本地设置。
- `com.gouxiong.sleep.util.Theme`：颜色、字号、组件样式。
- `com.gouxiong.sleep.util.EmergencyNotifier`：电话/短信升级。

## MVP 实现边界

首版重点是完整闭环，不追求医疗级算法：

- 能开始/停止守护。
- 能在锁屏/后台保持前台服务。
- 能记录事件和每晚汇总。
- 能学习本机安静声音/动作基线，并把动态阈值写入事件证据。
- 能触发强唤醒页。
- 能配置紧急联系人。
- 能生成本地报告和趋势雏形。

暂不实现：

- 云端账号。
- 服务器同步。
- 医疗诊断。
- 复杂 ML 模型。
- 真正声纹识别。

## 构建方式

本机无全局 Gradle，因此首版使用 Android SDK 直编：

1. `aapt2` 编译资源并生成 `R.java`。
2. `javac` 编译 Java 源码。
3. `d8` 生成 `classes.dex`。
4. 将 dex 写入 APK。
5. `zipalign` 对齐。
6. `apksigner` 使用 debug keystore 签名。

构建脚本：`android-app/build.ps1`。

输出：`android-app/build/outputs/apk/gouxiong-sleep-debug.apk`。

## 权限策略

- 首次启动不一次性请求所有权限。
- 开始守护时请求麦克风、通知相关权限。
- 启用紧急联系人时请求电话/短信权限。
- 蓝牙音箱优先走系统音频路由，不自建复杂蓝牙连接。

## 验收

- 断网可用。
- 无注册、无服务器。
- APK 可安装。
- 首页大按钮可开始守护。
- 守护服务能记录事件。
- 强唤醒页能确认“我没事”。
- 紧急联系人设置不上传。
