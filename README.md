# 知更 · 影音书籍雷达

电影、电视剧、动漫、综艺和书籍的年份筛选、搜索、榜单、收藏与新作发现。

## 数据源

- TMDB：电影/电视剧/综艺；需要自行配置 API Key 或 Read Access Token。
- Jikan → AniList：动漫主备来源，发生超时、限流或网络故障会尝试备用接口。
- Open Library → Google Books：书籍主备来源。注意 Open Library 是作品首版年份，Google Books 是具体版本出版年份。
- TVmaze：在没有 TMDB 令牌时作为电视剧及综艺有限备用来源。
- 各分类并行加载，单一来源失败不影响其他分类；相同筛选条件的成功结果缓存最多 7 天，备用数据明确标为非实时。

## 使用和部署

直接访问 index.html，或从 GitHub Settings → Pages 启用 Pages，在 Build and deployment 里选择 GitHub Actions。
若已有 GitHub Actions 部署流程，推送 main 后可自动部署；部署地址通常是 https://nmm26301-maker.github.io/zhi-geng/ 。
网页的 TMDB 密钥存入当前设备浏览器 localStorage，**不得将令牌写入 GitHub 公共源码**。正式多人使用需要服务端密钥代理。

免费第三方 API 存在网络、限流和 CORS 限制，不保证全球网络均可用；在首访且无缓存情况下，多路 API 都不可用可能显示空数据。

## 书籍资源桥接（来自用户提供的 Z-Library APK 需求）

- 点击「书籍」分类后，显示独立的「阅读资源」工具区。
- 每本图书卡片底部都有「查找阅读资源」，可以打开 Open Library、Google Books、Project Gutenberg 对应的检索或作品页面，并复制书名与作者。
- 在「接口与偏好设置」填写自己信任的 **HTTPS** 图书网址，即可在图书详情中打开外部 Z-Library 入口。网址可以包含 `{query}` 占位符，知更会以 URL 编码后的“书名 + 作者”替换它；没有占位符则只打开所填网址。网址保存在浏览器本地，不上传 GitHub。
- 网站不会自动使用 APK 内的私有接口、账号、Tor 组件或下载能力，也**没有**把 APK 植入网页。网站不托管第三方程序安装包，不提供未经授权的作品下载。
- 提供外部入口不代表我们对第三方网址真实性、安全性或内容合法性进行了认证；用户需要自行确认。

关于提供的 APK 的静态检视与后续原生整合边界：参见 [docs/zlibrary-apk-review.md](docs/zlibrary-apk-review.md)。

Z-Library 只预留接口位置，不接入未经核实的下载接口，本站不提供未经授权的作品下载。

文档：[TMDB](https://developer.themoviedb.org/)、[AniList](https://docs.anilist.co/)、[Jikan](https://docs.api.jikan.moe/)、[Open Library](https://openlibrary.org/dev/docs/api/search)、[Google Books](https://developers.google.com/books/docs/v1/reference/volumes/list)。
