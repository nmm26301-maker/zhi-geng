# 知更 · 影音与阅读 v0.4

电影、电视剧、动漫、综艺与图书发现；支持年度筛选、关键词、个人清单、书架和本地阅读。

## 直接使用

完整版：https://zhigeng-reading.savoryvine10.chatgpt.site

进入「书籍」→「连接 Z-Library」→ 选择自己确认的账号源站，填写账号连接 → 输入书名，点击「搜书」。

也可选择「公开图书目录」，无需图书账号。影视使用自己的 TMDB API Key 或 Read Access Token，设置中可测试连接。搜索作品时默认检索所有年份，年度筛选可另选。

## 本次集成 heartleo/zlib

根据 https://github.com/heartleo/zlib 的 EAPI 登录与搜索实现，适配为适合浏览器的服务端模块，并保留 MIT 版权声明：`licenses/heartleo-zlib-MIT.txt`。

- 站内搜索书名、作者，显示真实来源返回的书目，支持年份范围、语言、精确检索、EPUB/PDF/TXT/MOBI/AZW3/DJVU/FB2 格式筛选和分页。
- 支持邮箱密码登录；EAPI 登录未通过时尝试上游的 rpc.php 表单方式。也可使用自己客户端的 remix_userid/remix_userkey 会话。
- 密码仅用于本次转接登录，不写磁盘；会话仅留在当前页面内存，刷新后需重新连接。服务器不保存账号密码和会话，也不将其放入 URL。
- 来源被拒、超时、返回验证页或重定向时显示错误，停止该次请求；不会以公开书目或旧缓存冒充账号搜索结果。
- 书籍可查看来源书页、加入书架；本版集成范围为账号连接与书目搜索，未集成下载、Kindle 投递、下载历史与全量账号同步。
- Z-Library 的域名与接口由上游控制，不保证所有源站当前可用；域名列表来自该项目，不代表官方接口认证。未使用用户真实账号完成在线端到端验证。

## 完整版与静态版

GitHub Pages 是静态托管，不能执行后端登录。Pages 保留公开目录搜索与本地阅读，并显示完整版入口。

完整版使用同源 `/api/gateway` 转接，减少浏览器直连第三方产生的跨域与网络问题。仅允许代码中列出的书目 API 和源站；外部来源自身故障、限流或账号限制仍可能造成失败。TMDB 的凭证错误与网络故障分别提示，不保证凭证一定有效。

## 本地运行 / Node 部署

Node.js 22 及以上，无生产运行依赖：

```bash
npm start
```

打开 http://localhost:3000 。也可设置 `PORT`。线上需要通过平台提供 HTTPS。

容器构建：`docker build -t zhigeng .`；启动：`docker run --rm -p 3000:3000 zhigeng`。

源文件：`assets/` 为前端，`server/gateway.mjs` 为账号及元数据适配，`server/serve.mjs` 为本地及 Node 部署入口。完整版另由 Sites 托管同一套前端与适配代码。

## 书架与阅读

- 支持想读、在读、已读，书签及阅读进度。
- 用户主动导入的 EPUB/PDF/TXT 存在设备浏览器 IndexedDB，每本上限 60 MB；电子书不上传服务器。
- EPUB 首次使用需加载 epub.js/JSZip；PDF 使用浏览器阅读器，进度手动维护。
- 书架和收藏保存在当前设备，换浏览器、清除数据不会同步。请保留原始电子书。

## 校验

```bash
npm ci
npm test
node scripts/check-book-bridge.mjs
node scripts/check-book-library.mjs
node scripts/test-book-ui.mjs
node scripts/test-book-resilience.mjs
node scripts/test-real-book-search.mjs
```

包含后端筛选传参、备用登录、域名与跳转限制、账号会话不持久化，以及界面连接→搜索→详情→书架→翻页→异常→断开的模拟接口测试。模拟测试不代表第三方实时可用性。

## 公开数据源

TMDB（影视，需自备凭证）、Jikan/AniList（动漫）、TVmaze（部分电视内容）、Open Library/Google Books/Wikidata/Wikipedia/Gutendex（书目）。来源评分、年份和可读定义不完全一致，不能直接当作同平台评分。本服务提供书目发现与用户导入文件阅读。
