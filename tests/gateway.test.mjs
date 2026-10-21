import {test} from 'node:test';
import assert from 'node:assert/strict';
import {domainOrigin,zlib,catalog,handleGateway} from '../server/gateway.mjs';
const response=(data,opts)=>new Response(JSON.stringify(data),opts);
const account={id:'123',key:'session_ABC123'};
test('rejects private hosts, URL tricks and unapproved redirects before credentials leave',async()=>{
 for(const url of ['http://z-lib.gd','https://127.0.0.1','https://z-lib.gd.attacker.test','https://z-lib.gd:8443','https://u:p@z-lib.gd','https://z-lib.gd/search'])assert.throws(()=>domainOrigin(url));
 assert.equal(domainOrigin('https://z-lib.gd'),'https://z-lib.gd');
 let calls=0;
 await assert.rejects(zlib({domain:'https://z-lib.gd',action:'search',session:account,query:'book'},async(url,options)=>{
  calls++;assert.equal(options.redirect,'manual');return new Response('',{status:302,headers:{Location:'https://attacker.test'}});
 }),/跳转/);assert.equal(calls,1);
});
test('search maps exact, years, languages, formats and pagination without inventing scores',async()=>{
 const result=await zlib({domain:'https://z-lib.gd',action:'search',session:account,query:'三体',page:2,filters:{exact:true,from:2000,to:2026,language:'chi',extension:'epub'}},async(url,opts)=>{
  assert.equal(url,'https://z-lib.gd/eapi/book/search');assert.equal(opts.headers['remix-userkey'],account.key);
  const p=new URLSearchParams(opts.body);assert.equal(p.get('message'),'三体');assert.equal(p.get('page'),'2');assert.equal(p.get('e'),'1');assert.equal(p.get('yearFrom'),'2000');assert.equal(p.get('languages[]'),'chinese');assert.equal(p.get('extensions[]'),'epub');
  return response({success:1,pagination:{total_pages:4},books:[{id:10,hash:'abc',title:'<b>三体</b>',author:'刘慈欣',year:'2008',interestScore:999,extension:'epub',href:'/book/10/abc',cover:'javascript:alert(1)'}]});
 });
 assert.equal(result.page,2);assert.equal(result.totalPages,4);assert.equal(result.items[0].title,'三体');assert.equal(result.items[0].score,0);assert.equal(result.items[0].image,'');
});
test('login fallback uses upstream cookies, never writes password or tokens into URL',async()=>{
 const calls=[];
 const result=await zlib({domain:'https://z-lib.gd',action:'login',email:'reader@example.test',password:'private-pass'},async(url,opts)=>{
  calls.push(url);assert(!url.includes('private-pass'));assert.equal(opts.redirect,'manual');
  if(url.endsWith('/login'))return response({success:0,error:'Authorization failed'},{status:400});
  return response({response:{validationError:null}},{headers:{'set-cookie':'remix_userid=123; Path=/, remix_userkey=session_ABC123; Path=/'}});
 });
 assert.deepEqual(result.session,account);assert.equal(calls.length,2);assert(!JSON.stringify(result).includes('private-pass'));
});
test('invalid session and empty query never become public fallback matches',async()=>{
 await assert.rejects(zlib({domain:'https://z-lib.gd',action:'search',query:'book'}),/账号/);
 await assert.rejects(zlib({domain:'https://z-lib.gd',action:'search',session:account,query:''}),/书名/);
 const result=await zlib({domain:'https://z-lib.gd',action:'search',session:account,query:'unknown'},async()=>response({success:1,books:[]}));assert.deepEqual(result.items,[]);
});
test('public proxy is allowlisted and supports real TVmaze schedule path',async()=>{
 await assert.rejects(catalog({url:'https://example.test/private'}),/列表/);
 const result=await catalog({url:'https://api.tvmaze.com/schedule/web?date=2026-10-16'},async()=>response([]));assert.equal(result.status,200);
});
test('gateway rejects cross origin JSON and never caches sessions',async()=>{
 const r=await handleGateway(new Request('https://site.test/api/gateway',{method:'POST',headers:{origin:'https://other.test','Content-Type':'application/json'},body:'{}'}));assert.equal(r.status,403);assert.equal(r.headers.get('cache-control'),'no-store');
 const bad=await handleGateway(new Request('https://site.test/api/gateway',{method:'POST',headers:{'Content-Type':'application/json'},body:'broken'}));assert.equal(bad.status,400);
});
