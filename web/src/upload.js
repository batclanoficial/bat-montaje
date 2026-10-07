import { accountRequest } from './account.js';
import { clearPrivate, loadPrivate, savePrivate } from './session.js';
import { deletePendingVideo, loadPendingVideo, savePendingVideo } from './storage.js';

const KEY = 'pending_upload';
const CHUNK = 8 * 1024 * 1024;

function assertSessionUri(uri) {
  const url = new URL(uri);
  if (url.protocol !== 'https:' || url.hostname !== 'www.googleapis.com' ||
      url.pathname !== '/upload/drive/v3/files' || !url.searchParams.has('upload_id')) {
    throw new Error('Sesión de subida no válida.');
  }
  return uri;
}

function putChunk(uri, data, range, onProgress, signal) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', assertSessionUri(uri));
    xhr.timeout = 90000;
    xhr.setRequestHeader('Content-Range', range);
    if (data.size) xhr.setRequestHeader('Content-Type', 'video/mp4');
    xhr.upload.onprogress = event => {
      if (event.lengthComputable) onProgress(event.loaded, event.total);
    };
    xhr.onload = () => resolve({ status: xhr.status,
      range: xhr.getResponseHeader('Range') || '', body: xhr.responseText });
    xhr.onerror = () => reject(new Error('La subida fue interrumpida.'));
    xhr.ontimeout = () => reject(new Error('La subida tardó demasiado.'));
    xhr.onabort = () => reject(new DOMException('Cancelado', 'AbortError'));
    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(data);
  });
}

function nextOffset(range) {
  if (!range) return 0;
  const match = /^bytes=0-(\d+)$/.exec(range);
  if (!match) throw new Error('No se pudo reanudar la subida.');
  return Number(match[1]) + 1;
}

async function pause(attempt, signal) {
  const ms = Math.min(8000, 1000 * (2 ** attempt));
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timeout); reject(new DOMException('Cancelado', 'AbortError'));
    }, { once: true });
  });
}

export async function beginUpload(blob) {
  const id = crypto.randomUUID();
  await savePendingVideo(id, blob);
  const pending = { local_id: id, client_request_id: crypto.randomUUID(),
    file_size: blob.size, upload_id: '', session_uri: '' };
  await savePrivate(KEY, pending);
  return pending;
}

export const pendingUpload = () => loadPrivate(KEY);

export async function abandonUpload(pending) {
  if (pending?.upload_id) {
    const result = await accountRequest('cancel_upload', { upload_id: pending.upload_id });
    if (!result.ok) throw new Error('No se pudo cancelar el envío.');
  }
  await deletePendingVideo(pending.local_id);
  await clearPrivate(KEY);
}

export async function continueUpload(pending, onProgress, signal) {
  const blob = await loadPendingVideo(pending.local_id);
  if (!blob) throw new Error('El video pendiente ya no está disponible.');
  if (!pending.upload_id || !pending.session_uri) {
    const reservation = await accountRequest('start_upload', {
      client_request_id: pending.client_request_id, file_size: blob.size
    });
    if (!reservation.ok) throw new Error(reservation.error || 'No se pudo iniciar el envío.');
    if (reservation.status === 'NEW') {
      await deletePendingVideo(pending.local_id); await clearPrivate(KEY);
      return reservation;
    }
    pending.upload_id = reservation.upload_id;
    pending.session_uri = assertSessionUri(reservation.session_uri || '');
    await savePrivate(KEY, pending);
  }
  let failures = 0;
  let renewals = 0;
  let sentChunk = false;
  while (true) {
    if (signal?.aborted) throw new DOMException('Cancelado', 'AbortError');
    let reply;
    try {
      reply = await putChunk(pending.session_uri, new Blob([]), `bytes */${blob.size}`,
        () => {}, signal);
    } catch (error) {
      if (error.name === 'AbortError' || ++failures > 4) throw error;
      await pause(failures, signal); continue;
    }
    if (reply.status === 200 || reply.status === 201) {
      let id = '';
      try { id = JSON.parse(reply.body).id || ''; } catch { /* confirmado abajo */ }
      const done = id ? await accountRequest('finish_upload',
        { upload_id: pending.upload_id, drive_file_id: id }) :
        await accountRequest('renew_upload', { upload_id: pending.upload_id });
      if (done.ok && done.status === 'NEW') {
        await deletePendingVideo(pending.local_id); await clearPrivate(KEY);
        return done;
      }
      throw new Error('BAT no pudo confirmar el envío.');
    }
    if (reply.status === 308 && !reply.range && sentChunk)
      throw new Error('No se pudo confirmar el avance de la subida en este navegador.');
    if (reply.status === 404) {
      if (++renewals > 3) throw new Error('No se pudo reanudar el envío.');
      const fresh = await accountRequest('renew_upload', { upload_id: pending.upload_id });
      if (!fresh.ok) throw new Error(fresh.error || 'No se pudo reanudar el envío.');
      if (fresh.status === 'NEW') {
        await deletePendingVideo(pending.local_id); await clearPrivate(KEY);
        return fresh;
      }
      pending.session_uri = assertSessionUri(fresh.session_uri);
      await savePrivate(KEY, pending);
      failures = 0; sentChunk = false; continue;
    }
    if (reply.status === 429 || reply.status >= 500) {
      if (++failures > 4) throw new Error('BAT no respondió. Reintenta más tarde.');
      await pause(failures, signal); continue;
    }
    if (reply.status !== 308) throw new Error('No se pudo completar la subida.');
    const offset = nextOffset(reply.range);
    if (offset >= blob.size) { await pause(1, signal); continue; }
    const end = Math.min(blob.size, offset + CHUNK);
    try {
      const answer = await putChunk(pending.session_uri, blob.slice(offset, end),
        `bytes ${offset}-${end - 1}/${blob.size}`,
        (sent) => onProgress(Math.min(blob.size, offset + sent), blob.size), signal);
      sentChunk = true;
      if (answer.status === 200 || answer.status === 201) {
        let id = '';
        try { id = JSON.parse(answer.body).id || ''; } catch { /* comprobación posterior */ }
        if (!id) continue;
        const done = await accountRequest('finish_upload',
          { upload_id: pending.upload_id, drive_file_id: id });
        if (!done.ok || done.status !== 'NEW') throw new Error('BAT no pudo confirmar el envío.');
        await deletePendingVideo(pending.local_id); await clearPrivate(KEY);
        onProgress(blob.size, blob.size);
        return done;
      }
      if (answer.status === 308 || answer.status === 404) {
        failures = 0; continue; // Consultar posición real antes de reintentar.
      }
      if (answer.status === 429 || answer.status >= 500) {
        if (++failures > 4) throw new Error('BAT no respondió. Reintenta más tarde.');
        await pause(failures, signal); continue;
      }
      throw new Error('No se pudo completar la subida.');
    } catch (error) {
      if (error.name === 'AbortError' || ++failures > 4) throw error;
      await pause(failures, signal);
    }
  }
}
