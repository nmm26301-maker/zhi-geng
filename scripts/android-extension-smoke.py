"""Original local provider/account contract via actual Android UI; no live accounts."""
import io,json,pathlib,re,shlex,subprocess,sys,time,xml.etree.ElementTree as E
from PIL import Image,ImageChops
APP='cn.zhigeng.app';OUT=pathlib.Path('extension-check');OUT.mkdir(exist_ok=True)
RESULT={'versionCode':619,'scope':'original local TypeScript provider and fake OAuth service; not a production login','cases':{}}
def adb(*args):
 p=subprocess.run(['adb',*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=45)
 if p.returncode:raise RuntimeError(repr(args)+': '+p.stdout.decode(errors='replace'))
 return p.stdout
def txt(*a):return adb(*a).decode(errors='replace')
def tree():
 adb('shell','uiautomator','dump','/sdcard/extension-check.xml');return E.fromstring(adb('shell','cat','/sdcard/extension-check.xml'))
def bounds(n):return tuple(map(int,re.findall(r'\d+',n.get('bounds',''))))
def nodes(label,t):return [n for n in t.iter('node') if label in (n.get('text'),n.get('content-desc'),n.get('resource-id')) and len(bounds(n))==4 and bounds(n)[3]-bounds(n)[1]>12]
def click(n):
 a,b,c,d=bounds(n);adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.35)
def locate(label):
 previous=None;down=True;reversed_once=False
 for _ in range(40):
  t=tree();found=nodes(label,t)
  if found:return sorted(found,key=lambda n:n.get('clickable')!='true')[0]
  signature=E.tostring(t)
  if signature==previous:
   if reversed_once:break
   down=not down;reversed_once=True
  previous=signature
  scroll=[n for n in t.iter('node') if n.get('scrollable')=='true']
  if not scroll:break
  a,b,c,d=bounds(scroll[-1]);x=(a+c)//2;lo=b+(d-b)//3;hi=b+2*(d-b)//3
  adb('shell','input','swipe',x,hi if down else lo,x,lo if down else hi,400)
 raise AssertionError('Missing '+label+'; '+str([(n.get('text'),n.get('content-desc')) for n in tree().iter('node')]))
def tap(label):click(locate(label))
def wait(predicate,timeout=35):
 end=time.monotonic()+timeout;previous=None;down=True
 while time.monotonic()<end:
  t=tree()
  if predicate(t):return t
  signature=E.tostring(t)
  if signature==previous:down=not down
  previous=signature
  scroll=[n for n in t.iter('node') if n.get('scrollable')=='true' and n.get('class')=='android.widget.ScrollView']
  if scroll:
   a,b,c,d=bounds(scroll[-1]);x=(a+c)//2;lo=b+(d-b)//3;hi=b+2*(d-b)//3
   adb('shell','input','swipe',x,hi if down else lo,x,lo if down else hi,400)
  time.sleep(.4)
 raise AssertionError('UI wait timed out: '+str([n.get('text') for n in tree().iter('node')]))
def has(label,t):return bool(nodes(label,t))
def back():adb('shell','input','keyevent','4');time.sleep(.4)
def fill(label,value):
 click(locate(label));adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67');adb('shell','input','text',shlex.quote(value));back()
def launch(activity,extra=()):
 adb('shell','am','start','-W','-n',APP+'/.'+activity,*extra);time.sleep(.6)
def screen(name):
 (OUT/(name+'.xml')).write_bytes(E.tostring(tree()));(OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def pref(file,key):return json.loads(E.fromstring(adb('shell','run-as',APP,'cat','shared_prefs/'+file+'.xml')).find("string[@name='"+key+"']").text)
def mark(name,details=True):
 RESULT['cases'][name]=details;(OUT/'result.json').write_text(json.dumps(RESULT,ensure_ascii=False,indent=2));print('PASS '+name,flush=True)
def decoded(name):
 image=Image.open(io.BytesIO(adb('exec-out','screencap','-p'))).convert('RGB');w,h=image.size;region=image.crop((w//10,h//4,w*9//10,h*3//5))
 count=sum(1 for r,g,b in region.getdata() if max(r,g,b)-min(r,g,b)>70 and max(r,g,b)>100);assert count>10000,count
 image.save(OUT/(name+'.png'));return region
def failure(kind,error,tb):
 RESULT['failure']=str(error)
 try:screen('failure');(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
 except Exception:pass
 (OUT/'result.json').write_text(json.dumps(RESULT,ensure_ascii=False,indent=2));sys.__excepthook__(kind,error,tb)
sys.excepthook=failure
adb('shell','pm','clear',APP);adb('logcat','-c')
launch('AnimeExtensionsTestActivity')
tap('添加扩展仓库 / 清单网址');fill('扩展仓库 HTTPS 地址','https://10.0.2.2:8447/index.json');tap('读取清单')
wait(lambda t:has('Original TypeScript fixture · 1.0',t));tap('Original TypeScript fixture · 1.0');tap('安装并启用')
wait(lambda t:has('扩展已安装，可配置偏好后搜索',t),80);mark('installTypeScriptRepository')
fill('扩展搜索名称','Original');tap('搜索可播放剧集');wait(lambda t:has('Original extension fixture',t));tap('Original extension fixture')
wait(lambda t:any('Original episode one' in n.get('text','') for n in t.iter('node')))
label=next(n.get('text') for n in tree().iter('node') if n.get('class')=='android.widget.Button' and 'Original episode one' in n.get('text',''));tap(label);tap('main')
wait(lambda t:has('选择画质 / 视频源',t));quality=next(n.get('text') for n in tree().iter('node') if 'Original 480p' in n.get('text',''));tap(quality);tap('确认保存并播放')
wait(lambda t:has('播放中',t));first=decoded('01-provider-decoded');time.sleep(3);second=decoded('02-provider-advanced')
assert sum(1 for p in ImageChops.difference(first,second).getdata() if max(p)>20)>1000
back();entry=pref('anime_video_7_v1','episodes')['1'];assert entry['position']>=1000
assert entry['headers']['Referer']=='https://example.org/original-fixture';assert entry['subtitles'];mark('providerSearchResolveNativeDecodeHeadersSubtitles')
tap('已关联剧集与离线缓存');tap('缓存第1集');tap('开始缓存')
for _ in range(3):
 t=tree();allow=nodes('com.android.permissioncontroller:id/permission_allow_button',t)
 if allow:click(allow[0]);break
 time.sleep(.3)
end=time.monotonic()+90
while time.monotonic()<end:
 state=locate('第1集缓存状态').get('text','')
 if '可离线播放' in state:break
 assert '失败' not in state,state;time.sleep(.5)
else:raise AssertionError('cache incomplete '+state)
screen('03-provider-cached');adb('shell','svc','wifi','disable');adb('shell','svc','data','disable');tap('播放第1集');wait(lambda t:has('播放中 · 离线',t));decoded('04-provider-offline');back()
adb('shell','svc','wifi','enable');adb('shell','svc','data','enable');mark('providerOfflineCacheDecode')
for provider,name in [('anilist','Original AniList account'),('mal','Original MAL account')]:
 adb('shell','am','force-stop',APP);launch('AnimeTrackerTestActivity',('--es','provider',provider));wait(lambda t:has('已连接 · '+name,t));tap('远端追番')
 wait(lambda t:has('导入此条到本地',t));tap('导入此条到本地');wait(lambda t:has('确认导入',t));tap('取消')
 before=pref('anix_watchlist_v1','entries') if provider=='mal' else None
 tap('导入此条到本地');wait(lambda t:has('确认导入',t));tap('确认导入');wait(lambda t:has('已导入到本地追番',t))
 local=pref('anix_watchlist_v1','entries');assert local['7']['progress']==2 and local['7']['state']=='在看'
 tap('本地追番 · 上传');tap('上传此条到账号');tap('取消');counts=json.loads((OUT/'protocol.json').read_text())['counts'];assert counts['aniWrites' if provider=='anilist' else 'malWrites']==0
 tap('上传此条到账号');tap('确认上传');wait(lambda t:has('远端已确认保存这条追番',t));screen('05-'+provider+'-sync')
 secure=adb('shell','run-as',APP,'cat','shared_prefs/protected.xml');assert b'fixture-anime-619-' not in secure
 mark(provider+'CallbackEncryptedImportCancelConfirmedUpload')
 tap('断开当前平台账号');tap('断开');wait(lambda t:has('尚未连接当前平台',t));assert pref('anix_watchlist_v1','entries')['7']['progress']==2
 mark(provider+'DisconnectPreservesLocal')
protocol=json.loads((OUT/'protocol.json').read_text());assert protocol['counts']['aniWrites']==1 and protocol['counts']['malWrites']==1 and protocol['counts']['refreshes']>=1 and protocol['counts']['mediaWithReferer']>=2
logs=txt('logcat','-d','-s','AndroidRuntime:E');(OUT/'runtime.txt').write_text(logs);assert 'FATAL EXCEPTION' not in logs,logs
mark('malRefreshAndNoCrashes',protocol['counts']);mark('completed')
