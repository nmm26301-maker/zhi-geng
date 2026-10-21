/* 知更 · AI 会员代充/共享/比价目录，严格按用户截图 5 个首选 + 2 个备选。 */
(() => {
  'use strict';
  const $=(selector,root=document)=>root.querySelector(selector);
  const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const REVISION='20261020-ai5plus2';
  const STORE='zg-ai-membership-favorites-v1';
  const state={data:null,query:'',brand:'all',delivery:'all',loading:0};
  const IDS={primary:['gamsgo','airadar','tehuio','familypro','yinhe'],alternatives:['huanqiu','relayradar']};
  function safeURL(url){
    try{const u=new URL(url);return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch{return null;}
  }
  function favorites(){
    const valid=new Set(state.data?.groups?.flatMap(g=>g.entries.map(x=>x.id))||[]);
    try{
      const a=JSON.parse(localStorage.getItem(STORE)||'[]');
      return new Set(Array.isArray(a)?a.filter(x=>valid.has(x)):[]);
    }catch{return new Set();}
  }
  function choose(id){
    const fav=favorites();
    if(fav.has(id))fav.delete(id);else fav.add(id);
    try{localStorage.setItem(STORE,JSON.stringify([...fav]));}
    catch{setStatus('无法保存收藏，请检查浏览器本地存储设置。');return;}
    render();
  }
  function setStatus(message){const el=$('#zgMemberStatus');if(el)el.textContent=message;}
  function card(entry,index,kind,labels,favs){
    const url=safeURL(entry.url);
    const tags=entry.types.map(t=>labels[t]||t).join(' · ');
    const matched=favs.has(entry.id);
    const mono=kind==='primary'?String(index+1).padStart(2,'0'):'B'+(index+1);
    return '<article class="zg-member-card" data-zg-member-id="'+esc(entry.id)+'">'+
      '<div class="zg-member-card-top"><span class="zg-member-no">'+mono+'</span><div class="zg-member-name"><h3>'+esc(entry.name)+'</h3><small>'+esc(entry.role)+'</small></div>'+
      '<button type="button" class="zg-member-star'+(matched?' is-saved':'')+'" data-zg-member-fav="'+esc(entry.id)+'" aria-label="'+(matched?'取消收藏 ':'收藏 ')+esc(entry.name)+'">'+(matched?'★':'☆')+'</button></div>'+
      '<div class="zg-member-tags">'+entry.types.map(t=>'<span>'+esc(labels[t]||t)+'</span>').join('')+'</div>'+
      '<p class="zg-member-summary">'+esc(entry.summary)+'</p>'+
      '<div class="zg-member-details"><p><strong>涵盖：</strong>'+esc(entry.brands.join('、'))+'</p>'+
      '<p><strong>付款：</strong>'+esc(entry.payment)+'</p>'+
      '<p><strong>注意：</strong>'+esc(entry.tip)+'</p></div>'+
      '<div class="zg-member-card-footer"><a href="'+esc(url||'#')+'" target="_blank" rel="noopener noreferrer" aria-label="查看'+esc(entry.name)+'网站">查看网站 ↗</a>'+
      '<span>'+esc(entry.verified)+'</span></div></article>';
  }
  function render(){
    const host=$('#zgMemberGroups');
    if(!host||$('#zgPanel')?.dataset.page!=='memberships'||!state.data)return;
    const data=state.data,labels=data.deliveryLabels||{},fav=favorites();
    const q=state.query.toLocaleLowerCase();
    let total=0;
    host.innerHTML=data.groups.map((group,i)=>{
      const shown=group.entries.filter(e=>
        (state.brand==='all'||e.brands.includes(state.brand))&&
        (state.delivery==='all'||e.types.includes(state.delivery))&&
        (!q||(e.name+' '+e.role+' '+e.summary+' '+e.brands.join(' ')+e.types.join(' ')).toLocaleLowerCase().includes(q)));
      total+=shown.length;
      return '<section class="zg-member-group" data-zg-member-group="'+esc(group.id)+'">'+
        '<div class="zg-member-group-heading"><div><span>0'+(i+1)+' / 02 · '+(i===0?'PRIMARY SELECTION':'BACKUP PICKS')+'</span>'+
        '<h2>'+esc(group.title)+'</h2><p>'+esc(group.subtitle)+'</p></div><small>'+shown.length+' / '+group.entries.length+' 家</small></div>'+
        '<div class="zg-member-grid">'+(shown.length?shown.map((x,j)=>card(x,group.entries.indexOf(x),group.id,labels,fav)).join(''):
          '<div class="zg-member-empty">本组暂无匹配的平台，试试更换筛选条件。</div>')+'</div></section>';
    }).join('');
    const count=$('#zgMemberCount');if(count)count.textContent='找到 '+total+' / 7 个平台 · 5 个首选 + 2 个备选';
    const favEl=$('#zgMemberSaved');if(favEl)favEl.textContent='已收藏 '+fav.size+' 个';
  }
  function onClick(event){
    if($('#zgPanel')?.dataset.page!=='memberships')return;
    const button=event.target.closest('[data-zg-member-fav]');
    if(button)choose(button.getAttribute('data-zg-member-fav'));
  }
  async function loadData(force){
    const req=++state.loading;
    try{
      if(!state.data||force){
        const resp=await fetch('data/ai-memberships.json?v='+REVISION+'&t='+Date.now(),{cache:'no-store'});
        if(!resp.ok)throw Error('HTTP '+resp.status);
        const data=await resp.json();
        if(data.schemaVersion!==2||!Array.isArray(data.groups)||data.groups.length!==2||
          data.groups.some(g=>!IDS[g.id]||g.entries.length!==IDS[g.id].length||
          g.entries.some((e,i)=>e.id!==IDS[g.id][i]||!safeURL(e.url))))throw Error('服务器返回旧版 AI 会员名单');
        if(req!==state.loading)return;
        state.data=data;
      }
      render();
      setStatus('已载入截图确认的 5 个主推荐 + 2 个备选。链接只在你主动点击时访问第三方网站。');
    }catch(error){
      if(req!==state.loading)return;
      const host=$('#zgMemberGroups');
      if(host)host.innerHTML='<div class="zg-member-empty">AI 会员目录暂不可用：'+esc(error.message)+'。请点击「刷新目录」或等候网站部署。</div>';
      setStatus('当前可能仍在使用旧部署或缓存版本，尚未载入正确名单。');
    }
  }
  function mount(panel){
    if(!panel)return;
    panel.innerHTML='<div class="zg-section-top"><span>SUBSCRIPTION FINDER · YOUR SHORTLIST</span><h1>✦ AI 会员代充与比价</h1>'+
      '<p>按你截图里的选择：GamsGo、AI订阅价格雷达、特惠购、FamilyPro、银河录像局；环球巴士和 Relay Radar 作为备选。</p></div>'+
      '<div class="zg-member-hero"><div><span>CURATED · 5 + 2</span><h2>先比同一款，再看怎么交付。</h2>'+
      '<p>代充自己的账号、共享账号、镜像与成品号不是一种商品。按真实交付方式选择，别只看最低价。</p></div><span class="zg-member-orbit" aria-hidden="true">✦</span></div>'+
      '<div class="zg-member-toolbar"><label for="zgMemberSearch">搜索网站或 AI 产品<input id="zgMemberSearch" type="search" autocomplete="off" placeholder="例如：ChatGPT、Claude、卡密…" /></label>'+
      '<label for="zgMemberBrand">AI 平台<select id="zgMemberBrand"><option value="all">全部平台</option><option>ChatGPT</option><option>Claude</option><option>Gemini</option><option>Grok</option><option>Cursor</option></select></label>'+
      '<label for="zgMemberDelivery">购买类型<select id="zgMemberDelivery"><option value="all">全部方式</option><option value="own">自有账号代充</option><option value="shared">共享／合租</option><option value="redeem">卡密／兑换</option><option value="mirror">镜像服务</option><option value="account">成品账号</option><option value="compare">先比价</option></select></label></div>'+
      '<div class="zg-member-meta"><span id="zgMemberCount" aria-live="polite">正在读取名单…</span><span id="zgMemberSaved">已收藏 0 个</span><button type="button" id="zgMemberRefresh">↻ 刷新目录</button></div>'+
      '<p class="zg-member-alert" role="note">价格、库存和微信／支付宝付款方式会变化；仅在卖家实际结算页核对后才可确认。共享账号可能存在聊天记录泄露和官方平台条款风险，请不要交出自己的密码、Session 或验证码。</p>'+
      '<div id="zgMemberGroups" class="zg-member-groups"><p class="zg-member-loading">读取 5 个主推荐 + 2 个备选中…</p></div>'+
      '<p id="zgMemberStatus" class="zg-member-status" role="status" aria-live="polite"></p>';
    const search=$('#zgMemberSearch');
    search.value=state.query;
    search.addEventListener('input',v=>{state.query=v.target.value.trim().slice(0,80);render();});
    const brand=$('#zgMemberBrand');brand.value=state.brand;brand.addEventListener('change',v=>{state.brand=v.target.value;render();});
    const delivery=$('#zgMemberDelivery');delivery.value=state.delivery;delivery.addEventListener('change',v=>{state.delivery=v.target.value;render();});
    $('#zgMemberRefresh').addEventListener('click',()=>{state.data=null;loadData(true);});
    panel.removeEventListener('click',onClick);
    panel.addEventListener('click',onClick);
    loadData(false);
  }
  window.ZhigengMemberships={mount,revision:REVISION};
})();