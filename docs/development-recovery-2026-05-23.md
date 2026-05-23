# 开发进度恢复记录

恢复日期：2026-05-23

## 当前结论

本仓库当前是“原生 Android APK + Node 服务端代理”的 MVP 后期联调状态。上一阶段交付基准在 `docs/apk-delivery-notes.md`，最后一次文档交付时间是 2026-05-18。

本次重装后检查结果：

- 服务端源码、SQLite 数据、后台令牌文件和本地备份仍在本机目录中。
- 服务端本地单测通过：`npm.cmd test`。
- 服务端可启动，本地入口为 `http://127.0.0.1:8787`，后台页面为 `http://127.0.0.1:8787/admin`。
- 当前服务端 `/health` 是开发降级状态：短信为 dev mode，模型为 fallback，阿里短信和 DashScope/百炼 Key 未恢复。
- Android SDK 已定位到 `C:\Users\Lenovo\AppData\Local\Android\Sdk`；使用临时环境变量后 `android-app/test.ps1` 可完成 APK 构建、签名校验和静态验收。
- Git 工作区有大量未提交改动和新增文件，这些改动代表 2026-05-18 之后的开发成果，不能随意回退。

## 当前未提交成果

主要新增或修改方向：

- 服务端 `server/`：手机号验证码、档案、长期记忆、模型代理、关怀消息、后台管理、备份恢复、Live WebSocket。
- Android 小助手：联网账号、主动关怀、服务端消息朗读、实时语音 Live、PCM 录制和播放、自动插话打断、睡前故事、本地助眠音、新闻源真实门禁和“您睡了么？”入睡确认。
- 2D Avatar：`AvatarView`、`AvatarState`、`AvatarCommand`，四角色 `avatar_2d_*` PNG 资源，自主眨眼/呼吸/眼神微动、嘴型驱动、TTS 播报结束状态回落。
- E2E 脚本：`e2e-server-care.ps1`、`e2e-live-voice.ps1`、`e2e-assistant-ui-mode.ps1`、`e2e-avatar-states.ps1`。
- 文档：服务端生产部署、2D Avatar 实现、资产说明、假功能审计、UI/UX 规格等。

## 已验证命令

服务端单测：

```powershell
cd D:\重要\www\www\狗熊睡眠\server
npm.cmd test
```

结果：通过。

服务端生产前检查：

```powershell
cd D:\重要\www\www\狗熊睡眠\server
npm.cmd run ops:check
```

当前结果：失败 1 项、警告 2 项。

- 失败：阿里短信变量未补齐。
- 警告：阿里模型 Key 未配置，会走兜底。
- 警告：服务监听 `0.0.0.0:8787`，生产需放在 HTTPS 反向代理和防火墙后。

本地启动：

```powershell
cd D:\重要\www\www\狗熊睡眠\server
powershell -ExecutionPolicy Bypass -File .\start-server.ps1
```

本次已启动并验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

Android 构建：

```powershell
cd D:\重要\www\www\狗熊睡眠\android-app
$env:ANDROID_HOME='C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

当前结果：通过。脚本会构建 `build/outputs/apk/gouxiong-sleep-debug.apk`、校验签名、检查权限、静态检查核心能力，并扫描本地 Key 是否误打进 APK。

## 需要恢复的本机环境

1. Android SDK 当前已定位。若新开 PowerShell，仍需设置当前会话环境变量：

```powershell
$env:ANDROID_HOME = "C:\Users\Lenovo\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

实际路径以本机 Android SDK 安装位置为准。SDK 至少需要：

- `platforms/android-35/android.jar` 或其他可用 platform。
- `build-tools/*/aapt2.exe`
- `build-tools/*/d8.bat`
- `build-tools/*/zipalign.exe`
- `build-tools/*/apksigner.bat`
- `platform-tools/adb.exe`

2. 恢复服务端真实配置。不要提交这些文件或 Key。

可选位置：

- `server/.env`
- 项目根 `.env`
- 桌面 `aliyun-sms.env`
- 桌面 `guanlin-aliyun-ai.env`

必填生产变量见 `docs/server-production-deploy.md`。当前 `server/.env` 只剩：

```text
ALIYUN_REALTIME_ENABLED=1
ALIYUN_REALTIME_MODEL=qwen3-omni-flash-realtime
```

## 当前服务数据

本地服务数据目录：

```text
server/data/
```

当前存在：

- `gouxiong.sqlite3`
- `admin-token.txt`
- `admin-page-password.txt`
- `.server-secret`

本地备份目录：

```text
server/backups/
```

当前备份：

- `gouxiong-20260517T111329Z.sqlite3`
- `gouxiong-20260517T111329Z.sqlite3.json`

这些目录已在 `.gitignore` 中，不会提交。

## 下一步接手顺序

1. 先不要清理或回退 Git 工作区。
2. 恢复 Android SDK 路径，跑通 `android-app/build.ps1` 和 `android-app/test.ps1`。
3. 补回服务端短信和模型 Key，跑 `server/npm.cmd run ops:check`、`smoke:model`、`smoke:realtime`。
4. 有模拟器或真机后，依次跑四个 E2E：
   - `e2e-assistant-ui-mode.ps1`
   - `e2e-avatar-states.ps1`
   - `e2e-server-care.ps1`
   - `e2e-live-voice.ps1`

`e2e-live-voice.ps1` 已包含实时语音、模型音频、自动插话、自然语音能力入口，以及“您睡了么？”三分支验收：没睡继续陪伴、睡了转守护、无回应转守护。
5. 验证无误后再整理一次 Git 提交，把 2026-05-18 之后的未提交开发成果分批提交。

## 后续产品限制

这些不是重装导致的问题，而是项目当前真实边界：

- 当前 APK 是 debug 包，`android:debuggable="true"`，正式发包前必须关闭。
- 当前 2D Avatar 是原生 `AvatarView` + 半身 PNG，不是完整 Live2D SDK。
- 可穿戴设备和 Health Connect 自动读取未接入，只支持手动摘要和手动设备读数。
- 真实麦克风中文识别、老人插话、扬声器回采、锁屏全屏唤醒、短信/电话权限，需要真机验收。
- 服务端当前是 SQLite 单机部署，多实例前不能多个进程同时写同一个数据库。
