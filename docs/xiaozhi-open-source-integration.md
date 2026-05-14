# 小智开源项目吸收方案

版本：V1.0  
评估对象：[TOM88812/xiaozhi-android-client](https://github.com/TOM88812/xiaozhi-android-client)  
本地评估快照：`50916e3`  
许可证：Apache License 2.0

## 1. 结论

小智项目适合借鉴实时互动体验，但不适合直接整包塞进当前狗熊睡眠 APK。

原因：

- 小智客户端是 Flutter 多平台工程；狗熊睡眠当前是 Java 原生直编 APK。
- 小智的核心交互依赖 Flutter 插件：`web_socket_channel`、`opus_dart`、`opus_flutter`、`record`、`flutter_pcm_player`、`flutter_sound`、`audio_session`。
- 小智 README 中一部分 Live2D、实时打断、服务端、用户系统等能力标注为商业版或深度适配自研服务端，不能默认全部按开源能力复用。
- Apache-2.0 允许代码复用，但人物、图片、模型、演示图等资产仍要逐项核对，不能直接拿来做狗熊睡眠的品牌形象。

推荐做法：

- 不复制小智品牌、人物、截图、模型资产。
- 不迁入 Flutter 运行时。
- 吸收协议和架构：实时 WebSocket、Opus 音频帧、abort 中断、TTS/STT/Emotion 事件。
- 用狗熊睡眠自己生成的 `gxs_*` 3D 小助手资产做形象。

## 2. 小智核心链路

### 2.1 WebSocket 连接

小智通过 WebSocket 建立长连接，移动端发送 headers：

- `device-id`
- `client-id`
- `protocol-version: 1`
- `Authorization: Bearer <token>`

连接后发送 hello：

```json
{
  "type": "hello",
  "version": 1,
  "transport": "websocket",
  "audio_params": {
    "format": "opus",
    "sample_rate": 16000,
    "channels": 1,
    "frame_duration": 60
  }
}
```

### 2.2 音频流

小智音频策略：

- 麦克风采集 PCM16。
- 16kHz、单声道。
- 按 60ms 帧编码成 Opus。
- Opus 二进制帧直接发 WebSocket。
- 服务端返回二进制 Opus 帧。
- 客户端解码成 PCM 后直接喂播放器。

这条思路适合狗熊睡眠后续替换当前 `SpeechRecognizer + TTS` 兜底链路。

### 2.3 听说状态

开始监听：

```json
{
  "session_id": "<session>",
  "type": "listen",
  "state": "start",
  "mode": "auto"
}
```

停止监听：

```json
{
  "session_id": "<session>",
  "type": "listen",
  "state": "stop",
  "mode": "auto"
}
```

开始通话：

```json
{
  "type": "start",
  "mode": "auto",
  "audio_params": {
    "format": "opus",
    "sample_rate": 16000,
    "channels": 1,
    "frame_duration": 60
  }
}
```

### 2.4 打断

小智的打断消息：

```json
{
  "session_id": "<session>",
  "type": "abort",
  "reason": "wake_word_detected"
}
```

狗熊睡眠可以借鉴这个语义：老人点击头像、说“我来说”、或后续检测到插话时，先停止本地播放，再给实时服务端发 `abort`。

### 2.5 服务端事件

小智处理的文本事件：

- `tts`：服务端开始播一句话，`state=sentence_start`，携带 `text`。
- `stt`：服务端识别到用户话，携带 `text`。
- `emotion`：服务端返回表情或情绪状态，携带 `emotion`。
- 二进制消息：服务端返回 Opus 音频帧。

狗熊睡眠 UI 可映射：

| 小智事件 | 狗熊睡眠 UI |
| --- | --- |
| `tts sentence_start` | 小助手进入“我慢慢说” |
| `stt` | 气泡显示“我听到了” |
| `emotion` | 角色切到听、想、安抚、开心等姿态 |
| binary audio | 播放实时语音 |
| `abort` | 停止播放，进入“我在听” |

## 3. 不能直接照搬的部分

- Flutter UI：当前 APK 不是 Flutter，直接搬会导致工程体系重构。
- Live2D 商业版能力：README 标注商业版/深度适配服务端，不能作为开源可用能力承诺。
- 商业服务端：狗熊睡眠不能依赖未知商业接口作为安全底座。
- 人物模型/图片：必须视作第三方资产，除非逐一确认授权。
- 用户会员、支付、设备管理：不符合狗熊睡眠的中老年简化定位。

## 4. 狗熊睡眠吸收顺序

### 阶段 A：协议骨架

已开始：

- 在 Java 原生工程中建立小智式协议消息构造器。
- 保留当前 DeepSeek 文本兜底。
- UI 上维持 Live 舞台。

目标：

- 让后续真实 WebSocket 引擎可以直接接入，不再改 UI。

### 阶段 B：原生 WebSocket + Opus

已开始：

- 原生 Java WebSocket 客户端。

已落地到当前 APK 工程：

- `NativeWebSocketClient`：原生 `ws/wss` 客户端，不依赖 Flutter 或第三方 WebSocket 库；支持自定义 headers、HTTP Upgrade、`Sec-WebSocket-Accept` 校验、文本帧、二进制帧、ping/pong、close 和分片消息。
- `LiveCompanionSession`：小智式实时会话包装；连接时发送 `hello`，保留 `device-id`、`client-id`、`protocol-version`、`Authorization` headers，并暴露 `startCall`、`listen`、`abort`、`voiceMute`、`sendOpusFrame`、`tts/stt/emotion/audio` 回调。
- `LivePcmRecorder`：16kHz、单声道、60ms PCM16 帧采集；优先使用 `VOICE_COMMUNICATION` 音源，并打开系统回音消除、降噪和自动增益，为后续 Opus 编码做准备。

仍需要补：

- Opus 编码/解码库选择。
- PCM 播放器，支持低延迟播放。
- 音频焦点、回音消除、噪声抑制。

### 阶段 C：中断与状态机

需要补：

- 点击头像/按钮发送 `abort`。
- 用户插话检测后发送 `abort`。
- 服务端 `tts/stt/emotion` 映射小助手姿态。
- 播放队列可清空，500ms 内回到听。

### 阶段 D：视觉形象

原则：

- 不拿小智人物。
- 用狗熊睡眠 `gxs_*` 角色。
- 生成听、说、想、安抚、看一眼等姿态帧。
- 未来可接 Live2D 或轻量骨骼动画，但模型必须原创或授权清楚。

## 5. 许可证处理

如果后续复用小智代码片段：

- 保留 Apache-2.0 许可证文本。
- 在 `NOTICE` 或第三方声明中标注：
  - 项目名：xiaozhi-android-client
  - 作者/仓库：TOM88812/xiaozhi-android-client
  - 许可证：Apache License 2.0
  - 使用范围：WebSocket/Opus/实时语音协议参考或派生代码
- 不把小智的图片、模型、商标、UI 直接作为狗熊睡眠资产。

当前阶段只做架构参考和协议重写，未复制小智源码到 APK。
