/* 知更 v0.1 — metadata discovery only. Never downloads copyrighted media. */
(() => {
  'use strict';
  const NOW = new Date();
  const CURRENT_YEAR = NOW.getFullYear();
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const state = {category:'all', view:'explore', year:String(CURRENT_YEAR), sort:'popular', keyword:'', query:'', page:1, items:[], lastRun:0, loading:false, moreAvailable:false};
  const STORE = { key:'zhigeng_tmdb_credential_v1', saved:'zhigeng_saved_v1' };
  const GENRES = {
    '悬疑': {movie:'9648',tv:'9648',anime:7,book:'mystery'},
    '科幻': {movie:'878',tv:'10765',anime:24,book:'science_fiction'},
    '治愈': {movie:'18',tv:'18',anime:36,book:'self_help'},
    '喜剧': {movie:'35',tv:'35',anime:4,book:'humor'},
    '动作': {movie:'28',tv:'10759',anime:1,book:'adventure'},
    '纪录': {movie:'99',tv:'99',anime:0,book:'biography'},
    '爱情': {movie:'10749',tv:'10749',anime:22,book:'romance'},
    '历史': {movie:'36',tv:'10768',anime:13,book:'history'}
  };
  const LABELS = {movie:'电影',tv:'电视剧',anime:'动漫',variety:'综艺',book:'书籍'};
  const SOURCE_COLOR = {movie:'#f5b49a',tv:'#99c7fe',anime:'#d0b3ff',variety:'#f3d28f',book:'#b5dfb5'};
  const tagSentence = {'悬疑':'悬念感拉满','科幻':'想象力探索','治愈':'温柔的作品','喜剧':'适合放松','动作':'节奏与冒险','纪录':'拓宽眼界','爱情':'关于人与感情','历史':'重访另一段时间'};

  let toastHandle;
  function getKey(){try{return localStorage.getItem(STORE.key)||'';}catch{return '';}}
  function getSaved(){try{return JSON.parse(localStorage.getItem(STORE.saved)||'{}')||{};}catch{return {};}}
  function saveList(data){try{localStorage.setItem(STORE.saved,JSON.stringify(data));}catch{toast('浏览器不允许本地存储，请检查隐私设置');}}
  function toast(message){const node=$('#toast');node.textContent=message;node.classList.add('show');clearTimeout(toastHandle);toastHandle=setTimeout(()=>node.classList.remove('show'),2500);}
  function safe(str){return String(str??'').replace(/[&<>"']/g,(ch)=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));}
  function safeUrl(url){try{const v=new URL(url);return ['https:','http:'].includes(v.protocol)?v.href:'#';}catch{return '#';}}
  function short(str,max=90){const t=String(str||'').replace(/\s+/g,' ').trim();return t.length>max?t.slice(0,max)+'…':t;}
  function plainHTML(value){const node=document.createElement('div');node.innerHTML=String(value||'');return node.textContent||'';}
  function endpoint(base, values={}){const url=new URL(base);Object.entries(values).forEach(([key,val])=>{if(val!==undefined&&val!==null&&val!=='')url.searchParams.set(key,String(val));});return url.toString();}
  async function request(url, options={}){
    const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),11500);
    try{const res=await fetch(url,{...options,signal:controller.signal,headers:{Accept:'application/json',...(options.headers||{})}});if(!res.ok){throw new Error(`${res.status}${res.status===429?'（请求频率限制）':''}`);}return await res.json();}
    catch(e){if(e.name==='AbortError')throw new Error('请求超时');throw e;}
    finally{clearTimeout(timeout);}
  }
  function tmdbAuth(){const key=getKey();if(!key)return null;return /^[\w-]{100,}$/.test(key)||key.startsWith('eyJ')?{headers:{Authorization:`Bearer ${key}`}}:{params:{api_key:key}};}
  async function tmdbRequest(path,params={}){const auth=tmdbAuth();if(!auth)throw new Error('尚未配置 TMDB');return request(endpoint('https://api.themoviedb.org/3/'+path,{language:'zh-CN',include_adult:'false',...params,...(auth.params||{})}),auth.headers?{headers:auth.headers}:{});}
  function num(v){const n=Number(v);return Number.isFinite(n)?n:0;}
  function cleanDate(v){return /^\d{4}-\d{2}-\d{2}/.test(String(v||''))?String(v).slice(0,10):'';}
  function normalizeTmdb(item,category){const tv=category!=='movie';const date=cleanDate(tv?item.first_air_date:item.release_date);return {id:category+':'+item.id,type:category,title:item.title||item.name||item.original_title||item.original_name||'未命名作品',original:item.original_title||item.original_name||'',year:date?Number(date.slice(0,4)):null,date,score:num(item.vote_average),votes:num(item.vote_count),interest:num(item.popularity),image:item.poster_path?'https://image.tmdb.org/t/p/w500'+item.poster_path:'',summary:item.overview||'',source:'TMDB',url:`https://www.themoviedb.org/${tv?'tv':'movie'}/${item.id}`,status:'',subtype:''};}
  async function moviesOrShows(category){const movie=category==='movie';const route=movie?'movie':'tv';const params={page:state.page};const earliest=new Date(NOW.getTime()-35*86400000).toISOString().slice(0,10);
    if(state.query){const result=await tmdbRequest('search/'+route,{query:state.query,page:state.page,...(state.year!=='all'?{year:state.year,first_air_date_year:state.year}:{})});return (result.results||[]).map(x=>normalizeTmdb(x,category));}
    if(state.year!=='all'&&state.view!=='latest'){params[movie?'primary_release_year':'first_air_date_year']=state.year;}
    if(state.keyword&&GENRES[state.keyword])params.with_genres=GENRES[state.keyword][category==='variety'?'tv':route];
    if(category==='variety'){params.with_genres='10764|10767';}
    if(state.view==='latest'){params[movie?'primary_release_date.gte':'first_air_date.gte']=earliest;params[movie?'primary_release_date.lte':'first_air_date.lte']=new Date(NOW.getTime()+60*86400000).toISOString().slice(0,10);}
    params.sort_by=state.view==='latest'?(movie?'primary_release_date.desc':'first_air_date.desc'):state.sort==='rating'?'vote_average.desc':state.sort==='new'?(movie?'primary_release_date.desc':'first_air_date.desc'):'popularity.desc';
    if(state.sort==='rating'&&!state.query){params['vote_count.gte']=movie?60:30;}
    const result=await tmdbRequest('discover/'+route,params);return (result.results||[]).map(x=>normalizeTmdb(x,category));
  }
  function normalizeJikan(item){const date=cleanDate(item.aired?.from);return {id:'anime:'+item.mal_id,type:'anime',title:item.title_english||item.title||'未命名动漫',original:item.title_japanese||'',year:date?Number(date.slice(0,4)):item.year||null,date,score:num(item.score),votes:num(item.scored_by),interest:num(item.members),image:item.images?.jpg?.large_image_url||item.images?.jpg?.image_url||'',summary:item.synopsis||'',source:'Jikan / MAL',url:item.url||`https://myanimelist.net/anime/${item.mal_id}`,status:item.status||'',subtype:item.type||''};}
  async function animes(){let url;
    if(state.view==='latest'&&!state.query)url=endpoint('https://api.jikan.moe/v4/seasons/now',{page:state.page,limit:24,sfw:true});
    else {const opts={page:state.page,limit:24,sfw:true};if(state.query)opts.q=state.query;if(state.year!=='all'){opts.start_date=state.year+'-01-01';opts.end_date=state.year+'-12-31';}if(state.keyword&&GENRES[state.keyword]?.anime)opts.genres=GENRES[state.keyword].anime;
      if(!state.query){opts.order_by=state.sort==='new'?'start_date':state.sort==='rating'?'score':'members';opts.sort='desc';}url=endpoint('https://api.jikan.moe/v4/anime',opts);
    }
    const result=await request(url);return (result.data||[]).map(normalizeJikan).filter(x=>x.title);
  }
  function normalizeOpenLibrary(item){const year=Number(item.first_publish_year)||null;const rating=num(item.ratings_average);const key=String(item.key||'');const workKey=key.startsWith('/works/')?key:'/works/'+key.replace(/^\/+/, '');return {id:'book:'+workKey,type:'book',title:item.title||'未命名图书',original:'',year,date:year?year+'-01-01':'',score:rating?rating*2:0,votes:num(item.ratings_count),interest:num(item.readinglog_count)||num(item.edition_count),image:item.cover_i?`https://covers.openlibrary.org/b/id/${item.cover_i}-M.jpg`:'',summary:'',source:'Open Library',url:'https://openlibrary.org'+workKey,status:item.public_scan_b?'可查看公开扫描':'',subtype:(item.author_name||[]).slice(0,2).join(' · '),ratingBase:rating?'5':'10'};}
  async function books(){const year=state.view==='latest'?CURRENT_YEAR:state.year;const q=[];
    if(state.query)q.push('('+state.query.replace(/[\\+\-!():^\[\]{}~*?|&]/g,' ')+')');
    else q.push('subject:'+(GENRES[state.keyword]?.book||'fiction'));
    if(year!=='all')q.push(`first_publish_year:[${year} TO ${year}]`);
    const result=await request(endpoint('https://openlibrary.org/search.json',{q:q.join(' AND '),fields:'key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count,readinglog_count,edition_count,public_scan_b',limit:24,page:state.page,sort:state.sort==='new'||state.view==='latest'?'new':state.sort==='rating'?'rating':'readinglog_count desc'}));
    return (result.docs||[]).map(normalizeOpenLibrary);
  }
  function normalizeTvMaze(item,category){const show=item.show||item;const episodeDate=cleanDate(item.airdate);const date=episodeDate||cleanDate(show.premiered);return {id:`${category}:maze:${show.id}`,type:category,title:show.name||'未命名节目',original:'',year:date?Number(date.slice(0,4)):null,date,score:num(show.rating?.average),votes:0,interest:num(show.weight),image:show.image?.medium||show.image?.original||'',summary:plainHTML(show.summary),source:'TVmaze',url:show.url||`https://www.tvmaze.com/shows/${show.id}`,status:show.status||'',subtype:show.network?.name||show.webChannel?.name||''};}
  async function tvmaze(category){const isVariety=(show)=>{const genres=(show.genres||[]).join(' ').toLowerCase();return /reality|game show|talk show|music/.test(genres)||/reality|talk show|game show/i.test(show.type||'');};let arr;
    if(state.query){const data=await request(endpoint('https://api.tvmaze.com/search/shows',{q:state.query}));arr=(data||[]).map(x=>x.show);}
    else {if(state.year!=='all'&&state.year!==String(CURRENT_YEAR)&&state.view!=='latest')return [];
      const data=await request(endpoint('https://api.tvmaze.com/schedule/web',{date:NOW.toISOString().slice(0,10)}));arr=(data||[]).filter(x=>x.show).map(x=>({...x.show,airdate:x.airdate}));}
    return arr.filter(x=>category==='variety'?isVariety(x):!isVariety(x)).slice(0,35).map(x=>normalizeTvMaze(x,category));
  }
  // Static snapshot is used only when live services returned no matching items.
  async function snapshotFallback(){
    try{
      const response=await fetch('data/weekly.json',{cache:'no-store'});
      if(!response.ok)return [];
      const snapshot=await response.json();
      if(!Array.isArray(snapshot.items)||!snapshot.items.length)return [];
      const items=snapshot.items.map(v=>{
        const type=v.type||({'电影':'movie','电视剧':'tv','综艺':'variety','动漫':'anime','书籍':'book'}[v.category]);
        if(!type)return null;
        return {id:'snapshot:'+v.id,type,title:v.title||'',original:'',year:v.year||null,date:v.date||'',score:type==='book'?num(v.score)*2:num(v.score),votes:0,interest:1,image:v.image||'',summary:v.description||'',source:(v.source||'')+' · 每日快照',url:v.url||'#',status:'',subtype:'',ratingBase:type==='book'?'5':'10'};
      }).filter(Boolean);
      return items.filter(x=>(state.category==='all'||x.type===state.category)&&(state.year==='all'||String(x.year)===state.year)&&(!state.query||x.title.toLowerCase().includes(state.query.toLowerCase()))&&!state.keyword);
    }catch{return [];}
  }
  function activeSources(){if(state.category==='all')return ['movie','tv','anime','variety','book'];return [state.category];}
  async function pullSource(category){if(category==='book')return books();if(category==='anime')return animes();if((category==='movie'||category==='tv'||category==='variety')&&getKey())return moviesOrShows(category);if(category==='tv'||category==='variety')return tvmaze(category);return [];}
  function sorted(items){const arr=[...items];const year=Number(state.year);return arr.sort((a,b)=>{
    if(state.sort==='rating')return b.score-a.score||b.votes-a.votes;
    if(state.sort==='new'||state.view==='latest')return (b.date||'').localeCompare(a.date||'')||b.score-a.score;
    if(state.sort==='hidden')return (b.score*(Math.log10(b.votes+2)+1)/(Math.log10(b.interest+10)+2))-(a.score*(Math.log10(a.votes+2)+1)/(Math.log10(a.interest+10)+2));
    const am=(a.year===year?2:1),bm=(b.year===year?2:1);return bm* Math.log10(b.interest+10) - am*Math.log10(a.interest+10);
  });}
  function unique(items){const seen=new Set();return items.filter(x=>{if(!x||seen.has(x.id))return false;seen.add(x.id);return true;});}
  function cardReason(item){if(state.keyword)return tagSentence[state.keyword]||'探索更多作品';if(item.year===CURRENT_YEAR)return '今年的新发现';if(item.score>=8.5&&item.votes>=15)return '获得较高用户评分';if(item.score>=7.5&&item.votes>=10)return '口碑值得关注';if(item.type==='book')return '打开书籍资料与合法阅读选项';if(state.view==='latest')return '关注近期更新';return item.summary?short(item.summary,46):'点开详情，了解这部作品';}
  function renderCard(item,saved){const itemSaved=!!saved[item.id];const score=item.score?`${item.ratingBase==='5'?(item.score/2).toFixed(1):item.score.toFixed(1)}/${item.ratingBase||'10'}`:'暂无评分';const star=item.score?'★':'—';const origin=item.subtype||item.source;const title=safe(item.title);const image=item.image?`<img loading="lazy" src="${safe(safeUrl(item.image))}" alt="${title} 封面" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" /><div class="cover-fallback" style="display:none">${title}</div>`:`<div class="cover-fallback">${title}</div>`;
    return `<article class="work-card" data-id="${safe(item.id)}"><div class="cover-wrap" data-action="open" title="打开作品资料">${image}<div class="cover-shade"></div><button class="bookmark ${itemSaved?'saved':''}" data-action="save" aria-label="${itemSaved?'移出收藏':'加入收藏'}" title="${itemSaved?'移出收藏':'加入收藏'}">${itemSaved?'♥':'♡'}</button><span class="rating-pill"><span>${star}</span> ${score}</span></div><div class="card-source"><span><span class="source-dot" style="background:${SOURCE_COLOR[item.type]||'#c4f3ae'}"></span>${LABELS[item.type]||''} · ${safe(item.source)}</span><span>${item.year||'—'}</span></div><h4 class="card-title" data-action="open" title="${title}">${title}</h4><div class="card-subtitle">${safe(origin)}${item.status?' <span class="sep">·</span> '+safe(short(item.status,19)):''}</div><div class="card-reason">${safe(cardReason(item))}</div>${state.view==='saved'?`<button class="card-more" data-action="finished">${saved[item.id]?.finished?'✓ 已完成（点此撤销）':'标记为已完成 ✓'}</button>`:''}</article>`;
  }
  function updateSavedCount(){const saved=getSaved();$('#savedCount').textContent=Object.keys(saved).length;}
  function render(){const saved=getSaved();let items=state.view==='saved'?Object.values(saved).map(x=>x.item).filter(Boolean):state.items;
    if(state.view==='saved'){if(state.category!=='all')items=items.filter(x=>x.type===state.category);if(state.year!=='all')items=items.filter(x=>String(x.year)===state.year);if(state.query)items=items.filter(x=>[x.title,x.original,x.subtype].join(' ').toLowerCase().includes(state.query.toLowerCase()));}
    if(state.view==='saved'&&state.sort==='new')items=items.sort((a,b)=>(b.date||'').localeCompare(a.date||''));else items=sorted(items);
    $('#workGrid').innerHTML=items.map(x=>renderCard(x,saved)).join('');$('#resultCount').textContent=items.length?`显示 ${items.length} 部作品`:'暂无匹配作品';$('#emptyState').hidden=items.length>0||state.loading;
    $('#emptyTitle').textContent=state.view==='saved'?'清单里还没有作品':'暂时没有匹配的作品';$('#emptyText').textContent=state.view==='saved'?'浏览作品后点封面右上角的 ♡ 即可收藏，下次仍能找到。':'可以尝试更换年份、关键词或类型。部分类型需要配置 TMDB 密钥。';
    $('#moreButton').hidden=!state.moreAvailable||state.view==='saved'||!items.length;
    updateSavedCount();
  }
  function setTitle(){const heading=state.view==='saved'?'我的私人清单':state.view==='latest'?'最近上新':'探索好作品';const sub=state.view==='saved'?'收藏留在你的浏览器里，随时回来继续看。':state.view==='latest'?'关注近期开播、新番与新书动态。':'从热门新作到高分遗珠，发现属于你的下一部。';$('#sectionTitle').innerHTML=safe(heading)+' <span class="heading-sparkle">✳</span>';$('#sectionSubtitle').textContent=sub;$('#resultsHeading').textContent=state.view==='saved'?'你的收藏':state.view==='latest'?'近期动向':'灵感推荐';$$('[data-page]').forEach(el=>el.classList.toggle('active',el.dataset.page===state.view));$$('[data-mobile]').forEach(el=>el.classList.toggle('active',el.dataset.mobile===state.view));$$('[data-category]').forEach(el=>el.classList.toggle('active',el.dataset.category===state.category));$$('[data-tag]').forEach(el=>el.classList.toggle('active',el.dataset.tag===state.keyword));}
  async function load(append=false){if(state.view==='saved'){state.loading=false;state.moreAvailable=false;setTitle();render();return;}const run=++state.lastRun;state.loading=true;if(!append){state.items=[];$('#workGrid').innerHTML='';$('#loading').hidden=false;$('#emptyState').hidden=true;}$('#notice').hidden=true;$('#moreButton').hidden=true;setTitle();
    const sources=activeSources();const settled=await Promise.allSettled(sources.map(pullSource));if(run!==state.lastRun)return;let fresh=[];const fails=[];const missing=[];settled.forEach((result,i)=>{if(result.status==='fulfilled'){fresh.push(...result.value);if(!result.value.length&&(sources[i]==='movie'&&!getKey()))missing.push('电影需要配置 TMDB');}else fails.push(`${LABELS[sources[i]]}: ${result.reason?.message||'请求失败'}`);});
    let fallbackUsed=false;
    if(!fresh.length&&!append){const backup=await snapshotFallback();if(run!==state.lastRun)return;if(backup.length){fresh=backup;fallbackUsed=true;}}
    state.items=unique(append?[...state.items,...fresh]:fresh);state.loading=false;state.moreAvailable=!fallbackUsed&&fresh.length>=19;$('#loading').hidden=true;
    const notes=[];if(fallbackUsed)notes.push('当前展示最近一次自动更新的资料快照，并非实时数据；更新时间可在每周订阅文件中查看。');if(!getKey()&&['all','movie','tv','variety'].includes(state.category))notes.push('尚未接入 TMDB：电影、年度电视剧与综艺数据不完整。点击右上角 ⚙ 配置密钥后即可使用完整筛选。');
    if(fails.length)notes.push('部分数据源暂不可用：'+fails.join('；')+'。可稍后重试。');if(notes.length){$('#notice').hidden=false;$('#notice').textContent=notes.join(' ');}$('#dataMode').textContent=fallbackUsed?'自动更新快照':fails.length?'部分接口暂不可用':'实时公开数据';render();
  }
  function resetAndLoad(){state.page=1;state.moreAvailable=false;load();}
  function changeCategory(c){state.category=c;setTitle();resetAndLoad();}
  function changeView(v){state.view=v;if(v==='latest'||v==='saved')state.year='all';else if(v==='explore'&&state.year==='all')state.year=String(CURRENT_YEAR);if(v==='saved')state.category='all';$('#yearSelect').value=state.year;setTitle();resetAndLoad();document.querySelector('#discoverySection').scrollIntoView({behavior:'smooth',block:'start'});}
  function openSettings(){const modal=$('#settingsModal');modal.hidden=false;$('#tmdbKey').value=getKey();$('#tmdbKey').focus();}
  function closeSettings(){$('#settingsModal').hidden=true;}
  function updateFavorite(id){let target=state.items.find(x=>x.id===id);const saved=getSaved();if(saved[id]){delete saved[id];toast('已从清单移除');}else{target=target||saved[id]?.item;if(!target)return;saved[id]={item:target,finished:false,savedAt:new Date().toISOString()};toast('已加入我的清单 ♡');}saveList(saved);render();}
  function markFinished(id){const saved=getSaved();if(!saved[id])return;saved[id].finished=!saved[id].finished;saveList(saved);toast(saved[id].finished?'已标记完成':'已恢复为待看 / 待读');render();}
  function searchChange(){const q=$('#searchInput').value.trim();$('#clearSearch').hidden=!q;state.query=q;resetAndLoad();}
  let searchTimer;
  function init(){
    const years=['all'];for(let y=CURRENT_YEAR+1;y>=1950;y--)years.push(String(y));$('#yearSelect').innerHTML=years.map(y=>`<option value="${y}">${y==='all'?'全部年份':y+' 年'}</option>`).join('');$('#yearSelect').value=state.year;
    $('#headerDate').textContent=new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'long',day:'numeric'}).format(NOW);
    $('#sideNav').addEventListener('click',e=>{const btn=e.target.closest('[data-page]');if(btn)changeView(btn.dataset.page);});
    $$('#categoryNav button,#categoryTabs button').forEach(btn=>btn.addEventListener('click',()=>changeCategory(btn.dataset.category)));
    $$('.bottom-nav button').forEach(btn=>btn.addEventListener('click',()=>btn.dataset.mobile==='settings'?openSettings():changeView(btn.dataset.mobile)));
    $('#yearSelect').addEventListener('change',e=>{state.year=e.target.value;resetAndLoad();});
    $('#sortSelect').addEventListener('change',e=>{state.sort=e.target.value;resetAndLoad();});
    $('#keywordChips').addEventListener('click',e=>{const el=e.target.closest('[data-tag]');if(!el)return;state.keyword=el.dataset.tag;setTitle();resetAndLoad();});
    $('#searchInput').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(searchChange,430);});
    $('#searchInput').addEventListener('keydown',e=>{if(e.key==='Enter'){clearTimeout(searchTimer);searchChange();}});
    $('#clearSearch').addEventListener('click',()=>{$('#searchInput').value='';searchChange();});
    $('#heroExplore').addEventListener('click',()=>changeView('explore'));
    $('#heroLatest').addEventListener('click',()=>changeView('latest'));
    $('#refreshButton').addEventListener('click',resetAndLoad);
    $('#moreButton').addEventListener('click',()=>{state.page++;load(true);});
    $('#resetButton').addEventListener('click',()=>{state.category='all';state.year=String(CURRENT_YEAR);state.keyword='';state.query='';$('#searchInput').value='';$('#yearSelect').value=state.year;setTitle();resetAndLoad();});
    $('#workGrid').addEventListener('click',e=>{const action=e.target.closest('[data-action]');if(!action)return;const id=action.closest('[data-id]')?.dataset.id;if(!id)return;if(action.dataset.action==='save')return updateFavorite(id);if(action.dataset.action==='finished')return markFinished(id);const item=state.items.find(x=>x.id===id)||getSaved()[id]?.item;if(item)window.open(safeUrl(item.url),'_blank','noopener,noreferrer');});
    $('#guideButton').addEventListener('click',()=>toast('数据源：TMDB / Jikan / Open Library / TVmaze；评分来自各数据源，不能直接当作同一平台评分。'));
    ['#settingsButton','#headerSettings'].forEach(sel=>$(sel).addEventListener('click',openSettings));$('#modalClose').addEventListener('click',closeSettings);
    $('#settingsModal').addEventListener('click',e=>{if(e.target===$('#settingsModal'))closeSettings();});document.addEventListener('keydown',e=>{if(e.key==='Escape')closeSettings();});
    $('#saveSettings').addEventListener('click',()=>{const key=$('#tmdbKey').value.trim();try{if(key)localStorage.setItem(STORE.key,key);else localStorage.removeItem(STORE.key);}catch{toast('浏览器不允许保存密钥');return;}closeSettings();toast('设置已保存，开始重新加载');resetAndLoad();});
    $('#clearKey').addEventListener('click',()=>{$('#tmdbKey').value='';try{localStorage.removeItem(STORE.key);}catch{}toast('密钥已清除');});
    $('#avatarButton').addEventListener('click',e=>{e.preventDefault();changeView('saved');});
    const hash=location.hash.replace('#','');if(hash==='saved'||hash==='latest')state.view=hash;updateSavedCount();setTitle();load();
  }

  // v0.2: all live sources are isolated, with typed backup providers.
  // Prefer fast failure and a second provider over an 11.5s single-provider wait.
  request = async function(url,options={}){
    const {timeoutMs=6800,...opts}=options;
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),timeoutMs);
    try {
      const response=await fetch(url,{...opts,signal:controller.signal,headers:{Accept:'application/json',...(opts.headers||{})}});
      if(!response.ok)throw new Error('HTTP '+response.status+(response.status===429?'（请求频率限制）':''));
      return await response.json();
    } catch(error){
      if(error.name==='AbortError')throw new Error('请求超时');
      if(error instanceof TypeError)throw new Error('网络连接失败或跨域限制');
      throw error;
    } finally {clearTimeout(timeout);}
  };
  const originalAnimes=animes;
  animes=async function(){
    try{return await originalAnimes();}
    catch(primary){
      const season=['WINTER','SPRING','SUMMER','FALL'][Math.floor(NOW.getMonth()/3)];
      const latest=state.view==='latest'&&!state.query;
      const query='query($page:Int,$search:String,$greater:FuzzyDateInt,$lesser:FuzzyDateInt,$season:MediaSeason,$seasonYear:Int,$genre:String,$sort:[MediaSort]){Page(page:$page,perPage:24){media(type:ANIME,isAdult:false,search:$search,startDate_greater:$greater,startDate_lesser:$lesser,season:$season,seasonYear:$seasonYear,genre:$genre,sort:$sort){id title{english romaji native} startDate{year month day} averageScore popularity description(asHtml:false) coverImage{large} siteUrl format status}}}';
      const genres={'悬疑':'Mystery','科幻':'Sci-Fi','治愈':'Slice of Life','喜剧':'Comedy','动作':'Action','爱情':'Romance','历史':'Historical'};
      const vars={page:state.page,search:state.query||null,greater:!latest&&state.year!=='all'?Number(state.year)*10000:null,lesser:!latest&&state.year!=='all'?(Number(state.year)+1)*10000:null,season:latest?season:null,seasonYear:latest?CURRENT_YEAR:null,genre:genres[state.keyword]||null,sort:[state.sort==='rating'?'SCORE_DESC':state.sort==='new'||latest?'START_DATE_DESC':'POPULARITY_DESC']};
      try{
        const result=await request('https://graphql.anilist.co',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({query,variables:vars}),timeoutMs:7600});
        if(result.errors?.length)throw new Error('AniList 查询失败');
        return (result.data?.Page?.media||[]).map(v=>{
          const d=v.startDate||{},date=d.year&&d.month&&d.day?[d.year,String(d.month).padStart(2,'0'),String(d.day).padStart(2,'0')].join('-'):'';
          return {id:'anime:anilist:'+v.id,type:'anime',title:v.title?.english||v.title?.romaji||v.title?.native||'未命名动漫',original:v.title?.native||'',year:d.year||null,date,score:num(v.averageScore)/10,votes:0,interest:num(v.popularity),image:v.coverImage?.large||'',summary:plainHTML(v.description||''),source:'AniList',url:v.siteUrl||'https://anilist.co/anime/'+v.id,status:v.status||'',subtype:v.format||''};
        }).filter(v=>v.title);
      } catch(secondary){throw new Error('Jikan：'+primary.message+'；AniList：'+secondary.message);}
    }
  };
  const originalBooks=books;
  books=async function(){
    try{return await originalBooks();}
    catch(primary){
      try{
        const lookup=state.query||('subject:'+(GENRES[state.keyword]?.book||'fiction'));
        const result=await request(endpoint('https://www.googleapis.com/books/v1/volumes',{q:lookup,maxResults:35,startIndex:(state.page-1)*35,orderBy:state.year!=='all'||state.view==='latest'||state.sort==='new'?'newest':'relevance',printType:'books'}),{timeoutMs:7600});
        const year=state.view==='latest'?CURRENT_YEAR:state.year;
        return (result.items||[]).map(v=>{
          const info=v.volumeInfo||{},published=String(info.publishedDate||''),date=/^\d{4}-\d{2}-\d{2}/.test(published)?published.slice(0,10):/^\d{4}$/.test(published)?published+'-01-01':'';
          const rating=num(info.averageRating);
          return {id:'book:google:'+v.id,type:'book',title:info.title||'未命名图书',original:'',year:date?Number(date.slice(0,4)):null,date,score:rating*2,votes:num(info.ratingsCount),interest:num(info.ratingsCount),image:(info.imageLinks?.thumbnail||info.imageLinks?.smallThumbnail||'').replace(/^http:/,'https:'),summary:plainHTML(info.description||''),source:'Google Books',url:info.infoLink||'https://books.google.com/books?id='+encodeURIComponent(v.id),status:'',subtype:(info.authors||[]).slice(0,2).join(' · '),ratingBase:rating?'5':'10'};
        }).filter(v=>year==='all'||v.year===Number(year)).slice(0,24);
      } catch(secondary){throw new Error('Open Library：'+primary.message+'；Google Books：'+secondary.message);}
    }
  };
  const sourceBeforeCache=pullSource,cachePrefix='zhigeng_source_v2:';
  function sourceCacheKey(category){
    return cachePrefix+encodeURIComponent(JSON.stringify({category,view:state.view,year:state.year,sort:state.sort,keyword:state.keyword,query:state.query,page:state.page}));
  }
  pullSource=async function(category){
    try{
      const items=await sourceBeforeCache(category);
      if(items.length){try{localStorage.setItem(sourceCacheKey(category),JSON.stringify({at:Date.now(),items:items.slice(0,30)}));}catch{}}
      return items;
    } catch(error){
      try{
        const cache=JSON.parse(localStorage.getItem(sourceCacheKey(category))||'null');
        if(cache&&cache.items?.length&&Date.now()-cache.at<7*86400000){
          (state.cachedLabels||(state.cachedLabels=[])).push(LABELS[category]);
          return cache.items;
        }
      }catch{}
      throw error;
    }
  };
  const renderBeforeCache=render;
  render=function(){
    renderBeforeCache();
    if(state.view!=='saved'&&state.cachedLabels?.length){
      const message=state.cachedLabels.join('、')+'已使用历史缓存，并非实时结果。可稍后刷新。';
      const notice=$('#notice');
      if(!notice.textContent.includes(message))notice.textContent+=(notice.textContent?' ':'')+message;
      notice.hidden=false;
      $('#dataMode').textContent='包含缓存数据';
    }
  };
  const loadBeforeCache=load;
  load=function(...args){state.cachedLabels=[];return loadBeforeCache(...args);};

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();