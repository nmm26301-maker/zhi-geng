import fs from 'node:fs';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';

const html=fs.readFileSync('index.html','utf8');
const dom=new JSDOM(html,{url:'https://example.test/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window;
Object.defineProperty(w.document,'readyState',{value:'complete',configurable:true});
w.Element.prototype.scrollIntoView=function(){};
const seed=JSON.parse(fs.readFileSync('data/books.json','utf8'));
const calls=[];
const ok=value=>({ok:true,status:200,json:async()=>value});
w.fetch=async(url)=>{
  const path=String(url);calls.push(path);
  if(path.includes('data/books.json'))return ok(seed);
  if(path.includes('wikidata.org/w/api.php')){
    const u=new URL(path),q=u.searchParams.get('search');
    if(q==='三体')return ok({search:[
      {id:'Q99991011',label:'三体',description:'中国作家刘慈欣创作的长篇科幻小说'},
      {id:'Q99991012',label:'三体电影',description:'2026年电影'}
    ]});
    return ok({search:[]});
  }
  if(path.includes('wikipedia.org/w/api.php')){
    const u=new URL(path),q=u.searchParams.get('gsrsearch');
    if(q==='活着')return ok({query:{pages:{
      '11002':{pageid:11002,title:'活着 (小说)',extract:'《活着》是中国作家余华创作的长篇小说。该作品讲述主人公的经历。'},
      '11003':{pageid:11003,title:'活着 (电影)',extract:'《活着》是一部改编自同名小说的电影。'}
    }}});
    return ok({query:{pages:{}}});
  }
  if(path.includes('gutendex.com/books/')){
    const q=new URL(path).searchParams.get('search');
    if(q==='Pride and Prejudice')return ok({results:[{
      id:1342,title:'Pride and Prejudice',authors:[{name:'Austen, Jane'}],
      download_count:92411,languages:['en'],subjects:['English literature'],
      formats:{'image/jpeg':'https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg'}
    }]});
    return ok({results:[]});
  }
  if(path.includes('openlibrary.org/search.json'))throw new w.TypeError('Network blocked');
  if(path.includes('googleapis.com/books'))return {ok:false,status:429,json:async()=>({})};
  if(path.includes('data/weekly.json'))return ok({items:[]});
  if(path.includes('api.jikan.moe'))return ok({data:[]});
  if(path.includes('api.tvmaze.com'))return ok([]);
  return ok({results:[],items:[]});
};
for(const path of ['assets/books-catalog.js','assets/book-search.js','assets/app.js','assets/book-bridge.js','assets/book-library.js']){
  w.eval(fs.readFileSync(path,'utf8'));
}
const pause=ms=>new Promise(done=>setTimeout(done,ms));
await pause(100);
w.document.querySelector('#categoryTabs [data-category="book"]').click();
await pause(130);
assert.equal(w.document.querySelector('#yearSelect').value,'all','book selection must not default to 2026');

const form=w.document.getElementById('bookSearchForm');
const input=w.document.getElementById('bookSearchInput');
input.value='三体';form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
await pause(160);
const bookTitles=[...w.document.querySelectorAll('.work-card .card-title')].map(x=>x.textContent);
assert(bookTitles.includes('三体'),'Chinese book search should return authentic Wikidata matching novel');
assert(!bookTitles.includes('三体电影'),'non-book entities must be filtered');
assert.equal(w.document.querySelector('#yearSelect').value,'all','book query searches every year');
assert(calls.some(u=>u.includes('wikidata.org/w/api.php')&&new URL(u).searchParams.get('search')==='三体'));
assert(!calls.some(u=>u.includes('openlibrary.org/search.json')),'do not wait on blocked Open Library after independent search succeeds');

input.value='活着';form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
await pause(140);
const wikiTitles=[...w.document.querySelectorAll('.work-card .card-title')].map(x=>x.textContent);
assert(wikiTitles.includes('活着 (小说)'),'Chinese Wikipedia book fallback should find a novel when Wikidata returns no result');
assert(!wikiTitles.includes('活着 (电影)'),'Wikipedia fallback should exclude movie records');

input.value='Pride and Prejudice';form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
await pause(140);
assert([...w.document.querySelectorAll('.card-title')].some(x=>x.textContent==='Pride and Prejudice'),'English book search returns Gutendex record');
assert(w.document.querySelector('.card-source').textContent.includes('Project Gutenberg'),'source must be labeled');
assert(w.document.getElementById('bookHistoryChips').textContent.includes('Pride and Prejudice'),'search history records book query');

input.value='zzzz-unmatchable-314159';form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
await pause(150);
assert.equal(w.document.querySelectorAll('.work-card').length,0,'no invented matches for unknown book query');
assert(w.document.getElementById('notice').textContent.includes('实时数据接口暂不可用'),'remaining blocked sources are explained');
console.log('Real book search flow passed: Chinese novel, title filtering, English Gutenberg, all-years, history and no fabricated results.');
w.close();
