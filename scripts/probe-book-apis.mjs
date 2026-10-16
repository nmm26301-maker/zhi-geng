const cases=[
  ['Gutendex','https://gutendex.com/books/?search=pride%20and%20prejudice'],
  ['Wikidata','https://www.wikidata.org/w/api.php?action=wbsearchentities&search=%E4%B8%89%E4%BD%93&language=zh&format=json&origin=*']
];
await Promise.all(cases.map(async([name,url])=>{
  const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),10000);
  try{
    const r=await fetch(url,{signal:controller.signal,headers:{Accept:'application/json'}});
    const data=await r.json().catch(()=>({}));
    const count=Array.isArray(data.search)?data.search.length:Array.isArray(data.results)?data.results.length:0;
    console.log(name+': HTTP '+r.status+'; entries='+count+'; CORS='+JSON.stringify(r.headers.get('access-control-allow-origin')));
  }catch(e){console.warn(name+': probe failed ('+e.message+')');}
  finally{clearTimeout(timer);}
}));
