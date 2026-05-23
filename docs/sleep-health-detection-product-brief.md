# 睡了么：睡眠异常实时唤醒手机软件调研与产品方案

调研日期：2026-05-13

## 1. 竞品：蜗牛睡眠

蜗牛睡眠的公开定位是“睡眠健康管理”应用。App Store 页面显示它由 Seblong Technology Co., Ltd. 开发，类别为健康健美，主打“睡眠监测、鼾声梦话记录、深度睡眠检测、失眠哄睡”，并宣称全球超过 7000 万用户使用。公开功能包括：

- 睡眠监测：记录日、周、年睡眠数据，分析深睡眠、浅睡眠、睡眠效率、睡眠质量。
- 夜间声音记录：梦话、鼾声、磨牙、咳嗽等声音事件，分析打鼾时长、分贝、严重程度、阻塞程度等。
- 健康初筛与建议：失眠、打鼾、睡眠呼吸暂停风险预警及改善建议。
- 智能闹钟：浅睡唤醒。
- 助眠内容：白噪音、冥想、音乐等。
- 健康生态：iOS 端支持将部分睡眠数据写入 Apple Health，App Store 页面说明主要写入卧床时间、起床时间。

Android 分发页暴露的权限对我们有参考价值。小米应用商店列出了麦克风前台服务、健康前台服务、媒体播放前台服务、防止手机休眠、振动、通知、勿扰模式、蓝牙、文件读写等权限。这说明睡眠监测 App 要想稳定过夜运行，不能只做普通后台任务，必须把“前台服务、低功耗采样、权限解释、异常恢复”当作核心工程能力。

蜗牛睡眠的不足或机会点：

- 更强在“事后报告”和“风险初筛”，不是明确的夜间实时安全守护。
- 对异常事件的处理更偏记录和建议，未突出“异常发生时立即唤醒用户或通知家属”。
- 麦克风、健康数据和云端存储会天然带来隐私顾虑。我们的产品应默认本地处理，只保留异常片段，给用户强控制权。
- 医疗边界需要谨慎。蜗牛睡眠自己的说明也强调睡眠报告、风险评估、改善建议仅供参考，不代表最终结论。

## 2. GitHub 开源参考

| 项目 | 类型 | 可借鉴点 | 局限 |
| --- | --- | --- | --- |
| [natgluons/ChronoSense](https://github.com/natgluons/ChronoSense) | Python 睡眠音频分析 | 使用 librosa/torchaudio、PyTorch/Sklearn 做鼾声、咳嗽、环境噪声等扰动识别，适合做算法训练和原型验证 | 不是成熟移动端实时 App，星标少，工程成熟度有限 |
| [britig/SnoreDetection](https://github.com/britig/SnoreDetection) | Android 鼾声检测 | 使用 Android AudioRecord 采样，基于周期性高低声判断鼾声，可作为第一版规则检测参考 | 老项目，规则粗糙，不能覆盖呼吸暂停和复杂噪声 |
| [gernot-h/NoiseAlert](https://github.com/gernot-h/NoiseAlert) | Android 噪声触发器 | 麦克风监测、阈值触发、唤醒设备、发送广播，适合参考“声音触发后唤醒手机”的工程路径 | 只看音量阈值，不理解睡眠事件 |
| [ian0697/SleepAdviser](https://ian0697.github.io/SleepAdviser/) | Android 睡眠助手 | 用加速度计做自动睡眠跟踪，包含闹钟、睡眠音乐、SQLite/Firebase | 项目偏课程/演示，算法和实时安全能力较弱 |
| [vmiklos/plees-tracker](https://github.com/vmiklos/plees-tracker) | Android 睡眠记录 | Kotlin、MIT、F-Droid、离线、CSV 导入导出，适合参考隐私友好的睡眠日志和数据管理 | 明确“不在睡眠时做任何事”，没有传感器监测 |
| [cbrnr/sleepecg](https://github.com/cbrnr/sleepecg) | ECG 睡眠分期 | 基于 ECG 的心搏检测、睡眠分期和 PSG 数据集读取，适合未来接入手表/胸带数据 | 需要 ECG 信号，不适合手机裸机 MVP |
| [elyiorgos/sleeppy](https://github.com/elyiorgos/sleeppy) | 可穿戴加速度计睡眠分析 | Wrist-worn accelerometer 的睡眠/清醒分类、睡眠指标、报告生成 | Python 2.7 时代项目，偏离线研究，不是实时移动端 |
| [UCLA-VMG/NonContactApneaDetection](https://github.com/UCLA-VMG/NonContactApneaDetection) | 热成像 + 雷达呼吸暂停检测 | 提供非接触式呼吸/呼吸暂停检测思路，含数据采集、预处理、推理代码 | 依赖热成像/雷达硬件，不适合纯手机第一版 |
| [haitham-chabayta/contactless-respiratory-rate-measurement-app](https://github.com/haitham-chabayta/contactless-respiratory-rate-measurement-app) | Android 热成像呼吸率 | OpenCV 找鼻下区域、低通滤波、峰值检测，适合参考呼吸信号处理 | 依赖 FLIR 外设，采样只有 30 秒，不是整夜监测 |
| [OpenSeizureDetector](https://github.com/openseizuredetector) | 发作告警生态 | Android / 可穿戴 / caregiver alert 的告警升级链路值得参考 | 疾病不同，算法不能直接复用 |
| [dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) | 实时音频检测框架 | 实时麦克风流、80ms 音频帧、VAD、降噪、阈值调参，对低延迟音频检测有参考价值 | 目标是唤醒词，不是睡眠异常，需要重新训练事件模型 |

结论：GitHub 上有可借鉴模块，但没有一个能直接作为“手机睡眠异常实时唤醒”完整开源系统。我们应组合三类能力：手机端前台传感服务、音频/动作异常识别、分级唤醒与联系人升级。

## 3. 产品定位

产品名暂定：睡了么。

一句话：一款默认本地处理的睡眠安全守护 App，在用户睡眠中识别可能的噩梦、惊恐、严重鼾声、疑似呼吸中断等异常，并按风险等级进行温和干预、强唤醒或联系人通知；醒来后基于异常记录给出非诊断性的睡眠改善建议和就医沟通建议。

边界要清楚：不做诊断、不下医学结论，但可以给用户建议。第一版不要定位成“诊断睡眠呼吸暂停”，更稳妥的定位是：

- 睡眠异常记录与提醒。
- 疑似风险事件的个人安全告警。
- 基于夜间事件、用户反馈和可穿戴数据的生活方式建议。
- 给用户和医生沟通时提供可回放的事件线索。

## 4. 第一版核心场景

1. 睡前开启“守护模式”
   用户把手机放在床头或床垫旁，App 进行麦克风和动作采样校准，确认音量、充电、后台权限、勿扰例外、闹钟音量。

2. 夜间低功耗监听
   App 不持续保存整夜录音，只做实时特征提取。默认只保存异常前后 15-30 秒片段，用户可以关闭音频保存。

3. 异常事件分级
   - 低风险：记录事件，不唤醒。例如轻微鼾声、环境噪声、短暂翻身。
   - 中风险：温和干预。例如持续大鼾、疑似噩梦轻喊、焦躁翻动，播放低音量提示音或轻震。
   - 高风险：立即唤醒。例如疑似憋气后喘息、尖叫/惊恐、连续异常动作，触发强铃声、震动、闪光。
   - 升级风险：强唤醒后用户 30-60 秒无确认，则通知紧急联系人。

4. 早晨报告
   展示睡眠时长、异常事件时间线、鼾声/梦话/噩梦片段、唤醒次数、用户主观感受、风险趋势，并生成“今晚可以尝试”的建议卡片。

## 5. 异常检测策略

### 噩梦 / 夜惊 / 惊恐

可用信号：

- 突发尖叫、哭喊、急促语音、喘息。
- 短时间内动作强度上升。
- 如果接入手表，结合心率突增。

第一版算法：

- 音频事件分类：环境噪声、普通说话、梦话、喊叫、哭声、喘息。
- 动作特征：短窗口 RMS、峰值、周期性、翻身次数。
- 风险规则：高强度声音 + 动作突增，或连续惊恐语音超过阈值。

唤醒方式：

- 优先低刺激：呼唤用户昵称、轻柔提示音、手机震动。
- 若持续异常再强唤醒。

### 疑似呼吸异常 / 呼吸暂停风险

手机裸机不能可靠诊断呼吸暂停。第一版只能做“疑似呼吸异常提醒”，高可信应依赖 SpO2、呼吸率、心率、可穿戴或外部传感器。

可用信号：

- 鼾声周期突然中断后出现喘息或呛咳。
- 长时间高强度鼾声、鼾声频次升高。
- 可穿戴数据中的 SpO2 下降、心率异常、呼吸率异常。

第一版算法：

- 规则模型：鼾声周期、静默区间、喘息/咳嗽事件组合。
- 可选 ML：音频梅尔谱分类，识别 snore / gasp / cough / silence / speech。
- 可穿戴增强：若 Health Connect 或 BLE 设备提供 SpO2/呼吸率，用作高风险确认条件。

唤醒方式：

- 中风险先轻干预，鼓励侧睡。
- 高风险强唤醒并要求用户确认。
- 多次高风险或无响应时通知联系人。

### 磨牙 / 咳嗽 / 严重鼾声

这类事件更适合记录和趋势管理，不宜默认强唤醒。用户可单独开启“严重鼾声唤醒”或“磨牙提醒”。

## 6. 用户建议系统（非诊断）

建议系统的目标是“帮助用户改善睡眠和判断何时需要进一步咨询”，不是“判断用户患有什么病”。所有建议都应带有触发依据，并避免医学结论。

建议分三类：

- 即时建议：在中低风险事件后给出低刺激干预，例如“检测到连续高强度鼾声，可以尝试侧卧提醒”。即时建议只作用于唤醒策略，不在夜间展示复杂文本。
- 早晨建议：基于一晚数据给出可执行建议，例如“昨晚 02:10-02:40 连续鼾声较多，今晚可以尝试侧睡、抬高枕头、避免睡前饮酒，并观察是否下降”。
- 趋势建议：基于多晚重复模式提醒用户，例如“近 7 晚有 5 晚出现多次喘息/呛咳样声音，建议记录给医生或睡眠门诊查看”。

建议文案规则：

- 可以说：“疑似”“可能有关”“可以尝试”“建议记录后咨询医生”。
- 不说：“你患有睡眠呼吸暂停”“系统诊断为”“治疗方案是”。
- 每条建议必须显示依据，例如事件次数、持续时间、时间段、用户反馈。
- 建议优先给低风险行为：睡姿、枕头高度、卧室噪声、睡前饮酒/咖啡因、规律作息、压力复盘。
- 当高风险事件频繁出现，建议升级为“尽快咨询专业医生/睡眠门诊”，而不是给治疗结论。

示例建议：

- 鼾声频繁：昨晚检测到 12 段连续鼾声，最长 8 分钟。今晚可以尝试侧卧、抬高枕头，并避免睡前饮酒。
- 疑似喘息：昨晚出现 3 次“静默后喘息/呛咳”模式。建议保留事件片段，并在复诊或睡眠咨询时展示给医生。
- 疑似噩梦/惊恐：昨晚 04:18 出现高强度喊叫和动作突增。可以在早晨记录梦境、压力源和睡前活动，观察是否与压力或作息变化相关。
- 强唤醒频繁：近 7 天触发 4 次强唤醒。建议降低普通鼾声唤醒敏感度，只保留尖叫、喘息、无响应升级等高风险事件。

## 7. 技术路线

建议 Android-first 原生实现。原因是我们的核心能力依赖长时间前台服务、麦克风、传感器、勿扰例外、闹钟、Health Connect、可穿戴和电池策略，原生比跨平台更稳。首个可交付 APK 采用 Java 原生 View 和 Android SDK 直编，避免外部依赖；后续产品化版本可迁移到 Kotlin + Jetpack Compose。

架构模块：

- `SleepSessionController`：管理睡眠会话开始、暂停、结束、异常恢复。
- `SensorCaptureService`：Android 前台服务，声明 microphone/health/mediaPlayback 类型，负责整夜采样。
- `AudioPipeline`：16kHz PCM 采样、VAD、降噪、分帧、环形缓冲、特征提取。
- `AudioEventDetector`：第一版规则 + 轻量模型，识别鼾声、喘息、咳嗽、尖叫、梦话、磨牙。
- `MotionDetector`：加速度计/陀螺仪，识别翻身、突发动作、静止状态。
- `VitalConnector`：Health Connect、Wear OS、蓝牙设备，读取心率、SpO2、呼吸率、睡眠阶段。
- `RiskEngine`：多信号融合，输出风险等级、原因、置信度、建议动作。
- `RecommendationEngine`：根据事件模式、用户反馈和趋势生成非诊断建议卡。
- `LocalAnalyticsEngine`：离线计算每晚汇总、7/30 天趋势、个人基线、分析可信度。
- `AlarmEscalationManager`：轻唤醒、强唤醒、确认按钮、联系人短信/电话/通知升级。
- `LocalVault`：本地加密数据库，保存会话、事件、每晚汇总、短音频片段、用户配置。
- `ReportEngine`：生成早晨报告、趋势图、医生沟通摘要。

Android 官方 Health Connect 文档支持睡眠会话、睡眠阶段、心率、血氧、呼吸率等数据类型，并提醒睡眠跟踪需要正确处理后台执行、前台服务、权限和电池限制。第一版应接入 Health Connect 读写睡眠会话和可用的健康指标，但不能依赖它提供实时睡眠阶段，因为很多设备只在醒来后写入完整睡眠数据。

## 8. 数据模型草案

### SleepSession

- `id`
- `startTime`
- `endTime`
- `mode`: normal / sensitive / quiet
- `phonePlacement`: bedside / mattress / unknown
- `chargingState`
- `permissionsSnapshot`
- `userMorningScore`

### SleepEvent

- `id`
- `sessionId`
- `startTime`
- `endTime`
- `type`: snore / gasp / cough / speech / scream / bruxism / motion_spike / suspected_apnea / nightmare
- `riskLevel`: low / medium / high / escalation
- `confidence`
- `signals`: audio / motion / heart_rate / spo2 / respiratory_rate
- `clipPath`
- `actionTaken`: none / gentle / alarm / contact
- `userFeedback`: true_positive / false_positive / unsure

### NightlySummary

- `id`
- `sessionId`
- `sleepWindowStart`
- `sleepWindowEnd`
- `guardDurationMinutes`
- `estimatedQuietSleepMinutes`
- `eventCount`
- `highRiskEventCount`
- `autoCancelCount`
- `strongWakeCount`
- `snoreDurationSeconds`
- `longestSnoreSeconds`
- `gaspLikeEventCount`
- `coughCount`
- `nightmareLikeEventCount`
- `motionSpikeCount`
- `analysisConfidence`: high / medium / low
- `confidenceReasons`
- `createdOffline`: true

### TrendSummary

- `id`
- `range`: 7d / 30d / 90d
- `snoreTrend`
- `highRiskTrend`
- `autoCancelTrend`
- `falsePositiveRate`
- `topEventTypes`
- `recommendedPolicyChange`

### Recommendation

- `id`
- `sessionId`
- `eventIds`
- `category`: sleep_environment / posture / routine / stress_review / medical_consultation / alarm_tuning
- `title`
- `body`
- `basis`
- `priority`: low / medium / high
- `userState`: accepted / dismissed / saved

### AlarmPolicy

- `enabledEventTypes`
- `quietInterventionFirst`
- `highRiskImmediateAlarm`
- `contactAfterSeconds`
- `emergencyContacts`
- `doNotDisturbOverride`

## 9. 隐私、安全与医疗边界

必须从第一版就写进产品：

- 默认本地处理，不上传整夜录音。
- 默认只保存异常片段，用户可关闭片段保存。
- 所有事件、每晚汇总、趋势指标和音频片段本地加密。
- 结构化睡眠数据长期保存在本机；异常音频片段可设置 7 天、30 天、永久或不保存。
- 无网络时也必须能生成昨晚报告、7/30 天趋势、分析可信度和导出摘要。
- 清楚解释麦克风、前台服务、通知、勿扰例外、Health Connect 权限用途。
- 明确“不是医疗诊断，不替代医生和多导睡眠监测/居家睡眠呼吸检测”。产品可以提供生活方式、睡眠环境、复盘和就医沟通建议，但不能给疾病诊断或治疗结论。
- 若将产品宣传为“诊断睡眠呼吸暂停”或“治疗/预防疾病”，就可能进入医疗器械监管路径。FDA 明确把使用手机传感器如加速度计、麦克风测量睡眠生理参数并用于诊断睡眠呼吸暂停的软件列为可能监管的设备软件功能。中国市场也应预留 NMPA/医疗器械合规评估。

## 10. MVP 范围

第一阶段不要做全量“睡眠分期准确性”竞争，优先做异常唤醒闭环。

MVP 功能：

- Android 守护模式。
- 麦克风 + 加速度计整夜前台服务。
- 鼾声、喊叫/尖叫、咳嗽、喘息、强动作的规则检测。
- 分级唤醒：轻提示、强铃声、确认按钮。
- 无确认通知紧急联系人。
- 早晨事件时间线和片段回放。
- 基于事件模式生成非诊断建议卡。
- 用户反馈每个事件是否误报，用于后续训练。
- 本地加密存储和一键删除。

暂缓：

- 直接宣称呼吸暂停诊断。
- 云端 AI 长音频分析。
- iOS 实时守护。
- 医疗报告。
- 复杂社区、助眠内容商城。

## 11. 6 周落地计划

第 1 周：PRD、权限清单、风险规则、Android 工程骨架、交互原型。

第 2 周：前台服务、麦克风采样、加速度计采样、环形缓冲、本地数据库。

第 3 周：规则检测器，覆盖音量峰值、周期性鼾声、喘息/咳嗽、尖叫、动作突增。

第 4 周：分级唤醒、确认流程、联系人通知、勿扰例外和失败兜底。

第 5 周：早晨报告、事件回放、建议卡、用户反馈、导出摘要。

第 6 周：10-20 晚真实测试，评估误报、漏报、电量、后台稳定性、权限流失。

## 12. 验收标准

- 手机充电状态下守护模式连续运行 7 小时不中断。
- Android 系统杀后台、锁屏、勿扰模式下仍能完成高风险强唤醒。
- 使用测试音频播放鼾声、喘息、尖叫，事件检测到唤醒动作延迟小于 3 秒。
- 默认不保存整夜音频，只保存异常片段。
- 每个事件都能解释触发原因，例如“尖叫声 + 动作突增”。
- 每条建议都能解释依据，并避免诊断式文案。
- 用户可以一键删除全部本地睡眠数据。
- 高风险无确认后能通知紧急联系人。
- 医疗文案只使用“疑似风险、提醒、建议记录后咨询医生”，不使用“系统诊断为、治疗方案是、预防疾病”等表达。

## 13. 参考来源

- [蜗牛睡眠 App Store 中国区页面](https://apps.apple.com/cn/app/%E8%9C%97%E7%89%9B%E7%9D%A1%E7%9C%A0-%E7%9D%A1%E7%9C%A0%E7%9B%91%E6%B5%8B-%E9%BC%BE%E5%A3%B0%E6%A2%A6%E8%AF%9D%E8%AE%B0%E5%BD%95-%E6%B7%B1%E5%BA%A6%E7%9D%A1%E7%9C%A0%E6%A3%80%E6%B5%8B-%E5%A4%B1%E7%9C%A0%E5%93%84%E7%9D%A1%E7%A5%9E%E5%99%A8/id1025313530)
- [蜗牛睡眠小米应用商店页面](https://app.mi.com/details?id=com.seblong.idream)
- [蜗牛睡眠隐私政策](https://snailsleep.net/vip03.html)
- [Android Developers: Develop Sleep Experiences with Health Connect](https://developer.android.com/health-and-fitness/health-connect/experiences/sleep)
- [FDA: Examples of Device Software Functions the FDA Regulates](https://www.fda.gov/medical-devices/device-software-functions-including-mobile-medical-applications/examples-device-software-functions-fda-regulates)
- [FDA: Examples of Software Functions That Are NOT Medical Devices](https://www.fda.gov/medical-devices/device-software-functions-including-mobile-medical-applications/examples-software-functions-are-not-medical-devices)
- [AASM: Polysomnography for OSA should include arousal-based scoring](https://aasm.org/advocacy/position-statements/polysomnography-for-obstructive-sleep-apnea-should-include-arousal-based-scoring-an-american-academy-of-sleep-medicine-position-statement/)
- [natgluons/ChronoSense](https://github.com/natgluons/ChronoSense)
- [britig/SnoreDetection](https://github.com/britig/SnoreDetection)
- [gernot-h/NoiseAlert](https://github.com/gernot-h/NoiseAlert)
- [vmiklos/plees-tracker](https://github.com/vmiklos/plees-tracker)
- [cbrnr/sleepecg](https://github.com/cbrnr/sleepecg)
- [elyiorgos/sleeppy](https://github.com/elyiorgos/sleeppy)
- [UCLA-VMG/NonContactApneaDetection](https://github.com/UCLA-VMG/NonContactApneaDetection)
- [haitham-chabayta/contactless-respiratory-rate-measurement-app](https://github.com/haitham-chabayta/contactless-respiratory-rate-measurement-app)
- [OpenSeizureDetector](https://github.com/openseizuredetector)
- [dscripka/openWakeWord](https://github.com/dscripka/openWakeWord)
