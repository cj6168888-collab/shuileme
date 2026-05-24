# Image2 分层资源第一批

生成日期：2026-05-24

这一批资源用于把示意图升级成真实 App 观感。源图保留在 `source/`，经过裁切、抠色或筛选后的可用资源放在 `assets/`。

## 可用资源

- `assets/home-screen-real-effect.png`：首页真实效果图，用于后续 UI 对照验收。
- `assets/home-bear-hero-illustration.png`：新版首页睡熊卧室插画，已同步到 Android `ui_sleep_scene_image2.png`。
- `assets/home-hero-illustration-from-screen.png`：从首页真实效果图中裁出的 Hero 局部参考。
- `assets/home-waveform-card-from-screen.png`：从首页真实效果图中裁出的波形卡片参考。
- `assets/waveform-line-alpha.png`：透明睡眠波形线，已同步到 Android `ui_waveform_line_image2.png`。
- `assets/digital-human-assistant-alpha.png`：透明数字人半身像，已同步到 Android `ui_digital_human_assistant_image2.png`。

## 筛选结论

- 首页真实效果图：可作为当前首页精修基准。
- 首页睡熊插画：可直接用于 Hero 场景。
- 波形线条：可作为图片层备用；当前 Android 仍可优先使用原生绘制波形，保证可动态变化。
- 第一张数字人源图不合格，因为模型画出了假透明棋盘背景；已保留在 `source/digital-human-assistant-source.png` 供对比，不进入 App。
- 第二张数字人源图使用纯品红背景，已抠成透明 PNG，可进入 App 做小助理页视觉升级。

## 下一步

1. 首页以 `home-screen-real-effect.png` 做逐项验收，继续微调间距、字号、图标和卡片比例。
2. 小助理页使用 `digital-human-assistant-alpha.png` 作为数字人主视觉，重做为一屏完成。
3. 继续按提示词生成吃药图标、健康习惯图标、睡眠报告页和睡前自检页。
