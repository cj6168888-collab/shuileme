# 2026-05-23 诚实检查与页面精简记录

## 本次改动

- 首页改为操作优先：压缩睡眠场景图，移除一句式介绍，把“开始守护”“睡前检查”“睡眠报告”“诚实检查”“小助手”放到首屏。
- 设置页删除重复说明，联网账号页删除宣传型小助手文案，改为“账号/服务端/诚实检查”的状态入口。
- 服务能力自检改名为“诚实检查”，用短清单显示服务端、短信、模型、实时语音、麦克风、2D Avatar、故事/助眠音、新闻、Key 安全。
- 麦克风拾音验证删除长说明，只显示权限、AudioRecord、PCM 帧、声音变化、结论。
- 通用设置按钮、首页小入口、底部导航和诚实检查清单行都降低高度，减少翻页和滚动。

## 模拟器验证

- `android-app/test.ps1` 通过。
- 已安装到模拟器 `emulator-5554` 并冷启动截图。
- 麦克风现场验证仍诚实显示：权限通过、AudioRecord 启动、PCM 83 帧，但 RMS 仅 `0.0001-0.0002`，所以结论为“未证明真实拾音”。

## APK

- 文件：`artifacts/apk/shuileme-debug-20260523-compact-honest.apk`
- SHA256：`96892C10878CB6AE35743671075D051385BE301A0B27B4A9E8036F39B634AFA8`

## 截图

- 首页：`artifacts/debug-ui/home-compact-final2.png`
- 麦克风验证：`artifacts/debug-ui/honest-mic-compact-final2.png`
