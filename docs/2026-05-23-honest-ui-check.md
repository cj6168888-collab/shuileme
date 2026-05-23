# 2026-05-23 诚实检查与页面精简记录

## 本次改动

- 首页改为操作优先：压缩睡眠场景图，移除一句式介绍，把“开始守护”“睡前检查”“睡眠报告”“诚实检查”“小助手”放到首屏。
- 设置页删除重复说明，联网账号页删除宣传型小助手文案，改为“账号/服务端/诚实检查”的状态入口。
- 服务能力自检改名为“诚实检查”，用短清单显示服务端、短信、模型、实时语音、麦克风、2D Avatar、故事/助眠音、新闻、Key 安全。
- 麦克风拾音验证删除长说明，只显示权限、AudioRecord、PCM 帧、声音变化、结论。
- 通用设置按钮、首页小入口、底部导航和诚实检查清单行都降低高度，减少翻页和滚动。
- 语音能力拆成两层：`麦克风` 只证明 AudioRecord 有声音；`听懂人话` 必须等 SpeechRecognizer 返回文字后才算通过。
- 实时陪伴页默认让系统中文语音识别优先占用麦克风，连续 PCM 传服务端先暂停，避免两个录音入口抢麦导致“有拾音但小助手没反应”。

## 模拟器验证

- `android-app/test.ps1` 通过。
- `android-app/e2e-live-voice.ps1` 通过：模拟语音识别文本能进入 `/api/live/session`，触发小助手回复、语音快捷入口和“您睡了么？”睡眠确认流程。
- 已安装到模拟器 `emulator-5554` 并冷启动截图。
- 麦克风现场验证仍诚实显示：权限通过、AudioRecord 启动、PCM 83 帧，但 RMS 仅 `0.0001-0.0002`，所以结论为“未证明真实拾音”。
- 当前 E2E 中 `live_audio_frames=0` 是预期结果：为保证“听懂人话”优先，原始 PCM 连续上传被标记为 `paused_for_speech_recognizer`，不再抢系统语音识别。

## APK

- 文件：`artifacts/apk/shuileme-debug-20260523-compact-honest.apk`
- SHA256：`9DAD1C58C66E45E9A2D74B5DC9581245A08BE562EBAB85537AA0DC902D83FD68`

## 截图

- 首页：`artifacts/debug-ui/home-compact-final2.png`
- 麦克风验证：`artifacts/debug-ui/honest-mic-compact-final2.png`
