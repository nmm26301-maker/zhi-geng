package cn.zhigeng.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Routes website buttons to full external browsers, including verified app-link hosts. */
public final class BrowserLinks {
    private BrowserLinks() {}

    public static boolean open(Context context, String raw) {
        String url = BrowserPolicy.webUrl(raw);
        if (url.isEmpty()) return unavailable(context, "网址暂不可用");
        PackageManager manager = context.getPackageManager();
        // Resolve a neutral, non-existent host so a site's installed app cannot intercept it.
        Intent probe = webIntent("https://zhigeng-browser.invalid/");
        Set<String> browsers = new LinkedHashSet<>();
        for (ResolveInfo result : manager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (result.activityInfo != null && result.activityInfo.exported
                    && !context.getPackageName().equals(result.activityInfo.packageName)) {
                browsers.add(result.activityInfo.packageName);
            }
        }
        if (browsers.isEmpty()) return unavailable(context, "未找到外部浏览器，请先安装或启用手机浏览器");
        ResolveInfo preferred = manager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        String selected = preferred == null || preferred.activityInfo == null
                ? null : preferred.activityInfo.packageName;
        if (browsers.contains(selected)) {
            if (launch(context, webIntent(url).setPackage(selected))) return true;
            browsers.remove(selected);
        }
        ArrayList<Intent> choices = new ArrayList<>();
        for (String name : browsers) choices.add(webIntent(url).setPackage(name));
        if (choices.isEmpty()) return unavailable(context, "浏览器暂时无法打开，请检查是否已启用");
        Intent target = choices.remove(0);
        if (!choices.isEmpty()) {
            target = Intent.createChooser(target, "选择外部浏览器")
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, choices.toArray(new Intent[0]));
        }
        if (launch(context, target)) return true;
        return unavailable(context, "浏览器暂时无法打开，请检查是否已启用");
    }

    private static Intent webIntent(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private static boolean launch(Context context, Intent intent) {
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }

    private static boolean unavailable(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        return false;
    }
}
