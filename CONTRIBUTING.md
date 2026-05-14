# Contributing

感谢关注狗熊睡眠。

## 基本原则

- 不做医学诊断，不宣称可诊断睡眠呼吸暂停。
- 夜间守护默认静默，不用运行提示音证明 App 存活。
- 默认离线，本地优先，不上传整夜录音。
- 电话、短信、麦克风等敏感权限必须有明确用途。
- 使用、分发、演示或二次开发时保留 `佛山吉麟数字生命研究院荣誉出品` 标注。

## 开发检查

提交前运行：

```powershell
cd android-app
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

不要提交：

- `android-app/local.deepseek.properties`
- `android-app/build/`
- `android-app/keystore/`
- 任意 `sk-` 开头的 API Key

## 代码风格

当前 MVP 使用 Java 原生 Android View。保持大字体、大按钮、少文字和中老年友好设计。
