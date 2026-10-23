"""Exercise the new native pages against original local TLS fixtures and real browser intents."""
import hashlib,json,pathlib,re,subprocess,sys,time,xml.etree.ElementTree as E,zipfile
APP='cn.zhigeng.app';OUT=pathlib.Path('reading-tools-check');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45)
def dump():
    adb('shell','uiautomator','dump','/sdcard/reading-tools.xml')
    return E.fromstring(adb('shell','cat','/sdcard/reading-tools.xml'))
def text():return ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in dump().iter('node'))
def wait(value):
    for _ in range(18):
        if value in text():return
        time.sleep(.7)
    raise AssertionError('Missing UI: '+value+'\n'+text())
def node(value):
    for _ in range(8):
        for n in dump().iter('node'):
            if n.get('package') in (APP,'android') and value in (n.get('text'),n.get('content-desc'),n.get('hint')):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if c>a and d>b:return ((a+c)//2,(b+d)//2)
        scrolls=[n for n in dump().iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true']
        if scrolls:
            a,b,c,d=map(int,re.findall(r'\d+',scrolls[0].get('bounds')))
            adb('shell','input','swipe',(a+c)//2,d-60,(a+c)//2,b+60,300)
        else:adb('shell','input','swipe',540,1400,540,550,300)
        time.sleep(.4)
    raise AssertionError('Missing action: '+value)
def tap(value):adb('shell','input','tap',*node(value));time.sleep(.6)
def fill(value,entry,clear=False):
    tap(value)
    if clear:
        adb('shell','input','keyevent','123');adb('shell','input','keyevent','--longpress','67')
        for _ in range(50):adb('shell','input','keyevent','67')
    adb('shell','input','text',entry);adb('shell','input','keyevent','4');time.sleep(.3)
def screen(name):
    print('Reading tools checkpoint:',name,flush=True)
    (OUT/(name+'.xml')).write_bytes(E.tostring(dump(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def launch():
    adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',APP+'/.MainActivity');time.sleep(1)
def menu(name):tap('更多工具');tap(name)
def browser():
    state=adb('shell','dumpsys','activity','activities').decode()
    match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?u\d+\s+([\w.]+)/',state)
    assert match and match.group(1) in BROWSERS,state

def return_resource():
    # Browsers may consume Back for their keyboard or navigation history first.
    for _ in range(8):
        state=adb('shell','dumpsys','activity','activities').decode()
        resumed=re.findall(r'(?:mResumedActivity|topResumedActivity)[^\n]*',state)
        if any(APP+'/.ResourceSearchActivity' in line for line in resumed):return
        adb('shell','input','keyevent','4');time.sleep(1)
    raise AssertionError('Browser did not return to the native resource screen: '+state)

def failure(kind,value,trace):
    try:screen('failure-state')
    except Exception:pass
    sys.__excepthook__(kind,value,trace)
sys.excepthook=failure

query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://zhigeng-browser.invalid/').decode()
BROWSERS=set(re.findall(r'^\s*([\w.]+)/',query,re.M));BROWSERS.discard(APP);assert BROWSERS,query
adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
adb('logcat','-c');launch();menu('极映空间 · 漫画搜索');wait('漫画导航');screen('01-resource-page')
# Unit tests check the exact q= URL. Android 13 redacts paths in activity logs;
# the UI smoke verifies a browsable external package and the intended host.
fill('输入漫画、书名或影视名称','Naruto');adb('logcat','-c');tap('去极映空间搜索');browser()
logs=adb('logcat','-d','-s','ActivityTaskManager:I','ActivityManager:I').decode();assert 'dat=https://jys66.top/' in logs,logs
return_resource();wait('极映空间')
tap('漫画导航');browser();logs=adb('logcat','-d','-s','ActivityTaskManager:I','ActivityManager:I').decode();assert 'dat=https://jys66.top/' in logs,logs
return_resource();tap('‹ 返回');wait('更多工具');menu('BookOrbit 书库');wait('连接书库');screen('02-bookorbit-connect')
fill('https://你的 BookOrbit 服务地址','https://10.0.2.2:8444');fill('BookOrbit 用户名','reader');fill('BookOrbit 密码','local-test-password')
tap('连接书库');wait('找到 21 本书');screen('03-bookorbit-library')
fill('搜索书名或作者（当前书库）','Spring');tap('搜索书库');wait('找到 1 本书');screen('04-search')
prefs=adb('exec-out','run-as',APP,'cat','shared_prefs/protected.xml')
for value in [b'local-test-password',b'orbit-protocol-access-not-a-secret',b'"refresh":']:
    assert value not in prefs,'unencrypted BookOrbit credential persisted'
adb('shell','am','force-stop',APP);launch();menu('BookOrbit 书库');wait('找到 21 本书')
# The encrypted session must reconnect after process death without a password.
fill('搜索书名或作者（当前书库）','Spring');tap('搜索书库');wait('找到 1 本书');tap('查看图书与版本');wait('下载 EPUB · 保存到手机');screen('05-book-detail')
tap('下载 EPUB · 保存到手机');wait('已保存到手机本地');screen('06-phone-copy')
names=adb('shell','ls','-1','/sdcard/Download/知更').decode().splitlines();assert 'Spring letter.epub' in names,names
target=OUT/'downloaded.epub';adb('pull','/sdcard/Download/知更/Spring letter.epub',target)
with zipfile.ZipFile('apk-output/Zhigeng-debug-test.apk') as apk:expected=apk.read('assets/reader-fixture.epub')
assert target.read_bytes()==expected;target.unlink()
tap('立即阅读');time.sleep(6)
if 'GOT IT' in text():tap('GOT IT')
wait('第一章');screen('07-native-reader');adb('shell','input','keyevent','4');time.sleep(1)
logs=adb('logcat','-d','-s','AndroidRuntime:E').decode();assert 'FATAL EXCEPTION' not in logs,logs
events=json.loads((OUT/'protocol.json').read_text())
for event in ['native-login','native-refresh','libraries','query:Spring:0','detail','download']:assert event in events,events
report=dict(versionCode=613,api=int(adb('shell','getprop','ro.build.version.sdk').strip()),
    separateNativePages=True,comicCategoryExternal=True,resourceSearchExternal=True,nativeBookOrbitLogin=True,
    tokenRefresh=True,librarySearch=True,encryptedSessionRestart=True,epubDownload=True,
    sharedCopyByteExact=True,nativeReader=True,sha256=hashlib.sha256(expected).hexdigest(),crashes=False,
    fixture='original local TLS BookOrbit contract and EPUB; no real user server credentials')
(OUT/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
