# 0.6.19 社区视频扩展与追番账号

本版本在 0.6.18 的 Media3 原生播放器、离线缓存上增加可安装的 JavaScript / TypeScript 视频源运行时，以及 AniList / MyAnimeList 授权与逐条双向同步。包名、图标与发布证书沿用已有版本。

## 视频扩展

更多工具 / 我的 → 社区视频源扩展，或 Anix 动漫详情 → 社区扩展找剧集。填写 Seanime / Anix `onlinestream-provider` JSON 清单网址，也可通过系统文件选择器导入 JS / TS。安装前显示脚本来源并确认；支持停用、移除及本机加密偏好设置。清单支持单个 manifest、manifest 数组或带 plugins 数组的对象，脚本字段为 payloadURI。

选择扩展后搜索作品、选择匹配条目、剧集、服务器和画质，确认本地集数后进入原生播放器。剧集与请求头关联保存，支持 Referer / Origin / User-Agent、外置 SRT/VTT/ASS 字幕、续播和缓存。字幕联网加载，不承诺离线字幕。缓存与普通视频导出沿用视频库功能。没有捆绑第三方视频源，也不保证所有社区扩展均兼容：网站登录、地区、DRM、网页挑战及未实现的扩展宿主接口仍可能导致失败。

QuickJS-KT 1.0.15 / QuickJS 限制运行时间、内存及堆栈；TypeScript 5.8.3 官方编译器按 npm SHA-512 校验后打包。只暴露明确的网络、DOM、URL、加密及计时接口，不暴露 JVM 反射、进程、文件和知更账号。扩展只能请求 HTTPS 公网地址，账号请求阻止携带授权跨站跳转；扩展偏好只提供给相应扩展。完整开源许可随 APK 保存。

## 账号设置与同步

更多工具 / 我的 → 追番账号与同步。使用为知更注册的公开 Client ID，不能填写 Client Secret，也不借用 Anix 的应用身份。

- AniList 开发者页面：https://anilist.co/settings/developer，回调地址 `cn.zhigeng.app://oauth/anilist`，implicit grant。
- MyAnimeList 开发者页面：https://myanimelist.net/apiconfig，注册 public/native 客户端，回调地址 `cn.zhigeng.app://oauth/mal`，使用官方目前支持的 plain PKCE。

在授权应用设置保存 Client ID 后，点击连接账号，浏览器跳到服务官网登录并批准。授权返回检查地址、随机 state、重复参数和十分钟有效期；令牌经 Android Keystore 加密后保存在本机。MAL 支持令牌续期；AniList 过期后重新授权。

远端追番按页读取。每次导入只替换确认的这一条本地状态与集数；每次上传只写入确认的这一部状态、已看集数，保留其他条目和评分。导入提示本地与远端的当前值；取消不会写入。MAL 条目通过 AniList 的 idMal 对应关系关联本地目录。断开连接仅清除本机凭证，不删除本地或远端追番。播放完成仍只更新本地追番，远端上传需要明确确认。

## 验证范围

单元测试检查授权 state/PKCE/参数、HTML 宿主、视频源结构、真实 QuickJS 执行与 TypeScript 编译、异步 DOM/加密及死循环中断。Android 回归使用明确标注的原创 TypeScript 扩展、原创 H264/AAC 视频及本地模拟 OAuth/追番服务，检查真实 UI、原生解码、请求头/字幕、离线缓存、加密、MAL 刷新与导入上传确认。结果见 releases/checks-0.6.19；未生成或失败的结果不视为通过。

真实 AniList/MAL 登录仍需要有效 Client ID 与用户亲自在官网授权。本地协议测试不等于真实账号联调；未验证的社区扩展和实体手机不列为已通过。

官方参考：https://docs.anilist.co/guide/auth/ 、https://docs.anilist.co/guide/auth/implicit 、https://myanimelist.net/apiconfig/references/authorization 。
