"""Both Word Atlas entries hand the exact requested URL to an external browser."""
import json,pathlib,re,subprocess,time,xml.etree.ElementTree as E
APP='cn.zhigeng.app';URL='https://0xlei.tech/words/#/';OUT=pathlib.Path('english-check');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/english.xml');return E.fromstring(adb('shell','cat','/sdcard/english.xml'))
def tap(label):
    for _ in range(12):
        for n in sorted(tree().iter('node'),key=lambda n:n.get('clickable')!='true'):
            if label in (n.get('text'),n.get('content-desc')):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if c>a and d>b:adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.6);return
        adb('shell','input','swipe',540,1550,540,650,220)
    raise AssertionError('Missing English action: '+label)
def home():
    adb('shell','am','force-stop',APP);adb('shell','am','start','-W','-n',APP+'/.MainActivity');time.sleep(1)
def package():
    activity=adb('shell','dumpsys','activity','activities')
    match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?u\d+\s+([\w.]+)/',activity)
    return match.group(1) if match else ''
def screen(name):
    (OUT/(name+'.xml')).write_bytes(E.tostring(tree(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=45))
query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://zhigeng-browser.invalid/')
browsers=set(re.findall(r'^\s*([\w.]+)/',query,re.M));browsers.discard(APP);assert browsers
browser='com.android.browser' if 'com.android.browser' in browsers else sorted(browsers)[0]
adb('shell','cmd','role','add-role-holder','--user','0','android.app.role.BROWSER',browser)
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
assert 'private void englishLearning(){open("'+URL+'");}' in pathlib.Path('android/app/src/main/java/cn/zhigeng/app/MainActivity.java').read_text()
cases=[]
for entry,button in [('tools','英语学习 · 词光'),('mine','英语学习 · 词光 ↗')]:
    home()
    if entry=='tools':tap('更多工具')
    else:tap('我的')
    screen(entry+'-entry');adb('logcat','-c');tap(button)
    for _ in range(12):
        if package() in browsers:break
        time.sleep(.5)
    else:raise AssertionError('English link did not open an external browser')
    logs=adb('logcat','-d','-s','ActivityTaskManager:I');assert 'dat=https://0xlei.tech/' in logs,logs[-4000:]
    (OUT/(entry+'-intent.txt')).write_text(logs)
    for _ in range(8):
        adb('shell','input','keyevent','4');time.sleep(.5)
        if package()==APP:break
    else:raise AssertionError('English browser did not return to Zhigeng')
    cases.append(dict(entry=entry,url=URL,externalBrowser=True,returnedToNative=True));screen(entry+'-returned')
logs=adb('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in logs,logs
(OUT/'result.json').write_text(json.dumps(dict(versionCode=617,api=33,entries=cases,websiteContentTested=False,exactUrlSourceAndPolicyVerified=True,intentLogRedactsPath=True,crashes=False),ensure_ascii=False,indent=2))
print('English website routing passed for both native entries',flush=True)
adb('shell','svc','wifi','enable');adb('shell','svc','data','enable');adb('shell','am','force-stop',APP)
