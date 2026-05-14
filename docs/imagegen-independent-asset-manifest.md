# 狗熊睡眠独立绘图模型资产清单

用途：后续所有正式 UI 素材按本清单逐张生成、逐张接入。参考图只用于风格约束，不得再从总截图裁图。

## 1. 通用绘图风格

所有提示词都继承以下约束：

```text
Use case: stylized-concept
Style/medium: warm high-quality 3D mobile app illustration, soft rounded shapes, friendly elderly-care feeling, polished toy-like clay/Pixar-like material without copying any copyrighted character, clean warm white background when not a scene, bright gentle lighting, clear subject edges, no watermark, no medical diagnosis symbol, no white coat, no hospital.
Color palette: warm cream, bright blue, fresh green, morning orange, soft red only for emergency.
Audience: elderly-friendly Chinese Android sleep guardian app.
Constraints: no embedded UI text unless explicitly requested; keep generous padding; avoid fear, horror, hospital, disease diagnosis, dark oppressive mood, stock-photo look.
```

## 2. 命名规则

新资产使用 `gxs_` 前缀，避免和旧 `ui_*` 截图切片混淆。

- 场景背景：`gxs_scene_<screen>_<state>.png`
- 角色：`gxs_role_<role>_<pose>.png`
- 吉祥物：`gxs_bear_<scene>_<pose>.png`
- 按钮质感：`gxs_button_<purpose>.png`
- 图标：`gxs_icon_<object>.png`
- 动效帧：`gxs_anim_<role>_<action>_<frame>.png`

接入 Android 后放入：

- 整屏/大图：`android-app/src/main/res/drawable-nodpi/`
- 小图标：后续可放 `drawable-xxhdpi`，首版可先 `drawable-nodpi`
- 源图和提示词：`docs/assets/imagegen/`

## 3. 第一批关键资产

### 3.1 App 图标

文件：`gxs_logo_bear_moon_app_icon.png`  
尺寸：1024x1024  
用途：启动图标、品牌页、空状态。

```text
Asset type: Android app icon
Primary request: a cute round dog-bear sleeping while hugging a crescent moon, tiny stars around it, icon-safe centered composition.
Scene/backdrop: warm cream circular badge background, no UI text.
Subject: soft brown dog-bear mascot with closed eyes, gentle smile, hugging a pale yellow crescent moon.
Composition/framing: centered, large readable silhouette, works at small size, safe padding for adaptive icon crop.
Lighting/mood: soft bedtime glow, comforting and trustworthy.
Constraints: no text, no watermark, no hard shadows, no scary night mood.
```

### 3.2 首页夜间卧室背景

文件：`gxs_scene_home_night_bedroom.png`  
尺寸：1440x1920  
用途：首页上半屏背景。

```text
Asset type: mobile app background
Primary request: warm bedtime bedroom scene for a sleep guardian app.
Scene/backdrop: cozy bedroom at night, blue curtains, moon and small stars outside the window, bedside lamp, clean bed, warm cream and navy balance.
Subject: empty composition area in lower third for app cards and button overlays.
Composition/framing: vertical phone background, main visual in upper half, no embedded text, no UI elements.
Lighting/mood: peaceful night, soft lamp glow, reassuring not dark.
Constraints: leave clear safe space for large native button at bottom, no people, no text.
```

### 3.3 首页睡觉狗熊主体

文件：`gxs_bear_home_sleeping.png`  
尺寸：1024x1024  
用途：首页主吉祥物，可叠在背景上。

```text
Asset type: mascot cutout source on flat chroma-key background
Primary request: cute dog-bear sleeping under a blue star blanket, peaceful face.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: round brown dog-bear, closed eyes, small dog-like nose, tucked under soft blanket with tiny stars.
Composition/framing: centered full mascot, generous padding, no cast shadow.
Lighting/mood: soft cozy bedtime, warm and safe.
Constraints: no text, do not use #00ff00 in subject, crisp edges.
```

### 3.4 开始守护按钮质感

文件：`gxs_button_start_guard_blue.png`  
尺寸：960x256  
用途：首页主按钮背景。

```text
Asset type: button background texture
Primary request: glossy friendly blue rounded pill button background for an elderly-friendly mobile app.
Scene/backdrop: transparent-looking button on flat #00ff00 chroma-key background.
Subject: blue rounded rectangle button with subtle highlight, gentle drop shadow, left circular icon well but no text.
Composition/framing: horizontal pill button, large padding, no embedded words.
Lighting/mood: clean, tactile, trustworthy.
Constraints: no text, no watermark, do not use #00ff00 inside the button.
```

### 3.5 早安护理背景

文件：`gxs_scene_morning_care_window.png`  
尺寸：1440x1920  
用途：早安页顶部背景。

```text
Asset type: mobile app background
Primary request: bright morning care scene for elderly sleep app.
Scene/backdrop: warm sunlit window, soft clouds, potted plant, cup on table, cream background.
Subject: clear upper area for morning mascot, lower area clean for native cards.
Composition/framing: vertical phone background, cheerful but calm, no embedded UI text.
Lighting/mood: morning sunlight, hopeful, gentle.
Constraints: no text, no medical objects, no busy clutter.
```

### 3.6 早安挥手狗熊

文件：`gxs_bear_morning_wave.png`  
尺寸：1024x1024  
用途：早安页吉祥物。

```text
Asset type: mascot cutout source on flat chroma-key background
Primary request: cute dog-bear waving good morning, friendly smile.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: round brown dog-bear sitting at a table, one paw waving, bright eyes, warm expression.
Composition/framing: centered upper-body mascot, generous padding, no cast shadow.
Lighting/mood: soft morning sunlight, cheerful but not childish.
Constraints: no text, do not use #00ff00 in subject.
```

### 3.7 强唤醒闹钟主体

文件：`gxs_alarm_clock_red_3d.png`  
尺寸：1024x1024  
用途：强唤醒页主视觉。

```text
Asset type: emergency wake illustration cutout source on flat chroma-key background
Primary request: friendly red 3D alarm clock, clear and attention-grabbing but not frightening.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: glossy red classic alarm clock, large white clock face, simple hands, soft rounded edges.
Composition/framing: centered, large, generous padding, no text.
Lighting/mood: bright morning alert, urgent but kind.
Constraints: no horror mood, no flames, no medical symbols, do not use #00ff00 in subject.
```

### 3.8 强唤醒暖光背景

文件：`gxs_scene_alarm_warm_sunburst.png`  
尺寸：1440x1920  
用途：强唤醒全屏背景。

```text
Asset type: mobile alarm background
Primary request: warm sunrise alert background for a wake confirmation screen.
Scene/backdrop: cream and orange sunrise gradient, soft clouds, subtle radial light rays, clean lower safe area for a big native button.
Subject: no characters, no text, no UI controls.
Composition/framing: vertical phone background, top visual light rays, center clear for alarm clock overlay, bottom clear for button.
Lighting/mood: bright, clear, awake, non-threatening.
Constraints: no embedded text, no harsh red full-screen background.
```

## 4. 四个小助手大形象

共同规格：

- 聊天大形象：`1536x2048`
- 头像：`1024x1024`
- 姿态帧：`1024x1536`
- 背景：用于抠图时用 #00ff00；如直接用于卡片，可用暖白浅色背景。

### 4.1 贴心小妹

文件：`gxs_role_sister_chat_idle.png`

```text
Asset type: 3D companion character for live voice chat
Primary request: warm cute young Chinese girl companion for an elderly sleep care app, polite and lively like a caring granddaughter.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: half-body girl, soft coral pink cardigan, white collar, bow or simple hair accessory, friendly big eyes, gentle waving pose.
Composition/framing: facing viewer directly, upper body visible, hands visible, generous padding.
Lighting/mood: warm, kind, cheerful, trustworthy.
Constraints: non-medical, no white coat, no hospital, no sexualized styling, no text, do not use #00ff00 in subject.
```

### 4.2 懂事小弟

文件：`gxs_role_brother_chat_idle.png`

```text
Asset type: 3D companion character for live voice chat
Primary request: smart caring young Chinese boy companion, polite and responsible like a helpful grandson.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: half-body boy, soft green hoodie or sky-blue casual jacket, warm smile, attentive eyes, one hand slightly raised as if saying I am listening.
Composition/framing: facing viewer, upper body visible, clean silhouette.
Lighting/mood: fresh, clear, sincere.
Constraints: non-medical, no school logo, no hospital, no text, do not use #00ff00 in subject.
```

### 4.3 阳光小哥

文件：`gxs_role_young_man_chat_idle.png`

```text
Asset type: 3D companion character for live voice chat
Primary request: reliable sunny young Chinese man companion for elderly sleep guardian app, gentle and steady.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: half-body young man, light blue casual shirt or jacket, warm confident smile, open posture, soft hair, friendly eyes.
Composition/framing: facing viewer, upper body and one hand visible, large clean silhouette.
Lighting/mood: sunny, safe, patient.
Constraints: no doctor outfit, no medical symbol, no text, do not use #00ff00 in subject.
```

### 4.4 温柔姐姐

文件：`gxs_role_gentle_woman_chat_idle.png`

```text
Asset type: 3D companion character for live voice chat
Primary request: gentle caring young Chinese woman companion, patient and warm like a family caregiver, default assistant for elderly users.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: half-body young woman, soft green and cream casual clothes, gentle smile, calm eyes, hands lightly together or small wave.
Composition/framing: facing viewer, upper body visible, clean rounded silhouette.
Lighting/mood: peaceful, trustworthy, affectionate.
Constraints: no white coat, no nurse outfit, no hospital, no text, do not use #00ff00 in subject.
```

## 5. 小助手动效姿态

每个角色都生成以下姿态，保持同一身份、服装和脸型。

| 动作 | 文件后缀 | 用途 |
| --- | --- | --- |
| 倾听 | `_listen.png` | 用户说话时 |
| 说话 | `_speak.png` | TTS 播放时 |
| 思考 | `_think.png` | 检索、找物品时 |
| 安抚 | `_reassure.png` | 用户着急时 |
| 点头 | `_nod.png` | 确认和鼓励 |
| 挥手 | `_wave.png` | 早安、首次见面 |

通用追加提示：

```text
Keep the same character identity, clothing, face, lighting, and 3D style as the idle version. Change only the pose and expression for <action>. No text, no extra objects, no medical symbols.
```

## 6. 功能图标

尺寸：512x512  
背景：#00ff00 抠图源或暖白圆角底  
用途：卡片、按钮、底部导航。

| 文件 | 主体 | 颜色 |
| --- | --- | --- |
| `gxs_icon_shield_check.png` | 盾牌 + 对勾 | 绿 |
| `gxs_icon_play_circle.png` | 播放圆钮 | 蓝 |
| `gxs_icon_sun_morning.png` | 太阳 | 橙 |
| `gxs_icon_heart_safe.png` | 爱心 | 红/粉 |
| `gxs_icon_water_glass.png` | 水杯 | 蓝 |
| `gxs_icon_pill_capsule.png` | 胶囊/药盒 | 橙 |
| `gxs_icon_bluetooth_speaker.png` | 蓝牙音箱 | 蓝 |
| `gxs_icon_phone_sms.png` | 电话短信 | 红 |
| `gxs_icon_microphone.png` | 麦克风 | 蓝 |
| `gxs_icon_camera_eye.png` | 摄像头 | 绿 |
| `gxs_icon_keys.png` | 钥匙 | 黄 |
| `gxs_icon_glasses.png` | 眼镜 | 深蓝 |
| `gxs_icon_medicine_box.png` | 药盒 | 橙 |
| `gxs_icon_mobile_phone.png` | 手机 | 蓝 |

通用图标提示：

```text
Asset type: mobile app 3D icon
Primary request: <object> icon for elderly-friendly sleep guardian app.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: single rounded 3D icon, toy-like material, clear silhouette, no text.
Composition/framing: centered, generous padding, readable at 48dp.
Lighting/mood: soft studio lighting, warm and friendly.
Constraints: no text, no watermark, no complex background, do not use #00ff00 in subject.
```

## 7. 卡片和按钮素材

按钮背景只生成无文字底图，文字由原生 UI 渲染。

| 文件 | 用途 | 尺寸 | 色彩 |
| --- | --- | --- | --- |
| `gxs_button_start_guard_blue.png` | 开始守护 | 960x256 | 蓝 |
| `gxs_button_safe_green.png` | 我没事 | 960x256 | 绿 |
| `gxs_button_morning_orange.png` | 早安护理 | 960x256 | 橙 |
| `gxs_button_alarm_red.png` | 强唤醒确认 | 1080x280 | 红 |
| `gxs_card_safe_green.png` | 状态卡底 | 960x360 | 浅绿 |
| `gxs_card_care_orange.png` | 护理卡底 | 960x360 | 浅橙 |
| `gxs_card_voice_blue.png` | 语音气泡底 | 960x360 | 浅蓝 |

按钮通用提示：

```text
Asset type: button background texture
Primary request: friendly glossy rounded pill button background, no text.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: <color> rounded pill, soft highlight, subtle shadow, clean center area for native Android text.
Composition/framing: horizontal, generous padding, no icon unless requested.
Constraints: no text, no watermark, do not use #00ff00 inside the button.
```

## 8. 页面级验收图

每完成一批资产后，要合成并截图以下页面：

- `home_guard_ready.png`：首页未开始。
- `home_guard_running.png`：守护中。
- `morning_care.png`：早安护理。
- `alarm_wake.png`：强唤醒。
- `assistant_chat_first_meeting.png`：第一次认识。
- `assistant_chat_listening.png`：打开即听。
- `assistant_chat_thinking.png`：后台检索安抚。
- `settings_cards.png`：我的页大卡片设置。

验收失败条件：

- 看出截图裁切边缘。
- 角色风格不一致。
- 文字被图片压住或图片里带错误文字。
- 红色出现在普通聊天主按钮。
- 图标在 48dp 下看不清。
- 大字体模式下主按钮文字溢出。
