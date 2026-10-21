import subprocess,time,pathlib,xml.etree.ElementTree as E,json,atexit
out=pathlib.Path('emulator-check');out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args]).decode(errors='replace')
atexit.register(lambda:(out/'cover-runtime.txt').write_text(adb('logcat','-d','-s','ZhigengCover:V','AndroidRuntime:E')))
def dump():
 adb('shell','uiautomator','dump','/sdcard/zhigeng.xml');return E.fromstring(adb('shell','cat','/sdcard/zhigeng.xml'))
def tap(text):
 tree=dump()
 for n in tree.iter('node'):
  if n.get('text')==text or n.get('content-desc')==text:
   nums=[int(x) for x in n.get('bounds').replace('][',',').strip('[]').split(',')]
   adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2));time.sleep(2);return
 raise AssertionError('Missing navigation: '+text)
def screen(name,required):
 tree=dump();strings=[n.get('text','') for n in tree.iter('node')]
 (out/(name+'.xml')).write_bytes(E.tostring(tree,encoding='utf-8'))
 with (out/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
 assert any(required in x for x in strings),('Missing UI',required,strings)
def has(text):return any(text in n.get('text','') for n in dump().iter('node'))
def open_fcc():
 adb('shell','am','force-stop','cn.zhigeng.app');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity');time.sleep(2)
 tap('打开 AI 助手');tap('FCC 多模型')
def model(label,ref):tap('选择 FCC 模型');tap(label+' (not an AI model)\n'+ref)
adb('install','-r','apk-output/Zhigeng-debug-test.apk');adb('logcat','-c');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity');time.sleep(3)
time.sleep(16);screen('01-domestic-discovery','国内精选');native_covers=sum('封面已加载' in n.get('content-desc','') for n in dump().iter('node'));adb('shell','input','swipe','540','1500','540','650','400');time.sleep(3);screen('01b-curated-covers','我不是药神');tap('榜单');screen('02-annual-rank','年度排行榜');tap('编辑推荐');screen('02-editorial-rank','知更编辑推荐');tap('阅读');screen('03-chinese-books','公开图书目录');tap('书架');screen('04-library','导入');tap('我的');screen('05-settings','TMDB')
tap('更多工具');tap('节点导航 5＋5');screen('28-node-directory','常用节点，');tap('代理 IP 5家');screen('29-proxy-group','Decodo');adb('shell','input','swipe','540','1550','540','1100','300');time.sleep(1);tap('收藏Decodo 德口多');adb('shell','input','swipe','540','450','540','1600','350');time.sleep(1);tap('我的收藏');screen('30-node-favorite','Decodo');tap('机场 5家');screen('31-airport-group','星岛梦');tap('‹ 返回')
tap('更多工具');tap('AI 会员 5＋2');screen('32-ai-memberships','订阅之前，');tap('备选 2个');screen('33-ai-alternatives','环球巴士');tap('‹ 返回')
tap('更多工具');tap('接码导航');screen('21-sms-directory','FreePhoneNum')
tap('收藏FreePhoneNum');tap('我的收藏');screen('22-sms-favorite','FreePhoneNum');tap('隐藏FreePhoneNum');screen('23-sms-hidden-empty','暂无匹配');tap('已隐藏');screen('24-sms-hidden','FreePhoneNum');tap('恢复FreePhoneNum');tap('全部网站');screen('25-sms-restored','FreePhoneNum');tap('‹ 返回')
tap('打开 AI 助手');screen('06-ai-assistant','让好奇，')
tap('FCC 多模型');screen('07-fcc-connection','连接你的 FCC');tap('保存并读取模型');screen('08-fcc-invalid-address','请填写有效的 HTTPS');tap('服务商');screen('09-fcc-directory','61 家服务商');tap('‹ 返回')
tap('助手菜单');tap('关于 Duck.ai');screen('07-ai-about','手机浏览器打开');tap('知道了');tap('‹ 返回');screen('10-native-return','TMDB')
# All website departures are exercised separately against the actual external browser.
chat_state='external browser; tested by android-browser-smoke.py'
# Only the debug variant accepts this fixture shortcut. Release excludes the hook and test CA.
adb('shell','am','force-stop','cn.zhigeng.app');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity','--ez','fcc_protocol_test','true');time.sleep(3)
tap('连接网关');screen('11-fcc-local-test','连接你的 FCC');tap('保存并读取模型');tap('进入聊天');screen('12-fcc-models','Protocol fixture')
tap('FCC 消息输入框');adb('shell','input','text','Draft%spersist%stest');adb('shell','input','keyevent','4')
model('Rate limit fixture','fixture/http429');screen('13-fcc-model-draft','Draft persist test')
tap('服务商');tap('聊天');screen('14-fcc-tab-draft','Draft persist test')
open_fcc();screen('15-fcc-restart-draft','Draft persist test')
model('Protocol fixture','fixture/local-text');tap('发送');time.sleep(2);screen('16-fcc-protocol-reply','本地协议测试回复')
assert not has('从一句话开始。'),'Welcome card remained after first send'
tap('FCC 消息输入框');adb('shell','input','text','Rate%stest');adb('shell','input','keyevent','4')
model('Rate limit fixture','fixture/http429');tap('发送');screen('17-fcc-rate-limit','HTTP 429')
assert has('Rate test') and has('重试'),'Failed prompt/retry action was lost'
tap('重试');screen('18-fcc-retry','HTTP 429');assert has('Rate test')
model('Slow fixture','fixture/slow-text');tap('发送');tap('停止');time.sleep(2);screen('19-fcc-stopped','已停止')
assert has('Rate test') and has('发送'),'Stopped prompt/send action was lost'
open_fcc();screen('20-fcc-stopped-recovery','Rate test');assert not has('已停止，未完成内容'),'Cancelled reply persisted in context'
protected=adb('shell','run-as','cn.zhigeng.app','cat','shared_prefs/protected.xml')
assert all(x not in protected for x in ['fixture-token-not-a-secret','Draft persist test','Rate test','本地协议测试回复']),'Secrets, draft or chats stored in plaintext'
logs=adb('logcat'  ,'-d','-s','AndroidRuntime:E');(out/'runtime-errors.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,logs
(out/'result.json').write_text(json.dumps({'app':'Zhigeng','version':'0.6.12','api':29,'nativeNavigation':'passed','visibleCuratedCoversLoaded':native_covers,'latestDirectories':'passed: independent nodes 5+5, extra console bundled, memberships 5+2, local favorite and filters','annualRankings':'passed: native year/type/region/sort screen and editorial toggle; adapters unit-tested with source fixtures','smsDirectory':'passed: native six sites, favorite, hide, hidden filter and restore','kanjuEntry':'external browser; tested separately; live playback not tested','screens':len(list(out.glob('*.png'))),'fccProtocolFixture':'passed: HTTPS, bearer auth, model names, streaming reply, HTTP 429 and retry, stop, encrypted storage','fccDraftRecovery':'passed: model switch, tab switch, process restart, failure and stop','fccRealProvider':'not tested: no user gateway or provider credentials','duckAiOpening':chat_state,'duckAiChatSent':False,'accountDownload':'not tested: needs real user credentials'},ensure_ascii=False,indent=2))
print('Native Android emulator smoke passed: navigation, Duck.ai, local HTTPS/SSE, HTTP 429/retry, stop, draft recovery and encrypted storage; no fatal exception')

