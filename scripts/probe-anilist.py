"""One public read-only query, no account, token, tracker write, or provider stream."""
import json,pathlib,re,time,urllib.request
out=pathlib.Path('anix-check');out.mkdir(exist_ok=True)
source=pathlib.Path('android/app/src/main/java/cn/zhigeng/app/AnimeCatalogClient.java').read_text()
query=re.search(r'static final String QUERY="([^"]+)";',source).group(1)
report=dict(endpoint='https://graphql.anilist.co',readOnly=True,checkedAt=int(time.time()))
try:
    data=json.dumps(dict(query=query,variables=dict(search='Cowboy Bebop',page=1,sort=['SEARCH_MATCH']))).encode()
    request=urllib.request.Request(report['endpoint'],data,{'Content-Type':'application/json','Accept':'application/json','User-Agent':'ZhigengAndroid/0.6.16'})
    with urllib.request.urlopen(request,timeout=25) as response:body=json.load(response)
    assert not body.get('errors'),body.get('errors')
    page=body['data']['Page'];assert isinstance(page['media'],list) and isinstance(page['pageInfo']['hasNextPage'],bool)
    assert page['media'] and page['media'][0]['id']>0
    report.update(passed=True,items=len(page['media']),hasNextPage=page['pageInfo']['hasNextPage'])
except Exception as error:report.update(passed=False,error=str(error)[:300])
(out/'live-api.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False))
