/* 知更网页版 · 安静阅读布局增强（不干预原有 EPUB/TXT/PDF 解析、书签与存档） */
(() => {
  'use strict';
  function init() {
    const reader=document.getElementById('bookReader');
    if (!reader || reader.querySelector('#zgReaderControls')) return;
    const controls=document.createElement('div');
    controls.id='zgReaderControls';controls.className='zg-reader-controls';
    controls.innerHTML='<button id="zgReaderToggle" type="button" aria-label="显示或隐藏阅读工具栏" aria-expanded="true">☷ 工具</button>' +
      '<button id="zgReaderPrev" type="button" aria-label="上一页">‹</button><button id="zgReaderNext" type="button" aria-label="下一页">›</button>' +
      '<label class="zg-reader-theme-label">阅读底色 <select id="zgReaderTheme" aria-label="阅读底色"><option value="paper">纸白</option><option value="warm">米色</option><option value="night">夜间</option></select></label>';
    reader.append(controls);
    const toggle=reader.querySelector('#zgReaderToggle');
    const choose=reader.querySelector('#zgReaderTheme');
    const setQuiet=quiet=>{
      reader.classList.toggle('zg-reader-quiet',quiet);
      toggle.textContent=quiet?'☷ 工具':'✧ 沉浸';
      toggle.setAttribute('aria-expanded',String(!quiet));
    };
    const setTheme=mode=>{
      reader.classList.remove('zg-reader-paper','zg-reader-warm','zg-reader-night');
      reader.classList.add('zg-reader-'+mode);
      choose.value=mode;
      try {localStorage.setItem('zhigeng_web_reader_theme',mode);}catch{}
    };
    const selected=(()=>{try {return localStorage.getItem('zhigeng_web_reader_theme')||'warm';}catch{return 'warm';}})();
    setTheme(['paper','warm','night'].includes(selected)?selected:'warm');
    toggle.addEventListener('click',()=>setQuiet(!reader.classList.contains('zg-reader-quiet')));
    choose.addEventListener('change',()=>setTheme(choose.value));
    reader.querySelector('#zgReaderPrev').addEventListener('click',()=>reader.querySelector('#bookReaderPrev')?.click());
    reader.querySelector('#zgReaderNext').addEventListener('click',()=>reader.querySelector('#bookReaderNext')?.click());
    reader.querySelector('#bookReaderContent').addEventListener('click',event=>{
      if(event.target.closest('input,button,select,a'))return;
      if(window.getSelection()?.toString())return;
      setQuiet(!reader.classList.contains('zg-reader-quiet'));
    });
    let previousHidden=reader.hidden;
    new MutationObserver(()=>{
      if(previousHidden && !reader.hidden) setQuiet(true);
      previousHidden=reader.hidden;
    }).observe(reader,{attributes:true,attributeFilter:['hidden']});
    reader.addEventListener('keydown',event=>{
      if(event.key==='Escape'&&reader.classList.contains('zg-reader-quiet')){
        setQuiet(false);
      }
    });
    setQuiet(false);
  }
  if (document.readyState==='loading') document.addEventListener('DOMContentLoaded',init);
  else init();
})();
