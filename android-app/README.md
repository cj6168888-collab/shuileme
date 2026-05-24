# 睡了么 Android App

这是睡了么的原生 Android MVP。当前实现使用 Java 原生 View 和 Android SDK 直编脚本，不依赖 Gradle。

## 本地构建

前置条件：

- JDK 17 或兼容 JDK。
- Android SDK，需包含 platform、build-tools、platform-tools。
- 已设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。

构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

真机交付包可显式写入默认服务端地址：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -ServerBaseUrl "https://jilinpc.com/shuileme"
```

测试：

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

输出 APK：

```text
build/outputs/apk/gouxiong-sleep-debug.apk
```

## DeepSeek 测试 Key

不要把 API Key 写入源码或 APK。

本机调试可创建 `local.deepseek.properties`：

```properties
deepseek.apiKey=sk-...
deepseek.model=deepseek-v4-flash
```

然后连接测试机运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\set-deepseek-test-key.ps1
```

该文件已被 `.gitignore` 忽略，构建脚本不会把它打进 APK。
