# 2026-05-23 诚实检查与页面精简记录

## 本次改动

- 首页改为操作优先：压缩睡眠场景图，移除一句式介绍，把“开始守护”“睡前检查”“睡眠报告”“诚实检查”“小助手”放到首屏。
- 设置页删除重复说明，联网账号页删除宣传型小助手文案，改为“账号/服务端/诚实检查”的状态入口。
- 服务能力自检改名为“诚实检查”，用短清单显示服务端、短信、模型、实时语音、麦克风、2D Avatar、故事/助眠音、新闻、Key 安全。
- 麦克风拾音验证删除长说明，只显示权限、AudioRecord、PCM 帧、声音变化、结论。
- 通用设置按钮、首页小入口、底部导航和诚实检查清单行都降低高度，减少翻页和滚动。
- 语音能力拆成两层：`麦克风` 只证明 AudioRecord 有声音；`听懂人话` 必须等系统识别或服务端实时 ASR 返回文字后才算通过。
- 实时陪伴页改为云端实时 ASR 优先：连上 `/api/live/session` 后立即上传 PCM，服务端 `aliyun_realtime` 最终转写会驱动小助手进入对话；6.5 秒内未连通才降级到手机系统 `SpeechRecognizer`。
- 睡眠守护拾音不再只看“服务是否启动”：守护服务会记录 AudioRecord 是否启动、读帧次数、RMS、峰值和错误，并显示在诚实检查里。
- 睡眠守护声音阈值从旧的假设基线 `1800` 改为真实底噪学习，默认基线降为 `120`；冷启动或样本不足时不再沿用过高旧基线。
- 全局真实性检查继续收紧：模型显示为“Key已配，需探测”，实时语音显示为“链路已配，真机待验”，服务端 `/health` 新增 `truth` 闸门。
- 真机验收失败后追加修复：守护拾音通过必须是 90 秒内新读帧；前台麦克风服务启动失败会写入诚实检查；“您睡了么？”等待确认改为独立持久状态，只有明确“没睡/继续”或“睡了/守护”才结束确认。

## 模拟器验证

- `android-app/test.ps1` 通过。
- `android-app/e2e-live-voice.ps1` 通过：模拟语音识别文本能进入 `/api/live/session`，触发小助手回复、语音快捷入口和“您睡了么？”睡眠确认流程；实时 PCM 和模型音频链路均有帧。
- `server npm test` 通过。
- 已安装到模拟器 `emulator-5554` 并冷启动截图。
- 麦克风现场验证仍诚实显示：权限通过、AudioRecord 启动、PCM 83 帧，但 RMS 仅 `0.0001-0.0002`，所以结论为“未证明真实拾音”。
- 当前 E2E 结果：`live_audio_frames=20`、`live_model_audio_frames=1`、`live_interrupt=true`、`sleep_check_awake_reply=true`、`sleep_check_asleep_reply=true`、`sleep_check_no_reply=true`。
- 模拟器清空数据后启动睡眠守护：18 秒内守护 AudioRecord 读到 36 次，RMS `2.8`，峰值 `8`，错误为空；该结果证明守护服务真实读帧，但模拟器环境仍无法替代真机播放鼾声/咳嗽样本验收。

## APK

- 文件：`artifacts/apk/shuileme-debug-20260523-compact-honest.apk`
- SHA256：`2247D7A6E269FFAD757833BFD3FD93EDEE2C480431A1647E4356350C4A869586`

## 截图

- 首页：`artifacts/debug-ui/home-compact-final2.png`
- 麦克风验证：`artifacts/debug-ui/honest-mic-compact-final2.png`
