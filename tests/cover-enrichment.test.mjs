import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';
const root=resolve(import.meta.dirname,'..');
const cover=readFileSync(resolve(root,'assets/cover-enrichment.js'),'utf8');
const html=readFileSync(resolve(root,'index.html'),'utf8');
const site=readFileSync(resolve(root,'assets/zhigeng-v064.js'),'utf8');
const annual=readFileSync(resolve(root,'assets/annual-rankings.js'),'utf8');
const curated=JSON.parse(readFileSync(resolve(root,'data/domestic.json'),'utf8'));
const delay=()=>new Promise(resolve=>setTimeout(resolve,60));

test('Wikimedia image enrichment matches exact title, rejects unrelated or non-Wikimedia thumbnails',async()=>{
  const dom=new JSDOM('<html></html>',{url:'https://test.example/',runScripts:'outside-only'});
  const w=dom.window;
  w.eval(cover);
  let calls=0;
  const fetcher=async url=>{
    calls++;
    const parsed=new URL(url);
    assert.equal(parsed.searchParams.get('origin'),'*');
    assert.equal(parsed.searchParams.get('pilicense'),'free');
    return {ok:true,json:async()=>({query:{pages:[
      {title:'长安三万里',thumbnail:{source:'https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/example.jpg/360px-example.jpg'}},
      {title:'让子弹飞',thumbnail:{source:'https://tracker.invalid/x.jpg'}},
      {title:'2024年的标题相近条目',thumbnail:{source:'https://upload.wikimedia.org/wikipedia/commons/a.jpg'}}
    ]}})};
  };
  const rows=await w.ZhigengCoverEnrichment.fill([
    {type:'movie',title:'长安三万里',image:''},
    {type:'movie',title:'让子弹飞',image:''},
    {type:'movie',title:'2024年的标题',image:''},
    {type:'book',title:'三体',image:''}
  ],fetcher);
  assert.equal(calls,1);
  assert.match(rows[0].image,/upload\.wikimedia\.org/);
  assert.match(rows[0].imageSource,/非官方海报/);
  assert.equal(rows[1].image,'');
  assert.equal(rows[2].image,'');
  assert.equal(rows[3].image,'');
  dom.window.close();
});

test('annual Chinese TMDB cards actually load real posters and restore text cover if image fails',async()=>{
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;
  w.scrollTo=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  w.localStorage.setItem('zhigeng_tmdb_credential_v1','a'.repeat(32));
  w.fetch=async url=>{
    const path=String(url);
    if(path.includes('data/domestic.json'))return {ok:true,json:async()=>curated};
    if(path.includes('api.themoviedb.org'))return {ok:true,json:async()=>({
      total_pages:1,total_results:1,results:[{
        id:2001,title:'测试国产年度电影',release_date:new Date().getFullYear()+'-02-18',
        poster_path:'/correct-poster.jpg',overview:'来自电影接口',vote_average:8.4,vote_count:16,popularity:91
      }]
    })};
    throw Error('Unexpected network call '+path);
  };
  w.eval(annual);
  w.eval(site);
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
  await delay();
  const link=w.document.querySelector('a[href="#rankings"]');
  assert.ok(link);
  link.click();await delay();
  assert.equal(w.document.querySelector('#zgRankRegion').value,'CN');
  const img=w.document.querySelector('#zgRankingList .zg-poster');
  assert.ok(img,'ranked works with source image must show a poster element');
  assert.equal(img.src,'https://image.tmdb.org/t/p/w342/correct-poster.jpg');
  assert.equal(img.getAttribute('loading'),'lazy');
  assert.equal(img.getAttribute('referrerpolicy'),'no-referrer');
  img.dispatchEvent(new w.Event('load'));
  assert.ok(img.closest('.zg-art').classList.contains('is-loaded'));
  img.dispatchEvent(new w.Event('error'));
  assert.equal(img.hidden,true);
  assert.equal(img.closest('.zg-art').classList.contains('is-loaded'),false);
  assert.match(img.closest('.zg-art').textContent,/测试国产年度电影/);
  dom.window.close();
});


test('every domestic image host is explicitly trusted and fallback restores a readable title',async()=>{
  const html=readFileSync(resolve(root,'index.html'),'utf8');
  const core=readFileSync(resolve(root,'assets/zhigeng-v064.js'),'utf8');
  const catalog=JSON.parse(readFileSync(resolve(root,'data/domestic.json'),'utf8'));
  assert.equal(catalog.items.length,28);
  assert.equal(catalog.items.filter(x=>x.image).length,28);
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;
  w.scrollTo=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  w.fetch=async url=>{
    if(String(url).includes('data/domestic.json'))return {ok:true,json:async()=>catalog};
    throw Error('unexpected '+String(url));
  };
  w.eval(core);
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
  await new Promise(done=>setTimeout(done,40));
  const cards=[...w.document.querySelectorAll('#zgDomesticGrid .zg-work')];
  assert.equal(cards.length,28);
  assert.ok(cards.every(card=>card.querySelector('.zg-poster')));
  const first=cards[0].querySelector('img.zg-poster');
  assert.match(first.src,/v2\.cri\.cn/);
  first.dispatchEvent(new w.Event('error'));
  assert.equal(first.dataset.backupAttempt,'1');
  assert.match(first.src,/assets\.wmoov\.com/);
  first.dispatchEvent(new w.Event('load'));
  assert.ok(first.closest('.zg-art').classList.contains('is-loaded'));
  first.dispatchEvent(new w.Event('error'));
  assert.equal(first.hidden,true);
  assert.equal(first.closest('.zg-art').classList.contains('is-loaded'),false);
  assert.match(first.closest('.zg-art').textContent,/我不是药神/);
  const movie=catalog.items.find(x=>x.type==='movie'&&x.title==='活着');
  const book=catalog.items.find(x=>x.type==='book'&&x.title==='活着');
  assert.notEqual(movie.image,book.image,'movie and book must not share the same poster');
  dom.window.close();
});
