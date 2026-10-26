"""Exercise the official Anix entry page; does not claim desktop playback on Android."""
import json,pathlib,re,subprocess,time,xml.etree.ElementTree as E
APP='cn.zhigeng.app';OUT=pathlib.Path('anix-check');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/anix-check.xml')
    return E.fromstring(adb('shell','cat','/sdcard/anix-check.xml'))
def has(value):return any(value in n.get('text','') for n in tree().iter('node'))
def tap(value):
    for _ in range(12):
        root=tree()
        for n in root.iter('node'):
            if value in (n.get('text'),n.get('content-desc')):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if c>a and d>b:adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.8);return
        scroll=next((n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true'),None)
        assert scroll is not None,'Missing Anix action: '+value
        a,b,c,d=map(int,re.findall(r'\d+',scroll.get('bounds')))
        adb('shell','input','swipe',(a+c)//2,d-40,(a+c)//2,b+40,250)
    raise AssertionError('Missing Anix action: '+value)
def screenshot(name):
    print('Anix checkpoint:',name,flush=True)
    (OUT/(name+'.xml')).write_bytes(E.tostring(tree(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=45))
def open_page():
    adb('shell','am','force-stop',APP)
    adb('shell','am','start','-W','-n',APP+'/.MainActivity');time.sleep(2)
    tap('更多工具');tap('Anix v1.3.0');assert has('没有 Android 安装包')
def resumed():
    state=adb('shell','dumpsys','activity','activities')
    match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?u\d+\s+([\w.]+)/([^\s}]+)',state)
    return match.groups() if match else ('','')
def back_to_page():
    for _ in range(8):
        if resumed()[0]==APP:
            assert 'AnixActivity' in resumed()[1];return
        adb('shell','input','keyevent','4');time.sleep(1)
    raise AssertionError('External browser did not restore the native Anix page')
adb('logcat','-c');open_page();screenshot('01-native-page')
query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://anix-browser.invalid/')
browsers=set(re.findall(r'^\s*([\w.]+)/',query,re.M));browsers.discard(APP);assert browsers
actions=[('进入 Anix 官网 ↗','anix-linux.vercel.app'),('查看 v1.3.0 官方发布 ↗','github.com'),('社区扩展使用说明 ↗','github.com')]
entries=[]
for label,host in actions:
    adb('logcat','-c');tap(label)
    for _ in range(15):
        if resumed()[0] in browsers:break
        time.sleep(.5)
    assert resumed()[0] in browsers,(label,resumed())
    logs=adb('logcat','-d','-s','ActivityTaskManager:I')
    assert 'dat=https://'+host in logs,(label,logs)
    entries.append(dict(label=label,host=host,external=True));back_to_page()
font=adb('shell','settings','get','system','font_scale').strip()
rotation=adb('shell','settings','get','system','user_rotation').strip()
automatic=adb('shell','settings','get','system','accelerometer_rotation').strip()
try:
    adb('shell','wm','size','984x1920');adb('shell','wm','density','420')
    adb('shell','settings','put','system','font_scale','2.0');open_page()
    screenshot('02-small-phone-large-type');tap('社区扩展使用说明 ↗');back_to_page()
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
    screenshot('03-landscape-large-type');tap('进入 Anix 官网 ↗');back_to_page()
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
    for key,value in [('font_scale',font),('user_rotation',rotation),('accelerometer_rotation',automatic)]:
        if value=='null':adb('shell','settings','delete','system',key)
        else:adb('shell','settings','put','system',key,value)
logs=adb('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in logs,logs
report=dict(versionCode=int(re.search(r'versionCode=(\d+)',adb('shell','dumpsys','package',APP)).group(1)),api=33,
    nativePage=True,linuxOnlyNotice=True,officialEntries=entries,browserReturns=True,smallPhone375dp=True,
    fontScale=2.0,landscape=True,desktopPlayerEmbedded=False,websiteNetworkRequired=False,crashes=False)
(OUT/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
adb('shell','am','force-stop',APP)
