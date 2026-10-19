# 知更 · Android App

**安卓安装包：[Zhigeng-v0.6.3.apk](releases/Zhigeng-v0.6.3.apk?raw=true)**

原生安卓应用，支持 Android 8.0 及以上。发现、榜单、阅读、书架、我的五个页面；28项国内编辑推荐、TMDB 国内评分榜、Z-Library 站内链接与下载、书架、EPUB/TXT/PDF 内置阅读。主要功能使用安卓原生界面；AI 助手包含 Duck.ai 官方网页和原生 FCC 网关聊天。

安卓源码与构建说明见 [android/README.md](android/README.md)。最终 APK 已完成私有发布签名校验，沿用0.5版签名，可覆盖安装。模拟器已通过原有五页、Duck.ai 网页加载、FCC 目录、HTTPS 鉴权、增量回复、HTTP 429 与重试、停止、草稿恢复和加密保存检查，无崩溃。FCC 回复使用标注清楚的本地协议测试服务；真实网关对话及真实图书账号下载尚未测试。签名密钥单独保存，不提交公开仓库。

[Figma 国内发现 / 榜单 / 下载设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=6-653) · [设计预览](design/zhigeng-v0.6-preview.png)

AI 入口在主界面右上角「✦ AI 助手」。原生助手首页包含三个可复制的问题模板，点击「开始聊天」进入内嵌 Duck.ai。提供刷新、返回和连接失败重试，不跳外部浏览器。知更不会自动发送聊天、书架或账号数据；模型和用量以官方页面为准。

[助手 Figma 设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=13-41) · [助手设计预览](design/zhigeng-v0.6.1-ai.png)

FCC 入口在「AI 助手 → FCC 多模型」。填写自己的 HTTPS FCC 网关地址与 Proxy Token 后，可读取实际模型列表、切换模型并在知更内增量聊天。支持停止、复制回复、失败重试；草稿在切换模型、切换标签页与重新打开应用后恢复。连接、对话与草稿加密保存在设备，对话及缓存按网关和 Token 分开保存。61 家服务商为目录快照，不代表全部已开通或免费；需要用户在 FCC 管理端配置对应账号。

0.6.3 优化首次聊天空态、可读模型名称和流式滚动：发送后收起欢迎卡，阅读上文时暂停跟随，点击状态文字回到最新回复。

[FCC Figma 设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=16-2126) · [FCC 设计预览](design/zhigeng-v0.6.2-fcc.png)

下载入口在「阅读 → Z-Library 搜索 → 图书详情 → 获取下载链接 / 下载到书架」。下载权限与链接有效性以源站账号返回为准；失败或取消不把临时文件放入书架。

---

## 网页版 · 2026-10-19 接码导航整合

- **复用此前做好的接码网站**：从仓库原有 `sms-chinese-hub` 分支取回最终「访问原站」版本 `sms-hub/visit-only-20261019.html`，保存到 `main` 分支的 `sms-hub/index.html`。完整地址：`https://nmm26301-maker.github.io/zhi-geng/sms-hub/`。
- 知更侧栏新增「接码导航」，手机底部新增「接码」，页面路径为 `/#sms`。站点采用知更原生「纸白／松绿」界面，支持网站名称／域名搜索、收藏、隐藏以及已隐藏恢复。
- 保留之前严格筛选的 **6 个** 原站：`freephonenum.com`、`smstome.com`、`receive-smss.com`、`temporary-phone-number.com`、`temp-number.com`、`receive-sms-free.cc`。此前排除的其他网站不会自动加回。2026-10-19 重新确认各首页可访问，但没有发送实际验证短信或承诺任意号码能够收到验证码。
- **不提供「中文浏览」、网页翻译或跨域采集**；点击「访问原站」打开外部原始页面，站点内容及语言由原站决定，避免第三方拒绝 iframe 时出现空白网页。
- 原站点导航使用的 `sms-nav-fav-1` 与 `sms-nav-hide-1` 本地存储键保持一致；数据只在同一浏览器、同一网站来源下共享，不从此前的 `raw.githack.com` 浏览器存储自动迁移。知更不会读取、收集或存储第三方短信/验证码。
- 共享号码的收件箱对其他访问者公开，**不适合重要账户、银行、付款、密码找回或敏感身份验证**。
- 测试：`tests/sms-hub.test.mjs`；页面脚本：`assets/sms-hub.js`；样式：`assets/zhigeng-v064.css`。

## 网页版 · 2026-10-19 看剧AI（kanju.ai）

- 在桌面侧栏、安卓手机浏览器底部导航新增 **「看剧 / 看剧AI」** 独立页面：`#kanju`。
- 使用官方永久域名 **https://kanju.ai/**；只在用户主动点击后尝试 iframe 载入，提供在新标签打开官方页面的备用通道。kanju.ai 可能自动分配其他访问域名，或限制跨域 iframe，因此**不能保证第三方界面一定能嵌入/登录/播放**。不会通过代理、绕过反嵌入策略或抓取视频实现伪内嵌。
- 支持手动输入片名，在点击操作时使用官网目前可识别的 `/session/new?prompt=` 搜索路由；如该路由失效，可通过官网入口自行搜索。电影、电视剧、动漫、综艺的国内精选、年度榜单和常规发现卡片都增加「看剧AI 找片」快捷入口。**点击快捷入口只预填片名，不自动搜索，不上传收藏历史。**
- 登录、观看、版权与播放线路完全由 kanju.ai 管理；知更不转存其账号、Cookie、视频、历史、下载链接，也不将本地书架、TMDB 凭证或 AI 聊天数据发送给它。
- 源码：`assets/kanju-bridge.js`，样式：`assets/zhigeng-v064.css`，页面接入：`assets/zhigeng-v064.js` 和 `index.html`，测试：`tests/kanju-bridge.test.mjs`。

## 网页版 · 2026-10-19 年度排行榜

网页「榜单 → 每年排行榜」现提供从 **1900 年到当前年份** 的选择器、作品类别（电影、电视剧、动漫、综艺、书籍）、地区和排序依据。切换年份会重新请求对应的数据，而非更换标题。

- **电影／电视剧／综艺**：TMDB `discover/movie` / `discover/tv` 按该年上映／首播日期范围、来源地、评分人数至少 50 票和平均分或热度排序，支持逐页查看更多；需要在设置里填写并验证个人 TMDB 凭证，未配置时明确说明，不编造评分。
- **动漫**：AniList GraphQL 使用 `seasonYear`、`countryOfOrigin` 查询该年动漫，按平均分或关注度排序；无需 TMDB 密钥。AniList 关注人数与评分人数不是同一指标，页面单独标明。
- **书籍**：Open Library `first_publish_year` 按首次出版年份筛选；中文范围仅代表目录存在中文语言版本，不等于中国作者／中国原创；评分排行榜要求至少 10 人评价，热度按读者书架关注度排序。即时 API 访问受限时，全球范围、第一页可回退至仓库现有 `data/books.json` 对应年份的历史目录样本，清晰标明样本边界。
- **年份范围不代表所有年份都有数据**：缺数据、超时、上游限流或 TMDB 密钥缺失都会显示实际原因；2026 年等未结束年份标为「进行中」。不同平台分数不混在同一个排行榜里。
- 保留「知更编辑精选」和「TMDB 综合评分榜」两个独立入口。所有年度卡片与现有收藏共用浏览器本地片单。
- 文件：`assets/annual-rankings.js`；前端交互：`assets/zhigeng-v064.js`；测试：`tests/annual-rankings.test.mjs`。可运行 `npm ci && npm test`。

## 网页版 · 2026-10-19 同步至安卓 0.6.4 功能入口

- **GitHub Pages 静态版：** https://nmm26301-maker.github.io/zhi-geng/ （推送 `main` 后通过仓库 Pages 工作流发布）。
- **独立 Node 完整版：** https://zhigeng-reading.savoryvine10.chatgpt.site （需要托管平台重新拉取本仓库更新后才能同步本次网页代码；GitHub 推送不代表第三方托管已自动更新）。
- 首页新增「国内精选」，直接读取与安卓一致的 `data/domestic.json` 28 条编辑推荐，包含电影、剧集、国创动画、综艺、中文图书；**不编造评分、热度或当作实时排行**。
- 独立「榜单」页：知更编辑推荐榜及 TMDB 中国来源用户评分榜。TMDB 需先在设置里验证自有 API Key / Access Token；评分榜过滤票数不足 50 的作品；榜单检索失败就明确报告，不伪装为实时结果。
- 独立「AI 助手」：Duck.ai 官方网页提供站内尝试加载与官方新标签兜底；若官方禁止 iframe 内嵌，遵守浏览器限制、不绕过安全策略。FCC 多模型网页版直接请求用户填写的 HTTPS 网关，读取网关实际模型、发送文本消息、解析流式回复、支持停止与错误提示；**网关必须支持 CORS**，Proxy Token、对话只存当前网页内存，不会被写入仓库、localStorage 或主动读取书架。
- 「阅读」沿用网页版已有 EPUB/TXT/PDF 本地文件导入、书架、进度与书签；增加沉浸工具栏切换及网页阅读外壳的纸白、米色、夜间配色。**网页版使用的阅读引擎与安卓 Readium 不同**，原书章节重排、字形和 PDF 操作不能视作完全同等能力。
- 浏览器收藏仍在 `zhigeng_saved_v1`，国内精选与榜单的收藏可在原来的「我的片单」继续查看。
- Z-Library 的账号搜索仍需同源 Node 后端 `/api/gateway`；GitHub Pages 静态版只能展示公开图书目录，**无法进行服务端账号登录/下载**。网页端没有伪装已实现安卓的下载校验流程。
- 回归测试：`node --test tests/web-v064.test.mjs`，另有 GitHub Actions `web-check.yml`。

## 附：网页版本与后端 v0.4

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
