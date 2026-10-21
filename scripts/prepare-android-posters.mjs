/* 把已经校验的精选封面打入原生 APK，首次打开无需联网。 */
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {imageExtension} from './build-static-site.mjs';
const origin='https://nmm26301-maker.github.io/zhi-geng/';
const assets=join(process.cwd(),'android/app/src/main/assets');
const path=join(assets,'domestic.json');
const local=JSON.parse(await readFile(path,'utf8'));
const reply=await fetch(origin+'data/domestic.json?apk=607',{signal:AbortSignal.timeout(20000),cache:'no-store'});
if(!reply.ok)throw Error('Poster catalog unavailable: '+reply.status);
const remote=await reply.json();
const byId=new Map(remote.items.map(item=>[item.id,item]));
if(byId.size!==local.items.length)throw Error('Poster catalog count changed');
await mkdir(join(assets,'posters'),{recursive:true});
const report=[];let cursor=0;
async function worker(){
 while(cursor<local.items.length){
  const item=local.items[cursor++],entry=byId.get(item.id);
  if(!entry||entry.title!==item.title||entry.type!==item.type||
    !/^assets\/posters\/[a-f0-9]{24}\.(jpg|png|webp|gif)$/.test(entry.image))throw Error('Mismatched poster: '+item.title);
  const response=await fetch(new URL(entry.image,origin),{signal:AbortSignal.timeout(20000)});
  if(!response.ok)throw Error('Poster unavailable: '+item.title);
  const bytes=Buffer.from(await response.arrayBuffer()),extension=imageExtension(bytes);
  if(!extension||bytes.length>6_000_000||!entry.image.endsWith('.'+extension))throw Error('Invalid poster: '+item.title);
  const asset=entry.image.slice('assets/'.length);
  await writeFile(join(assets,asset),bytes);
  item.imageAsset=asset;
  report.push({id:item.id,title:item.title,asset,bytes:bytes.length,
    sha256:createHash('sha256').update(bytes).digest('hex'),source:entry.imageMirroredFrom||entry.imageSource});
 }
}
await Promise.all(Array.from({length:4},worker));
if(report.length!==28)throw Error('All 28 original curated posters must be bundled');
await writeFile(path,JSON.stringify(local,null,2)+'\n');
await writeFile(join(assets,'poster-manifest.json'),JSON.stringify({count:report.length,items:report},null,2)+'\n');
console.log('Bundled offline native covers: '+report.length+'/28');
