/* Browser UI for heartleo/zlib EAPI search. Session stays in page memory. */
(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  let credentials=null,domain='',mode='public',busy=false;
  const status=message=>{$('zlibStatus').textContent=message;};
  function sync(){
    const connected=!!credentials;
    $('zlibConnect').textContent=connected?'账号已连接 · 管理':'连接 Z-Library';
    $('zlibSessionState').textContent=connected?'已连接 · 会话仅保留在当前页面':'未连接';
    $('zlibDisconnect').hidden=!connected;
    $('bookExtension').disabled=mode!=='zlib';
    $('bookAvailability').disabled=mode==='zlib';
    $('bookSearchHint').textContent=mode==='zlib'?'使用已连接账号在知更内搜书，支持年份、语言与格式筛选。':'从公开图书目录和在线书目中搜索，不需要 TMDB 密钥。';
  }
  function close(){$('zlibModal').hidden=true;$('zlibPassword').value='';}
  async function connect(event){
    event.preventDefault();if(busy)return;busy=true;
    $('zlibLogin').disabled=true;status('正在连接源站，请稍候…');
    const selected=$('zlibDomain').value;
    try{
      const action=$('zlibUseSession').checked?'verify':'login';
      const payload={service:'zlib',action,domain:selected};
      if(action==='verify')payload.session={id:$('zlibUserId').value.trim(),key:$('zlibUserKey').value.trim()};
      else{payload.email=$('zlibEmail').value.trim();payload.password=$('zlibPassword').value;}
      const result=await window.ZhigengTransport.post(payload);
      credentials=action==='verify'?payload.session:result.session;domain=selected;
      if(!credentials?.id||!credentials.key)throw new Error('来源未返回有效会话');
      $('zlibUserId').value='';$('zlibUserKey').value='';
      mode='zlib';$('bookProvider').value=mode;sync();status('连接成功，可以输入书名开始搜索。');close();
      if($('bookSearchInput').value.trim())window.ZhigengBookAPI?.searchBooks($('bookSearchInput').value);
    }catch(e){status(e.message||'连接失败');}
    finally{$('zlibPassword').value='';busy=false;$('zlibLogin').disabled=false;}
  }
  window.ZhigengZlib={
    active:()=>mode==='zlib',
    cacheScope:()=>mode==='zlib'?'zlib:'+domain+':'+(credentials?.id||'guest'):'public',
    async search({query,filters={},page=1}){
      if(!credentials)throw new Error('请先点击「连接 Z-Library」，登录自己的账号后搜书');
      if(!String(query||'').trim())throw new Error('请输入书名或作者，然后点击「搜书」');
      status('正在搜索…');
      try{
        const result=await window.ZhigengTransport.post({service:'zlib',action:'search',domain,session:credentials,query,page,filters:{...filters,extension:$('bookExtension').value}});
        status(result.items.length?'找到 '+result.items.length+' 条结果':'来源搜索成功，没有符合条件的图书');
        window.ZhigengZlib.moreAvailable=result.totalPages?result.page<result.totalPages:result.items.length>=24;
        return result.items;
      }catch(e){status(e.message);throw e;}
    },moreAvailable:false
  };
  function init(){
    $('zlibConnect').addEventListener('click',async()=>{
      $('zlibModal').hidden=false;$('zlibClose').focus();
      if(!await window.ZhigengTransport.available())status('当前是静态版。请打开完整版知更，账号搜索需要服务端。');
    });
    $('zlibClose').addEventListener('click',close);
    $('zlibModal').addEventListener('click',e=>{if(e.target===$('zlibModal'))close();});
    document.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
    $('zlibForm').addEventListener('submit',connect);
    $('zlibUseSession').addEventListener('change',()=>{
      const use=$('zlibUseSession').checked;$('zlibSessionFields').hidden=!use;$('zlibPasswordFields').hidden=use;
      $('zlibEmail').required=!use;$('zlibPassword').required=!use;$('zlibUserId').required=use;$('zlibUserKey').required=use;
    });
    $('bookProvider').addEventListener('change',()=>{mode=$('bookProvider').value;sync();window.ZhigengBookAPI?.reload();});
    $('bookExtension').addEventListener('change',()=>window.ZhigengBookAPI?.reload());
    $('zlibDisconnect').addEventListener('click',()=>{
      credentials=null;domain='';mode='public';$('bookProvider').value=mode;sync();status('已清除当前页面的账号会话');window.ZhigengBookAPI?.reload();
    });sync();
    window.ZhigengTransport.available().then(online=>{$('fullVersionHint').hidden=online;});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
