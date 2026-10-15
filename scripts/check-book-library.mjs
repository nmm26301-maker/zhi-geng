import assert from 'node:assert/strict';
import fs from 'node:fs';

const html=fs.readFileSync('index.html','utf8');
const lib=fs.readFileSync('assets/book-library.js','utf8');
const app=fs.readFileSync('assets/app.js','utf8');
const css=fs.readFileSync('assets/styles.css','utf8');

const ids=new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]));
const required=[
  'bookStudio','bookLanguage','bookYearFrom','bookYearTo','bookAvailability','bookExact',
  'bookFilterApply','bookFilterClear','bookImportButton','bookFileInput','bookShelfCount',
  'bookShelfList','bookShelfTabs','bookHistory','bookHistoryChips','bookHistoryClear',
  'bookStudioMessage','bookDetailsModal','bookDetailsTitle','bookDetailsMeta',
  'bookDetailsSummary','bookDetailsExtra','bookDetailsCover','bookDetailsClose',
  'bookWantButton','bookReadingButton','bookFinishedButton','bookRemoveButton',
  'bookDetailsRead','bookDetailsResource','bookReader','bookReaderClose',
  'bookReaderTitle','bookReaderPrev','bookReaderNext','bookReaderFontUp','bookReaderFontDown',
  'bookReaderBookmark','bookReaderBookmarks','bookReaderContent','bookReaderStatus',
  'bookReaderText','bookReaderPdf','bookReaderEpub','bookReaderProgressLabel',
  'bookReaderProgress','bookReaderFinish','bookReaderExternal'
];
const groupControls=new Set(['bookWantButton','bookReadingButton','bookFinishedButton','bookReaderContent']);
for(const id of required){
  assert(ids.has(id),'missing book feature element: '+id);
  if(!groupControls.has(id))assert(lib.includes(id),'book feature not connected: '+id);
}
assert(lib.includes("querySelectorAll('[data-set-read-status]')"),'status button group not wired');
assert(html.includes('<script src="assets/book-library.js" defer></script>'));
assert(app.includes('window.ZhigengBookAPI'),'missing module interop');
assert(app.includes('window.ZhigengBookLibrary?.filters?.()'),'missing filter integration');
assert(app.includes('ebook_access'),'missing online read filter');
assert(app.includes('data-action="book-details"'),'missing book card detail action');
assert(lib.includes("indexedDB.open(DB_NAME,1)"),'missing local file persistence');
assert(lib.includes('URL.createObjectURL'),'missing local PDF file reading');
assert(lib.includes('epubjs@0.3.93'),'missing EPUB reader');
assert(lib.includes('new TextDecoder'),'missing TXT reader');
assert(lib.includes('renderBookmarks()'),'missing bookmark controls');
assert(lib.includes('bookmarks=[mark,...collection].slice(0,30)'),'bookmarks should be bounded');
assert(lib.includes('MAX_FILE_SIZE = 60 * 1024 * 1024'),'no import file size limit');
assert(css.includes('@media(max-width:700px)'),'missing mobile layout');
assert(!lib.includes('/eapi/book/'),'do not copy private APK endpoints');
console.log('Book library and reader wiring checks passed ('+required.length+' UI controls).');
