# Gemini Live 接入路线

版本：V1.0  
日期：2026-05-14  
目标：把狗熊睡眠小助手从当前兜底语音链路升级为真正实时音视频会话。

## 1. 官方能力判断

按当前官方文档，Gemini Live API 适合实时低延迟语音/视频应用。Android 官方建议通过 Firebase AI Logic 在 Android App 内直接调用 Live API，不需要自建后端。

关键事实：

- Gemini Live API 面向实时低延迟语音支持，可流式处理输入和输出。
- Firebase AI Logic 方式仍是开发者预览版，可能有不兼容变更。
- Android 接入需要 Firebase AI Logic SDK，也就是 Gradle/Firebase 工程能力。
- 推荐 Live 模型示例是 `gemini-2.5-flash-native-audio-preview-12-2025`。
- Live 会话通过持久连接进行双向流式交互。
- Live API 的常见输入/输出音频格式是：输入 raw 16-bit PCM 16kHz little-endian，输出 raw 16-bit PCM 24kHz little-endian。
- Firebase AI Logic 的 Live API 能力页列出“Handling interruptions”当前还不支持。

结论：Gemini Live 是正确目标，但不能把当前 Firebase Android 预览版误说成已经完全支持 Gemini App 那种无缝打断。产品要保持两层设计：

1. UI 和本地兜底先做到 Live 形态。
2. 真正全双工、可打断能力后续接入更底层 Live WebSocket 或支持打断的实时模型。

## 2. 当前 APK 状态

当前 APK 已经具备 Live-first 外壳：

- 打开小助手页自动听。
- 没有发送按钮。
- 主界面是大头像 + 气泡 + 三个大动作。
- 支持按钮或点击头像打断 TTS 并重新听。
- 等待联网模型时先播放安抚语。
- 摄像头入口用“看一眼”，并有物品位置记忆。

当前仍是兜底链路：

```text
SpeechRecognizer -> 文本模型 -> Android TTS
```

限制：

- 不是真正音频流式模型。
- 助手说话时不能可靠用麦克风热词打断，否则容易把 TTS 自己识别成用户声音。
- 摄像头不是连续视频上下文，而是低清采样图。

## 3. 为什么不能直接在当前工程硬接 Firebase SDK

当前 APK 是无 Gradle 的 Java + Android SDK 直编工程。这条路线优点是交付快、可控、无外部依赖；缺点是不能直接引入 Firebase AI Logic SDK。

要接 Gemini Live Android 官方 SDK，需要做一次工程升级：

- 引入 Gradle 或迁移到 Kotlin/Java Gradle Android 项目。
- 加入 Firebase BoM 和 `com.google.firebase:firebase-ai`。
- 加入 Firebase 配置文件。
- 处理 API Key / Firebase App Check / 包名限制。
- 建立 `LiveModel` 和 `LiveSession`。

这不是简单加一个 Java 文件能完成的。

## 4. 推荐实施路线

### 阶段 1：保持当前直编 APK，完善 Live 形态

目标：先把老人端体验做对。

- 大头像面对面。
- 点击头像可打断。
- 三个大动作：我来说、暂停、看一眼。
- 本地安抚语先响。
- 物品记忆先查本机。
- 夜间守护不受联网影响。

验收：不用看文字、不点发送，也能连续完成三轮对话。

### 阶段 2：建立 Gradle 分支接 Firebase AI Logic

目标：接通官方 Android Live API。

任务：

- 新建 Gradle Android 工程或迁移当前工程。
- 接入 Firebase AI Logic。
- 配置 Live 模型。
- 麦克风 PCM 流式输入。
- 模型音频流式输出。
- 摄像头低清帧输入。
- 把模型状态映射为小助手动画：听、想、说、看。

验收：模型音频响应不再走 Android TTS。

### 阶段 3：处理打断能力

由于 Firebase AI Logic 当前列出的能力限制包括不支持 interruption handling，需要分两条路评估：

- 路线 A：继续 Firebase SDK，使用“点击头像/我来说”作为产品级打断。
- 路线 B：接更底层 Gemini Developer API WebSocket 或其他实时模型，自己处理打断、音频流、会话恢复和工具调用。

对于中老年产品，阶段 2 可以先上线“按钮/头像打断”；真正语音打断在阶段 3 单独验收。

### 阶段 4：工具调用

Live 模型需要能调用本机工具：

- 查询昨晚睡眠摘要。
- 查询药物提醒状态。
- 查询物品位置记忆。
- 记录今天状态。
- 打开摄像头看一眼。
- 设置喝水/吃药提醒。

夜间强唤醒、电话、短信不能交给云端模型决定，只能由本机安全规则触发。

## 5. Android Live 会话状态

App 内部统一用这些状态驱动 UI：

```text
IDLE
LISTENING
THINKING
SPEAKING
SEEING
INTERRUPTED
FALLBACK_TEXT
NETWORK_SLOW
```

状态映射：

- `LISTENING`：我在听您说。
- `THINKING`：您别急，我想想。
- `SPEAKING`：我慢慢说给您听。
- `SEEING`：我看一眼周围，帮您记住。
- `INTERRUPTED`：我停下了，您说。
- `FALLBACK_TEXT`：网络慢一点，我先陪您。

## 6. 隐私与安全

- 不把 Google/Firebase/Gemini Key 写入 APK。
- 如果使用 Firebase 直连，必须限制包名、SHA 指纹和配额。
- 摄像头只在聊天页和用户授权场景运行。
- 不保存连续视频，只保存结构化物品位置文字。
- 不上传亲人录音、联系人电话、整夜录音。
- 睡眠危机唤醒保留本地算法和本地倒计时。

## 7. 开发验收标准

阶段 1：

- 当前 APK 仍可无 Gradle 构建。
- 小助手页无发送按钮。
- 点击头像可进入“我来说”状态。
- 模拟器可打开 Live 页面截图。

阶段 2：

- Gradle 工程能编译。
- Firebase AI Logic LiveSession 能连接。
- 音频输入输出不再走 Android TTS。
- 断网时自动降级当前兜底链路。

阶段 3：

- 用户插话后，助手音频能在 500ms 内停止。
- 助手能恢复继续听。
- 不把模型自己的语音误当成用户。

阶段 4：

- 能用自然语言问“我的钥匙在哪”并调用本机物品记忆。
- 能问“昨晚睡得怎么样”并调用睡眠摘要。
- 能说“半小时后提醒我吃药”并创建本机提醒。
