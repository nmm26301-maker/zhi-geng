// Android bindings for the MIT Anix/Seanime prelude. Nothing exposes Context, files or app accounts.
(function(){
  'use strict';
  globalThis.exports={};
  const invoke=(name,args)=>JSON.parse(__host(name,JSON.stringify(Array.from(args,v=>ArrayBuffer.isView(v)?Array.from(v):v))));
  const host={};
  for(const name of ['log','done','utf8Decode','b64Encode','urlParse','urlSet','docLoad','docSelect','docText','scannerUtils','sleep'])host[name]=function(){return invoke(name,arguments)};
  for(const name of ['utf8Encode','b64Decode','digest','hmac','aes','randomBytes','gunzip'])host[name]=function(){return new Uint8Array(invoke(name,arguments))};
  host.fetch=(id,request,body)=>{
    __fetch(request,host.b64Encode(body)).then(raw=>{const r=JSON.parse(raw);__engine.deliverFetch(id,null,JSON.stringify(r.meta),host.b64Decode(r.base64))},error=>__engine.deliverFetch(id,String(error),'{}',new Uint8Array()));
  };
  host.timer=(id,ms)=>__timer(id,ms).then(fire=>{if(fire)__engine.fireTimer(id)},()=>{});
  host.cancelTimer=id=>__cancelTimer(id);
  globalThis.__engine=__makePrelude(host,{id:'android-provider',version:'0.6.19',preferences:__preferences});
})();
