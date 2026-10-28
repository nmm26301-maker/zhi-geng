package cn.zhigeng.app;

import android.content.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** User-installed Seanime manifests and compiled JS. Original files and linked episodes are retained. */
class AnimePluginRegistry {
    private final Context app;private final SharedPreferences prefs;private final SecretStore secrets;private final File folder;private final AnimeNet net;private static final Object LOCK=new Object();
    AnimePluginRegistry(Context c){this(c,new AnimeNet());}
    AnimePluginRegistry(Context c,AnimeNet net){app=c.getApplicationContext();prefs=app.getSharedPreferences("anime_plugins_v1",Context.MODE_PRIVATE);secrets=new SecretStore(app);folder=new File(app.getFilesDir(),"anime-plugins");this.net=net;}
    JSONArray installed()throws Exception{synchronized(LOCK){return new JSONArray(prefs.getString("installed","[]"));}}
    static String digest(String text)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    static JSONObject manifest(JSONObject raw,String base)throws Exception {
        if(!raw.optString("type").equals("onlinestream-provider"))throw new Exception("只支持 onlinestream-provider 扩展");
        String id=raw.getString("id"),name=raw.optString("name",id);if(!id.matches("[A-Za-z0-9_-]{1,64}")||name.length()>100)throw new Exception("扩展编号或名称无效");
        JSONObject result=new JSONObject(raw.toString());result.put("name",name);
        String payload=raw.optString("payloadURI");if(payload.isEmpty())throw new Exception("扩展没有 payloadURI 脚本地址");
        URI uri=new URI(base).resolve(payload);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new Exception("扩展脚本需要 HTTPS 地址");result.put("payloadURI",uri.toASCIIString()).put("source",base);
        return result;
    }
    JSONArray index(String url)throws Exception {
        URI uri=new URI(url);String host=uri.getHost();
        if(host!=null&&(host.equals("drive.google.com")||host.equals("docs.google.com"))){String id="";java.util.regex.Matcher m=java.util.regex.Pattern.compile("/file/d/([A-Za-z0-9_-]+)").matcher(uri.getPath());if(m.find())id=m.group(1);if(id.isEmpty()&&uri.getQuery()!=null)for(String pair:uri.getQuery().split("&"))if(pair.startsWith("id="))id=pair.substring(3);if(!id.matches("[A-Za-z0-9_-]{1,200}"))throw new Exception("无法识别公开扩展仓库链接");url="https://drive.usercontent.google.com/download?id="+id+"&export=download";}
        AnimeNet.Reply r=net.request(url,"GET",new JSONObject(),null,false);if(r.status!=200)throw new Exception("扩展仓库读取失败（HTTP "+r.status+"）");if(r.bytes.length>2*1024*1024)throw new Exception("仓库清单超过 2 MB");
        Object value=new JSONTokener(r.text().replaceFirst("^\\uFEFF","")).nextValue();JSONArray entries;
        if(value instanceof JSONArray)entries=(JSONArray)value;else if(value instanceof JSONObject){JSONObject object=(JSONObject)value;entries=object.optJSONArray("plugins");if(entries==null)entries=new JSONArray().put(object);}else throw new Exception("链接返回的不是扩展 JSON 清单");
        if(entries.length()>200)throw new Exception("每个仓库最多读取200个扩展");JSONArray result=new JSONArray();for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry!=null&&entry.optString("type").equals("onlinestream-provider"))result.put(manifest(entry,r.url));}return result;
    }
    JSONObject install(JSONObject manifest)throws Exception {
        AnimeNet.Reply r=net.request(manifest.getString("payloadURI"),"GET",new JSONObject(),null,false);if(r.status!=200||r.bytes.length>2*1024*1024)throw new Exception("扩展脚本读取失败或超过2 MB");String source=r.text(),hash=digest(source),expected=manifest.optString("sha256");if(!expected.isEmpty()&&!expected.equalsIgnoreCase(hash))throw new Exception("扩展脚本校验值不匹配");
        return installSource(manifest,source,hash);
    }
    JSONObject installLocal(String filename,String source)throws Exception {
        String hash=digest(source),id="local_"+hash.substring(0,20);JSONObject meta=new JSONObject().put("id",id).put("name",filename.length()>100?filename.substring(0,100):filename).put("version","local").put("type","onlinestream-provider").put("source","本地文件");return installSource(meta,source,hash);
    }
    private JSONObject installSource(JSONObject manifest,String source,String hash)throws Exception {
        String compiled=AnimePluginCompiler.compile(app,source),id=manifest.getString("id");if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new Exception("扩展编号无效");JSONObject saved=new JSONObject(manifest.toString()).put("hash",hash).put("file",id+"-"+hash+".js").put("enabled",true);
        synchronized(LOCK){if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("扩展目录暂不可写");File target=new File(folder,saved.getString("file"));File temporary=File.createTempFile("compiled-",".tmp",folder);try{try(FileOutputStream out=new FileOutputStream(temporary)){out.write(compiled.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temporary.renameTo(target)&&!target.isFile())throw new IOException("扩展脚本未能保存");JSONArray installed=installed(),updated=new JSONArray();boolean replaced=false;for(int i=0;i<installed.length();i++){JSONObject old=installed.getJSONObject(i);if(old.getString("id").equals(id)){updated.put(saved);replaced=true;}else updated.put(old);}if(!replaced){if(installed.length()>=50)throw new Exception("最多安装50个扩展");updated.put(saved);}if(!prefs.edit().putString("installed",updated.toString()).commit())throw new Exception("扩展设置未能保存");return saved;}finally{temporary.delete();}}
    }
    JSONObject preferences(String id)throws Exception{String raw=secrets.get("plugin.config."+id);return raw.isEmpty()?new JSONObject():new JSONObject(raw);}
    void preferences(String id,String raw)throws Exception{if(raw.length()>16384)throw new Exception("扩展设置过大");JSONObject values=new JSONObject(raw);Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();if(!(values.get(key) instanceof String))throw new Exception("扩展设置的值需要是字符串");}secrets.put("plugin.config."+id,values.toString());}
    void change(String id,boolean remove,boolean enabled)throws Exception {
        synchronized(LOCK){JSONArray updated=new JSONArray(),all=installed();for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(row.getString("id").equals(id)){if(!remove)updated.put(row.put("enabled",enabled));}else updated.put(row);}if(!prefs.edit().putString("installed",updated.toString()).commit())throw new Exception("扩展设置未能保存");if(remove)secrets.put("plugin.config."+id,"");}
    }
    AnimePluginEngine engine(JSONObject manifest)throws Exception {
        if(!manifest.optBoolean("enabled"))throw new Exception("此扩展已停用");String filename=manifest.getString("file");if(!filename.matches("[A-Za-z0-9_-]+-[a-f0-9]{64}\\.js"))throw new Exception("扩展文件名不可用");File file=new File(folder,filename);if(file.length()>4*1024*1024)throw new Exception("编译后的扩展过大");try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] part=new byte[4096];int n;while((n=in.read(part))!=-1)out.write(part,0,n);return new AnimePluginEngine(app,out.toString("UTF-8"),preferences(manifest.getString("id")));}
    }
}
