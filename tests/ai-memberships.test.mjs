import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';
const root=resolve(import.meta.dirname,'..');
const read=p=>readFileSync(resolve(root,p),'utf8');
const html=read('index.html');
const app=read('assets/zhigeng-v064.js');
const annual=read('assets/annual-rankings.js');
const script=read('assets/ai-memberships.js');
const data=JSON.parse(read('data/ai-memberships.json'));
const domestic=JSON.parse(read('data/domestic.json'));
const settle=()=>new Promise(done=>setTimeout(done,35));
async function page(){
  const dom=new JSDOM(html,{url:'https://nmm26301-maker.github.io/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window;
  w.scrollTo=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  let external=0;
  w.fetch=async url=>{
    if(String(url).includes('data/domestic.json'))return {ok:true,json:async()=>domestic};
    if(String(url).includes('data/ai-memberships.json'))return {ok:true,json:async()=>data};
    external++;
    throw Error('Unexpected request: '+String(url));
  };
  w.eval(annual);w.eval(script);w.eval(app);
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'));await settle();
  return {dom,w,external:()=>external};
}
function click(w,selector){const el=w.document.querySelector(selector);assert.ok(el,'Missing '+selector);el.click();return el;}
test('user screenshot exact five selected and two backups, replaced eight-site mistake',()=>{
  assert.equal(data.schemaVersion,2);
  assert.equal(data.groups.length,2);
  assert.deepEqual(data.groups.map(x=>x.id),['primary','alternatives']);
  assert.deepEqual(data.groups.map(x=>x.entries.map(e=>e.name)),[
    ['GamsGo','AI订阅价格雷达','特惠购','FamilyPro','银河录像局'],
    ['环球巴士','Relay Radar']
  ]);
  assert.deepEqual(data.groups.map(g=>g.entries.length),[5,2]);
  assert.equal(data.groups.flatMap(g=>g.entries).length,7);
  assert.ok(!data.groups.flatMap(g=>g.entries).some(e=>['sharingful','sharesub','coosub','naifei'].includes(e.id)));
  assert.equal(data.groups[0].entries[1].url,'https://airadar.vip/');
  assert.equal(data.groups[0].entries[2].url,'https://tehuio.com/');
  assert.equal(data.groups[1].entries[1].url,'https://aicodex.chat/subscribe');
  assert.match(html,/href="#memberships"/);
  assert.match(html,/href="#nodes"/,'nodes remains an independent shortcut');
  assert.match(html,/assets\/ai-memberships\.js/);
});
test('mobile AI membership route displays five main cards and two backup cards with safe external links',async()=>{
  const {dom,w,external}=await page();
  click(w,'a[href="#memberships"]');await settle();
  assert.equal(w.document.querySelector('#zgPanel').dataset.page,'memberships');
  assert.deepEqual([...w.document.querySelectorAll('.zg-member-group')].map(g=>g.querySelectorAll('.zg-member-card').length),[5,2]);
  const links=[...w.document.querySelectorAll('.zg-member-card-footer a')];
  assert.equal(links.length,7);
  assert.ok(links.every(a=>a.href.startsWith('https://')&&a.rel.includes('noopener')&&a.rel.includes('noreferrer')&&a.target==='_blank'));
  assert.equal(w.document.querySelectorAll('#zgPanel iframe').length,0);
  assert.equal(external(),0);
  assert.match(w.document.querySelector('#zgMemberStatus').textContent,/5 个主推荐/);
  dom.window.close();
});
test('search and product/delivery filters keep the two groups separate',async()=>{
  const {dom,w}=await page();click(w,'a[href="#memberships"]');await settle();
  const brand=w.document.querySelector('#zgMemberBrand');
  brand.value='Claude';brand.dispatchEvent(new w.Event('change',{bubbles:true}));
  assert.equal(w.document.querySelectorAll('.zg-member-group').length,2);
  assert.ok(w.document.querySelectorAll('.zg-member-group[data-zg-member-group="primary"] .zg-member-card').length>=2);
  const delivery=w.document.querySelector('#zgMemberDelivery');
  delivery.value='compare';delivery.dispatchEvent(new w.Event('change',{bubbles:true}));
  const cards=[...w.document.querySelectorAll('.zg-member-card')];
  assert.deepEqual(cards.map(x=>x.dataset.zgMemberId).sort(),['airadar','relayradar'].sort());
  brand.value='all';brand.dispatchEvent(new w.Event('change',{bubbles:true}));
  delivery.value='all';delivery.dispatchEvent(new w.Event('change',{bubbles:true}));
  const field=w.document.querySelector('#zgMemberSearch');field.value='特惠购';field.dispatchEvent(new w.Event('input',{bubbles:true}));
  assert.deepEqual([...w.document.querySelectorAll('.zg-member-card')].map(x=>x.dataset.zgMemberId),['tehuio']);
  dom.window.close();
});
test('favorites persist through page switches with no double handlers',async()=>{
  const {dom,w}=await page();click(w,'a[href="#memberships"]');await settle();
  click(w,'[data-zg-member-fav="gamsgo"]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('zg-ai-membership-favorites-v1')),['gamsgo']);
  click(w,'a[href="#assistant"]');await settle();
  click(w,'a[href="#memberships"]');await settle();
  assert.ok(w.document.querySelector('[data-zg-member-fav="gamsgo"]').classList.contains('is-saved'));
  click(w,'[data-zg-member-fav="gamsgo"]');
  assert.deepEqual(JSON.parse(w.localStorage.getItem('zg-ai-membership-favorites-v1')),[]);
  dom.window.close();
});
test('refresh re-reads catalog and excludes obsolete saved IDs',async()=>{
  const {dom,w}=await page();
  w.localStorage.setItem('zg-ai-membership-favorites-v1',JSON.stringify(['gamsgo','sharesub','coosub']));
  click(w,'a[href="#memberships"]');await settle();
  assert.equal(w.document.querySelector('#zgMemberSaved').textContent,'已收藏 1 个');
  click(w,'#zgMemberRefresh');await settle();
  assert.equal(w.document.querySelectorAll('.zg-member-card').length,7);
  dom.window.close();
});
