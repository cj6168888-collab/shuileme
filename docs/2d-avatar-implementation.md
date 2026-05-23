# 2D Avatar 小助手实现说明

更新时间：2026-05-18

## 当前定位

本阶段先落地原生分层 2D Avatar，不直接接入 Live2D SDK。目标是把小助手从“单张头像动画”升级为可验证的数字人状态机：听、想、说、插话、看东西、安慰和紧急叫醒都由状态驱动。

## 已实现

- `AvatarState`：定义 `idle/listening/user_speaking/thinking/speaking/interrupted/seeing/reading/finding/comforting/happy/worried/urgent_wakeup`。
- `AvatarCommand`：定义 `setState/setEmotion/startSpeaking/stopSpeaking/mouthLevel/blink/nod/lookAtUser/lookDown/wave/urgentWake`。
- `AvatarView`：原生 Canvas 分层绘制头部、头发、眼睛、眉毛、嘴、脸颊、身体和手势；同时支持载入 `avatar_2d_*` 大半身 PNG，在视频聊天模式下作为主视觉，并把说话动效贴近脸部/嘴部而不是落在身体区域。
- 自主微动作：`AvatarView` 会按状态自动眨眼、呼吸、点头、挥手、嘴型变化和轻微眼神移动。听用户说话时眼神会缓慢找声源，思考/阅读时会低头，安慰状态会放软视线；这些动作由 `seedStateGaze` 和 `updateAutonomousGaze` 驱动，纳入 `android-app/test.ps1` 静态验收。
- 小助手 Live 页面：`createLiveAvatarStage` 已改为创建 `AvatarView`，并按当前角色绑定对应生成半身资产。
- 说话落点：TTS 播报结束后会通过 `settleCompanionAvatarAfterSpeech` 回到真实状态。实时语音回到 `listening`；本地助眠音播放中回到 `comforting`；睡着确认中回到等待用户回应，避免 Avatar 假停在“说话中”。
- 显示模式切换：小助手页默认视频聊天模式，显示大半身 2D 角色、声波和必要控制，不显示对话文字气泡；点击右上角小“文”按钮可切到文字聊天模式，显示大字文本气泡并隐藏大半身角色。
- 实时语音状态：
  - 打开麦克风：`listening`
  - 用户开始说话：`user_speaking`
  - 模型/本地思考：`thinking`
  - 模型或 TTS 说话：`speaking`
  - 用户插话或主动打断：`interrupted` 后回到 `listening`
- 视觉状态：
  - 快速看一眼：`seeing`
  - 读药瓶、报告、文字：`reading`
  - 找东西：`finding`
- 紧急叫醒：强唤醒页使用同一个 `AvatarView`，状态为 `urgent_wakeup`。
- 嘴型：
  - 阿里 Realtime/服务端模型音频 PCM 帧播放时，APK 根据 PCM16 RMS 能量驱动 `mouthLevel`。
  - 视频聊天模式的大半身 PNG 尚未拆层，当前用角色脸部坐标上的轻量嘴部开合提示和整体彩色声波配合音频能量，避免把“说话”表现成身体下方的条形波。
  - 系统 TTS 没有暴露原始音频能量，当前用定时嘴型驱动保持说话感，并在文档与 `/health` 中标明。

## 服务健康标记

`/health` 已新增 2D Avatar 能力标记：

- `local_2d_avatar_view: true`
- `avatar_state_machine: true`
- `avatar_command_protocol: true`
- `avatar_speech_settle: true`
- `mouth_level_protocol: true`
- `pcm_energy_mouth_driver: true`
- `tts_timed_mouth_driver: true`
- `emotion_label_protocol: true`
- `model_emotion_tags: true`
- `live2d_sdk: false`

这表示当前已完成本机 2D Avatar 状态机，并且服务端 Live 回复会向 APK 发送结构化情绪事件：`emotion/intensity/gesture/safety_level/speech_text/source`。APK 会记录这些字段，并映射到 `AvatarCommand.setEmotion`、`wave/nod/lookAtUser/lookDown/urgentWake` 等手势指令。当前还没有接入 Live2D SDK，结构化情绪主要由服务端对模型回复与用户语义做稳定推断，不等同于完整 Live2D 表情包或模型原生动画控制。

## 验收

- `android-app/test.ps1` 会检查：
  - Live 主视觉必须是 `AvatarView`
  - 不能回退为单张图片动画
  - 必须存在文字/视频聊天模式切换
  - 四个 `avatar_2d_*` 大半身资源必须存在
  - 必须存在状态机、指令协议、嘴型输入和紧急叫醒状态
  - 波形动画不能修改布局尺寸
- `android-app/e2e-assistant-ui-mode.ps1` 会在模拟器安装 APK，真实点击“文/视频”切换，验证视频模式不显示文字气泡、文字模式不保留大半身 Avatar，并截图留存。
- `android-app/e2e-avatar-states.ps1` 会在模拟器安装 APK，真实触发 Camera2 “看一眼”调试入口并等待 `last_vision_capture_*` 写入；随后触发噩梦强唤醒演练，确认 `AlarmActivity` 前台运行并记录 `urgent_wakeup`。`AlarmActivity` 内的紧急 `AvatarView` 仍由静态检查保证，因为全屏唤醒页在部分模拟器上无法稳定导出 UI tree。这让 `seeing` 和 `urgent_wakeup` 不只停留在单纯代码路径。
- `server/npm test` 会检查 `/health` 中 2D Avatar 的真实能力标记。

## 当前限制

- 当前视频模式已接入生成半身 PNG，但这些 PNG 尚未拆层；它是可运行的视频聊天主视觉，不是完整 Live2D。
- Live2D SDK 未接入，暂不具备专业 Live2D 物理摆动、模型绑定和美术表情包。
- 系统 TTS 的嘴型不是音频能量真驱动；模型 PCM 音频帧才是能量驱动。
- 模型情绪标签已通过服务端 `emotion` 事件进入 APK，并能驱动 Avatar 状态与手势；但它仍是服务端稳定推断层，不是模型直接输出的完整动画剧本。

## 下一阶段

- 为四个助手补正式分层 PNG 或 Live2D 美术资产。
- 当前图像模型生成的四个角色资产已归档在 `docs/assets/avatars/`，并同步为 Android `avatar_2d_*` 资源；详见 `docs/2d-avatar-art-assets.md`。
- 让模型直接输出更细的 `speech_text/emotion/intensity/gesture/safety_level` 动画剧本，并保留服务端安全兜底。
- 下一步继续把 `AvatarView` 的手势、眼神和情绪过渡接入更多真实事件，例如新闻读取、睡前故事段落、助眠音淡出和入睡确认。
