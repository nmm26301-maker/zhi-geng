"""Exercise real Media3 decoding, resumed downloads, SAF files and offline playback.

Only a disposable emulator test installation is cleared. All video bytes are an
original generated fixture. No production account or third-party stream is used.
"""
import hashlib,io,json,os,pathlib,re,signal,subprocess,sys,time,xml.etree.ElementTree as E
from PIL import Image,ImageChops
APP='cn.zhigeng.app';OUT=pathlib.Path('video-check');OUT.mkdir(exist_ok=True)
BASE='https://10.0.2.2:8446';RESULT={'versionCode':618,'cases':{},'fixture':'original generated 24-second moving H264/AAC MP4 and HLS'}
def adb(*args):
    p=subprocess.run(['adb',*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=45)
    if p.returncode:raise RuntimeError(repr(args)+': '+p.stdout.decode(errors='replace'))
    return p.stdout
def txt(*args):return adb(*args).decode(errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/video-check.xml')
    return E.fromstring(adb('shell','cat','/sdcard/video-check.xml'))
def bounds(n):return tuple(map(int,re.findall(r'\d+',n.get('bounds',''))))
def nodes(value,t=None):
    if t is None:t=tree()
    return [n for n in t.iter('node') if value in (n.get('text'),n.get('content-desc'),n.get('resource-id')) and len(bounds(n))==4 and bounds(n)[3]-bounds(n)[1]>12]
def click(n):
    a,b,c,d=bounds(n);adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.4)
def scroll(t,down=True):
    choices=[n for n in t.iter('node') if n.get('scrollable')=='true' and len(bounds(n))==4]
    if not choices:return False
    # Last scroll container is the active dialog/footer, not the screen behind it.
    a,b,c,d=bounds(choices[-1]);x=(a+c)//2;pad=min(80,(d-b)//5)
    adb('shell','input','swipe',x,d-pad if down else b+pad,x,b+pad if down else d-pad,250);return True
def tap(value,search=True):
    for i in range(28 if search else 1):
        t=tree();found=nodes(value,t)
        if found:click(sorted(found,key=lambda n:n.get('clickable')!='true')[0]);return
        up_first=value in ('添加视频地址','导入剧集清单','导入本地视频','查询播出平台','播放第1集','缓存第1集','导出第1集视频')
        if not scroll(t,(i<14)!=up_first):break
    raise AssertionError('Missing video action: '+value)
def wait(test,timeout=25):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        t=tree()
        if test(t):return t
        time.sleep(.5)
    raise AssertionError('Timed out waiting for video state')
def has(value,t):return bool(nodes(value,t))
def screen(name):
    (OUT/(name+'.xml')).write_bytes(E.tostring(tree(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def mark(name,details=True):
    RESULT['cases'][name]=details;(OUT/'result.json').write_text(json.dumps(RESULT,ensure_ascii=False,indent=2));print('PASS '+name,flush=True)
def back():adb('shell','input','keyevent','4');time.sleep(.5)
def launch():
    adb('shell','am','start','-W','-n',APP+'/.AnimeVideoTestActivity');time.sleep(.5)
def restart():adb('shell','am','force-stop',APP);launch()
def fill(label,value):
    t=tree();found=[n for n in nodes(label,t) if n.get('class')=='android.widget.EditText']
    if not found:
        tap(label);found=[n for n in nodes(label) if n.get('class')=='android.widget.EditText']
    assert found,label;click(found[0]);adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67')
    # Inputs here are ASCII URLs/numbers/names, quoted as one Android shell argument.
    import shlex
    adb('shell','input','text',shlex.quote(value))
    assert any(n.get('text')==value for n in tree().iter('node') if n.get('class')=='android.widget.EditText'),label
    back()
def add(episode,path):
    tap('添加视频地址');fill('视频集数',str(episode));fill('HTTPS 视频地址',BASE+path);tap('保存视频');time.sleep(.5)
def pref(file,key):
    raw=adb('shell','run-as',APP,'cat','shared_prefs/'+file+'.xml')
    return json.loads(E.fromstring(raw).find("string[@name='"+key+"']").text)
def saved(episode):return pref('anime_video_7_v1','episodes')[str(episode)]
def seconds(t):
    for n in t.iter('node'):
        m=re.search(r'进度 (\d+):(\d+) /',n.get('text',''))
        if m:return int(m[1])*60+int(m[2])
    return -1
def decoded(name):
    # uiautomator's idle wait stalls while both native playback clocks update.
    # Inspect the actual video pixels without requesting an idle UI hierarchy.
    image=Image.open(io.BytesIO(adb('exec-out','screencap','-p'))).convert('RGB')
    width,height=image.size
    region=image.crop((width//10,height//4,width*9//10,height*3//5))
    colourful=sum(1 for r,g,b in region.getdata() if max(r,g,b)-min(r,g,b)>70 and max(r,g,b)>100)
    assert colourful>10000,dict(colourfulPixels=colourful,size=image.size)
    image.save(OUT/(name+'.png'))
    return region
def play(episode,offline=False):
    tap('播放第'+str(episode)+'集')
    time.sleep(2);first=decoded('decoded-'+str(episode)+'-first')
    time.sleep(2);second=decoded('decoded-'+str(episode)+('-offline' if offline else ''))
    changed=sum(1 for pixel in ImageChops.difference(first,second).getdata() if max(pixel)>20)
    assert changed>1000,dict(changedPixels=changed,episode=episode)
    time.sleep(2)
    position=saved(episode)['position']
    assert position>=1000,dict(episode=episode,checkpoint=position)
    return position//1000
def cache(episode):
    tap('缓存第'+str(episode)+'集');tap('开始缓存')
    for _ in range(4):
        t=tree();allow=nodes('com.android.permissioncontroller:id/permission_allow_button',t)
        if allow:click(allow[0]);break
        time.sleep(.5)
def status(episode):
    label='第'+str(episode)+'集缓存状态'
    for i in range(28):
        t=tree();found=nodes(label,t)
        if found:return found[0].get('text','')
        if not scroll(t,i<14):break
    raise AssertionError('Missing cache status '+label)
def cached(episode,timeout=100):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        value=status(episode)
        if '可离线播放' in value:return
        if '失败' in value:raise AssertionError(value)
        time.sleep(1)
    raise AssertionError('Cache incomplete '+str(episode)+': '+value)
def picker_downloads():
    # DocumentsUI starts in Recent. Use the drawer and Downloads location.
    t=tree()
    menu=nodes('Show roots',t) or nodes('显示根目录',t) or nodes('Open navigation drawer',t)
    if menu:click(menu[0])
    for label in ('Downloads','下载'):
        found=nodes(label)
        if found:click(found[-1]);return
    raise AssertionError('Downloads location not found')
def exported():
    return txt('shell','ls','-1','/sdcard/Download').splitlines()
def export(episode):
    tap('导出第'+str(episode)+'集视频');picker_downloads()
    t=tree();edits=[n for n in t.iter('node') if n.get('class')=='android.widget.EditText'];assert edits
    field=edits[-1];click(field);adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67');adb('shell','input','text','zhigeng-video-export.mp4');back()
    t=tree();save=nodes('SAVE',t) or nodes('保存',t);assert save;click(save[0])
    wait(lambda t:any('视频已导出到手机' in n.get('text','') for n in t.iter('node')),30)
def failure(kind,error,tb):
    RESULT['failure']=repr(error)
    try:screen('failure');(OUT/'runtime.txt').write_bytes(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    (OUT/'result.json').write_text(json.dumps(RESULT,ensure_ascii=False,indent=2));sys.__excepthook__(kind,error,tb)
sys.excepthook=failure

adb('shell','pm','clear',APP);adb('logcat','-c');launch()
# Read-only catalog platform query, external browser roundtrip.
query=txt('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://example.org/')
browsers=set(re.findall(r'^\s*([\w.]+)/',query,re.M));browsers.discard(APP);assert browsers
browser='com.android.browser' if 'com.android.browser' in browsers else sorted(browsers)[0]
adb('shell','cmd','role','add-role-holder','--user','0','android.app.role.BROWSER',browser)
tap('查询播出平台');wait(lambda t:has('Original platform · example.org',t));adb('logcat','-c');tap('Original platform · example.org')
time.sleep(1);logs=txt('logcat','-d','-s','ActivityTaskManager:I');assert 'dat=https://example.org/' in logs
restart();mark('platformBrowserRouting')
add(1,'/clip.mp4');play(1);back();time.sleep(1)
checkpoint=saved(1)['position'];assert checkpoint>=5000
restart();resumed=play(1);assert resumed>=checkpoint//1000-1,(checkpoint,resumed)
ended=wait(lambda t:any(n.get('text')=='播放结束' for n in t.iter('node')),35)
rotate_bounds=bounds(nodes('横屏 / 竖屏',ended)[0]);screen('02-resumed-and-completed')
assert pref('anix_watchlist_v1','entries')['7']['progress']==1
back();mark('nativePlaybackResumeAndWatchlist',{'checkpointMs':checkpoint,'resumedSeconds':resumed,'progress':1})
add(2,'/slow.mp4');cache(2);tap('暂停缓存第2集');wait(lambda t:any('缓存已暂停' in n.get('text','') for n in nodes('第2集缓存状态',t)))
screen('03-paused');restart();assert '缓存已暂停' in status(2);tap('继续缓存第2集');adb('shell','input','keyevent','3');time.sleep(4);launch();cached(2,120)
protocol=json.loads((OUT/'protocol.json').read_text());assert any(r['path']=='/slow.mp4' and r['start']>0 for r in protocol['requests']),protocol
mark('pauseRestartResumeAndBackgroundDownload')
# Import a real JSON document through the system picker, not a seeded preference.
playlist=OUT/'zhigeng-video-list.json';playlist.write_text(json.dumps({'episodes':[{'episode':3,'url':BASE+'/master.m3u8','format':'hls'},{'episode':4,'url':BASE+'/error.html'}]}))
adb('push',playlist,'/sdcard/Download/zhigeng-video-list.json');adb('push','video-fixture/clip.mp4','/sdcard/Download/zhigeng-original-video.mp4')
tap('导入剧集清单');tap('选择 JSON 文件');picker_downloads();tap('zhigeng-video-list.json');wait(lambda t:any('已关联剧集 · 4 集'==n.get('text') for n in t.iter('node')));mark('safPlaylistImport')
cache(3);cached(3);cache(4)
end=time.monotonic()+50
while time.monotonic()<end:
    if '失败' in status(4):break
    time.sleep(1)
else:raise AssertionError('HTML response accepted as completed video')
screen('04-rejected-html');mark('hlsDownloadAndHtmlRejection')
cache(1);cached(1);export(1);adb('pull','/sdcard/Download/zhigeng-video-export.mp4',OUT/'export.mp4')
original=pathlib.Path('video-fixture/clip.mp4').read_bytes();assert (OUT/'export.mp4').read_bytes()==original
before=exported();tap('导出第1集视频');back();time.sleep(.5);assert exported()==before;mark('exportExactBytesAndCancel',{'bytes':len(original),'sha256':hashlib.sha256(original).hexdigest()});(OUT/'export.mp4').unlink()
tap('导入本地视频');fill('起始集数','5');tap('继续');picker_downloads();tap('zhigeng-original-video.mp4');wait(lambda t:any('已关联剧集 · 5 集'==n.get('text') for n in t.iter('node')))
restart();play(5);back();mark('safLocalGrantSurvivesRestart')
# Kill the origin and disable network before cached MP4, HLS and local playback.
os.kill(int((OUT/'fixture.pid').read_text()),signal.SIGTERM);adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
for episode in (2,3,5):
    play(episode,episode!=5);decoded('06-offline-'+str(episode));back()
mark('offlineMp4HlsAndLocalDecoded')
play(2,True);before=saved(2)['position'];a,b,c,d=rotate_bounds;adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(6);decoded('07-landscape');after=saved(2)['position'];assert after>=before,(before,after);back();mark('rotationRetainsPlayback')
logs=txt('logcat','-d','-s','AndroidRuntime:E');(OUT/'runtime.txt').write_text(logs);assert 'FATAL EXCEPTION' not in logs,logs
RESULT['crashes']=False;RESULT['api']=int(txt('shell','getprop','ro.build.version.sdk'));mark('completed')
adb('shell','svc','wifi','enable');adb('shell','svc','data','enable');adb('shell','am','force-stop',APP)
