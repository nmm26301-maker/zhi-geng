package cn.zhigeng.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.jsoup.Jsoup;

/** Native BookOrbit library client, kept separate from the existing reading/search providers. */
public class BookOrbitActivity extends ReadingToolActivity {
    static final String SESSION="bookorbit_session_v1";
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private SecretStore secrets;
    private volatile BookOrbitClient client;
    private volatile int generation;
    private EditText server,username,password,search;
    private TextView status,pageLabel;
    private LinearLayout results;
    private Button previous,next;
    private JSONArray libraries=new JSONArray();
    private long libraryId;
    private int page;
    private String term="";
    private AtomicBoolean cancelled;
    private AlertDialog downloading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);secrets=new SecretStore(this);
        if(state!=null){term=state.getString("term","");page=state.getInt("page",0);libraryId=state.getLong("library",0);}
        try{client=BookOrbitClient.restore(secrets.get(SESSION));}catch(Exception ignored){}
        if(client==null)connection();else libraryScreen();
    }

    private boolean current(int request,BookOrbitClient owner) {
        return request==generation && owner==client && !isFinishing() && !isDestroyed();
    }
    private void persist(int request,BookOrbitClient owner) throws Exception {
        if(current(request,owner))secrets.put(SESSION,owner.session());
    }
    private void error(int request,BookOrbitClient owner,Exception e) {
        runOnUiThread(()->{if(current(request,owner))status.setText(message(e));});
    }
    private String message(Exception e) {
        if(e instanceof java.net.SocketTimeoutException)return "连接超时，请检查服务器并重试";
        if(e instanceof javax.net.ssl.SSLException)return "无法验证服务的 HTTPS 证书，请检查服务器证书";
        if(e instanceof java.io.IOException)return "无法连接 BookOrbit，请检查网络与服务地址";
        if(e instanceof JSONException || e instanceof ClassCastException)return "服务器响应与 BookOrbit v1 接口不兼容";
        return Objects.toString(e.getMessage(),"连接失败，请重试");
    }

    private void connection() {
        screen("BookOrbit");hero("BOOKORBIT  /  独立书库","你的书库，\n接着读。","连接自己的 BookOrbit 服务，在知更搜索、下载并离线阅读。");
        add(body,text("服务地址",13,GREEN),22);server=input("https://你的 BookOrbit 服务地址");
        server.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(getPreferences(MODE_PRIVATE).getString("last_server",""));add(body,server,8);
        username=input("BookOrbit 用户名");add(body,username,12);password=input("BookOrbit 密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);add(body,password,10);
        Button connect=button("连接书库",true);add(body,connect,14);status=text("",13,MUTED);add(body,status,12);
        connect.setOnClickListener(v->connect(false,null,connect));
        Button magic=button("使用 BookOrbit 登录链接",false);add(body,magic,10);
        magic.setOnClickListener(v->{EditText link=input("粘贴 BookOrbit 的 /magic?token= 登录链接");link.setSaveEnabled(false);
            new AlertDialog.Builder(this).setTitle("使用登录链接").setView(link).setNegativeButton("取消",null)
                .setPositiveButton("连接",(d,w)->connect(true,link.getText().toString(),connect)).show();});
        add(body,text("BookOrbit 是自托管书库，需要已有服务器与账号；这里不会自动部署服务器，也不会附带付费书源。密码仅用于本次登录，会话加密保存在本机。",12,MUTED),22);
        Button sample=button("体验官方示例书库",false);add(body,sample,12);
        sample.setOnClickListener(v->{server.setText("https://demo.bookorbit.app");connect(true,
            "https://demo.bookorbit.app/magic?token=2d92cb900e184cf0eb8b11f72cffc6011673d1016e1b300d750eb3d76abc1572",connect);});
        add(body,text("官方演示提供公共领域示例书；自己的图书需要连接自己的服务器。",12,MUTED),10);
        Button official=button("BookOrbit 官方项目与部署说明 ↗",false);add(body,official,14);
        official.setOnClickListener(v->BrowserLinks.open(this,"https://github.com/bookorbit/bookorbit"));
        Button demo=button("官方演示与使用说明 ↗",false);add(body,demo,10);
        demo.setOnClickListener(v->BrowserLinks.open(this,"https://bookorbit.app/"));
    }

    private void connect(boolean magic,String link,Button button) {
        String base=server.getText().toString(),name=username.getText().toString().trim(),pass=password.getText().toString();
        if(!magic&&(name.isEmpty()||pass.isEmpty())){status.setText("请填写用户名和密码，或使用登录链接");return;}
        if(name.length()>100||pass.length()>1024){status.setText("账号或密码过长");return;}
        final BookOrbitClient owner;
        try{owner=new BookOrbitClient(base);}catch(Exception e){status.setText(message(e));return;}
        client=owner;int request=++generation;button.setEnabled(false);status.setText("正在连接 BookOrbit…");password.setText("");
        jobs.execute(()->{try{
            if(magic)owner.magicLogin(link);else owner.login(name,pass);
            persist(request,owner);
            runOnUiThread(()->{if(!current(request,owner))return;
                getPreferences(MODE_PRIVATE).edit().putString("last_server",owner.base).apply();page=0;libraryId=0;term="";libraryScreen();});
        }catch(Exception e){runOnUiThread(()->{if(current(request,owner)){button.setEnabled(true);status.setText(message(e));}});}});
    }

    private void libraryScreen() {
        screen("BookOrbit");hero("BOOKORBIT  /  我的书库","下一本，\n就在这里。",client.base);
        LinearLayout actions=row();Button reload=button("刷新书库",false),disconnect=button("断开连接",false);
        actions.addView(reload,new LinearLayout.LayoutParams(0,-2,1));actions.addView(disconnect,new LinearLayout.LayoutParams(0,-2,1));add(body,actions,16);
        reload.setOnClickListener(v->loadLibraries());disconnect.setOnClickListener(v->disconnect());
        status=text("正在读取书库…",13,MUTED);add(body,status,14);
        search=input("搜索书名或作者（当前书库）");search.setText(term);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);add(body,search,12);
        Button find=button("搜索书库",true);find.setOnClickListener(v->submit());add(body,find,10);
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){submit();return true;}return false;});
        results=column();add(body,results,12);pageLabel=text("",12,MUTED);add(body,pageLabel,16);
        LinearLayout pages=row();previous=button("上一页",false);next=button("下一页",false);
        previous.setEnabled(false);next.setEnabled(false);pages.addView(previous,new LinearLayout.LayoutParams(0,-2,1));pages.addView(next,new LinearLayout.LayoutParams(0,-2,1));add(body,pages,8);
        previous.setOnClickListener(v->{if(page>0){page--;loadBooks();}});next.setOnClickListener(v->{page++;loadBooks();});
        add(body,text("EPUB / TXT / PDF 下载后可在知更阅读；其他书籍格式可保存，漫画及有声书可在服务器网页继续使用。下载同时沿用手机本地副本功能。",12,MUTED),18);
        loadLibraries();
    }

    private void submit() {
        String value=search.getText().toString().trim();if(value.length()>200){search.setError("搜索词请控制在200字以内");return;}
        term=value;page=0;loadBooks();
    }
    private void loadLibraries() {
        BookOrbitClient owner=client;int request=++generation;status.setText("正在读取书库…");
        jobs.execute(()->{try{JSONArray data=owner.libraries();persist(request,owner);
            runOnUiThread(()->{if(!current(request,owner))return;libraries=data;chooseLibrary();});
        }catch(Exception e){error(request,owner,e);}});
    }
    private void chooseLibrary() {
        ArrayList<JSONObject> visible=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<libraries.length();i++){
            JSONObject item=libraries.optJSONObject(i);if(item==null||item.optLong("id")<=0)continue;
            String type=item.optString("type","books");if(!type.equals("books"))continue;
            visible.add(item);names.add(item.optString("name","未命名书库"));
        }
        if(visible.isEmpty()){status.setText("当前账号没有可浏览的图书书库，请在服务器添加图书或授予权限。");results.removeAllViews();return;}
        int selected=0;for(int i=0;i<visible.size();i++)if(visible.get(i).optLong("id")==libraryId)selected=i;
        // Reuse the spinner across refreshes; do not leave stale selectors in the screen.
        View old=body.findViewWithTag("bookorbit_libraries");if(old!=null)body.removeView(old);
        Spinner spinner=new Spinner(this);spinner.setTag("bookorbit_libraries");spinner.setContentDescription("选择 BookOrbit 书库");
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));spinner.setSelection(selected);
        body.addView(spinner,3,new LinearLayout.LayoutParams(-1,dp(52)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                long nextId=visible.get(position).optLong("id");if(nextId!=libraryId){libraryId=nextId;page=0;}loadBooks();
            }
        });
    }

    private void loadBooks() {
        if(libraryId<=0){status.setText("请先连接并选择书库");return;}
        BookOrbitClient owner=client;int request=++generation;long library=libraryId;int requestedPage=page;String query=term;
        previous.setEnabled(false);next.setEnabled(false);results.removeAllViews();status.setText("正在搜索书库…");
        jobs.execute(()->{try{JSONObject data=owner.books(library,query,requestedPage);persist(request,owner);
            runOnUiThread(()->{if(current(request,owner))showBooks(data,requestedPage);});
        }catch(Exception e){error(request,owner,e);}});
    }
    private void showBooks(JSONObject data,int requestedPage) {
        JSONArray items=data.optJSONArray("items");int total=data.optInt("total",0);results.removeAllViews();
        if(items==null){status.setText("服务器未返回可识别的图书列表");return;}
        status.setText("找到 "+total+" 本书 · "+(term.isEmpty()?"当前书库":term));
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null||item.optLong("id")<=0)continue;
            LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(shape(0xffffffff,20));
            TextView title=text(item.isNull("title")?"未命名图书":item.optString("title","未命名图书"),19,INK);add(card,title,0);
            add(card,text(BookOrbitProtocol.author(item),13,MUTED),8);Button detail=button("查看图书与版本",false);add(card,detail,12);
            detail.setOnClickListener(v->detail(item.optLong("id")));add(results,card,12);
        }
        if(items.length()==0)add(results,text("没有匹配的书籍，试试更换关键词或书库。",15,MUTED),16);
        pageLabel.setText("第 "+(requestedPage+1)+" 页 · 每页20本");previous.setEnabled(requestedPage>0);
        next.setEnabled((long)(requestedPage+1)*20<total);
    }

    private void detail(long id) {
        BookOrbitClient owner=client;int request=++generation;status.setText("正在读取图书信息…");
        jobs.execute(()->{try{JSONObject book=owner.detail(id);persist(request,owner);
            runOnUiThread(()->{if(current(request,owner)){status.setText("图书信息已载入");showDetail(book);}});
        }catch(Exception e){error(request,owner,e);}});
    }
    private void showDetail(JSONObject book) {
        LinearLayout content=column();content.setPadding(dp(22),dp(14),dp(22),dp(22));
        add(content,text(BookOrbitProtocol.author(book),14,GREEN),0);
        String description=Jsoup.parse(book.optString("description","")).text();
        add(content,text(description.isEmpty()?"暂无简介":description,14,MUTED),12);
        JSONArray files=book.optJSONArray("files");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(book.optString("title","图书版本"))
            .setView(wrap(content)).setNegativeButton("关闭",null).create();
        int supported=0;
        if(files!=null)for(int i=0;i<files.length();i++){
            JSONObject file=files.optJSONObject(i);if(file==null||file.optLong("id")<=0)continue;
            String format=file.optString("format").toLowerCase(Locale.ROOT),role=file.optString("role");
            if(!BookOrbitProtocol.supported(format)||!(role.equals("primary")||role.equals("content")))continue;
            supported++;Button save=button("下载 "+format.toUpperCase(Locale.ROOT)+" · 保存到手机",true);add(content,save,14);
            save.setOnClickListener(v->{dialog.dismiss();download(book,file);});
        }
        if(supported==0)add(content,text("这本书没有当前支持的下载版本，可在服务器网页查看漫画或有声书。",13,MUTED),14);
        Button website=button("在浏览器中打开此书",false);add(content,website,14);
        website.setOnClickListener(v->BrowserLinks.open(this,client.base+"/books/"+book.optLong("id")));dialog.show();
    }
    private ScrollView wrap(LinearLayout view){ScrollView scroll=new ScrollView(this);scroll.addView(view);return scroll;}

    private void download(JSONObject book,JSONObject file) {
        BookOrbitClient owner=client;int request=++generation;AtomicBoolean stop=new AtomicBoolean(false);cancelled=stop;
        TextView progress=text("正在准备下载…",14,INK);progress.setPadding(dp(22),dp(18),dp(22),dp(18));
        downloading=new AlertDialog.Builder(this).setTitle(book.optString("title")).setView(progress).setCancelable(false)
            .setNegativeButton("取消",(d,w)->stop.set(true)).create();downloading.show();
        jobs.execute(()->{File downloaded=null;try{
            Map<String,String> headers=owner.downloadHeaders();persist(request,owner);
            downloaded=BookDownloader.downloadFromBookOrbit(this,owner.base,BookOrbitProtocol.fileUrl(owner.base,file.getLong("id")),
                file.optString("format"),headers,stop,(bytes,total)->runOnUiThread(()->{
                    if(current(request,owner))progress.setText(String.format(Locale.ROOT,"已下载 %.1f MB",bytes/1048576.0));}));
            JSONObject item=BookOrbitProtocol.shelfItem(owner.base,book,file,Uri.fromFile(downloaded).toString());File result=downloaded;
            runOnUiThread(()->{if(!current(request,owner)||stop.get()){result.delete();return;}
                downloading.dismiss();setResult(RESULT_OK,new Intent().putExtra("downloadedBook",item.toString()));finish();});
        }catch(Exception e){if(downloaded!=null)downloaded.delete();runOnUiThread(()->{if(current(request,owner)){
            downloading.dismiss();status.setText(stop.get()?"已取消下载，书架未添加文件":message(e));}});}});
    }
    private void disconnect() {
        BookOrbitClient old=client;++generation;
        try{secrets.put(SESSION,"");}catch(Exception e){status.setText("无法清除连接，请稍后重试");return;}
        client=null;libraryId=0;page=0;term="";connection();
        jobs.execute(()->{try{old.logout();}catch(Exception ignored){}});
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("term",search==null?term:search.getText().toString());state.putInt("page",page);state.putLong("library",libraryId);}
    @Override protected void onDestroy(){generation++;if(cancelled!=null)cancelled.set(true);if(downloading!=null)downloading.dismiss();jobs.shutdownNow();super.onDestroy();}
}
