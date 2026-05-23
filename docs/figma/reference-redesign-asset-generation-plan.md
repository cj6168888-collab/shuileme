# 睡了么参考图重做版素材生成任务单

用途：把 `gouxiong-sleep-reference-redesign.svg` 里的矢量占位图替换成绘图模型生成的独立 3D 位图素材。所有图片都不能带最终 UI 文案、手机状态栏、按钮文字或水印；文字和点击区由 Android 原生 View / Figma 文本层承担。

## P0 必做素材

### `gxs_logo_bear_moon_app_icon.png`

1024x1024。用于启动图标、品牌区和首页顶部。

```text
Use case: logo-brand
Asset type: Android app icon and Figma brand mark
Primary request: a cute round dog-bear sleeping while hugging a crescent moon, tiny stars around it, centered inside a warm cream rounded badge.
Style/medium: warm high-quality 3D toy-like mobile app illustration, soft rounded shapes, friendly elderly-care feeling, polished clay/Pixar-like material without copying any copyrighted character.
Composition/framing: centered, large readable silhouette, icon-safe padding, no text.
Color palette: warm cream, soft brown bear, pale yellow moon, deep navy night circle, small golden stars.
Constraints: no Chinese text, no UI, no watermark, no hospital or medical symbols, no scary dark mood.
```

### `gxs_scene_home_night_guard.png`

1440x1920。用于守护首页手机顶部场景。

```text
Use case: stylized-concept
Asset type: mobile app hero scene
Primary request: cozy night bedroom scene with a cute dog-bear sleeping under a blue star blanket, moon and stars outside the window, bedside lamp, calm and reassuring.
Style/medium: warm 3D mobile app illustration, soft rounded shapes, high quality, elderly-friendly.
Composition/framing: vertical phone background, main bear and bed in upper-middle, lower third clean enough for native status card and button overlays.
Color palette: deep navy night, warm lamp yellow, soft blue blanket, cream highlights.
Constraints: no embedded UI text, no phone status bar, no buttons, no watermark, no fear or hospital mood.
```

### `gxs_scene_morning_bear_wave.png`

1440x1920。用于早安护理页顶部和 Figma 早安手机。

```text
Use case: stylized-concept
Asset type: morning care mobile scene
Primary request: cheerful dog-bear waving good morning beside a sunlit window, cup of water on table, potted plant, warm morning light.
Style/medium: polished warm 3D mobile app illustration, cute but not childish, elderly-friendly.
Composition/framing: vertical phone background, bear in upper half, clean lower area for native water and medication cards.
Color palette: warm cream, morning orange, soft green plant, gentle sunlight.
Constraints: no embedded UI text, no phone status bar, no medical diagnosis symbols, no watermark.
```

### `gxs_alarm_clock_red_3d.png`

1024x1024。用于强唤醒页主视觉。

```text
Use case: stylized-concept
Asset type: emergency wake confirmation illustration
Primary request: friendly glossy red 3D alarm clock, clear and attention-grabbing but kind, large white face, rounded edges.
Style/medium: polished 3D mobile app object, soft toy-like material, no fear.
Composition/framing: centered object with generous padding, suitable for overlay on sunrise background.
Color palette: soft red, warm white, gentle orange highlight.
Constraints: no text, no phone status bar, no medical symbols, no flames, no horror mood, no watermark.
```

## P1 组件图标

| 文件名 | 用途 | 要求 |
| --- | --- | --- |
| `gxs_icon_shield_check.png` | 安心守护 / 守护已就绪 | 绿色盾牌对勾，3D 小图标 |
| `gxs_icon_sun_morning.png` | 晨间关怀 / 早安导航 | 橙色太阳，亲切不刺眼 |
| `gxs_icon_heart_family.png` | 家人陪伴 | 柔和红心，不用于普通危险提示 |
| `gxs_icon_bluetooth_speaker.png` | 音量/蓝牙 | 蓝牙音箱，清楚表达外放 |
| `gxs_icon_water_glass.png` | 喝水卡 | 透明水杯，浅蓝高光 |
| `gxs_icon_pill_capsule.png` | 吃药卡 | 胶囊/药盒，避免医疗恐惧 |

通用提示词补充：

```text
Small 3D mobile app icon, centered on flat warm white or transparent-ready background, no text, no watermark, rounded soft style, visually consistent with dog-bear sleep guardian app.
```

## P1 四个小助手

| 文件名 | 角色 | 设计要求 |
| --- | --- | --- |
| `gxs_role_sister_chat_idle.png` | 贴心小妹 | 可爱、活泼、礼貌，像贴心晚辈 |
| `gxs_role_brother_chat_idle.png` | 懂事小弟 | 聪明、孝顺、温和提醒 |
| `gxs_role_young_man_chat_idle.png` | 阳光小哥 | 阳光可靠、温柔、适合安全提醒 |
| `gxs_role_gentle_woman_chat_idle.png` | 温柔姐姐 | 温柔耐心、贴心倾听 |

统一提示词：

```text
Use case: stylized-concept
Asset type: live companion character for elderly-friendly mobile app
Primary request: half-body 3D Chinese companion character facing the user, warm smile, polite and caring expression.
Style/medium: high-quality 3D toy-like mobile app character, soft lighting, friendly, original, not copied from any IP or celebrity.
Composition/framing: upper-body portrait, centered, enough padding for rounded role card crop.
Color palette: warm cream background, role-specific soft accent color.
Constraints: no text, no watermark, no white coat, no hospital or medical diagnosis symbol, no uncanny realism.
```

## 接入规则

- 生成源图保存到 `docs/assets/imagegen/`。
- APK 正式资源保存到 `android-app/src/main/res/drawable-nodpi/`。
- 文件名必须使用 `gxs_*` 前缀。
- 接入后删除或降级旧 `ui_*` 过渡图在主流程中的使用。
- 每个素材接入后必须重新生成 APK，并保存模拟器截图。
