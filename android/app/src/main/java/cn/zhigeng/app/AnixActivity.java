package cn.zhigeng.app;

import android.os.Bundle;
import android.widget.Button;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;

/** Official Anix entry points; the upstream Linux application is not bundled. */
public class AnixActivity extends ReadingToolActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen("Anix");
        hero("ANIX  /  官方项目","动漫工具，\n从这里进入。","v1.3.0 · Linux 桌面版 · MIT 开源");
        add(body,text("官方 v1.3.0 提供 Linux AppImage，没有 Android 安装包。本页提供官方入口；在手机打开官网不会启动桌面播放器。",16,INK),24);
        entry("进入 Anix 官网 ↗","https://anix-linux.vercel.app",true);
        entry("查看 v1.3.0 官方发布 ↗","https://github.com/MaidLover69/Anix/releases/tag/1.3",false);
        entry("社区扩展使用说明 ↗","https://github.com/MaidLover69/Anix#extensions",false);
        add(body,text("Anix 可在 Linux 上管理追番列表、查看播出日程和播放本地动漫。社区来源通过其扩展设置添加，内容可用性取决于所选来源。",15,INK),24);
        add(body,text("所有官网与说明页面均使用手机外部浏览器打开。",14,INK),16);
    }
    private void entry(String label,String url,boolean primary) {
        Button action=button(label,primary);add(body,action,16);
        int ripple=((primary?Color.WHITE:GREEN)&0x00ffffff)|0x33000000;
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),shape(primary?GREEN:PALE,18),null));
        action.setOnClickListener(v->BrowserLinks.open(this,url));
    }
}
