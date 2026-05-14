# 狗熊睡眠

狗熊睡眠是一款面向夜间安全守护的睡眠健康检测手机软件。核心目标不是只做睡眠评分，而是在疑似噩梦、惊恐、严重鼾声、喘息/憋气等异常发生时，按风险等级及时唤醒用户，并在无响应时通知联系人。

当前阶段：竞品和开源项目调研、MVP 产品方案、Android-first 技术路线、非诊断建议边界。

## 版权

佛山吉麟数字生命研究院荣誉出品。

本项目采用 Apache License 2.0 开源。使用、分发、演示或二次开发时，请保留 `NOTICE` 文件和以上版权标注。

## 文档

- [睡眠异常实时唤醒手机软件调研与产品方案](docs/sleep-health-detection-product-brief.md)
- [AI 优先睡眠健康功能规划](docs/offline-mvp-feature-plan.md)
- [原生 APK UI/UX 设计规范](docs/native-apk-ui-ux-spec.md)
- [UI/UX 重新设计总方案](docs/independent-ui-ux-redesign-plan.md)
- [Android 技术实施方案](docs/android-technical-implementation.md)
- [检测算法与测试方案](docs/detection-algorithm-test-plan.md)
- [权限与发布风险清单](docs/permissions-release-risk-checklist.md)
- [视觉资产方案](docs/visual-assets-spec.md)
- [独立绘图模型资产清单](docs/imagegen-independent-asset-manifest.md)
- [小助手设计文件](docs/companion-assistant-design.md)
- [小助手 Live 实时陪伴架构](docs/live-companion-architecture.md)
- [Gemini Live 接入路线](docs/gemini-live-integration-roadmap.md)
- [开源发布清单](docs/open-source-release-checklist.md)
- [APK 交付说明](docs/apk-delivery-notes.md)

## 初始 MVP 方向

- Android 原生 APK；首个可安装包使用 Java 原生 View、无外部依赖直编，后续可迁移 Kotlin + Compose。
- 引导设置：直接开始 + 简单三步设置，详细项放到设置页。
- 麦克风 + 加速度计整夜前台服务。
- 鼾声、尖叫/喊叫、喘息、咳嗽、强动作的实时检测。
- 分级唤醒：轻提示、强铃声、确认按钮、最多 3 位紧急联系人电话/短信升级。
- 唤醒输出：手机扬声器、蓝牙音箱、震动、屏幕常亮和闪光灯回退。
- 唤醒声音：内置无版权轻快纯音乐、本地歌曲、亲人录音。
- 智能分析：结合昨晚记录、每晚汇总、7/30 天趋势、主人档案和今天状态生成复盘建议。
- 小助手：四种角色、自然语言第一次认识、主人起名定身份、分步建档、今日状态记忆、晨间护理、吃药喝水提醒、摄像头视觉陪伴和 DeepSeek 联网分析。
- 视觉陪伴：首次使用即申请摄像头权限；进入小助手聊天时默认低清自动看一眼，压缩图片后交给联网模型分析，把药品、手机、钥匙、眼镜等常见物品最近位置写入本机数据库；用户问“钥匙在哪”时优先直接回答最近看到的位置。
- 默认本地处理和本地加密，不保存整夜录音，只保存结构化数据和可选异常片段。
- 异常证据：记录页展示触发时的手机声音/动作指标、可选现场短录音，并把手表/血氧仪的心率、血氧、呼吸率读数按时间对齐，供用户和医生复盘。
- 建议能力：基于异常记录给出睡眠环境、作息、睡姿、复盘和就医沟通建议。
- 医疗边界：第一版不做诊断、不下医学结论，不宣称诊断睡眠呼吸暂停。

## 当前 APK

- 调试包由本地构建生成：`android-app/build/outputs/apk/gouxiong-sleep-debug.apk`
- 构建命令：`powershell -ExecutionPolicy Bypass -File .\android-app\build.ps1`
- 交付说明：[APK 交付说明](docs/apk-delivery-notes.md)

## 视觉参考

- [狗熊睡眠 UI/品牌概念图](docs/assets/gouxiong-sleep-ui-brand-board.png)
- [小助手灵动聊天 UI 概念图](docs/assets/companion-live-chat-ui-board.png)
