# 小智开源项目吸收方案

版本：V1.0  
评估对象：[TOM88812/xiaozhi-android-client](https://github.com/TOM88812/xiaozhi-android-client)  
本地评估快照：`50916e3`  
许可证：Apache License 2.0

## 1. 结论

小智项目适合借鉴实时互动体验，但不适合直接整包塞进当前睡了么 APK。

原因：

- 小智客户端是 Flutter 多平台工程；睡了么当前是 Java 原生直编 APK。
- 小智的核心交互依赖 Flutter 插件：`web_socket_channel`、`opus_dart`、`opus_flutter`、`record`、`flutter_pcm_player`、`flutter_sound`、`audio_session`。
- 小智 README 中一部分 Live2D、实时打断、服务端、用户系统等能力标注为商业版或深度适配自研服务端，不能默认全部按开源能力复用。
- Apache-2.0 允许代码复用，但人物、图片、模型、演示图等资产仍要逐项核对，不能直接拿来做睡了么的品牌形象。

推荐做法：

- 不在老人主界面展示小智品牌、截图、模型资产；如复制测试素材，只作为本地集成参考并保留来源说明。
- 不迁入 Flutter 运行时。
- 吸收协议和架构：实时 WebSocket、Opus 音频帧、abort 中断、TTS/STT/Emotion 事件。
- 用睡了么自己生成的 `gxs_*` 3D 小助手资产做形象。

当前工程状态：

- 已前置申请摄像头、麦克风、媒体读取、通知、电话和短信权限；老人首次打开即可看到系统权限弹窗。
- 当前 APK 的实时聊天已经能启动 Android 语音识别服务，并优先通过 `/api/live/session` WebSocket 把识别文本发送给服务端；同一会话也会发送 16kHz/30ms raw PCM16 麦克风帧。APK 已消费服务端 `sentence_delta` 增量，用于气泡实时刷新和分段 TTS 排队。服务端已具备可选阿里 Realtime 桥接，可把 PCM16 转成 `input_audio_buffer.append` 并回传流式转写、文本增量和模型音频帧；APK 已用 `AudioTrack` 接入模型音频帧低延迟播放路径。用户插话或点击头像时，APK 会停止本地播放并发送小智式 `abort`，服务端会向 Realtime 桥转发 `response.cancel` 和 `input_audio_buffer.clear`；小助手说话或模型音频播放期间，APK 也会用实时 PCM RMS、自适应噪声底和 240ms 连续语音门做保守自动插话检测。普通 HTTP 聊天只作为 WebSocket 失败时的兜底。
- 当前 APK 的语音输出仍是 Android `TextToSpeech` 兜底，已按四个角色做声线参数和男/女声优先匹配；如果手机只安装了一个中文女声包，系统 TTS 仍无法保证“懂事小弟”“阳光小哥”一定变成真实男声。
- 小智式“像真人一样实时打断、低延迟说话”的关键不是简单调 pitch/rate，而是 WebSocket 长连接 + 16kHz 短帧 Opus 或 PCM 音频流 + 服务端 STT/LLM/TTS 流式返回。睡了么已落地 WebSocket 文本回合、模型文本增量、30ms PCM 音频帧传输、低延迟 PCM 播放和服务端 Realtime 取消事件，仍需补 Opus 编解码、真实设备回音消除和真实公网 Realtime 长时稳定性验收。
- 阿里百炼/DashScope、短信和其他模型 Key 不能明文封装进 APK。正式版必须走安全服务端代理，否则 APK 被反编译后 Key 会直接泄露。

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
    "frame_duration": 30
  }
}
```

### 2.2 音频流

小智音频策略：

- 麦克风采集 PCM16。
- 16kHz、单声道。
- 按 30ms 或服务端协商帧编码成 Opus。
- Opus 二进制帧直接发 WebSocket。
- 服务端返回二进制 Opus 帧。
- 客户端解码成 PCM 后直接喂播放器。

这条思路适合睡了么后续替换当前 `SpeechRecognizer + TTS` 兜底链路。

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
    "frame_duration": 30
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

睡了么可以借鉴这个语义：老人点击头像、说“我来说”、或后续检测到插话时，先停止本地播放，再给实时服务端发 `abort`。

### 2.5 服务端事件

小智处理的文本事件：

- `tts`：服务端开始播一句话，`state=sentence_start`，携带 `text`。
- `stt`：服务端识别到用户话，携带 `text`。
- `emotion`：服务端返回表情或情绪状态，携带 `emotion`。
- 二进制消息：服务端返回 Opus 音频帧。

睡了么 UI 可映射：

| 小智事件 | 睡了么 UI |
| --- | --- |
| `tts sentence_start` | 小助手进入“我慢慢说” |
| `stt` | 气泡显示“我听到了” |
| `emotion` | 角色切到听、想、安抚、开心等姿态 |
| binary audio | 播放实时语音 |
| `abort` | 停止播放，进入“我在听” |

## 3. 不能直接照搬的部分

- Flutter UI：当前 APK 不是 Flutter，直接搬会导致工程体系重构。
- Live2D 商业版能力：README 标注商业版/深度适配服务端，不能作为开源可用能力承诺。
- 商业服务端：睡了么不能依赖未知商业接口作为安全底座。
- 人物模型/图片：必须视作第三方资产，除非逐一确认授权。
- 用户会员、支付、设备管理：不符合睡了么的中老年简化定位。

## 4. 睡了么吸收顺序

### 阶段 A：协议骨架

已开始：

- 在 Java 原生工程中建立小智式协议消息构造器。
- 保留当前服务端文本兜底；阿里多模态负责后续文本、图片和音频理解，DeepSeek 只可作为低成本文字兜底。
- UI 上维持 Live 舞台。

目标：

- 让后续真实 WebSocket 引擎可以直接接入，不再改 UI。

### 阶段 B：原生 WebSocket + Opus

已开始：

- 原生 Java WebSocket 客户端。

已落地到当前 APK 工程：

- `NativeWebSocketClient`：原生 `ws/wss` 客户端，不依赖 Flutter 或第三方 WebSocket 库；支持自定义 headers、HTTP Upgrade、`Sec-WebSocket-Accept` 校验、文本帧、二进制帧、ping/pong、close 和分片消息。
- `LiveCompanionSession`：小智式实时会话包装；连接时发送 `hello`，保留 `device-id`、`client-id`、`protocol-version`、`Authorization` headers，并暴露 `startCall`、`listen`、`abort`、`voiceMute`、`sendTextInput`、`sendPcmFrame`、`tts/stt/emotion/audio` 回调；APK 端会消费 `sentence_delta` 增量并驱动气泡与分段 TTS。
- `LivePcmRecorder`：16kHz、单声道、30ms PCM16 帧采集；优先使用 `VOICE_COMMUNICATION` 音源，并打开系统回音消除、降噪和自动增益，为后续 Opus 编码做准备。
- 服务端 `/api/live/session`：已提供 Bearer 鉴权 WebSocket，会返回 `hello/stt/tts/emotion/audio_received`，能接收 raw PCM16 音频帧并对 `input_text` 走长期记忆和模型文本增量回答；显式开启 `ALIYUN_REALTIME_ENABLED=1` 后，会桥接阿里 Realtime，回传 `stt`、`sentence_delta/sentence_end` 和二进制模型音频帧；收到 `abort` 后会向 Realtime 桥发送 `response.cancel` 和 `input_audio_buffer.clear`。
- `MainActivity` 实时语音页：系统 `SpeechRecognizer` 得到文本后优先调用 `LiveCompanionSession.sendTextInput()`；发送在线程中执行，避免 Android 主线程网络异常；同时启动 `LivePcmRecorder` 把麦克风 PCM 帧发给服务端；端到端脚本会确认服务端以 `live_voice` 来源落库，并确认 APK 已发送实时音频帧。

仍需要补：

- Opus 编码/解码库选择。
- 真实阿里 Realtime 账号环境下的流式 ASR 长时稳定性验收。
- 真实公网模型音频流输出的长时播放、自然语音插话阈值标定、回音消除和音频焦点联调。
- 音频焦点、回音消除、噪声抑制的真机调优。

### 阶段 C：中断与状态机

需要补：

- 点击头像/按钮发送 `abort`。
- 用户插话检测后发送 `abort`；当前 APK 已具备基于 PCM RMS 的保守触发和调试 E2E，仍需真机校准。
- 服务端 `tts/stt/emotion` 映射小助手姿态。
- 播放队列可清空，500ms 内回到听。

### 阶段 D：视觉形象

原则：

- 不把小智机器人当作老人端陪伴人物；老人端继续使用睡了么四个亲人化小助手。
- 用睡了么 `gxs_*` 角色。
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
- 不把小智的图片、模型、商标、UI 直接作为睡了么资产。

当前阶段已吸收实时语音处理思路，并复制 `robot_3.png` 为本地语音核心参考图；老人端 UI 不直接展示该机器人形象。
