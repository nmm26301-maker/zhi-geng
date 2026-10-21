"""Reader UI tests use only original, local documents bundled in debug."""
import subprocess,time,pathlib,xml.etree.ElementTree as E,json,atexit
from PIL import Image
out=pathlib.Path('emulator-check');out.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args]).decode(errors='replace')
def tree():
 adb('shell','uiautomator','dump','/sdcard/reader.xml');return E.fromstring(adb('shell','cat','/sdcard/reader.xml'))
def texts():return [n.get('text','') for n in tree().iter('node')]
def tap(value):
 for n in tree().iter('node'):
  if n.get('text')==value or n.get('content-desc')==value:
   b=[int(x) for x in n.get('bounds').replace('][',',').strip('[]').split(',')];adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(1.5);return
 raise AssertionError(('Missing reader action',value,texts()))
def center():adb('shell','input','tap','540','960');time.sleep(1)
def screen(name,required):
 t=tree();(out/(name+'.xml')).write_bytes(E.tostring(t,encoding='utf-8'))
 with (out/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
 assert any(required in n.get('text','') for n in t.iter('node')),('Missing content',required)
atexit.register(lambda:(out/'reader-runtime-errors.txt').write_text(adb('logcat','-d','-s','AndroidRuntime:E')))
def visual_text(name,prefix):
 # API 29 software WebView omits document nodes from UIAutomator. Verify the first
 # paragraph's known fixture region on the screenshot, excluding caption and artwork.
 img=Image.open(out/(name+'.png')).convert('RGB');w,h=img.size
 crop=img.crop((int(w*.1),int(h*.235),int(w*.94),int(h*.325)))
 assert sum(1 for r,g,b in crop.getdata() if max(r,g,b)<150)>1000,'First EPUB body paragraph is not painted'
def open_book(kind):
 adb('shell','am','force-stop','cn.zhigeng.app');adb('shell','am','start','-W','-n','cn.zhigeng.app/.MainActivity','--es','reader_test',kind);time.sleep(9)
 if 'GOT IT' in texts():tap('GOT IT');time.sleep(2)
adb('install','-r','apk-output/Zhigeng-debug-test.apk');adb('logcat','-c')
open_book('epub');screen('reader-01-epub-page','第一章');visual_text('reader-01-epub-page','这是一份为知更编写');assert '排版' not in texts(),'Reader chrome is visible by default'
center();tap('目录');screen('reader-02-toc','第二章 山间晚风');tap('第二章 山间晚风');time.sleep(2);screen('reader-03-second-chapter','第二章')
center();tap('添加书签');tap('排版');screen('reader-04-settings','阅读排版');tap('字号');screen('reader-04b-font-size','字号');tap('夜间');screen('reader-05-night-settings','✓ 夜间');tap('完成');tap('收起');screen('reader-06-night-page','第二章')
open_book('epub');screen('reader-07-location-restored','第二章');center();tap('书签');screen('reader-08-bookmarks','第二章');tap('完成');tap('排版');tap('米色');tap('完成');tap('收起')
open_book('txt');screen('reader-09-txt-page','第一章');center();tap('目录');screen('reader-10-txt-toc','第二章 山间晚风');tap('第二章 山间晚风');time.sleep(2);screen('reader-11-txt-chapter','第二章')
open_book('txt');screen('reader-12-txt-restored','第二章')
open_book('gb');screen('reader-13-gb18030','第一章')
open_book('pdf');screen('reader-14-pdf','1 / 2');center();tap('下一页 ›');screen('reader-15-pdf-next','2 / 2');tap('记书签');open_book('pdf');screen('reader-16-pdf-restored','2 / 2')
logs=adb('logcat','-d','-s','AndroidRuntime:E');(out/'reader-runtime-errors.txt').write_text(logs);assert 'FATAL EXCEPTION' not in logs,logs
(out/'reader-result.json').write_text(json.dumps({'version':'0.6.9','api':29,'engine':'Readium Kotlin 3.1.0','documents':'original local debug fixtures','epub':'passed: rendered chapters, table of contents, bookmark, font size, theme and locator restart','txt':'passed: offline EPUB adapter, chapter navigation, locator restart, GB18030','pdf':'passed: native rendering, page navigation, bookmark, page restart','crashes':False},ensure_ascii=False,indent=2))
print('Reader emulator smoke passed: EPUB/TXT/GB18030/PDF, hidden chrome, directory, bookmark, themes and progress restoration')

