# 服务端代理与长期关怀记忆

## 目标

睡了么从纯本地 APK 升级为“APK + 服务端代理”后，模型 Key 不再进入 APK。服务端负责手机号验证码注册、用户档案、聊天洞察、异常/经济风险记录、消息队列和大模型代理。

## 数据边界

- APK 本地仍保留夜间唤醒、紧急联系人、基础档案和本机兜底。
- 服务端保存长期记忆：身体状况、用药习惯、睡眠情况、家庭情况、兴趣爱好、关怀偏好。
- 聊天中出现用药、健康、经济风险、异常睡眠、情绪、家庭情况时，APK 会分类上传为 `insights`。
- 服务端回答仍是生活建议，不做诊断，不替代医生。

## MVP 接口

服务端目录：`server/`

- `GET /admin`：本地后台页面，用于查看用户、档案、长期记忆和下发小助手消息。
- `POST /api/auth/request-code`：发送验证码。开发模式返回 `dev_code`。
- `POST /api/auth/verify`：验证码登录，返回 Bearer Token。
- `PUT /api/profile`：同步主人档案和小助手身份。
- `POST /api/insights`：上传聊天/视觉/状态中的长期关怀记忆。
- `POST /api/chat`：服务端拼接长期记忆后代理阿里百炼/DashScope 文本模型，DeepSeek 只保留为可选文字兜底。
- `POST /api/care/brief`：主动关怀专用接口，按喝水、吃药、晨间简报、睡眠异常唤醒等场景生成可直接朗读的话术，并可入队为待朗读消息。
- `POST /api/vision`：服务端代理阿里百炼/DashScope 视觉模型请求。药瓶、报告、物品识别、“看世界”等图片任务默认走 `ALIYUN_VISION_MODEL`。
- `POST /api/audio`：服务端代理阿里百炼/DashScope 音频模型或结构化声波摘要分析，用于睡眠声音辅助判断，不做诊断。
- `GET /api/live/session`：小智式实时陪伴 WebSocket，需要 Bearer Token；支持 `hello/start/listen/abort/input_text` 文本事件、`sentence_delta` 模型文本增量、raw PCM16 二进制音频帧接收确认；显式开启 `ALIYUN_REALTIME_ENABLED=1` 后，会把 PCM16 帧桥接到阿里 Realtime WebSocket，并把流式转写、模型文本增量和模型音频帧映射回 APK 协议；收到 APK `abort` 时会向 Realtime 桥转发 `response.cancel` 和 `input_audio_buffer.clear`。
- `GET /api/messages/pending`：APK 拉取小助手要读给用户听的消息。
- `POST /api/messages/{id}/read`：消息已读。
- `GET /api/admin/users`：管理端用户列表，需要独立管理令牌。
- `GET /api/admin/users/{id}`：管理端查看用户档案、洞察和消息。
- `POST /api/admin/users/{id}/messages`：管理端给某个用户下发待朗读消息。

## APK 行为

- “我的小助手”增加“手机号注册 / 服务端”。
- 默认服务端地址为 `http://10.0.2.2:8787`，供 Android 模拟器访问电脑本机。
- 注册成功后，聊天、看图和自动洞察优先走服务端。
- 小助手实时语音页优先用 `/api/live/session` WebSocket 发送系统识别文本并接收模型流式文本回复，APK 会消费 `sentence_delta` 来更新气泡并分段排队 TTS，同时发送 16kHz/30ms raw PCM16 麦克风帧；连接或发送失败时再回退 `/api/chat`。服务端已具备阿里 Realtime 桥接代码，可转发 PCM16 做流式 ASR，并能把模型音频帧回传；APK 已接入 `AudioTrack` PCM 流式播放器，可播放回传的模型音频帧。用户插话或点击头像触发中断时，APK 会停止本地播放并发送 `abort`，服务端再取消 Realtime 当前响应和清空输入缓冲。
- 健康档案、今日状态、首次认识信息会自动同步服务端。
- 进入聊天页时自动拉取服务器消息，并让小助手朗读。
- 服务能力自检会显示实时陪伴 WebSocket、文本流式、Realtime 桥接、服务端 ASR、模型音频回传和 APK 低延迟播放是否可用；未显式开启 Realtime 时，仍应显示服务端 ASR 和模型音频输出未启用。

## 生产化要求

- 关闭 `GOUXIONG_DEV_SMS=1`，接入真实短信服务。
- 设置 `GOUXIONG_SERVER_SECRET`，不能使用自动生成的本地密钥。
- 设置强随机 `GOUXIONG_ADMIN_TOKEN`，并限制管理接口只能从后台或内网访问。
- 配置 `DASHSCOPE_API_KEY` 后再开放“读药瓶、读报告、看合同、听睡眠声音”等强依赖多模态理解的能力；未配置时 APK 不崩溃，只给陪伴式兜底提醒。
- 数字人不是普通语言模型能力，需要另接阿里云数字人/RTC/流媒体会话。当前服务端已具备 WebSocket 会话骨架、可选 Realtime 桥接和 APK PCM 播放器，但仍不能把静态 TTS/图片或音频帧播放假装成真人数字人。
- 使用 HTTPS，APK 生产包关闭 `android:usesCleartextTraffic`。
- 增加数据库备份、数据删除、家属授权查看、风控审计和隐私协议。
- 对健康和用药内容继续保持非诊断边界。
