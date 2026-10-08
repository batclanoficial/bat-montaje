import { batRequest } from './bridge.js';
import { clearSession, installation, loadSession, passwordKey, randomToken, saveSession } from './session.js';

const $ = id => document.getElementById(id);
let session = null;
let view = 'register';
let onStateChange = () => {};
let showEditor = () => {};
let busy = false;

const names = { APPROVED: 'Aprobada', REJECTED: 'Rechazada', SUSPENDED: 'Suspendida',
  BANEADO: 'Baneada', PENDIENTE: 'Pendiente' };

function message(text) { $('accountMessage').textContent = text; }
function setBusy(value) {
  busy = value;
  $('accountContent').querySelectorAll('button').forEach(button => { button.disabled = value; });
}
function field(id, label, type = 'text', attrs = '') {
  return `<label>${label}<input id="${id}" type="${type}" ${attrs} /></label>`;
}
function commonLocal() {
  const button = $('continueLocal');
  if (button) button.onclick = showEditor;
}

function render() {
  const content = $('accountContent');
  message('');
  if (view === 'register') {
    content.innerHTML = `<h2>REGISTRO</h2><p class="subtle">Completa tus datos para solicitar acceso a BAT.</p>
      <div class="account-form">${field('regName', 'Nombre del jugador', 'text', 'autocomplete="nickname" maxlength="32"')}
      <p class="suffix">Se añadirá <strong>-BAT</strong> una sola vez.</p>
      ${field('regPlayerId', 'Player ID', 'text', 'maxlength="64"')}
      ${field('regEmail', 'Correo electrónico', 'email', 'autocomplete="email"')}
      ${field('regPassword', 'Contraseña', 'password', 'autocomplete="new-password" minlength="12"')}
      ${field('regConfirm', 'Confirmar contraseña', 'password', 'autocomplete="new-password" minlength="12"')}
      <button id="registerNow" class="primary" type="button">REGISTRARSE</button>
      <button id="toLogin" class="quiet" type="button">ACCEDER A MI CUENTA</button>
      <button id="continueLocal" class="quiet" type="button">CONTINUAR EN LOCAL</button></div>`;
    $('registerNow').onclick = register;
    $('toLogin').onclick = () => { view = 'login'; render(); };
    commonLocal();
  } else if (view === 'login') {
    content.innerHTML = `<h2>ACCEDER A MI CUENTA</h2><p class="subtle">Utiliza el correo de tu cuenta BAT. Si esta instalación es nueva, te enviaremos un código por correo.</p>
      <div class="account-form">${field('loginEmail', 'Correo electrónico', 'email', 'autocomplete="email"')}
      ${field('loginPassword', 'Contraseña', 'password', 'autocomplete="current-password"')}
      <div id="webCodeBlock" hidden>${field('webCode', 'Código enviado a tu correo', 'text', 'autocomplete="one-time-code" inputmode="text" maxlength="19"')}
      <p class="subtle">El código caduca en 10 minutos y se utiliza una sola vez.</p></div>
      <button id="loginNow" class="primary" type="button">ACCEDER A MI CUENTA</button>
      <button id="forgotPassword" class="quiet" type="button">OLVIDÉ MI CONTRASEÑA</button>
      <button id="toRegister" class="quiet" type="button">CREAR CUENTA BAT</button>
      <button id="continueLocal" class="quiet" type="button">CONTINUAR EN LOCAL</button></div>`;
    $('loginNow').onclick = login;
    $('forgotPassword').onclick = () => { view = 'forgot'; render(); };
    $('toRegister').onclick = () => { view = 'register'; render(); };
    commonLocal();
  } else if (view === 'forgot') {
    content.innerHTML = `<h2>RESTABLECER CONTRASEÑA</h2><p class="subtle">Te enviaremos un enlace temporal a tu correo.</p>
      <div class="account-form">${field('resetEmail', 'Correo electrónico', 'email', 'autocomplete="email"')}
      <button id="sendReset" class="primary" type="button">ENVIAR ENLACE</button>
      <button id="backToLogin" class="quiet" type="button">VOLVER A ACCEDER</button></div>`;
    $('sendReset').onclick = requestReset;
    $('backToLogin').onclick = () => { view = 'login'; render(); };
  } else if (view === 'pending') {
    content.innerHTML = `<h2>SOLICITUD PENDIENTE</h2>
      <p class="subtle">Un administrador de BAT revisará tu solicitud. Puedes seguir creando montajes locales.</p>
      <div class="account-form"><button id="checkStatus" class="primary" type="button">COMPROBAR ESTADO</button>
      <button id="continueLocal" class="quiet" type="button">CONTINUAR EN LOCAL</button></div>`;
    $('checkStatus').onclick = checkPending;
    commonLocal();
  } else if (view === 'profile') {
    content.innerHTML = `<div id="profileFields" class="profile-fields"></div>
      <div class="account-form"><button id="refreshProfile" class="quiet" type="button">ACTUALIZAR CUENTA</button>
      <button id="signOut" class="danger-button" type="button">SALIR DE LA CUENTA</button></div>`;
    $('refreshProfile').onclick = loadProfile;
    $('signOut').onclick = logout;
  } else if (view === 'offline') {
    content.innerHTML = `<h2>NO SE PUDO CONECTAR CON BAT</h2>
      <p class="subtle">Puedes volver a intentar o continuar con el editor local.</p>
      <div class="account-form"><button id="retryAccount" class="primary" type="button">REINTENTAR</button>
      <button id="continueLocal" class="quiet" type="button">CONTINUAR EN LOCAL</button></div>`;
    $('retryAccount').onclick = initialize;
    commonLocal();
  }
}

async function register() {
  if (busy) return;
  let name = $('regName').value.trim().replace(/\s+/g, ' ');
  while (/-BAT$/i.test(name)) name = name.slice(0, -4).trim();
  const playerId = $('regPlayerId').value;
  const email = $('regEmail').value.trim().toLowerCase();
  const password = $('regPassword').value;
  if (name.length < 2 || name.length > 32 || !/^[\p{L}\p{N} _.\-]+$/u.test(name) ||
      /^[=+@\-]/.test(name) || !playerId || playerId.length > 64 ||
      playerId !== playerId.trim() || /^[=+@\-]/.test(playerId) ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    return message('Revisa el nombre, Player ID y correo electrónico.');
  if (password.length < 12 || password.length > 128)
    return message('La contraseña debe tener entre 12 y 128 caracteres.');
  if (password !== $('regConfirm').value) return message('Las contraseñas no coinciden.');
  if (!confirm('Vinculación de la instalación\n\nTu cuenta BAT quedará vinculada a esta instalación web. Una cuenta solo puede tener una PWA vinculada.')) return;
  setBusy(true); message('Registrando tu cuenta…');
  try {
    const device = await installation();
    const salt = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte =>
      byte.toString(16).padStart(2, '0')).join('');
    const request = { action: 'register', client_kind: 'WEB', player_name: name,
      player_id: playerId, email, password_salt: salt,
      password_key: await passwordKey(password, salt, 150000),
      registration_id: crypto.randomUUID(), pending_token: randomToken(), ...device };
    const result = await batRequest(request);
    if (!result.ok) return message(result.error || 'No se pudo completar el registro.');
    session = { mode: 'PENDIENTE', client_kind: 'WEB', email,
      installation_id: device.installation_id, device_binding_id: device.device_binding_id,
      registration_id: request.registration_id, pending_token: request.pending_token };
    await saveSession(session);
    view = 'pending'; render();
    message('Solicitud enviada. Espera la aprobación de BAT.');
  } catch (error) {
    console.error('Registro web', error);
    message('No se pudo conectar con BAT. Inténtalo nuevamente.');
  } finally { setBusy(false); }
}

async function login() {
  if (busy) return;
  const email = $('loginEmail').value.trim().toLowerCase();
  const password = $('loginPassword').value;
  const linkCode = $('webCode').value.trim();
  if (!email || !password) return message('Escribe tu correo y contraseña.');
  setBusy(true); message('Accediendo a tu cuenta…');
  try {
    const salt = await batRequest({ action: 'password_salt', email });
    if (!salt.ok) return message('No se pudo acceder a la cuenta.');
    const device = await installation();
    const accessToken = randomToken();
    const result = await batRequest({ action: 'login_web', email,
      password_key: await passwordKey(password, salt.salt, salt.iterations),
      web_link_code: linkCode, access_token: accessToken, client_kind: 'WEB', ...device });
    if (result.verification_required) {
      $('webCodeBlock').hidden = false;
      message(result.message || result.error || 'Revisa tu correo para autorizar esta instalación.');
      $('webCode').focus();
      return;
    }
    if (!result.ok) return message(result.error || 'Correo o contraseña incorrectos.');
    session = { mode: 'ACTIVE', email, access_token: accessToken,
      client_kind: 'WEB', ...device };
    await saveSession(session);
    onStateChange(result);
    view = 'profile'; render();
  } catch (error) {
    console.error('Acceso web', error);
    message('No se pudo conectar con BAT. Inténtalo nuevamente.');
  } finally { setBusy(false); if (view === 'profile') loadProfile(); }
}

async function checkPending() {
  if (!session || busy) return;
  setBusy(true); message('Comprobando tu solicitud…');
  try {
    const status = await batRequest({ action: 'registration_status', ...session });
    if (!status.ok) return message('No se pudo comprobar la solicitud.');
    if (status.status === 'APPROVED') {
      const accessToken = randomToken();
      const activated = await batRequest({ action: 'activate', ...session, access_token: accessToken });
      if (!activated.ok) return message('No se pudo activar la cuenta.');
      session = { mode: 'ACTIVE', client_kind: 'WEB', email: session.email,
        installation_id: session.installation_id, device_binding_id: session.device_binding_id,
        access_token: accessToken };
      await saveSession(session);
      onStateChange(activated);
      view = 'profile'; render();
    } else if (status.status === 'REJECTED') {
      message('Tu registro no fue aprobado. Puedes continuar creando montajes locales.');
    } else {
      message('Tu solicitud continúa pendiente.');
    }
  } catch (error) {
    console.error('Estado de registro web', error);
    message('No se pudo conectar con BAT. Inténtalo nuevamente.');
  } finally { setBusy(false); if (view === 'profile') loadProfile(); }
}

async function loadProfile() {
  if (!session || busy) return;
  setBusy(true); message('Actualizando cuenta…');
  try {
    const response = await batRequest({ action: 'account_profile', ...session });
    if (!response.ok) return message('No se pudo consultar la cuenta.');
    onStateChange(response);
    const fields = $('profileFields');
    fields.replaceChildren();
    const labels = [
      ['Nombre', response.player_name], ['Player ID', response.player_id],
      ['Correo electrónico', response.email],
      ['Fecha de creación', response.registration_date?.slice(0, 10) || ''],
      ['Fecha de aprobación', response.approval_date?.slice(0, 10) || 'Pendiente'],
      ['Estado', names[response.status] || 'Pendiente']
    ];
    if (response.status === 'APPROVED') {
      const used = response.uploads_used_today ?? 0;
      const limit = response.daily_upload_limit ?? 0;
      labels.push(['Uso de hoy', `${used} de ${limit} videos`],
        ['Disponibles', `${Math.max(0, limit - used)} videos`]);
    }
    for (const [label, value] of labels) {
      const row = document.createElement('div'); row.className = 'profile-row';
      const strong = document.createElement('strong'); strong.textContent = label;
      const span = document.createElement('span'); span.textContent = value || '—';
      row.append(strong, span); fields.append(row);
    }
    message('');
  } catch (error) {
    console.error('Perfil web', error);
    message('No se pudo conectar con BAT. Inténtalo nuevamente.');
  } finally { setBusy(false); }
}

async function requestReset() {
  if (busy) return;
  const email = $('resetEmail').value.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return message('Escribe un correo válido.');
  setBusy(true);
  try {
    await batRequest({ action: 'request_password_reset', email });
    message('Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.');
  } catch (error) {
    console.error('Recuperación web', error);
    message('No se pudo conectar con BAT. Inténtalo nuevamente.');
  } finally { setBusy(false); }
}

async function logout() {
  if (!session || !confirm('¿Salir de tu cuenta?')) return;
  setBusy(true);
  try {
    const result = await batRequest({ action: 'logout', ...session });
    if (!result.ok) return message('No se pudo cerrar la sesión. Inténtalo de nuevo.');
    await clearSession(); session = null; onStateChange(null);
    view = 'login'; render();
  } catch (error) {
    console.error('Salida web', error);
    message('No se pudo conectar con BAT. Inténtalo de nuevo.');
  } finally { setBusy(false); }
}

export async function initializeAccount(options = {}) {
  onStateChange = options.onStateChange || onStateChange;
  showEditor = options.showEditor || showEditor;
  try {
    session = await loadSession();
    if (!session) { view = 'register'; render(); return; }
    if (session.mode === 'PENDIENTE') {
      view = 'pending'; render(); await checkPending(); return;
    }
    const response = await batRequest({ action: 'validate', ...session });
    if (!response.ok) { view = 'login'; render(); message('Tu sesión terminó. Accede nuevamente.'); return; }
    onStateChange(response);
    view = 'profile'; render(); await loadProfile();
  } catch (error) {
    console.error('Inicio web sin conexión', error);
    view = 'offline'; render();
  }
}

export function showAccountPage() { render(); if (view === 'profile') loadProfile(); }
export function accountSession() { return session; }
export async function accountRequest(action, extra = {}) {
  if (!session?.access_token) throw new Error('Accede a tu cuenta BAT.');
  return batRequest({ action, ...session, ...extra });
}
