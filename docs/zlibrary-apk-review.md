# 用户提供的 Z-Library Android APK：静态检视与集成边界

本文件记录 2026-10-15 用户提供的 `base.apk` 静态检查结果，不代表第三方程序官方来源或安全认证。

| 项目 | 检视结果 |
|---|---|
| 文件 | base.apk |
| 文件大小 | 33,076,250 bytes |
| SHA-256 | `159a142595ab2f6be62a4b231d5d6193739607bfe1d816271bf52f217de32120` |
| DEX 内自述版本 | ZLibrary Android application ver.1.11.4 |
| 代码标识 | `com/positron_it/zlib` |
| 证书主题 | CN=Positron_IT, OU=Positron_IT, O=Positron_IT |
| 证书 SHA-256 指纹 | `DF:FE:23:63:F9:A7:DC:72:1F:FA:6E:D7:06:DF:98:8A:C6:E2:07:22:21:B6:F9:B9:DA:DB:75:5E:56:6D:1F:3B` |
| 运行库 | 含 Android 原生库及 `libtor.so` |

## 为什么不将 APK 直接合并到知更？

目前知更是 GitHub Pages 上的 HTML/JS 静态网页，而 APK 是已编译的 Android 原生程序。网页不能直接执行 APK 内部代码或 Android 组件。对闭源程序进行重新打包还涉及签名、授权、依赖和安全检查；因此**没有**将此 APK 作为网站依赖上传，也没有将其伪装成“已经完成原生合并”。

## 本次实际完成的部分

在知更的书籍模块中实现完整的网页侧资源发现交互：图书元数据从已有的 Open Library / Google Books 获取，用户可打开 Open Library / Google Books / Project Gutenberg 的图书检索或公开馆藏页面；也可以自行填写 HTTPS 第三方网址或带有 `{query}` 的检索模板。此桥接不请求 Z-Library 的私人 API、不收集第三方登录凭证、不运行 APK 里的 Tor 服务，不做文件抓取、自动下载或绕过访问控制。

## 若后续开发安卓原生版

需要在独立 Android 工程中，以确认来源、安全性和授权为前提，明确采用 Android Intent 公开跳转或用户主动操作的外部应用跳转；不能假定任意 APK 能被嵌入、改包或提取源码。未经过完整安全审查，不应向用户自动推送此安装包。
