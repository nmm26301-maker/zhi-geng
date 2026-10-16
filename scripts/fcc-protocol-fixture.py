"""Local TLS fixture for protocol/UI QA only. It is not an AI model or a public gateway."""
import http.server,ssl,json,time,pathlib
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def respond(self,code,body,kind='application/json'):
  self.send_response(code);self.send_header('Content-Type',kind);self.end_headers();self.wfile.write(body.encode())
 def do_GET(self):
  if self.headers.get('Authorization')!='Bearer fixture-token-not-a-secret':return self.respond(401,'{}')
  if self.path!='/v1/models?view=messages':return self.respond(404,'{}')
  models=[{'id':'fixture/text','display_name':'Protocol fixture (not an AI model)','provider_model_ref':'fixture/local-text','inputModalities':['text']},{'id':'fixture/429','display_name':'Rate limit fixture (not an AI model)','provider_model_ref':'fixture/http429'}]
  self.respond(200,json.dumps({'data':models}))
 def do_POST(self):
  if self.headers.get('Authorization')!='Bearer fixture-token-not-a-secret':return self.respond(401,'{}')
  if self.path!='/v1/messages':return self.respond(404,'{}')
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
  assert body['stream'] is True and body['messages'][-1]['role']=='user'
  if body['model']=='fixture/429':return self.respond(429,'{}')
  events=[{'type':'message_start','message':{'model':'fixture/local-text'}},{'type':'content_block_start','content_block':{'type':'text','text':''}},{'type':'content_block_delta','delta':{'type':'text_delta','text':'本地协议测试回复'}},{'type':'content_block_delta','delta':{'type':'text_delta','text':'（不是真实模型）。'}},{'type':'message_delta','delta':{'stop_reason':'end_turn'}},{'type':'message_stop'}]
  self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  try:
   for e in events:self.wfile.write(('data: '+json.dumps(e,ensure_ascii=False)+'\n\n').encode());self.wfile.flush();time.sleep(.15)
  except (BrokenPipeError,ConnectionResetError):pass
base=pathlib.Path(__file__).resolve().parent.parent/'android/tests/fixtures'
ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(base/'local-test-only.crt',base/'local-test-only.key')
server=http.server.ThreadingHTTPServer(('0.0.0.0',8443),Handler);server.socket=ctx.wrap_socket(server.socket,server_side=True);server.serve_forever()
