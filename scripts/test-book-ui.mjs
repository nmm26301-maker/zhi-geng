import fs from 'node:fs';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';

const source=fs.readFileSync('index.html','utf8');
const dom=new JSDOM(source,{url:'https://example.test/zhi-geng/',runScripts:'outside-only',pretendToBeVisual:true});
const {window:w}=dom;
Object.defineProperty(w.document,'readyState',{value:'complete',configurable:true});
w.Element.prototype.scrollIntoView=function(){};
const seen=[];
w.fetch=async (url) => {
  seen.push(String(url));
  const path=String(url);
  const json=(value)=>({ok:true,status:200,json:async()=>value});
  if(path.includes('openlibrary.org/search.json'))
    return json({docs:[{key:'/works/OL123W',title:'测试图书',author_name:['测试作者'],
      first_publish_year:2026,cover_i:123,ratings_average:4.1,ratings_count:35,
      readinglog_count:40,edition_count:4,language:['chi'],ebook_access:'borrowable'}]});
  if(path.includes('/works/OL123W.json'))return json({description:'这是一本用于检查书籍详情功能的测试书。'});
  if(path.includes('api.jikan.moe'))return json({data:[]});
  if(path.includes('api.tvmaze.com'))return json([]);
  if(path.includes('data/weekly.json'))return json([]);
  return json({results:[],items:[]});
};
for(const script of ['assets/app.js','assets/book-bridge.js','assets/book-library.js']){
  w.eval(fs.readFileSync(script,'utf8'));
}
const sleep=(ms)=>new Promise(r=>setTimeout(r,ms));
await sleep(80);
const category=w.document.querySelector('#categoryTabs [data-category="book"]');
assert(category,'book tab exists');
category.click();
await sleep(150);
assert.equal(w.document.getElementById('bookStudio').hidden,false,'book studio appears on book tab');
assert(w.document.querySelectorAll('.work-card').length>0,'book search returns metadata cards');
const bookTitle=w.document.querySelector('.card-title');
assert(bookTitle.textContent.includes('测试图书'),'book title rendered');
bookTitle.click();
assert.equal(w.document.getElementById('bookDetailsModal').hidden,false,'book detail dialog opens');
w.document.getElementById('bookWantButton').click();
const saved=JSON.parse(w.localStorage.getItem('zhigeng_library_shelf_v1'));
assert(Object.values(saved).some(entry=>entry.status==='want'),'want-to-read state persisted');
assert(w.document.querySelector('#bookShelfList .book-shelf-item'),'book shelf reflects saved item');
w.document.getElementById('bookDetailsClose').click();
assert.equal(w.document.getElementById('bookDetailsModal').hidden,true,'book detail dialog closes');
w.document.getElementById('bookLanguage').value='eng';
w.document.getElementById('bookYearFrom').value='2018';
w.document.getElementById('bookYearTo').value='2022';
w.document.getElementById('bookFilterApply').click();
await sleep(120);
const bookRequests=seen.filter(url=>url.includes('openlibrary.org/search.json'));
assert(bookRequests.length>=2,'book query retriggered');
const latest=new URL(bookRequests.at(-1));
assert(latest.searchParams.get('q').includes('language:eng'),'language reaches source query');
assert(latest.searchParams.get('q').includes('first_publish_year:[2018 TO 2022]'),'year range reaches source query');
assert.equal(w.document.querySelector('#yearSelect').value,'all','wide year filter resets single-year selector');
console.log('Book UI browser-like smoke passed: search, category, details, shelf, filters.');
w.close();
