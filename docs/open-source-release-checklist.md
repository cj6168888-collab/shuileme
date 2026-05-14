# 狗熊睡眠开源发布清单

## 版权与开源协议

- 开源协议：Apache License 2.0。
- 版权方：佛山吉麟数字生命研究院。
- 必须保留的版权标注：`佛山吉麟数字生命研究院荣誉出品`。
- 标注位置：`NOTICE`、`README.md`、公开演示说明、再分发文档或应用内关于页面。

## 发布前安全检查

- 不提交 `android-app/local.deepseek.properties`。
- 不提交任何 `sk-` 开头的 API Key。
- 不提交 `android-app/build/` 输出目录。
- 不提交 `*.keystore`。
- 正式发行包关闭 `android:debuggable="true"`。
- 正式发行前将 DeepSeek Key 存储从 `SharedPreferences` 升级为 Android Keystore。

## 当前调试说明

当前仓库保留调试注入脚本 `android-app/set-deepseek-test-key.ps1`。它读取本机忽略文件
`android-app/local.deepseek.properties`，再通过调试 Intent 写入测试 App 的本机设置。

该脚本用于本机验证，不会把 API Key 打进 APK。

## 建议 GitHub 仓库信息

- 仓库名：`gouxiong-sleep`
- 描述：`Offline-first Android sleep guardian with elder-friendly companion care. 佛山吉麟数字生命研究院荣誉出品。`
- 可见性：Public
- 默认分支：`main`
- 创建远端仓库时不要勾选 README、License、.gitignore，本地仓库已经包含这些文件。

## GitHub 推送命令

创建空仓库后执行：

```powershell
cd D:\www\狗熊睡眠
git remote add origin https://github.com/<你的账号或组织>/gouxiong-sleep.git
git push -u origin main
```

如果远端已存在：

```powershell
cd D:\www\狗熊睡眠
git remote set-url origin https://github.com/<你的账号或组织>/gouxiong-sleep.git
git push -u origin main
```

推送后在 GitHub Actions 中确认 `Android APK Check` 通过。
