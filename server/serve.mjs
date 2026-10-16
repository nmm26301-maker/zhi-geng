import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {handleGateway} from './gateway.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.svg':'image/svg+xml','.xml':'application/xml'};
const server=http.createServer(async(req,res)=>{
  try{
    const url=new URL(req.url,'http://'+req.headers.host);
    if(url.pathname==='/api/gateway'){
      let size=0;const chunks=[];
      for await(const chunk of req){size+=chunk.length;if(size>20000){res.writeHead(413);res.end();return;}chunks.push(chunk);}
      const response=await handleGateway(new Request(url,{method:req.method,headers:req.headers,...(req.method==='POST'?{body:Buffer.concat(chunks)}:{})}));
      res.writeHead(response.status,Object.fromEntries(response.headers));res.end(await response.text());return;
    }
    if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return;}
    const rel=decodeURIComponent(url.pathname).replace(/^\/+/,''),parts=rel.split('/');
    if(parts.some(p=>p.startsWith('.'))||!['index.html','assets','data','feed.xml','licenses'].includes(parts[0])&&rel!==''){res.writeHead(404);res.end();return;}
    const file=path.resolve(root,rel||'index.html');if(!file.startsWith(root)){res.writeHead(403);res.end();return;}
    const content=await fs.readFile(file);
    res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','X-Content-Type-Options':'nosniff','Cache-Control':'no-cache'});res.end(req.method==='HEAD'?null:content);
  }catch{res.writeHead(404);res.end('Not found');}
});
server.listen(Number(process.env.PORT)||3000,'0.0.0.0',()=>console.log('Zhigeng server ready'));
