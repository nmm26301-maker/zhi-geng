/* 图书元数据容灾：只使用真实来源的快照；仅返回匹配当前条件的记录。 */
(() => {
  'use strict';
  const KEY='zhigeng_public_book_catalog_v1';
  const COOLDOWN_KEY='zhigeng_google_books_429_until_v1';
  const FIFTEEN_MINUTES=15*60*1000;
  let catalogPromise=null;
  let manualRefresh=false;
  let lastSource=''; // 'catalog' or 'live'
  let lastError='';
  function persisted(){
    try{const obj=JSON.parse(localStorage.getItem(KEY)||'null');return obj?.items?.length?obj:null;}catch{return null;}
  }
  function remember(value){
    try{localStorage.setItem(KEY,JSON.stringify(value));}catch{}
  }
  async function obtain(){
    if(catalogPromise)return catalogPromise;
    catalogPromise=(async()=>{
      try {
        const res=await fetch('data/books.json',{cache:'no-cache'});
        if(!res.ok)throw Error('本地目录 HTTP '+res.status);
        const data=await res.json();
        if(!Array.isArray(data.items)||!data.items.length)throw Error('本地目录暂无记录');
        remember(data);
        return data;
      } catch {
        return persisted()||{items:[],updatedAt:null};
      }
    })();
    return catalogPromise;
  }
  function convert(v){
    if(!v?.title||!/^https:\/\//.test(v.url||''))return null;
    const year=Number(v.year)||null;
    const score=Number(v.score)||0;
    return {
      id:String(v.id||'book:catalog:'+v.url),type:'book',title:String(v.title),
      original:'',year,date:year?year+'-01-01':'',score:score>0?score*2:0,
      votes:Number(v.votes)||0,interest:Number(v.interest)||1,
      image:v.image||'',summary:v.summary||'',
      source:(v.source||'公开图书目录')+' · 缓存目录',url:v.url,
      status:v.ebookAccess==='public'?'可公开阅读':v.ebookAccess==='borrowable'?'馆藏可借阅':'',
      subtype:v.author||'',isbn:v.isbn||'',languages:v.language||[],
      ebookAccess:v.ebookAccess||'',subjects:v.subjects||[],
      editionCount:Number(v.editionCount)||0,ratingBase:score>0?'5':'10'
    };
  }
  const normalizeText=v=>String(v||'').toLowerCase().normalize('NFKC').trim();
  function matches(item,{year,view,query,keyword,sort,page,filters={}}){
    const yr=view==='latest'?new Date().getFullYear():year;
    if(yr!=='all'&&Number(item.year)!==Number(yr))return false;
    if(filters.from&&(item.year===null||item.year<filters.from))return false;
    if(filters.to&&(item.year===null||item.year>filters.to))return false;
    if(filters.language && !(item.languages||[]).includes(filters.language))return false;
    if(filters.availability && item.ebookAccess!==filters.availability)return false;
    if(query){
      const hay=normalizeText([item.title,item.subtype,item.isbn].join(' '));
      if(!hay.includes(normalizeText(query)))return false;
    }
    if(keyword){
      const words=(item.subjects||[]).map(normalizeText).join(' ');
      const table={'悬疑':'mystery','科幻':'science fiction','治愈':'self-help','喜剧':'humor','动作':'adventure','纪录':'biography','爱情':'romance','历史':'history'};
      if(!words.includes(table[keyword]||normalizeText(keyword)))return false;
    }
    return true;
  }
  async function search(options){
    const catalog=await obtain();
    let items=(catalog.items||[]).map(convert).filter(Boolean)
      .filter(item=>matches(item,options));
    const sort=options.sort;
    if(sort==='new'||options.view==='latest')items.sort((a,b)=>(b.year||0)-(a.year||0)||b.score-a.score);
    else if(sort==='rating')items.sort((a,b)=>b.score-a.score);
    else items.sort((a,b)=>b.interest-a.interest||b.score-a.score);
    const page=Math.max(1,Number(options.page)||1),size=24;
    return {items:items.slice((page-1)*size,page*size),count:items.length,updatedAt:catalog.updatedAt};
  }
  function reportCatalog(reason=''){
    lastSource='catalog';lastError=reason;
  }
  function reportLive(){
    lastSource='live';lastError='';
  }
  function markGoogle429(){
    try{localStorage.setItem(COOLDOWN_KEY,String(Date.now()+FIFTEEN_MINUTES));}catch{}
  }
  function googleInCooldown(){
    try{return Date.now()<Number(localStorage.getItem(COOLDOWN_KEY)||0);}catch{return false;}
  }
  function refreshNext(){manualRefresh=true;}
  function consumeManualRefresh(){const value=manualRefresh;manualRefresh=false;return value;}
  window.ZhigengBooksCatalog={
    search,refreshNext,consumeManualRefresh,reportCatalog,reportLive,
    markGoogle429,googleInCooldown,
    get lastSource(){return lastSource;},
    get lastError(){return lastError;}
  };
})();
