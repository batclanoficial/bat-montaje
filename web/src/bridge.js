// La URL del despliegue es pública; no es una credencial. Los secretos siguen en Apps Script.
const BACKEND = 'https://script.google.com/macros/s/AKfycbxnE-NeLYmbDMQ6nlImFPiIPWN1ShH2N-4J2xmigeuowqwCs4F7W9XV-gjTL-W-zAjQ/exec';
const SANDBOX_ORIGIN = /^https:\/\/[a-z0-9-]+-script\.googleusercontent\.com$/;

let connection;

function openBridge() {
  if (connection) return connection;
  connection = new Promise((resolve, reject) => {
    const nonce = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte =>
      byte.toString(16).padStart(2, '0')).join('');
    const iframe = document.createElement('iframe');
    iframe.hidden = true;
    iframe.title = 'Conexión BAT';
    iframe.referrerPolicy = 'no-referrer';
    const pending = new Map();
    let child = null;
    let origin = '';
    const timer = setTimeout(() => {
      if (!child) {
        connection = null;
        iframe.remove();
        window.removeEventListener('message', listener);
        reject(new Error('No se pudo conectar con BAT.'));
      }
    }, 12000);
    function listener(event) {
      const data = event.data;
      if (!SANDBOX_ORIGIN.test(event.origin) || !data || data.nonce !== nonce) return;
      if (data.batBridge === 'ready' && !child) {
        child = event.source;
        origin = event.origin;
        clearTimeout(timer);
        resolve({
          call(request) {
            return new Promise((done, fail) => {
              const id = crypto.randomUUID();
              const timeout = setTimeout(() => {
                pending.delete(id);
                fail(new Error('BAT no respondió a tiempo.'));
              }, 45000);
              pending.set(id, response => { clearTimeout(timeout); done(response); });
              child.postMessage({ batBridge: 'request', nonce, id, request }, origin);
            });
          }
        });
      } else if (data.batBridge === 'response' && event.source === child &&
          event.origin === origin && typeof data.id === 'string') {
        const done = pending.get(data.id);
        if (done) { pending.delete(data.id); done(data.response); }
      }
    }
    window.addEventListener('message', listener);
    iframe.src = `${BACKEND}?mode=bridge&nonce=${nonce}`;
    document.body.append(iframe);
  });
  return connection;
}

export async function batRequest(request) {
  const bridge = await openBridge();
  return bridge.call(request);
}
