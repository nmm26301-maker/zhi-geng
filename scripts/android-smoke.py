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
 assert any(required in x for x in strings),('Missing UI',required,strings)
 (out/(name+'.xml')).write_bytes(E.tostring(tree,encoding='utf-8'))
 with (out/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
adb('install','-r','apk-output/Zhigeng-debug-test.apk');adb('logcat','-c');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity');time.sleep(3)
screen('01-domestic-discovery','国内精选');tap('榜单');screen('02-editorial-rank','知更编辑推荐');tap('阅读');screen('03-chinese-books','公开图书目录');tap('书架');screen('04-library','导入');tap('我的');screen('05-settings','TMDB')
tap('✦ AI 助手');screen('06-ai-assistant','让好奇，')
tap('⋯');tap('关于 Duck.ai');screen('07-ai-about','本页内嵌 Duck.ai 官方网站');tap('知道了')
tap('开始聊天  →');time.sleep(12)
screen('08-duckai-embedded','AI 助手')
ai_strings=[n.get('text','') for n in dump().iter('node')]
chat_state='network error' if any('连接未完成' in x for x in ai_strings) else 'page finished' if any('官方网页' in x for x in ai_strings) else 'loading'
# Never send a chat or accept service terms automatically; leave those choices to the user.
assert 'cn.zhigeng.app/.AiActivity' in adb('shell','dumpsys','activity','activities'), 'Duck.ai left the app'
tap('⋯');tap('助手首页');screen('09-ai-return','开始聊天')
tap('‹ 返回');screen('10-native-return','TMDB')
logs=adb('logcat' ,'-d','-s','AndroidRuntime:E');(out/'runtime-errors.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,logs
(out/'result.json').write_text(json.dumps({'app':'Zhigeng','version':'0.6.1','api':29,'nativeNavigation':'passed','screens':10,'duckAiEmbeddedState':chat_state,'duckAiChatSent':False,'accountDownload':'not tested: needs real user credentials'},ensure_ascii=False,indent=2))
print('Native Android emulator smoke passed: five native tabs, assistant foyer, service info, embedded Duck.ai and return; no fatal exception')
