const DB_NAME = 'bat-montaje-local';
const STORE = 'montages';
const UPLOAD_STORE = 'pending-uploads';
const VERSION = 2;

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(UPLOAD_STORE)) db.createObjectStore(UPLOAD_STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function transaction(mode, method, argument) {
  const db = await openDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const request = tx.objectStore(STORE)[method](argument);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

async function uploadTransaction(mode, method, argument, second) {
  const db = await openDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(UPLOAD_STORE, mode);
      const store = tx.objectStore(UPLOAD_STORE);
      const request = second === undefined ? store[method](argument) : store[method](argument, second);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      tx.onerror = () => reject(tx.error);
    });
  } finally { db.close(); }
}

export async function saveMontage(record) {
  await transaction('readwrite', 'put', record);
  return record;
}

export async function listMontages() {
  const records = await transaction('readonly', 'getAll');
  return records.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function getMontage(id) {
  return transaction('readonly', 'get', id);
}

export async function deleteMontage(id) {
  return transaction('readwrite', 'delete', id);
}

export const savePendingVideo = (id, blob) => uploadTransaction('readwrite', 'put', blob, id);
export const loadPendingVideo = id => uploadTransaction('readonly', 'get', id);
export const deletePendingVideo = id => uploadTransaction('readwrite', 'delete', id);
