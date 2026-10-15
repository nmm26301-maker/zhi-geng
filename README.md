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

Z-Library 只预留接口位置，不接入未经核实的下载接口，本站不提供未经授权的作品下载。

文档：[TMDB](https://developer.themoviedb.org/)、[AniList](https://docs.anilist.co/)、[Jikan](https://docs.api.jikan.moe/)、[Open Library](https://openlibrary.org/dev/docs/api/search)、[Google Books](https://developers.google.com/books/docs/v1/reference/volumes/list)。
