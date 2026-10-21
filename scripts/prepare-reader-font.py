"""Build the offline reading font from pinned, OFL-licensed Noto Serif SC source."""
import hashlib,pathlib,urllib.request
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset
root=pathlib.Path(__file__).resolve().parent.parent
url='https://raw.githubusercontent.com/google/fonts/47d54ca1ebb14fd864695f1d39dc734f39017c33/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf'
raw=urllib.request.urlopen(url,timeout=120).read()
assert hashlib.sha1(b'blob '+str(len(raw)).encode()+bytes([0])+raw).hexdigest()=='eab063faf229160a52d3760f5555150e4eb9e5bf','Font source changed'
p=root/'android/app/build/reader-font-source.ttf';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
font=instantiateVariableFont(TTFont(p),{'wght':400},inplace=True)
options=subset.Options();options.flavor='woff2';options.name_IDs=['*'];options.name_legacy=True;options.name_languages=['*']
s=subset.Subsetter(options=options);s.populate(unicodes=list(range(0x20,0x100))+list(range(0x2000,0x2070))+list(range(0x3000,0x3040))+list(range(0x3400,0xa000))+list(range(0xff00,0xfff0)));s.subset(font)
# Rename the derived subset; do not use an upstream reserved font name.
for record in font['name'].names:
 if record.nameID in (1,4,6,16):record.string=('Zhigeng Reading Serif' if record.nameID!=6 else 'ZhigengReadingSerif').encode(record.getEncoding(),errors='replace')
font.flavor='woff2'
out=root/'android/app/src/main/assets/fonts/zhigeng-serif.woff2';out.parent.mkdir(parents=True,exist_ok=True);font.save(out)
print('Offline Chinese reading font:',out.stat().st_size,'bytes; source git blob verified')

