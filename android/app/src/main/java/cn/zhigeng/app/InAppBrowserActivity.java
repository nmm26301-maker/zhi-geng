package cn.zhigeng.app;

import android.app.Activity;
import android.os.Bundle;

/** Compatibility entry for restored old tasks; all websites now leave for the browser. */
public class InAppBrowserActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BrowserLinks.open(this, getIntent().getStringExtra("url"));
        finish();
    }
}
