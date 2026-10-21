/* 构建时缓存真实封面，浏览器从本站读取；失败保留原始来源和备用图。 */
import {cp,mkdir,readFile,writeFile,rm} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

export function imageExtension(bytes) {
  if(bytes.length<16)return '';
  if(bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'jpg';
  if(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))return 'png';
  if(/^GIF8[79]a$/.test(bytes.subarray(0,6).toString('ascii')))return 'gif';
  if(bytes.subarray(0,4).toString()==='RIFF'&&bytes.subarray(8,12).toString()==='WEBP')return 'webp';
  return '';
}
export async function mirrorPoster(item,{output,cache,fetcher=fetch}) {
  const candidates=[...new Set([item.image,item.imageBackup].filter(Boolean))];
  for(const url of candidates){
    try {
      const remote=new URL(url);
      if(remote.protocol!=='https:'||remote.username||remote.password)continue;
      const key=createHash('sha256').update(url).digest('hex').slice(0,24);
      const cachePath=join(cache,key);
      let bytes;
      try {bytes=await readFile(cachePath);if(!imageExtension(bytes))bytes=null;}catch{}
      if(!bytes){
        const response=await fetcher(url,{signal:AbortSignal.timeout(12000),
          headers:{'User-Agent':'Mozilla/5.0 (compatible; ZhigengCoverCache/1.0)','Accept':'image/*'}});
        if(!response.ok||!String(response.headers.get('content-type')||'').startsWith('image/'))continue;
        if(Number(response.headers.get('content-length'))>8*1024*1024)continue;
        if(response.url&&new URL(response.url).protocol!=='https:')continue;
        const chunks=[];let size=0;
        for await(const chunk of response.body){
          size+=chunk.length;
          if(size>8*1024*1024)throw Error('image too large');
          chunks.push(chunk);
        }
        bytes=Buffer.concat(chunks);
        if(!imageExtension(bytes))continue;
        await writeFile(cachePath,bytes);
      }
      const extension=imageExtension(bytes);
      if(!extension)continue;
      const name=key+'.'+extension;
      await writeFile(join(output,name),bytes);
      return {...item,image:'assets/posters/'+name,imageBackup:item.imageBackup||item.image,
        imageSource:item.imageSource||url,imageOriginal:item.image,imageMirroredFrom:url};
    }catch{}
  }
  return {...item};
}
export async function build(root=process.cwd()) {
  const site=join(root,'_site');
  await rm(site,{recursive:true,force:true});
  await mkdir(site,{recursive:true});
  for(const path of ['index.html','assets','data','feed.xml','sms-hub','releases']){
    await cp(join(root,path),join(site,path),{recursive:true});
  }
  const output=join(site,'assets/posters'),cache=join(root,'.cache/zhigeng-posters');
  await mkdir(output,{recursive:true});await mkdir(cache,{recursive:true});
  const path=join(site,'data/domestic.json');
  const catalog=JSON.parse(await readFile(path,'utf8'));
  const original=catalog.items,items=new Array(original.length);
  let cursor=0;
  const worker=async()=>{
    while(cursor<original.length){
      const i=cursor++;
      items[i]=await mirrorPoster(original[i],{output,cache});
    }
  };
  await Promise.all(Array.from({length:4},worker));
  const mirrored=items.filter(item=>item.image.startsWith('assets/posters/')).length;
  const report={generatedAt:new Date().toISOString(),total:items.length,mirrored,
    failed:items.filter(item=>!item.image.startsWith('assets/posters/')).map(item=>({title:item.title,type:item.type}))};
  catalog.items=items;
  await writeFile(path,JSON.stringify(catalog,null,2)+'\n');
  await writeFile(join(site,'data/poster-status.json'),JSON.stringify(report,null,2)+'\n');
  console.log('Verified same-origin posters: '+mirrored+'/'+items.length);
  if(report.failed.length)console.log('Kept external fallback: '+JSON.stringify(report.failed));
  if(process.env.GITHUB_STEP_SUMMARY)await writeFile(process.env.GITHUB_STEP_SUMMARY,
    '### 国内精选封面\n本站真实图片：'+mirrored+'/'+items.length+'。\n'+
    report.failed.map(item=>'- 待补：'+item.title+'（'+item.type+'）').join('\n')+'\n',{flag:'a'});
  if(!mirrored)throw Error('No real posters available; keep the previous deployment');
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)await build();
