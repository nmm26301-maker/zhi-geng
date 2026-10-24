# 知更 0.6.13 · 两个独立阅读工具

入口：阅读页、我的页面与顶部「更多工具」。原有发现、榜单、书架及外部浏览器策略保持兼容。

## BookOrbit 书库

知更原生客户端连接用户自己的 HTTPS BookOrbit 服务。支持用户名/密码登录、同服务器 Magic Link、书库切换、书名/作者搜索、20本分页、详情、已支持文件格式下载与加密会话恢复。密码不落盘，会话按服务器保存于 Android Keystore 加密存储。支持有效 TLS 证书的自托管地址、子路径与自定义端口。

下载回到现有书架，沿用完整文件校验、100MB上限、取消清理、Download/知更独立副本及系统另存。EPUB/TXT/PDF 使用原生阅读器；BookOrbit 的漫画、有声书及服务器管理功能提供外部浏览器入口。完整 BookOrbit 服务器不在手机 APK 内运行，也不会替用户部署服务器。官方说明入口交给浏览器；App 不附带演示站免密登录令牌。

客户端按 BookOrbit v1 接口独立编写，没有打入上游服务器或 Vue 前端源码。协议核对提交：`bookorbit/bookorbit@99aa7dce705986eac29a8f73aa65331de5f50680`。

上游：https://github.com/bookorbit/bookorbit
官方说明：https://bookorbit.app/
上游协议：AGPL-3.0-only，另有 ADDITIONAL_TERMS.md 与 NOTICE；这些文件适用于上游代码。

## 极映空间

独立原生页面提供漫画、动漫、电子书、影视、字幕与应用工具分类，及本机最近搜索与清除。关键词经编码传给原站 `https://jys66.top/index.html?q=...`；漫画分类链接为 `/manhua.html`。所有网址均通过 BrowserLinks 交给外部浏览器，知更不内嵌网站。分类与搜索行为依据原站 2026-10-23 的公开 HTML 和 common.js 核对，未猜测第三方漫画 API。

## 验证范围

本地原创 TLS 协议服务验证 BookOrbit 登录、原生令牌续期、目录、搜索、详情与原创 EPUB 下载；模拟器检查会话重启、字节一致的共享副本、原生阅读与漫画/搜索浏览器 Intent。真实用户的自托管服务、第三方漫画内容及官方演示服务须按实际可用性核验，不以本地协议服务冒充真实站点。


## 0.6.13 发布核验

Android 13（API 33）已通过：原正式签名 0.6.12 → 0.6.13 覆盖安装，UID、首次安装时间、书架、收藏和重启数据保留；BookOrbit 原创 TLS 协议登录与令牌续期、搜索、加密会话重启、EPUB 下载、手机副本字节一致及原生阅读；极映空间搜索/漫画分类交给外部浏览器；12 个原有网址入口；共享副本的重名保护、系统另存、取消和卸载后保留。

新增流程与回归：[Actions 40001415779](https://github.com/nmm26301-maker/zhi-geng/actions/runs/40001415779)。原签名升级：[Actions 39992766675](https://github.com/nmm26301-maker/zhi-geng/actions/runs/39992766675)。完整构建中的编译、单元测试、原生基线与 EPUB/TXT/PDF 阅读阶段已通过；该次工作流后续因 Android 13 日志隐去 URL 路径导致旧测试断言失败，修正后专项工作流通过。真实用户服务器及 Android 8/9 新页面尚未验证。正式文件校验值见[发布记录](../releases/Zhigeng-v0.6.13.json)。
