package cn.zhigeng.app;

import java.net.URI;

/** Strict endpoint construction: secrets never go in a URL or across redirects. */
public final class FccPolicy {
    private FccPolicy() {}
    public static String base(String value) {
        try {
            String raw=value==null?"":value.trim();URI u=new URI(raw);
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null
                    ||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new Exception();
            String path=u.getPath()==null?"":u.getPath();
            String decoded=u.getPath();if(decoded.contains("..")||decoded.contains("//")||raw.contains("\\"))throw new Exception();
            while(path.endsWith("/"))path=path.substring(0,path.length()-1);
            if(!path.endsWith("/v1"))path+="/v1";
            return new URI("https",u.getRawAuthority(),path,null,null).toASCIIString();
        } catch(Exception e){throw new IllegalArgumentException("请填写有效的 HTTPS 网关地址，如 https://你的域名 或 /v1 地址；不要填写密钥或参数。");}
    }
    public static String token(String raw){String s=raw==null?"":raw.trim();if(s.length()>4096)throw new IllegalArgumentException("网关 Token 过长");for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i))||Character.isWhitespace(s.charAt(i)))throw new IllegalArgumentException("网关 Token 不能包含空格或换行");return s;}
    public static String status(int code){
        switch(code){case 401:case 403:return "网关鉴权失败，请检查 Proxy Token 和网关权限（HTTP "+code+"）。";
            case 402:return "服务商额度或余额不足，请在 FCC 管理端检查账号（HTTP 402）。";
            case 404:return "未找到 FCC 接口，请核对网关地址和版本（HTTP 404）。";
            case 429:return "网关或模型服务商限流，请稍后重试或手动换模型（HTTP 429）。";
            default:return code>=300&&code<400?"网关返回跳转。为避免发送错地址，请填写跳转后的 HTTPS 网关地址。":"网关请求失败（HTTP "+code+"），请检查 FCC 服务状态。";}
    }
}
