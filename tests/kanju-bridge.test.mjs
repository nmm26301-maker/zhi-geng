import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';

const root=resolve(import.meta.dirname,'..');
const html=readFileSync(resolve(root,'index.html'),'utf8');
const bridge=readFileSync(resolve(root,'assets/kanju-bridge.js'),'utf8');
const annual=readFileSync(resolve(root,'assets/annual-rankings.js'),'utf8');
const app=readFileSync(resolve(root,'assets/zhigeng-v064.js'),'utf8');
const catalog=JSON.parse(readFileSync(resolve(root,'data/domestic.json'),'utf8'));
const settle=()=>new Promise(resolve=>setTimeout(resolve,30));
async function page() {
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;
  window.scrollTo=()=>{};
  window.HTMLElement.prototype.scrollIntoView=()=>{};
  window.fetch=async url=>{
    if(String(url).includes('data/domestic.json'))return {ok:true,json:async()=>catalog};
    throw new Error('Unexpected third-party request in test: '+url);
  };
  window.eval(annual);
  window.eval(bridge);
  window.eval(app);
  window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
  await settle();
  return dom;
}
test('website has visible official Kanju AI entry and no auto iframe on navigation',async()=>{
  assert.match(html,/assets\/kanju-bridge\.js/);
  assert.match(html,/href="#kanju"/);
  const dom=await page();
  const {window}=dom;
  window.document.querySelector('a[href="#kanju"]').click();
  await settle();
  assert.equal(window.document.querySelector('#zgPanel').dataset.page,'kanju');
  assert.ok(window.document.querySelector('#zgKanjuQuery'));
  assert.equal(window.document.querySelector('#zgKanjuFrameHost iframe'),null);
  assert.equal(window.document.querySelector('#zgKanjuExternal').href,'https://kanju.ai/');
  assert.match(window.document.querySelector('#zgKanjuStatus').textContent,/尚未加载/);
  dom.window.close();
});
test('Kanju AI search is explicit, correctly encoded and always has external official fallback',async()=>{
  const dom=await page();const {window}=dom;
  window.document.querySelector('a[href="#kanju"]').click();
  await settle();
  const field=window.document.querySelector('#zgKanjuQuery');
  field.value='漫长的季节 & 第二季';
  field.dispatchEvent(new window.Event('input',{bubbles:true}));
  const external=window.document.querySelector('#zgKanjuExternal');
  const official=new URL(external.href);
  assert.equal(official.origin,'https://kanju.ai');
  assert.equal(official.pathname,'/session/new');
  assert.equal(official.searchParams.get('prompt'),'漫长的季节 & 第二季');
  assert.equal(window.document.querySelector('#zgKanjuFrameHost iframe'),null);
  const submit=window.document.querySelector('#zgKanjuForm');
  submit.dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));
  const frame=window.document.querySelector('#zgKanjuFrameHost iframe');
  assert.ok(frame);
  assert.equal(new URL(frame.src).searchParams.get('prompt'),'漫长的季节 & 第二季');
  assert.equal(frame.referrerPolicy,'no-referrer');
  assert.equal(external.getAttribute('target'),'_blank');
  assert.match(window.document.querySelector('#zgKanjuStatus').textContent,/能否正常显示/);
  dom.window.close();
});
test('recommendation shortcut pre-fills title without automatically contacting Kanju',async()=>{
  const dom=await page();const {window}=dom;
  const shortcut=window.document.querySelector('#zgDomesticGrid [data-zg-kanju-title]');
  assert.ok(shortcut,'film/tv recommendation card has Kanju action');
  const title=shortcut.dataset.zgKanjuTitle;
  shortcut.click();await settle();
  assert.equal(window.document.querySelector('#zgPanel').dataset.page,'kanju');
  assert.equal(window.document.querySelector('#zgKanjuQuery').value,title);
  assert.equal(window.document.querySelector('#zgKanjuFrameHost iframe'),null);
  dom.window.close();
});
test('legacy film cards gain a Kanju action but books do not',async()=>{
  const dom=await page();const {window}=dom;
  const grid=window.document.querySelector('#workGrid');
  grid.innerHTML='<article class="work-card" data-id="movie:1"><h4 class="card-title">测试电影</h4></article>'+
    '<article class="work-card" data-id="book:/works/x"><h4 class="card-title">测试图书</h4></article>';
  await settle();
  assert.equal(grid.querySelectorAll('[data-zg-kanju-title]').length,1);
  assert.equal(grid.querySelector('[data-zg-kanju-title]').dataset.zgKanjuTitle,'测试电影');
  dom.window.close();
});
