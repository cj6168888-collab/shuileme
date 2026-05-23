# 3D 陪伴聊天形象开源方案调研

更新日期：2026-05-14

## 目标

睡了么的小助手不是普通客服聊天框，而是面向中老年人的“陪伴对象”。目标不是炫技，而是让老人感觉：

- 她在认真看着我、听我说；
- 她会等我、打断后也不生气；
- 她有呼吸、眨眼、轻微动作、表情变化；
- 她说话时嘴型和声音能对上；
- 她不是冰冷按钮，而像家里人一样在身边。

## 调研结论

优先路线：`VRM 3D 角色 + WebView/Three.js 实时渲染 + 原生 Android 语音/摄像头/安全能力`。

原因：

- 当前 APK 是原生 Java，直接接 Unity 体积和构建复杂度都高。
- Three.js + `@pixiv/three-vrm` 可以在 Android WebView 内加载 VRM，和现有原生页面通过 JSBridge 通信。
- VRM 模型资源更容易替换，四个助手可以各自绑定不同 VRM。
- 比 Live2D 更符合“3D 聊天形象”的要求；比纯视频数字人更实时、更可打断。

最终分级：

| 等级 | 路线 | 用途 | 当前判断 |
| --- | --- | --- | --- |
| A | `Three.js + @pixiv/three-vrm + Android WebView` | 睡了么第一阶段 3D 小助手主路线 | 先做 |
| A- | `TalkingHead + GLB + viseme/音频口型` | 半身真人感、嘴型同步增强路线 | 做技术预研，不先重构 |
| B | `Open-LLM-VTuber / AIRI / Amica` | 参考完整陪伴体验、状态机和实时语音 | 借鉴交互，不直接搬整套 |
| B | `Unity + ChatdollKit / UniVRM` | 后续重型 3D APK 或单独 Unity 模块 | 成熟但当前成本高 |
| C | 纯 Live2D | 灵动但不是严格 3D | 可参考，不做主形象 |

本项目第一阶段不追求“模型库堆砌”，只追求老人能一眼感觉小助手是活的：

- 眼睛会看人；
- 会呼吸和眨眼；
- 听用户说话时有点头反馈；
- 想事情时不僵住，先说安抚语；
- 说话时嘴部和身体有同步动作；
- 用户插话时立刻停下并看向用户。

## 候选项目

### 1. Amica

仓库：https://github.com/semperai/amica
许可：主体 MIT；3D 模型和图片按各自作者许可。

能力：

- 面向可自定义 3D 角色的自然语音聊天。
- 支持导入 VRM。
- 支持语音合成、语音识别、视觉、情绪表达。
- 技术栈使用 Three.js 与 `@pixiv/three-vrm`。

适合睡了么的部分：

- VRM 加载与情绪驱动思路。
- 语音聊天 + 视觉 + 角色表情的一体化产品形态。
- 可参考其“角色可配置、声音可配置、表情可配置”的设计。

不适合直接照搬的部分：

- 它是 Web/Desktop 产品，不是原生 Android APK。
- 默认偏年轻 AI 伴侣，不适合直接给中老年使用。
- 需要重做 UI 语言和角色人格。

### 2. ChatVRM

仓库：https://github.com/pixiv/ChatVRM
许可：MIT；仓库已归档。

能力：

- 浏览器内与 3D VRM 角色对话。
- 可导入 VRM 文件。
- 可根据角色调整声音。
- 支持带情绪表达的回复。

适合睡了么的部分：

- 最小可行 VRM 聊天原型参考。
- 代码较简单，适合拆出核心：加载 VRM、驱动表情、播放 TTS。

风险：

- 2025-05-27 已归档，只适合作为参考，不适合作为长期依赖。

### 3. AIRI

仓库：https://github.com/moeru-ai/airi
许可：MIT。

能力：

- 自托管 AI companion。
- 支持实时语音聊天。
- 支持 VRM 与 Live2D。
- VRM 动画包含自动眨眼、自动看向、待机眼动。
- 支持浏览器音频输入、客户端语音识别和说话检测。

适合睡了么的部分：

- “活着”的关键细节：自动眨眼、看向用户、待机眼动。
- 角色状态机：听、想、说、看、安抚。
- 可借鉴其模块拆分：耳朵、嘴巴、身体、记忆。

风险：

- 项目较大，直接移植成本高。
- 产品语气偏 VTuber / AI companion，需要改造成亲人陪伴。

### 4. Open-LLM-VTuber

仓库：https://github.com/Open-LLM-VTuber/Open-LLM-VTuber
许可：项目代码许可独立；Live2D 样例模型单独许可。

能力：

- 免手持语音互动。
- 支持语音打断。
- 支持摄像头、屏幕录制、截图，让 AI companion 能“看见”。
- 支持点击/拖拽触摸反馈。
- 支持 Live2D 表情映射。

适合睡了么的部分：

- 语音打断体验。
- 视觉感知与陪伴融合。
- 点击/拖拽触摸反馈思路。

不作为主路线的原因：

- 核心更偏 Live2D，不是严格 3D。
- Live2D 示例模型许可复杂。
- 可作为交互体验参考，不作为 3D 主引擎。

### 5. TalkingHead

仓库：https://github.com/met4citizen/TalkingHead
许可：MIT。

能力：

- 浏览器 JavaScript 3D talking head。
- 支持实时口型同步。
- 支持 GLB 全身 3D 模型、Mixamo 动画。
- 支持表情、emoji 到表情转换。
- 支持流式音频与实时打断接口。

适合睡了么的部分：

- “像真人说话”的口型同步最强。
- 适合做半身真人陪伴形象。
- 如果选择写实/半写实青年、姐姐形象，它是重点候选。

风险：

- 默认不是 VRM，而偏 GLB + ARKit/Oculus blend shape。
- 中文口型需要走外部 TTS viseme 或音频驱动口型。

### 6. ChatdollKit

仓库：https://github.com/uezo/ChatdollKit
许可：Apache-2.0。

能力：

- Unity 3D 虚拟助手 SDK。
- 支持 VRM。
- 支持语音、动作、表情、眨眼、口型同步。
- 支持多种 LLM。

适合睡了么的部分：

- 如果后续改 Unity Android，ChatdollKit 是最成熟的 3D 虚拟助手 SDK。
- 表情、动作、口型、语音整合度好。

当前不选它做第一阶段的原因：

- APK 当前是原生 Java 直编。
- Unity 导入会明显增加体积、构建链路和调试复杂度。
- 第一阶段应先用 WebView/Three.js 验证陪伴体验。

### 7. 3D 模型资源

#### Open Source Avatars

仓库：https://github.com/ToxSam/open-source-avatars
许可：登记库本身 CC0；单个 avatar 按 collection 标注。

价值：

- 提供 VRM 头像资源索引。
- 有直接 VRM 下载链接、预览图、许可信息。
- 包含 CC0 / CC-BY 等集合。

问题：

- 很多头像偏游戏、NFT、二次元或潮流，不一定适合老人。
- 需要挑“亲切、干净、不过度性感、不吓人”的模型。

#### VRoid Sample / Preset

说明：https://vroid.pixiv.help/hc/en-us/articles/4402394424089-VRoidPreset-A-Z

价值：

- 可作为 VRoid Studio 制作四个角色的基础。
- 官方说明允许在营利或非营利活动中使用。
- 可以编辑纹理和参数，做成原创角色。

限制：

- 不是 CC0。
- 禁止把原样样例模型或 VRM 文件作为 CC0 分发。
- 不能收费再分发原始样例模型。

## 当前页面问题归类

小助手现在已经比普通聊天页更接近 Live，但仍有三个核心问题必须继续改：

### 1. 页面过长

问题：

- “小助手设置”“主人档案”“看看/记东西”等页面仍然有大量卡片和说明。
- 中老年用户不会逐字看完，反而会觉得复杂。
- 设置入口太多，容易让人以为必须配置完才能使用。

改法：

- 聊天页只保留大头像、状态气泡、暂停、看一眼、首页。
- 角色选择收成横向四个头像卡，不再一屏四段文字说明。
- 主人档案变成对话式采集，不做表单式页面；必要时只展示“我记住了什么”。
- 大段说明移动到“隐私与安全说明”，默认不打扰主流程。

### 2. 层次不够强

问题：

- 现有页面有“卡片叠卡片”的倾向，视觉上像后台管理页。
- 睡了么参考图的重点是：大场景图 + 大按钮 + 少量状态提示，不是信息瀑布。

改法：

- 每个一级页面只允许一个主视觉：睡眠熊、早安熊、起床闹钟、3D 小助手。
- 每页只保留一个主动作：开始守护 / 我醒了 / 我没事 / 和我聊聊。
- 次动作改为底部 2-3 个图标按钮。
- 所有解释性文字最多两行。

### 3. 小助手仍像“功能集合”

问题：

- 老人进入小助手后看到“建档、设置、记状态、联网”等功能词，会产生学习成本。
- 用户真正要的是“她在陪我”，不是“我来配置一个系统”。

改法：

- 第一屏就是半身 3D 小助手面对用户。
- 首次认识完全在对话里完成。
- 设置页只作为兜底，不能成为主流程。
- 小助手主动把复杂任务翻译成自然语言，例如：“我想更了解您一点，今天先问一个问题就好。”

## 3D 小助手页面硬规范

这是下一版实现必须遵守的 UI/UX 约束。

### 页面结构

```text
顶部 56dp：小助手名字 + 小状态点
中部 55%-65%：半身 3D 角色舞台
气泡 1 行到 2 行：正在听 / 正在想 / 正在说的短句
底部 96dp：暂停、看一眼、回首页
```

禁止：

- 禁止消息列表作为主体。
- 禁止“发送”按钮作为主入口。
- 禁止页面无限下拉。
- 禁止把设置、建档、联网 Key 放在第一屏。
- 禁止红色按钮用于普通打断；红色只给起床强唤醒和紧急确认。

### 动画状态

| 状态 | 视觉 | 触发 |
| --- | --- | --- |
| `idle` | 慢呼吸、自然眨眼、轻微左右眼动 | 页面打开后空闲 |
| `listening` | 看向用户、轻点头、波形微动 | 麦克风正在听 |
| `thinking` | 低头 0.5 秒后抬头、手部小动作、先安抚 | 模型检索或找物品 |
| `speaking` | 嘴部开合、头部轻动、眼神稳定 | TTS / 实时音频输出 |
| `interrupted` | 立刻闭嘴、停动画、回到倾听 | 用户插话或点暂停 |
| `seeing` | 眼神扫视、提示“我看一眼” | 摄像头采样 |
| `comforting` | 微笑、慢点头 | 用户焦虑、找不到东西、身体不舒服 |

### 四个角色差异

| 角色 | 动作 | 颜色 | 声音方向 |
| --- | --- | --- | --- |
| 贴心小妹 | 眨眼更灵动、挥手更明显 | 粉红、奶白、暖橙 | 女声偏轻快，像孙女/女儿 |
| 懂事小弟 | 点头清楚、手势干净 | 草绿、浅蓝、奶白 | 男童声或少年声，清楚乖巧 |
| 阳光小哥 | 姿态挺拔、动作稳 | 蓝、绿、暖白 | 青年男声，温和但有力量 |
| 温柔姐姐 | 动作最慢、眼神最柔和 | 紫、浅绿、奶白 | 青年女声，慢一点、安抚感强 |

不要做成恋爱陪伴，不做医疗人员，不穿白大褂，不出现听诊器、红十字等诊断暗示。

## 工程落地方案

### 已落地：原生 Live 舞台壳

当前 APK 已先接入轻量 Live 舞台：

- 聊天页的头像区默认使用原生 `ImageView` 加载角色 PNG，避免 Live 首屏启动 WebView 造成模拟器/低端机 ANR；
- Java 侧通过 `listening / thinking / speaking / seeing / comforting` 状态驱动原生缩放、摆动和状态点动画；
- 角色资源来自 APK drawable，不依赖网络、不需要额外 assets 打包链；
- 点击头像或舞台会立即触发 `abort`，用于验证用户插话打断；
- WebView/VRM 不再作为当前默认路径，只作为后续数字人原型路线。

这不是最终 VRM，但已经把“小助手舞台”和“语音状态机”分离出来，后续可以在独立原型中验证 WebView/Three.js/VRM，稳定后再替换原生图片舞台。

### 下一阶段：WebView / VRM 3D 壳原型

新增目录：

```text
android-app/src/main/assets/companion_avatar/
  index.html
  avatar.js
  vendor/
    three.module.js
    GLTFLoader.js
    three-vrm.module.js
  models/
    sister.vrm
    brother.vrm
    young_man.vrm
    gentle_woman.vrm
```

新增 Android 桥：

```text
CompanionAvatarBridge
  setRole(role)
  setState(state)
  setEmotion(emotion)
  setMouthLevel(level)
  setSpeechText(text)
  interrupt()
```

`MainActivity` 聊天页改造：

- `addLiveCompanionStage()` 当前保持原生 PNG 舞台作为稳定默认；
- WebView/VRM 先做独立原型页和性能验收，不能直接替换 Live 首屏；
- 如果 WebView/WebGL 失败，必须自动退回当前原生 PNG 头像；
- `SpeechRecognizer` 状态变化调用 `setState("listening")`；
- 等待 DeepSeek / 实时模型时调用 `setState("thinking")`；
- TTS 开始时调用 `setState("speaking")`；
- 用户插话时调用 `interrupt()` 并回到 `listening`；
- 摄像头采样时调用 `setState("seeing")`。

### 第二阶段：模型资产

短期可用：

- 从 open-source-avatars 选择干净、亲切、不过度游戏化的 VRM；
- 或用 VRoid Studio 生成四个基础角色，再导出 VRM；
- 所有来源记录到 `NOTICE`，不阻塞内部测试。

长期目标：

- 单独生成四个专属角色；
- 每个角色一套半身近景动作、表情、衣服色彩；
- 统一睡了么视觉：奶白底、明快色、大圆角、大按钮。

### 第三阶段：嘴型

先做可验收的低成本版本：

- TTS 开始：进入 `speaking`；
- 每 80-120ms 用音量或伪 RMS 更新 `mouthOpen`；
- 标点暂停时嘴合上；
- TTS 结束：嘴合上，回到 `listening`。

后续升级：

- 如果 TTS 可返回 viseme，用 viseme 驱动；
- 如果走 TalkingHead，用 `streamAudio` / `streamInterrupt` 承接实时音频。

## 采购/开源借鉴建议

| 需求 | 借鉴项目 | 采用方式 |
| --- | --- | --- |
| 快速 VRM 3D 渲染 | `@pixiv/three-vrm` | 直接作为 WebView 3D 加载核心 |
| 完整 VRM 对话样例 | ChatVRM / Amica | 拆加载、表情、语音状态，不搬 UI |
| 视觉、打断、主动说话 | Open-LLM-VTuber | 借状态机和交互理念 |
| “活着”的数字生命结构 | AIRI | 借模块拆分：耳朵、嘴巴、记忆、身体 |
| 半身真人口型 | TalkingHead | 后续高级路线，重点验证中文口型 |
| Unity 级稳定 3D 助手 | ChatdollKit | 第二代大版本再考虑 |

## 验收标准

第一阶段必须通过：

- 打开小助手 1 秒内看到半身角色或兜底大头像。
- 角色空闲时有呼吸和眨眼，不能静止像图片。
- 用户说话时状态变成“我在听”，并有点头或波形。
- 等待超过 800ms 时，先本地说“您别急，我想想”。
- 小助手说话时，用户插话或点头像，必须 600ms 内停止朗读。
- 页面一屏内能完成核心聊天，不需要向下滚很久。
- 普通模式没有刺眼红色大按钮。
- WebGL 加载失败时不崩溃，自动回到当前 PNG 头像。
- APK 里不能硬编码测试 API Key。
- 摄像头只在聊天页或用户明确“看一眼”时工作，不在夜间守护后台偷拍。

## 推荐落地架构

### 阶段一：WebView 3D 角色壳

在原生 Android 内新增 `CompanionAvatarActivity` 或替换现有聊天页上半屏：

- `WebView` 加载本地 HTML。
- HTML 内使用 Three.js + `@pixiv/three-vrm`。
- Android 通过 `addJavascriptInterface` 发事件：
  - `listening`
  - `thinking`
  - `speaking`
  - `seeing`
  - `comforting`
  - `sleep_guard`
  - `wake_alarm`
- JS 根据状态驱动：
  - 呼吸 idle
  - 自动眨眼
  - 眼神轻微跟随
  - 听的时候点头/看向用户
  - 想的时候手托腮/轻微低头
  - 说话时嘴型/波形驱动嘴部
  - 安抚时微笑、慢点头

### 阶段二：四个角色模型

四个助手各自一套 VRM：

- 贴心小妹：粉色/浅红，动作更活泼，眨眼频率稍高，微笑多。
- 懂事小弟：绿色/浅蓝，动作干净，点头明显，适合记事提醒。
- 阳光小哥：蓝色/绿色，姿态稳，安防/强提醒时更坚定。
- 温柔姐姐：紫色/浅绿，动作最慢，目光柔和，适合长期陪伴。

不要做成“恋爱感”或“虚拟主播感”，要像可靠亲人。

### 阶段三：口型同步

先用低成本方式：

- TTS 播放时按音量 RMS 驱动嘴部开合。
- 句子中遇到逗号、句号时嘴部闭合、点头。

后续升级：

- 如果使用支持 viseme 的 TTS，改成 viseme 驱动。
- TalkingHead 的流式接口可作为高级路线。

### 阶段四：情绪价值交互

陪伴助手必须有状态，而不是只会回答：

- 用户沉默：轻声问“我在，您慢慢说。”
- 用户焦虑找东西：先说“别急，我陪您想想。”
- 用户反复说同一件事：不烦躁，温柔复述。
- 用户说身体不舒服：先安抚，再提醒联系家人/医生。
- 用户早晨醒来：问好、喝水、吃药、天气、睡眠简报。

## 当前 APK 应立即改的方向

1. 新增 `assets/avatar/` 目录，放四个 VRM。
2. 新增 `android-app/src/main/assets/companion_avatar/`：
   - `index.html`
   - `avatar.js`
   - `three.module.js`
   - `three-vrm.module.js`
3. 增加 `CompanionAvatarBridge`：
   - `setRole(role)`
   - `setState(state)`
   - `setMouthLevel(level)`
   - `setEmotion(emotion)`
   - `sayStart(text)`
   - `sayEnd()`
4. 聊天页第一屏：
   - 上半屏：半身 3D 角色。
   - 下半屏：实时状态、波形、暂停/看一眼。
   - 不再展示大段文字。

## 结论

如果只追求最快落地：

- 用 `@pixiv/three-vrm + Open Source Avatars/VRoid`，Android WebView 承载。

如果追求最强“真人说话感”：

- 研究 `TalkingHead`，走 GLB + blendshape + viseme/音频驱动。

如果后续允许重型引擎：

- 上 Unity + ChatdollKit。

当前建议：先做 VRM WebView 原型，把“活着的陪伴感”做出来，再换更精致的四个角色模型。
