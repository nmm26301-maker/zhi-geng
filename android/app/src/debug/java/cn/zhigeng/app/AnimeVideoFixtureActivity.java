package cn.zhigeng.app;
public final class AnimeVideoFixtureActivity extends AnimeVideoLibraryActivity {
    @Override protected AnimeCatalogClient createCatalogClient()throws Exception{return new AnimeCatalogClient("https://10.0.2.2:8446/graphql");}
}
