import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';

const root=resolve(import.meta.dirname,'..');
const read=path=>readFileSync(resolve(root,path),'utf8');
const pageHtml=read('index.html');
const moduleCode=read('assets/node-directory.js');
const appCode=read('assets/zhigeng-v064.js');
const annualCode=read('assets/annual-rankings.js');
const nodeData=JSON.parse(read('data/node-sites.json'));
const domestic=JSON.parse(read('data/domestic.json'));
const settle=()=>new Promise(done=>setTimeout(done,45));

async function createPage(){
  const dom=new JSDOM(pageHtml,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;
  w.scrollTo=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  let requests=0;
  w.fetch=async url=>{
    requests++;
    if(String(url).includes('data/domestic.json'))return {ok:true,json:async()=>domestic};
    if(String(url).includes('data/node-sites.json'))return {ok:true,json:async()=>nodeData};
    throw Error('Unexpected network call: '+String(url));
  };
  w.eval(annualCode);
  w.eval(moduleCode);
  w.eval(appCode);
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'));
  await settle();
  return {dom,w,requests:()=>requests};
}
function tap(w,selector){
  const el=w.document.querySelector(selector);
  assert.ok(el,'Missing '+selector);
  el.click();
  return el;
}

test('exactly two independent five-item lists, original FreeCat entry, unique HTTPS addresses',()=>{
  assert.equal(nodeData.groups.length,2);
  assert.equal(nodeData.groups[0].id,'proxy');
  assert.equal(nodeData.groups[1].id,'airport');
  assert.deepEqual(nodeData.groups.map(g=>g.entries.length),[5,5]);
  assert.equal(new Set(nodeData.groups.flatMap(g=>g.entries.map(x=>x.id))).size,10);
  assert.equal(new Set(nodeData.groups.flatMap(g=>g.entries.map(x=>x.url))).size,10);
  for(const site of nodeData.groups.flatMap(g=>g.entries)){
    const url=new URL(site.url);
    assert.equal(url.protocol,'https:');
    assert.equal(url.username,'');
    assert.equal(url.password,'');
    assert.equal(url.search,'');
  }
  assert.equal(nodeData.groups[1].entries[0].url,'https://us.freecat.cloud/register');
  assert.match(pageHtml,/href="#nodes"/);
  assert.match(pageHtml,/node-directory\.js/);
});

test('node entry opens two separately titled groups, 5+5 cards and never preloads shopping sites',async()=>{
  const {dom,w,requests}=await createPage();
  tap(w,'a[href="#nodes"]');
  await settle();
  assert.equal(w.document.querySelector('#zgPanel').dataset.page,'nodes');
  const groups=[...w.document.querySelectorAll('.zg-node-group')];
  assert.equal(groups.length,2);
  assert.deepEqual(groups.map(g=>g.querySelectorAll('.zg-node-card').length),[5,5]);
  assert.match(groups[0].textContent,/静态/);
  assert.match(groups[1].textContent,/低价机场/);
  assert.equal(requests(),2,'Only local catalogs may be fetched');
  const anchors=[...w.document.querySelectorAll('.zg-node-card-actions a')];
  assert.equal(anchors.length,10);
  assert.ok(anchors.every(x=>x.target==='_blank'&&x.rel.includes('noopener')&&x.rel.includes('noreferrer')));
  assert.equal(w.document.querySelectorAll('#zgNodesSections iframe').length,0);
  dom.window.close();
});

test('search filters both groups independently, without mixing services',async()=>{
  const {dom,w}=await createPage();
  tap(w,'a[href="#nodes"]');await settle();
  const input=w.document.querySelector('#zgNodesSearch');
  input.value='静态 ISP';
  input.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.equal(w.document.querySelectorAll('.zg-node-group').length,2);
  assert.equal(w.document.querySelectorAll('.zg-node-group[data-zg-node-group="airport"] .zg-node-card').length,0);
  assert.ok(w.document.querySelectorAll('.zg-node-group[data-zg-node-group="proxy"] .zg-node-card').length>=2);
  assert.match(w.document.querySelector('#zgNodesCount').textContent,/额外控制台入口 1 个/);
  dom.window.close();
});

test('favorites persist after navigating away and back without duplicate toggle',async()=>{
  const {dom,w}=await createPage();
  tap(w,'a[href="#nodes"]');await settle();
  tap(w,'.zg-node-card [data-zg-node-favorite="webshare"]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('zhigeng-node-fav-v1')),['webshare']);
  tap(w,'a[href="#sms"]');await settle();
  tap(w,'a[href="#nodes"]');await settle();
  assert.ok(w.document.querySelector('.zg-node-card [data-zg-node-favorite="webshare"]').classList.contains('is-saved'));
  tap(w,'.zg-node-card [data-zg-node-favorite="webshare"]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('zhigeng-node-fav-v1')),[]);
  dom.window.close();
});

test('requested Yifen dashboard is a separate exact deep link, not an eleventh provider',async()=>{
  assert.equal(nodeData.schemaVersion,2);
  assert.equal(nodeData.groups.flatMap(group=>group.entries).length,10);
  assert.equal(nodeData.shortcuts.length,1);
  const dashboard=nodeData.shortcuts[0];
  assert.equal(dashboard.id,'yifen-dashboard');
  assert.equal(dashboard.providerId,'yifen');
  assert.equal(dashboard.url,'https://xn--4gqx1hgtfdmt.com/#/dashboard');
  const source=nodeData.groups.find(g=>g.id==='airport').entries.find(x=>x.id===dashboard.providerId);
  assert.equal(new URL(dashboard.url).origin,new URL(source.url).origin);
  const {dom,w,requests}=await createPage();
  tap(w,'a[href="#nodes"]');await settle();
  assert.deepEqual([...w.document.querySelectorAll('.zg-node-group')].map(g=>g.querySelectorAll('.zg-node-card').length),[5,5]);
  assert.equal(w.document.querySelectorAll('.zg-node-shortcut').length,1);
  const extra=w.document.querySelector('.zg-node-group[data-zg-node-group="airport"] .zg-node-shortcut-link');
  assert.ok(extra);
  assert.equal(extra.href,dashboard.url);
  assert.equal(extra.target,'_blank');
  assert.match(extra.rel,/noopener/);
  assert.match(extra.rel,/noreferrer/);
  assert.match(w.document.querySelector('#zgNodesCount').textContent,/额外控制台入口 1 个/);
  assert.equal(requests(),2,'navigation must not prefetch external service login or billing pages');
  dom.window.close();
});

test('mobile nav retains SMS and Kanju separately alongside new node category',()=>{
  assert.match(pageHtml,/<a class="zg-mobile-link" href="#sms">/);
  assert.match(pageHtml,/<a class="zg-mobile-link" href="#kanju">/);
  assert.match(pageHtml,/<a class="zg-mobile-link" href="#nodes">/);
});
