package cn.zhigeng.app;

/** Debug-only route to original localhost fixtures. Absent from the release APK. */
public class AnixTestActivity extends AnixActivity {
    @Override protected AnimeCatalogClient createClient()throws Exception {
        return new AnimeCatalogClient("https://10.0.2.2:8445");
    }
}
