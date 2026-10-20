# 知更 0.6.7 · 安卓预览版安装说明

这是一款独立原生 Android App（Android 8.0+），并非网页快捷方式。

## 获取两种安装包

- **已发布、可覆盖旧版的正式版 0.6.6**：[`Zhigeng-v0.6.6.apk`](Zhigeng-v0.6.6.apk?raw=true)。它沿用既有发布证书；尚不包含 0.6.7 的完整离线封面及最新图标。
- **0.6.7 安装预览版（直接下载）**：打开 [GitHub Releases](https://github.com/nmm26301-maker/zhi-geng/releases)，选择已发布的最新 `知更 Android 0.6.7 预览`，从 Assets 点击 `Zhigeng-v0.6.7-preview.apk`。只有构建、阅读器和模拟器检查全部通过，才会自动创建预览发布。若 Releases 暂无预览版，请查看 [Android Actions](https://github.com/nmm26301-maker/zhi-geng/actions/workflows/android.yml) 的最新运行状态；也可在**成功**运行的 Artifacts 下载 `Zhigeng-native-android-v0.6.7` ZIP 并解压。
- 同一个 ZIP 里的 `Zhigeng-v0.6.7-unsigned.apk` 是原包名的**未签名**正式版构建产物，**不能直接安装**，也不能直接覆盖 0.6.6。另有 `Zhigeng-debug-test.apk` 仅供测试。

**注意**：只有当 Android Actions 的 build / emulator / reader 任务实际成功、Artifact 存在时，才能使用 0.6.7 预览版。源码提交和工作流触发不等于构建成功。本文件不宣称构建已完成。

## 手机安装

1. 用安卓手机浏览器打开上面的 GitHub Releases 页面，选择最新预览发布的 APK；预览版尚未发布时可以先安装已有正式版 0.6.6。
2. 直接下载并点击 `Zhigeng-v0.6.7-preview.apk`；若从 Actions 的 ZIP 下载，先用文件管理器解压。
3. 必要时仅允许当前文件管理器或浏览器“安装未知应用”。
4. 桌面显示「知更预览」；原「知更」不会被覆盖，两者的收藏、书架、登录信息各自独立。

预览包使用构建机器临时生成的 Android debug 证书给 **release 变体**签名。这不是正式生产发布证书；跨次运行的预览 APK **可能不能互相覆盖升级**，可卸载上一个预览版后安装下一版，但卸载会清除预览版的本地数据。不要用预览包替换正式版签名，也不要把正式签名密钥上传到公开仓库。

## 包名与校验

- 正式应用 ID：`cn.zhigeng.app`（0.6.6 原发布证书）。
- 预览应用 ID：`cn.zhigeng.app.preview`（0.6.7 测试发布签名）。
- 构建 ZIP 包含 `preview-package-info.txt`、`preview-signature.txt`、`preview-sha256.txt`，用于核实实际签名、包名和完整性。
- 构建对 28 张精选封面执行来源与图片类型校验；若不足 28 张则阻止打包。模拟器对两种安装包分别进行导航/启动校验，另对正式 debug 包做阅读器、FCC 协议模拟测试。

首次测试前请勿卸载原知更，避免清除原有的书架与配置。Z-Library 账号下载、真实 FCC 网关、第三方视频播放与支付尚未完成真实账户端到端验证。
