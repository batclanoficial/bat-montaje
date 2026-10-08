import './style.css';
import { DEFAULT_AFTER, DEFAULT_BEFORE, formatTime, maskTimestamp, mergedRanges,
  parseTimestamp, validateEvent } from './timeline.js';
import { deleteMontage, getMontage, listMontages, saveMontage } from './storage.js';
import { renderMontage } from './render.js';
import { accountRequest, accountSession, initializeAccount, showAccountPage } from './account.js';
import { abandonUpload, beginUpload, confirmPendingUpload, continueUpload, pendingUpload } from './upload.js';

const $ = id => document.getElementById(id);
const state = { file: null, fileUrl: '', durationMs: 0, events: [], rendering: false,
  accountAuthorized: false, uploading: false, cancelling: false, pending: null,
  uploadController: null, waitingWorker: null, installPrompt: null };

function notice(message) {
  $('notice').textContent = message;
  $('notice').classList.toggle('hidden', !message);
  if (message) $('notice').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function integerSeconds(input) {
  const value = Number(input.value);
  return Number.isInteger(value) && value >= 0 && value <= 600 ? value : null;
}

function refreshButtons() {
  const valid = !!state.file && state.events.length > 0 && !state.rendering &&
    mergedRanges(state.events, state.durationMs).length > 0;
  $('createLocal').disabled = !valid;
  $('uploadBat').disabled = !valid || !state.accountAuthorized || state.uploading;
  $('montageHint').textContent = valid ? 'El montaje se creará en este dispositivo.' :
    'Añade eventos para crear tu video.';
  $('playPause').disabled = !state.file;
  $('seek').disabled = !state.file;
  $('eventCount').textContent = String(state.events.length);
}

function typedTime(input) {
  input.addEventListener('input', () => {
    const end = input.selectionStart === input.value.length;
    input.value = maskTimestamp(input.value);
    if (end) input.setSelectionRange(input.value.length, input.value.length);
  });
}

function selectVideo(file) {
  if (!file) return;
  if (!file.type.startsWith('video/') && !/\.(mp4|mov|m4v|webm)$/i.test(file.name)) {
    notice('Selecciona un archivo de video compatible.');
    return;
  }
  if (state.fileUrl) URL.revokeObjectURL(state.fileUrl);
  state.file = file;
  state.fileUrl = URL.createObjectURL(file);
  state.durationMs = 0;
  state.events = []; // Un video nuevo nunca hereda eventos del anterior.
  $('preview').src = state.fileUrl;
  $('sourceName').textContent = file.name;
  $('videoFile').value = '';
  $('renderStatus').textContent = '';
  notice('');
  renderEvents();
  refreshButtons();
}

function addEvent() {
  if (!state.file || !state.durationMs) return notice('Selecciona primero un video.');
  const type = $('eventType').value;
  const timeMs = parseTimestamp($('eventTime').value);
  const endMs = type === 'COMBATE' ? parseTimestamp($('combatEnd').value) : timeMs;
  const before = integerSeconds($('defaultBefore'));
  const after = integerSeconds($('defaultAfter'));
  if (before === null || after === null) return notice('Los márgenes deben ser segundos enteros entre 0 y 600.');
  const event = { id: crypto.randomUUID(), type, timeMs, endMs,
    beforeMs: before * 1000, afterMs: after * 1000,
    customName: type === 'OTRO' ? $('customName').value.trim() : '' };
  const error = validateEvent(event, state.durationMs);
  if (error) return notice(error);
  state.events.push(event);
  state.events.sort((a, b) => a.timeMs - b.timeMs);
  $('eventTime').value = '';
  $('combatEnd').value = '';
  $('customName').value = '';
  notice('');
  renderEvents();
  refreshButtons();
}

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}

function renderEvents() {
  const list = $('eventList');
  list.replaceChildren();
  if (!state.events.length) {
    list.append(node('p', 'subtle', 'Todavía no hay eventos.'));
    return;
  }
  for (const event of state.events) {
    const row = node('div', 'event-row');
    const main = node('div', 'event-main');
    const title = node('div', 'event-title');
    title.append(node('strong', '', event.type === 'OTRO' ? event.customName : event.type));
    title.append(` · ${formatTime(event.timeMs)}${event.type === 'COMBATE' ? ` – ${formatTime(event.endMs)}` : ''}`);
    const actions = node('div', 'event-actions');
    const view = node('button', '', 'VER');
    view.type = 'button';
    view.onclick = () => {
      const video = $('preview');
      video.pause();
      video.currentTime = event.timeMs / 1000;
      $('previewSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
      updatePlayer();
    };
    const remove = node('button', 'danger', 'ELIMINAR');
    remove.type = 'button';
    remove.onclick = () => {
      state.events = state.events.filter(item => item.id !== event.id);
      renderEvents(); refreshButtons();
    };
    actions.append(view, remove);
    main.append(title, actions);
    const margins = node('div', 'event-margins');
    const before = node('input'); before.type = 'number'; before.min = '0'; before.max = '600'; before.step = '1'; before.value = String(event.beforeMs / 1000); before.setAttribute('aria-label', 'Segundos antes');
    const after = node('input'); after.type = 'number'; after.min = '0'; after.max = '600'; after.step = '1'; after.value = String(event.afterMs / 1000); after.setAttribute('aria-label', 'Segundos después');
    const save = node('button', '', 'GUARDAR'); save.type = 'button';
    save.onclick = () => {
      const b = integerSeconds(before), a = integerSeconds(after);
      if (b === null || a === null) return notice('Usa segundos enteros entre 0 y 600.');
      event.beforeMs = b * 1000; event.afterMs = a * 1000;
      notice('Ajustes guardados.'); refreshButtons();
    };
    margins.append('Antes', before, 'Después', after, save);
    row.append(main, margins);
    list.append(row);
  }
}

function updatePlayer() {
  const video = $('preview');
  $('timeDisplay').textContent = `${formatTime(video.currentTime * 1000)} / ${formatTime(state.durationMs)}`;
  $('seek').value = state.durationMs ? String(Math.round(video.currentTime * 1000000 / state.durationMs)) : '0';
  $('playPause').textContent = video.paused ? '▶' : 'Ⅱ';
  $('playPause').setAttribute('aria-label', video.paused ? 'Reproducir' : 'Pausar');
}

function download(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url; link.download = filename;
  document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

async function createLocal() {
  if (state.rendering || !state.file) return;
  state.rendering = true;
  refreshButtons();
  const button = $('createLocal');
  button.textContent = 'CREANDO…';
  try {
    const blob = await renderMontage(state.file, state.events, state.durationMs,
      message => { $('renderStatus').textContent = message; });
    const filename = `BAT_Montaje_${new Date().toISOString().replace(/[:.]/g, '-')}.mp4`;
    download(blob, filename);
    try {
      await saveMontage({ id: crypto.randomUUID(), name: filename, createdAt: new Date().toISOString(), blob });
      $('renderStatus').textContent = 'Montaje creado y guardado en este dispositivo.';
    } catch (error) {
      console.error('No se pudo guardar el montaje en el navegador', error);
      $('renderStatus').textContent = 'Montaje descargado. El navegador no pudo conservar una copia en Historial.';
    }
  } catch (error) {
    console.error('Error de exportación local', error);
    $('renderStatus').textContent = 'No se pudo crear el montaje en este dispositivo. Prueba con un video más corto o usa la APK.';
  } finally {
    state.rendering = false; button.textContent = 'LOCAL'; refreshButtons();
  }
}

async function renderHistory() {
  const search = $('historySearch').value.trim().toLocaleLowerCase('es');
  const list = $('localHistory'); list.replaceChildren();
  try {
    const records = await listMontages();
    const visible = search ? records.filter(item => item.name.toLocaleLowerCase('es').includes(search)) : records.slice(0, 5);
    if (!visible.length) return list.append(node('p', 'subtle', 'No hay montajes que mostrar.'));
    for (const record of visible) {
      const row = node('div', 'history-row');
      const meta = node('div', 'history-meta');
      meta.append(node('strong', '', record.name), node('small', '', new Date(record.createdAt).toLocaleString('es-PR')));
      const actions = node('div', 'history-actions');
      const open = node('button', '', 'DESCARGAR'); open.type = 'button';
      open.onclick = async () => { const saved = await getMontage(record.id); if (saved?.blob) download(saved.blob, saved.name); };
      const remove = node('button', 'danger', 'ELIMINAR'); remove.type = 'button';
      remove.onclick = async () => {
        if (!confirm('¿Eliminar este montaje?\n\nEsta acción eliminará el montaje guardado localmente en este dispositivo.')) return;
        await deleteMontage(record.id); await renderHistory();
      };
      actions.append(open, remove); row.append(meta, actions); list.append(row);
    }
  } catch (error) {
    console.error('Historial local no disponible', error);
    list.append(node('p', 'subtle', 'No se pudo abrir el historial en este navegador.'));
  }
  renderCloudHistory();
}

async function renderCloudHistory() {
  const list = $('cloudHistory'); list.replaceChildren();
  if (!state.accountAuthorized) {
    list.append(node('p', 'subtle', 'Accede a Cuenta BAT para ver tus envíos.'));
    return;
  }
  try {
    const result = await accountRequest('my_uploads', { limit: 5 });
    if (!result.ok) throw new Error(result.error || 'Historial no disponible');
    if (!result.uploads.length) {
      list.append(node('p', 'subtle', 'Todavía no has enviado montajes a BAT.'));
      return;
    }
    const labels = { UPLOADING: 'Subiendo', NEW: 'Enviado', APPROVED: 'Aprobado',
      REJECTED: 'No aprobado', COMPLETED: 'Completado', FAILED: 'Error' };
    for (const item of result.uploads) {
      const row = node('div', 'history-row');
      const meta = node('div', 'history-meta');
      meta.append(node('strong', '', item.filename),
        node('small', '', `${labels[item.status] || 'Enviado'} · ${new Date(item.uploaded_at).toLocaleString('es-PR')}`));
      row.append(meta); list.append(row);
    }
  } catch (error) {
    console.error('Historial de envíos web', error);
    list.append(node('p', 'subtle', 'No se pudieron cargar los envíos. Inténtalo más tarde.'));
  }
}

function uploadStatus(message, percent) {
  $('uploadMessage').textContent = message;
  if (percent !== undefined) {
    $('uploadProgress').value = percent;
    $('uploadPercent').textContent = `${Math.floor(percent)} %`;
  }
}

async function runPendingUpload() {
  if (!state.pending || state.uploading) return;
  state.uploading = true; state.cancelling = false;
  state.uploadController = new AbortController();
  $('resumeUpload').classList.add('hidden');
  $('cancelUpload').disabled = false;
  uploadStatus('Subiendo video a BAT…');
  refreshButtons();
  try {
    await continueUpload(state.pending, (sent, total) =>
      uploadStatus(sent >= total ? 'Confirmando envío con BAT…' : 'Subiendo video a BAT…',
        sent / total * 100), state.uploadController.signal);
    state.pending = null;
    uploadStatus('Video enviado correctamente a BAT.', 100);
    $('cancelUpload').disabled = true;
  } catch (error) {
    console.error('Subida web interrumpida', error);
    if (!state.cancelling) {
      uploadStatus($('uploadProgress').value >= 100 ?
        'BAT aún no confirma el envío. Reintenta para comprobarlo sin volver a crear el video.' :
        'La subida fue interrumpida. Puedes reanudarla.');
      $('resumeUpload').classList.remove('hidden');
    }
  } finally {
    state.uploading = false; state.uploadController = null; refreshButtons();
  }
}

async function uploadMontage() {
  if (state.rendering || !state.accountAuthorized || !state.file || !state.events.length) return;
  state.rendering = true; refreshButtons();
  showPage('upload');
  $('resumeUpload').classList.add('hidden');
  $('cancelUpload').disabled = true;
  uploadStatus('Creando montaje para BAT…', 0);
  try {
    const blob = await renderMontage(state.file, state.events, state.durationMs,
      message => uploadStatus(message));
    state.pending = await beginUpload(blob);
    await runPendingUpload();
  } catch (error) {
    console.error('Preparación de envío web', error);
    uploadStatus('No se pudo preparar el montaje. Inténtalo nuevamente.');
  } finally { state.rendering = false; refreshButtons(); }
}

async function cancelPendingUpload() {
  if (!state.pending || !confirm('¿Cancelar este envío a BAT?')) return;
  state.cancelling = true;
  state.uploadController?.abort();
  $('cancelUpload').disabled = true;
  try {
    await abandonUpload(state.pending);
    state.pending = null;
    $('resumeUpload').classList.add('hidden');
    uploadStatus('Envío cancelado.', 0);
  } catch (error) {
    console.error('Cancelación de envío web', error);
    uploadStatus('No se pudo cancelar el envío. Inténtalo nuevamente.');
    $('cancelUpload').disabled = false;
  }
}

function showPage(name) {
  $('editorPage').classList.toggle('hidden', name !== 'editor');
  $('historyPage').classList.toggle('hidden', name !== 'history');
  $('accountPage').classList.toggle('hidden', name !== 'account');
  $('uploadPage').classList.toggle('hidden', name !== 'upload');
  scrollTo({ top: 0, behavior: 'smooth' });
  if (name === 'history') renderHistory();
  if (name === 'account') showAccountPage();
}

async function setupServiceWorker() {
  if (!('serviceWorker' in navigator) || !window.isSecureContext) return;
  try {
    const registration = await navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`,
      { scope: import.meta.env.BASE_URL });
    const reveal = () => {
      if (registration.waiting && navigator.serviceWorker.controller) {
        state.waitingWorker = registration.waiting;
        $('updateBanner').classList.remove('hidden');
      }
    };
    reveal();
    registration.addEventListener('updatefound', () => registration.installing?.addEventListener('statechange', reveal));
    let reloading = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (!reloading) { reloading = true; location.reload(); }
    });
  } catch (error) { console.error('Actualización sin conexión no disponible', error); }
}

function setup() {
  $('videoFile').onchange = event => selectVideo(event.target.files?.[0]);
  typedTime($('eventTime')); typedTime($('combatEnd'));
  $('eventType').onchange = () => {
    $('combatEndLabel').classList.toggle('hidden', $('eventType').value !== 'COMBATE');
    $('customNameLabel').classList.toggle('hidden', $('eventType').value !== 'OTRO');
  };
  $('addEvent').onclick = addEvent;
  $('applyMargins').onclick = () => {
    const b = integerSeconds($('defaultBefore')), a = integerSeconds($('defaultAfter'));
    if (b === null || a === null) return notice('Usa segundos enteros entre 0 y 600.');
    for (const event of state.events) { event.beforeMs = b * 1000; event.afterMs = a * 1000; }
    renderEvents(); notice('Ajustes aplicados a todos los eventos.');
  };
  $('preview').onloadedmetadata = () => {
    const seconds = $('preview').duration;
    if (!Number.isFinite(seconds) || seconds <= 0) return notice('No se pudo leer la duración de este video.');
    state.durationMs = Math.floor(seconds * 1000);
    updatePlayer(); refreshButtons();
  };
  $('preview').ontimeupdate = updatePlayer;
  $('preview').onplay = updatePlayer; $('preview').onpause = updatePlayer;
  $('playPause').onclick = () => { const video = $('preview'); if (video.paused) video.play().catch(() => notice('No se pudo reproducir el video.')); else video.pause(); };
  $('seek').oninput = () => { $('preview').currentTime = Number($('seek').value) / 1000 * (state.durationMs / 1000); };
  $('createLocal').onclick = createLocal;
  $('uploadBat').onclick = uploadMontage;
  $('resumeUpload').onclick = runPendingUpload;
  $('cancelUpload').onclick = cancelPendingUpload;
  $('backFromUpload').onclick = () => showPage('editor');
  $('historyButton').onclick = () => showPage('history');
  $('accountButton').onclick = () => showPage('account');
  $('backFromAccount').onclick = () => showPage('editor');
  $('backFromHistory').onclick = () => showPage('editor');
  $('historySearch').oninput = renderHistory;
  $('updateButton').onclick = () => state.waitingWorker?.postMessage({ type: 'ACTIVATE_UPDATE' });
  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault(); state.installPrompt = event;
    $('installButton').classList.remove('hidden');
  });
  $('installButton').onclick = async () => { await state.installPrompt?.prompt(); $('installButton').classList.add('hidden'); };
  $('defaultBefore').value = String(DEFAULT_BEFORE);
  $('defaultAfter').value = String(DEFAULT_AFTER);
  refreshButtons(); setupServiceWorker();
  if (navigator.storage?.persist) navigator.storage.persist().catch(() => {});
  showPage('account');
  initializeAccount({ showEditor: () => showPage('editor'),
    onStateChange: payload => {
      state.accountAuthorized = !!payload?.authorized;
      refreshButtons();
    } }).then(async () => {
    state.pending = await pendingUpload().catch(() => null);
    if (accountSession()?.mode === 'ACTIVE') {
      if (state.pending) {
        showPage('upload');
        uploadStatus('Comprobando envío pendiente…');
        try {
          const confirmed = await confirmPendingUpload(state.pending);
          if (confirmed) {
            state.pending = null;
            uploadStatus('Video enviado correctamente a BAT.', 100);
            $('cancelUpload').disabled = true;
          } else {
            uploadStatus('La subida fue interrumpida. Pulsa REANUDAR para continuar.');
            $('resumeUpload').classList.remove('hidden');
          }
        } catch (error) {
          console.error('Confirmación de envío pendiente', error);
          uploadStatus('No se pudo comprobar el envío. Inténtalo nuevamente.');
          $('resumeUpload').classList.remove('hidden');
        }
      } else showPage('editor');
    }
  });
}

setup();
