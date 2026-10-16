/* 知更 · 自主实现的书架与本地阅读器。只管理用户选择的文件。 */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const SHELF_KEY = 'zhigeng_library_shelf_v1';
  const FILTER_KEY = 'zhigeng_library_filters_v1';
  const HISTORY_KEY = 'zhigeng_library_searches_v1';
  const DB_NAME = 'zhigeng_own_book_files_v1';
  const TYPES = ['epub', 'pdf', 'txt'];
  const MAX_FILE_SIZE = 60 * 1024 * 1024;
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const jsonRead = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key) || 'null') ?? fallback; } catch { return fallback; } };
  const jsonWrite = (key, value) => { try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch { return false; } };
  const cleanYear = value => { const n=Number(value); return Number.isInteger(n)&&n>=1400&&n<=2100?n:0; };
  const state = {selected:null,readerId:null,mode:null,blobUrl:null,epubBook:null,rendition:null,shelfFilter:'all',fontSize:17,detailRequest:0,loading:false};
  const filterDefaults = {language:'',from:0,to:0,availability:'',exact:false};

  function shelf() {
    const value=jsonRead(SHELF_KEY,{});
    return value && typeof value==='object' && !Array.isArray(value) ? value : {};
  }
  function filters() {
    const v=jsonRead(FILTER_KEY,filterDefaults);
    const languages=['chi','eng','jpn','fre','ger','spa'];
    return {language:languages.includes(v.language)?v.language:'',from:cleanYear(v.from),to:cleanYear(v.to),availability:['public','borrowable'].includes(v.availability)?v.availability:'',exact:v.exact===true};
  }
  function resetFilters(){
    jsonWrite(FILTER_KEY,filterDefaults);
    if(document.getElementById('bookLanguage'))populateFilters();
  }
  window.ZhigengBookLibrary = {filters,resetFilters};
  function storeShelf(value) {
    if (!jsonWrite(SHELF_KEY,value)) message('书架保存失败：本机存储空间不足或浏览器禁止保存。');
    updateShelf();
  }
  function message(value){ const el=$('bookStudioMessage'); if(el)el.textContent=value; }
  function cloneBook(item) {
    return {
      id:String(item.id),type:'book',title:String(item.title||'未命名图书'),
      subtype:String(item.subtype||''),year:item.year||null,
      image:String(item.image||''),source:String(item.source||''),url:String(item.url||''),
      summary:String(item.summary||''),isbn:String(item.isbn||''),
      languages:Array.isArray(item.languages)?item.languages.slice(0,12):[],
      editionCount:item.editionCount||0,ebookAccess:item.ebookAccess||'',
      local:!!item.local,format:String(item.format||''),size:item.size||0,lastModified:item.lastModified||0
    };
  }
  function shelfEntry(item,readState) {
    const all=shelf(), prior=all[item.id]||{};
    return {...prior,item:cloneBook(item),status:readState||prior.status||'want',
      addedAt:prior.addedAt||Date.now(),updatedAt:Date.now(),
      percent:Number(prior.percent)||0,location:prior.location||'',bookmarks:prior.bookmarks||[]};
  }
  function setStatus(item,readState) {
    if(!item)return;
    const data=shelf();
    data[item.id]=shelfEntry(item,readState);
    storeShelf(data);
    refreshDetails();
    message('已更新书架状态。');
  }
  function openDb() {
    return new Promise((resolve,reject)=>{
      if(!('indexedDB' in window)) { reject(new Error('当前浏览器不支持本地文件库')); return; }
      const req=indexedDB.open(DB_NAME,1);
      req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains('files'))db.createObjectStore('files',{keyPath:'id'});};
      req.onsuccess=()=>resolve(req.result);
      req.onerror=()=>reject(req.error||new Error('无法打开本地文件库'));
      req.onblocked=()=>reject(new Error('本地文件库被其他页面占用'));
    });
  }
  async function putFile(record) {
    const db=await openDb();
    try { return await new Promise((resolve,reject)=>{
      const tx=db.transaction('files','readwrite');
      tx.objectStore('files').put(record);
      tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error||new Error('本地存储写入失败'));
      tx.onabort=()=>reject(tx.error||new Error('写入中断'));
    }); } finally {db.close();}
  }
  async function getFile(id) {
    const db=await openDb();
    try {return await new Promise((resolve,reject)=>{
      const tx=db.transaction('files','readonly');
      const req=tx.objectStore('files').get(id);
      req.onsuccess=()=>resolve(req.result||null);req.onerror=()=>reject(req.error);
    }); } finally {db.close();}
  }
  async function deleteFile(id) {
    const db=await openDb();
    try {return await new Promise((resolve,reject)=>{
      const tx=db.transaction('files','readwrite');
      tx.objectStore('files').delete(id);
      tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error);
    });}finally{db.close();}
  }
  function randomId(){ return 'local:' + (crypto.randomUUID?crypto.randomUUID():Date.now()+'-'+Math.random().toString(16).slice(2)); }
  async function importFiles(files) {
    if(!files.length)return;
    let imported=0,skipped=0,failed=0;
    for(const file of files) {
      const format=(file.name.split('.').pop()||'').toLowerCase();
      if(!TYPES.includes(format)||!file.size||file.size>MAX_FILE_SIZE){skipped++;continue;}
      const existing=Object.values(shelf()).some(e=>e?.item?.local&&e.item.title===file.name.replace(/\.[^.]+$/,'')&&e.item.size===file.size&&e.item.lastModified===file.lastModified);
      if(existing){skipped++;continue;}
      const id=randomId();
      const item={id,type:'book',title:file.name.replace(/\.[^.]+$/,''),subtype:'个人导入',source:'本地文件',format,local:true,size:file.size,lastModified:file.lastModified,summary:'已保存在当前浏览器的本地图书。',url:''};
      try {
        await putFile({id,blob:file,name:file.name,format,size:file.size});
        const all=shelf(); all[id]=shelfEntry(item,'reading');
        if(!jsonWrite(SHELF_KEY,all)){await deleteFile(id);throw new Error('书架索引保存失败');}
        imported++;
      } catch(error){failed++;console.warn('保存电子书失败',error);}
    }
    $('bookFileInput').value='';
    updateShelf();
    message('导入完成：成功 '+imported+' 本，跳过 '+skipped+' 本，失败 '+failed+' 本。大文件受设备存储配额限制。');
  }
  function statusLabel(status){return ({want:'想读',reading:'在读',finished:'已读'})[status]||'想读';}
  function formatSize(bytes){return bytes>1024*1024?(bytes/1024/1024).toFixed(1)+' MB':Math.ceil(bytes/1024)+' KB';}
  function updateShelf() {
    const entries=Object.values(shelf()).filter(e=>e&&e.item);
    entries.sort((a,b)=>(b.updatedAt||0)-(a.updatedAt||0));
    $('bookShelfCount').textContent=entries.length;
    $('bookShelfTabs').querySelectorAll('[data-shelf-filter]').forEach(b=>b.classList.toggle('active',b.dataset.shelfFilter===state.shelfFilter));
    const visible=entries.filter(e=>state.shelfFilter==='all'||(state.shelfFilter==='local'?e.item.local:e.status===state.shelfFilter));
    const grid=$('bookShelfList');grid.replaceChildren();
    if(!visible.length) {
      const note=document.createElement('p');note.className='book-shelf-empty';
      note.textContent=entries.length?'此分类还没有书。':'书架还空着。浏览书籍添加到书架，或导入自己的 EPUB / PDF / TXT 开始阅读。';
      grid.append(note);
      return;
    }
    for(const entry of visible.slice(0,150)){
      const item=entry.item;
      const row=document.createElement('article');row.className='book-shelf-item';
      const thumb=document.createElement('div');thumb.className='book-shelf-cover';
      if(item.image && /^https:\/\//.test(item.image)){
        const image=document.createElement('img'); image.loading='lazy'; image.src=item.image;image.alt='';image.onerror=()=>image.remove();thumb.append(image);
      }else{thumb.textContent='▥';}
      const info=document.createElement('div');info.className='book-shelf-info';
      const title=document.createElement('strong');title.textContent=item.title;title.title=item.title;
      const meta=document.createElement('small');meta.textContent=[item.subtype||item.source,statusLabel(entry.status),item.local?item.format.toUpperCase()+' · '+formatSize(item.size):''].filter(Boolean).join(' · ');
      const progress=document.createElement('div');progress.className='book-shelf-progress';
      progress.textContent=item.local?'已读 '+Math.round(entry.percent||0)+'%':(entry.status==='finished'?'✓ 已完成':'书籍资料与阅读计划');
      info.append(title,meta,progress);
      const actions=document.createElement('div');actions.className='book-shelf-actions';
      const detail=document.createElement('button');detail.type='button';detail.textContent='详情';detail.onclick=()=>openDetails(item);
      actions.append(detail);
      if(item.local){
        const read=document.createElement('button');read.type='button';read.className='book-shelf-read';read.textContent='继续阅读';read.onclick=()=>openReader(item.id);actions.append(read);
      }
      row.append(thumb,info,actions);grid.append(row);
    }
  }
  function statusRefresh() {
    const item=state.selected;if(!item)return;
    const selected=shelf()[item.id]?.status||'';
    $('bookDetailsModal').querySelectorAll('[data-set-read-status]').forEach(b=>b.classList.toggle('book-status-selected',b.dataset.setReadStatus===selected));
    $('bookRemoveButton').hidden=!selected;
    $('bookDetailsRead').hidden=!item.local;
  }
  function refreshDetails(){statusRefresh();}
  function openDetails(item) {
    if(!item)return;
    state.selected=cloneBook(item);
    const details=$('bookDetailsModal');
    $('bookDetailsTitle').textContent=item.title||'未命名图书';
    $('bookDetailsMeta').textContent=[item.subtype,item.year,item.source,item.format?.toUpperCase()].filter(Boolean).join(' · ');
    $('bookDetailsSummary').textContent=item.summary||'暂无详细介绍，可以先收藏，稍后再读。';
    $('bookDetailsExtra').textContent=[item.isbn?'ISBN '+item.isbn:'',
      item.editionCount?'收录版本 '+item.editionCount:'',
      item.ebookAccess==='public'?'开放在线阅读':item.ebookAccess==='borrowable'?'馆藏提供借阅':''].filter(Boolean).join(' · ');
    const cover=$('bookDetailsCover');cover.hidden=!/^https:\/\//.test(item.image||'');
    if(!cover.hidden)cover.src=item.image;else cover.removeAttribute('src');
    statusRefresh();
    details.hidden=false;
    $('bookDetailsClose').focus();
    if(item.url && /^https:\/\/openlibrary.org\/works\/OL[0-9]+W\/?$/.test(item.url)){
      const request=++state.detailRequest;
      const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),5500);
      fetch(item.url+'.json',{signal:controller.signal}).then(r=>{if(!r.ok)throw Error('无法获取简介');return r.json();})
        .then(data=>{
          if(state.detailRequest!==request||state.selected?.id!==item.id)return;
          const desc=typeof data.description==='string'?data.description:data.description?.value;
          if(desc)$('bookDetailsSummary').textContent=String(desc).slice(0,2600);
        }).catch(()=>{}).finally(()=>clearTimeout(timeout));
    }
  }
  function closeDetails(){state.detailRequest++;$('bookDetailsModal').hidden=true;state.selected=null;}
  function openResourcesForSelected(){
    const item=state.selected; if(!item)return;
    closeDetails();
    const button=document.createElement('button');
    button.type='button';button.dataset.action='resources';button.dataset.bookTitle=item.title;
    button.dataset.bookAuthor=item.subtype||'';button.dataset.bookSrc=item.url||'';
    document.body.append(button);button.click();button.remove();
  }
  async function removeSelected(){
    const item=state.selected;if(!item)return;
    if(item.local){try{await deleteFile(item.id);}catch(error){message('本地文件删除失败：'+error.message);return;}}
    const data=shelf();delete data[item.id];storeShelf(data);closeDetails();message('已从阅读书架中移除。');
  }
  function getHistory(){const v=jsonRead(HISTORY_KEY,[]);return Array.isArray(v)?v.slice(0,12):[];}
  function saveSearch(query){
    const str=String(query||'').trim().slice(0,120);
    if(!str)return;
    jsonWrite(HISTORY_KEY,[str,...getHistory().filter(s=>s!==str)].slice(0,8));
    renderHistory();
  }
  function renderHistory() {
    const terms=getHistory(), parent=$('bookHistory'),list=$('bookHistoryChips');
    parent.hidden=!terms.length;list.replaceChildren();
    terms.forEach(term=>{
      const chip=document.createElement('button');chip.type='button';chip.textContent=term;
      chip.onclick=()=>{
        const search=document.getElementById('searchInput');search.value=term;
        search.dispatchEvent(new Event('input',{bubbles:true}));
        window.ZhigengBookAPI?.showBooks?.();
      };
      list.append(chip);
    });
  }
  function populateFilters(){
    const f=filters();
    $('bookLanguage').value=f.language;$('bookYearFrom').value=f.from||'';
    $('bookYearTo').value=f.to||'';$('bookAvailability').value=f.availability;
    $('bookExact').checked=f.exact;
  }
  function applyFilters(){
    const from=cleanYear($('bookYearFrom').value),to=cleanYear($('bookYearTo').value);
    if(from&&to&&from>to){message('起始年份不能晚于结束年份。');return;}
    const f={language:$('bookLanguage').value,from,to,availability:$('bookAvailability').value,exact:$('bookExact').checked};
    if(!jsonWrite(FILTER_KEY,f)){message('无法保存搜索条件。');return;}
    if(from||to)window.ZhigengBookAPI?.chooseYearAll?.();
    window.ZhigengBookAPI?.reload?.();
    message('已应用筛选条件。语言与可读状态由 Open Library 提供；备用数据源可能不支持相同条件。');
  }
  function syncStudio(){
    const selected=document.querySelector('#categoryTabs [data-category="book"].active');
    $('bookStudio').hidden=!selected;
    if(selected)updateShelf();
  }
  function progressSave(update){
    if(!state.readerId)return;
    const entries=shelf(),value=entries[state.readerId];if(!value)return;
    Object.assign(value,update,{updatedAt:Date.now()});
    jsonWrite(SHELF_KEY,entries);
    $('bookReaderProgressLabel').textContent='已读 '+Math.round(value.percent||0)+'%';
    if(state.mode==='epub')$('bookReaderProgress').value=Math.round(value.percent||0);
  }
  function destroyReader(){
    if(state.rendition){try{state.rendition.destroy();}catch{}state.rendition=null;}
    if(state.epubBook){try{state.epubBook.destroy();}catch{}state.epubBook=null;}
    if(state.blobUrl){URL.revokeObjectURL(state.blobUrl);state.blobUrl=null;}
    $('bookReaderPdf').removeAttribute('src');
    $('bookReaderEpub').replaceChildren();
    $('bookReaderText').replaceChildren();
    for(const id of ['bookReaderText','bookReaderPdf','bookReaderEpub'])$(id).hidden=true;
    state.mode=null;
  }
  function closeReader(){
    const id=state.readerId;
    state.readerId=null;
    destroyReader();
    $('bookReader').hidden=true;
    document.body.classList.remove('book-reader-is-open');
    if(id)updateShelf();
  }
  function setReaderStatus(text){$('bookReaderStatus').textContent=text||'';}
  function setMode(mode){
    state.mode=mode;
    $('bookReaderText').hidden=mode!=='txt';
    $('bookReaderPdf').hidden=mode!=='pdf';
    $('bookReaderEpub').hidden=mode!=='epub';
    const paged=mode==='epub';
    $('bookReaderPrev').hidden=!paged;$('bookReaderNext').hidden=!paged;
    $('bookReaderFontDown').hidden=mode==='pdf';$('bookReaderFontUp').hidden=mode==='pdf';
    $('bookReaderProgress').disabled=paged;
  }
  function textProgress(){
    const el=$('bookReaderText');
    const total=Math.max(1,el.scrollHeight-el.clientHeight);
    const pct=el.scrollHeight<=el.clientHeight?100:Math.min(100,Math.round(el.scrollTop/total*100));
    progressSave({percent:pct,location:el.scrollTop});
    $('bookReaderProgress').value=pct;
  }
  async function addScript(url, globalName){
    if(window[globalName])return;
    await new Promise((resolve,reject)=>{
      const node=document.createElement('script');node.src=url;node.async=true;
      node.onload=resolve;node.onerror=()=>reject(new Error('阅读组件加载失败，请检查网络'));
      document.head.append(node);
    });
    if(!window[globalName])throw new Error('阅读组件未正确加载');
  }
  async function openReader(id){
    const entry=shelf()[id];
    if(!entry?.item?.local)return;
    closeDetails();
    closeReader();
    state.readerId=id;
    $('bookReader').hidden=false;document.body.classList.add('book-reader-is-open');
    $('bookReaderTitle').textContent=entry.item.title;
    $('bookReaderProgress').value=Math.round(entry.percent||0);
    $('bookReaderProgressLabel').textContent='已读 '+Math.round(entry.percent||0)+'%';
    renderBookmarks();
    setReaderStatus('读取本地图书…');
    setStatus(entry.item,'reading');
    try{
      const row=await getFile(id);
      if(state.readerId!==id)return;
      if(!row?.blob)throw new Error('本地文件未找到，可能已经被浏览器清理。请重新导入。');
      const format=row.format;
      if(format==='pdf'){
        setMode('pdf');
        state.blobUrl=URL.createObjectURL(row.blob);
        $('bookReaderPdf').src=state.blobUrl;
        const link=$('bookReaderExternal');link.href=state.blobUrl;link.hidden=false;
        setReaderStatus('PDF 使用浏览器原生查看器。可在底部手动记录阅读进度。');
      }else if(format==='txt'){
        setMode('txt');$('bookReaderExternal').hidden=true;
        const bytes=await row.blob.arrayBuffer();
        let text=new TextDecoder('utf-8').decode(bytes);
        if((text.match(/�/g)||[]).length>Math.max(3,text.length/500)){
          try{const alternate=new TextDecoder('gb18030').decode(bytes);if((alternate.match(/�/g)||[]).length<(text.match(/�/g)||[]).length)text=alternate;}catch{}
        }
        const node=$('bookReaderText');node.textContent=text;
        node.style.fontSize=state.fontSize+'px';
        requestAnimationFrame(()=>{if(state.readerId===id){node.scrollTop=Number(shelf()[id]?.location)||0;textProgress();}});
        setReaderStatus('');
      }else if(format==='epub'){
        setMode('epub');$('bookReaderExternal').hidden=true;
        setReaderStatus('初始化 EPUB 阅读器…');
        await addScript('https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js','JSZip');
        await addScript('https://cdn.jsdelivr.net/npm/epubjs@0.3.93/dist/epub.min.js','ePub');
        if(state.readerId!==id)return;
        const bytes=await row.blob.arrayBuffer();
        const book=window.ePub(bytes);state.epubBook=book;
        await book.ready;
        if(state.readerId!==id)return;
        const rendition=book.renderTo('bookReaderEpub',{width:'100%',height:'100%',flow:'paginated',spread:'none'});
        state.rendition=rendition;
        rendition.themes.fontSize(state.fontSize+'px');
        rendition.on('relocated',location=>{
          if(state.readerId!==id)return;
          const cfi=location?.start?.cfi||'';
          let percent=location?.start?.percentage;
          if(!Number.isFinite(percent)&&book.locations?.length?.()) {
            try{percent=book.locations.percentageFromCfi(cfi);}catch{}
          }
          const updated={location:cfi};
          if(Number.isFinite(percent))updated.percent=Math.max(0,Math.min(100,Math.round(percent*100)));
          progressSave(updated);
        });
        await rendition.display(typeof entry.location==='string'&&entry.location.startsWith('epubcfi(')?entry.location:undefined);
        setReaderStatus('');
        book.locations.generate(1300).then(()=>{
          if(state.readerId!==id)return;
          const cfi=rendition.currentLocation()?.start?.cfi;
          if(cfi){const p=book.locations.percentageFromCfi(cfi);if(Number.isFinite(p))progressSave({percent:Math.round(p*100)});}
        }).catch(()=>{});
      }else throw new Error('暂不支持这种文件格式。');
    }catch(error){if(state.readerId===id)setReaderStatus('无法阅读：'+(error?.message||'未知错误')+'。可以导入 PDF 或 TXT 文件重试。');}
  }
  function changeFont(delta){
    state.fontSize=Math.max(12,Math.min(30,state.fontSize+delta));
    if(state.mode==='txt')$('bookReaderText').style.fontSize=state.fontSize+'px';
    if(state.mode==='epub')state.rendition?.themes.fontSize(state.fontSize+'px');
  }
  function renderBookmarks(){
    const select=$('bookReaderBookmarks');
    select.replaceChildren();
    const first=document.createElement('option');first.value='';first.textContent='书签';select.append(first);
    const entry=shelf()[state.readerId],marks=entry?.bookmarks||[];
    marks.forEach((mark,index)=>{
      const option=document.createElement('option');option.value=String(index);
      option.textContent='书签 '+(index+1)+' · '+(mark.percent||0)+'%';select.append(option);
    });
    select.hidden=!marks.length;
  }
  function jumpToBookmark(index){
    const entry=shelf()[state.readerId];
    const mark=entry?.bookmarks?.[index];if(!mark)return;
    if(state.mode==='epub'&&typeof mark.location==='string'&&mark.location.startsWith('epubcfi(')){
      state.rendition?.display(mark.location).catch(()=>setReaderStatus('无法定位这个 EPUB 书签。'));
    }else if(state.mode==='txt'){
      const body=$('bookReaderText');
      body.scrollTop=typeof mark.location==='number'?mark.location:(body.scrollHeight-body.clientHeight)*(mark.percent||0)/100;
      textProgress();
    }else if(state.mode==='pdf'){
      progressSave({percent:mark.percent||0});
      $('bookReaderProgress').value=mark.percent||0;
      setReaderStatus('已恢复记录的 PDF 阅读进度。PDF 的实际页面需在浏览器内手动定位。');
    }
  }
  function createBookmark(){
    if(!state.readerId)return;
    const data=shelf(),entry=data[state.readerId];if(!entry)return;
    const mark={location:entry.location||0,percent:Math.round(entry.percent||0),at:Date.now()};
    const collection=Array.isArray(entry.bookmarks)?entry.bookmarks:[];
    entry.bookmarks=[mark,...collection].slice(0,30);
    storeShelf(data);
    renderBookmarks();
    setReaderStatus('已保存书签 · '+mark.percent+'%。再次打开会自动恢复最近阅读位置。');
  }
  function init(){
    populateFilters();renderHistory();updateShelf();syncStudio();
    const searchForm=$('bookSearchForm'),bookInput=$('bookSearchInput');
    searchForm.addEventListener('submit',event=>{
      event.preventDefault();
      const term=bookInput.value.trim();
      if(!term){message('请输入要查询的书名或作者。');bookInput.focus();return;}
      window.ZhigengBookAPI?.searchBooks?.(term);
      saveSearch(term);
      $('bookSearchHint').textContent='正在查找「'+term.slice(0,80)+'」，默认跨所有年份搜索…';
    });
    $('bookImportButton').addEventListener('click',()=>$('bookFileInput').click());
    $('bookFileInput').addEventListener('change',e=>importFiles([...e.target.files]));
    $('bookFilterApply').addEventListener('click',applyFilters);
    $('bookFilterClear').addEventListener('click',()=>{
      for(const id of ['bookLanguage','bookYearFrom','bookYearTo','bookAvailability'])$(id).value='';
      $('bookExact').checked=false;applyFilters();
    });
    $('bookShelfTabs').addEventListener('click',e=>{
      const tab=e.target.closest('[data-shelf-filter]');if(!tab)return;
      state.shelfFilter=tab.dataset.shelfFilter;updateShelf();
    });
    $('bookHistoryClear').addEventListener('click',()=>{jsonWrite(HISTORY_KEY,[]);renderHistory();});
    $('searchInput').addEventListener('keydown',e=>{
      if(e.key==='Enter'&&!$('bookStudio').hidden)saveSearch(e.target.value);
    });
    $('searchInput').addEventListener('change',e=>{
      if(!$('bookStudio').hidden)saveSearch(e.target.value);
    });
    document.addEventListener('click',e=>{
      // Bookmark button inside a cover belongs to the existing favorites handler.
      if(e.target.closest('[data-action="save"]'))return;
      const detail=e.target.closest('[data-action="book-details"]');
      if(detail){
        e.preventDefault();e.stopPropagation();
        const id=detail.getAttribute('data-book-id');
        const item=window.ZhigengBookAPI?.getItem?.(id)||shelf()[id]?.item;
        if(item)openDetails(item);
        return;
      }
      if(e.target.closest('[data-category], [data-page], [data-mobile]'))queueMicrotask(syncStudio);
    },true);
    $('bookDetailsModal').addEventListener('click',e=>{if(e.target===$('bookDetailsModal'))closeDetails();});
    $('bookDetailsClose').addEventListener('click',closeDetails);
    $('bookDetailsModal').querySelectorAll('[data-set-read-status]').forEach(btn=>btn.addEventListener('click',()=>setStatus(state.selected,btn.dataset.setReadStatus)));
    $('bookRemoveButton').addEventListener('click',removeSelected);
    $('bookDetailsRead').addEventListener('click',()=>{if(state.selected?.local)openReader(state.selected.id);});
    $('bookDetailsResource').addEventListener('click',openResourcesForSelected);
    $('bookReaderClose').addEventListener('click',closeReader);
    $('bookReaderPrev').addEventListener('click',()=>state.rendition?.prev());
    $('bookReaderNext').addEventListener('click',()=>state.rendition?.next());
    $('bookReaderFontDown').addEventListener('click',()=>changeFont(-2));
    $('bookReaderFontUp').addEventListener('click',()=>changeFont(2));
    $('bookReaderBookmark').addEventListener('click',createBookmark);
    $('bookReaderBookmarks').addEventListener('change',e=>{if(e.target.value!=='')jumpToBookmark(Number(e.target.value));e.target.value='';});
    $('bookReaderFinish').addEventListener('click',()=>{const entry=shelf()[state.readerId];if(entry)setStatus(entry.item,'finished');setReaderStatus('已标为读完。');});
    $('bookReaderProgress').addEventListener('input',e=>{
      const p=Number(e.target.value);
      if(state.mode==='txt'){
        const el=$('bookReaderText');el.scrollTop=(el.scrollHeight-el.clientHeight)*p/100;
        textProgress();
      }else if(state.mode==='pdf')progressSave({percent:p});
    });
    $('bookReaderText').addEventListener('scroll',()=>{
      if(state.mode!=='txt'||!state.readerId)return;
      clearTimeout(state.loading);state.loading=setTimeout(textProgress,180);
    },{passive:true});
    document.addEventListener('keydown',e=>{
      if(e.key==='Escape'){if(state.readerId)closeReader();else if(!$('bookDetailsModal').hidden)closeDetails();}
      if(state.mode==='epub'&&e.key==='ArrowRight')state.rendition?.next();
      if(state.mode==='epub'&&e.key==='ArrowLeft')state.rendition?.prev();
    });
    window.addEventListener('storage',e=>{if(e.key===SHELF_KEY)updateShelf();});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);
  else init();
})();