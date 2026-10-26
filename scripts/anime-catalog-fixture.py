"""Original local TLS AniList response fixture, not a live hosted service."""
import http.server,json,pathlib,ssl
ROOT=pathlib.Path(__file__).resolve().parent.parent
OUT=pathlib.Path('anix-check');OUT.mkdir(exist_ok=True)
EVENTS=[];RETRIES=0
def record(value):
    EVENTS.append(value);(OUT/'protocol.json').write_text(json.dumps(EVENTS,ensure_ascii=False,indent=2))
def media(id,title):return dict(id=id,isAdult=False,title=dict(userPreferred=title,native=title),episodes=12,format='TV',status='RELEASING',seasonYear=2026,description='Original local catalogue fixture.',nextAiringEpisode=dict(episode=4,airingAt=1800000000))
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def respond(self,status,data):
        value=json.dumps(data,ensure_ascii=False).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(value)))
        if status==429:self.send_header('Retry-After','1')
        self.end_headers();self.wfile.write(value)
    def do_POST(self):
        global RETRIES
        assert self.headers.get('Authorization') is None and self.headers.get('Cookie') is None
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        assert 'isAdult: false' in body['query'] and 'hasNextPage' in body['query']
        variables=body['variables'];term=variables.get('search') or '';page=variables['page'];record(dict(term=term,page=page))
        if term=='retry':
            RETRIES+=1
            if RETRIES==1:return self.respond(503,{})
        if term=='graphql-error':return self.respond(200,dict(data=None,errors=[dict(message='Original failure')]))
        if term=='limited':return self.respond(429,{})
        items=[media(1,'春日动漫 Original fixture'),media(2,'夏日动漫')]
        more=not term and page==1
        if page==2:items=[media(3,'Next page anime')]
        elif term in ('Fixture','retry'):items=items[:1]
        elif term:items=[]
        return self.respond(200,dict(data=dict(Page=dict(pageInfo=dict(hasNextPage=more,total=0),media=items))))
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);base=ROOT/'android/tests/fixtures';context.load_cert_chain(base/'local-test-only.crt',base/'local-test-only.key')
server=http.server.ThreadingHTTPServer(('0.0.0.0',8445),Handler);server.socket=context.wrap_socket(server.socket,server_side=True);server.serve_forever()
