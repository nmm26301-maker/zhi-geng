/* 看剧AI · 仅用户主动使用的官方站点入口。没有抓取、视频代理或账号同步。 */
(() => {
  'use strict';
  const OFFICIAL='https://kanju.ai/';
  let pendingTitle='';
  const $=selector=>document.querySelector(selector);
  const escape=value=>String(value||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const clean=value=>String(value||'').replace(/[\u0000-\u001f\u007f]/g,' ').trim().slice(0,100);
  function searchURL(title) {
    const q=clean(title);
    if(!q)return OFFICIAL;
    const url=new URL('/session/new',OFFICIAL);
    url.searchParams.set('prompt',q);
    return url.href;
  }
  function setStatus(message) {
    const node=$('#zgKanjuStatus');
    if(node)node.textContent=message;
  }
  function updateLink() {
    const field=$('#zgKanjuQuery');
    const link=$('#zgKanjuExternal');
    const value=clean(field?.value);
    if(link) {
      link.href=searchURL(value);
      link.textContent=value?'在看剧AI官方页面搜索 ↗':'打开看剧AI官方网站 ↗';
    }
    return value;
  }
  function requestFrame(withQuery=false) {
    const title=updateLink();
    if(withQuery&&!title) {
      setStatus('先填写片名，或点击「尝试打开官网」直接进入看剧AI。');
      $('#zgKanjuQuery')?.focus();
      return;
    }
    const host=$('#zgKanjuFrameHost');
    if(!host)return;
    const frame=document.createElement('iframe');
    frame.src=withQuery?searchURL(title):OFFICIAL;
    frame.title='看剧AI 官方页面';
    frame.loading='eager';
    frame.referrerPolicy='no-referrer';
    frame.allow='fullscreen; picture-in-picture; encrypted-media';
    frame.allowFullscreen=true;
    host.replaceChildren(frame);
    setStatus('已请求在知更中打开看剧AI。能否正常显示、登录、搜索和播放取决于对方网站的网页嵌入及访问规则；如被阻止，请使用上方官方入口。');
  }
  function mount(panel) {
    if(!panel)return;
    panel.innerHTML='<div class="zg-section-top"><span>OFFICIAL SITE · KANJU.AI</span><h1>▣ 看剧AI · 在知更找片</h1>'+
      '<p>将看剧AI作为独立影视入口，搜片与播放由 kanju.ai 官方网站提供。</p></div>'+
      '<div class="zg-kanju-layout"><div class="zg-kanju-intro"><div><span class="zg-kanju-overline">KANJU.AI / 官方网页</span>'+
      '<h2>好作品找到了，接着去看。</h2><p>可以输入片名，主动交给看剧AI搜索；也可以直接尝试在知更内访问官方网站。</p>'+
      '<form id="zgKanjuForm" class="zg-kanju-form"><label for="zgKanjuQuery">电影、电视剧、动漫、综艺片名</label>'+
      '<div class="zg-kanju-input-wrap"><input type="search" id="zgKanjuQuery" maxlength="100" autocomplete="off" spellcheck="false" placeholder="输入想看的片名…" />'+
      '<button type="submit" class="zg-primary">在知更内找片 ↗</button></div></form>'+
      '<div class="zg-kanju-actions"><button id="zgKanjuHome" class="zg-kanju-quiet-button" type="button">尝试打开官网</button>'+
      '<a id="zgKanjuExternal" href="'+OFFICIAL+'" target="_blank" rel="noopener noreferrer">打开看剧AI官方网站 ↗</a>'+
      '<a href="#rankings" class="zg-kanju-back">返回知更年度榜单 ↗</a></div></div>'+
      '<div class="zg-kanju-flower" aria-hidden="true"><span>✦</span><strong>看剧AI</strong><small>SEARCH · DISCOVER · WATCH</small></div></div>'+
      '<p class="zg-kanju-disclaimer">第三方服务说明：仅在点击搜索或加载后连接看剧AI。知更不传送书架、收藏、历史记录、TMDB 密钥或聊天信息；输入的片名会发送给看剧AI。第三方账号、服务可用性、播放权限和版权由对方管理。对方可能禁止网页内嵌或要求跳转到分配的访问域名。</p>'+
      '<p id="zgKanjuStatus" class="zg-data-note" role="status" aria-live="polite">尚未加载第三方页面。点击操作后会尝试打开，始终保留官方网页备用入口。</p>'+
      '<div id="zgKanjuFrameHost" class="zg-kanju-frame"><div class="zg-kanju-placeholder"><span>◉</span><strong>看剧AI</strong><p>在知更里尝试加载官方页面</p><small>如果对方限制内嵌，请通过官方入口使用</small></div></div></div>';
    const input=$('#zgKanjuQuery');
    if(input)input.value=pendingTitle;
    updateLink();
    $('#zgKanjuForm')?.addEventListener('submit',event=>{
      event.preventDefault();
      pendingTitle=updateLink();
      requestFrame(true);
    });
    $('#zgKanjuHome')?.addEventListener('click',()=>requestFrame(false));
    input?.addEventListener('input',()=>{pendingTitle=clean(input.value);updateLink();});
  }
  function lookup(title) {
    pendingTitle=clean(title);
    if(location.hash==='#kanju') {
      mount($('#zgPanel'));
      return;
    }
    location.hash='#kanju';
  }
  function decorateLegacyCards() {
    const grid=$('#workGrid');
    if(!grid)return;
    for(const card of grid.querySelectorAll('.work-card')) {
      if(card.querySelector('.zg-kanju-lookup'))continue;
      const title=card.querySelector('.card-title');
      const kind=String(card.getAttribute('data-id')||'').split(':')[0];
      if(!title||!['movie','tv','anime','variety'].includes(kind))continue;
      const action=document.createElement('button');
      action.type='button';action.className='zg-kanju-lookup zg-kanju-legacy';
      action.textContent='看剧AI 找片 ↗';
      action.setAttribute('data-zg-kanju-title',clean(title.textContent));
      card.append(action);
    }
  }
  function init() {
    const grid=$('#workGrid');
    if(grid) {
      decorateLegacyCards();
      new MutationObserver(decorateLegacyCards).observe(grid,{childList:true});
    }
    document.addEventListener('click',event=>{
      const button=event.target.closest('[data-zg-kanju-title]');
      if(!button)return;
      event.preventDefault();
      lookup(button.getAttribute('data-zg-kanju-title'));
    });
  }
  window.ZhigengKanju={mount,lookup,searchURL,official:OFFICIAL};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);
  else init();
})();
