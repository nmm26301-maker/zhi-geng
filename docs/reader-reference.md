# 阅读页面参考与开源选型

用户希望现有知更 Android App 的阅读页面参考微信读书。此文记录选型；0.6.10 的阅读器尚未按此方案改版。

## 已核对的项目（2026-10-21）

| 项目 | 当前仓库说明 | 对知更的用途 |
| --- | --- | --- |
| [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) | BSD-3-Clause；Kotlin；提供 test-app、EPUB 分页/滚动、搜索与高亮能力 | 知更已经使用 3.1.0，保留引擎和已有本地进度结构 |
| [Anx Reader](https://github.com/Anxcye/anx-reader) | 当前 MIT；Flutter；README 说明 1.2.6 后由 GPL-3.0 改回 MIT；支持字体、字重、字/行/段间距、边距、主题及笔记 | 参考中文排版设置与笔记入口组织；使用任何代码时固定版本并保留对应许可 |
| [Readest](https://github.com/readest/readest) | AGPL-3.0；Next.js / Tauri；分页、滚动、高亮、笔记与排版主题 | 参考阅读体验和功能组织；当前不引入其源码 |
| [h7ml/weread](https://github.com/h7ml/weread) | 社区仿微信读书的 Fresh / Deno Web 项目；仓库已归档 | 仅作为阅读布局参考 |

腾讯公开的 [Tencent/WeChatReading](https://github.com/Tencent/WeChatReading) 是微信读书 Skills / Agent 接口，不能将其当作 Android 阅读客户端源码。

## 建议的实现方向

沿用知更包名、正式签名、书架、本地文件和 locator 存储；在当前 ReaderActivity 上迭代，不生成另一个应用。

- 阅读正文：弱化页眉页脚，扩大正文区，提供纸白、暖纸、深色配色。
- 中央点按：展示上下工具栏；底部集中目录、进度、字体/排版、主题，避免多层堆叠按钮。
- 排版：保留宋体/黑体、字号、行距、段距、边距、分页/滚动，增加清晰的实时预览与选中状态。
- 后续功能：在 Readium 已有能力上添加选中划线/笔记与全文搜索，分别实现和验证后再声称支持。
- 网站链接继续交给外部手机浏览器；本地图书继续在知更内阅读。

## 验收重点

检查中文 EPUB / UTF-8 与 GB18030 TXT、原书样式、长章节、夜间切换、分页/滚动、旋转/进程重启后的 locator 恢复。保留 PDF 原生阅读行为。每次升级使用原生产证书并验证旧数据保留。
