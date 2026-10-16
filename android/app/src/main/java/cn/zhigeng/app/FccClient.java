package cn.zhigeng.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

/** Independent client of FCC's public Messages and model-list APIs; no provider keys. */
public final class FccClient {
    public interface Listener { void text(String total); }
    public static final class Reply { public final String text,model,reason;Reply(String t,String m,String r){text=t;model=m;reason=r;} }
    private final String base,token;private volatile HttpURLConnection active;private volatile boolean cancelled;
    public FccClient(String endpoint,String credential){base=FccPolicy.base(endpoint);token=FccPolicy.token(credential);}
    public void cancel(){cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();}
    private void check(){if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException("已停止");}
    private HttpURLConnection connect(String path,String method) throws Exception {
        check();HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();active=c;c.setRequestMethod(method);c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(180000);
        c.setRequestProperty("Accept","application/json, text/event-stream");c.setRequestProperty("anthropic-version","2023-06-01");if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);check();return c;
    }
    private void status(HttpURLConnection c) throws Exception {check();int code=c.getResponseCode();check();if(code!=200)throw new IOException(FccPolicy.status(code));}
    public JSONArray models() throws Exception {
        HttpURLConnection c=connect("/models?view=messages","GET");try{status(c);return parseModels(read(c.getInputStream(),4_000_000));}finally{c.disconnect();active=null;}
    }
    public static JSONArray parseModels(String raw) throws Exception {
        JSONObject root=new JSONObject(raw);JSONArray data=root.optJSONArray("data");if(data==null)throw new IOException("网关没有返回 FCC 模型目录，请检查是否填成了网页地址。");JSONArray result=new JSONArray();java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(int i=0;i<data.length();i++){JSONObject m=data.optJSONObject(i);if(m==null)continue;String id=m.optString("id","").trim();if(id.isEmpty()||id.length()>512||!ids.add(id))continue;JSONArray modes=m.optJSONArray("inputModalities");if(modes!=null){boolean text=false;for(int j=0;j<modes.length();j++)if("text".equals(modes.optString(j)))text=true;if(!text)continue;}
            result.put(new JSONObject().put("id",id).put("name",m.optString("display_name",id)).put("ref",m.optString("provider_model_ref",id)));}
        if(result.length()==0)throw new IOException("网关未列出可用文字模型，请先在 FCC 管理端配置服务商。");return result;
    }
    public static JSONObject request(String model,JSONArray context,String prompt) throws Exception {
        if(model==null||model.trim().isEmpty())throw new IOException("请先选择模型或填写模型 ID。");if(prompt==null||prompt.trim().isEmpty())throw new IOException("请输入消息。");if(prompt.length()>6000)throw new IOException("单条消息最多 6000 字。");
        JSONArray messages=new JSONArray();int total=prompt.length(),start=Math.max(0,context.length()-24);if(start%2!=0)start++;
        while(start<context.length()){int count=0;for(int i=start;i<context.length();i++)count+=context.getJSONObject(i).optString("content").length();if(count+total<=64000)break;start+=2;}
        for(int i=start;i<context.length();i++){JSONObject row=context.getJSONObject(i);String role=row.optString("role");if(!role.equals("user")&&!role.equals("assistant"))throw new IOException("对话记录格式错误，请新建对话。");messages.put(new JSONObject().put("role",role).put("content",row.optString("content")));}
        messages.put(new JSONObject().put("role","user").put("content",prompt));return new JSONObject().put("model",model).put("max_tokens",2048).put("stream",true).put("messages",messages);
    }
    public Reply chat(String model,JSONArray context,String prompt,Listener listener) throws Exception {
        JSONObject body=request(model,context,prompt);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);HttpURLConnection c=connect("/messages","POST");
        try{c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){check();out.write(bytes);}status(c);String type=c.getContentType();
            if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream"))return stream(c.getInputStream(),model,listener);
            JSONObject json=new JSONObject(read(c.getInputStream(),4_000_000));if(json.has("error"))throw new IOException("网关返回模型错误，请检查该模型的配置或切换模型。");StringBuilder text=new StringBuilder();JSONArray content=json.optJSONArray("content");if(content!=null)for(int i=0;i<content.length();i++){JSONObject block=content.optJSONObject(i);if(block!=null&&"text".equals(block.optString("type")))text.append(block.optString("text"));}
            if(text.length()==0)throw new IOException("模型没有返回文字，请选择支持文字聊天的模型。");listener.text(text.toString());return new Reply(text.toString(),json.optString("model",model),json.optString("stop_reason","end_turn"));
        }finally{c.disconnect();active=null;}
    }
    Reply stream(InputStream input,String model,Listener listener) throws Exception {
        StringBuilder text=new StringBuilder(),data=new StringBuilder();String[] metadata={model,""};boolean[] done={false};int[] seen={0};
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new Limited(input,8_000_000),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){check();if(line.isEmpty()){event(data,text,metadata,done,seen,listener);data.setLength(0);if(done[0])break;}else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(line.startsWith("data: ")?6:5));}}if(!done[0]&&data.length()>0)event(data,text,metadata,done,seen,listener);}
        check();if(!done[0])throw new IOException("连接中断，回复尚未完成；可检查网络后重试。");if(text.length()==0)throw new IOException("模型未返回文字；可切换模型后重试。");return new Reply(text.toString(),metadata[0],metadata[1]);
    }
    private void event(StringBuilder data,StringBuilder text,String[] metadata,boolean[] done,int[] seen,Listener listener) throws Exception {
        if(data.length()==0)return;String raw=data.toString();if(raw.equals("[DONE]")){done[0]=true;return;}JSONObject e=new JSONObject(raw);String type=e.optString("type");
        if(type.equals("error"))throw new IOException("模型生成失败，请在 FCC 管理端检查该服务商配置或切换模型。");
        if(type.equals("message_start")){JSONObject m=e.optJSONObject("message");if(m!=null)metadata[0]=m.optString("model",metadata[0]);}
        if(type.equals("content_block_start")){JSONObject b=e.optJSONObject("content_block");if(b!=null&&"text".equals(b.optString("type")))text.append(b.optString("text"));}
        if(type.equals("content_block_delta")){JSONObject d=e.optJSONObject("delta");if(d!=null&&"text_delta".equals(d.optString("type")))text.append(d.optString("text"));}
        if(type.equals("message_delta")){JSONObject d=e.optJSONObject("delta");if(d!=null)metadata[1]=d.optString("stop_reason",metadata[1]);}
        if(type.equals("message_stop"))done[0]=true;if(text.length()>100000)throw new IOException("回复过长，请缩短问题后重试。");if(text.length()!=seen[0]){seen[0]=text.length();listener.text(text.toString());}
    }
    private String read(InputStream input,int limit) throws Exception {try(InputStream in=new Limited(input,limit);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){check();out.write(b,0,n);}check();return out.toString("UTF-8");}}
    private static final class Limited extends FilterInputStream {private final int max;private int size;Limited(InputStream in,int max){super(in);this.max=max;}private void count(int n)throws IOException{if(n>0&&(size+=n)>max)throw new IOException("网关响应过大，请检查接口地址。");}public int read()throws IOException{int n=super.read();if(n>=0)count(1);return n;}public int read(byte[]b,int o,int l)throws IOException{int n=in.read(b,o,l);count(n);return n;}}
}
