const DB_NAME = 'bat-montaje-account';
const STORE = 'vault';

function openVault() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function get(key) {
  const db = await openVault();
  try {
    return await new Promise((resolve, reject) => {
      const request = db.transaction(STORE, 'readonly').objectStore(STORE).get(key);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally { db.close(); }
}

async function put(key, value) {
  const db = await openVault();
  try {
    await new Promise((resolve, reject) => {
      const request = db.transaction(STORE, 'readwrite').objectStore(STORE).put(value, key);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  } finally { db.close(); }
}

async function remove(key) {
  const db = await openVault();
  try {
    await new Promise((resolve, reject) => {
      const request = db.transaction(STORE, 'readwrite').objectStore(STORE).delete(key);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  } finally { db.close(); }
}

async function key() {
  let stored = await get('key');
  if (!stored) {
    stored = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false,
      ['encrypt', 'decrypt']);
    await put('key', stored);
  }
  return stored;
}

async function saveEncrypted(name, value) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  const data = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, await key(), bytes);
  await put(name, { iv, data });
}

async function loadEncrypted(name) {
  const entry = await get(name);
  if (!entry) return null;
  try {
    const data = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: entry.iv },
      await key(), entry.data);
    return JSON.parse(new TextDecoder().decode(data));
  } catch { return null; }
}

export const saveSession = value => saveEncrypted('session', value);
export const loadSession = () => loadEncrypted('session');
export const clearSession = () => remove('session');
export const savePrivate = (name, value) => saveEncrypted(name, value);
export const loadPrivate = name => loadEncrypted(name);
export const clearPrivate = name => remove(name);

export async function installation() {
  let id = await loadEncrypted('installation');
  if (!id) {
    id = { installation_id: crypto.randomUUID(),
      device_binding_id: Array.from(crypto.getRandomValues(new Uint8Array(32)), byte =>
        byte.toString(16).padStart(2, '0')).join('') };
    await saveEncrypted('installation', id);
  }
  return id;
}

export function randomToken() {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), byte =>
    byte.toString(16).padStart(2, '0')).join('');
}

export async function passwordKey(password, salt, iterations) {
  if (!/^[0-9a-f]{32}$/i.test(salt) || !Number.isInteger(iterations) ||
      iterations < 100000 || iterations > 1000000) throw new Error('Configuración de acceso inválida.');
  const raw = await crypto.subtle.importKey('raw', new TextEncoder().encode(password),
    'PBKDF2', false, ['deriveBits']);
  const saltBytes = new Uint8Array(salt.match(/../g).map(part => parseInt(part, 16)));
  const derived = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256',
    salt: saltBytes, iterations }, raw, 256);
  return Array.from(new Uint8Array(derived), byte => byte.toString(16).padStart(2, '0')).join('');
}
