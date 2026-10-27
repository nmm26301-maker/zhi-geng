# Anix v1.3.0 · 知更原生动漫目录与追番

入口：更多工具、阅读页与我的页面中的 Anix v1.3.0。原生实现 AniList 动漫搜索、热门目录、按 hasNextPage 翻页、详情及下一集播出时间；状态分为想看、在看、已看完、搁置、弃番，支持已看集数校验、本地分页、确认移出、进程重启与离线查看。追番使用独立本地存储，不改动现有书架，不需要 AniList 密钥或登录，不进行账号同步。

实现借鉴 MIT 项目 MaidLover69/Anix v1.3.0 的目录/追番设计，并核对其 src-tauri/src/clients/anilist.rs 与 AniList 官方 API 文档，Android 适配独立编写。数据通过 https://graphql.anilist.co 公开只读查询取得；关键词使用 GraphQL variables，默认过滤成人条目，限制响应大小，处理 HTTP/GraphQL 错误和 429 冷却。本地列表离线可用，失败请求不会写入虚假目录。下一集时间来自来源，在设备时区显示。

已核对 upstream README、MIT LICENSE 与 release 标签 1.3。官方资产是 Linux 的 Anix_1.3.0_amd64.AppImage。知更没有打包其 Tauri/libmpv 桌面播放器或视频扩展，没有将目录资料描述为可直接播放的视频。官方发布页、官网及 AniList 条目均通过外部浏览器打开。

- 上游：https://github.com/MaidLover69/Anix
- v1.3.0：https://github.com/MaidLover69/Anix/releases/tag/1.3
- AniList 分页：https://docs.anilist.co/guide/graphql/pagination
- AniList 限流：https://docs.anilist.co/guide/rate-limiting

验证使用原创本地 TLS 元数据；真实公开 API 的只读探测另记 live-api.json，不把本地服务当成真实线上可用性的证据。Android 测试用 Activity 及本地端点只存在于 debug 构建。

## 0.6.17 追番改进

「我的追番」可按五种状态筛选，按名称或最近更新排序；本地搜索匹配保存的中文、罗马字及英文名称。筛选、排序和已提交关键词在重启后保留，旋转屏幕保留本地页码。旧版记录仍可使用；没有修改时间的旧记录排在最近更新之后，再按名称和条目 ID 稳定排序。

「想看」「在看」条目增加「看完一集 +1」；首次记集数进入在看，达到已知总集数时转为已看完。搁置、弃番、已看完和达到上限的条目不显示快捷按钮，仍可用「更新追番」手动调整。新保存的条目保留有长度上限的简介及名称别名，可离线查看详情和搜索。旧记录需联网重新保存后才有离线简介，播出时间明确标为上次保存的信息。

本轮未加入视频扩展、在线播放、下载或账号同步；不会把动漫目录当作视频源。验证结果另记本版本发布记录，未完成的验证不得沿用 0.6.16 的结果。

## 0.6.18 原生播放与下载

新增视频关联、原生播放、续播位置、上下集、结束后更新本地追番、MP4/HLS/DASH 离线缓存和普通视频导出。可查询当前动漫目录收录的播出平台，通过浏览器打开来源地址。实现范围、使用方法、开源选型与未完成部分见 [原生剧集播放](anime-playback.md)。
