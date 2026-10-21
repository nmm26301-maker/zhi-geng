/* 知更真实图片补全：优先保留原数据源海报；少数无图片作品仅在百科有自由许可图片时补入。 */
(() => {
  'use strict';
  const cache=new Map();
  const supported=new Set(['movie','tv','anime','variety','book']);
  const ambiguous=new Set(['活着','三体','白夜追凶','归途','大圣归来']);
  function coverURL(url) {
    try {const value=new URL(url);return value.protocol==='https:'&&
      (value.hostname==='upload.wikimedia.org'||value.hostname.endsWith('.wikimedia.org'))?value.href:'';}
    catch {return '';}
  }
  function key(item){return item.type+'|'+item.title.trim();}
  function eligible(item){
    return item&&supported.has(item.type)&&typeof item.title==='string'&&
      item.title.trim().length>1&&item.title.trim().length<75&&!ambiguous.has(item.title.trim())&&!item.image;
  }
  async function request(batch,fetcher) {
    const api=new URL('https://zh.wikipedia.org/w/api.php');
    api.searchParams.set('action','query');
    api.searchParams.set('format','json');
    api.searchParams.set('formatversion','2');
    api.searchParams.set('origin','*');
    api.searchParams.set('prop','pageimages');
    api.searchParams.set('piprop','thumbnail');
    api.searchParams.set('pithumbsize','360');
    api.searchParams.set('pilicense','free');
    api.searchParams.set('titles',batch.map(x=>x.title.trim()).join('|'));
    const ctl=new AbortController();
    const timer=setTimeout(()=>ctl.abort(),8500);
    try {
      const res=await (fetcher||fetch)(api.href,{signal:ctl.signal});
      if(!res.ok)throw Error('wiki unavailable');
      const json=await res.json();
      // 只接受规范化后与原始检索词严格一致的百科页，杜绝误用同名电影、图书或其他主题。
      const exact=new Map((json.query?.pages||[]).filter(p=>p&&typeof p.title==='string')
        .map(p=>[p.title.replace(/_/g,' ').trim(),coverURL(p.thumbnail?.source)]));
      for(const item of batch)cache.set(key(item),exact.get(item.title.trim())||'');
    }catch{
      for(const item of batch)if(!cache.has(key(item)))cache.set(key(item),'');
    }finally{clearTimeout(timer);}
  }
  async function fill(items,fetcher) {
    if(!Array.isArray(items)||!items.length)return items;
    const result=items.map(item=>({...item}));
    const unique=new Map();
    for(const item of result)if(eligible(item)&&!cache.has(key(item)))unique.set(key(item),item);
    const all=[...unique.values()].slice(0,45);
    for(let i=0;i<all.length;i+=15)await request(all.slice(i,i+15),fetcher);
    for(const item of result){
      const url=cache.get(key(item));
      if(eligible(item)&&url){item.image=url;item.imageSource='维基百科自由许可配图（非官方海报）';}
    }
    return result;
  }
  window.ZhigengCoverEnrichment={fill,coverURL};
})();