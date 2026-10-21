/**
 * 知更 Icon System v1
 * 风格：圆端等线宽（24×24）+ 原创知更鸟识别标识。
 * 纯 SVG，无图标字体/外部 CDN；保留原 DOM 按钮与事件监听。
 */
(() => {
  "use strict";
  const P = {
    compass:'<circle cx="12" cy="12" r="8.5"/><path d="m15.2 8.8-2.5 5.1-4.9 1.3 2.4-5.1 5-1.3Z"/><circle cx="12" cy="12" r=".7" fill="currentColor" stroke="none"/>',
    search:'<circle cx="10.9" cy="10.9" r="6.9"/><path d="m16 16 4.2 4.2"/>',
    sparkle:'<path d="m12 2.7 1.95 6.65L20.6 11.3l-6.65 1.95L12 19.9l-1.95-6.65-6.65-1.95 6.65-1.95L12 2.7Z"/><path d="m19 18 .55 1.45L21 20l-1.45.55L19 22l-.55-1.45L17 20l1.45-.55L19 18Z"/>',
    heart:'<path d="M20.2 8.5c0-2.5-1.9-4.3-4.3-4.3-1.6 0-3.1.9-3.9 2.2-.8-1.3-2.3-2.2-3.9-2.2-2.4 0-4.3 1.8-4.3 4.3 0 4.8 8.2 10.3 8.2 10.3s8.2-5.5 8.2-10.3Z"/>',
    grid:'<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
    movie:'<rect x="3.5" y="5" width="17" height="15" rx="3"/><path d="m3.5 10 17-5M8 5l3.3 4.4M14 5l3 3.8"/><path d="m10 12.5 5 2.6-5 2.5z"/>',
    tv:'<rect x="3" y="6" width="18" height="13.5" rx="3"/><path d="m8 3 4 3 4-3"/><path d="M7 16h6M17.5 14v2"/>',
    anime:'<path d="m12 3 2.2 5.7L20 11l-5.8 2.3L12 19l-2.2-5.7L4 11l5.8-2.3L12 3Z"/><path d="M18.8 17.2 19.7 19l1.8.8-1.8.9-.9 1.8-.8-1.8-1.8-.9 1.8-.8Z"/>',
    variety:'<rect x="8" y="3.5" width="8" height="12" rx="4"/><path d="M5.5 11.7a6.5 6.5 0 0 0 13 0M12 18.3V21M8 21h8"/><path d="M10 8h4M10 11h4"/>',
    book:'<path d="M12 20.5c-2.4-1.7-5-2.3-8.5-2.1V4.8C7 4.4 9.7 5 12 6.6v13.9Zm0 0c2.4-1.7 5-2.3 8.5-2.1V4.8C17 4.4 14.3 5 12 6.6"/><path d="M12 6.6v13.9"/>',
    rankings:'<path d="M4.5 20V11h5v9M14.5 20V7h5v13M3 20.5h18"/><path d="m12 3 .9 1.8 2 .3-1.45 1.4.35 2-1.8-1-1.8 1 .35-2L9.1 5.1l2-.3Z"/>',
    play:'<rect x="3.2" y="4" width="17.6" height="16" rx="3.4"/><path d="m10 8.3 5.8 3.7-5.8 3.7Z"/>',
    sms:'<rect x="4.5" y="3" width="15" height="18" rx="3"/><path d="M9 6h6M9 17.5h6"/><path d="M9 10h6v4H9z"/>',
    nodes:'<circle cx="6" cy="6" r="2.4"/><circle cx="18" cy="8.2" r="2.4"/><circle cx="8" cy="18" r="2.4"/><circle cx="18" cy="18" r="2.4"/><path d="m8.2 6.5 7.4 1.3M7 8.2l.7 7.4M10.4 18H15.6M16.9 10.5l.8 5.1"/>',
    crown:'<path d="m3 7 4.7 4 4.3-6 4.3 6L21 7l-1.5 11h-15L3 7ZM5.5 21h13"/><path d="M12 13.5h.01"/>',
    library:'<path d="M4.3 4.2h4.9v16H4.3zM9.2 4.2h4.9v16H9.2zM16 6.2l4.1-1.1 3.2 13.8-4.1 1.1z"/><path d="M6.2 8v8M11.1 8v8"/>',
    bot:'<rect x="3.5" y="7" width="17" height="13" rx="4"/><path d="M12 3v4M9 3h6"/><circle cx="8.6" cy="13" r="1" fill="currentColor" stroke="none"/><circle cx="15.4" cy="13" r="1" fill="currentColor" stroke="none"/><path d="M9 17h6"/>',
    user:'<circle cx="12" cy="8" r="4"/><path d="M4.5 20c.4-4.1 3.3-6 7.5-6s7.1 1.9 7.5 6"/>',
    settings:'<path d="M9.3 3.2h5.4l.5 2.5 2.1 1.2 2.4-.9 2.7 4.6-1.9 1.7v2.4l1.9 1.7-2.7 4.6-2.4-.9-2.1 1.2-.5 2.5H9.3l-.5-2.5-2.1-1.2-2.4.9-2.7-4.6 1.9-1.7v-2.4l-1.9-1.7L4.3 6l2.4.9 2.1-1.2.5-2.5Z" transform="translate(1 -1.5) scale(.92)"/><circle cx="12" cy="12" r="2.8"/>',
    calendar:'<rect x="3.4" y="5" width="17.2" height="16" rx="2.5"/><path d="M7.7 3v4M16.3 3v4M3.4 10h17.2"/><path d="m9 15 2 2 4-4"/>',
    rss:'<path d="M4 4.5A15.5 15.5 0 0 1 19.5 20M4 10.5a9.5 9.5 0 0 1 9.5 9.5"/><circle cx="5.2" cy="18.8" r="1.7" fill="currentColor" stroke="none"/>',
    refresh:'<path d="M20 10a8 8 0 0 0-14.5-3M4 4v5.5h5.5M4 14a8 8 0 0 0 14.5 3M20 20v-5.5h-5.5"/>',
    bookmark:'<path d="M6.2 4.3h11.6v16l-5.8-3.7-5.8 3.7V4.3Z"/>',
    arrow:'<path d="M5 18 19 5M9 5h10v10"/>'
  };
  function svg(name) {
    if (!P[name]) return null;
    const wrapper = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    wrapper.setAttribute("viewBox", "0 0 24 24");
    wrapper.setAttribute("aria-hidden", "true");
    wrapper.setAttribute("focusable", "false");
    wrapper.setAttribute("class", "zg-icon");
    wrapper.innerHTML = P[name];
    return wrapper;
  }
  function brand() {
    const icon = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    icon.setAttribute("viewBox", "0 0 64 64");
    icon.setAttribute("class", "zg-icon zg-icon--brand");
    icon.setAttribute("aria-hidden", "true");
    icon.setAttribute("focusable", "false");
    icon.innerHTML = '<rect x="1" y="1" width="62" height="62" rx="19" fill="#244E40"/><path d="M12 46.5c12-3.4 24-2.1 39-1" fill="none" stroke="#EACB94" stroke-width="2.6" stroke-linecap="round"/><path d="M17.7 34.5c.2-10.3 8.2-18.4 18.6-17.8 8.4.4 14.7 6.8 14.6 15.4-.2 11.6-9.1 18.4-20.4 17.2-8.1-.8-13-6.8-12.8-14.8Z" fill="#FFF9EC"/><path d="M19.3 37.4c-2.2-5 1.3-11.5 7.1-13.5 2.7 6.5 8.1 9.8 15.8 10.6-1.6 9.4-12.6 14.9-19 10.4-3.6-2.2-4.4-4.7-3.9-7.5Z" fill="#B45F4D"/><path d="M19.3 37.1c3.1-7.5 8.8-11.5 16.7-11.1-1.4 8-6.7 12.2-16.7 14.3Z" fill="#D7E8CC"/><path d="m49.7 29.6 7.5 2.6-7.2 3.1Z" fill="#E7B468"/><circle cx="42.1" cy="26.4" r="2.1" fill="#203B32"/><circle cx="42.7" cy="25.8" r=".6" fill="#fff"/><path d="m18 13 1.25 3.25L22.5 17.5l-3.25 1.25L18 22l-1.25-3.25L13.5 17.5l3.25-1.25Z" fill="#F3DCA7"/>';
    return icon;
  }
  function setIcon(el, name, mark = false) {
    if (!el || el.querySelector("svg.zg-icon")) return;
    const child = mark ? brand() : svg(name);
    if (child) el.replaceChildren(child);
  }
  function prependIcon(el, name) {
    if (!el || el.querySelector("svg.zg-icon")) return;
    const child = svg(name);
    if (child) {
      // 保留按钮中的文本、计数徽标、small 标签及已有事件处理器。
      [...el.childNodes].filter(n => n.nodeType === Node.TEXT_NODE && /^[\s◉⌕▥▣◇▤✦♡⚙]+$/.test(n.textContent || "")).forEach(n => n.remove());
      el.prepend(child);
    }
  }
  function paint() {
    setIcon(document.querySelector(".brand-mark"), "brand", true);
    const mobile = document.querySelector(".mobile-brand");
    if (mobile && !mobile.querySelector("svg")) {
      [...mobile.childNodes].filter(n => n.nodeType === Node.TEXT_NODE).forEach(n => n.remove());
      mobile.prepend(brand());
    }
    ["compass","sparkle","heart"].forEach((name,i) => setIcon(document.querySelectorAll(".nav-icon")[i],name));
    ["grid","movie","tv","anime","variety","book"].forEach((name,i) => setIcon(document.querySelectorAll(".category-nav .cat-icon")[i],name));
    setIcon(document.querySelector(".search-icon"),"search");
    setIcon(document.querySelector(".hero-art .radar-core"),"sparkle");
    setIcon(document.querySelector("#headerSettings"),"settings");
    setIcon(document.querySelector("#avatarButton"),"heart");
    setIcon(document.querySelector(".newsletter-icon"),"rss");
    setIcon(document.querySelector(".heading-sparkle"),"sparkle");
    setIcon(document.querySelector(".empty-glyph"),"compass");
    setIcon(document.querySelector(".book-search-control > span"),"search");
    ["calendar","sparkle","heart"].forEach((name,i) => setIcon(document.querySelectorAll(".stats-row .stat-symbol")[i],name));
    const settingsLink = document.querySelector(".settings-link");
    if (settingsLink && !settingsLink.querySelector("svg")) {
      settingsLink.textContent = settingsLink.textContent.replace(/^[\s⚙\u00a0]+/,"");
      prependIcon(settingsLink,"settings");
    }
    const bottom = {
      "#rankings":"rankings", "#kanju":"play", "#sms":"sms", "#nodes":"nodes",
      "#memberships":"crown", "#library":"library", "#assistant":"bot"
    };
    document.querySelectorAll(".bottom-nav > *").forEach(el => {
      const name = el.dataset.mobile === "explore" ? "compass"
        : el.dataset.mobile === "saved" ? "heart" : bottom[el.getAttribute("href")];
      if (!name) return;
      // 仅清除标签前原先的单字形图标，保留 small。
      [...el.childNodes].filter(n => n.nodeType === Node.TEXT_NODE).forEach(n => n.remove());
      prependIcon(el,name);
    });
    const footer = document.querySelector(".site-footer > span");
    if (footer && !footer.querySelector("svg")) {
      footer.textContent = footer.textContent.replace(/^◉\s*/,"");
      footer.prepend(brand());
      footer.querySelector("svg").style.width = "17px";
      footer.querySelector("svg").style.height = "17px";
      footer.querySelector("svg").style.marginRight = "7px";
    }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded",paint,{once:true});
  else paint();
})();