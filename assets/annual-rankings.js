/* 年度排行榜数据适配。按同一类型、同一年份和同一数据源排序。 */
(() => {
  'use strict';
  const fetchJSON = async (url, init, fetcher) => {
    const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),16000);
    try {
      const res=await (fetcher||fetch)(url,{...init,signal:ctl.signal});
      if(!res.ok)throw Error('数据接口 HTTP '+res.status+(res.status===429?' · 请求过于频繁':''));
      const data=await res.json();
      if(!data||typeof data!=='object')throw Error('数据源未返回有效数据');
      return data;
    } catch(e) {if(e.name==='AbortError')throw Error('数据源请求超时');throw e;}
    finally {clearTimeout(timer);}
  };
  const result=(items,f,note,more=false)=>({
    items:items.map((v,i)=>({...v,rank:(f.page-1)*20+i+1,annual:true})),
    note,hasMore:!!more&&f.page<25
  });
  const validate=(input)=>{
    const f={...input,year:Number(input.year),page:Number(input.page)};
    if(!Number.isInteger(f.year)||f.year<1900||f.year>new Date().getFullYear())throw Error('年份无效');
    if(!Number.isInteger(f.page)||f.page<1||f.page>25)throw Error('页数无效');
    if(!['movie','tv','anime','variety','book'].includes(f.type))throw Error('请选择内容类型');
    if(!['CN','global'].includes(f.region)||!['rating','popular'].includes(f.sort))throw Error('筛选条件无效');
    return f;
  };
  async function films(f){
    const credential=String(f.credential||'').trim();
    const isKey=/^[a-f0-9]{32}$/i.test(credential);
    const isToken=/^eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(credential);
    if(!isKey&&!isToken)return {items:[],hasMore:false,needsTmdb:true,note:'影视年度真实排行榜需要 TMDB API Key 或 Read Access Token。请到「接口与偏好设置」验证并保存；没有凭证时不会编造排名。'};
    const route=f.type==='movie'?'movie':'tv';
    const key=route==='movie'?'primary_release_date':'first_air_date';
    const url=new URL('https://api.themoviedb.org/3/discover/'+route);
    // 热度榜允许未评分新作进入；国内评分榜降低样本门槛，同时明确评分偏差。
    const minVotes=f.sort==='popular'?0:(f.region==='CN'?10:50);
    const params={'language':'zh-CN','include_adult':'false','page':String(f.page),
      'sort_by':f.sort==='rating'?'vote_average.desc':'popularity.desc',
      [key+'.gte']:f.year+'-01-01',[key+'.lte']:f.year+'-12-31'};
    if(minVotes>0)params['vote_count.gte']=String(minVotes);
    if(f.type==='variety')params.with_genres='10764|10767';
    // OR 匹配内地／香港／台湾制作来源；不是将所有中文配音外国片视作国产。
    if(f.region==='CN')params.with_origin_country='CN|HK|TW';
    for(const [name,value] of Object.entries(params))url.searchParams.set(name,value);
    const headers={Accept:'application/json'};
    if(isKey)url.searchParams.set('api_key',credential);else headers.Authorization='Bearer '+credential;
    const data=await fetchJSON(url.href,{headers},f.fetcher);
    const date=route==='movie'?'release_date':'first_air_date';
    const rows=(Array.isArray(data.results)?data.results:[])
      .filter(v=>String(v[date]||'').slice(0,4)===String(f.year)&&Number(v.vote_count)>=minVotes)
      .map(v=>({
        id:'tmdb:'+route+':'+v.id,type:f.type,title:v.title||v.name||'未命名作品',year:String(f.year),
        author:'TMDB 用户评分',tag:f.sort==='rating'?'评分排行':'热度排行',
        summary:v.overview||'',source:'TMDB',url:'https://www.themoviedb.org/'+route+'/'+v.id,
        image:v.poster_path?'https://image.tmdb.org/t/p/w342'+v.poster_path:'',
        score:Number(v.vote_average)||0,votes:Number(v.vote_count)||0,interest:Number(v.popularity)||0,
        scoreLabel:'TMDB /10'
      }));
    return result(rows,f,'TMDB · '+f.year+' 年 · '+(f.region==='CN'?'内地／香港／台湾来源':'全球')+
      ' · '+(f.sort==='rating'?'TMDB 平均评分排序':'TMDB 热度排序')+
      (minVotes?' · '+minVotes+'票起（小样本评分需谨慎）':' · 不限定评分人数')+
      ' · 当前源共 '+Number(data.total_results||0)+' 条匹配（非全国作品全集）',
      f.page<Number(data.total_pages||1));
  }
  async function anime(f){
    const query='query YearAnime($page:Int!,$year:Int!,$origin:CountryCode,$sort:MediaSort!){Page(page:$page,perPage:20){pageInfo{hasNextPage}media(type:ANIME,seasonYear:$year,countryOfOrigin:$origin,sort:[$sort],isAdult:false){id title{native romaji english}siteUrl coverImage{large}description(asHtml:false)averageScore popularity seasonYear}}}';
    const body={query,variables:{page:f.page,year:f.year,origin:f.region==='CN'?'CN':null,
      sort:f.sort==='rating'?'SCORE_DESC':'POPULARITY_DESC'}};
    const data=await fetchJSON('https://graphql.anilist.co',{method:'POST',headers:{Accept:'application/json','Content-Type':'application/json'},body:JSON.stringify(body)},f.fetcher);
    if(data.errors?.length)throw Error(String(data.errors[0]?.message||'AniList 查询失败'));
    const page=data.data?.Page;
    if(!page||!Array.isArray(page.media))throw Error('AniList 返回格式无效');
    const rows=page.media.filter(v=>Number(v.seasonYear)===f.year).map(v=>({
      id:'anilist:'+v.id,type:'anime',title:v.title?.native||v.title?.english||v.title?.romaji||'未命名动漫',
      author:'AniList',year:String(f.year),source:'AniList',tag:f.sort==='rating'?'平均分排行':'关注度排行',
      url:v.siteUrl||'https://anilist.co/anime/'+v.id,image:v.coverImage?.large||'',
      summary:String(v.description||'').replace(/<[^>]*>/g,'').slice(0,220),
      score:(Number(v.averageScore)||0)/10,votes:0,interest:Number(v.popularity)||0,scoreLabel:'AniList /10'
    }));
    return result(rows,f,'AniList · '+f.year+' 年 · '+(f.region==='CN'?'中国来源动漫':'全球动漫')+
      ' · '+(f.sort==='rating'?'均分排序':'关注人数排序')+' · 关注人数不等于评分人数',
      page.pageInfo?.hasNextPage);
  }
  async function books(f){
    const url=new URL('https://openlibrary.org/search.json');
    const q=['first_publish_year:'+f.year];
    if(f.region==='CN')q.push('language:chi');
    if(f.sort==='rating')q.push('ratings_count:[10 TO *]');
    url.searchParams.set('q',q.join(' AND '));
    url.searchParams.set('fields','key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count,readinglog_count,edition_count');
    url.searchParams.set('sort',f.sort==='rating'?'rating':'readinglog');
    url.searchParams.set('limit','20');
    url.searchParams.set('page',String(f.page));
    const data=await fetchJSON(url.href,{headers:{Accept:'application/json'}},f.fetcher);
    const rows=(Array.isArray(data.docs)?data.docs:[])
      .filter(v=>Number(v.first_publish_year)===f.year)
      .map(v=>({
        id:'book:'+v.key,type:'book',title:v.title||'未命名图书',year:String(f.year),
        author:Array.isArray(v.author_name)?v.author_name.slice(0,2).join(' / '):'',
        tag:f.sort==='rating'?'读者评分':'书架关注度',source:'Open Library',
        summary:'首次出版年份为 '+f.year+'；书目包含不同语言版本。',
        url:'https://openlibrary.org'+(String(v.key||'').startsWith('/')?v.key:'/works/'+v.key),
        image:v.cover_i?'https://covers.openlibrary.org/b/id/'+v.cover_i+'-M.jpg':'',
        score:Number(v.ratings_average)||0,votes:Number(v.ratings_count)||0,
        interest:Number(v.readinglog_count)||0,scoreLabel:'Open Library /5'
      }));
    return result(rows,f,'Open Library · '+f.year+' 年首次出版 · '+
      (f.region==='CN'?'存在中文版本，不代表中国原创':'全部语言')+
      ' · '+(f.sort==='rating'?'至少10人评分':'读者书架关注度')+'，不代表销量',
      f.page*20<Number(data.numFound||data.num_found||0));
  }
  async function cachedBooks(f,error){
    if(f.type!=='book'||f.region!=='global'||f.page!==1)throw error;
    const catalog=await fetchJSON('data/books.json',{},f.fetcher);
    const list=(Array.isArray(catalog.items)?catalog.items:[]).filter(v=>Number(v.year)===f.year);
    if(!list.length)throw error;
    list.sort((a,b)=>f.sort==='popular'?Number(b.interest||0)-Number(a.interest||0):
      ((Number(b.votes)>=10?Number(b.score):0)-(Number(a.votes)>=10?Number(a.score):0))||
      Number(b.interest||0)-Number(a.interest||0));
    const rows=list.slice(0,20).map(v=>({
      ...v,year:String(f.year),type:'book',author:v.author||'',scoreLabel:'Open Library /5',
      summary:'来自上次生成的多年度书目缓存',source:'Open Library 历史缓存',tag:'缓存样本'
    }));
    return result(rows,f,'实时书籍接口不可用；展示 '+f.year+' 年已保存的 '+list.length+
      ' 条书目样本，按'+(f.sort==='rating'?'评分':'关注度')+'排序，非完整年度排行榜。',false);
  }
  async function load(input){
    const f=validate(input);
    try {
      if(f.type==='anime')return await anime(f);
      if(f.type==='book')return await books(f);
      const value=await films(f);
      // 无 TMDB 凭证时，公开年度作品索引仅是检索目录，不是评分排行榜。
      if(value.needsTmdb&&f.region==='CN'&&['movie','tv'].includes(f.type)&&window.ZhigengPublicAnnualCatalog?.load) {
        try{return await window.ZhigengPublicAnnualCatalog.load(f);}
        catch(error){return {...value,note:value.note+'；公开百科作品目录暂不可用：'+error.message};}
      }
      return value;
    } catch(e) {
      try {return await cachedBooks(f,e);}catch{}
      throw e;
    }
  }
  window.ZhigengAnnualRankings={load,minYear:1900};
})();
