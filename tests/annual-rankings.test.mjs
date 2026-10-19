import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {JSDOM} from 'jsdom';
const source=readFileSync(resolve(import.meta.dirname,'../assets/annual-rankings.js'),'utf8');
function ranker(){
  const dom=new JSDOM('<html><body></body></html>',{url:'https://test.example/',runScripts:'outside-only'});
  dom.window.eval(source);
  return dom.window.ZhigengAnnualRankings.load;
}
const filters={year:'2024',page:1,type:'movie',region:'CN',sort:'rating'};
const reply=(data,inspect=()=>{})=>async(url,options)=>{inspect(new URL(url),options);return {ok:true,json:async()=>data};};

test('annual film query enforces year, Chinese origin, vote floor and pagination',async()=>{
  let calls=0;const load=ranker();
  const fake=reply({total_pages:2,results:[
    {id:1,title:'正确年份',release_date:'2024-05-02',vote_average:8.6,vote_count:80},
    {id:2,title:'错误年份',release_date:'2023-05-02',vote_average:9,vote_count:80},
    {id:3,title:'低票数',release_date:'2024-05-02',vote_average:9,vote_count:5}
  ]},url=>{
    calls++;assert.equal(url.searchParams.get('primary_release_date.gte'),'2024-01-01');
    assert.equal(url.searchParams.get('primary_release_date.lte'),'2024-12-31');
    assert.equal(url.searchParams.get('with_origin_country'),'CN');
    assert.equal(url.searchParams.get('vote_count.gte'),'50');
  });
  const data=await load({...filters,credential:'a'.repeat(32),fetcher:fake});
  assert.equal(calls,1);assert.deepEqual(data.items.map(x=>x.title),['正确年份']);
  assert.equal(data.items[0].score,8.6);assert.equal(data.hasMore,true);
});
test('TMDB missing credentials never pretends to have annual rankings',async()=>{
  const load=ranker();let calls=0;
  const r=await load({...filters,credential:'',fetcher:async()=>{calls++;throw Error('unexpected')}});
  assert.equal(calls,0);assert.equal(r.needsTmdb,true);assert.equal(r.items.length,0);
});
test('AniList passes actual selected year, country and sort separately',async()=>{
  const load=ranker();let vars;
  const r=await load({...filters,type:'anime',sort:'popular',page:2,fetcher:reply({
    data:{Page:{pageInfo:{hasNextPage:true},media:[
      {id:9,title:{native:'年度动画'},seasonYear:2024,averageScore:86,popularity:2000},
      {id:10,title:{native:'另一年'},seasonYear:2022,averageScore:90,popularity:3000}
    ]}}
  },(url,options)=>{vars=JSON.parse(options.body).variables;})});
  assert.equal(vars.year,2024);assert.equal(vars.origin,'CN');assert.equal(vars.sort,'POPULARITY_DESC');
  assert.equal(r.items.length,1);assert.equal(r.items[0].score,8.6);assert.equal(r.items[0].rank,21);
  assert.equal(r.hasMore,true);
});
test('books use first publication year and clearly label language vs country',async()=>{
  const load=ranker();let q='';
  const r=await load({...filters,type:'book',fetcher:reply({numFound:1,docs:[
    {key:'/works/OL123W',title:'图书A',first_publish_year:2024,
    author_name:['作者A'],ratings_average:4.5,ratings_count:30,readinglog_count:450}
  ]},url=>{q=url.searchParams.get('q');})});
  assert.match(q,/first_publish_year:2024/);assert.match(q,/language:chi/);
  assert.match(q,/ratings_count:\[10 TO \*\]/);
  assert.equal(r.items[0].scoreLabel,'Open Library /5');assert.match(r.note,/不代表中国原创/);
});
test('invalid year and unsupported all-category ranking rejected',async()=>{
  const load=ranker();
  await assert.rejects(load({...filters,year:'2100'}),/年份无效/);
  await assert.rejects(load({...filters,type:'all'}),/请选择内容类型/);
});
