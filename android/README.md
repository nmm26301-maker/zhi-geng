# 知更 Android 0.5.0

独立原生安卓应用（Java + Android SDK），不是 WebView 网页壳，不依赖前一版知更网站或 ChatGPT 网页登录。

- 安卓 8.0 及以上；通用 APK，不捆绑原生架构库。
- 发现：电影、电视剧、动漫、综艺、书籍，按年份搜索与筛选；影视口碑排序。
- 阅读：公开目录搜书，以及适配自 heartleo/zlib（MIT）的账号登录、EAPI 搜索、年份范围、语言、格式、精确检索和翻页。账号登录失败时尝试上游 RPC 登录方式。
- 书架：想读、在读、已读，收藏保存在本设备。长按移除。
- 本地图书：系统文件选择器导入 TXT / EPUB / PDF；TXT 和 EPUB 可在 App 内阅读正文，保存进度与单个书签；PDF 交给手机已安装的阅读器。EPUB 为文字阅读，不保留原版复杂排版，不支持 DRM 加密文件。
- 设置：TMDB 凭证测试、Z-Library 账号连接与清除。凭证和会话由 Android Keystore 的 AES-GCM 密钥加密；不保存密码，不写入仓库或日志。关闭系统备份，卸载会清除本机配置与阅读记录。

影视搜索需要有效 TMDB 凭证。书籍与动漫无需 TMDB。各来源可能受网络、限流、账号或源站可用性影响；Z-Library 的实时账号连接需用户自己的账号验证。

## 构建

JDK 17 / Gradle 8.9 / Android SDK 35 / AGP 8.7.3：

```bash
cd android
gradle :app:assembleRelease
```

仓库 Actions 的 `Build Zhigeng Android APK` 自动构建未签名 APK。最终交付 APK 使用单独保管的私有发布密钥签名；私钥不放公开仓库。后续更新必须沿用同一签名。

`app/src/main/assets/heartleo-zlib-MIT.txt` 保留上游许可。内置目录来自仓库已有真实数据快照，不以快照伪装实时结果。
