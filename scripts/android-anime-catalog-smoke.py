"""Native catalogue, retry, local progress and offline restart against original TLS data."""
import json,pathlib,re,subprocess,sys,time,xml.etree.ElementTree as E
APP='cn.zhigeng.app';OUT=pathlib.Path('anix-check');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*map(str,args)],stderr=subprocess.STDOUT,timeout=45).decode(errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/anime.xml');return E.fromstring(adb('shell','cat','/sdcard/anime.xml'))
def has(value):return any(value.casefold() in n.get('text','').casefold() for n in tree().iter('node'))
def top():
    for _ in range(5):adb('shell','input','swipe',540,450,540,1600,160)
def wait(value):
    for _ in range(16):
        if has(value):return
        time.sleep(.5)
    raise AssertionError('Missing anime UI: '+value)
def tap(value):
    for _ in range(12):
        root=tree()
        for n in root.iter('node'):
            if value.casefold() in (n.get('text','').casefold(),n.get('content-desc','').casefold()):
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                if c>a and d>b:adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.6);return
        scroll=next((n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true'),None)
        assert scroll is not None,'Missing anime action: '+value
        a,b,c,d=map(int,re.findall(r'\d+',scroll.get('bounds')));height=d-b
        adb('shell','input','swipe',(a+c)//2,b+int(height*.85),(a+c)//2,b+int(height*.35),250)
    raise AssertionError('Missing anime action: '+value)
def fill(label,value):
    tap(label);adb('shell','input','keyevent','123')
    for _ in range(30):adb('shell','input','keyevent','67')
    adb('shell','input','text',value);adb('shell','input','keyevent','4')
def screen(name):
    print('Anime checkpoint:',name,flush=True);(OUT/(name+'.xml')).write_bytes(E.tostring(tree(),encoding='utf-8'))
    (OUT/(name+'.png')).write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=45))
def failed(kind,value,trace):
    try:
        screen('failure-state');(OUT/'runtime-failure.txt').write_text(adb('logcat','-d','-s','AndroidRuntime:E'))
    except Exception:pass
    sys.__excepthook__(kind,value,trace)
sys.excepthook=failed
def launch():
    adb('shell','am','force-stop',APP);adb('shell','am','start','-W','-n',APP+'/.AnixTestActivity');time.sleep(1)
def external(host):
    query=adb('shell','cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.BROWSABLE','-d','https://anime-browser.invalid/')
    browsers=set(re.findall(r'^\s*([\w.]+)/',query,re.M));browsers.discard(APP)
    for _ in range(16):
        state=adb('shell','dumpsys','activity','activities')
        match=re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?u\d+\s+([\w.]+)/',state)
        if match and match.group(1) in browsers:break
        time.sleep(.5)
    else:raise AssertionError('Anime link did not use an external browser')
    assert 'dat=https://'+host in adb('logcat','-d','-s','ActivityTaskManager:I')
    for _ in range(8):
        adb('shell','input','keyevent','4');time.sleep(.7)
        state=adb('shell','dumpsys','activity','activities')
        if re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*'+re.escape(APP)+r'/[^\n]*AnixTestActivity',state):
            time.sleep(1);ime=adb('shell','dumpsys','input_method')
            if 'mInputShown=true' in ime or 'mIsInputViewShown=true' in ime:adb('shell','input','keyevent','4');time.sleep(.7)
            top();return
    raise AssertionError('Browser did not restore the anime screen')
adb('logcat','-c');adb('shell','am','start','-W','-n',APP+'/.MainActivity');time.sleep(1)
tap('更多工具');tap('Anix v1.3.0');wait('搜索动漫名称');screen('00-main-entry')
launch();wait('动漫目录 · 第 1 页 · 2 部');screen('01-native-catalogue')
tap('动漫下一页');wait('动漫目录 · 第 2 页 · 1 部');screen('02-next-page');tap('动漫上一页');wait('动漫目录 · 第 1 页 · 2 部')
top();fill('输入动漫名称','Fixture');tap('搜索动漫');wait('动漫目录 · 第 1 页 · 1 部')
tap('动漫详情');adb('logcat','-c');tap('查看 AniList 条目 ↗');external('anilist.co');screen('02a-external-detail-return')
tap('加入追番');tap('选择追番状态');tap('在看');fill('填写已看集数','3');tap('保存追番');wait('已看 3 集');screen('03-local-progress')
top();tap('我的追番');wait('本地追番 · 1 部');events=len(json.loads((OUT/'protocol.json').read_text()))
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable');launch();wait('本地追番 · 1 部');wait('已看 3 集')
assert len(json.loads((OUT/'protocol.json').read_text()))==events,'offline watchlist made a catalogue request'
screen('04-offline-restart');tap('更新追番');fill('填写已看集数','13');tap('保存追番');wait('已看集数不能超过总集数');tap('取消')
top();tap('我的追番');tap('更新追番');tap('移出追番');tap('保留');wait('已看 3 集')
top();tap('我的追番');tap('更新追番');tap('移出追番');tap('移出');wait('本地追番 · 0 部');screen('05-remove-confirmed')
adb('shell','svc','wifi','enable');adb('shell','svc','data','enable');top();tap('热门动漫');wait('动漫目录 · 第 1 页 · 2 部')
top();fill('输入动漫名称','retry');tap('搜索动漫');wait('HTTP 503');tap('重试动漫目录');wait('动漫目录 · 第 1 页 · 1 部');screen('06-transient-retry')
top();fill('输入动漫名称','graphql-error');tap('搜索动漫');wait('动漫目录返回错误');screen('07-graphql-error')
top();tap('我的追番');wait('本地追番 · 0 部');top();tap('热门动漫');wait('动漫目录返回错误')
top();fill('输入动漫名称','limited');tap('搜索动漫');wait('动漫服务请求较多');screen('08-rate-limit')
top();tap('我的追番');wait('本地追番 · 0 部');tap('Anix 官方项目与使用说明');wait('官方没有 Android 安装包')
adb('logcat','-c');tap('查看 v1.3.0 官方发布 ↗');external('github.com')
font=adb('shell','settings','get','system','font_scale').strip();rotation=adb('shell','settings','get','system','user_rotation').strip();auto=adb('shell','settings','get','system','accelerometer_rotation').strip()
try:
    adb('shell','wm','size','984x1920');adb('shell','wm','density','420');adb('shell','settings','put','system','font_scale','2.0');launch()
    screen('09-small-phone-large-type');tap('Anix 官方项目与使用说明');wait('Anix v1.3.0');tap('关闭')
    adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');time.sleep(1)
    screen('10-landscape-large-type')
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
    for key,value in [('font_scale',font),('user_rotation',rotation),('accelerometer_rotation',auto)]:
        if value=='null':adb('shell','settings','delete','system',key)
        else:adb('shell','settings','put','system',key,value)
logs=adb('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in logs,logs
events=json.loads((OUT/'protocol.json').read_text());assert dict(term='',page=2) in events and dict(term='Fixture',page=1) in events
assert sum(x['term']=='retry' for x in events)==2,events
report=dict(versionCode=int(re.search(r'versionCode=(\d+)',adb('shell','dumpsys','package',APP)).group(1)),api=33,nativeCatalogue=True,search=True,
    hasNextPagePagination=True,localWatchlist=True,progressValidation=True,offlineRestart=True,removalConfirmation=True,
    transientRetry=True,graphQlError=True,rateLimit=True,detailExternalBrowser=True,officialReleaseExternal=True,browserReturns=True,smallPhone375dp=True,fontScale=2.0,landscape=True,
    desktopPlayerEmbedded=False,fixture='original local TLS AniList contract; live availability reported separately',crashes=False)
(OUT/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
adb('shell','am','force-stop',APP)
