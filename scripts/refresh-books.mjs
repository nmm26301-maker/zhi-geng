/* 在 GitHub Actions 服务端定期构建真实公开图书目录，不要求访客浏览器连接国外图书 API。 */
import fs from 'node:fs/promises';
const FILE='data/books.json';
const years=[new Date().getUTCFullYear(),new Date().getUTCFullYear()-1,
  new Date().getUTCFullYear()-2,new Date().getUTCFullYear()-3,new Date().getUTCFullYear()-4,
  new Date().getUTCFullYear()-5,new Date().getUTCFullYear()-6,
  2019,2018,2015,2010,2000,1990,1980,1970];
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const short=(value,size=100)=>String(value??'').slice(0,size);
async function get(url){
  const c=new AbortController();const timer=setTimeout(()=>c.abort(),10000);
  try{
    const r=await fetch(url,{signal:c.signal,headers:{Accept:'application/json','User-Agent':'Zhigeng-Public-Book-Index/1.0'}});
    if(!r.ok)throw Error('HTTP '+r.status);
    return r.json();
  }finally{clearTimeout(timer);}
}
const old=await fs.readFile(FILE,'utf8').then(JSON.parse).catch(()=>({items:[]}));
const oldItems=Array.isArray(old.items)?old.items:[];
const collected=new Map(oldItems.filter(x=>x?.id&&x.title&&x.url).map(x=>[x.id,x]));
let success=0,failures=0;
for(const year of [...new Set(years)]){
  try{
    const url=new URL('https://openlibrary.org/search.json');
    url.searchParams.set('q','subject:fiction AND first_publish_year:['+year+' TO '+year+']');
    url.searchParams.set('fields','key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count,readinglog_count,edition_count,language,ebook_access,subject');
    url.searchParams.set('sort','readinglog');
    url.searchParams.set('limit','24');
    const data=await get(url);
    for(const v of data.docs||[]){
      const path=String(v.key||'');
      if(!/^\/works\/OL\d+W$/.test(path)||!v.title)continue;
      const id='book:'+path,prev=collected.get(id)||{};
      collected.set(id,{
        id,title:short(v.title,180),author:(v.author_name||[]).slice(0,2).join(' · '),
        year:Number(v.first_publish_year)||year,
        image:v.cover_i?'https://covers.openlibrary.org/b/id/'+v.cover_i+'-M.jpg':prev.image||'',
        source:'Open Library',url:'https://openlibrary.org'+path,
        score:Number(v.ratings_average)||0,votes:Number(v.ratings_count)||0,
        interest:Number(v.readinglog_count)||Number(v.edition_count)||1,
        language:(v.language||[]).slice(0,8),subjects:(v.subject||[]).slice(0,14).map(x=>short(x,70)),
        ebookAccess:short(v.ebook_access||''),editionCount:Number(v.edition_count)||0
      });
    }
    success++;
    console.log('Open Library '+year+': '+(data.docs||[]).length+' records');
  }catch(e){
    failures++;
    console.warn('Open Library '+year+': '+e.message);
    if(/HTTP 429/.test(e.message||''))break;
  }
  await sleep(350);
}
try{
  const data=await get('https://gutendex.com/books/?page=1');
  for(const v of data.results||[]){
    if(!v.id||!v.title)continue;
    const id='book:gutenberg:'+v.id;
    const author=(v.authors||[]).slice(0,2).map(a=>a.name).join(' · ');
    const pic=v.formats?.['image/jpeg']||'';
    collected.set(id,{
      id,title:short(v.title,180),author,year:null,image:pic.startsWith('https://')?pic:'',
      source:'Project Gutenberg',url:'https://www.gutenberg.org/ebooks/'+v.id,
      score:0,votes:0,interest:Number(v.download_count)||1,
      language:(v.languages||[]).map(x=>({'en':'eng','zh':'chi','ja':'jpn','fr':'fre','de':'ger','es':'spa'}[x]||x)),
      subjects:(v.subjects||[]).slice(0,15),ebookAccess:'public',editionCount:1
    });
  }
  console.log('Gutendex: '+(data.results||[]).length);
}catch(e){console.warn('Gutendex: '+e.message);}
if(!collected.size)throw Error('No verified books available: keep previous catalog');
const books=[...collected.values()].filter(v=>v.title&&/^https:\/\//.test(v.url));
books.sort((a,b)=>(b.year||0)-(a.year||0)||b.interest-a.interest);
await fs.mkdir('data',{recursive:true});
await fs.writeFile(FILE,JSON.stringify({updatedAt:new Date().toISOString(),source:'Open Library / Project Gutenberg',yearCount:new Set(books.map(v=>v.year)).size,count:books.length,items:books},null,2)+'\n');
console.log('Saved '+books.length+' source-linked real books; OL successes='+success+', failures='+failures);
