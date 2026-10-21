import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {mirrorPoster,imageExtension} from '../scripts/build-static-site.mjs';

const jpeg=Buffer.from([255,216,255,224,0,16,74,70,73,70,0,0,1,0,0,0,255,217]);
test('poster cache checks bytes, retries backup, and reuses files without network',async()=>{
 const root=await mkdtemp(join(tmpdir(),'zhigeng-posters-'));
 const output=join(root,'out'),cache=join(root,'cache');
 await mkdir(output);await mkdir(cache);
 try {
   const original={id:'film:1',title:'影片',image:'https://primary.example/a',imageBackup:'https://backup.example/b'};
   const calls=[];
   const fetcher=async url=>{
     calls.push(url);
     return new Response(url===original.image?Buffer.from('<html>not an image</html>'):jpeg,
       {headers:{'Content-Type':'image/jpeg'}});
   };
   const result=await mirrorPoster(original,{output,cache,fetcher});
   assert.deepEqual(calls,[original.image,original.imageBackup]);
   assert.match(result.image,/^assets\/posters\/[a-f0-9]{24}\.jpg$/);
   assert.equal(result.imageOriginal,original.image);
   assert.equal(result.imageMirroredFrom,original.imageBackup);
   assert.deepEqual(await readFile(join(output,result.image.split('/').pop())),jpeg);
   const cached=await mirrorPoster({...original,image:original.imageBackup,imageBackup:''},
     {output,cache,fetcher:()=>{throw Error('must use cache');}});
   assert.equal(cached.image,result.image);
   assert.equal(imageExtension(Buffer.from('<html>pretend jpeg</html>')),'');
 }finally{await rm(root,{recursive:true,force:true});}
});
test('unavailable poster remains an external source and never becomes a fake cached cover',async()=>{
 const root=await mkdtemp(join(tmpdir(),'zhigeng-posters-'));
 try{
   const item={title:'影片',image:'https://source.example/cover',imageBackup:''};
   const result=await mirrorPoster(item,{output:root,cache:root,
     fetcher:async()=>new Response('unavailable',{status:503})});
   assert.deepEqual(result,item);
   assert.equal(result.imageMirroredFrom,undefined);
 }finally{await rm(root,{recursive:true,force:true});}
});
