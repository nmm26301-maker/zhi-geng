package cn.zhigeng.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Opens an original metadata fixture without an AniList account or external service. */
public final class AnimeVideoTestActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);startActivity(new Intent(this,AnimeVideoFixtureActivity.class).putExtra("anime","{\"id\":7,\"episodes\":12,\"title\":{\"userPreferred\":\"Original video fixture\"}}"));finish();}
}
