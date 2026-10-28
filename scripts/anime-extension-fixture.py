"""Original HTTPS extension and authenticated tracker contract. No production account or media."""
import http.server,json,pathlib,re,ssl,threading,time,urllib.parse
ROOT=pathlib.Path(__file__).resolve().parent.parent;OUT=ROOT/'extension-check';OUT.mkdir(exist_ok=True)
BASE='https://10.0.2.2:8447';LOCK=threading.Lock();EVENTS=[];COUNTS={'aniWrites':0,'malWrites':0,'refreshes':0,'mediaWithReferer':0}
MEDIA={'id':7,'idMal':21,'episodes':12,'isAdult':False,'title':{'userPreferred':'Original tracker fixture','romaji':'Original tracker fixture'},'description':'Original offline synopsis'}
SOURCE='''class Provider {
 getSettings(): object {return {episodeServers:['main','backup'],supportsDub:true};}
 async search(options: {query:string}): Promise<object[]> {
   const response=await fetch('BASE/search?q='+encodeURIComponent(options.query));
   const doc=LoadDoc(response.text());
   return doc('a.series').map((i:number,node:any)=>({id:node.attr('data-id'),title:node.text()}));
 }
 async findEpisodes(series:string): Promise<object[]> {
   const doc=LoadDoc((await fetch('BASE/episodes?id='+encodeURIComponent(series))).text());
   return doc('a.episode').map((i:number,node:any)=>({id:node.attr('data-id'),number:Number(node.attr('data-number')),title:node.text(),url:'BASE/resolve'}));
 }
 async findEpisodeServer(episode:any,server:string): Promise<object> {
   return (await fetch('BASE/resolve?id='+encodeURIComponent(episode.id)+'&server='+encodeURIComponent(server))).json();
 }
}'''.replace('BASE',BASE)
def record(path,method,body=None):
    with LOCK:
        EVENTS.append({'path':path,'method':method,'body':body})
        (OUT/'protocol.json').write_text(json.dumps({'requests':EVENTS,'counts':COUNTS}))
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def send(self,raw,kind='application/json',status=200):
        if not isinstance(raw,bytes):raw=json.dumps(raw).encode() if kind=='application/json' else raw.encode()
        self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        path=urllib.parse.urlsplit(self.path).path
        if path=='/index.json':self.send({'name':'Original fixture repository','plugins':[{'id':'original_fixture','name':'Original TypeScript fixture','version':'1.0','type':'onlinestream-provider','payloadURI':'provider.ts'}]});record(path,'GET');return
        if path=='/provider.ts':self.send(SOURCE,'text/plain');record(path,'GET');return
        if path=='/search':self.send('<a class="series" data-id="original-series">Original extension fixture</a>','text/html');record(path,'GET');return
        if path=='/episodes':self.send('<a class="episode" data-id="original-ep-1" data-number="1">Original episode one</a>','text/html');record(path,'GET');return
        if path=='/resolve':self.send({'headers':{'Referer':'https://example.org/original-fixture'},'videoSources':[{'url':BASE+'/clip.mp4','type':'mp4','quality':'Original 480p','subtitles':[{'url':BASE+'/sub.vtt','format':'vtt','language':'en'}]}]});record(path,'GET');return
        if path=='/sub.vtt':self.send('WEBVTT\n\n00:00.000 --> 00:20.000\nOriginal fixture subtitle\n','text/vtt');record(path,'GET');return
        if path=='/clip.mp4':
            assert self.headers.get('Referer')=='https://example.org/original-fixture',dict(self.headers)
            file=ROOT/'video-fixture/clip.mp4';raw=file.read_bytes();start=0
            if self.headers.get('Range'):
                match=re.fullmatch(r'bytes=(\d+)-(\d*)',self.headers['Range']);assert match;start=int(match[1])
            if start>=len(raw):self.send_error(416);return
            self.send_response(206 if start else 200);self.send_header('Content-Type','video/mp4');self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(len(raw)-start))
            if start:self.send_header('Content-Range',f'bytes {start}-{len(raw)-1}/{len(raw)}')
            self.end_headers();self.wfile.write(raw[start:]);COUNTS['mediaWithReferer']+=1;record(path,'GET');return
        if path.startswith('/mal/'):
            assert self.headers.get('Authorization') in ('Bearer fixture-anime-619-mal','Bearer fixture-anime-619-mal-new')
            if path=='/mal/users/@me':self.send({'id':102,'name':'Original MAL account'});record(path,'GET');return
            if path=='/mal/users/@me/animelist':self.send({'data':[{'node':{'id':21,'title':'Original MAL fixture','num_episodes':12},'list_status':{'status':'watching','num_episodes_watched':2}}],'paging':{}});record(path,'GET');return
        self.send_error(404)
    def do_POST(self):
        raw=self.rfile.read(int(self.headers.get('Content-Length','0')))
        if self.path=='/anilist':
            body=json.loads(raw);query=body['query'];token=self.headers.get('Authorization')
            if 'Viewer' in query:assert token=='Bearer fixture-anime-619-ani';self.send({'data':{'Viewer':{'id':101,'name':'Original AniList account'}}})
            elif 'SaveMediaListEntry' in query:
                assert token=='Bearer fixture-anime-619-ani';v=body['variables'];assert v['id']==7 and v['progress']==2 and v['state']=='CURRENT';COUNTS['aniWrites']+=1;self.send({'data':{'SaveMediaListEntry':{'mediaId':7,'status':'CURRENT','progress':2}}})
            elif 'mediaList' in query:
                assert token=='Bearer fixture-anime-619-ani';self.send({'data':{'Page':{'pageInfo':{'hasNextPage':False},'mediaList':[{'status':'CURRENT','progress':2,'media':MEDIA}]}}})
            elif 'Media(' in query:assert token is None;self.send({'data':{'Media':MEDIA}})
            else:raise AssertionError(query)
            record('/anilist','POST',body);return
        if self.path=='/token':
            body={k:v[0] for k,v in urllib.parse.parse_qs(raw.decode()).items()};assert body['client_id']=='abcdef1234567890' and 'client_secret' not in body
            refresh=body['grant_type']=='refresh_token'
            if refresh:assert body['refresh_token']=='fixture-anime-619-refresh';COUNTS['refreshes']+=1
            else:assert body['code']=='fixture-anime-619-code' and len(body['code_verifier'])==64 and body['redirect_uri']=='cn.zhigeng.app://oauth/mal'
            self.send({'access_token':'fixture-anime-619-mal-new' if refresh else 'fixture-anime-619-mal','refresh_token':'fixture-anime-619-refresh','expires_in':3600 if refresh else 1});record('/token','POST',{'grant_type':body['grant_type']});return
        self.send_error(404)
    def do_PUT(self):
        assert self.path=='/mal/anime/21/my_list_status';assert self.headers.get('Authorization')=='Bearer fixture-anime-619-mal-new'
        raw=self.rfile.read(int(self.headers.get('Content-Length','0')));body={k:v[0] for k,v in urllib.parse.parse_qs(raw.decode()).items()};assert body=={'status':'watching','num_watched_episodes':'2'},body
        COUNTS['malWrites']+=1;self.send({'status':'watching','num_episodes_watched':2});record(self.path,'PUT',body)
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);cert=ROOT/'android/tests/fixtures';context.load_cert_chain(cert/'local-test-only.crt',cert/'local-test-only.key')
server=http.server.ThreadingHTTPServer(('0.0.0.0',8447),Handler);server.socket=context.wrap_socket(server.socket,server_side=True)
(OUT/'fixture.pid').write_text(str(__import__('os').getpid()));server.serve_forever()
