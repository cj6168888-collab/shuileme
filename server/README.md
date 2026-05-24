# 睡了么服务端代理

轻量服务端用于手机号验证码注册、用户档案数据库、长期关怀记忆、结构化记忆抽取、模型 Key 代理和小助手消息队列。

主实现是 Node 25 + 内置 SQLite，无需 npm 安装第三方依赖。

## 本地启动

复制示例配置，不要把真实 Key 提交到仓库：

```powershell
cd D:\www\睡了么\server
Copy-Item .env.example .env
notepad .env
```

```powershell
cd D:\www\睡了么\server
npm.cmd test
npm.cmd run smoke:model
npm.cmd run ops:check
npm.cmd run backup
powershell -ExecutionPolicy Bypass -File .\start-server.ps1
```

`npm.cmd test` 使用临时数据库验证验证码、档案、洞察、兜底模型、管理端和消息队列。`npm.cmd run smoke:model` 也使用临时数据库和开发短信模式；只有服务端能读取到 `DASHSCOPE_API_KEY` / `ALIYUN_MODEL_API_KEY` / `ALIYUN_BAILIAN_API_KEY` 时才真实调用阿里文本、视觉和音频模型，音频测试会附带一段合成 WAV 片段，未配置 Key 时会跳过。`npm.cmd run smoke:realtime` 使用同一个桌面模型 Key 做最小 Realtime WebSocket 握手，不发送音频，成功时应返回 `session.created`。

默认监听 `http://0.0.0.0:8787`。Android 模拟器访问宿主机使用：

```text
http://10.0.2.2:8787
```

本地后台页面：

```text
http://127.0.0.1:8787/admin
```

打开后输入 `.env` 里的 `GOUXIONG_ADMIN_TOKEN`，可查看用户档案、长期记忆和消息队列，导出/删除服务端用户数据，并给用户下发一条小助手待朗读消息。

如果配置了 `GOUXIONG_ADMIN_BASIC_PASSWORD`，打开 `/admin` 前会先出现浏览器 Basic 登录框；这是页面级保护，进入页面后仍要输入 `GOUXIONG_ADMIN_TOKEN` 才能调用管理接口。`start-server.ps1` 在缺少本机密钥时会自动生成 `server/data/.server-secret`、`server/data/admin-token.txt`，并在默认 `0.0.0.0` 监听且没有配置后台页面密码时生成 `server/data/admin-page-password.txt`。

## 环境变量

- `GOUXIONG_PORT`：端口，默认 `8787`
- `GOUXIONG_DB_PATH`：SQLite 文件路径，默认 `server/data/gouxiong.sqlite3`
- `GOUXIONG_SERVER_SECRET`：会话签名密钥，生产环境必须设置
- `GOUXIONG_ADMIN_TOKEN`：管理接口令牌，用于查看用户档案、洞察和下发待朗读消息
- `GOUXIONG_ADMIN_BASIC_USER`：后台页面 Basic Auth 用户名，默认 `admin`
- `GOUXIONG_ADMIN_BASIC_PASSWORD`：后台页面 Basic Auth 密码；为空时本地页面不启用 Basic Auth，生产环境必须设置
- `GOUXIONG_BACKUP_DIR`：数据库备份目录，默认 `server/backups`
- `GOUXIONG_DEV_SMS`：`1` 时验证码直接在响应里返回 `dev_code`，仅供本地测试；真实环境必须为 `0`
- `GOUXIONG_OTP_PHONE_MAX_PER_MINUTE`：同一手机号每分钟验证码次数，默认 `1`
- `GOUXIONG_OTP_PHONE_MAX_PER_10M`：同一手机号 10 分钟验证码次数，默认 `5`
- `GOUXIONG_OTP_PHONE_MAX_PER_DAY`：同一手机号每天验证码次数，默认 `12`
- `GOUXIONG_OTP_IP_MAX_PER_10M`：同一来源 10 分钟验证码次数，默认 `30`
- `GOUXIONG_OTP_IP_MAX_PER_DAY`：同一来源每天验证码次数，默认 `200`
- `GOUXIONG_MEMORY_AI_EXTRACT`：`1` 时聊天记忆抽取会在本地规则基础上叠加阿里文本模型，默认 `1`
- `GOUXIONG_DISABLE_MODEL`：`1` 时强制关闭阿里/DeepSeek 模型通道，只走本地兜底；用于端到端脚本隔离 APK 链路，不建议生产环境开启
- `ALIYUN_MEMORY_MAX_TOKENS`：长期记忆抽取模型最大输出 token，默认 `600`
- `ALIYUN_SMS_ACCESS_KEY_ID` / `ALIYUN_SMS_ACCESS_KEY_SECRET`：阿里云短信 AccessKey，只放服务端
- `ALIYUN_SMS_SIGN_NAME`：阿里云短信签名名称
- `ALIYUN_SMS_TEMPLATE_CODE`：验证码模板 Code，模板变量默认使用 `code`
- `ALIYUN_SMS_REGION_ID`：默认 `cn-hangzhou`
- `ALIYUN_SMS_TEMPLATE_PARAM_KEY`：模板验证码变量名，默认 `code`
- `DASHSCOPE_API_KEY` / `ALIYUN_MODEL_API_KEY` / `ALIYUN_BAILIAN_API_KEY`：三者任选其一，阿里百炼/DashScope Key，只放服务端，不进入 APK
- `ALIYUN_MODEL_ENDPOINT`：默认 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
- `ALIYUN_TEXT_MODEL`：默认 `qwen-plus`，用于日常陪伴聊天和长期记忆分析
- `ALIYUN_VISION_MODEL`：默认 `qwen3-vl-plus`，用于看药瓶、读报告、识别物品和“看世界”
- `ALIYUN_AUDIO_MODEL`：默认 `qwen3-omni-flash`，用于睡眠声音/声波摘要分析
- `ALIYUN_REALTIME_ENABLED=1`：显式开启阿里 Realtime WebSocket 桥接。不开启时 `/api/live/session` 只接收 PCM16 帧并走文本兜底，不会产生实时 ASR 费用。
- `ALIYUN_REALTIME_API_KEY` / `DASHSCOPE_REALTIME_API_KEY`：Realtime 专用 Key；未单独配置时，只有在 `ALIYUN_REALTIME_ENABLED=1` 时才会复用 `DASHSCOPE_API_KEY`。
- `ALIYUN_REALTIME_MODEL`：默认 `qwen3-omni-flash-realtime`，用于 Live 会话的流式 ASR/音频响应桥接。
- `ALIYUN_REALTIME_ENDPOINT`：默认 `wss://dashscope.aliyuncs.com/api-ws/v1/realtime`；测试可指向本地假 WebSocket 后端。
- `ALIYUN_AVATAR_APP_ID` / `ALIYUN_AVATAR_ENDPOINT`：预留阿里云数字人会话配置；当前服务端健康检查只标记配置状态，不伪造数字人流媒体会话
- `LINLY_TALKER_STREAM_ENABLED=1`：启用 Linly-Talker-Stream sidecar 状态上报；它只作为可选数字人媒体层，不接管睡眠安全、记忆或陪伴大脑。
- `LINLY_TALKER_STREAM_URL`：Linly-Talker-Stream 后端地址，默认示例为 `http://127.0.0.1:8010`，健康检查会派生 `/offer`、`/human`、`/humanaudio` 等 sidecar 入口。
- `LINLY_TALKER_STREAM_WEB_URL`：Linly-Talker-Stream 前端页面地址。若为空会回退到 `LINLY_TALKER_STREAM_URL`，适用于后端直接托管 `web` 静态目录的部署；若使用 Vite dev server，可设为 `http://127.0.0.1:3000`。
- `LINLY_TALKER_STREAM_AVATAR_ENGINE`：当前 sidecar 的数字人引擎标识，建议第一版用 `wav2lip`，后续再评估 `musetalk`。
- `LINLY_TALKER_STREAM_TRANSPORT`：当前固定为 `webrtc`，用于提醒 APK 这是媒体会话，不是普通 HTTP 文本接口。
- `LINLY_TALKER_STREAM_TIMEOUT_MS`：服务端转发到 Linly sidecar 的超时时间，默认 `12000`。
- `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL`：可选文字兜底。阿里多模态已配置时优先使用阿里，不再把 DeepSeek 当作视觉、音频或数字人能力
- `VISION_API_KEY` / `VISION_ENDPOINT` / `VISION_MODEL`：历史兼容视觉通道；建议迁移到 `ALIYUN_VISION_MODEL`

## 核心接口

- `GET /health` / `GET /api/health`：健康检查
- `POST /api/auth/request-code`：发送手机号验证码
- `POST /api/auth/verify`：验证码登录，返回 Bearer token
- `PUT /api/profile`：同步主人健康、用药、睡眠、家庭和兴趣档案
- `GET /api/me/export`：用户自助导出服务端账号、档案、长期记忆、聊天记录和消息记录，不包含验证码和会话密钥
- `DELETE /api/me`：用户自助删除服务端账号及关联档案、长期记忆、聊天记录、消息和旧验证码
- `POST /api/insights`：上传聊天中提取的用药、健康、经济、异常等长期记忆
- `POST /api/chat`：服务端拼接长期记忆后代理阿里百炼文本模型，未配置时才走兜底
- `POST /api/care/brief`：主动关怀专用接口，生成喝水、吃药、晨间简报、养生小妙招、睡眠异常唤醒等可直接朗读的话术，可选入队给 APK 朗读
- `POST /api/vision`：服务端代理阿里百炼视觉模型，支持低清自动看一眼和高清仔细看
- `POST /api/audio`：服务端代理阿里百炼音频模型或声波摘要分析，用于睡眠声音辅助判断，不做诊断
- `GET /api/live/session`：小智式实时陪伴 WebSocket。默认支持文本轮次、文本流式回复和 PCM16 帧接收；显式开启 `ALIYUN_REALTIME_ENABLED=1` 后，会把 PCM16 桥接到阿里 Realtime，并把转写、模型文本增量和模型音频二进制帧回传到 APK 协议。
- `GET /api/avatar/status`：返回本机 2D Avatar 与 Linly-Talker-Stream sidecar 状态，并实时探测 Linly `/health`，用 `linly_digital_human.live` 区分“已配置”和“正在运行”。
- `POST /api/avatar/session/offer`：把 APK 的 WebRTC offer 转发给 Linly-Talker-Stream `/offer`，返回 answer 和 `sessionid`。
- `POST /api/avatar/session/{id}/say`：把服务端已生成的回复文本转发给 Linly `/human`，默认 `type=echo`，只驱动数字人说话，不让 Linly 再接管 LLM。
- `POST /api/avatar/session/{id}/stop`：转发到 Linly `/interrupt_talk`，用于用户插话时停止数字人发声。
- `GET /api/avatar/session/{id}/speaking`：转发到 Linly `/is_speaking`，用于 APK 查询 sidecar 是否仍在说话。
- `GET /api/messages/pending`：拉取小助手要朗读的消息
- `POST /api/messages`：创建一条待朗读消息
- `POST /api/messages/{id}/read`：标记消息已读
- `POST /api/admin/model/probe`：管理端主动探测阿里文本模型真实可用性，需要 `GOUXIONG_ADMIN_TOKEN`，不返回任何密钥
- `GET /api/admin/audit-logs`：管理端查看最近 100 条审计日志，包括详情查看、导出、删除、消息下发和模型探测
- `GET /api/admin/users`：管理端查看用户列表，需要 `GOUXIONG_ADMIN_TOKEN`
- `GET /api/admin/users/{id}`：管理端查看单个用户档案、洞察和消息
- `GET /api/admin/users/{id}/export`：管理端导出单个用户服务端资料，不包含验证码和会话密钥
- `DELETE /api/admin/users/{id}`：管理端删除单个用户及关联数据
- `POST /api/admin/users/{id}/messages`：管理端下发一条小助手待朗读消息

`server/start-server.ps1` 会自动读取 `server/.env`、项目根 `.env`、桌面的 `aliyun-sms.env` 和 `guanlin-aliyun-ai.env`。如果阿里云短信配置完整且未显式设置 `GOUXIONG_DEV_SMS=1`，验证码会走阿里云真实短信，接口不会返回 `dev_code`；如果 `guanlin-aliyun-ai.env` 内配置了 `DASHSCOPE_API_KEY`，聊天、看图和声波分析会自动走阿里百炼/DashScope。

`/health` 会返回 `security` 状态，显示后台页面保护、管理令牌、服务端签名密钥、验证码限流和审计日志是否启用。不会返回任何密码或 Key。

当前消息推送是轮询式 MVP。正式版可把 `push_messages` 接入厂商推送或 FCM，APK 仍保留拉取兜底。

`/api/live/session` 会发送小智式 Live WebSocket 事件。除 `hello/stt/tts/audio_received` 外，模型回复和 Realtime 桥接还会发送结构化 `emotion` 事件，字段包括 `emotion`、`intensity`、`gesture`、`safety_level`、`speech_text` 和 `source`；APK 用这些字段驱动 2D Avatar 的表情与手势，并在本地记录 `last_live_emotion_tag_*` 供验收。

## 本地后台

- 页面入口：`GET /admin`
- 配置 `GOUXIONG_ADMIN_BASIC_PASSWORD` 后，页面本身先走浏览器 Basic Auth；不配置时仅适合本机开发。
- 使用 `start-server.ps1` 默认启动时，如果服务监听 `0.0.0.0` 且未配置 Basic 密码，会自动生成 `server/data/admin-page-password.txt`；该目录已在 `.gitignore` 中，不会提交。
- 使用 `start-server.ps1` 启动且未配置 `GOUXIONG_SERVER_SECRET` / `GOUXIONG_ADMIN_TOKEN` 时，会自动生成 `server/data/.server-secret` 和 `server/data/admin-token.txt`；生产环境建议显式配置环境变量并妥善保管。
- 页面本身不保存到服务端，只在浏览器本地保存管理令牌。
- 所有后台数据请求都走 `/api/admin/*`，必须携带 `Authorization: Bearer <GOUXIONG_ADMIN_TOKEN>`。
- “模型探测”按钮会在管理员手动点击时向阿里百炼/DashScope 发送一条短文本探针，用于确认 Key、模型名和网络链路真实可用；未配置 Key 时只显示兜底状态。
- “审计日志”记录后台查看详情、导出、删除、消息下发、模型探测，以及用户自助导出/删除；审计记录不保存验证码、会话哈希或服务端密钥。
- “结构化记忆”来自聊天自动抽取，会把身体、用药、睡眠、家庭、兴趣、情绪、财务风险、异常事件和偏好沉淀为主人画像；后续模型回答会优先带入这些记忆。
- “导出用户数据”只导出用户资料、档案、长期记忆、聊天、消息记录和该用户相关审计事件，不导出验证码、会话哈希或服务端密钥。
- “删除用户”会删除该用户服务端账号和关联数据；APK 侧本地睡眠记录仍由手机本地删除入口处理。
- 后台只用于照护和测试，不应直接暴露到公网；生产部署应加 HTTPS、访问控制、备份保留和误删恢复流程。

## 运维脚本

- `npm.cmd run ops:check`：输出上线前检查报告，缺少关键密钥、后台页面保护、短信配置时返回失败。
- `npm.cmd run backup`：使用 SQLite `VACUUM INTO` 生成一致性备份，并输出 `.json` 校验清单。
- `powershell -ExecutionPolicy Bypass -File .\restore-db.ps1 -BackupPath <备份文件>`：恢复数据库，覆盖前会先生成当前库的安全副本。

生产部署说明见 [docs/server-production-deploy.md](../docs/server-production-deploy.md)。
