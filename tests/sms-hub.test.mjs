import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';

const root=resolve(import.meta.dirname,'..');
const read=path=>readFileSync(resolve(root,path),'utf8');
const html=read('index.html');
const sms=read('assets/sms-hub.js');
const core=read('assets/zhigeng-v064.js');
const annual=read('assets/annual-rankings.js');
const standalone=read('sms-hub/index.html');
const catalog=JSON.parse(read('data/domestic.json'));
const settle=()=>new Promise(done=>setTimeout(done,35));
async function createPage(){
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;
  window.scrollTo=()=>{};
  window.HTMLElement.prototype.scrollIntoView=()=>{};
  window.fetch=async url=>{
    if(String(url).includes('data/domestic.json'))return {ok:true,json:async()=>catalog};
    throw Error('Unexpected request in SMS navigation tests: '+String(url));
  };
  window.eval(annual);
  window.eval(sms);
  window.eval(core);
  window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
  await settle();
  return dom;
}
const click=(win,selector)=>{const el=win.document.querySelector(selector);assert.ok(el,'Missing '+selector);el.click();return el;};

test('previous visit-only SMS website was brought to main with six source links and no translation feature',()=>{
  assert.match(html,/href="#sms"/);
  assert.match(html,/assets\/sms-hub\.js/);
  assert.match(standalone,/receive-smss\.com/);
  assert.match(standalone,/freephonenum\.com/);
  assert.doesNotMatch(standalone,/中文浏览|Google 翻译|网页翻译|translate\.google|translate\.goog/);
  const links=[...standalone.matchAll(/https:\/\/(?:freephonenum\.com|smstome\.com|receive-smss\.com|temporary-phone-number\.com|temp-number\.com|receive-sms-free\.cc)\//g)];
  assert.equal(links.length,6);
});

test('SMS navigation shows six items and never contacts third-party sites without clicking source',async()=>{
  const dom=await createPage();const w=dom.window;
  click(w,'a[href="#sms"]');await settle();
  assert.equal(w.document.querySelector('#zgPanel').dataset.page,'sms');
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,6);
  const sites=[...w.document.querySelectorAll('.zg-sms-card-actions a')];
  assert.equal(sites.length,6);
  assert.ok(sites.every(x=>x.target==='_blank'&&x.rel.includes('noopener')&&x.href.startsWith('https://')));
  assert.equal(w.document.querySelectorAll('.zg-sms-card iframe').length,0);
  assert.equal(w.ZhigengSmsHub.sites.length,6);
  assert.ok(!w.document.querySelector('#zgPanel').textContent.includes('中文浏览'));
  dom.window.close();
});

test('search, favorite, hide and hidden recovery follow original local storage namespace',async()=>{
  const dom=await createPage();const w=dom.window;
  click(w,'a[href="#sms"]');await settle();
  const search=w.document.querySelector('#zgSmsSearch');
  search.value='smstome';search.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,1);
  click(w,'.zg-sms-card [data-zg-sms-star]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('sms-nav-fav-1')),['https://smstome.com/']);
  click(w,'[data-zg-sms-filter="fav"]');
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,1);
  click(w,'.zg-sms-card [data-zg-sms-hide]');
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,0);
  assert.deepEqual(JSON.parse(w.localStorage.getItem('sms-nav-hide-1')),['https://smstome.com/']);
  click(w,'[data-zg-sms-filter="hidden"]');
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,1);
  click(w,'.zg-sms-card [data-zg-sms-hide]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('sms-nav-hide-1')),[]);
  dom.window.close();
});

test('re-enter SMS page without duplicate event handlers, and preserve favorite',async()=>{
  const dom=await createPage();const w=dom.window;
  click(w,'a[href="#sms"]');await settle();
  const selected=w.document.querySelector('.zg-sms-card [data-zg-sms-star]').dataset.zgSmsStar;
  click(w,'a[href="#assistant"]');await settle();
  click(w,'a[href="#sms"]');await settle();
  click(w,'.zg-sms-card [data-zg-sms-star]');
  const favorites=JSON.parse(w.localStorage.getItem('sms-nav-fav-1'));
  assert.equal(favorites.length,1);
  assert.equal(favorites[0],selected);
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,6);
  dom.window.close();
});

test('all hidden storage records are constrained to the six verified URLs',async()=>{
  const dom=await createPage();const w=dom.window;
  w.localStorage.setItem('sms-nav-hide-1',JSON.stringify(['https://unknown.example/', 'https://smstome.com/']));
  click(w,'a[href="#sms"]');await settle();
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,5);
  click(w,'[data-zg-sms-filter="hidden"]');
  assert.equal(w.document.querySelectorAll('.zg-sms-card').length,1);
  assert.match(w.document.querySelector('.zg-sms-card').textContent,/SMSToMe/);
  dom.window.close();
});
