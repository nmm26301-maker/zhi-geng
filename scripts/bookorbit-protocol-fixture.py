"""Local TLS BookOrbit v1 protocol fixture. No real account, hosted server, or commercial book."""
import http.server,json,pathlib,ssl,time,urllib.parse
ROOT=pathlib.Path(__file__).resolve().parent.parent
REPORT=pathlib.Path('reading-tools-check');REPORT.mkdir(exist_ok=True)
EVENTS=[]
ACCESS='orbit-protocol-access-not-a-secret'
REFRESH='a'*64
REVOKED=False
RETRY_REQUESTS=0
def record(event):
    EVENTS.append(event)
    (REPORT/'protocol.json').write_text(json.dumps(EVENTS,indent=2))
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def respond(self,status,data,content_type='application/json'):
        body=data if isinstance(data,bytes) else json.dumps(data).encode()
        self.send_response(status);self.send_header('Content-Type',content_type);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def authorized(self):
        if REVOKED or self.headers.get('Authorization')!='Bearer '+ACCESS:
            self.respond(401,{});return False
        assert self.headers.get('Cookie') is None,'native session leaked a web cookie'
        return True
    def do_POST(self):
        global REVOKED,RETRY_REQUESTS
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        if self.path=='/api/v1/auth/login':
            assert body=={'username':'reader','password':'local-test-password','clientKind':'native','deviceLabel':'知更 Android'}
            REVOKED=False
            record('native-login');return self.respond(200,dict(accessToken='expired',refreshToken=REFRESH,user={'username':'reader'}))
        if self.path=='/api/v1/auth/refresh':
            if REVOKED:record('refresh-revoked');return self.respond(401,{})
            assert body=={'refreshToken':REFRESH};record('native-refresh')
            return self.respond(200,dict(accessToken=ACCESS,refreshToken='b'*64))
        if not self.authorized():return
        if self.path=='/api/v1/auth/logout':record('logout');return self.respond(200,{})
        if self.path!='/api/v1/libraries/1/books':return self.respond(404,{})
        term=body.get('q','');page=body['pagination']['page'];assert body['pagination']['size']==20
        record('query:'+term+':'+str(page))
        if term=='expire':
            REVOKED=True;record('session-revoked');return self.respond(401,{})
        if term=='retry':
            RETRY_REQUESTS+=1
            if RETRY_REQUESTS==1:record('transient-error');return self.respond(503,{})
        if term=='limited':return self.respond(429,{})
        if term=='missing':return self.respond(200,dict(items=[],total=0,page=page,size=20))
        books=[dict(id=i,title='Spring letter' if i==1 else 'Example book '+str(i),authors=[{'name':'Original local fixture'}]) for i in range(1,22)]
        if term=='retry':term='Spring'
        if term:books=[b for b in books if term.lower() in b['title'].lower()]
        return self.respond(200,dict(items=books[page*20:(page+1)*20],total=len(books),page=page,size=20))
    def do_GET(self):
        if not self.authorized():return
        if self.path=='/api/v1/libraries':
            record('libraries');return self.respond(200,[dict(id=1,name='Original fixture library',type='books')])
        if self.path=='/api/v1/auth/me':return self.respond(200,dict(username='reader'))
        if self.path=='/api/v1/books/1':
            record('detail');return self.respond(200,dict(id=1,title='Spring letter',description='<p>Original EPUB for protocol checks.</p>',authors=[{'name':'Original local fixture'}],files=[dict(id=7,format='epub',role='primary',sizeBytes=4096)]))
        if self.path=='/api/v1/books/files/7/download':
            record('download');return self.respond(200,(ROOT/'android/app/src/debug/assets/reader-fixture.epub').read_bytes(),'application/epub+zip')
        return self.respond(404,{})
base=ROOT/'android/tests/fixtures'
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(base/'local-test-only.crt',base/'local-test-only.key')
server=http.server.ThreadingHTTPServer(('0.0.0.0',8444),Handler);server.socket=context.wrap_socket(server.socket,server_side=True);server.serve_forever()
