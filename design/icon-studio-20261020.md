# 知更 · Icon Studio 2026.10

[Figma 可编辑的 18 款矢量图标组件](https://www.figma.com/design/U1wIxILbo1lK05eGDY9eo8?node-id=33-256)

## 视觉方向

- **自然且有辨识度**：主品牌为原创知更鸟图案，朱砂红胸羽与森林绿背景，保留微小星光。
- **克制、精致、轻未来感**：辅助图标全部为圆角线性 24×24、1.85 单位描边；不用表情符号、Unicode 方框替代图形。
- **网站与安卓一致**：网站使用内联 SVG，安卓使用 VectorDrawable XML，无需外部图标字体。
- **延续品牌配色**：Forest `#244E40`、Paper `#F7F5EF`、Vermilion `#BB614E`、Sage `#E9EEE7`。
- **交互**：选中态提高描边权重与背景对比；移动端图标容器不小于 44dp 的交互目标，保留语义标签与屏幕阅读器提示；遵循 reduced-motion。

## 资源映射

| 场景 | 网站资源 | Android 资源 |
|---|---|---|
| 品牌图标 | `assets/zhigeng-mark.svg` | `app/src/main/res/drawable/ic_launcher.xml` |
| 底部导航 | `assets/icon-system.js` / `.css` | `zg_ic_compass`, `zg_ic_rankings`, `zg_ic_book`, `zg_ic_library`, `zg_ic_user` |
| 分类与工具 | `assets/icon-system.js` | `zg_ic_movie`, `zg_ic_tv`, `zg_ic_anime`, `zg_ic_variety`, `zg_ic_bot`, `zg_ic_more`, `zg_ic_grid` |

Android 资源路径以上均相对 `android/`。

## 实际覆盖范围

网站：侧栏主导航、分类导航、品牌栏、搜索、AI / 节点 / 阅读等手机导航，以及设置、收藏、首页状态图标。

Android 原生版：桌面启动图标、首页品牌标识、主导航五项、发现与榜单类别、AI 助手及工具按钮。

## 校验现状

已检查网站 icon-system JavaScript 可通过语法解析；Android MainActivity 的 12 个新矢量 drawable 引用均有对应 XML 文件；Figma 图标样板截图已人工检查。**尚未完成真实 Android SDK/Gradle 构建、安装机型截图或签名 APK 交付**。不能将源码更新描述为可直接安装的新 APK；发布前需沿用已有发布签名，并验证点击跳转、选中态、暗色/亮色与 Android 8+ 图标兼容性。

## 回退

删除 `index.html` 中 `icon-system.css` 与 `icon-system.js` 两处引用，可恢复原网站字符图标。安卓端使用 Git 历史还原 `MainActivity.java`、`ic_launcher.xml` 即可恢复旧版视觉，不涉及用户数据迁移。
