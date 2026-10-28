package cn.zhigeng.app;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Only the explicit Seanime host operations are exposed to the JS sandbox. */
final class AnimePluginHost {
    final AnimeNet net;final boolean privateAllowed;final List<Document> docs=new ArrayList<>();final List<List<Node>> nodes=new ArrayList<>();int htmlBytes;
    volatile String result,error;volatile boolean done;long callId;
    AnimePluginHost(AnimeNet net,boolean privateAllowed){this.net=net;this.privateAllowed=privateAllowed;}
    static byte[] bytes(JSONArray a)throws Exception {if(a.length()>1024*1024)throw new Exception("扩展字节数据过大");byte[] b=new byte[a.length()];for(int i=0;i<b.length;i++)b[i]=(byte)a.getInt(i);return b;}
    static JSONArray array(byte[] b){JSONArray a=new JSONArray();for(byte x:b)a.put(x&255);return a;}
    String dispatch(String op,String raw)throws Exception {
        if(raw.length()>8*1024*1024)throw new Exception("扩展参数过大");JSONArray a=new JSONArray(raw);Object out;
        switch(op){
            case "log":out=JSONObject.NULL;break;
            case "done":if(a.getLong(0)==callId){if(a.getBoolean(1))result=a.getString(2);else error="扩展执行失败："+a.optString(2).substring(0,Math.min(500,a.optString(2).length()));done=true;}out=JSONObject.NULL;break;
            case "utf8Encode":out=array(a.getString(0).getBytes(StandardCharsets.UTF_8));break;
            case "utf8Decode":out=new String(bytes(a.getJSONArray(0)),StandardCharsets.UTF_8);break;
            case "b64Encode":out=Base64.getEncoder().encodeToString(bytes(a.getJSONArray(0)));break;
            case "b64Decode":out=array(Base64.getDecoder().decode(a.getString(0).replaceAll("\\s","").replace('-','+').replace('_','/')));break;
            case "digest":out=array(MessageDigest.getInstance(algorithm(a.getString(0))).digest(bytes(a.getJSONArray(1))));break;
            case "hmac":{String alg="Hmac"+algorithm(a.getString(0)).replace("-","");Mac mac=Mac.getInstance(alg);byte[] key=bytes(a.getJSONArray(1));if(key.length==0)key=new byte[]{0};mac.init(new SecretKeySpec(key,alg));out=array(mac.doFinal(bytes(a.getJSONArray(2))));break;}
            case "randomBytes":{int n=a.getInt(0);if(n<0||n>65536)throw new Exception("随机数据长度不可用");byte[] b=new byte[n];new SecureRandom().nextBytes(b);out=array(b);break;}
            case "aes":{boolean encrypt=a.getString(0).equals("encrypt"),padding=a.getBoolean(5);String mode=a.getString(1).toUpperCase(Locale.ROOT);if(!mode.equals("CBC")&&!mode.equals("ECB"))throw new Exception("不支持的加密模式");Cipher cipher=Cipher.getInstance("AES/"+mode+"/"+(padding?"PKCS5Padding":"NoPadding"));SecretKeySpec key=new SecretKeySpec(bytes(a.getJSONArray(2)),"AES");if(mode.equals("ECB"))cipher.init(encrypt?1:2,key);else cipher.init(encrypt?1:2,key,new IvParameterSpec(bytes(a.getJSONArray(3))));out=array(cipher.doFinal(bytes(a.getJSONArray(4))));break;}
            case "gunzip":{byte[] b=bytes(a.getJSONArray(0));InputStream compressed=new ByteArrayInputStream(b);if(b.length>=2&&(b[0]&255)==31&&(b[1]&255)==139)compressed=new GZIPInputStream(compressed);else compressed=new InflaterInputStream(compressed);try(InputStream in=compressed;ByteArrayOutputStream buffer=new ByteArrayOutputStream()){byte[] part=new byte[4096];int n;while((n=in.read(part))!=-1){if(buffer.size()+n>4*1024*1024)throw new Exception("扩展解压数据过大");buffer.write(part,0,n);}out=array(buffer.toByteArray());}break;}
            case "sleep":Thread.sleep(Math.min(1000,Math.max(0,a.optLong(0))));out=JSONObject.NULL;break;
            case "urlParse":{try{URI u=new URI(a.getString(0).replace(" ","%20"));if(!u.isAbsolute())u=new URI(a.getString(1)).resolve(u);out=parts(u).toString();}catch(Exception e){out=JSONObject.NULL;}break;}
            case "urlSet":out=setUrl(a.getString(0),a.getString(1),a.getString(2));break;
            case "docLoad":{String html=a.getString(0);htmlBytes+=html.length();if(docs.size()>=32||htmlBytes>4*1024*1024)throw new Exception("扩展 HTML 内容过大");Document d=Jsoup.parse(html);if(d.getAllElements().size()>20000)throw new Exception("扩展 HTML 节点过多");docs.add(d);nodes.add(new ArrayList<>());out=docs.size()-1;break;}
            case "docSelect":out=select(a);break;
            case "docText":out=read(a);break;
            case "scannerUtils":out=scanner(a.getString(0),new JSONArray(a.getString(1)));break;
            default:throw new Exception("扩展需要尚不支持的接口："+op);
        }
        return encode(out==null?JSONObject.NULL:out);
    }
    private static String encode(Object value){String text=new JSONArray().put(value).toString();return text.substring(1,text.length()-1);}
    private static String algorithm(String alg)throws Exception{switch(alg.toLowerCase(Locale.ROOT)){case "md5":return "MD5";case "sha1":return "SHA-1";case "sha256":return "SHA-256";case "sha512":return "SHA-512";default:throw new Exception("不支持的摘要算法");}}
    static JSONObject parts(URI u)throws Exception {
        String scheme=u.getScheme()==null?"":u.getScheme(),host=u.getHost()==null?"":u.getHost(),authority=host+(u.getPort()<0?"":":"+u.getPort());String[] user=(u.getRawUserInfo()==null?"":u.getRawUserInfo()).split(":",2);
        return new JSONObject().put("href",u.toASCIIString()).put("origin",host.isEmpty()?"null":scheme+"://"+authority).put("protocol",scheme+":").put("host",authority).put("hostname",host).put("port",u.getPort()<0?"":Integer.toString(u.getPort())).put("username",user[0]).put("password",user.length>1?user[1]:"").put("pathname",u.getRawPath()==null||u.getRawPath().isEmpty()?"/":u.getRawPath()).put("search",u.getRawQuery()==null?"":"?"+u.getRawQuery()).put("hash",u.getRawFragment()==null?"":"#"+u.getRawFragment());
    }
    private static Object setUrl(String url,String part,String value)throws Exception {
        try{URI u=new URI(url);JSONObject p=parts(u);if(part.equals("href"))return parts(new URI(value)).toString();
            switch(part){case "protocol":p.put("protocol",value.replace(":","")+":");break;case "host":p.put("host",value);break;case "hostname":p.put("host",value+(p.getString("port").isEmpty()?"":":"+p.getString("port")));break;case "port":p.put("host",p.getString("hostname")+(value.isEmpty()?"":":"+value));break;case "pathname":p.put("pathname",value.startsWith("/")?value:"/"+value);break;case "search":p.put("search",value.isEmpty()?"":value.startsWith("?")?value:"?"+value);break;case "hash":p.put("hash",value.isEmpty()?"":value.startsWith("#")?value:"#"+value);break;case "username":case "password":p.put(part,value);break;default:return JSONObject.NULL;}
            String user=p.getString("username"),pass=p.getString("password");return parts(new URI(p.getString("protocol")+"//"+(user.isEmpty()?"":user+(pass.isEmpty()?"":":"+pass)+"@")+p.getString("host")+p.getString("pathname")+p.getString("search")+p.getString("hash"))).toString();
        }catch(Exception e){return JSONObject.NULL;}
    }
    private static boolean matches(Node n,String selector){return n instanceof Element&&(selector.isEmpty()||((Element)n).is(selector));}
    private List<Node> inputs(int doc,JSONArray handles)throws Exception{ArrayList<Node> out=new ArrayList<>();if(doc<0||doc>=nodes.size())return out;for(int i=0;i<handles.length();i++){int h=handles.getInt(i);if(h>=0&&h<nodes.get(doc).size())out.add(nodes.get(doc).get(h));}return out;}
    private JSONArray select(JSONArray args)throws Exception {
        int d=args.getInt(0);String op=args.getString(1),s=args.optString(3);if(s.length()>512||s.contains(":matches"))throw new Exception("扩展 CSS 选择器过于复杂");LinkedHashSet<Node> out=new LinkedHashSet<>();List<Node> in=inputs(d,args.getJSONArray(2));if(d<0||d>=docs.size())return new JSONArray();
        if(op.equals("document"))out.add(docs.get(d));else if(op.equals("root"))out.addAll(docs.get(d).select(s));else for(Node n:in){
            switch(op){
                case "find":if(n instanceof Element)for(Element hit:((Element)n).select(s))if(hit!=n)out.add(hit);break;
                case "contents":out.addAll(n.childNodes());break;
                case "children":for(Node child:n.childNodes())if(matches(child,s))out.add(child);break;
                case "parent":if(matches(n.parentNode(),s))out.add(n.parentNode());break;
                case "closest":for(Node parent=n;parent!=null;parent=parent.parentNode())if(matches(parent,s)){out.add(parent);break;}break;
                case "parents":case "parentsUntil":for(Node parent=n.parentNode();parent!=null;parent=parent.parentNode()){if(op.equals("parentsUntil")&&matches(parent,s))break;if(matches(parent,op.equals("parents")?s:""))out.add(parent);}break;
                case "siblings":for(Node sib:n.siblingNodes())if(matches(sib,s))out.add(sib);break;
                case "next":case "prev":case "nextAll":case "prevAll":case "nextUntil":case "prevUntil":{
                    boolean forward=op.startsWith("next"),once=op.equals("next")||op.equals("prev"),until=op.endsWith("Until");
                    for(Node sib=forward?n.nextSibling():n.previousSibling();sib!=null;sib=forward?sib.nextSibling():sib.previousSibling()){
                        if(!(sib instanceof Element))continue;if(until&&matches(sib,s))break;if(matches(sib,until?"":s))out.add(sib);if(once)break;
                    }break;}
                case "filter":case "not":if(matches(n,s)==op.equals("filter"))out.add(n);break;
                case "has":if(n instanceof Element&&((Element)n).select(s).stream().anyMatch(e->e!=n))out.add(n);break;
                default:throw new Exception("未知 HTML 选择操作");
            }
        }
        JSONArray ids=new JSONArray();List<Node> all=nodes.get(d);for(Node n:out){int index=all.indexOf(n);if(index<0){if(all.size()>=30000)throw new Exception("扩展选择节点过多");all.add(n);index=all.size()-1;}ids.put(index);}return ids;
    }
    private Object read(JSONArray a)throws Exception {
        int d=a.getInt(0);String op=a.getString(1),arg=a.optString(3);List<Node> in=inputs(d,a.getJSONArray(2));Node n=in.isEmpty()?null:in.get(0);Element first=null;for(Node node:in)if(node instanceof Element){first=(Element)node;break;}
        switch(op){case "text":{StringBuilder text=new StringBuilder();for(Node node:in)text.append(node instanceof Element?((Element)node).wholeText():node instanceof TextNode?((TextNode)node).getWholeText():"");return text.toString();}
            case "html":return n instanceof Element?((Element)n).html():"";case "outerHtml":return n==null?"":n.outerHtml();case "attr":return first!=null&&first.hasAttr(arg)?first.attr(arg):JSONObject.NULL;
            case "attrs":{JSONObject attrs=new JSONObject();if(first!=null)for(Attribute attr:first.attributes())attrs.put(attr.getKey(),attr.getValue());return attrs.toString();}
            case "hasClass":for(Node node:in)if(node instanceof Element&&((Element)node).hasClass(arg))return "1";return "0";
            case "is":if(arg.length()>512||arg.contains(":matches"))throw new Exception("扩展选择器过于复杂");for(Node node:in)if(matches(node,arg))return "1";return "0";
            default:throw new Exception("未知 HTML 读取操作");}
    }
    private static String normalize(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private static Object scanner(String method,JSONArray a)throws Exception {
        String value=a.optString(0);if(value.length()>4000)throw new Exception("标题过长");String normalized=normalize(value);Object out;
        switch(method){case "normalizeTitle":case "sanitizeQuery":case "buildSearchQuery":case "buildAdvancedQuery":case "buildSeasonQuery":case "buildPartQuery":out=normalized;break;
            case "getSignificantTokens":out=new JSONArray(Arrays.asList(normalized.split(" +")));break;
            case "buildSmartSearchTitles":out=new JSONArray().put(value).put(normalized);break;
            case "extractYear":case "extractSeasonNumber":case "extractPartNumber":{java.util.regex.Matcher m=java.util.regex.Pattern.compile(method.equals("extractYear")?"\\b(19\\d{2}|20\\d{2})\\b":method.equals("extractSeasonNumber")?"(?i)(?:season\\s*|\\bs)(\\d+)":"(?i)(?:part\\s*|cour\\s*)(\\d+)").matcher(value);out=m.find()?Integer.valueOf(m.group(1)):JSONObject.NULL;break;}
            case "compareTitles":out=normalize(a.optString(1)).equals(normalized)?1.0:0.0;break;
            case "findBestMatch":throw new Exception("此扩展使用的标题匹配接口暂不兼容，请使用另一扩展");
            default:throw new Exception("不支持的标题辅助接口："+method);}
        return encode(out);
    }
    String fetch(String request,byte[] body)throws Exception {
        JSONObject r=new JSONObject(request);String method=r.optString("method","GET").toUpperCase(Locale.ROOT);if(!Arrays.asList("GET","HEAD","POST","PUT","DELETE","OPTIONS","PATCH").contains(method))throw new Exception("扩展请求方法不可用");
        JSONObject headers=new JSONObject();JSONArray pairs=r.optJSONArray("headers");if(pairs!=null)for(int i=0;i<pairs.length();i++){JSONArray pair=pairs.getJSONArray(i);headers.put(pair.getString(0),pair.getString(1));}
        AnimeNet.Reply response=net.request(r.getString("url"),method,headers,method.equals("GET")||method.equals("HEAD")?null:body,privateAllowed,r.optString("redirect","follow"));
        if(response.bytes.length>1024*1024)throw new Exception("扩展网络响应超过1 MB");
        JSONArray responseHeaders=new JSONArray();Iterator<String> keys=response.headers.keys();while(keys.hasNext()){String k=keys.next();responseHeaders.put(new JSONArray().put(k).put(response.headers.get(k)));}
        return new JSONObject().put("meta",new JSONObject().put("status",response.status).put("url",response.url).put("headers",responseHeaders).put("redirected",!response.url.equals(r.getString("url"))).put("statusText",Integer.toString(response.status))).put("base64",Base64.getEncoder().encodeToString(response.bytes)).toString();
    }
}
