# 知更 · Android App

> **2026-10-21 · Android 0.6.8 覆盖升级**：使用用户提供的蓝色知更鸟与书本图标，固定原包名 `cn.zhigeng.app`、版本码 608。已移除独立预览构建和发布入口。升级包必须沿用 0.6.6 的原证书，并通过旧版覆盖安装及数据保留检查后交付；未签名构建产物不作为安装包。

**安卓安装包：[Zhigeng-v0.6.6.apk](releases/Zhigeng-v0.6.6.apk?raw=true)**

原生安卓应用，支持 Android 8.0 及以上。发现、榜单、阅读、书架、我的五个页面；28项国内编辑推荐、TMDB 国内评分榜、Z-Library 站内链接与下载、书架、EPUB/TXT/PDF 内置阅读。主要功能使用安卓原生界面；AI 助手包含 Duck.ai 官方网页和原生 FCC 网关聊天。

安卓源码与构建说明见 [android/README.md](android/README.md)。最终 APK 已完成私有发布签名校验，沿用0.5版签名，可覆盖安装。模拟器已通过原有五页、Duck.ai 网页加载、FCC 目录、HTTPS 鉴权、增量回复、HTTP 429 与重试、停止、草稿恢复和加密保存检查，无崩溃。FCC 回复使用标注清楚的本地协议测试服务；真实网关对话及真实图书账号下载尚未测试。签名密钥单独保存，不提交公开仓库。

## Android 0.6.6 · 最新聊天合并版

最终 0.6.6 构建、单元测试、Android 29 阅读器和原生功能回归均通过；阅读检查覆盖 EPUB / TXT / GB18030 / PDF、目录、书签、字号、夜间模式和重启定位。模拟器截图确认封面已实际显示，个别原站仍有 DNS / 超时限制。详情见 [发布校验记录](releases/Zhigeng-v0.6.6.json) 、[实际阅读截图](design/zhigeng-v0.6.6-reader-actual.png) 与 [实际封面截图](design/zhigeng-v0.6.6-covers-actual.png)。

本次更新将网站最近的功能完整合入独立安卓 App：

- **节点导航**：代理 IP 五家、机场五家分开，额外保留你提供的控制台网址。第五家代理 IPRoyal 仍明确标记为前一轮候选，未冒充已核实最终名单。
- **AI 会员**：截图指定的 GamsGo、AI订阅价格雷达、特惠购、FamilyPro、银河录像局五个主推荐，另列环球巴士、Relay Radar 两个备选。与节点独立，支持搜索、AI 产品/交付方式筛选、本地收藏、目录刷新。
- **国内年度榜**：内地/香港/台湾，国内评分10票起、全球50票起，热度不限票数，最多25页；无 TMDB 密钥可查看电影/剧集公开年度目录，明确标注无评分、无排名，不将条目索引当评分榜。
- **真实封面**：同步最新28部国内精选图片和备用来源；发现、榜单、详情显示来源封面，支持 HTTPS 跳转、备用图、网络短暂失败后自动重试与手动重试。图片仍受原站网络与反盗链限制。
- **阅读器**：Readium Kotlin 3.1.0、离线中文宋体、米色/白色/夜间主题、目录、多书签、字号和间距设置；TXT 本地 UTF-8/GB18030 适配，PDF 原生阅读。修正 WebView 分栏正文绘制缺块。
- 保留 Z-Library 应用内链接与下载、六站接码、kanju.ai、Duck.ai 和原生 FCC 聊天；新服务统一从顶部「⋯ 更多工具」或「我的」进入。

旧版 EPUB/TXT 的像素进度与旧书签无法精确迁移，升级后首次需从目录定位一次；旧数据保留，PDF 页码兼容。第三方网站的付款、账号登录、真实播放和真实图书账号下载未实际完成，目录里的支付和核实程度按原始资料保留。

[Figma 节点 / AI会员](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=30-226) · [两屏设计预览](design/zhigeng-v0.6.6-directories.png) · [年度榜 / 看剧 / 接码设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=26-201) · [阅读器设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=20-134)

[Figma 国内发现 / 榜单 / 下载设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=6-653) · [设计预览](design/zhigeng-v0.6-preview.png)

AI 入口在主界面右上角「✦ AI 助手」。原生助手首页包含三个可复制的问题模板，点击「开始聊天」进入内嵌 Duck.ai。提供刷新、返回和连接失败重试，不跳外部浏览器。知更不会自动发送聊天、书架或账号数据；模型和用量以官方页面为准。

[助手 Figma 设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=13-41) · [助手设计预览](design/zhigeng-v0.6.1-ai.png)

FCC 入口在「AI 助手 → FCC 多模型」。填写自己的 HTTPS FCC 网关地址与 Proxy Token 后，可读取实际模型列表、切换模型并在知更内增量聊天。支持停止、复制回复、失败重试；草稿在切换模型、切换标签页与重新打开应用后恢复。连接、对话与草稿加密保存在设备，对话及缓存按网关和 Token 分开保存。61 家服务商为目录快照，不代表全部已开通或免费；需要用户在 FCC 管理端配置对应账号。

0.6.3 优化首次聊天空态、可读模型名称和流式滚动：发送后收起欢迎卡，阅读上文时暂停跟随，点击状态文字回到最新回复。

[FCC Figma 设计](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=16-2126) · [FCC 设计预览](design/zhigeng-v0.6.2-fcc.png)

下载入口在「阅读 → Z-Library 搜索 → 图书详情 → 获取下载链接 / 下载到书架」。下载权限与链接有效性以源站账号返回为准；失败或取消不把临时文件放入书架。

---


## AI 会员代充 / 共享 / 比价（最新截图指定，2026-10-20）

入口：https://nmm26301-maker.github.io/zhi-geng/#memberships

**AI 会员名单严格按照用户截图的 5 个主推荐 + 2 个备选，不是节点机场网站。** **节点导航没有删除。** 手机底部导航和电脑版侧栏同时保留「节点导航 5+5」（`/#nodes`）与「AI会员」（`/#memberships`）两个独立入口，前者保留原有两组节点及额外控制台链接，后者独立列出 AI 会员平台。手机导航项较多时可左右滑动。

主推荐按截图顺序：
1. **GamsGo** — https://www.gamsgo.com/ — 多 AI / 软件 / 影音会员，需分辨代充与共享商品。
2. **AI订阅价格雷达** — https://airadar.vip/ — ChatGPT、Claude、Gemini 等公开报价的比价导航（不直接代充）。
3. **特惠购** — https://tehuio.com/ — ChatGPT、Claude、Google AI 等自有账号充值、卡密与成品号；其帮助中心列出微信及支付宝，但没有做实际付款验证。
4. **FamilyPro** — https://familypro.io/ — 共享/合租账号，也可能有个别代充商品；共享账号有聊天隐私风险。
5. **银河录像局** — https://nf.video/ — 偏影音会员，兼有部分 AI 代充或镜像服务；镜像不等于官方 Plus 订阅。

截图注明的备选：
- **环球巴士** — https://universalbus.cn/ — 官方页面发布过部分商品下架、暂停新单公告，购买前必须确认。
- **Relay Radar** — https://aicodex.chat/subscribe — 多来源 AI 订阅报价比价渠道，不是具体代充商家。

支持站内实时筛选（按 AI 产品、交付方式、网站名称）、本地收藏、刷新网站目录；网站外链都在用户主动点击后才打开。仅展示七个选定平台，不内嵌登录/购买页，也不代收款、不读取共享账号、卡密或支付信息。价格及微信/支付宝渠道均以当前商品页面、实际结算和售后为准，未做下单验证。

数据文件：\`data/ai-memberships.json\`（schema v2，5+2），模块：\`assets/ai-memberships.js\`，自动测试：\`tests/ai-memberships.test.mjs\`。浏览器强制使用版本号加载以减少 GitHub Pages 缓存不更新的情况。安卓独立 APK 不随网页发布自动更新。


## 国内年度榜单扩展与真实封面（2026-10-20）

- **实际原因已修复**：此前年度查询返回 `image` 或 `poster_path`，但 `assets/zhigeng-v064.js` 的卡片仅渲染彩色文字占位，完全没有 `img` 元素。现在 TMDB 电影／电视剧／综艺、AniList 动画、Open Library 图书的合法 HTTPS 来源图片均直接显示（懒加载、等比例填充）；失败、空链接或不可信主机则保留清晰的类型背景与标题，不出现破图。
- **扩宽国内候选池**：默认年度筛选为「内地＋香港＋台湾」，TMDB `with_origin_country=CN|HK|TW` 而非原来的 `CN` 单独范围。国内评分榜门槛由 50 票调整至 10 票，并明确提醒小样本偏差；「热度」排序**不设置最低投票数**，以包含刚上映但尚未评分的国内新作，最高可翻 25 页（每页最多 20 条）。返回行严格核验作品的上映/首播年份，排名仅用于 TMDB 返回的真实排序，不杜撰分数。注意不同 TMDB 作品的制作地区可能存在缺失或混乱，不能保证是所有国产作品全集。
- **编辑精选图片补全**：28 部人工精选作品此前 `image` 字段都是空值，新引入 `assets/cover-enrichment.js`，以一次多作品批量请求查询中文维基百科的自由许可页面图片，且仅取作品标题完全一致的配图。限制为 `upload.wikimedia.org` 图片，不匹配、不存在或受网络限制时仍保留知更艺术文字占位。百科配图不等同于正版电影海报，不能保证所有作品都能补到图片。
- **无 TMDB 密钥时的补充**：国产电影与电视剧新增 `assets/public-annual-catalog.js`，尝试使用维基百科对应年份的公开作品列表（通过 MediaWiki API）列出作品条目，每页 20 条，并链接回原始百科条目；卡片**不显示假评分、不显示“NO.”名次**。不是评分榜，也不保证有全部作品或所有年份的可用页面。维基百科可能在某些网络环境无法访问，此时界面明确提示。综艺仍需 TMDB 访问凭证。
- **使用前提**：TMDB 的电影／电视剧／综艺实时年度排序继续需要用户自行在设置中验证 TMDB 访问凭证。AniList 国产动漫和 Open Library 的中文出版书目无需 TMDB 密钥，但「有中文版图书」不等同于中国原创；没有任何来源能够保证覆盖每年所有作品。年度真实排行、人工编辑精选与不同来源的评分各自区分。
- 回归测试：`tests/annual-rankings.test.mjs` 验证内地港澳台来源、年份和无投票热度筛选；`tests/cover-enrichment.test.mjs` 验证来源海报生成、图片加载失败以及百科图片的严格域名／标题对应。URL 在 `index.html` 增加独立版本号避免旧浏览器脚本缓存。

## 网页版 · 2026-10-19 节点服务目录（**最新聊天替换版**）

入口：`https://nmm26301-maker.github.io/zhi-geng/#nodes`。原接码、看剧AI、年度榜单功能保持不变。

**本次操作是覆盖原名单，不是追加。** 上一版未经确认的 Webshare、SOAX、Oxylabs 等自选结果已替换；低价机场原有「一分机场、一元机场、三毛机场、赔钱机场」等不再作为最新筛选的五家。按照用户最新的「重新筛选」「支持支付宝／微信」聊天，分成严格 5 + 5：

- **专业静态/动态代理 IP：** Decodo（`https://decodo.cn/`）、NextProxy（`https://www.nextproxy.com/`）、IPFast（`https://ipfast.net/`）、LycheeIP（`https://www.lycheeip.com/`）、IPRoyal（`https://iproyal.com/`，**暂用更早一轮提到的候选占位，原最新聊天第五家名字暂无法恢复**）。四家最新确认名称不应与第五家暂用候选混为一谈。
- **低价机场订阅：** 星岛梦（`https://kfccbb.xingdaomeng.com/`）、FreeCat（`https://us.freecat.cloud/register`，用户原链接）、九云（`https://jiuyundl.com/`）、宝云（`https://888by.baoyundl.com/`）、锦云（`https://jybdw1.wanhlj.com/`）。机场 URL 中的部分站点来自第三方目录/推广跳转，不保证等于持久官方主域名；注册付款前应再次确认。
- **额外独立控制台**：`https://xn--4gqx1hgtfdmt.com/#/dashboard`，是用户另外明确提供的网址；单独放在两组之后，不归入机场五家，不计入十个不同服务商。

最新聊天中提及的参考月付：星岛梦 ¥25/150GB，FreeCat ¥12/100GB，九云 ¥6/150GB，宝云 ¥4/200GB，锦云 ¥6/50GB；实际套餐以官网结算页为准。最新聊天提到的支付方式：Decodo 亚洲地区支付宝、微信未核实；NextProxy、IPFast、LycheeIP 官网列支付宝和微信；机场星岛梦、九云、宝云、锦云的双支付信息来自第三方资料，FreeCat 支付宝被提及、微信未核实。这些都**未做过实际付款验证**。

UI 显示分组、价格、支付方式及核实程度；保留站内搜索、收藏，第三方网页通过 HTTPS 新标签打开，不代理视频/订阅/账号/支付数据。没有独立实测 ISP/ASN、IP 信誉、AI 服务解锁或长期在线率，也不保证适合 ChatGPT、Gemini、Claude。低价机场可能频繁变更域名、运营主体或套餐；优先短期小额试用。

代码：`data/node-sites.json`（schema v3）、`assets/node-directory.js`、`assets/zhigeng-v064.css`；回归测试：`tests/node-directory.test.mjs`。

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

### 2026-10-20 节点列表旧版本缓存与发布排队修复

节点数据使用 `rev=20261020-live4` 加时间戳、`fetch(..., {cache:'no-store'})` 绕过浏览器缓存；检测 `schemaVersion=3` 与最新 5+5 服务 ID，发现旧目录会提示错误而不冒充已完成替换。UI 新增「刷新最新名单」。HTML 引用的新 JS/CSS 带版本号。Pages 发布工作流 `cancel-in-progress: true` 防止连续文件提交产生大量过时的并行／排队发布，最终以最新主分支为准。若 Pages 正在排队，仍要等待 GitHub 的部署实际成功，链接加 `?r=20261020-live4#nodes` 只能绕缓存，不能取代尚未发布的文件。

## 2026-10-20 国内精选 28 部作品海报逐一补全

用户截图显示「国内精选」《我不是药神》《霸王别姬》《活着》等仍是文字色块，真实根因是此前 `data/domestic.json` 里 **28 个 `image` 字段全部为空**；仅修复了前端卡片渲染，却没有提供可加载的内容。现通过公开媒体资料、TMDB、图书资料来源为 **全部 28 个独立作品**（6 电影、6 电视剧、4 动漫、4 综艺、8 图书）添加了明确的 HTTPS 海报/书封 URL，同时记录图片地址作为来源说明，不随意把同名电影/小说混用。5 部作品配置第二个备用来源。版本字段为 `coverUpdatedAt=2026-10-20`。

`assets/zhigeng-v064.js` 使用经逐一核对的原图域名白名单，加载成功后替换文本色块；失败则尝试已配置的备用图，全部失败保持可读标题和编辑信息；缓存图片也可立即显示。国内精选 JSON 现使用 `?rev=20261020-curated28` 并以 `cache:no-store` 请求，HTML 同步更新了脚本缓存版本。回归测试会断言 28 条图片地址均存在、28 张图片 DOM 元素实际渲染、电影/小说《活着》来源不同，及备用图与最终退回文字卡的功能。

图片仍由对应第三方图片服务器提供，可能存在反盗链、地区限制、权限或链接变更；**仓库有 28 条图片数据，不等于已经在所有安卓浏览器中确认加载成功**。没有对版权海报进行本地抓取或重新分发。

