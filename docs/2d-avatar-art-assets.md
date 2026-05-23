# 2D 小助手美术资产清单

更新时间：2026-05-18

## 资产定位

本批资产由图像模型生成，用于“睡了么”四个小助手的正式视觉方向。当前先作为 2D Avatar/Live2D 前置美术资产使用：角色卡片、设置页头像、小助手视频聊天舞台、后续分层 PNG 与 Live2D 绑定参考。

Live 主聊天舞台的视频模式已使用这些大半身 PNG，但仍放在原生 `AvatarView` 状态机里承载状态、呼吸、声波和紧急/看东西提示；文字模式则隐藏大半身，只显示大字文本气泡。

## 文件位置

概念设计板：

- `docs/assets/avatars/source/avatar_design_sheet_v1.png`

源图：

- `docs/assets/avatars/source/avatar_sister_source.png`
- `docs/assets/avatars/source/avatar_brother_source.png`
- `docs/assets/avatars/source/avatar_young_man_source.png`
- `docs/assets/avatars/source/avatar_gentle_woman_source.png`

透明 PNG：

- `docs/assets/avatars/transparent/avatar_sister.png`
- `docs/assets/avatars/transparent/avatar_brother.png`
- `docs/assets/avatars/transparent/avatar_young_man.png`
- `docs/assets/avatars/transparent/avatar_gentle_woman.png`

Android 资源：

- `android-app/src/main/res/drawable-nodpi/avatar_2d_sister.png`
- `android-app/src/main/res/drawable-nodpi/avatar_2d_brother.png`
- `android-app/src/main/res/drawable-nodpi/avatar_2d_young_man.png`
- `android-app/src/main/res/drawable-nodpi/avatar_2d_gentle_woman.png`

## 角色定义

| 角色 | 视觉关键词 | UI 用途 |
|---|---|---|
| 贴心小妹 | 珊瑚粉、蝴蝶结、活泼温柔、亲近家人感 | 适合默认陪伴、撒娇提醒、喝水吃药 |
| 懂事小弟 | 绿色连帽衫、认真、聪明、乖巧记事 | 适合提醒、记物、读消息 |
| 阳光小哥 | 蓝色上衣、可靠、阳光、保护感 | 适合安全守护、异常提醒、复盘建议 |
| 温柔姐姐 | 薰衣草色、耐心、温柔、安抚 | 适合情绪陪伴、晨间简报、健康建议 |

## 当前接入

- `roleAvatarAssetName()` 已切换到 `avatar_2d_*` 资源。
- 角色选择卡、助手卡片等非 Live 主视觉会显示新图。
- Live 主聊天舞台的视频模式会显示对应角色的大半身图。
- Live 主聊天舞台的文字模式会隐藏大半身角色，只保留文本气泡和语音状态。
- 状态、嘴型提示、看东西提示和紧急提示仍由 `AvatarView` 驱动；这些 PNG 尚未拆层，不等同于完整 Live2D。

## 后续拆层计划

下一阶段需要把每个角色拆成可动画层：

- 头发前层 / 后层
- 脸部底图
- 左右眼白、瞳孔、高光
- 上眼皮、下眼皮
- 左右眉毛
- 嘴型 A/I/U/E/O 和闭嘴
- 脸颊红晕
- 躯干、左右手、衣服局部
- 表情层：开心、担心、安慰、着急、认真看

拆层完成后，再用 `AvatarCommand` 驱动这些层，或替换为正式 Live2D SDK。
