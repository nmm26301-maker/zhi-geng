import fs from 'node:fs';
import assert from 'node:assert/strict';

const html = fs.readFileSync('index.html', 'utf8');
const app = fs.readFileSync('assets/app.js', 'utf8');
const bridge = fs.readFileSync('assets/book-bridge.js', 'utf8');

const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
const all = new Set(ids);
assert.equal(all.size, ids.length, 'duplicate id in index.html');

const expected = [
  'bookHub', 'bookHubSource', 'bookHubOpenLibrary', 'zlibraryUrl',
  'saveZlibraryUrl', 'clearZlibraryUrl', 'bookSourceStatus', 'bookExternalStatus',
  'bookResourceModal', 'bookResourceTitle', 'bookResourceMeta', 'bookResourceClose',
  'bookOpenLibrary', 'bookGoogleBooks', 'bookGutenberg', 'bookModalExternal',
  'bookModalSetup', 'bookCopyTitle', 'bookCopyStatus'
];
for (const id of expected) {
  assert(all.has(id), 'missing UI element: ' + id);
  assert(bridge.includes(id), 'unreferenced UI element: ' + id);
}
assert(html.includes('src="assets/book-bridge.js?v=20261016-zlib1"'), 'book bridge script not loaded');
assert(app.includes('data-action="resources"'), 'book card action missing');
assert(bridge.includes("url.protocol !== 'https:'"), 'HTTPS-only validation missing');
assert(bridge.includes("addEventListener('click'"), 'resource interaction missing');
assert(!html.includes('zlibrary-global.se'), 'unverified site must not be a hardcoded default');

console.log('Book bridge static integration checks passed.');

