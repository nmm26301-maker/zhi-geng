/* 知更 · 原「接码站」导航集成。仅链接经过筛选的公开号码站，不采集或显示短信。 */
(() => {
  'use strict';
  const sites=Object.freeze([
    {name:'FreePhoneNum',url:'https://freephonenum.com/',letter:'F',info:'美国、加拿大等地区的公开临时号码；部分历史号码已归档。'},
    {name:'SMSToMe',url:'https://smstome.com/',letter:'S',info:'公开免费号码与历史短信，按站点提供的国家地区选择。'},
    {name:'Receive-SMSS',url:'https://receive-smss.com/',letter:'R',info:'按地区查看公开号码和历史收件箱。'},
    {name:'Temporary Phone Number',url:'https://temporary-phone-number.com/',letter:'T',info:'临时号码和公开短信收件箱导航。'},
    {name:'Temp Number',url:'https://temp-number.com/',letter:'N',info:'查看公开临时号码和历史短信。'},
    {name:'Receive SMS Free',url:'https://receive-sms-free.cc/',letter:'M',info:'公开号码与短信页面，部分验证码可能被服务方过滤。'}
  ]);
  const F='sms-nav-fav-1',H='sms-nav-hide-1';
  const state={filter:'all',query:''};
  const $=(sel,root=document)=>root.querySelector(sel);
  const $$=(sel,root=document)=>Array.from(root.querySelectorAll(sel));
  const safe=x=>String(x||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const whitelist=new Set(sites.map(x=>x.url));
  function read(key) {
    try {
      const arr=JSON.parse(localStorage.getItem(key)||'[]');
      return new Set(Array.isArray(arr)?arr.filter(x=>whitelist.has(x)):[]);
    } catch {return new Set();}
  }
  function persist(key,values) {
    try {localStorage.setItem(key,JSON.stringify([...values]));return true;}
    catch{return false;}
  }
  function status(text) {
    const el=$('#zgSmsStatus');
    if(el)el.textContent=text;
  }
  function render(){
    const host=$('#zgSmsCards');
    if(!host)return;
    const favorites=read(F),hidden=read(H);
    const q=state.query.trim().toLocaleLowerCase();
    const visible=sites.filter(s=>(state.filter==='hidden'?hidden.has(s.url):!hidden.has(s.url))&&
      (state.filter!=='fav'||favorites.has(s.url))&&
      (!q||(s.name+' '+s.url+' '+s.info).toLocaleLowerCase().includes(q)));
    $('#zgSmsCount').textContent='显示 '+visible.length+' 个网站 · 上次人工核查：2026-10-19';
    $$('#zgSmsFilters [data-zg-sms-filter]').forEach(b=>{
      b.classList.toggle('is-active',b.dataset.zgSmsFilter===state.filter);
      b.setAttribute('aria-pressed',b.dataset.zgSmsFilter===state.filter?'true':'false');
    });
    host.innerHTML=visible.length?visible.map(site=>{
      const starred=favorites.has(site.url),isHidden=hidden.has(site.url);
      return '<article class="zg-sms-card"><div class="zg-sms-card-top"><div class="zg-sms-icon">'+safe(site.letter)+'</div>'+
        '<div class="zg-sms-name"><h3>'+safe(site.name)+'</h3><span>'+safe(site.url.replace('https://',''))+'</span></div>'+
        '<button type="button" class="zg-sms-star'+(starred?' is-saved':'')+'" aria-label="'+(starred?'取消收藏':'收藏')+safe(site.name)+'" data-zg-sms-star="'+safe(site.url)+'">'+(starred?'★':'☆')+'</button></div>'+
        '<p>'+safe(site.info)+'</p><div class="zg-sms-card-meta"><span>原站直达</span><span>公开号码</span></div>'+
        '<div class="zg-sms-card-actions"><a href="'+safe(site.url)+'" target="_blank" rel="noopener noreferrer">访问原站 <span aria-hidden="true">↗</span></a>'+
        '<button type="button" data-zg-sms-hide="'+safe(site.url)+'">'+(isHidden?'恢复显示':'隐藏此网站')+'</button></div></article>';
    }).join(''):'<div class="zg-sms-empty">暂无匹配的网站。可以修改搜索词或切换「已隐藏」查看。</div>';
  }
  function toggle(key,url) {
    if(!whitelist.has(url))return;
    const values=read(key);
    if(values.has(url))values.delete(url);else values.add(url);
    if(!persist(key,values)){status('浏览器无法保存偏好，请检查本地存储设置。');return;}
    render();
  }
  function handlePanelClick(event) {
      if($('#zgPanel')?.dataset.page!=='sms')return;
      const el=event.target.closest('[data-zg-sms-filter],[data-zg-sms-star],[data-zg-sms-hide]');
      if(!el)return;
      if(el.dataset.zgSmsFilter){
        state.filter=el.dataset.zgSmsFilter;
        render();
      } else if(el.dataset.zgSmsStar)toggle(F,el.dataset.zgSmsStar);
      else if(el.dataset.zgSmsHide)toggle(H,el.dataset.zgSmsHide);
  }
  function mount(panel) {
    if(!panel)return;
    panel.innerHTML='<div class="zg-section-top"><span>PUBLIC SMS DIRECTORY · BOOKMARKS</span><h1>接码网站导航</h1>'+
      '<p>这是你之前的「接码站」导航，原样保留有效站点筛选、搜索、收藏与隐藏；只提供原网站入口。</p></div>'+
      '<div class="zg-sms-banner"><div><span>6 SITES · ONE PLACE</span><h2>常用接码站，集中在这里。</h2>'+
      '<p>根据 2026-10-19 的人工核查名单收录；已再次确认六个站点首页可访问，但不保证任意号码当下都能接收验证码。</p></div>'+
      '<div class="zg-sms-banner-shape" aria-hidden="true">✳</div></div>'+
      '<div class="zg-sms-toolbar"><div><strong>网站列表</strong><p id="zgSmsCount" role="status">已收录 6 个网站</p></div>'+
      '<input type="search" id="zgSmsSearch" aria-label="搜索接码网站" placeholder="搜索名称或网址…" autocomplete="off"/></div>'+
      '<div id="zgSmsFilters" class="zg-chips" role="group" aria-label="显示站点"><button data-zg-sms-filter="all" class="zg-chip" type="button">全部网站</button>'+
      '<button data-zg-sms-filter="fav" class="zg-chip" type="button">☆ 我的收藏</button>'+
      '<button data-zg-sms-filter="hidden" class="zg-chip" type="button">已隐藏</button></div>'+
      '<div id="zgSmsCards" class="zg-sms-grid"></div>'+
      '<p class="zg-sms-warning"><strong>注意：</strong>这些是公开共享号码，任何访问者都可能看到接收的短信。不要用它们注册重要账户，也不要用于银行、支付、密码找回或敏感信息验证。</p>'+
      '<div class="zg-sms-footer"><span id="zgSmsStatus" role="status">收藏与隐藏只存储在本地设备；知更不读取、不缓存短信或验证码。</span>'+
      '<a href="sms-hub/index.html" target="_blank" rel="noopener noreferrer">打开原接码导航完整页面 ↗</a></div>';
    const input=$('#zgSmsSearch');
    if(input){
      input.value=state.query;
      input.addEventListener('input',()=>{state.query=input.value.trim();render();});
    }
    panel.removeEventListener('click',handlePanelClick);
    panel.addEventListener('click',handlePanelClick);
    render();
  }
  window.ZhigengSmsHub={mount,sites,storage:{favorites:F,hidden:H}};
})();
