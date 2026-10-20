import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';
const root=resolve(import.meta.dirname,'..');
const catalog=readFileSync(resolve(root,'assets/public-annual-catalog.js'),'utf8');
const annual=readFileSync(resolve(root,'assets/annual-rankings.js'),'utf8');
function setup(){
 const dom=new JSDOM('<html></html>',{url:'https://test.example/',runScripts:'outside-only'});
 dom.window.eval(catalog);dom.window.eval(annual);
 return dom;
}
const movies='<table class="wikitable"><tbody><tr><th>日期</th><th>片名</th><th>导演</th></tr>'+
  Array.from({length:25},(_,i)=>'<tr><td>2月'+(i+1)+'日</td><td><a href="/wiki/电影_'+i+'">真实电影'+i+'</a></td><td>导演</td></tr>').join('')+
  '</tbody></table>';
test('free domestic wiki directory is unranked, year-scoped, source-linked and paginated',async()=>{
 const dom=setup();const w=dom.window;let count=0;let url;
 const fetcher=async input=>{
   count++;url=new URL(input);
   return {ok:true,json:async()=>({parse:{text:movies}})};
 };
 const f={year:2024,type:'movie',region:'CN',sort:'rating',page:1,credential:'',fetcher};
 const p1=await w.ZhigengAnnualRankings.load(f);
 assert.equal(p1.items.length,20);
 assert.equal(p1.hasMore,true);
 assert.equal(p1.publicCatalog,true);
 assert.equal(p1.needsTmdb,true);
 assert.ok(p1.items.every(x=>x.directory===true&&x.score===0&&!x.rank&&x.url.startsWith('https://zh.wikipedia.org/wiki/')));
 assert.match(p1.note,/无评分／无排名/);
 assert.equal(url.searchParams.get('page'),'2024年中国大陆电影作品列表');
 assert.equal(url.searchParams.get('origin'),'*');
 const p2=await w.ZhigengAnnualRankings.load({...f,page:2});
 assert.equal(p2.items.length,5);assert.equal(p2.hasMore,false);
 assert.equal(count,1,'same year should use cached public listing');
 dom.window.close();
});
test('public Chinese drama index parses genuine show column without ranking',async()=>{
 const dom=setup();
 const sample='<table class="wikitable"><tbody><tr><th>剧名</th><th>集数</th></tr><tr><td><a href="/wiki/电视剧甲">电视剧甲</a></td><td>24</td></tr></tbody></table>';
 let param;
 const rows=await dom.window.ZhigengPublicAnnualCatalog.load({year:2024,type:'tv',region:'CN',page:1,fetcher:async url=>{
   param=new URL(url).searchParams.get('page');
   return {ok:true,json:async()=>({parse:{text:sample}})};
 }});
 assert.equal(param,'中国大陆电视剧列表 (2024年)');
 assert.equal(rows.items[0].title,'电视剧甲');
 assert.equal(rows.items[0].score,0);
 dom.window.close();
});
test('unavailable Wikipedia directory never pretends to have verified rankings',async()=>{
 const dom=setup();let calls=0;
 const r=await dom.window.ZhigengAnnualRankings.load({year:2021,type:'movie',region:'CN',sort:'popular',page:1,credential:'',fetcher:async()=>{
   calls++;throw Error('network unavailable');
 }});
 assert.equal(calls,1);
 assert.equal(r.items.length,0);
 assert.equal(r.needsTmdb,true);
 assert.match(r.note,/暂不可用/);
 dom.window.close();
});
