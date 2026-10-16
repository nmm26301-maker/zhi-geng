import fs from 'node:fs';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';

const html=fs.readFileSync('index.html','utf8');
const dom=new JSDOM(html,{url:'https://example.test/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window;
Object.defineProperty(w.document,'readyState',{value:'complete',configurable:true});
w.Element.prototype.scrollIntoView=function(){};
const seed=JSON.parse(fs.readFileSync('data/books.json','utf8'));
let openRequests=0,googleRequests=0,catalogRequests=0;
const successful=value=>({ok:true,status:200,json:async()=>value});
w.fetch=async(url)=>{
  const path=String(url);
  if(path.includes('data/books.json')){catalogRequests++;return successful(seed);}
  if(path.includes('openlibrary.org/search.json')){openRequests++;throw new w.TypeError('Failed to fetch');}
  if(path.includes('googleapis.com/books')){googleRequests++;return {ok:false,status:429,json:async()=>({})};}
  if(path.includes('data/weekly.json'))return successful({items:[]});
  if(path.includes('api.jikan.moe'))return successful({data:[]});
  if(path.includes('api.tvmaze.com'))return successful([]);
  return successful({results:[],items:[]});
};
for(const source of ['assets/books-catalog.js','assets/app.js','assets/book-bridge.js','assets/book-library.js']){
  w.eval(fs.readFileSync(source,'utf8'));
}
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
await wait(60);
w.document.querySelector('#categoryTabs [data-category="book"]').click();
await wait(120);
assert.equal(w.document.querySelectorAll('.work-card').length,seed.items.length,'show real local catalog despite live APIs being blocked');
assert.equal(openRequests,0,'do not call Open Library during cached browsing');
assert.equal(googleRequests,0,'do not call Google Books during cached browsing');
assert(w.document.querySelector('#dataMode').textContent.includes('缓存'),'display snapshot status');

w.document.getElementById('refreshButton').click();
await wait(110);
assert.equal(openRequests,1,'manual update tries Open Library once');
assert.equal(googleRequests,1,'manual update tries Google Books once');
assert.equal(w.document.querySelectorAll('.work-card').length,seed.items.length,'fallback to book snapshot after two failures');
assert(w.localStorage.getItem('zhigeng_google_books_429_until_v1'),'429 starts cooldown');

w.document.getElementById('refreshButton').click();
await wait(110);
assert.equal(googleRequests,1,'cooldown prevents repeat Google Books calls');
assert.equal(w.document.querySelectorAll('.work-card').length,seed.items.length,'book snapshot persists in cooldown');

const search=w.document.getElementById('searchInput');
search.value=seed.items[0].title;
search.dispatchEvent(new w.Event('input',{bubbles:true}));
search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
await wait(110);
assert(w.document.querySelectorAll('.work-card').length>=1,'matched cached search survives upstream failures');

search.value='zzzx-unmatchable-title-2026';
search.dispatchEvent(new w.Event('input',{bubbles:true}));
search.dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
await wait(110);
assert.equal(w.document.querySelectorAll('.work-card').length,0,'do not show unrelated books as search matches');
assert(w.document.getElementById('notice').textContent.includes('实时数据接口暂不可用'),'explain upstream failure rather than fabricate results');

w.document.getElementById('bookLanguage').value='eng';
w.document.getElementById('bookFilterApply').click();
await wait(100);
w.document.getElementById('resetButton').click();
await wait(100);
assert.equal(w.document.getElementById('bookLanguage').value,'','reset clears advanced book filters');
assert.equal(w.document.querySelectorAll('.work-card').length,seed.items.filter(x=>x.year===2026).length,'reset returns to 2026 matching records');
assert.equal(catalogRequests,1,'same-origin catalog is not repeatedly downloaded');

console.log('Offline + CORS + 429 regression passed. Cached records '+seed.items.length+'; Google calls '+googleRequests+'.');
w.close();
