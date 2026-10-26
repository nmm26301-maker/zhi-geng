"""Exercise the new native pages against original local TLS fixtures and real browser intents."""
import hashlib,json,pathlib,re,ssl,subprocess,sys,time,urllib.request,xml.etree.ElementTree as E,zipfile
API=int(subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk']).strip())
APP='cn.zhigeng.app';OUT=pathlib.Path('reading-tools-check');OUT.mkdir(exist_ok=True)
def adb(*args):
    try:return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45)
    except subprocess.CalledProcessError as error:
        raise RuntimeError('adb '+repr(args)+' failed: '+error.output.decode(errors='replace')) from error
LOG_BASE=set()
def clear_logs():
    global LOG_BASE
    if API<28:
        # Older emulator logd can reject clearing; compare against a snapshot instead.
        LOG_BASE=set(adb('logcat','-d').decode(errors='replace').splitlines())
    else:adb('logcat','-c')
def read_logs(*filters):
    raw=adb('logcat','-d','-s',*filters).decode(errors='replace')
    return '\n'.join(line for line in raw.splitlines() if line not in LOG_BASE) if API<28 else raw

def dump():
    adb('shell','uiautomator','dump','/sdcard/reading-tools.xml')
    return E.fromstring(adb('shell','cat','/sdcard/reading-tools.xml'))
def text():return ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in dump().iter('node'))
def wait(value):
    for _ in range(18):
        if value in text():return
        if API==26 and _>0 and _%3==0:
            start,end=(1450,500) if (_//3)%2 else (500,1450)
            adb('shell','input','swipe',540,start,540,end,300)
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
def top():
    for _ in range(5):adb('shell','input','swipe',540,450,540,1600,180)
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
def library_mode(mode):
    request=urllib.request.Request('https://localhost:8444/fixture/libraries',json.dumps({'mode':mode}).encode(),{'Content-Type':'application/json'})
    # This is the original localhost-only fixture, never a real user's server.
    with urllib.request.urlopen(request,context=ssl._create_unverified_context(),timeout=5) as response:assert response.status==200
def disabled(value):
    node(value)
    return any(n.get('text')==value and n.get('enabled')=='false' for n in dump().iter('node'))
def launch():
    adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',APP+'/.MainActivity');time.sleep(1)
def menu(name):tap('更多工具');tap(name)
def browser():
    for _ in range(30):
        state=adb('shell','dumpsys','activity','activities').decode()
        match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?u\d+\s+([\w.]+)/',state)
        if match and match.group(1) in BROWSERS:return
        if API==26:
            tree=dump()
            # Old dumpsys retains finished chooser records; act only on visible UI.
            if any(n.get('package') in BROWSERS for n in tree.iter('node')):return
            if any(n.get('package')=='android' and n.get('text')=='选择外部浏览器' for n in tree.iter('node')):
                tap('WebView Browser Tester');time.sleep(1);continue
        time.sleep(.7)
    raise AssertionError('External browser did not become ready: '+state)

browser_returns=[]
def return_resource():
    method='back'
    # Browsers may consume Back for their keyboard or navigation history first.
    for attempt in range(8):
        tree=dump()
        if any(n.get('package')==APP and n.get('text')=='极映空间' for n in tree.iter('node')):
            browser_returns.append(method);return
        state=adb('shell','dumpsys','activity','activities').decode()
        if API==26:
            (OUT/('return-'+str(len(browser_returns))+'-'+str(attempt)+'.txt')).write_text(state)
            (OUT/('return-'+str(len(browser_returns))+'-'+str(attempt)+'.xml')).write_bytes(E.tostring(tree,encoding='utf-8'))
        resumed=re.findall(r'(?:mResumedActivity|topResumedActivity)[^\n]*',state)
        if any(APP+'/' in line and 'ResourceSearchActivity' in line for line in resumed):
            browser_returns.append(method);return
        if API==26 and any('com.android.launcher' in line for line in resumed):
            # Android 8 can return a separate browser task to Home. Reopening the
            # actual launcher entry must preserve the native resource activity.
            adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',APP+'/.MainActivity')
            method='launcher';time.sleep(1);continue
        adb('shell','input','keyevent','4');time.sleep(1)
    raise AssertionError('Browser did not return to the native resource screen: '+state)

def failure(kind,value,trace):
    try:
        (OUT/'runtime-failure.txt').write_text(read_logs('AndroidRuntime:E'))
        screen('failure-state')
    except Exception:pass
    sys.__excepthook__(kind,value,trace)
sys.excepthook=failure

query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://zhigeng-browser.invalid/').decode()
BROWSERS=set(re.findall(r'^\s*([\w.]+)/',query,re.M));BROWSERS.discard(APP);assert BROWSERS,query
# Fresh API 26 emulators already have networking; their legacy svc process can exit 137.
# The following real HTTPS login still verifies connectivity rather than masking errors.
if API>=28:
    adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
resource_error=''
try:
    clear_logs();launch();menu('极映空间 · 漫画搜索');wait('极映空间');screen('01-resource-page')
    # Unit tests check the exact q= URL. Android 13 redacts paths in activity logs;
    # the UI smoke verifies a browsable external package and the intended host.
    fill('输入漫画、书名或影视名称','Naruto');clear_logs();tap('去极映空间搜索');browser()
    logs=read_logs('ActivityTaskManager:I','ActivityManager:I');assert 'dat=https://jys66.top/' in logs,logs
    return_resource();wait('极映空间')
    tap('漫画导航');browser();logs=read_logs('ActivityTaskManager:I','ActivityManager:I');assert 'dat=https://jys66.top/' in logs,logs
    return_resource();tap('‹ 返回');wait('更多工具');
except Exception as error:
    resource_error=str(error)
    screen('resource-failure')
    (OUT/'resource-runtime.txt').write_text(read_logs('AndroidRuntime:E'))
    adb('shell','am','force-stop',APP);launch()
menu('BookOrbit 书库');wait('BookOrbit');screen('02-bookorbit-connect')
fill('https://你的 BookOrbit 服务地址','https://10.0.2.2:8444');fill('BookOrbit 用户名','reader');fill('BookOrbit 密码','local-test-password')
tap('连接书库');wait('找到 21 本书');screen('03-bookorbit-library')
tap('下一页');wait('第 2 页');library_mode('replaced');top();tap('刷新书库');wait('Replacement fixture library');wait('找到 21 本书')
node('上一页');wait('第 1 页');assert disabled('上一页');screen('03c-replaced-library-first-page')
for mode in ['empty','podcasts']:
    library_mode(mode);top();tap('刷新书库');wait('当前账号没有可浏览的图书书库')
    assert disabled('搜索书库');assert disabled('上一页');assert disabled('下一页')
    top();tree=dump();assert not any(n.get('class')=='android.widget.Spinner' for n in tree.iter('node'))
    assert not any('第 1 页' in n.get('text','') or '第 2 页' in n.get('text','') for n in tree.iter('node'))
    screen('03d-'+mode+'-library')
library_mode('original');top();tap('刷新书库');wait('找到 21 本书')
fill('搜索书名或作者（当前书库）','retry');tap('搜索书库');wait('HTTP 503');tap('重试');wait('找到 1 本书');screen('03a-retry-retains-query')
fill('搜索书名或作者（当前书库）','expire',clear=True);tap('搜索书库');wait('连接已失效，请重新登录');screen('03b-expired-session');tap('重新登录')
adb('shell','am','force-stop',APP);launch();menu('BookOrbit 书库');wait('BookOrbit')
fill('BookOrbit 用户名','reader');fill('BookOrbit 密码','local-test-password');tap('连接书库');wait('找到 21 本书')
fill('搜索书名或作者（当前书库）','Spring');tap('搜索书库');wait('找到 1 本书');screen('04-search')
prefs=adb('exec-out','run-as',APP,'cat','shared_prefs/protected.xml')
for value in [b'local-test-password',b'orbit-protocol-access-not-a-secret',b'"refresh":']:
    assert value not in prefs,'unencrypted BookOrbit credential persisted'
adb('shell','am','force-stop',APP);launch();menu('BookOrbit 书库');wait('找到 21 本书')
# The encrypted session must reconnect after process death without a password.
fill('搜索书名或作者（当前书库）','Spring');tap('搜索书库');wait('找到 1 本书');tap('查看图书与版本');wait('下载 EPUB · 保存到手机');screen('05-book-detail')
tap('下载 EPUB · 保存到手机')
if API<29:
    # Android 8/9 uses the real system document picker instead of MediaStore.
    def picker_save():
        for _ in range(15):
            tree=dump()
            for n in tree.iter('node'):
                if n.get('package','').endswith('documentsui') and n.get('text','') in ('SAVE','Save','保存'):
                    a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                    if c>a and d>b:return ((a+c)//2,(b+d)//2)
            time.sleep(.7)
        raise AssertionError('System Save As picker was not ready: '+E.tostring(tree,encoding='unicode'))
    picker_save();screen('06a-save-as-picker')
    adb('shell','input','keyevent','4');time.sleep(1);wait('Spring letter')
    # Cancelling an export must retain the downloaded book in the original shelf.
    assert subprocess.run(['adb','shell','test','-e','/sdcard/Download/Spring letter.epub']).returncode!=0
    tap('Spring letter');tap('保存到手机本地')
    adb('shell','input','tap',*picker_save())
wait('已保存到手机本地');screen('06-phone-copy')
directory='/sdcard/Download/知更' if API>=29 else '/sdcard/Download'
names=adb('shell','ls','-1',directory).decode().splitlines();assert 'Spring letter.epub' in names,names
target=OUT/'downloaded.epub';adb('pull',directory+'/Spring letter.epub',target)
with zipfile.ZipFile('apk-output/Zhigeng-debug-test.apk') as apk:expected=apk.read('assets/reader-fixture.epub')
assert target.read_bytes()==expected;target.unlink()
tap('立即阅读');time.sleep(6)
if 'GOT IT' in text():tap('GOT IT')
wait('第一章');screen('07-native-reader');adb('shell','input','keyevent','4');time.sleep(1)
logs=read_logs('AndroidRuntime:E');assert 'FATAL EXCEPTION' not in logs,logs
events=json.loads((OUT/'protocol.json').read_text())
for event in ['native-login','native-refresh','libraries','library-query:1:1','library-query:2:0','library-mode:empty','library-mode:podcasts','transient-error','session-revoked','refresh-revoked','query:Spring:0','detail','download']:assert event in events,events
assert 'library-query:2:1' not in events,'replacement library inherited the previous page'
report=dict(versionCode=int(re.search(r'versionCode=(\d+)',adb('shell','dumpsys','package',APP).decode()).group(1)),api=API,storageStrategy='MediaStore' if API>=29 else 'SAF',
    saveAsCancellationChecked=API<29,
    browserReturnMethods=browser_returns,separateNativePages=True,comicCategoryExternal=not resource_error,resourceSearchExternal=not resource_error,nativeBookOrbitLogin=True,
    tokenRefresh=True,transientRetry=True,expiredSessionRecovery=True,emptyLibraryRecovery=True,replacedLibraryPageReset=True,librarySearch=True,encryptedSessionRestart=True,epubDownload=True,
    sharedCopyByteExact=True,nativeReader=True,sha256=hashlib.sha256(expected).hexdigest(),crashes=False,
    resourceError=resource_error,fixture='original local TLS BookOrbit contract and EPUB; no real user server credentials')
(OUT/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)

if resource_error:raise SystemExit('BookOrbit checks passed; resource-browser check remains failed; see result.json')

