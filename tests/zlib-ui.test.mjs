import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';
import {handleGateway} from '../server/gateway.mjs';
const pause=()=>new Promise(r=>setTimeout(r,45));
test('connect, query, inspect, save, filter, paginate, failure and disconnect work in the page',async()=>{
 const dom=new JSDOM(fs.readFileSync('index.html','utf8'),{url:'https://site.test/index.html',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window;Object.defineProperty(w.document,'readyState',{value:'complete'});
 w.Element.prototype.scrollIntoView=function(){};w.Headers=Headers;w.AbortSignal=AbortSignal;
 const calls=[];let down=false;
 const upstream=async(url,opts)=>{
  calls.push({url,body:opts.body});
  if(down)return new Response('<html>challenge</html>');
  if(url.endsWith('/eapi/user/login'))return Response.json({success:1,user:{id:123,remix_userkey:'session_ABC123'}});
  if(url.endsWith('/eapi/book/search')){
   const params=new URLSearchParams(opts.body),page=Number(params.get('page'));
   return Response.json({success:1,pagination:{current:page,total_pages:2},books:[{id:page,hash:'abc',title:page===1?'三体':'三体 II',author:'刘慈欣',year:'2008',extension:'epub',href:'/book/'+page+'/abc'}]});
  }
  return Response.json({});
 };
 w.fetch=async(url,opts={})=>{
  if(String(url)==='/api/gateway')return handleGateway(new Request('https://site.test/api/gateway',{method:opts.method||'GET',headers:opts.headers,body:opts.body}),upstream);
  if(String(url).includes('data/books.json'))return Response.json({items:[]});
  if(String(url).includes('data/weekly.json'))return Response.json({items:[]});
  return Response.json({data:[],items:[],results:[]});
 };
 try{
  for(const file of ['transport','zlib','books-catalog','book-search','app','book-bridge','book-library'])w.eval(fs.readFileSync('assets/'+file+'.js','utf8'));
  await pause();w.document.querySelector('#categoryTabs [data-category="book"]').click();await pause();
  w.document.getElementById('zlibConnect').click();await pause();
  assert.equal(w.document.getElementById('zlibModal').hidden,false);
  w.document.getElementById('zlibEmail').value='reader@example.test';w.document.getElementById('zlibPassword').value='private-pass';
  w.document.getElementById('zlibForm').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await pause();
  assert.equal(w.document.getElementById('zlibPassword').value,'');assert.equal(w.document.getElementById('bookProvider').value,'zlib');
  w.document.getElementById('bookSearchInput').value='三体';w.document.getElementById('bookSearchForm').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await pause();
  assert.equal(w.document.querySelector('.card-title').textContent,'三体');assert.equal(w.document.getElementById('moreButton').hidden,false);
  w.document.querySelector('.card-title').click();assert.equal(w.document.getElementById('bookDetailsModal').hidden,false);
  assert.equal(w.document.getElementById('bookDetailsOriginal').href,'https://z-lib.gd/book/1/abc');
  w.document.getElementById('bookWantButton').click();assert(w.document.querySelector('.book-shelf-item'));w.document.getElementById('bookDetailsClose').click();
  w.document.getElementById('bookExtension').value='epub';w.document.getElementById('bookExtension').dispatchEvent(new w.Event('change'));await pause();
  assert.equal(new URLSearchParams(calls.at(-1).body).get('extensions[]'),'epub');
  w.document.getElementById('moreButton').click();await pause();assert.equal(w.document.querySelectorAll('.work-card').length,2);assert.equal(w.document.getElementById('moreButton').hidden,true);
  down=true;w.document.getElementById('refreshButton').click();await pause();
  assert.equal(w.document.querySelectorAll('.work-card').length,0,'private provider failure must never display unrelated public catalog or previous cached results');
  assert(w.document.getElementById('notice').textContent.includes('非 JSON'));
  for(let i=0;i<w.localStorage.length;i++)assert(!w.localStorage.getItem(w.localStorage.key(i)).includes('session_ABC123'),'session must stay out of persistent storage');
  w.document.getElementById('zlibDisconnect').click();await pause();assert.equal(w.document.getElementById('bookProvider').value,'public');assert.equal(w.ZhigengZlib.active(),false);
 }finally{w.close();}
});
