"""Test real shared-storage writes using an original, local completed-download fixture."""
import hashlib,json,pathlib,re,subprocess,time,zipfile,xml.etree.ElementTree as E
APP='cn.zhigeng.app'
OUT=pathlib.Path('phone-download-check');OUT.mkdir(exist_ok=True)
def adb(*args):
    try:return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45)
    except subprocess.CalledProcessError as error:
        raise RuntimeError('adb '+repr(args)+' failed: '+error.output.decode(errors='replace')) from error
def clear_runtime_logs():
    # Restrict clearing to the buffers used by this check on older Android.
    for attempt in range(3):
        try:
            adb('logcat','-b','main','-b','crash','-c');return
        except RuntimeError:
            if attempt==2:raise
            time.sleep(1)
def dump():
    adb('shell','uiautomator','dump','/sdcard/phone-download.xml')
    return E.fromstring(adb('shell','cat','/sdcard/phone-download.xml'))
def tap(value):
    for n in dump().iter('node'):
        if value in (n.get('text'),n.get('content-desc')):
            a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
            if c>a and d>b:adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(1.5);return
    raise AssertionError('Missing download action: '+value)
def save_fixture():
    adb('shell','am','force-stop',APP)
    adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',APP+'/.MainActivity','--ez','download_export_test','true')
    for _ in range(10):
        if any(n.get('text')=='已保存到手机本地' for n in dump().iter('node')):break
        time.sleep(1)
    else:raise AssertionError('No completed public-copy confirmation')
    (OUT/'saved.xml').write_bytes(E.tostring(dump(),encoding='utf-8'))
    (OUT/'saved.png').write_bytes(adb('exec-out','screencap','-p'))
    assert any('下载/知更/' in n.get('text','') for n in dump().iter('node'))

clear_runtime_logs()
installed_version=int(re.search(r'versionCode=(\d+)',adb('shell','dumpsys','package',APP).decode()).group(1))
save_fixture()
directory='/sdcard/Download/知更'
names=lambda:[name for name in adb('shell','ls','-1',directory).decode().splitlines() if name.startswith('知更本地保存测试')]
before=names();assert len(before)==1,before
# Compare against the bundled source bytes, avoiding adb shell binary conversion
# and exec-out's handling of shell quotes in Unicode/duplicate file names.
with zipfile.ZipFile('apk-output/Zhigeng-debug-test.apk') as apk:
    original=apk.read('assets/reader-fixture.epub')
def check_copy(name):
    target=OUT/'pulled.epub'
    adb('pull',directory+'/'+name,target)
    actual=target.read_bytes();target.unlink()
    assert actual==original,dict(name=name,bytes=len(actual),expectedBytes=len(original),
        sha256=hashlib.sha256(actual).hexdigest(),expectedSha256=hashlib.sha256(original).hexdigest())
check_copy(before[0])
tap('立即阅读');time.sleep(6)
if any(n.get('text')=='GOT IT' for n in dump().iter('node')):tap('GOT IT');time.sleep(2)
assert any('第一章' in n.get('text','') for n in dump().iter('node'))
save_fixture();after=names();assert len(after)==2 and len(set(after))==2,after
for name in after:check_copy(name)
# Exercise the real system Save As picker without an account or external network.
tap('另存到…');time.sleep(2)
tree=dump();(OUT/'save-as.xml').write_bytes(E.tostring(tree,encoding='utf-8'))
assert any('SAVE'==n.get('text') or '保存'==n.get('text') for n in tree.iter('node'))
adb('shell','input','keyevent','4');time.sleep(1)
adb('shell','input','keyevent','4');time.sleep(1)
assert not any(n.get('text') in ['SAVE','保存'] for n in dump().iter('node'))
assert len(names())==2,'Cancelling Save As altered existing copies'
# Uninstall only the disposable emulator test app: public files must survive it.
assert b'Success' in adb('uninstall',APP)
for name in after:check_copy(name)
logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
assert 'FATAL EXCEPTION' not in logs,logs
result=dict(versionCode=installed_version,api=int(adb('shell','getprop','ro.build.version.sdk').strip()),
    directory='Download/知更',publicFilesVisible=True,bytesMatchCompletedDownload=True,
    duplicateNamesDoNotOverwrite=True,saveAsPicker=True,cancelPreservesFiles=True,
    independentOfAppUninstall=True,sha256=hashlib.sha256(original).hexdigest(),files=after,
    fixture='original local EPUB; account/network download is not exercised',crashes=False)
(OUT/'result.json').write_text(json.dumps(result,indent=2,ensure_ascii=False))
print(json.dumps(result,ensure_ascii=False),flush=True)
