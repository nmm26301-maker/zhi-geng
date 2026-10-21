/* 知更图书真实在线搜索：Wikidata + Gutendex，独立于 Open Library / Google Books。 */
(() => {
  'use strict';
  const CACHE_KEY='zhigeng_book_live_search_v3';
  const TTL=12*60*60*1000;
  const LIMIT=16;
  const validUrl=value=>{try{const u=new URL(value);return u.protocol==='https:'?u.href:'';}catch{return '';}};
  const text=value=>String(value??'').trim();
  const normalize=value=>text(value).toLowerCase().normalize('NFKC');
  function cacheRead(query,filters,page){
    try {
      const map=JSON.parse(localStorage.getItem(CACHE_KEY)||'{}');
      const key=JSON.stringify({query:normalize(query),filters,page});
      const entry=map[key];
      return entry&&Date.now()-entry.at<TTL?entry.items:null;
    }catch{return null;}
  }
  function cacheWrite(query,filters,page,items){
    try{
      const key=JSON.stringify({query:normalize(query),filters,page});
      const map=JSON.parse(localStorage.getItem(CACHE_KEY)||'{}');
      map[key]={at:Date.now(),items:items.slice(0,65)};
      const sorted=Object.entries(map).filter(([,x])=>x?.at&&Date.now()-x.at<TTL).sort((a,b)=>b[1].at-a[1].at).slice(0,20);
      localStorage.setItem(CACHE_KEY,JSON.stringify(Object.fromEntries(sorted)));
    }catch{}
  }
  async function getJson(url,timeoutMs=8500){
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),timeoutMs);
    try{
      const response=await (window.ZhigengTransport?.fetch||fetch)(url,{signal:controller.signal,headers:{Accept:'application/json'}});
      if(!response.ok)throw new Error('HTTP '+response.status);
      return await response.json();
    }finally{clearTimeout(timeout);}
  }
  const languageMap={eng:'en',chi:'zh',jpn:'ja',fre:'fr',ger:'de',spa:'es'};
  const languageBookMap={en:'eng',zh:'chi',ja:'jpn',fr:'fre',de:'ger',es:'spa'};
  function eligible(item,filters={}){
    if(filters.from&&(!item.year||item.year<filters.from))return false;
    if(filters.to&&(!item.year||item.year>filters.to))return false;
    if(filters.language&&!(item.languages||[]).includes(filters.language))return false;
    if(filters.availability&&item.ebookAccess!==filters.availability)return false;
    return true;
  }
  async function gutenberg(query,filters,page){
    if(filters.availability==='borrowable'||filters.from||filters.to)return [];
    const url=new URL('https://gutendex.com/books/');
    url.searchParams.set('search',query);
    if(filters.language)url.searchParams.set('languages',languageMap[filters.language]||'en');
    if(page>1)url.searchParams.set('page',String(page));
    const data=await getJson(url.toString());
    return (Array.isArray(data.results)?data.results:[]).map(book=>{
      const id=Number(book.id);
      if(!Number.isInteger(id)||!book.title)return null;
      const pic=validUrl(book.formats?.['image/jpeg']);
      const langs=(book.languages||[]).map(l=>languageBookMap[l]||l);
      return {id:'book:gutenberg:'+id,type:'book',title:text(book.title),original:'',year:null,date:'',
        score:0,votes:0,interest:Number(book.download_count)||1,image:pic,summary:(book.summaries||[]).slice(0,2).join(' ').slice(0,1800),
        source:'Project Gutenberg',url:'https://www.gutenberg.org/ebooks/'+id,status:'公版图书（请遵循所在地区版权规定）',
        subtype:(book.authors||[]).slice(0,2).map(a=>a.name).join(' · '),ratingBase:'10',
        languages:langs,subjects:(book.subjects||[]).slice(0,20),ebookAccess:'public',editionCount:1};
    }).filter(v=>v&&eligible(v,filters));
  }
  // Wikidata 支持 origin=* 的跨域实体搜索，结果可能含电影等同名实体，先按公开的描述过滤为图书。
  const BOOK_PATTERN=/小说|书籍|图书|文学|传记|著作|诗集|散文|文集|轻小说|漫画|短篇|长篇|科幻作品|作品集|novel|book|literary|fiction|poetry|poem|memoir|biography|anthology|short story|comic|manga|light novel|written work|collection of stories|textbook/i;
  const NONBOOK_PATTERN=/电影|电视剧|电视节目|电视连续剧|电子游戏|游戏|音乐专辑|动画电影|film|television|video game|album|motion picture|tv series/i;
  async function wikidata(query,filters,page) {
    if(filters.language||filters.availability||filters.from||filters.to||page>2)return [];
    const url=new URL('https://www.wikidata.org/w/api.php');
    url.searchParams.set('action','wbsearchentities');
    url.searchParams.set('search',query);
    url.searchParams.set('language',/[一-鿿]/.test(query)?'zh':'en');
    url.searchParams.set('uselang',/[一-鿿]/.test(query)?'zh':'en');
    url.searchParams.set('type','item');
    url.searchParams.set('limit','50');
    url.searchParams.set('format','json');
    url.searchParams.set('origin','*');
    if(page>1)url.searchParams.set('continue',String((page-1)*50));
    const data=await getJson(url.toString());
    return (Array.isArray(data.search)?data.search:[])
      .filter(v=>/^Q\d+$/.test(v.id||'')&&BOOK_PATTERN.test(text(v.description))&&!NONBOOK_PATTERN.test(text(v.description)))
      .slice(0,LIMIT).map(v=>({
        id:'book:wikidata:'+v.id,type:'book',title:text(v.label||v.match?.text||query),
        original:'',year:null,date:'',score:0,votes:0,interest:1,image:'',
        summary:text(v.description),source:'Wikidata',url:'https://www.wikidata.org/wiki/'+v.id,
        status:'图书百科资料',subtype:text(v.description),ratingBase:'10',
        languages:[],subjects:[],ebookAccess:'',editionCount:0
      }));
  }
  async function wikipedia(query,filters,page){
    if(filters.language||filters.availability||filters.from||filters.to||page>1)return [];
    const zh=/[一-鿿]/.test(query),host=zh?'zh.wikipedia.org':'en.wikipedia.org';
    const url=new URL('https://'+host+'/w/api.php');
    url.searchParams.set('action','query');
    url.searchParams.set('generator','search');
    url.searchParams.set('gsrsearch',query);
    url.searchParams.set('gsrnamespace','0');
    url.searchParams.set('gsrlimit','20');
    url.searchParams.set('prop','extracts');
    url.searchParams.set('exintro','1');
    url.searchParams.set('explaintext','1');
    url.searchParams.set('exchars','450');
    url.searchParams.set('format','json');
    url.searchParams.set('origin','*');
    const data=await getJson(url.toString());
    return Object.values(data.query?.pages||{}).filter(page=>{
      const heading=text(page.title),lead=text(page.extract).slice(0,210);
      const opening=lead.split(/[。.!?\n]/)[0].slice(0,170);
      return page.pageid && !NONBOOK_PATTERN.test(heading) &&
        BOOK_PATTERN.test(heading+' '+opening) &&
        !(NONBOOK_PATTERN.test(opening)&&!BOOK_PATTERN.test(heading));
    }).slice(0,12).map(v=>({
      id:'book:wikipedia:'+host+':'+v.pageid,
      type:'book',title:text(v.title),original:'',year:null,date:'',score:0,votes:0,interest:1,
      image:'',summary:text(v.extract).slice(0,1300),source:zh?'中文维基百科 · 图书':'Wikipedia · Books',
      url:'https://'+host+'/?curid='+v.pageid,status:'图书百科资料',subtype:'',
      ratingBase:'10',languages:[],subjects:[],ebookAccess:'',editionCount:0
    }));
  }
  function deduplicate(items){
    const seen=new Set();
    return items.filter(v=>{
      if(!v?.title)return false;
      const id=normalize(v.title).replace(/[\s\p{P}]+/gu,'');
      if(seen.has(id))return false;
      seen.add(id);return true;
    });
  }
  async function search({query,filters={},page=1}={}){
    query=text(query).slice(0,140);
    if(!query)return {items:[],attempted:0,failures:0,fromCache:false};
    const cached=cacheRead(query,filters,page);
    if(cached)return {items:cached,attempted:0,failures:0,fromCache:true};
    const results=await Promise.allSettled([gutenberg(query,filters,page),wikidata(query,filters,page),wikipedia(query,filters,page)]);
    const items=deduplicate(results.flatMap(r=>r.status==='fulfilled'?r.value:[]));
    const failures=results.filter(r=>r.status==='rejected').length;
    // 仅成功返回的数据可以缓存；断网失败时不写空结果，避免后续错误持续存在。
    if(items.length)cacheWrite(query,filters,page,items);
    return {items,attempted:3,failures,fromCache:false};
  }
  window.ZhigengBookSearch={search};
})();

