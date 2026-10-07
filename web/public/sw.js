const CACHE = 'bat-montaje-static-v2';
const BASE = '/bat-montaje/';
const SHELL = [BASE, `${BASE}index.html`, `${BASE}manifest.webmanifest`,
  `${BASE}icons/icon-192.png`, `${BASE}icons/icon-512.png`];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)));
});

self.addEventListener('activate', event => {
  event.waitUntil(Promise.all([
    caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('bat-montaje-static-') && key !== CACHE)
      .map(key => caches.delete(key)))),
    self.clients.claim()
  ]));
});

self.addEventListener('message', event => {
  if (event.data?.type === 'ACTIVATE_UPDATE') self.skipWaiting();
});

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || !url.pathname.startsWith(BASE)) return;
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(response => {
      if (response.ok) caches.open(CACHE).then(cache => cache.put(`${BASE}index.html`, response.clone()));
      return response;
    }).catch(() => caches.match(`${BASE}index.html`)));
    return;
  }
  if (url.pathname.startsWith(`${BASE}vendor/`)) {
    event.respondWith(fetch(request).then(response => {
      if (response.ok) caches.open(CACHE).then(cache => cache.put(request, response.clone()));
      return response;
    }).catch(() => caches.match(request)));
    return;
  }
  if (!/\.(?:js|css|png|svg|webmanifest|wasm)$/.test(url.pathname)) return;
  event.respondWith(caches.match(request).then(cached => cached || fetch(request).then(response => {
    if (response.ok) caches.open(CACHE).then(cache => cache.put(request, response.clone()));
    return response;
  })));
});
