/* EAPI search mapping adapted from heartleo/zlib (MIT), see licenses/. */
export class GatewayError extends Error {
  constructor(message,status=400){super(message);this.status=status;}
}
const DOMAINS=['1lib.sk','article.sk','articles.sk','z-lib.fm','z-lib.gd','z-lib.gl','z-lib.sk','z-library.ec','zlib.bz','zliba.ru'];
export function domainOrigin(value){
  let u;try{u=new URL(value);}catch{throw new GatewayError('请输入 HTTPS 源站地址');}
  if(u.protocol!=='https:'||u.port||u.username||u.password||u.search||u.hash||u.pathname!=='/'||!DOMAINS.includes(u.hostname))
    throw new GatewayError('仅接受 heartleo/zlib 列出的 HTTPS 源站，不含路径、端口或账号信息');
  return u.origin;
}
const str=(v,max=500)=>String(v??'').trim().slice(0,max);
const plain=v=>str(v,4000).replace(/<[^>]*>/g,'');
const int=(v,min,max,fallback)=>Number.isInteger(Number(v))&&Number(v)>=min&&Number(v)<=max?Number(v):fallback;
function session(input){
  const id=str(input?.id,40),key=str(input?.key,256);
  if(!/^\d{1,30}$/.test(id)||!/^[-a-zA-Z0-9_]{6,256}$/.test(key))throw new GatewayError('请先连接自己的 Z-Library 账号',401);
  return {'remix-userid':id,'remix-userkey':key,Cookie:'remix_userid='+id+'; remix_userkey='+key};
}
function safeURL(value,base){try{const u=new URL(value,base);return u.protocol==='https:'&&!u.username&&!u.password?u.href:'';}catch{return '';}}
function book(item,origin){
  const id=str(item.id,40),hash=str(item.hash,100),year=int(item.year,1000,2100,null);
  const href=safeURL(item.href,origin);
  return {id:'book:zlib:'+id+':'+hash,type:'book',title:plain(item.title)||'未命名图书',original:'',year,date:year?year+'-01-01':'',
    score:0,votes:0,interest:1,ratingBase:'10',image:safeURL(item.cover,origin),summary:plain(item.description),
    source:'Z-Library · heartleo/zlib',url:href&&new URL(href).origin===origin?href:origin+'/book/'+encodeURIComponent(id)+'/'+encodeURIComponent(hash),
    subtype:plain(item.author),status:[plain(item.language),str(item.extension,12).toUpperCase(),str(item.filesizeString,40)].filter(Boolean).join(' · '),
    format:str(item.extension,12),publisher:plain(item.publisher),isbn:str(item.isbn||item.identifier,80),
    zlib:{id,hash},languages:[str(item.language,40)],ebookAccess:''};
}
async function upstream(url,options,fetcher){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);
  try{
    const response=await fetcher(url,{...options,redirect:'manual',signal:controller.signal,cache:'no-store'});
    if(response.status>=300&&response.status<400)throw new GatewayError('来源站发生跳转，请选择最终源站后重新连接',502);
    const body=await response.text();
    if(body.length>4_000_000)throw new GatewayError('来源响应过大',502);
    let data;try{data=JSON.parse(body);}catch{throw new GatewayError('来源返回验证页或非 JSON 数据，请稍后重试或更换源站',502);}
    return {response,data};
  }catch(e){if(e instanceof GatewayError)throw e;throw new GatewayError(e.name==='AbortError'?'来源连接超时，请稍后重试':'服务器暂时无法连接来源，请稍后重试',502);}
  finally{clearTimeout(timer);}
}
export async function zlib(input,fetcher=fetch){
  const origin=domainOrigin(input.domain),action=input.action;
  const headers={Accept:'application/json','Content-Type':'application/x-www-form-urlencoded','User-Agent':'Zhigeng/1.0 (heartleo-zlib EAPI adapter)'};
  const call=(path,values={},credentials={})=>upstream(origin+path,{method:'POST',headers:{...headers,...credentials},body:new URLSearchParams(values).toString()},fetcher);
  if(action==='login'){
    const email=str(input.email,254),password=String(input.password||'');
    if(!email||!password||password.length>512)throw new GatewayError('请填写账号邮箱和密码');
    const result=await call('/eapi/user/login',{email,password});
    let id=str(result.data.user?.id,40),key=str(result.data.user?.remix_userkey||result.data.user?.remix_user_key,256);
    if(Number(result.data.success)!==1){
      const fallback=await call('/rpc.php',{isModal:'true',email,password,site_mode:'books',action:'login',isSingleLogin:'1',redirectUrl:'',gg_json_mode:'1'});
      if(fallback.data.response?.validationError)throw new GatewayError('登录未通过，请核对账号或在来源网站确认账号状态',401);
      const cookies=typeof fallback.response.headers.getSetCookie==='function'?fallback.response.headers.getSetCookie():[fallback.response.headers.get('set-cookie')||''];
      const raw=cookies.join('; ');
      id=raw.match(/(?:^|[;,]\s*)remix_userid=([^;,\s]+)/)?.[1]||'';
      key=raw.match(/(?:^|[;,]\s*)remix_userkey=([^;,\s]+)/)?.[1]||'';
    }
    session({id,key});
    return {connected:true,session:{id,key},domain:origin};
  }
  const credentials=session(input.session);
  if(action==='verify'){
    const {data}=await upstream(origin+'/eapi/user/profile',{method:'GET',headers:{...headers,...credentials}},fetcher);
    if(Number(data.success)!==1)throw new GatewayError('会话无效或已过期，请重新连接',401);
    return {connected:true};
  }
  if(action!=='search')throw new GatewayError('不支持的操作');
  const q=str(input.query,200);if(!q)throw new GatewayError('请输入书名或作者');
  const page=int(input.page,1,500,1),f=input.filters||{},values={message:q,page:String(page),limit:'24'};
  if(f.exact)values.e='1';
  const from=int(f.from,1000,2100,null),to=int(f.to,1000,2100,null);
  if(from&&to&&from>to)throw new GatewayError('起始年份不能大于结束年份');
  if(from)values.yearFrom=String(from);if(to)values.yearTo=String(to);
  const language={chi:'chinese',eng:'english',jpn:'japanese',fre:'french',ger:'german',spa:'spanish'}[f.language];
  if(language)values['languages[]']=language;
  if(['epub','pdf','txt','mobi','azw3','djvu','fb2'].includes(f.extension))values['extensions[]']=f.extension;
  const {data}=await call('/eapi/book/search',values,credentials);
  if(Number(data.success)!==1)throw new GatewayError('搜索被来源站拒绝，账号可能已过期或需要在来源站完成验证',401);
  return {items:(Array.isArray(data.books)?data.books:[]).slice(0,100).map(v=>book(v,origin)),page:int(data.pagination?.current,1,500,page),totalPages:int(data.pagination?.total_pages,0,100000,0)};
}
const PROVIDERS={
  'api.jikan.moe':/^\/v4\/(anime|seasons)(\/|$)/,
  'graphql.anilist.co':/^\/$/,
  'openlibrary.org':/^\/(search\.json|works\/OL\d+W\.json)$/,
  'www.googleapis.com':/^\/books\/v1\/volumes$/,
  'api.tvmaze.com':/^\/(schedule(?:\/web)?|search\/shows)$/,
  'gutendex.com':/^\/books\/$/,
  'www.wikidata.org':/^\/w\/api\.php$/,
  'zh.wikipedia.org':/^\/w\/api\.php$/,
  'en.wikipedia.org':/^\/w\/api\.php$/,
  'api.themoviedb.org':/^\/3\/(configuration|search\/(movie|tv)|discover\/(movie|tv))$/
};
export async function catalog(input,fetcher=fetch){
  let u;try{u=new URL(input.url);}catch{throw new GatewayError('无效来源');}
  if(u.protocol!=='https:'||u.port||u.username||u.password||!PROVIDERS[u.hostname]?.test(u.pathname))throw new GatewayError('来源不在可访问列表中');
  const headers={Accept:'application/json'},tmdb=u.hostname==='api.themoviedb.org';
  if(tmdb&&input.authorization){
    if(!/^Bearer eyJ[A-Za-z0-9_.-]+$/.test(input.authorization))throw new GatewayError('无效 TMDB Token');
    headers.Authorization=input.authorization;
  }
  const opts={method:'GET',headers};
  if(u.hostname==='graphql.anilist.co'){
    let body;try{body=typeof input.body==='string'?JSON.parse(input.body):input.body;}catch{throw new GatewayError('查询格式错误');}
    if(typeof body?.query!=='string'||body.query.length>6000||/\bmutation\b/i.test(body.query))throw new GatewayError('仅支持书目查询');
    opts.method='POST';headers['Content-Type']='application/json';opts.body=JSON.stringify(body);
  }
  const {response,data}=await upstream(u.href,opts,fetcher);
  return {status:response.status,data};
}
export async function handleGateway(request,fetcher=fetch){
  const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'};
  const reply=(value,status=200)=>new Response(JSON.stringify(value),{status,headers});
  if(request.method==='GET')return reply({ok:true,version:1,features:['catalog','zlib-search']});
  if(request.method!=='POST')return reply({error:'Method not allowed'},405);
  const origin=request.headers.get('origin');
  if(origin&&origin!==new URL(request.url).origin)return reply({error:'请在知更页面内操作'},403);
  if(!request.headers.get('content-type')?.startsWith('application/json'))return reply({error:'JSON required'},415);
  try{
    const raw=await request.text();if(raw.length>18000)return reply({error:'请求过大'},413);
    const input=JSON.parse(raw);
    if(input.service==='zlib')return reply(await zlib(input,fetcher));
    if(input.service==='catalog'){const result=await catalog(input,fetcher);return reply(result.data,result.status);}
    throw new GatewayError('未知服务');
  }catch(error){return reply({error:error instanceof GatewayError?error.message:'请求格式无效'},error instanceof GatewayError?error.status:400);}
}
