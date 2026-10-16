/* Same-origin gateway for hosted builds; Pages retains direct public requests. */
(() => {
  'use strict';
  const nativeFetch=window.fetch.bind(window);
  let ready=false;
  const health=nativeFetch('/api/gateway',{cache:'no-store',signal:AbortSignal.timeout(3500)})
    .then(async r=>{if(!r.ok)return false;const d=await r.json();return ready=d.ok===true&&d.version===1;}).catch(()=>false);
  const supported=new Set(['api.jikan.moe','graphql.anilist.co','openlibrary.org','www.googleapis.com','api.tvmaze.com','gutendex.com','www.wikidata.org','zh.wikipedia.org','en.wikipedia.org','api.themoviedb.org']);
  window.ZhigengTransport={
    available:()=>health,
    async post(payload){
      if(!await health)throw new Error('当前是静态版，请打开完整版知更使用账号搜索');
      const r=await nativeFetch('/api/gateway',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),cache:'no-store',signal:AbortSignal.timeout(30000)});
      const d=await r.json();if(!r.ok)throw new Error(d.error||'连接失败（'+r.status+'）');return d;
    },
    async fetch(url,options={}){
      let host;try{host=new URL(url,location.href).hostname;}catch{}
      if(supported.has(host)&&await health){
        const auth=new Headers(options.headers||{}).get('Authorization')||'';
        return nativeFetch('/api/gateway',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({service:'catalog',url:String(url),body:options.body,authorization:auth}),signal:options.signal,cache:'no-store'});
      }
      return nativeFetch(url,options);
    }
  };
})();
