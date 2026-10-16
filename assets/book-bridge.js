/* 知更 · 书籍资源桥接：仅跳转合法公开来源或用户自选站点，不反编译/分发 APK。 */
(() => {
  'use strict';
  const STORAGE_KEY = 'zhigeng_book_external_url_v1';
  const $ = id => document.getElementById(id);
  let currentBook = null;

  function getExternalUrl() {
    try { return localStorage.getItem(STORAGE_KEY) || ''; } catch { return ''; }
  }
  function validatedTemplate(value) {
    const valueText = String(value || '').trim();
    if (!valueText || valueText.length > 600) return null;
    try {
      const url = new URL(valueText.replaceAll('{query}', 'sample'));
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) return null;
      if (['localhost', '127.0.0.1', '[::1]'].includes(url.hostname.toLowerCase())) return null;
      return valueText;
    } catch { return null; }
  }
  function normalizedBookText(book) {
    return [book.title, book.author].filter(Boolean).join(' ');
  }
  function safeLink(url) {
    try {
      const value = new URL(url);
      return value.protocol === 'https:' ? value.href : '';
    } catch { return ''; }
  }
  function setBookSourceStatus(message) {
    $('bookSourceStatus').textContent = message;
  }
  function syncSourceSettings() {
    const configured = !!validatedTemplate(getExternalUrl());
    $('bookExternalStatus').textContent = configured ? '已设置' : '未设置';
    $('bookExternalStatus').className = configured ? 'status-good' : 'status-pending';
    $('bookHubSource').textContent = 'Z-Library 站内搜索';
    $('bookModalExternal').textContent = configured ? '在自选 Z-Library 入口中查找 ↗' : '配置 Z-Library 入口 ↗';
  }
  function openSettingsForBooks() {
    $('settingsButton').click();
    $('zlibraryUrl').value = getExternalUrl();
    $('zlibraryUrl').focus();
  }
  function bookSearchUrl(book, source) {
    const query = encodeURIComponent(normalizedBookText(book));
    if (source === 'openlibrary') return 'https://openlibrary.org/search?q=' + query;
    if (source === 'google') return 'https://books.google.com/books?q=' + query;
    if (source === 'gutenberg') return 'https://www.gutenberg.org/ebooks/search/?query=' + query;
    return '';
  }
  function openExternal(book) {
    const template = validatedTemplate(getExternalUrl());
    if (!template) { closeResourceDialog(); openSettingsForBooks(); return; }
    const q = encodeURIComponent(normalizedBookText(book));
    const dest = safeLink(template.replaceAll('{query}', q));
    if (!dest) { setBookSourceStatus('访问地址无效，请重新设置 HTTPS 网址。'); openSettingsForBooks(); return; }
    // 未配置搜索地址时只打开网站，不猜测非公开的搜索接口。
    window.open(dest, '_blank', 'noopener,noreferrer');
  }
  function syncBookHub() {
    const active = document.querySelector('#categoryTabs [data-category="book"].active');
    $('bookHub').hidden = !active;
    syncSourceSettings();
  }
  function openResourceDialog(target) {
    currentBook = {
      title: target.getAttribute('data-book-title') || '',
      author: target.getAttribute('data-book-author') || '',
      source: target.getAttribute('data-book-src') || ''
    };
    const name = normalizedBookText(currentBook);
    $('bookResourceTitle').textContent = currentBook.title || '书籍资源';
    $('bookResourceMeta').textContent = currentBook.author || '从公开目录查找阅读与借阅来源';
    $('bookOpenLibrary').href = safeLink(currentBook.source)?.startsWith('https://openlibrary.org/')
      ? safeLink(currentBook.source) : bookSearchUrl(currentBook, 'openlibrary');
    $('bookGoogleBooks').href = bookSearchUrl(currentBook, 'google');
    $('bookGutenberg').href = bookSearchUrl(currentBook, 'gutenberg');
    $('bookCopyTitle').setAttribute('data-copy', name);
    syncSourceSettings();
    $('bookResourceModal').hidden = false;
    $('bookResourceClose').focus();
  }
  function closeResourceDialog() {
    $('bookResourceModal').hidden = true;
  }
  async function copyTitle() {
    if (!currentBook) return;
    const text = normalizedBookText(currentBook);
    try {
      await navigator.clipboard.writeText(text);
      $('bookCopyStatus').textContent = '已复制书名与作者，可粘贴至其他应用搜索。';
    } catch {
      $('bookCopyStatus').textContent = '浏览器不支持自动复制，请手动选中书名。';
    }
  }
  function init() {
    $('zlibraryUrl').value = getExternalUrl();
    $('saveZlibraryUrl').addEventListener('click', () => {
      const text = $('zlibraryUrl').value.trim();
      if (!validatedTemplate(text)) {
        setBookSourceStatus('请输入有效的 HTTPS 地址，可选使用 {query} 代替检索词；不要填写密码或账号令牌。');
        return;
      }
      try {
        localStorage.setItem(STORAGE_KEY, text);
        setBookSourceStatus('已保存在本设备，仅用于打开你自行选择的外部页面。');
        syncSourceSettings();
      } catch { setBookSourceStatus('浏览器阻止本地保存，请检查存储权限。'); }
    });
    $('clearZlibraryUrl').addEventListener('click', () => {
      try { localStorage.removeItem(STORAGE_KEY); } catch {}
      $('zlibraryUrl').value = '';
      setBookSourceStatus('自选入口已清除。');
      syncSourceSettings();
    });
    $('bookHubSource').addEventListener('click', () => {
      $('zlibConnect').click();
    });
    $('bookHubOpenLibrary').addEventListener('click', () => {});
    $('bookModalExternal').addEventListener('click', () => currentBook && openExternal(currentBook));
    $('bookModalSetup').addEventListener('click', () => { closeResourceDialog(); openSettingsForBooks(); });
    $('bookCopyTitle').addEventListener('click', copyTitle);
    $('bookResourceClose').addEventListener('click', closeResourceDialog);
    $('bookResourceModal').addEventListener('click', e => { if (e.target === $('bookResourceModal')) closeResourceDialog(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeResourceDialog(); });
    // 捕获卡片上的“查找资源”，避免主卡片的默认“打开资料”事件接管。
    document.addEventListener('click', e => {
      const trigger = e.target.closest('[data-action="resources"]');
      if (trigger) {
        e.preventDefault();
        e.stopPropagation();
        openResourceDialog(trigger);
        return;
      }
      if (e.target.closest('[data-category], [data-page], [data-mobile]')) {
        queueMicrotask(syncBookHub);
      }
    }, true);
    syncBookHub();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();

