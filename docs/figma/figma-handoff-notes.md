# 睡了么 Figma 交付说明

## 当前状态

- 已创建 Figma 文件：<https://www.figma.com/design/n6CyKae43g7FjXP3O2Spop>
- 已写入 Figma：封面、品牌色、文字层级、组件样例、资产交接说明。
- 未继续写入 Figma 的原因：Starter 计划 MCP 调用限额已触发，Figma 拒绝后续写入。

## 本地可导入稿

- 参考图重做版 SVG：`docs/figma/gouxiong-sleep-reference-redesign.svg`
- 参考图重做版 HTML 预览：`docs/figma/gouxiong-sleep-reference-redesign.html`
- 参考图重做版 PNG 预览：`docs/figma/gouxiong-sleep-reference-redesign-board.png`
- 绘图模型素材任务单：`docs/figma/reference-redesign-asset-generation-plan.md`
- SVG：`docs/figma/gouxiong-sleep-ui-handoff.svg`
- HTML 预览：`docs/figma/gouxiong-sleep-ui-handoff.html`

导入 Figma 时，直接把 SVG 拖入 Figma 文件，或使用 Figma 的 Import 功能选择该 SVG。

优先使用“参考图重做版”作为视觉主稿：它直接对照用户提供的横向参考图，包含左侧品牌区、三台手机、右侧四个小助手和底部组件条。“ui-handoff” 是更完整的信息架构稿。

## 覆盖页面

1. 欢迎 / 直接开始
2. 守护首页
3. 睡前自检
4. 守护中 / 夜间状态
5. 强唤醒确认
6. 早安护理
7. 小助手 Live
8. 我的 / 设置

## 设计边界

- 不做诊断，不下医学结论。
- 强唤醒只说“高风险睡眠异常”，优先让用户确认安全。
- 首页只保留一个主行动，低频入口下沉到宫格或“我的”。
- 图片只负责视觉气质，按钮、文案、状态和无障碍由原生 Android View 承担。
