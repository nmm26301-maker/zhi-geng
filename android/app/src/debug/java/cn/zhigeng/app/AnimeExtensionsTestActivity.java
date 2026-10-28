package cn.zhigeng.app;
import org.json.*;
import android.content.Context;
import java.io.*;
/** Same registry/compiler/player UI, local test network permission only. */
public final class AnimeExtensionsTestActivity extends AnimeExtensionsActivity {
    @Override public void onCreate(android.os.Bundle state){
        getIntent().putExtra("anime", "{\"id\":7,\"episodes\":12,\"title\":{\"userPreferred\":\"Original extension fixture\"}}");super.onCreate(state);
    }
    @Override protected AnimePluginRegistry createRegistry(){return new FixtureRegistry(this);}
    private static final class FixtureRegistry extends AnimePluginRegistry {
        private final Context app;
        FixtureRegistry(Context context){super(context,new Anime619FixtureNet());app=context;}
        @Override AnimePluginEngine engine(JSONObject manifest)throws Exception {
            String file=manifest.getString("file");if(!file.matches("[A-Za-z0-9_-]+-[a-f0-9]{64}\\.js"))throw new IOException("fixture file invalid");
            String source=new String(java.nio.file.Files.readAllBytes(new File(new File(app.getFilesDir(),"anime-plugins"),file).toPath()),java.nio.charset.StandardCharsets.UTF_8);
            String prelude,bootstrap;try(InputStream a=app.getAssets().open("anix-prelude.js");InputStream b=app.getAssets().open("anime-plugin-bootstrap.js")){prelude=new String(a.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);bootstrap=new String(b.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            return new AnimePluginEngine(prelude,bootstrap,source,preferences(manifest.getString("id")),new AnimePluginHost(new Anime619FixtureNet(),true));
        }
    }
}
