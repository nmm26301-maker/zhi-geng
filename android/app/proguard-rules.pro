# The Readium web bridge must retain methods invoked by JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Navigator fragments use a publication-aware custom FragmentFactory.
-keep class org.readium.r2.navigator.epub.EpubNavigatorFragment { *; }
