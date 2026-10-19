/* 知更节点导航：五家代理 IP + 五家低价机场。导航不代理流量、不测试 IP、不收集账号数据。 */
(() => {
  'use strict';
  const $=sel=>document.querySelector(sel);
  const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const state={data:null,query:'',requestId:0};
  const STORAGE='zhigeng-node-fav-v1';
  function favorites(){
    try {
      const v=JSON.parse(localStorage.getItem(STORAGE)||'[]');
      return new Set(Array.isArray(v)?v.filter(x=>typeof x==='string'):[]);
    }catch{return new Set();}
  }
  function safeURL(value){
    try {const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password?u.href:'#';}
    catch{return '#';}
  }
  function card(site,kind,saved){
    const url=safeURL(site.url);
    const urlLabel=url==='#'?'网址待核验':new URL(url).hostname;
    const starred=saved.has(site.id);
    return '<article class="zg-node-card">'+
      '<div class="zg-node-card-top"><div class="zg-node-monogram" aria-hidden="true">'+escape(site.name.trim().slice(0,1).toUpperCase())+'</div>'+
      '<div class="zg-node-card-title"><h3>'+escape(site.name)+'</h3><small>'+escape(urlLabel)+'</small></div>'+
      '<button class="zg-node-star'+(starred?' is-saved':'')+'" data-zg-node-favorite="'+escape(site.id)+'" aria-label="'+(starred?'取消收藏 ':'收藏 ')+escape(site.name)+'" type="button">'+(starred?'★':'☆')+'</button></div>'+
      '<div class="zg-node-tags">'+(site.tags||[]).slice(0,3).map(x=>'<span>'+escape(x)+'</span>').join('')+'</div>'+
      (site.price?'<p class="zg-node-price">'+escape(site.price)+'</p>':'')+
      (site.payment?'<p class="zg-node-payment"><strong>付款：</strong>'+escape(site.payment)+'</p>':'')+
      '<p class="zg-node-description">'+escape(site.desc)+'</p>'+
      '<p class="zg-node-verification">'+escape(site.verified||'未提供独立网络实测')+'</p>'+
      '<div class="zg-node-card-actions"><a href="'+escape(url)+'" target="_blank" rel="noopener noreferrer" aria-label="打开'+escape(site.name)+'官方或参考入口">打开网站 ↗</a>'+
      '<small>'+escape(site.source||'参考目录')+'</small></div></article>';
  }
  function render() {
    if(!state.data||$('#zgPanel')?.dataset.page!=='nodes')return;
    const saved=favorites();
    const query=state.query.trim().toLocaleLowerCase();
    const body=$('#zgNodesSections');
    if(!body)return;
    let matching=0;
    const shortcuts=Array.isArray(state.data.shortcuts)?state.data.shortcuts:[];
    const renderShortcuts=()=>shortcuts.map(shortcut=>{
      const target=safeURL(shortcut.url);
      return '<aside class="zg-node-shortcut" aria-label="新增节点快捷入口">'+
        '<div class="zg-node-shortcut-icon" aria-hidden="true">↗</div>'+
        '<div class="zg-node-shortcut-copy"><span>'+escape(shortcut.label||'额外直达')+'</span><h3>'+escape(shortcut.name)+'</h3>'+
        '<p>'+escape(shortcut.desc)+'</p><small>'+escape(shortcut.verified)+'</small></div>'+
        '<a class="zg-node-shortcut-link" href="'+escape(target)+'" target="_blank" rel="noopener noreferrer">进入控制台 ↗</a></aside>';
    }).join('');
    body.innerHTML=state.data.groups.map((group,index)=>{
      const items=group.entries.filter(site=>!query||(site.name+' '+site.url+' '+site.desc+' '+site.tags.join(' ')+' '+(site.payment||'')+' '+(site.price||'')).toLocaleLowerCase().includes(query));
      matching+=items.length;
      return '<section class="zg-node-group" data-zg-node-group="'+escape(group.id)+'">'+
        '<div class="zg-node-group-header"><div><span class="zg-node-group-number">0'+(index+1)+' / 02</span><h2>'+escape(group.name)+'</h2><p>'+escape(group.subtitle)+'</p></div>'+
        '<span class="zg-node-group-total">'+items.length+' / '+group.entries.length+' 家</span></div>'+
        '<div class="zg-node-grid">'+(items.length?items.map(site=>card(site,group.id,saved)).join(''):'<p class="zg-node-noresults">这一组没有匹配结果，可以换一个搜索词。</p>')+'</div></section>';
    }).join('')+'<section class="zg-node-extra-section"><div class="zg-node-group-header"><div><span class="zg-node-group-number">EXTRA / NOT COUNTED</span><h2>额外指定的控制台</h2><p>独立保留你提供的地址，不归入最新筛选的机场五家。</p></div></div>'+renderShortcuts()+'</section>';
    const count=$('#zgNodesCount');
    if(count)count.textContent='共 '+state.data.groups.reduce((n,g)=>n+g.entries.length,0)+' 家 · 当前匹配 '+matching+' 家 · 额外控制台入口 '+shortcuts.length+' 个';
    const badge=$('#zgNodesFavoriteCount');
    if(badge)badge.textContent='已收藏 '+saved.size+' 家';
  }
  function toggleFavorite(id){
    const known=new Set(state.data?.groups?.flatMap(g=>g.entries.map(x=>x.id))||[]);
    if(!known.has(id))return;
    const list=favorites();
    if(list.has(id))list.delete(id);else list.add(id);
    try{localStorage.setItem(STORAGE,JSON.stringify([...list]));}
    catch{const status=$('#zgNodesStatus');if(status)status.textContent='浏览器不允许保存收藏，请检查隐私或存储设置。';return;}
    render();
  }
  function onPanelClick(event){
    if($('#zgPanel')?.dataset.page!=='nodes')return;
    const button=event.target.closest('[data-zg-node-favorite]');
    if(button)toggleFavorite(button.dataset.zgNodeFavorite);
  }
  async function mount(panel){
    if(!panel)return;
    panel.innerHTML='<div class="zg-section-top"><span>NETWORK DIRECTORY · 5 + 5</span><h1>节点与代理服务导航</h1>'+
      '<p>已替换为最新支付方式筛选的两组网站：五家代理（其中第五家为更早轮次的待核对占位）与五家机场；另保留你指定的控制台链接。</p></div>'+
      '<div class="zg-node-hero"><div><span>2 CATEGORIES · 10 SERVICES + 1 DASHBOARD</span><h2>适合的连接方式，比更多节点更重要。</h2>'+
      '<p>用于比较服务入口和产品类型，不是速度、IP信誉或 ChatGPT / Gemini / Claude 解锁的实测排行榜。</p></div>'+
      '<div class="zg-node-hero-art" aria-hidden="true">◈</div></div>'+
      '<div class="zg-node-tools"><label for="zgNodesSearch">查找服务<input id="zgNodesSearch" type="search" placeholder="搜索名称、静态IP、动态IP…" autocomplete="off" /></label>'+
      '<div><span id="zgNodesFavoriteCount">已收藏 0 家</span><span id="zgNodesCount" role="status" aria-live="polite">正在加载两组网站…</span></div></div>'+
      '<div class="zg-node-status" id="zgNodesStatus" role="status">已替换原先自行补的节点名单。最新聊天能核实4家代理与5家机场；代理第5家无法精确恢复，暂显示此前对话出现的 IPRoyal 并明确标记。支付宝、微信支付声明不等于实际付款成功；第三方注册链接可能含推广信息。</div>'+
      '<div id="zgNodesSections" class="zg-node-sections"><p class="zg-node-loading">正在读取 5 + 5 网站目录…</p></div>'+
      '<div class="zg-node-guide"><strong>为什么分开？</strong> 专业代理通常提供按 IP / 流量计费的 ISP、住宅或数据中心出口；机场通常提供多个节点的订阅。机场 IP 不一定独享或为住宅类型。AI 站点可用性取决于账号、区域政策、IP 信誉和使用方式，页面不作保证。</div>'+
      '<p class="zg-node-bottom-note">仅提供导航，不储存支付信息、代理密码、订阅链接或访问日志；收藏仅保存在当前浏览器。建议低价机场只做短期小额试用，注意服务条款及当地规定。</p>';
    const field=$('#zgNodesSearch');
    if(field){
      field.value=state.query;
      field.addEventListener('input',()=>{state.query=field.value.trim().slice(0,100);render();});
    }
    panel.removeEventListener('click',onPanelClick);
    panel.addEventListener('click',onPanelClick);
    const req=++state.requestId;
    if(state.data){render();return;}
    try {
      const res=await fetch('data/node-sites.json',{cache:'no-cache'});
      if(!res.ok)throw Error('目录 HTTP '+res.status);
      const data=await res.json();
      if(!Array.isArray(data.groups)||data.groups.length!==2||data.groups.some(g=>!Array.isArray(g.entries)||g.entries.length!==5))throw Error('目录分组数量应为 5 + 5');
      for(const group of data.groups)for(const entry of group.entries)if(safeURL(entry.url)==='#')throw Error('目录内有不可用的地址格式');
      if(!Array.isArray(data.shortcuts)||data.shortcuts.length!==1)throw Error('额外控制台链接配置异常');
      const dashboard=data.shortcuts[0];
      if(dashboard.url!=='https://xn--4gqx1hgtfdmt.com/#/dashboard'||safeURL(dashboard.url)==='#')throw Error('额外控制台地址不匹配用户指定链接');
      if(req!==state.requestId)return;
      state.data=data;render();
    }catch(error){
      if(req!==state.requestId)return;
      const host=$('#zgNodesSections');
      if(host)host.innerHTML='<p class="zg-node-noresults">暂时无法读取节点导航目录：'+escape(error.message)+'。请刷新网站重试。</p>';
    }
  }
  window.ZhigengNodes={mount,storageKey:STORAGE};
})();
