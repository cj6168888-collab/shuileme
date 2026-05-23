# 睡了么独立生成资产记录

## gxs_role_gentle_woman_chat_idle

- 日期：2026-05-14
- 用途：小助手 Live 面对面聊天默认角色大半身形象。
- 原始生成图：`docs/assets/imagegen/gxs_role_gentle_woman_chat_idle_source.png`
- APK 接入图：`android-app/src/main/res/drawable-nodpi/gxs_role_gentle_woman_chat_idle.png`
- Live 舞台裁剪图：`android-app/src/main/res/drawable-nodpi/gxs_role_gentle_woman_live_stage.png`
- 处理说明：原图按纯绿背景生成。测试透明抠图后发现边缘有轻微绿色溢出，首版 APK 改用暖白背景合成版接入，并裁出适合 Live 首屏的近景版本，避免人物过小。

提示词：

```text
Use case: stylized-concept
Asset type: 3D companion character for an elderly sleep care Android app live voice chat screen
Primary request: gentle caring young Chinese woman companion, patient and warm like a family caregiver, default assistant for elderly users.
Scene/backdrop: perfectly flat solid #00ff00 chroma-key background for background removal.
Subject: half-body young woman, soft green and cream casual clothes, gentle smile, calm eyes, hands lightly together or small wave, facing the viewer directly.
Style/medium: warm high-quality 3D mobile app character, soft rounded shapes, polished toy-like material, friendly but not childish.
Composition/framing: vertical half-body portrait, upper body visible, hands visible, generous padding, clean silhouette, suitable for a face-to-face live chat UI.
Lighting/mood: warm, trustworthy, affectionate, soft studio lighting.
Color palette: soft green, warm cream, gentle skin tones, warm white highlights.
Constraints: no text, no watermark, no white coat, no nurse outfit, no hospital, no medical symbol, no sexualized styling, do not use #00ff00 anywhere in the subject.
```
