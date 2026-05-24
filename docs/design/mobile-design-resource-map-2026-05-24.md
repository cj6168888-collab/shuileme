# 狗熊睡眠移动端设计资源拆解

生成日期：2026-05-24

这份文档把设计大模型输出的两张总图拆成可落地的页面资源和组件资源，作为后续移动端改版的视觉基准。当前拆解是基于 PNG 设计板的精确裁切，不是 Figma/PSD 分层文件；适合对照实现、提取视觉比例、复用图像元素。

## 源设计板

- 全页面设计板：`docs/design/source/mobile-all-pages-board.png`
- 全组件设计板：`docs/design/source/mobile-component-system-board.png`

## 页面级资源

目录：`docs/design/assets/pages/`

- `01_home_sleep_guard.png`：首页睡眠守护。保留 Hero、整排睡眠波形、睡眠报告入口、吃药提醒、健康习惯、底部导航。
- `02_companion_live_avatar.png`：小助理数字人实时陪伴。单屏承载数字人、语音条、按住说话、结束对话。
- `03_sleep_report.png`：睡眠报告。包含评分、睡眠指标、波形、睡眠阶段。
- `04_pre_sleep_check.png`：睡前自检。包含情绪、身体、咖啡因、晚餐、运动、屏幕使用。
- `05_medication_reminder.png`：吃药提醒设置。包含总开关、药物卡片、重复、提前提醒、用药记录。
- `06_health_habits.png`：健康习惯设置。包含喝水提醒、久坐提醒、时间间隔、开始/结束时间。
- `07_settings_home.png`：设置首页。包含用户信息、服务器能力、主人档案、提醒通知、设备管理、隐私等。
- `08_server_capability_check.png`：服务端账号与能力检查。明确展示连接、登录、同步、睡眠分析、提醒、数字人等状态。
- `09_owner_profile_memory.png`：主人档案/记忆。展示主人信息和长期记忆列表。
- `10_morning_brief_care.png`：早安简报/今日关怀。展示问候、昨晚回顾、今日关怀建议。

## 组件级资源

目录：`docs/design/assets/components/`

- `01_brand_logo_usage.png`：品牌标识、应用图标、横版组合。
- `02_color_palette.png`：色彩规范。
- `03_typography.png`：字号层级。
- `04_buttons_switches.png`：主按钮、危险按钮、次要按钮、开关。
- `05_bottom_navigation.png`：底部导航选中和未选中状态。
- `06_home_hero_card.png`：首页守护 Hero 卡片。
- `07_sleep_waveform_card.png`：睡眠波形整排卡片。
- `08_waveform_styles.png`：不同波形状态。
- `09_report_check_rows.png`：报告与自检行。
- `10_medication_card.png`：吃药提醒卡片。
- `11_medication_single_row.png`：单条吃药提醒行。
- `12_health_habit_card.png`：健康习惯卡片。
- `13_habit_single_rows.png`：单条健康习惯行。
- `14_server_status_row.png`：服务器状态行。
- `15_voice_meter.png`：语音条/音量可视化。
- `16_avatar_states.png`：数字人头像状态。
- `17_memory_profile_card.png`：个人记忆卡片。
- `18_status_chips.png`：状态徽章/标签。
- `20_dialog_form_elements.png`：弹窗与表单元素。

## 细分元素资源

目录：`docs/design/assets/elements/`

- `brand_app_icon_large.png`：大尺寸应用图标，可用于启动页、关于页、品牌展示。
- `brand_app_icon_small.png`：小尺寸应用图标，可用于设置页用户卡片。
- `brand_shield_mark.png`：盾牌品牌符号，可用于状态徽章或守护状态。
- `brand_horizontal_lockup.png`：横版品牌组合。
- `home_bear_bed_scene.png`：首页睡熊床景插画，可替换当前 Hero 插画资源。
- `companion_avatar_full.png`：小助理数字人主视觉。
- `owner_profile_avatar.png`：主人档案头像示例。
- `morning_sun_card.png`：早安简报头部卡片素材。
- `sleep_waveform_graph_only.png`：波形曲线视觉参考。
- `voice_meter_bars.png`：语音/音量条视觉参考。
- `avatar_state_list.png`：数字人不同状态头像参考。
- `primary_button_start_guard.png`：主按钮视觉参考。
- `danger_button_end_guard.png`：危险按钮视觉参考。

## 视觉规则

- 首页只放高频任务：睡眠守护、睡眠波形检测、睡眠报告入口、吃药提醒、健康习惯。
- “小助手”进入独立数字人页面，不在首页重复占空间。
- “家人电话”归入设置或主人档案，不放首页。
- “拾音”是授权/自动调教流程，不作为普通用户设置按钮暴露。
- “故事”“助眠音”属于陪伴数字人能力，不在首页做孤立按钮。
- 设置页只承载低频配置、账号能力、设备、隐私和通知。
- 首页波形必须是整排模块，并优先展示真实状态；没有数据时显示“未开始/待生成”，不要伪装成已分析。
- 颜色以深睡蓝、健康绿、提醒橙、危险红、温暖背景和卡片白为主，不做单一蓝色堆叠。

## 实现优先级

1. 首页继续严格对齐 `01_home_sleep_guard.png` 和组件 `06_home_hero_card.png`、`07_sleep_waveform_card.png`。
2. 小助理页面对齐 `02_companion_live_avatar.png`，把数字人、语音条和通话按钮压进一屏。
3. 睡眠报告对齐 `03_sleep_report.png`，重点补齐评分、指标、波形、阶段。
4. 吃药提醒和健康习惯对齐 `05_medication_reminder.png`、`06_health_habits.png`，补齐设置流和提醒状态。
5. 设置、服务器检查、主人档案、早安简报作为第二批页面统一落地。

## 构建配置提醒

正式 APK 构建必须使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\android-app\build.ps1 -ServerBaseUrl "https://jilinpc.com/shuileme"
```

`http://10.0.2.2:8787` 只允许用于模拟器本地调试。
