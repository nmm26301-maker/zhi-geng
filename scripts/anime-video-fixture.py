"""Original local HTTPS MP4/HLS and HTML-error origin with observable byte-range resume."""
import http.server,json,mimetypes,os,pathlib,re,ssl,threading,time
ROOT=pathlib.Path(__file__).resolve().parent.parent;BASE=ROOT/'video-fixture';OUT=pathlib.Path('video-check');OUT.mkdir(exist_ok=True)
EVENTS=[];BYTES={};LOCK=threading.Lock()
def record(path,start=0,count=0,new=False):
    with LOCK:
        if new:EVENTS.append(dict(path=path,start=start))
        BYTES[path]=BYTES.get(path,0)+count
        (OUT/'protocol.json').write_text(json.dumps(dict(requests=EVENTS,bytes=BYTES)))
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        assert body['variables']['id']==7 and 'streamingEpisodes' in body['query'] and 'mutation' not in body['query']
        assert self.headers.get('Authorization') is None
        value=dict(data=dict(Media=dict(id=7,isAdult=False,streamingEpisodes=[],externalLinks=[dict(type='STREAMING',site='Original platform',url='https://example.org/original-platform')])))
        raw=json.dumps(value).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw);record('/graphql',new=True)
    def do_GET(self):
        path=self.path.split('?',1)[0]
        if path=='/error.html':
            body=b'<html><body>Original unavailable-video fixture</body></html>';self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body);record(path,new=True);return
        name='clip.mp4' if path=='/slow.mp4' else path.lstrip('/')
        file=(BASE/name).resolve()
        if not file.is_relative_to(BASE.resolve()) or not file.is_file():self.send_error(404);return
        size=file.stat().st_size;start=0;end=size-1
        value=self.headers.get('Range','')
        if value:
            match=re.fullmatch(r'bytes=(\d+)-(\d*)',value)
            if not match:self.send_error(416);return
            start=int(match.group(1));end=min(size-1,int(match.group(2))) if match.group(2) else size-1
            if start>=size or start>end:self.send_error(416);return
        record(path,start,new=True);self.send_response(206 if value else 200)
        content='application/vnd.apple.mpegurl' if file.suffix=='.m3u8' else 'video/mp2t' if file.suffix=='.ts' else 'video/mp4'
        self.send_header('Content-Type',content);self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1))
        if value:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
        self.end_headers()
        try:
            with file.open('rb') as stream:
                stream.seek(start);left=end-start+1
                while left:
                    part=stream.read(min(4096,left));self.wfile.write(part);self.wfile.flush();left-=len(part);record(path,count=len(part))
                    if path=='/slow.mp4':time.sleep(.05)
        except (BrokenPipeError,ConnectionResetError,ssl.SSLError):pass
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);cert=ROOT/'android/tests/fixtures';context.load_cert_chain(cert/'local-test-only.crt',cert/'local-test-only.key')
server=http.server.ThreadingHTTPServer(('0.0.0.0',8446),Handler);server.socket=context.wrap_socket(server.socket,server_side=True)
(OUT/'fixture.pid').write_text(str(os.getpid()));server.serve_forever()
