import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';

const root=resolve(import.meta.dirname,'..');
const html=readFileSync(resolve(root,'index.html'),'utf8');
const script=readFileSync(resolve(root,'assets/zhigeng-v064.js'),'utf8');
const catalog=JSON.parse(readFileSync(resolve(root,'data/domestic.json'),'utf8'));

const settle=()=>new Promise(done=>setTimeout(done,25));

test('website assets load and editorial catalog matches Android v0.6 recommendations',async()=>{
  assert.equal(catalog.items.length,28);
  assert.equal(new Set(catalog.items.map(item=>item.id)).size,28);
  assert.ok(catalog.items.every(item=>item.editorial===true&&item.source==='知更编辑推荐'));
  assert.match(html,/zhigeng-v064\.css/);
  assert.match(html,/zhigeng-v064\.js/);
  assert.match(html,/href="#rankings"/);
  assert.match(html,/href="#assistant"/);
  assert.match(html,/href="#library"/);
});

test('homepage shows 28 curated entries, rankings and both assistant modes',async()=>{
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;
  window.scrollTo=()=>{};
  window.HTMLElement.prototype.scrollIntoView=()=>{};
  let apiCalls=0;
  window.fetch=async url=>{
    if(String(url).includes('data/domestic.json')) return {ok:true,json:async()=>catalog};
    apiCalls++;
    throw new Error('Unmocked network call');
  };
  window.eval(script);
  window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
  await settle();
  assert.equal(window.document.querySelectorAll('#zgDomesticGrid .zg-work').length,28);
  const typeButton=window.document.querySelector('#zgDomesticFilters [data-zg-type="book"]');
  typeButton.click();
  assert.equal(window.document.querySelectorAll('#zgDomesticGrid .zg-work').length,8);
  window.document.querySelector('a[href="#rankings"]').click();
  await settle();
  assert.equal(window.document.querySelector('#zgPanel').dataset.page,'rankings');
  assert.equal(window.document.querySelectorAll('#zgRankingList .zg-work').length,28);
  window.document.querySelector('[data-zg-rank-source="tmdb"]').click();
  await settle();
  assert.match(window.document.querySelector('#zgRankingCount').textContent,/TMDB.*密钥/);
  assert.equal(apiCalls,0,'Missing TMDB credentials must not call network');
  window.document.querySelector('a[href="#assistant"]').click();
  await settle();
  assert.ok(window.document.querySelector('#zgLoadDuck'));
  assert.equal(window.document.querySelector('#zgDuckFrame iframe'),null,'Duck.ai frame is opt-in');
  window.document.querySelector('[data-zg-ai-tab="fcc"]').click();
  assert.ok(window.document.querySelector('#zgFccURL'));
  assert.ok(window.document.querySelector('#zgFccSend'));
  assert.ok(window.document.querySelector('#zgFccStop').disabled);
  window.close();
});

test('curated save writes to the existing saved-list namespace',async()=>{
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only'});
  const {window}=dom;
  window.fetch=async()=>({ok:true,json:async()=>catalog});
  window.eval(script);
  window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
  await settle();
  const save=window.document.querySelector('#zgDomesticGrid [data-zg-save]');
  save.click();
  const data=JSON.parse(window.localStorage.getItem('zhigeng_saved_v1'));
  assert.equal(Object.keys(data).length,1);
  const record=Object.values(data)[0];
  assert.equal(record.item.source,'知更编辑推荐');
  assert.equal(record.item.ratingBase,'10');
  assert.equal(record.item.score,0,'Editorial score is never fabricated');
  window.close();
});
