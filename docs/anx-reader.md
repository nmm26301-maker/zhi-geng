# 安读 Anx Reader 集成

知更 0.6.20 将 Anx Reader 的 foliate-js 阅读内核作为本地 Android WebView 模块集成，保留知更原有的包名 `cn.zhigeng.app`、正式签名、书架和阅读数据。

## 支持范围

- MOBI、AZW3、FB2：由安读模块直接解析和阅读，支持目录、翻页、夜间模式、字号排版和阅读位置恢复。
- EPUB、TXT：继续使用知更现有的 Readium 阅读器。
- PDF：继续使用知更现有的 PdfRenderer 页面。
- 书籍文件只从本机 `content://` 或 `file://` URI 读取，安读模块不上传图书。

## 构建和许可

Android 构建入口仍是 `android` 目录的 Gradle 工程。安读前端资源位于 `android/app/src/main/assets/anx/foliate-js`，使用 Anx Reader 的 foliate-js fork 生成的离线 bundle。许可文本随包保存在 `anx-reader-MIT.txt`、`anx/foliate-js/LICENSE-foliate-js.txt` 和 bundle 附带的许可证文件。

安读模块使用 `appassets.androidplatform.net` 本地地址，WebView 只拦截并读取 APK 资源和当前书籍 URI，不访问远程图书地址。
