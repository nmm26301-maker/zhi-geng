package cn.zhigeng.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(247,247,242),INK=Color.rgb(35,52,43),MUTED=Color.rgb(122,134,123),GREEN=Color.rgb(71,118,93),PALE=Color.rgb(230,238,225),LINE=Color.rgb(222,228,217);
    private final ExecutorService jobs=Executors.newFixedThreadPool(3),images=Executors.newFixedThreadPool(3);
    private final LruCache<String,Bitmap> covers=new LruCache<>(12*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private SecretStore secrets;private Catalog catalog;private android.content.SharedPreferences prefs;
    private LinearLayout root,body,nav;private ScrollView scroll;private EditText search;private TextView status;private Button more;
    private Catalog.Query query=new Catalog.Query();private final ArrayList<JSONObject> visible=new ArrayList<>();private int generation=0;private String page="discover";private boolean busy=false;
    private static final int IMPORT=101;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);secrets=new SecretStore(this);catalog=new Catalog(this,secrets);prefs=getSharedPreferences("library",MODE_PRIVATE);if(state!=null){page=state.getString("page","discover");query.type=state.getString("type","all");query.text=state.getString("query","");query.provider=state.getString("provider","public");}render();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private TextView heading(String value,int size){TextView t=label(value,size,INK);t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String value,boolean strong){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(strong?Color.WHITE:GREEN);b.setBackground(background(strong?GREEN:PALE,14));b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(14),dp(7),dp(14),dp(7));return b;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setSingleLine();e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackground(background(Color.WHITE,14));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private void render(){
        generation++;busy=false;root=col();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=row();header.setPadding(dp(22),dp(15),dp(22),dp(10));TextView brand=heading("知更",28);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView small=label("ZHIGENG  /  0.5",10,MUTED);header.addView(small);root.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(true);body=col();body.setPadding(dp(20),dp(4),dp(20),dp(22));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setPadding(dp(10),dp(9),dp(10),dp(10));String[] titles={"◉ 发现","▤ 阅读","♡ 书架","⚙ 设置"},keys={"discover","books","shelf","settings"};
        for(int i=0;i<keys.length;i++){final String key=keys[i];Button b=button(titles[i],page.equals(key));b.setTextSize(13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);b.setOnClickListener(v->{if(page.equals(key))return;page=key;query=new Catalog.Query();if(key.equals("books"))query.type="book";render();});}root.addView(nav);
        switch(page){case "settings":settings();break;case "shelf":shelf();break;default:discover();}
    }
    private void discover(){
        TextView overline=label(page.equals("books")?"READING ROOM  ·  让阅读有处可去":"YOUR CULTURE COMPASS",10,GREEN);overline.setLetterSpacing(.12f);add(body,overline,14);
        add(body,heading(page.equals("books")?"下一本，读什么？":"好作品，总会遇见。",27),10);
        add(body,label(page.equals("books")?"中文与外文书目 · 你的私人书架":"电影、剧集、新番与书籍，慢慢找到喜欢的。",13,MUTED),8);
        if(!page.equals("books")){
            HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();String[] names={"精选","电影","电视剧","动漫","综艺","书籍"},types={"all","movie","tv","anime","variety","book"};
            for(int i=0;i<names.length;i++){final String type=types[i];Button b=button(names[i],query.type.equals(type));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(7);chips.addView(b,lp);b.setOnClickListener(v->{query.type=type;query.page=1;query.year="all";render();});}h.addView(chips);add(body,h,20);
        }
        if(query.type.equals("book")){
            LinearLayout source=row();Spinner provider=spinner(new String[]{"公开图书目录","Z-Library 账号搜索"});provider.setSelection(query.provider.equals("zlib")?1:0);source.addView(provider,new LinearLayout.LayoutParams(0,dp(46),1));Button connect=button(secrets.get("zkey").isEmpty()?"连接账号":"账号已连接",false);connect.setOnClickListener(v->{page="settings";render();});source.addView(connect);add(body,source,17);
            provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int pos,long id){String value=pos==1?"zlib":"public";if(!query.provider.equals(value)){query.provider=value;query.page=1;load(false);}}});
        }
        LinearLayout searchRow=row();search=input(query.type.equals("book")?"搜索书名、小说或作者":"搜索作品名称",false);search.setText(query.text);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(50),1));Button go=button("搜索",true);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(68),dp(50));gp.leftMargin=dp(8);searchRow.addView(go,gp);go.setOnClickListener(v->submit());search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){submit();return true;}return false;});add(body,searchRow,15);
        LinearLayout tools=row();Button filters=button("筛选 · "+(query.year.equals("all")?"全部年份":query.year),false);tools.addView(filters,new LinearLayout.LayoutParams(0,dp(42),1));Button refresh=button("↻ 更新",false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(95),dp(42));rp.leftMargin=dp(8);tools.addView(refresh,rp);filters.setOnClickListener(v->filterDialog());refresh.setOnClickListener(v->{query.page=1;load(false);});add(body,tools,10);
        status=label("正在寻找好作品…",12,MUTED);status.setLineSpacing(dp(3),1);add(body,status,18);
        load(false);
    }
    private void submit(){query.text=search.getText().toString().trim();query.year="all";query.page=1;load(false);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);return s;}
    private void filterDialog(){
        LinearLayout box=col();box.setPadding(dp(20),dp(10),dp(20),0);add(box,label("年份",13,MUTED),0);ArrayList<String> years=new ArrayList<>();years.add("全部年份");for(int y=java.time.Year.now().getValue()+1;y>=1950;y--)years.add(String.valueOf(y));Spinner year=spinner(years.toArray(new String[0]));year.setSelection(query.year.equals("all")?0:Math.max(0,years.indexOf(query.year)));add(box,year,0);
        Spinner lang=spinner(new String[]{"全部语言","中文","英语","日语"});String[] languages={"","chi","eng","jpn"};lang.setSelection(Arrays.asList(languages).indexOf(query.language));
        Spinner format=spinner(new String[]{"全部格式","EPUB","PDF","TXT","MOBI","AZW3","DJVU","FB2"});String[] formats={"","epub","pdf","txt","mobi","azw3","djvu","fb2"};format.setSelection(Math.max(0,Arrays.asList(formats).indexOf(query.extension)));
        EditText from=input("出版年份起（可空）",false),to=input("出版年份止（可空）",false);from.setInputType(InputType.TYPE_CLASS_NUMBER);to.setInputType(InputType.TYPE_CLASS_NUMBER);from.setText(query.from);to.setText(query.to);CheckBox exact=new CheckBox(this);exact.setText("精确短语搜索");exact.setChecked(query.exact);
        if(query.type.equals("book")){add(box,label("图书语言",13,MUTED),6);add(box,lang,0);if(query.provider.equals("zlib")){add(box,label("文件格式",13,MUTED),6);add(box,format,0);}add(box,from,7);add(box,to,7);add(box,exact,5);}
        Spinner sort=spinner(new String[]{"热门优先","口碑优先"});sort.setSelection(query.sort.equals("rating")?1:0);if(!query.type.equals("book")){add(box,label("排序",13,MUTED),6);add(box,sort,0);}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("筛选作品").setView(box).setNegativeButton("取消",null).setNeutralButton("清除",(a,b)->{query.year="all";query.from="";query.to="";query.language="";query.extension="";query.exact=false;query.page=1;load(false);}).setPositiveButton("应用",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(view->{String lo=from.getText().toString().trim(),hi=to.getText().toString().trim();if(!validYear(lo)||!validYear(hi)||!lo.isEmpty()&&!hi.isEmpty()&&lo.compareTo(hi)>0){toast("请填写合理的年份范围");return;}query.year=year.getSelectedItemPosition()==0?"all":years.get(year.getSelectedItemPosition());query.from=lo;query.to=hi;query.language=languages[lang.getSelectedItemPosition()];query.extension=formats[format.getSelectedItemPosition()];query.exact=exact.isChecked();query.sort=sort.getSelectedItemPosition()==1?"rating":"popular";query.page=1;d.dismiss();load(false);}));d.show();
    }
    private boolean validYear(String value){return value.isEmpty()||value.matches("[12][0-9]{3}");}
    private GridLayout grid;
    private void load(boolean append){
        if(status==null||isFinishing())return;final int token=++generation;Catalog.Query selected=query.copy();busy=true;if(!append){visible.clear();if(grid!=null&&grid.getParent()==body)body.removeView(grid);if(more!=null&&more.getParent()==body)body.removeView(more);}
        status.setText("正在搜索 · "+(selected.provider.equals("zlib")&&selected.type.equals("book")?"Z-Library":"公开数据源"));if(more!=null)more.setEnabled(false);
        jobs.execute(()->{Catalog.Result result;try{result=catalog.search(selected);}catch(Exception e){result=new Catalog.Result();result.note=e.getMessage()==null?"连接失败，请检查网络":e.getMessage();}final Catalog.Result got=result;
            runOnUiThread(()->{if(isFinishing()||token!=generation)return;busy=false;visible.addAll(got.items);renderResults(got.more);status.setText(visible.size()+" 部作品"+(got.note.isEmpty()?"":" · "+got.note)+(visible.isEmpty()&&got.note.isEmpty()?"\n没有匹配结果，试试更换书名或清除筛选。":""));});
        });
    }
    private void renderResults(boolean hasMore){
        if(grid!=null&&grid.getParent()==body)body.removeView(grid);if(more!=null&&more.getParent()==body)body.removeView(more);grid=new GridLayout(this);grid.setColumnCount(2);
        for(JSONObject item:visible){LinearLayout card=col();card.setBackground(background(Color.WHITE,15));card.setPadding(dp(10),dp(10),dp(10),dp(12));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(4),dp(6),dp(4),dp(6));card.setLayoutParams(lp);
            FrameLayout art=new FrameLayout(this);art.setBackground(background(PALE,10));TextView fallback=heading(item.optString("title"),17);fallback.setGravity(Gravity.CENTER);fallback.setPadding(dp(10),dp(8),dp(10),dp(8));art.addView(fallback,new FrameLayout.LayoutParams(-1,-1));ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(poster,new FrameLayout.LayoutParams(-1,-1));card.addView(art,new LinearLayout.LayoutParams(-1,dp(185)));loadCover(item.optString("image"),poster);
            TextView title=heading(item.optString("title"),15);title.setMaxLines(2);title.setMinHeight(dp(38));add(card,title,9);TextView meta=label(item.optString("year")+"  "+item.optString("author"),11,MUTED);meta.setMaxLines(1);add(card,meta,4);TextView source=label(item.optString("source"),10,GREEN);source.setMaxLines(1);add(card,source,6);card.setOnClickListener(v->details(item));grid.addView(card);
        }
        add(body,grid,8);more=button("继续加载更多 ↓",false);more.setVisibility(hasMore?View.VISIBLE:View.GONE);more.setOnClickListener(v->{if(!busy){query.page++;load(true);}});add(body,more,14);
    }
    private void loadCover(String raw,ImageView target){
        final String url=Catalog.secureUrl(raw);if(url.isEmpty())return;target.setTag(url);Bitmap cached=covers.get(url);if(cached!=null){target.setImageBitmap(cached);return;}
        images.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>3_000_000)throw new IOException();out.write(b,0,n);}byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap!=null){covers.put(url,bitmap);runOnUiThread(()->{if(url.equals(target.getTag()))target.setImageBitmap(bitmap);});}}finally{c.disconnect();}}catch(Exception ignored){}});
    }
    private JSONObject saved(){try{return new JSONObject(prefs.getString("shelf","{}"));}catch(Exception e){return new JSONObject();}}
    private void save(JSONObject value){if(!prefs.edit().putString("shelf",value.toString()).commit())toast("保存失败，请检查设备空间");}
    private void addToShelf(JSONObject item,String readingStatus){try{JSONObject s=saved();JSONObject copy=new JSONObject(item.toString());copy.put("readingStatus",readingStatus);s.put(copy.optString("id"),copy);save(s);toast("已加入书架 · "+readingStatus);}catch(Exception e){toast("保存失败");}}
    private void details(JSONObject item){
        LinearLayout content=col();content.setPadding(dp(22),dp(10),dp(22),dp(10));add(content,label(item.optString("author")+"  "+item.optString("year")+"\n"+item.optString("source"),13,GREEN),0);
        String summary=item.optString("summary");add(content,label(summary.isEmpty()?"暂无详细介绍，可打开来源书页了解更多。":summary,15,INK),14);
        LinearLayout actions=row();String[] statuses={"想读","在读","已读"};for(String value:statuses){Button b=button(item.optString("type").equals("book")?value:value.replace("读","看"),false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(2),dp(10),dp(2),0);actions.addView(b,lp);b.setOnClickListener(v->addToShelf(item,value));}add(content,actions,5);
        ScrollView sc=new ScrollView(this);sc.addView(content);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.optString("title")).setView(sc).setNegativeButton("关闭",null).setPositiveButton(item.has("localUri")?"开始阅读":"来源书页 ↗",(d,w)->{if(item.has("localUri"))readLocal(item);else open(item.optString("url"));}).create();dialog.show();
    }
    private void shelf(){
        add(body,label("PERSONAL LIBRARY",10,GREEN),15);add(body,heading("留一点时间，给喜欢。",26),10);add(body,label("想读、在读、已读，和你自己的电子书。",13,MUTED),8);Button imp=button("＋ 导入 TXT / EPUB / PDF",true);add(body,imp,20);imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","application/epub+zip","application/pdf"});startActivityForResult(i,IMPORT);});
        JSONObject s=saved();if(s.length()==0){add(body,label("书架还是空的。搜索到喜欢的作品后，点开详情收藏。",15,MUTED),30);return;}
        Iterator<String> keys=s.keys();while(keys.hasNext()){String key=keys.next();JSONObject item=s.optJSONObject(key);if(item==null)continue;LinearLayout card=col();card.setPadding(dp(16),dp(15),dp(16),dp(15));card.setBackground(background(Color.WHITE,15));add(card,heading(item.optString("title"),17),0);add(card,label(item.optString("readingStatus","想读")+" · "+item.optString("author")+" · "+(item.has("localUri")?"本地图书":item.optString("source")),12,GREEN),7);card.setOnClickListener(v->details(item));card.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("移出书架？").setMessage(item.optString("title")).setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{JSONObject entries=saved();entries.remove(key);save(entries);render();}).show();return true;});add(body,card,12);}
        add(body,label("长按作品可移出书架。本地文件仍由你自己保留。",11,MUTED),18);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name="电子书";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}
        String lower=name.toLowerCase(Locale.ROOT);if(!lower.endsWith(".txt")&&!lower.endsWith(".epub")&&!lower.endsWith(".pdf")){toast("请选择 TXT、EPUB 或 PDF 文件");return;}
        try{JSONObject item=new JSONObject().put("id","local:"+uri).put("title",name).put("type","book").put("author","").put("source","本地图书").put("localUri",uri.toString()).put("format",lower.endsWith(".pdf")?"pdf":lower.endsWith(".epub")?"epub":"txt");addToShelf(item,"想读");render();}catch(Exception e){toast("导入失败");}
    }
    private void readLocal(JSONObject item){
        Uri uri=Uri.parse(item.optString("localUri"));if(item.optString("format").equals("pdf")){try{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(ActivityNotFoundException e){toast("手机尚未安装 PDF 阅读器");}return;}
        Intent i=new Intent(this,ReaderActivity.class);i.putExtra("uri",uri.toString());i.putExtra("title",item.optString("title"));i.putExtra("format",item.optString("format"));startActivity(i);
    }
    private void settings(){
        add(body,label("MAKE IT YOURS",10,GREEN),15);add(body,heading("连接你的发现来源",26),10);add(body,label("安卓原生版 · 不需要打开网页\n账号会话以 Android Keystore 加密保存；不保存密码。",13,MUTED),10);
        add(body,heading("影视 · TMDB",19),25);EditText token=input("API Key 或 Read Access Token",true);token.setText(secrets.get("tmdb"));add(body,token,10);Button test=button("测试并保存 TMDB",true);add(body,test,10);TextView tmdb=label(secrets.get("tmdb").isEmpty()?"未配置，书籍与动漫可独立使用":"已保存凭证",12,MUTED);add(body,tmdb,8);
        test.setOnClickListener(v->{String value=token.getText().toString().trim();test.setEnabled(false);tmdb.setText("正在测试官方接口…");jobs.execute(()->{String message;try{catalog.verifyTmdb(value);message="连接成功，已加密保存";}catch(Exception e){message=e.getMessage();}String status=message;runOnUiThread(()->{test.setEnabled(true);tmdb.setText(status);});});});Button clear=button("清除 TMDB 凭证",false);add(body,clear,8);clear.setOnClickListener(v->{try{secrets.put("tmdb","");token.setText("");tmdb.setText("已清除");}catch(Exception e){toast("清除失败");}});
        add(body,heading("图书 · Z-Library",19),28);add(body,label("使用 heartleo/zlib 的登录与书目搜索适配。请选择你自己确认的账号源站。",12,MUTED),8);Spinner domain=spinner(Catalog.DOMAINS);int domainIndex=Arrays.asList(Catalog.DOMAINS).indexOf(secrets.get("zdomain"));domain.setSelection(Math.max(0,domainIndex));add(body,domain,8);EditText email=input("账号邮箱",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);EditText password=input("密码（仅用于这次登录）",true);add(body,email,8);add(body,password,8);Button connect=button("连接并加密保存会话",true);add(body,connect,10);TextView zs=label(secrets.get("zkey").isEmpty()?"未连接":"已连接，重开 App 可继续使用会话",12,MUTED);add(body,zs,8);
        connect.setOnClickListener(v->{String em=email.getText().toString().trim(),pw=password.getText().toString(),origin=domain.getSelectedItem().toString();if(em.isEmpty()||pw.isEmpty()){zs.setText("请填写账号和密码");return;}connect.setEnabled(false);zs.setText("正在连接来源站…");password.setText("");jobs.execute(()->{String message;try{catalog.login(origin,em,pw);message="连接成功，请去「阅读」选择 Z-Library 搜书";}catch(Exception e){message=e.getMessage();}String result=message;runOnUiThread(()->{connect.setEnabled(true);zs.setText(result);});});});
        Button disconnect=button("断开图书账号",false);add(body,disconnect,8);disconnect.setOnClickListener(v->{try{secrets.put("zid","");secrets.put("zkey","");secrets.put("zdomain","");zs.setText("已清除会话");}catch(Exception e){toast("清除失败");}});
        Button project=button("开源项目与许可 ↗",false);add(body,project,24);project.setOnClickListener(v->open("https://github.com/nmm26301-maker/zhi-geng"));add(body,label("知更 0.5.0 · Android 8.0+\nheartleo/zlib：MIT License\n书架和阅读记录在本设备；数据由各来源提供。",11,MUTED),14);
    }
    private void open(String raw){String url=Catalog.secureUrl(raw);if(url.isEmpty()){toast("来源链接暂不可用");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("无法打开链接");}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("page",page);state.putString("type",query.type);state.putString("query",query.text);state.putString("provider",query.provider);}
    @Override protected void onDestroy(){generation++;jobs.shutdownNow();images.shutdownNow();super.onDestroy();}
}
