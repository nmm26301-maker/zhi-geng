"""Exercise real website buttons and verify Android hands their URL to a browser app."""
import json, pathlib, re, subprocess, time, xml.etree.ElementTree as E

APP='cn.zhigeng.app'
OUT=pathlib.Path('browser-check');OUT.mkdir(exist_ok=True)
results=[]
def adb(*args):
    return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def dump():
    adb('shell','uiautomator','dump','/sdcard/browser-check.xml')
    return E.fromstring(adb('shell','cat','/sdcard/browser-check.xml'))
def tap(text,scrolls=5):
    for attempt in range(scrolls+1):
        for retry in range(4):
            tree=dump()
            for node in tree.iter('node'):
                if text in [node.get('text'),node.get('content-desc')]:
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
                    if x2>x1 and y2>y1:
                        adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2);time.sleep(1);return
            time.sleep(.5)
        if attempt<scrolls:
            adb('shell','input','swipe','540','1550','540','650','350');time.sleep(.5)
    (OUT/'missing-entry.xml').write_bytes(E.tostring(tree,encoding='utf-8'))
    (OUT/'missing-activity.txt').write_text(adb('shell','dumpsys','activity','activities'))
    raise AssertionError('Missing website entry: '+text)
def home():
    # End the external browser task before cold-starting the native task. No app data is cleared.
    for package in browsers:adb('shell','am','force-stop',package)
    adb('shell','am','force-stop',APP)
    adb('shell','am','start','-W','-f','0x10008000','-n',APP+'/.MainActivity')
    for retry in range(5):
        if any(n.get('content-desc')=='更多工具' for n in dump().iter('node')):return
        time.sleep(.5)
    raise AssertionError('Native home did not become ready')
def tools(name):
    home();tap('更多工具',0);tap(name,0)
def top_package():
    value=adb('shell','dumpsys','activity','activities')
    match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?\s([a-zA-Z][\w.]*)/',value)
    assert match, value
    return match.group(1)
def external(name,button,host):
    adb('logcat','-c');tap(button);time.sleep(1)
    package=top_package()
    logs=adb('logcat','-d','-s','ActivityTaskManager:I','ActivityManager:I')
    assert package in browsers,(name,package,browsers)
    assert host in logs,(name,host,logs[-5000:])
    assert 'act=android.intent.action.VIEW' in logs,(name,logs[-3000:])
    (OUT/(name+'.txt')).write_text(logs)
    results.append(dict(entry=name,browserPackage=package,host=host,external=True))
    print('External browser verified:',name,package,host,flush=True)

query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW',
          '-c','android.intent.category.BROWSABLE','-d','https://zhigeng-browser.invalid/')
browsers=set(re.findall(r'^\s*([\w.]+)/',query,re.M));browsers.discard(APP)
assert browsers,query
# This changes only the disposable emulator, and removes first-use chooser ambiguity.
preferred='com.android.browser' if 'com.android.browser' in browsers else sorted(browsers)[0]
adb('shell','cmd','role','add-role-holder','--user','0','android.app.role.BROWSER',preferred)
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')

home();tap('榜单');tap('编辑推荐');tap('我不是药神')
external('01-work-source','在浏览器中查看来源资料','search.douban.com')
tools('看剧 AI');external('02-kanju-home','浏览器打开看剧 AI 官网','kanju.ai')
tools('看剧 AI');tap('输入想看的片名…');adb('shell','input','text','Zhigeng');adb('shell','input','keyevent','4')
external('03-kanju-search','用浏览器找片','kanju.ai')
tools('接码导航');external('04-sms','访问 FreePhoneNum','freephonenum.com')
tools('节点导航 5＋5');tap('代理 IP 5家')
external('05-proxy','访问 Decodo 德口多','decodo.cn')
tools('节点导航 5＋5');tap('详情 Decodo 德口多')
external('06-directory-details','浏览器打开','decodo.cn')
tools('AI 会员 5＋2');external('07-ai-membership','访问 GamsGo','gamsgo.com')
tools('AI 会员 5＋2');tap('备选 2个')
external('08-ai-alternative','访问 环球巴士','universalbus.cn')
home();tap('打开 AI 助手');external('09-duck-ai','浏览器打开 Duck.ai  →','duck.ai')
home();tap('打开 AI 助手');tap('助手菜单');external('10-duck-menu','打开 Duck.ai','duck.ai')
home();tap('打开 AI 助手');tap('FCC 多模型');tap('服务商');tap('NVIDIA NIM')
external('11-fcc-provider','浏览器查看官方入口','build.nvidia.com')
home();tap('我的');external('12-project','开源项目与许可 ↗','github.com')
home();tap('书架')
logs=adb('logcat','-d','-s','AndroidRuntime:E');(OUT/'runtime.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,logs
report=dict(applicationId=APP,versionCode=609,api=29,externalBrowserEntries=results,
            websiteNetworkRequired=False,appResumedAfterBrowser=True,crashes=False)
(OUT/'result.json').write_text(json.dumps(report,indent=2,ensure_ascii=False))
print('Website routing passed:',len(results),'native entries opened the external browser')
