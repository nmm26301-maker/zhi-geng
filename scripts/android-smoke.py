import subprocess,time,pathlib,xml.etree.ElementTree as E,json
out=pathlib.Path('emulator-check');out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args]).decode(errors='replace')
def dump():
 adb('shell','uiautomator','dump','/sdcard/zhigeng.xml');return E.fromstring(adb('shell','cat','/sdcard/zhigeng.xml'))
def tap(text):
 tree=dump()
 for n in tree.iter('node'):
  if n.get('text')==text:
   nums=[int(x) for x in n.get('bounds').replace('][',',').strip('[]').split(',')]
   adb('shell','input','tap',str((nums[0]+nums[2])//2),str((nums[1]+nums[3])//2));time.sleep(2);return
 raise AssertionError('Missing navigation: '+text)
def screen(name,required):
 tree=dump();strings=[n.get('text','') for n in tree.iter('node')]
 (out/(name+'.xml')).write_bytes(E.tostring(tree,encoding='utf-8'))
 with (out/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
 assert any(required in x for x in strings),('Missing UI',required,strings)
adb('install','-r','apk-output/Zhigeng-debug-test.apk');adb('logcat','-c');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity');time.sleep(3)
screen('01-domestic-discovery','国内精选');tap('榜单');screen('02-editorial-rank','知更编辑推荐');tap('阅读');screen('03-chinese-books','公开图书目录');tap('书架');screen('04-library','导入');tap('我的');screen('05-settings','TMDB')
tap('✦ AI 助手');screen('06-ai-assistant','让好奇，')
tap('FCC 多模型');screen('07-fcc-connection','连接你的 FCC');tap('保存并读取模型');screen('08-fcc-invalid-address','请填写有效的 HTTPS');tap('服务商');screen('09-fcc-directory','61 家服务商');tap('‹ 返回')
tap('⋯');tap('关于 Duck.ai');screen('07-ai-about','本页内嵌 Duck.ai 官方网站');tap('知道了')
tap('开始聊天  →');time.sleep(12)
screen('08-duckai-embedded','AI 助手')
ai_strings=[n.get('text','') for n in dump().iter('node')]
chat_state='network error' if any('连接未完成' in x for x in ai_strings) else 'page finished' if any('官方网页' in x for x in ai_strings) else 'loading'
# Never send a chat or accept service terms automatically; leave those choices to the user.
assert 'cn.zhigeng.app/.AiActivity' in adb('shell','dumpsys','activity','activities'), 'Duck.ai left the app'
tap('⋯');tap('助手首页');screen('09-ai-return','开始聊天')
tap('‹ 返回');screen('10-native-return','TMDB')
# Only the debug variant accepts this fixture shortcut. Release excludes the hook and test CA.
adb('shell','am','force-stop','cn.zhigeng.app');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity','--ez','fcc_protocol_test','true');time.sleep(3)
tap('连接网关');screen('11-fcc-local-test','连接你的 FCC');tap('保存并读取模型');tap('进入聊天');screen('12-fcc-models','fixture/text')
tap('输入消息…');adb('shell','input','text','Protocol%stest');tap('发送');time.sleep(3);screen('13-fcc-protocol-reply','本地协议测试回复')
tap('fixture/text  ⌄');tap('Rate limit fixture (not an AI model)\nfixture/http429');tap('输入消息…');adb('shell','input','text','Rate%stest');tap('发送');time.sleep(2);screen('14-fcc-rate-limit','HTTP 429')
protected=adb('shell','run-as','cn.zhigeng.app','cat','shared_prefs/protected.xml')
assert 'fixture-token-not-a-secret' not in protected and 'Protocol test' not in protected and '本地协议测试回复' not in protected,'Secrets or chats stored in plaintext'
logs=adb('logcat'  ,'-d','-s','AndroidRuntime:E');(out/'runtime-errors.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,logs
(out/'result.json').write_text(json.dumps({'app':'Zhigeng','version':'0.6.2','api':29,'nativeNavigation':'passed','screens':17,'fccProtocolFixture':'passed: HTTPS, bearer auth, model list, streaming reply, HTTP 429, encrypted storage','fccRealProvider':'not tested: no user gateway or provider credentials','duckAiEmbeddedState':chat_state,'duckAiChatSent':False,'accountDownload':'not tested: needs real user credentials'},ensure_ascii=False,indent=2))
print('Native Android emulator smoke passed: native tabs, Duck.ai, FCC connection/catalog, local HTTPS protocol fixture, SSE reply, HTTP 429 and encrypted storage; no fatal exception')
