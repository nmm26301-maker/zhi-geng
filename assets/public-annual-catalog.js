/* 公开中国年度影视作品索引：取维基百科年份列表，不制造评分、票房或名次。 */
(() => {
  'use strict';
  const cache=new Map();
  const labels={movie:'中国大陆电影作品列表',tv:'中国大陆电视剧列表'};
  const safe=value=>String(value||'').replace(/\[\d+\]/g,'').replace(/\s+/g,' ').trim();
  function titleFor(f) {
    if(f.type==='movie')return f.year+'年'+labels.movie;
    if(f.type==='tv')return '中国大陆电视剧列表 ('+f.year+'年)';
    return '';
  }
  function parseTable(html,type,year,article,DOMParserClass) {
    const document=new DOMParserClass().parseFromString(html,'text/html');
    const tables=[...document.querySelectorAll('table.wikitable')];
    const collected=[];
    const seen=new Set();
    const target=type==='movie'?/^(片名|影片名稱|电影名称|電影名稱)$/:/^(劇名|剧名)$/;
    for(const table of tables){
      const rows=[...table.querySelectorAll('tr')].filter(row=>row.closest('table')===table);
      // 展开 rowspan / colspan，避免月份跨行后把导演、集数误当作片名。
      const grid=[];
      rows.forEach((row,r)=>{
        grid[r] ||= [];
        let col=0;
        for(const cell of [...row.children].filter(el=>['TD','TH'].includes(el.tagName))){
          while(grid[r][col])col++;
          const width=Math.max(1,Math.min(cell.colSpan||1,100));
          const height=Math.max(1,Math.min(cell.rowSpan===0?rows.length-r:cell.rowSpan||1,rows.length-r));
          for(let y=r;y<r+height;y++){
            grid[y] ||= [];
            for(let x=col;x<col+width;x++)grid[y][x]=cell;
          }
          col+=width;
        }
      });
      let titleCol=-1;
      for(const cells of grid){
        const heading=cells.findIndex(cell=>cell?.tagName==='TH'&&target.test(safe(cell.textContent)));
        if(heading>=0){titleCol=heading;continue;}
        if(titleCol<0)continue;
        const cell=cells[titleCol];
        if(!cell||cell.tagName!=='TD'||cell.colSpan>1)continue;
        const copy=cell.cloneNode(true);
        copy.querySelectorAll('sup,.reference,.sortkey,style,script').forEach(node=>node.remove());
        const link=copy.querySelector('a[href^="/wiki/"]');
        const value=safe(link?.textContent||copy.textContent).replace(/[＊*†‡]\s*$/,'').trim();
        if(!value||value.length>70||/^(片名|劇名|剧名|未知|待定|待播|TBA|上映日期|电视剧)$/i.test(value))continue;
        if(seen.has(value))continue;
        const url=link?.getAttribute('href');
        const href=url&&url.startsWith('/wiki/')?'https://zh.wikipedia.org'+url:article;
        seen.add(value);
        collected.push({
          id:'wikipedia:'+type+':'+year+':'+collected.length,type,title:value,year:String(year),
          author:'中文维基百科年度作品索引',tag:'公开作品目录 · 无评分',source:'维基百科年度作品目录',
          summary:'收录于 '+year+' 年度资料列表。仅为作品条目检索，不表示评分、热度或播放量。',
          url:href,image:'',score:0,votes:0,interest:0,directory:true
        });
      }
    }
    return collected;
  }
  async function load(f) {
    if(f.region!=='CN'||!['movie','tv'].includes(f.type))throw Error('公开年度列表只支持国内电影和电视剧');
    const title=titleFor(f);
    const article='https://zh.wikipedia.org/wiki/'+encodeURIComponent(title.replace(/ /g,'_'));
    const id=f.type+':'+f.year;
    if(!cache.has(id)){
      const api=new URL('https://zh.wikipedia.org/w/api.php');
      api.searchParams.set('action','parse');
      api.searchParams.set('format','json');
      api.searchParams.set('formatversion','2');
      api.searchParams.set('origin','*');
      api.searchParams.set('page',title);
      api.searchParams.set('prop','text');
      const controller=new AbortController();
      const timer=setTimeout(()=>controller.abort(),14000);
      try{
        const res=await (f.fetcher||fetch)(api.href,{signal:controller.signal});
        if(!res.ok)throw Error('HTTP '+res.status);
        const json=await res.json();
        const html=typeof json.parse?.text==='string'?json.parse.text:json.parse?.text?.['*'];
        if(!html)throw Error(json.error?.info||'该年暂无公开百科索引');
        const DOMParserClass=window.DOMParser;
        const entries=parseTable(html,f.type,f.year,article,DOMParserClass);
        if(!entries.length)throw Error('该年度页面没有可解析的作品表');
        cache.set(id,{entries,article});
      }finally{clearTimeout(timer);}
    }
    const {entries,article:source}=cache.get(id);
    const p=Number(f.page);
    const items=entries.slice((p-1)*20,p*20);
    return {items,hasMore:p*20<entries.length&&p<25,publicCatalog:true,
      needsTmdb:true,note:'维基百科 '+f.year+' 年 '+(f.type==='movie'?'内地电影':'内地电视剧')+
      ' 条目索引 · 共 '+entries.length+' 条可解析记录 · 无评分／无排名 · 未涵盖港澳台全部作品，也不等于全国完整上映清单 · 原文：'+source};
  }
  window.ZhigengPublicAnnualCatalog={load,parseTable};
})();