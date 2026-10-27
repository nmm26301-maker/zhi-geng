"""Original-signed 617->618 upgrade; legacy watchlist seed is an explicit test fixture."""
import json,pathlib,re,subprocess,time,xml.etree.ElementTree as E
APP='cn.zhigeng.app';OUT=pathlib.Path('upgrade618-check');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/upgrade618.xml')
    return E.fromstring(adb('shell','cat','/sdcard/upgrade618.xml'))
def has(text):return any(text in n.get('text','') for n in tree().iter('node'))
def wait(text):
    for _ in range(12):
        if has(text):return
        time.sleep(.5)
    raise AssertionError('Missing upgrade text: '+text)
def tap(text):
    for _ in range(14):
        for n in tree().iter('node'):
            if text in (n.get('text'),n.get('content-desc')):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if c>a and d>b:adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.7);return
        adb('shell','input','swipe',540,1550,540,650,250)
    raise AssertionError('Missing upgrade action: '+text)
def identity():
    data=adb('shell','dumpsys','package',APP)
    return {k:re.search(pattern,data).group(1) for k,pattern in {
        'uid':r'userId=(\d+)','firstInstallTime':r'firstInstallTime=([^\n]+)',
        'dataDir':r'dataDir=([^\n]+)','versionCode':r'versionCode=(\d+)'}.items()}
def launch():
    adb('shell','am','force-stop',APP);adb('shell','am','start','-W','-n',APP+'/.MainActivity');time.sleep(2)
def anime():tap('更多工具');tap('Anix v1.3.0');wait('本地追番 · 1 部')
def screen(name):
    (OUT/(name+'.xml')).write_bytes(E.tostring(tree(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=45))
assert pathlib.Path('previous.apk').is_file() and pathlib.Path('Zhigeng-v0.6.18.apk').is_file()
try:adb('uninstall',APP)
except subprocess.CalledProcessError:pass
adb('logcat','-c');assert 'Success' in adb('install','previous.apk');before=identity();assert before['versionCode']=='617'
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable');launch()
tap('榜单');tap('编辑推荐');tap('我不是药神');tap('想看');adb('shell','input','keyevent','4');time.sleep(.7)
tap('书架');wait('我不是药神');screen('01-old-shelf')
adb('shell','am','force-stop',APP);adb('root');adb('wait-for-device')
# Old-format snapshot deliberately lacks updatedAt, aliases and description.
seed={'1':{'media':{'id':1,'title':{'userPreferred':'Legacy upgrade fixture'},'episodes':12,'format':'TV'},'state':'在看','progress':3}}
prefs=E.Element('map');E.SubElement(prefs,'string',name='entries').text=json.dumps(seed,ensure_ascii=False)
E.SubElement(prefs,'boolean',name='local',value='true')
fixture=OUT/'legacy-prefs.xml';fixture.write_bytes(E.tostring(prefs,encoding='utf-8',xml_declaration=True))
folder='/data/user/0/'+APP+'/shared_prefs';target=folder+'/anix_watchlist_v1.xml'
adb('shell','mkdir','-p',folder);adb('push',fixture,target);adb('shell','chown',before['uid']+':'+before['uid'],folder,target)
adb('shell','chmod','660',target);adb('shell','restorecon','-RF',folder)
launch();anime();tap('动漫详情');wait('暂无离线简介');tap('关闭');wait('已看 3 集');screen('02-old-watchlist')
assert 'Success' in adb('install','-r','Zhigeng-v0.6.18.apk');after=identity();assert after['versionCode']=='618'
assert all(before[k]==after[k] for k in ('uid','firstInstallTime','dataDir')),(before,after)
launch();tap('书架');wait('我不是药神');screen('03-new-shelf')
launch();anime();tap('动漫详情');wait('暂无离线简介');tap('关闭');wait('已看 3 集');screen('04-legacy-watchlist-preserved')
tap('看完一集 +1');wait('已看 4 集');screen('05-new-progress')
launch();anime();tap('更新追番');wait('4');tap('取消');screen('06-offline-restart')
launch();tap('更多工具');tap('视频与离线缓存');wait('还没有关联剧集');screen('07-native-video-entry')
assert adb('shell','pm','list','packages',APP).strip()=='package:'+APP
logs=adb('logcat','-d','-s','AndroidRuntime:E');(OUT/'runtime.txt').write_text(logs);assert 'FATAL EXCEPTION' not in logs,logs
result=dict(previousVersionCode=617,versionCode=618,api=33,originalSignatureCoveringUpgrade=True,sameUid=True,
    sameFirstInstallTime=True,sameDataDir=True,shelfPreserved=True,legacyWatchlistPreserved=True,
    quickProgressPersisted=True,offlineRestart=True,noSecondApp=True,crashes=False,
    fixture='Explicit old-schema SharedPreferences seed, shown by original 0.6.17 before covering upgrade; no real account or network')
(OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
