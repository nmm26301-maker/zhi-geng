"""Original, non-copyrighted local QA documents. Debug assets only."""
import pathlib,zipfile
out=pathlib.Path('android/app/src/debug/assets');out.mkdir(parents=True,exist_ok=True)
def doc(title,body):return f'<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN" xml:lang="zh-CN"><head><title>{title}</title><style>p{{text-indent:2em;margin-bottom:.8em}}h1{{font-weight:normal}}img{{max-width:100%}}</style></head><body><h1>{title}</h1>{body}</body></html>'
with zipfile.ZipFile(out/'reader-fixture.epub','w') as z:
 z.writestr('mimetype','application/epub+zip',compress_type=zipfile.ZIP_STORED)
 z.writestr('META-INF/container.xml','<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
 manifest='<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="art" href="garden.svg" media-type="image/svg+xml"/>';spine='';nav=''
 names=['第一章 春日来信','第二章 山间晚风','第三章 月光书页']
 for i,name in enumerate(names):
  manifest+=f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>';spine+=f'<itemref idref="c{i}"/>';nav+=f'<li><a href="c{i}.xhtml">{name}</a></li>'
  intro='<p>这是一份为知更编写的本地排版测试文档。窗外的光落在书页上，阅读不必着急。</p>'
  if i==0:intro+='<img src="garden.svg" alt="测试插图：绿色山丘与金色太阳"/><p><a href="c1.xhtml">前往第二章</a> · 图片与链接应保留。</p>'
  body=''.join(f'<p>第{n+1}段。山间的风缓缓穿过树梢，远处的小路安静而清晰。我们在留白里停一停，让每一句话都有自己的节奏。这些文字仅用于检查中文排版、翻页、目录与阅读进度。</p>' for n in range(32))
  z.writestr(f'OEBPS/c{i}.xhtml',doc(name,intro+body))
 z.writestr('OEBPS/garden.svg','<svg xmlns="http://www.w3.org/2000/svg" width="600" height="200" viewBox="0 0 600 200"><rect width="600" height="200" fill="#e8ecdf"/><circle cx="465" cy="60" r="28" fill="#b9a478"/><path d="M0 180 Q130 30 300 150 Q430 60 600 180V200H0Z" fill="#78917a"/><path d="M0 200Q200 100 400 180T600 190V200Z" fill="#244e40"/></svg>')
 z.writestr('OEBPS/nav.xhtml',doc('目录',f'<nav xmlns:epub="http://www.idpf.org/2007/ops" epub:type="toc"><ol>{nav}</ol></nav>'))
 z.writestr('OEBPS/book.opf',f'<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:zhigeng:reader-qa-v1</dc:identifier><dc:title>春日来信 · 排版测试</dc:title><dc:language>zh-CN</dc:language><meta property="dcterms:modified">2026-10-17T00:00:00Z</meta></metadata><manifest>{manifest}</manifest><spine>{spine}</spine></package>')
text='第一章 春日来信\n'+('\n窗外的光落在书页上，阅读不必着急。留白让文字更舒展，行距让视线从容前行。\n'*60)+'第二章 山间晚风\n'+('\n这是一份本地 TXT 排版测试，检查自动目录与进度恢复。\n'*60)
(out/'reader-fixture.txt').write_text(text,encoding='utf-8');(out/'reader-fixture-gb.txt').write_bytes(text.encode('gb18030'))
# A two-page vector PDF for the built-in PDF renderer.
objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R >>']
for i in range(2):
 data=f'BT /F1 22 Tf 40 520 Td (Zhigeng PDF reading test - page {i+1}) Tj ET'.encode();objects.append(b'<< /Length '+str(len(data)).encode()+b' >>\nstream\n'+data+b'\nendstream')
 if i==0:objects.append(b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>')
objects.append(b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');pdf=b'%PDF-1.4\n';offsets=[0]
for i,obj in enumerate(objects,1):offsets.append(len(pdf));pdf+=str(i).encode()+b' 0 obj\n'+obj+b'\nendobj\n'
xref=len(pdf);pdf+=b'xref\n0 8\n0000000000 65535 f \n'+b''.join(f'{n:010d} 00000 n \n'.encode() for n in offsets[1:])+f'trailer\n<< /Size 8 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode();(out/'reader-fixture.pdf').write_bytes(pdf)
print('Debug reader fixtures prepared')
