/* 知更网站 0.6.4 功能扩展：复用 Android 编辑数据，独立榜单与网页版 AI 入口。 */
(() => {
  'use strict';
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const labels = {all:'全部',movie:'电影',tv:'电视剧',anime:'动漫',variety:'综艺',book:'书籍'};
  const state = {domestic:[],type:'all',rankType:'movie',rankSource:'yearly',rankYear:String(new Date().getFullYear()),rankRegion:'CN',rankSort:'rating',rankPage:1,rankHasMore:false,rankNote:'',rankingItems:[],chat:[],controller:null,rankSeq:0,tab:'duck',fccFingerprint:'',draft:''};
  const storeKey = 'zhigeng_saved_v1';
  const keyName = 'zhigeng_tmdb_credential_v1';

  function escapeHTML(input) {
    return String(input == null ? '' : input).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  }
  function safeHttp(link) {
    try { const url = new URL(String(link)); return url.protocol === 'https:' ? url.href : '#'; } catch { return '#'; }
  }
  // 仅接受媒体数据源的 HTTPS 图片，不把任意地址注入海报容器。
  function safePoster(value) {
    try {
      const url=new URL(String(value||''));
      return url.protocol==='https:'&&[
        'image.tmdb.org','s4.anilist.co','s3.anilist.co','covers.openlibrary.org','upload.wikimedia.org'
      ].includes(url.hostname)?url.href:'';
    } catch {return '';}
  }
  function readSaved() {
    try { const value = JSON.parse(localStorage.getItem(storeKey) || '{}'); return value && typeof value === 'object' && !Array.isArray(value) ? value : {}; }
    catch { return {}; }
  }
  function normalized(item) {
    return {id:String(item.id),type:item.type,title:item.title,original:'',subtype:item.author||'',year:String(item.year||''),date:'',source:item.source||'知更编辑推荐',url:item.url||'',image:item.image||'',score:(Number(item.score)||0)*(item.scoreLabel==='Open Library /5'?2:1),votes:Number(item.votes)||0,interest:Number(item.interest)||1,summary:item.summary||'',ratingBase:item.scoreLabel==='Open Library /5'?'5':'10'};
  }
  function toggleFavorite(item) {
    const saved = readSaved();
    if (saved[item.id]) delete saved[item.id];
    else saved[item.id] = {item:normalized(item),finished:false,savedAt:new Date().toISOString()};
    try { localStorage.setItem(storeKey,JSON.stringify(saved)); }
    catch { alert('浏览器存储不可用，收藏无法保存。'); return; }
    const count = $('#savedCount');
    if (count) count.textContent = String(Object.keys(saved).length);
    renderDomestic();
    if ($('#zgPanel').dataset.page === 'rankings') renderRankingList();
  }
  function typeFilters(selector, current, action) {
    return '<div class="zg-chips" role="group" aria-label="内容分类">' +
      ['all','movie','tv','anime','variety','book'].map(type =>
        '<button type="button" data-' + action + '="' + type + '" class="zg-chip ' + (current === type ? 'is-active':'') + '">' + labels[type] + '</button>'
      ).join('') + '</div>';
  }
  function card(item, index, ranked) {
    const saved = !!readSaved()[item.id];
    const type = labels[item.type] || item.type;
    const annualLabel=String(item.scoreLabel||'');
    const showScore=Number(item.score)>0&&(item.annual ? (item.type==='book'?Number(item.votes)>=10:item.type==='anime'||(Number(item.votes)>=10)) : Number(item.votes)>=50);
    const score=showScore ?
      '<span class="zg-rating">★ ' + Number(item.score).toFixed(1) + (annualLabel==='Open Library /5'?'/5':'/10') +
      ' <small>· ' + escapeHTML(annualLabel||'评分') + (item.type==='movie'||item.type==='tv'||item.type==='variety'?' · '+Number(item.votes).toLocaleString('zh-CN')+' 票':'') + '</small></span>' :
      '<span class="zg-editor-label">' + (item.annual?'暂无可靠评分':'编辑甄选') + '</span>';
    const author = item.author || item.subtype || '';
    const source = item.source || '知更编辑推荐';
    const poster=safePoster(item.image);
    const posterMarkup=poster?'<img class="zg-poster" src="'+escapeHTML(poster)+'" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer" />':'';
    return '<article class="zg-work" data-kind="' + escapeHTML(item.type) + '">' +
      '<div class="zg-art zg-art-' + escapeHTML(item.type) + '" aria-hidden="true">'+posterMarkup+'<span class="zg-art-number">' + String(index + 1).padStart(2,'0') + '</span>' +
      '<div class="zg-art-title">' + escapeHTML(item.title) + '</div><small>ZHIGENG · ' + escapeHTML(type) + '</small></div>' +
      '<div class="zg-work-copy"><div class="zg-work-meta">' + (ranked ? '<b class="zg-rank">NO. ' + String(item.rank || index + 1).padStart(2,'0') + '</b>' : '') +
      '<span>' + escapeHTML(type) + '</span><span>' + escapeHTML(item.year || '经典作品') + '</span></div>' +
      '<h3>' + escapeHTML(item.title) + '</h3><p class="zg-byline">' + escapeHTML(author) + ' · ' + escapeHTML(item.tag || source) + '</p>' +
      '<p class="zg-summary">' + escapeHTML(item.summary || '了解作品详情与来源。') + '</p><div class="zg-work-foot">' + score +
      '<div><button type="button" class="zg-save ' + (saved ? 'is-saved':'') + '" data-zg-save="' + escapeHTML(item.id) + '" aria-label="' + (saved ? '移出收藏':'加入收藏') + '">' + (saved ? '♥ 已收藏':'♡ 收藏') + '</button>' +
      '<a href="' + escapeHTML(safeHttp(item.url)) + '" target="_blank" rel="noopener noreferrer" aria-label="查看' + escapeHTML(item.title) + '的资料来源">作品资料 ↗</a>' +
      (item.type !== 'book' ? '<button type="button" class="zg-kanju-lookup" data-zg-kanju-title="' + escapeHTML(item.title) + '">看剧AI 找片 ↗</button>' : '') +
      '</div></div></div></article>';
  }
  function renderDomestic() {
    const list = state.domestic.filter(item => state.type === 'all' || item.type === state.type);
    $('#zgDomesticFilters').innerHTML = typeFilters('',state.type,'zg-type');
    $('#zgDomesticGrid').innerHTML = list.map((item,i) => card(item,i,false)).join('');
    $('#zgDomesticCount').textContent = '来自安卓同一份编辑目录 · ' + list.length + ' 部';
  }
  function renderRankingList() {
    const editor=state.rankSource==='editor', annual=state.rankSource==='yearly';
    const items=editor?state.domestic.filter(x=>state.rankType==='all'||x.type===state.rankType):state.rankingItems;
    const target=$('#zgRankingList');
    if(!target)return;
    target.innerHTML=items.length?items.map((x,i)=>card(x,i,!(annual&&x.editorialFallback))).join(''):
      '<div class="zg-empty">这个年份和分类暂无可显示的真实记录。可换个年份、类型或数据来源。'+
      (annual&&['movie','tv','variety'].includes(state.rankType)&&!localStorage.getItem(keyName)?
      ' <button class="zg-rank-setup" id="zgRankSetup" type="button">配置 TMDB 密钥 ↗</button>':'')+'</div>';
    $('#zgRankingCount').textContent=editor?'知更人工精选排序 · 不是实时年度榜 · 共 '+items.length+' 部':
      annual?state.rankNote:'TMDB 中国来源用户评分总榜 · 至少50票 · '+items.length+' 部';
    $('#zgRankingFilters').innerHTML=typeFilters('',state.rankType,'zg-rank-type');
    const all=$('#zgRankingFilters [data-zg-rank-type="all"]');
    if(all)all.disabled=annual;
    $$('#zgRankingFilters [data-zg-rank-type="anime"],#zgRankingFilters [data-zg-rank-type="book"]').forEach(el=>{
      el.disabled=state.rankSource==='tmdb';
    });
    const yearControls=$('#zgYearControls');
    if(yearControls)yearControls.hidden=!annual;
    const pageControls=$('#zgRankPager');
    if(pageControls)pageControls.hidden=!annual;
    if(annual) {
      $('#zgRankYear').value=state.rankYear;
      $('#zgRankRegion').value=state.rankRegion;
      $('#zgRankSort').value=state.rankSort;
      $('#zgRankPageLabel').textContent='第 '+state.rankPage+' 页';
      $('#zgRankPrev').disabled=state.rankLoading||state.rankPage<=1;
      $('#zgRankNext').disabled=state.rankLoading||!state.rankHasMore;
    }
    $$('[data-zg-rank-source]').forEach(el=>el.classList.toggle('is-active',el.dataset.zgRankSource===state.rankSource));
  }
  async function loadAnnual() {
    const requestId=++state.rankSeq;
    state.rankLoading=true;state.rankHasMore=false;state.rankingItems=[];
    state.rankNote='正在获取 '+state.rankYear+' 年 · '+(labels[state.rankType]||'')+' 的榜单…';
    renderRankingList();
    try {
      const ranker=window.ZhigengAnnualRankings;
      if(!ranker)throw Error('年度排行模块尚未加载，请刷新页面');
      const value=await ranker.load({
        type:state.rankType,year:state.rankYear,region:state.rankRegion,
        sort:state.rankSort,page:state.rankPage,
        credential:localStorage.getItem(keyName)||'',
        fetcher:window.ZhigengTransport?.fetch||fetch
      });
      if(requestId!==state.rankSeq||$('#zgPanel')?.dataset.page!=='rankings')return;
      state.rankingItems=value.items||[];
      state.rankHasMore=!!value.hasMore;
      state.rankNote=value.note||'年度目录';
      if(state.rankRegion==='CN'&&['movie','tv','variety'].includes(state.rankType))state.rankNote+=' · 数据源并非作品全集；切换「热度」可查看更多低票数新作。';
      if(value.needsTmdb)state.rankNote+=' 国产影视需配置 TMDB API 凭证；国产动画和中文书可用公开数据。国内编辑精选不是全国年度全集。';
    } catch(error) {
      if(requestId!==state.rankSeq||$('#zgPanel')?.dataset.page!=='rankings')return;
      state.rankingItems=[];state.rankHasMore=false;
      state.rankNote='年度榜单读取失败：'+(error?.message||'网络不可用')+'。不会把其他年份或缓存误当实时排行。';
    } finally {
      if(requestId===state.rankSeq) {state.rankLoading=false;renderRankingList();}
    }
  }
  function changeAnnualFilter() {
    state.rankPage=1;
    if(state.rankSource==='yearly')loadAnnual();
    else loadRanking();
  }
  async function loadRanking() {
    const requestId = ++state.rankSeq;
    if (state.rankSource === 'yearly') {await loadAnnual(); return;}
    if (state.rankSource === 'editor') { renderRankingList(); return; }
    const raw = localStorage.getItem(keyName) || '';
    const auth = raw.trim();
    if (!/^[a-f0-9]{32}$/i.test(auth) && !/^eyJ[A-Za-z0-9._-]{30,}$/.test(auth)) {
      state.rankingItems = [];
      renderRankingList();
      $('#zgRankingCount').textContent = 'TMDB 真实评分榜需要先在「接口与偏好设置」验证并保存 TMDB 密钥。';
      return;
    }
    const kind = ['movie','tv','variety'].includes(state.rankType) ? state.rankType : 'movie';
    const route = kind === 'movie' ? 'movie' : 'tv';
    const endpoint = new URL('https://api.themoviedb.org/3/discover/' + route);
    endpoint.searchParams.set('language','zh-CN');
    endpoint.searchParams.set('include_adult','false');
    endpoint.searchParams.set('with_origin_country','CN');
    endpoint.searchParams.set('sort_by','vote_average.desc');
    endpoint.searchParams.set('vote_count.gte','50');
    endpoint.searchParams.set('page','1');
    if (kind === 'variety') endpoint.searchParams.set('with_genres','10764|10767');
    endpoint.searchParams.set(route === 'movie' ? 'primary_release_date.lte' : 'first_air_date.lte',new Date().toISOString().slice(0,10));
    const headers = {Accept:'application/json'};
    if (/^[a-f0-9]{32}$/i.test(auth)) endpoint.searchParams.set('api_key',auth);
    else headers.Authorization = 'Bearer ' + auth;
    $('#zgRankingCount').textContent = '正在读取 TMDB 榜单…';
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(),16000);
      let response;
      try { response = await (window.ZhigengTransport?.fetch || fetch)(endpoint.href,{headers,signal:controller.signal}); }
      finally { clearTimeout(timer); }
      if (!response.ok) throw new Error(response.status === 401 ? 'TMDB 凭证未通过验证' : 'TMDB 请求失败（HTTP ' + response.status + '）');
      const json = await response.json();
      if (requestId !== state.rankSeq) return;
      state.rankingItems = (Array.isArray(json.results) ? json.results : []).filter(x=>Number(x.vote_count)>=50).map(x=>({
        id:'tmdb:' + route + ':' + x.id, type:kind, title:x.title||x.name||'未命名作品',
        year:String(x.release_date||x.first_air_date||'').slice(0,4), author:'TMDB 用户评分',
        tag:'不少于 50 位用户评分', summary:x.overview||'',source:'TMDB',
        url:'https://www.themoviedb.org/' + route + '/' + x.id,
        score:Number(x.vote_average)||0,votes:Number(x.vote_count)||0,
        image:x.poster_path?'https://image.tmdb.org/t/p/w342'+x.poster_path:''
      }));
      renderRankingList();
    } catch (error) {
      if (requestId !== state.rankSeq) return;
      state.rankingItems = []; renderRankingList();
      $('#zgRankingCount').textContent = '暂时无法读取 TMDB：' + (error.name === 'AbortError' ? '请求超时' : error.message) + '。不会以编辑榜伪装实时榜单。';
    }
  }

  function hideLegacy(hidden) {
    $$('.hero,.stats-row,#discoverySection,.newsletter-card,#zgDomestic').forEach(el => { el.hidden = hidden; });
  }
  function showPage(page) {
    const panel = $('#zgPanel');
    const inPanel = page === 'rankings' || page === 'assistant' || page === 'kanju' || page === 'sms' || page === 'nodes' || page === 'memberships';
    panel.hidden = !inPanel;
    panel.dataset.page = inPanel ? page : '';
    hideLegacy(inPanel);
    if(page!=='rankings') ++state.rankSeq;
    $$('.zg-side-link,.zg-mobile-link,.zg-ai-header').forEach(a => a.classList.toggle('is-active',a.getAttribute('href') === '#' + page));
    if (page === 'rankings') {
      panel.innerHTML = '<div class="zg-section-top"><span>ANNUAL DISCOVERY · 1900—' + new Date().getFullYear() + '</span><h1>每一年的好作品排行榜</h1><p>默认展示内地／香港／台湾的年度作品。可切换年份、类型、口碑与热度；含真实封面、数据来源和分页，不把编辑清单冒充全国完整排名。</p></div>' +
        '<div class="zg-ranking-toolbar"><div class="zg-toggle"><button data-zg-rank-source="yearly" type="button">每年排行榜</button><button data-zg-rank-source="editor" type="button">知更编辑精选</button><button data-zg-rank-source="tmdb" type="button">TMDB 综合评分榜</button></div><button type="button" class="zg-return" data-zg-return>返回探索 ↗</button></div>' +
        '<div id="zgYearControls" class="zg-year-controls" aria-label="年度排行榜筛选"><label>年份<select id="zgRankYear" aria-label="选择排行榜年份">' +
        Array.from({length:new Date().getFullYear()-1899},(_,i)=>new Date().getFullYear()-i).map(y=>'<option value="'+y+'">'+y+' 年'+(y===new Date().getFullYear()?' · 进行中':'')+'</option>').join('') +
        '</select></label><label>作品地区<select id="zgRankRegion" aria-label="选择作品地区"><option value="global">全球 / 所有语言</option><option value="CN">内地＋香港＋台湾</option></select></label>' +
        '<label>排行依据<select id="zgRankSort" aria-label="选择排行依据"><option value="rating">口碑评分</option><option value="popular">热度 / 关注度</option></select></label></div>' +
        '<div id="zgRankingFilters"></div><p class="zg-data-note" id="zgRankingCount" role="status" aria-live="polite"></p><div id="zgRankingList" class="zg-work-grid"></div>' +
        '<div id="zgRankPager" class="zg-rank-pager"><button type="button" id="zgRankPrev" data-zg-page="prev">← 上一页</button><span id="zgRankPageLabel">第 1 页</span><button type="button" id="zgRankNext" data-zg-page="next">下一页 →</button></div>';
      loadRanking();
    } else if (page === 'assistant') {
      panel.innerHTML = assistantMarkup();
      renderAssistantTab();
    } else if (page === 'kanju') {
      window.ZhigengKanju?.mount(panel);
    } else if (page === 'sms') {
      window.ZhigengSmsHub?.mount(panel);
    } else if (page === 'nodes') {
      window.ZhigengNodes?.mount(panel);
    } else if (page === 'memberships') {
      window.ZhigengMemberships?.mount(panel);
    } else panel.replaceChildren();
    if (inPanel) window.scrollTo({top:0,behavior:'smooth'});
  }
  function navigate(route) {
    if (route === 'library') {
      showPage(''); history.replaceState(null,'',location.pathname + location.search + '#book');
      const button = $('#categoryNav [data-category="book"]');
      if (button) button.click();
      $('#discoverySection')?.scrollIntoView({behavior:'smooth'});
      return;
    }
    if (route === 'domestic') {
      showPage(''); $('#zgDomestic')?.scrollIntoView({behavior:'smooth'});
      return;
    }
    if (route === 'assistant' || route === 'rankings' || route === 'kanju' || route === 'sms' || route === 'nodes' || route === 'memberships') showPage(route);
    else showPage('');
  }
  function assistantMarkup() {
    return '<div class="zg-section-top"><span>YOUR PERSONAL COMPANION</span><h1>✦ 知更 AI 助手</h1><p>帮你挑选、比较、规划下一部想看的作品。只有你主动提交的内容才会发送给所选服务。</p></div>' +
      '<div class="zg-ranking-toolbar"><div class="zg-toggle"><button type="button" data-zg-ai-tab="duck">Duck.ai 官方聊天</button><button type="button" data-zg-ai-tab="fcc">FCC 多模型</button></div><button class="zg-return" data-zg-return type="button">返回探索 ↗</button></div>' +
      '<div id="zgAssistantBody"></div>';
  }
  function renderAssistantTab() {
    $$('[data-zg-ai-tab]').forEach(el => el.classList.toggle('is-active',el.dataset.zgAiTab === state.tab));
    const body = $('#zgAssistantBody');
    if (!body) return;
    if (state.tab === 'duck') {
      body.innerHTML = '<div class="zg-duck-intro"><div><h2>Duck.ai</h2><p>官方网页体验。部分浏览器会因对方网站的安全策略拒绝内嵌，此时请通过官方入口打开；本站不绕过这些限制。</p></div>' +
        '<div class="zg-duck-actions"><button type="button" id="zgLoadDuck" class="zg-primary">在知更内尝试加载</button><a href="https://duck.ai/" target="_blank" rel="noopener noreferrer">打开 Duck.ai 官方网页 ↗</a></div></div>' +
        '<div class="zg-idea-chips"><span>提问灵感</span><button data-zg-prompt="想找一部温暖治愈的华语电影，有哪些推荐？">华语电影推荐</button><button data-zg-prompt="帮我制定一个一个月的经典阅读清单。">阅读计划</button><button data-zg-prompt="我喜欢悬疑推理，有哪些值得补的国产剧？">国产剧推荐</button></div>' +
        '<p id="zgPromptNotice" class="zg-data-note">点问题模板可复制，再由你决定是否粘贴并发送。</p><div id="zgDuckFrame" class="zg-duck-frame"><div class="zg-frame-placeholder">✦<br />点击上方按钮尝试载入官方聊天界面</div></div>';
    } else {
      body.innerHTML = '<div class="zg-fcc-layout"><div class="zg-fcc-config"><h2>连接自己的 FCC 网关</h2><p>需要你自己的 HTTPS Free Claude Code 兼容网关。网页直接请求网关，必须允许该网站的跨域访问（CORS）。</p>' +
        '<label>HTTPS 网关地址<input id="zgFccURL" type="url" placeholder="https://your-gateway.example" autocomplete="off" /></label>' +
        '<label>Proxy Token（如需要）<input id="zgFccToken" type="password" autocomplete="off" placeholder="仅保留在当前页面内存" /></label>' +
        '<button type="button" class="zg-primary" id="zgFccConnect">读取可用模型 ↗</button>' +
        '<label>当前模型<select id="zgFccModel"><option value="">请先连接网关</option></select></label>' +
        '<p id="zgFccStatus" role="status" aria-live="polite">不会保存 Token、对话或账号数据，也不会上传知更书架。</p>' +
        '<a href="https://github.com/nmm26301-maker/zhi-geng" target="_blank" rel="noopener noreferrer" class="zg-config-hint">知更项目与接入说明 ↗</a></div>' +
        '<div class="zg-fcc-chat"><div class="zg-chat-header"><strong>FCC 对话</strong><div><button type="button" id="zgFccClear">新建对话</button><button type="button" id="zgFccStop" disabled>停止</button></div></div>' +
        '<div id="zgFccMessages" class="zg-messages" aria-live="polite"><div class="zg-chat-empty">连接后输入问题，与实际网关返回的模型对话。</div></div>' +
        '<div class="zg-chat-compose"><textarea id="zgFccDraft" rows="3" placeholder="输入问题…仅发送当前对话，不会读取书架或搜索记录。"></textarea>' +
        '<button type="button" id="zgFccSend" class="zg-primary">发送 ↗</button></div></div></div>';
      renderMessages();
      $('#zgFccDraft').value = state.draft;
      $('#zgFccDraft').addEventListener('input',e => {state.draft=e.target.value;});
    }
  }
  function showFccStatus(message) {
    const el = $('#zgFccStatus');
    if (el) el.textContent = message;
  }
  function gatewayURL() {
    const input = $('#zgFccURL')?.value.trim();
    if (!input || input.length > 350) throw new Error('请输入有效的 HTTPS 网关地址。');
    const url = new URL(input);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('只能填写没有账号信息或查询参数的 HTTPS 网关地址。');
    if (url.hostname === 'localhost' || /\.local$/i.test(url.hostname) || /^(127\.|10\.|192\.168\.|169\.254\.)/.test(url.hostname)) throw new Error('请使用可通过公网 HTTPS 访问的网关域名。');
    return url.href.replace(/\/+$/,'');
  }
  function apiURL(endpoint) {
    const base = gatewayURL();
    return base.replace(/\/v1$/,'') + '/v1/' + endpoint;
  }
  function friendlyError(err) {
    if (err?.name === 'AbortError') return '请求已停止或超时。';
    if (err instanceof TypeError) return '网络请求被浏览器阻止，可能是 CORS、证书或网络问题。';
    return String(err?.message || err || '未知错误');
  }
  async function connectModels() {
    if (state.controller) return;
    let endpoint;
    try { endpoint = apiURL('models?view=messages'); } catch (error) { showFccStatus(friendlyError(error)); return; }
    const ctl = new AbortController();
    state.controller = ctl;
    showFccStatus('正在读取网关公布的模型列表…');
    const headers = {Accept:'application/json'};
    const token = $('#zgFccToken')?.value.trim();
    const fingerprint = gatewayURL() + '#' + (token || '');
    if (fingerprint !== state.fccFingerprint) state.chat = [];
    if (token) headers.Authorization = 'Bearer ' + token;
    try {
      const timeout = setTimeout(()=>ctl.abort(),20000);
      let response;
      try { response = await fetch(endpoint,{headers,signal:ctl.signal,cache:'no-store'}); } finally {clearTimeout(timeout);}
      if (!response.ok) throw new Error('模型目录 HTTP ' + response.status + (response.status===401 ? '：请检查 Proxy Token' : ''));
      const data = await response.json();
      const models = Array.isArray(data.data) ? data.data : Array.isArray(data.models) ? data.models : [];
      const select = $('#zgFccModel');
      select.replaceChildren();
      const option = new Option('选择模型','');select.add(option);
      const seen = new Set();
      for (const model of models) {
        const id = String(typeof model === 'string' ? model : model?.id || model?.model || '').trim();
        if (!id || seen.has(id)) continue;
        seen.add(id);select.add(new Option(String(model?.display_name||model?.name||id),id));
      }
      if (select.options.length > 1) select.selectedIndex = 1;
      state.fccFingerprint = fingerprint;
      showFccStatus('已读取 ' + seen.size + ' 个模型。模型能否使用，以网关的实际返回为准。');
    } catch (error) { showFccStatus(friendlyError(error)); }
    finally {state.controller = null;}
  }
  function renderMessages(liveReply = '') {
    const el = $('#zgFccMessages');
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 96;
    el.replaceChildren();
    const messages = state.chat.slice(-24);
    if (!messages.length && !liveReply) {
      const empty = document.createElement('div');empty.className = 'zg-chat-empty';
      empty.textContent = '你的聊天只在当前标签页内。请先连接模型，再输入问题。';el.append(empty);
    }
    for (const message of messages) {
      const node = document.createElement('div');node.className='zg-message ' + (message.role === 'user' ? 'zg-user':'zg-assistant');
      node.textContent = message.content;el.append(node);
    }
    if (liveReply) {
      const node = document.createElement('div');node.className='zg-message zg-assistant';
      node.textContent = liveReply;el.append(node);
    }
    if (nearBottom) el.scrollTop = el.scrollHeight;
  }
  function takeSSEBlock(block) {
    let eventType = '',raw = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventType = line.slice(6).trim();
      if (line.startsWith('data:')) raw.push(line.slice(5).trimStart());
    }
    if (!raw.length || raw.join('') === '[DONE]') return '';
    let data;
    try {data = JSON.parse(raw.join('\n'));}catch{return '';}
    if (eventType === 'error' || data.type === 'error') throw new Error(data.error?.message || data.message || '网关流式返回错误');
    if (data.type === 'content_block_delta' || eventType === 'content_block_delta') return String(data.delta?.text || '');
    if (data.type === 'content_block_start' || eventType === 'content_block_start') return String(data.content_block?.text || '');
    return '';
  }
  async function sendMessage() {
    if (state.controller) return;
    const draft = $('#zgFccDraft');
    const prompt = draft?.value.trim();
    const model = $('#zgFccModel')?.value;
    if (!prompt || !model) { showFccStatus('先读取并选择模型，再填写问题。'); return; }
    let endpoint;
    try { endpoint = apiURL('messages'); } catch (error) { showFccStatus(friendlyError(error)); return; }
    const headers = {'Content-Type':'application/json','anthropic-version':'2023-06-01',Accept:'text/event-stream, application/json'};
    const token = $('#zgFccToken')?.value.trim();
    if (gatewayURL() + '#' + (token || '') !== state.fccFingerprint) {showFccStatus('网关或 Token 已变化，请先重新读取模型，避免把旧对话发送到新连接。');return;}
    if (token) headers.Authorization = 'Bearer ' + token;
    const ctl = new AbortController();state.controller=ctl;
    const input = [...state.chat.slice(-24),{role:'user',content:prompt}];
    let answer='',buffer='',sawStreaming=false;
    state.chat.push({role:'user',content:prompt});
    $('#zgFccStop').disabled=false;
    $('#zgFccSend').disabled=true;
    renderMessages('正在思考…');
    showFccStatus('正在等待实际网关返回…');
    try {
      const res = await fetch(endpoint,{method:'POST',headers,signal:ctl.signal,body:JSON.stringify({model,max_tokens:1024,messages:input,stream:true})});
      if (!res.ok) throw new Error('对话 HTTP ' + res.status + (res.status===429 ? '：请求受限，请稍后再试。' : res.status===401 ? '：Token 无效或过期。' : ''));
      if (!res.body) throw new Error('网关没有返回可读取的流。');
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      const sse = (res.headers.get('content-type')||'').includes('text/event-stream');
      if (!sse) {
        let plain='';
        for (;;) {const result=await reader.read();if(result.done)break;plain+=decoder.decode(result.value,{stream:true});if(plain.length>300000)throw new Error('回复长度超限');}
        const parsed = JSON.parse(plain);
        answer = (parsed.content||[]).map(part=>part.text||'').join('');
      } else {
        for (;;) {
          const result=await reader.read();
          if (result.done) break;
          buffer += decoder.decode(result.value,{stream:true}).replace(/\r\n/g,'\n');
          let cut;
          while ((cut=buffer.indexOf('\n\n'))>=0) {
            const chunk=buffer.slice(0,cut);buffer=buffer.slice(cut+2);
            answer += takeSSEBlock(chunk);
            sawStreaming=true;
          }
          if (answer.length>300000 || buffer.length>100000) throw new Error('回复长度超限');
          renderMessages(answer || '正在等待内容…');
        }
        if (buffer.trim()) answer += takeSSEBlock(buffer.trim());
      }
      if (!answer.trim()) throw new Error(sawStreaming?'网关返回了空回复。':'网关未提供可读取的文字。');
      state.chat.push({role:'assistant',content:answer});
      draft.value=''; state.draft='';
      showFccStatus('已收到模型回复 · ' + model);
    } catch (error) {
      state.chat.pop();
      showFccStatus(friendlyError(error) + ' 提问草稿已保留，不会自动重发。');
    } finally {
      state.controller=null;
      const stop=$('#zgFccStop');if(stop)stop.disabled=true;
      const send=$('#zgFccSend');if(send)send.disabled=false;
      renderMessages();
    }
  }
  function handlePanelClick(event) {
    const action = event.target.closest('button,[data-zg-return]');
    if (!action) return;
    if (action.hasAttribute('data-zg-return')) {
      location.hash = '#explore'; showPage(''); $('#discoverySection')?.scrollIntoView({behavior:'smooth'});return;
    }
    const source=action.dataset.zgRankSource;
    if(source&&source!==state.rankSource) {
      state.rankSource=source;state.rankPage=1;
      if(source==='editor')state.rankType='all';
      if(source==='yearly'&&state.rankType==='all')state.rankType='movie';
      if(source==='tmdb'&&!['movie','tv','variety'].includes(state.rankType))state.rankType='movie';
      loadRanking();return;
    }
    const filter=action.dataset.zgRankType;
    if(filter) {
      state.rankType=filter;changeAnnualFilter();return;
    }
    if(action.id==='zgRankSetup'){$('#headerSettings')?.click();return;}
    const page=action.dataset.zgPage;
    if(page&&state.rankSource==='yearly'&&!state.rankLoading) {
      if(page==='next'&&state.rankHasMore)state.rankPage++;
      else if(page==='prev'&&state.rankPage>1)state.rankPage--;
      else return;
      loadAnnual();return;
    }
    if (action.dataset.zgAiTab) {if (state.controller) state.controller.abort();state.tab=action.dataset.zgAiTab;renderAssistantTab();return;}
    if (action.id === 'zgLoadDuck') {
      const frame = document.createElement('iframe');
      frame.src='https://duck.ai/';frame.title='Duck.ai 官方聊天';frame.loading='lazy';
      frame.referrerPolicy='no-referrer';frame.allow='clipboard-write';
      const host=$('#zgDuckFrame');host.replaceChildren(frame);
    }
    if (action.dataset.zgPrompt) {
      navigator.clipboard?.writeText(action.dataset.zgPrompt).then(()=>{
        $('#zgPromptNotice').textContent='问题已经复制，由你决定何时粘贴到官方聊天框。';
      }).catch(()=>{$('#zgPromptNotice').textContent='复制失败：' + action.dataset.zgPrompt;});
    }
    if (action.id === 'zgFccConnect') connectModels();
    if (action.id === 'zgFccSend') sendMessage();
    if (action.id === 'zgFccStop') state.controller?.abort();
    if (action.id === 'zgFccClear') {if (state.controller) return;state.chat=[];renderMessages();showFccStatus('已清除当前页面的聊天记录。');}
  }
  async function init() {
    const main = $('main.content');
    if (!main || $('#zgDomestic')) return;
    const domestic = document.createElement('section');
    domestic.id='zgDomestic';domestic.className='zg-domestic';
    domestic.innerHTML='<div class="zg-section-top zg-top-with-action"><div><span>HANDPICKED · MADE IN CHINA</span><h2>国内好作品，慢慢发现。</h2><p>和安卓端同步的华语电影、国产剧、国创动画、综艺与中文书精选。</p></div><a href="#rankings" class="zg-more-link">查看每年排行榜 ↗</a></div><div id="zgDomesticFilters"></div><p class="zg-data-note" id="zgDomesticCount"></p><div id="zgDomesticGrid" class="zg-work-grid"></div>';
    $('.stats-row')?.insertAdjacentElement('afterend',domestic);
    const panel = document.createElement('section');
    panel.id='zgPanel';panel.className='zg-panel';panel.hidden=true;
    domestic.insertAdjacentElement('afterend',panel);
    const side = document.createElement('div');
    side.innerHTML='<div class="side-caption">知更空间</div><nav class="side-nav zg-side-nav"><a class="nav-item zg-side-link" href="#domestic"><span class="nav-icon">✺</span> 国内精选</a><a class="nav-item zg-side-link" href="#rankings"><span class="nav-icon">▥</span> 年度榜单</a><a class="nav-item zg-side-link" href="#kanju"><span class="nav-icon">▣</span> 看剧AI</a><a class="nav-item zg-side-link" href="#sms"><span class="nav-icon">▥</span> 接码导航</a><a class="nav-item zg-side-link" href="#nodes"><span class="nav-icon">◇</span> 节点导航 5+5</a><a class="nav-item zg-side-link" href="#memberships"><span class="nav-icon">✦</span> AI 会员代充 · 比价</a><a class="nav-item zg-side-link" href="#library"><span class="nav-icon">▤</span> 我的阅读</a><a class="nav-item zg-side-link" href="#assistant"><span class="nav-icon">✦</span> AI 助手</a></nav>';
    $('#categoryNav')?.insertAdjacentElement('afterend',side);
    const headerAI=document.createElement('a');headerAI.href='#assistant';headerAI.className='zg-ai-header';headerAI.textContent='✦ AI 助手';$('.header-actions')?.prepend(headerAI);
    domestic.addEventListener('click',event=>{
      const type=event.target.closest('[data-zg-type]');
      if (type) {state.type=type.dataset.zgType;renderDomestic();}
    });
    panel.addEventListener('click',handlePanelClick);
    panel.addEventListener('change',event=>{
      if(event.target.id==='zgRankYear')state.rankYear=event.target.value;
      else if(event.target.id==='zgRankRegion')state.rankRegion=event.target.value;
      else if(event.target.id==='zgRankSort')state.rankSort=event.target.value;
      else return;
      changeAnnualFilter();
    });
    // 图片已加载才隐藏占位文字；加载失败则仍显示可读的文字卡。
    document.addEventListener('load',event=>{
      if(event.target.matches?.('.zg-poster'))event.target.closest('.zg-art')?.classList.add('is-loaded');
    },true);
    document.addEventListener('error',event=>{
      if(event.target.matches?.('.zg-poster')){event.target.hidden=true;event.target.closest('.zg-art')?.classList.remove('is-loaded');}
    },true);
    document.addEventListener('click',event=>{
      const save=event.target.closest('[data-zg-save]');
      if (!save) return;
      const item=[...state.domestic,...state.rankingItems].find(x=>x.id===save.dataset.zgSave);
      if (item) {event.preventDefault();toggleFavorite(item);}
    });
    document.addEventListener('click',event=>{
      const link=event.target.closest('a[href="#rankings"],a[href="#assistant"],a[href="#domestic"],a[href="#library"],a[href="#kanju"],a[href="#sms"],a[href="#nodes"],a[href="#memberships"]');
      if (!link) return;
      event.preventDefault();const route=link.getAttribute('href').slice(1);
      if(route==='library')navigate(route);
      else if(location.hash==='#'+route)navigate(route);
      else location.hash='#'+route;
    });
    $$('#sideNav button,#categoryNav button,#categoryTabs button,.bottom-nav button,#heroExplore,#heroLatest,#avatarButton').forEach(button=>{
      button.addEventListener('click',()=>{ if ($('#zgPanel').dataset.page) showPage(''); });
    });
    window.addEventListener('hashchange',()=>navigate(location.hash.slice(1)));
    try {
      const response=await fetch('data/domestic.json',{cache:'no-cache'});
      if (!response.ok) throw new Error('HTTP '+response.status);
      const value=await response.json();
      if (!Array.isArray(value.items) || value.items.length!==28) throw new Error('编辑目录格式与数量不符');
      state.domestic=value.items.map(normalized).map((item,i)=>Object.assign(item,{
        author:value.items[i].author,tag:value.items[i].tag,summary:value.items[i].summary,
        rank:value.items[i].rank,editorial:true
      }));
      renderDomestic();
      if ($('#zgPanel')?.dataset.page === 'rankings' && state.rankSource === 'editor') renderRankingList();
      if(window.ZhigengCoverEnrichment?.fill) {
        window.ZhigengCoverEnrichment.fill(state.domestic).then(items=>{
          if(!items)return;
          state.domestic=items;renderDomestic();
          if ($('#zgPanel')?.dataset.page === 'rankings' && state.rankSource === 'editor') renderRankingList();
        }).catch(()=>{});
      }
    } catch (error) {
      $('#zgDomesticCount').textContent='编辑目录暂不可用：'+error.message;
      $('#zgDomesticGrid').innerHTML='<div class="zg-empty">请检查 data/domestic.json 是否随网站一同发布。</div>';
    }
    if (['rankings','assistant','kanju','sms','nodes','memberships','domestic','library'].includes(location.hash.slice(1))) navigate(location.hash.slice(1));
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
  else init();
})();
