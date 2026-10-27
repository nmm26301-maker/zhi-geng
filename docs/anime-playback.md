# 知更 0.6.18 · 原生剧集播放与离线缓存

入口：更多工具 / 我的 → 视频与离线缓存，或 Anix 动漫卡片 → 播放与下载剧集。

- 通过系统文件选择器关联本地视频，可批量选择并指定起始集数；文件名按自然数字顺序排列，原文件保留。
- 添加 HTTPS 点播地址，支持普通视频、HLS 与 DASH；也可从 JSON 文件或粘贴清单批量导入。替换已有集数前确认，清理旧来源的缓存。
- 使用原生 Media3 播放器，提供暂停、拖动进度、前后10秒、倍速、横竖屏、上下集及可选自动连播。每5秒与离开播放器时保存续播位置；后台暂停视频。
- 播放结束可更新本地追番，进度取已看集数与当前集数的较大值；不清除已有简介和名称别名，不同步网站账号。
- MP4 / HLS / DASH 使用持久下载任务、前台通知及独立缓存目录。支持暂停、继续、失败重试、清除缓存；用户重新打开视频库后恢复待处理任务。已完成的视频从缓存读取，离线时不以网络请求兜底。
- 普通视频缓存完成后可通过系统保存位置选择器导出独立文件。HLS/DASH 使用应用内缓存播放，未将播放清单冒充 MP4。卸载后导出的独立文件保留，应用内缓存清除。
- 「查询播出平台」读取 AniList 针对当前条目的 streamingEpisodes 与 STREAMING 外链，通过原外部浏览器策略打开来源地址。平台播放权限、地区和内容由来源决定。

清单示例：

```json
{"episodes":[{"episode":1,"title":"第一集","url":"https://example.com/1.mp4","format":"auto"},{"episode":2,"title":"第二集","url":"https://example.com/2.m3u8","format":"hls"}]}
```

每次导入1–200集，每个条目最多关联2000集。清单只存数据，不执行 JavaScript。网络源只使用 HTTPS；不自动向第三方携带账号或 Cookie。HTTP HTML/JSON 错误页不能作为成功的视频缓存。

## 开源选型与范围

核对 MaidLover69/Anix 1.3（MIT）的 README、本地视频关联与下载/播放器结构后，按 Android 平台独立实现。保留现有包名和图标，不打包 Linux libmpv，不宣称兼容 Anix / Seanime 的 JavaScript 社区扩展。

原生解码与缓存采用 AndroidX Media3 1.8.0（Apache 2.0），该版本 minCompileSdk 35，与当前编译工具链一致。完整许可在 APK assets/media3-Apache-2.0.txt 和页面的「播放器开源许可」中。

- Anix：https://github.com/MaidLover69/Anix/tree/1.3
- Media3：https://github.com/androidx/media/tree/1.8.0
- 播放：https://developer.android.com/media/media3/exoplayer
- 下载：https://developer.android.com/media/media3/exoplayer/downloading-media

社区视频扩展运行时、AniList/MyAnimeList 账号授权同步仍未实现。需 Cookie/Referer/DRM 的网站流未在本模块自动解析；应使用相应平台入口。真实账号、具体视频源与不同手机硬件仍需实际验证。本轮协议与解码测试使用自行生成的视频，结果以发布记录为准。
