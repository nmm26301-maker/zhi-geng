import json, pathlib, re, subprocess, time, xml.etree.ElementTree as E

OUT=pathlib.Path('backups/Zhigeng-0.6.16/checks');OUT.mkdir(parents=True,exist_ok=True)
APP='cn.zhigeng.app'
def adb(*args):
    return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def dump():
    adb('shell','uiautomator','dump','/sdcard/upgrade.xml')
    return E.fromstring(adb('shell','cat','/sdcard/upgrade.xml'))
def has(text):
    return any(text in n.get('text','') or text in n.get('content-desc','') for n in dump().iter('node'))
def tap(text):
    for n in dump().iter('node'):
        if n.get('text')==text or n.get('content-desc')==text:
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(1.5);return
    raise AssertionError('Missing UI: '+text)
def screen(name):
    print('Checkpoint:',name,flush=True)
    tree=dump();(OUT/(name+'.xml')).write_bytes(E.tostring(tree,encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
def launch():
    adb('shell','am','force-stop',APP);adb('shell','am','start','-W','-n',APP+'/.MainActivity');time.sleep(3)
def identity():
    result=adb('shell','dumpsys','package',APP)
    return {k:re.search(pattern,result).group(1) for k,pattern in {
        'uid':r'userId=(\d+)','firstInstallTime':r'firstInstallTime=([^\n]+)',
        'dataDir':r'dataDir=([^\n]+)','versionCode':r'versionCode=(\d+)'}.items()}
def favorites():
    tap('我的');tap('更多工具');tap('节点导航 5＋5');tap('我的收藏')

adb('logcat','-c')
assert 'Success' in adb('install','-r','previous.apk')
before=identity();assert before['versionCode']=='615'
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
launch();screen('01-old-app')
tap('榜单');tap('编辑推荐')
tap('我不是药神');tap('想看');adb('shell','input','keyevent','4');time.sleep(1)
tap('书架');assert has('我不是药神');screen('02-old-shelf')
tap('我的');tap('更多工具');tap('节点导航 5＋5');tap('代理 IP 5家')
adb('shell','input','swipe','540','1550','540','1100','300');time.sleep(1)
tap('收藏Decodo 德口多')
adb('shell','input','swipe','540','450','540','1600','350');time.sleep(1)
tap('我的收藏');assert has('Decodo');screen('03-old-favorite')

assert 'Success' in adb('install','-r','backups/Zhigeng-0.6.16/Zhigeng-v0.6.16.apk')
after=identity();assert after['versionCode']=='616'
assert all(before[k]==after[k] for k in ['uid','firstInstallTime','dataDir']), (before,after)
launch();assert has('国内精选');screen('04-new-app')
adb('shell','input','swipe','540','1500','540','650','400');time.sleep(3)
screen('05-new-offline-covers')
covers=sum('封面已加载' in n.get('content-desc','') for n in dump().iter('node'))
assert covers>=2, covers
tap('书架');assert has('我不是药神');assert has('想读');screen('06-preserved-shelf')
favorites();assert has('Decodo');screen('07-preserved-favorite')
adb('shell','input','keyevent','4');launch();tap('书架');assert has('我不是药神')
screen('08-restarted-shelf')
launch();tap('更多工具');tap('Anix v1.3.0');assert has('搜索动漫名称');tap('我的追番');assert has('本地追番 · 0 部')
screen('09-original-signed-anix')
assert adb('shell','pm','list','packages',APP).strip()=='package:'+APP
logs=adb('logcat','-d','-s','AndroidRuntime:E');(OUT/'runtime.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,logs
result=dict(inPlaceUpgrade=True,sameUid=True,sameFirstInstallTime=True,sameDataDir=True,
            shelfPreserved=True,favoritesPreserved=True,restartPreserved=True,
            previousVersionCode=615,versionCode=616,applicationId=APP,api=int(adb('shell','getprop','ro.build.version.sdk').strip()),
            offlineCoversLoaded=covers,noSecondApp=True,anixNativeEntry=True,anixOfflineWatchlist=True,crashes=False,screens=9)
(OUT/'result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))

