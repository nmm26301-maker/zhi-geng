import fs from 'node:fs/promises';
const year=new Date().getUTCFullYear(),now=new Date(),items=[];
function esc(x){return String(x??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
async function get(url,opts={}){const c=new AbortController(),timer=setTimeout(()=>c.abort(),14000);try{const r=await fetch(url,{...opts,signal:c.signal});if(!r.ok)throw Error('HTTP '+r.status);return await r.json();}finally{clearTimeout(timer);}}
async function collect(name,main,backup){try{let result;try{result=await main();}catch(e){console.warn(name+' primary: '+e.message);result=await backup();}items.push(...result.filter(x=>x.title&&x.url));console.log(name+' '+result.length);}catch(e){console.warn(name+' unavailable: '+e.message);}}
await collect('动漫',async()=>{
 const r=await get('https://api.jikan.moe/v4/seasons/now?limit=20&sfw=true');
 return (r.data||[]).slice(0,16).map(v=>({id:'jikan:'+v.mal_id,title:v.title_english||v.title,url:v.url,category:'动漫',type:'anime',image:v.images?.jpg?.large_image_url||'',year:v.year||year,score:Number(v.score)||0,source:'Jikan',description:v.synopsis||''}));
},async()=>{
 const season=['WINTER','SPRING','SUMMER','FALL'][Math.floor(now.getUTCMonth()/3)];
 const query='query($season:MediaSeason,$year:Int){Page(perPage:20){media(type:ANIME,isAdult:false,season:$season,seasonYear:$year,sort:POPULARITY_DESC){id title{english romaji} siteUrl startDate{year} coverImage{large} averageScore description(asHtml:false)}}}';
 const r=await get('https://graphql.anilist.co',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({query,variables:{season,year}})});
 if(r.errors?.length)throw Error('GraphQL failed');
 return (r.data?.Page?.media||[]).slice(0,16).map(v=>({id:'anilist:'+v.id,title:v.title?.english||v.title?.romaji,url:v.siteUrl||'https://anilist.co/anime/'+v.id,category:'动漫',type:'anime',image:v.coverImage?.large||'',year:v.startDate?.year||year,score:(Number(v.averageScore)||0)/10,source:'AniList',description:v.description||''}));
});
await collect('书籍',async()=>{
 const u=new URL('https://openlibrary.org/search.json');u.searchParams.set('q','subject:fiction AND first_publish_year:['+year+' TO '+year+']');u.searchParams.set('fields','key,title,first_publish_year,author_name,cover_i,ratings_average');u.searchParams.set('sort','readinglog');u.searchParams.set('limit','20');
 const r=await get(u);return (r.docs||[]).slice(0,16).map(v=>({id:'ol:'+v.key,title:v.title,url:'https://openlibrary.org'+v.key,category:'书籍',type:'book',image:v.cover_i?'https://covers.openlibrary.org/b/id/'+v.cover_i+'-M.jpg':'',year:v.first_publish_year||null,score:Number(v.ratings_average)||0,source:'Open Library',description:(v.author_name||[]).join('、')}));
},async()=>{
 const r=await get('https://www.googleapis.com/books/v1/volumes?q=subject%3Afiction&orderBy=newest&maxResults=40');
 return (r.items||[]).map(v=>{const x=v.volumeInfo||{};return {id:'gb:'+v.id,title:x.title,url:x.infoLink||'https://books.google.com/books?id='+v.id,category:'书籍',type:'book',image:(x.imageLinks?.thumbnail||'').replace(/^http:/,'https:'),year:Number((x.publishedDate||'').slice(0,4)),score:Number(x.averageRating)||0,source:'Google Books',description:(x.authors||[]).join('、')};}).filter(v=>v.year===year).slice(0,16);
});
if(process.env.TMDB_READ_TOKEN){
 for(const kind of ['movie','tv']){
  try{const r=await get('https://api.themoviedb.org/3/trending/'+kind+'/week?language=zh-CN',{headers:{Authorization:'Bearer '+process.env.TMDB_READ_TOKEN}});
   items.push(...(r.results||[]).slice(0,16).map(v=>{const variety=kind==='tv'&&(v.genre_ids||[]).some(g=>g===10764||g===10767);const type=kind==='movie'?'movie':variety?'variety':'tv';return {id:'tmdb:'+type+':'+v.id,title:v.title||v.name,url:'https://www.themoviedb.org/'+kind+'/'+v.id,category:kind==='movie'?'电影':variety?'综艺':'电视剧',type,image:v.poster_path?'https://image.tmdb.org/t/p/w500'+v.poster_path:'',year:Number((v.release_date||v.first_air_date||'').slice(0,4))||year,score:Number(v.vote_average)||0,source:'TMDB',description:v.overview||''};}));
  }catch(e){console.warn('TMDB '+kind+': '+e.message);}
 }
}
const unique=[...new Map(items.filter(v=>v.title&&v.url).map(v=>[v.id,v])).values()];
if(!unique.length){console.warn('No live metadata; previous snapshot retained.');process.exit(0);}
await fs.mkdir('data',{recursive:true});
await fs.writeFile('data/weekly.json',JSON.stringify({updatedAt:now.toISOString(),year,count:unique.length,sources:[...new Set(unique.map(v=>v.source))],items:unique},null,2)+'\n');
const base=process.env.SITE_URL||'https://nmm26301-maker.github.io/zhi-geng/';
let rss='<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0"><channel><title>知更 · 影音书籍发现</title><link>'+esc(base)+'</link><description>公开作品资料的发现记录</description><language>zh-cn</language><lastBuildDate>'+now.toUTCString()+'</lastBuildDate>\n';
for(const v of unique.slice(0,60))rss+='<item><title>'+esc('['+v.category+'] '+v.title)+'</title><link>'+esc(v.url)+'</link><guid isPermaLink="false">'+esc(v.id)+'</guid><pubDate>'+now.toUTCString()+'</pubDate><description>'+esc((v.year||'')+' · '+v.source+' · '+(v.description||''))+'</description></item>\n';
rss+='</channel></rss>\n';
await fs.writeFile('feed.xml',rss);
console.log('Updated '+unique.length+' real metadata items');
