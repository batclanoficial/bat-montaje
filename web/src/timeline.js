import rules from '../../shared/montage-rules.json' with { type: 'json' };

export const EVENT_TYPES = Object.freeze(rules.eventTypes);
export const DEFAULT_BEFORE = rules.defaultBeforeSeconds;
export const DEFAULT_AFTER = rules.defaultAfterSeconds;

export function maskTimestamp(input) {
  const digits = String(input ?? '').replace(/\D/g, '').slice(0, 6);
  if (!digits) return '';
  if (digits.length <= 2) return `0:${digits.padStart(2, '0')}`;
  if (digits.length <= 4) return `${digits.slice(0, -2)}:${digits.slice(-2)}`;
  return `${digits.slice(0, -4)}:${digits.slice(-4, -2)}:${digits.slice(-2)}`;
}

export function parseTimestamp(value) {
  if (!/^\d{1,2}:\d{2}(?::\d{2})?$/.test(String(value ?? ''))) return -1;
  const parts = value.split(':').map(Number);
  if (parts.some((part, index) => index > 0 && part > 59)) return -1;
  const seconds = parts.length === 2
    ? parts[0] * 60 + parts[1]
    : parts[0] * 3600 + parts[1] * 60 + parts[2];
  return seconds * 1000;
}

export function formatTime(milliseconds) {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds / 60) % 60;
  const rest = String(seconds % 60).padStart(2, '0');
  return hours ? `${hours}:${String(minutes).padStart(2, '0')}:${rest}` : `${minutes}:${rest}`;
}

export function validateEvent(event, durationMs) {
  if (!EVENT_TYPES.includes(event.type)) return 'Selecciona un tipo de evento.';
  if (!Number.isInteger(event.timeMs) || event.timeMs < 0 || event.timeMs > durationMs)
    return 'El tiempo debe estar dentro del video.';
  if (!Number.isInteger(event.beforeMs) || event.beforeMs < 0 ||
      !Number.isInteger(event.afterMs) || event.afterMs < 0)
    return 'Los segundos antes y después deben ser enteros positivos.';
  if (event.type === 'OTRO' && !String(event.customName ?? '').trim())
    return 'Escribe un nombre para este evento.';
  if (event.type === 'COMBATE' && (!Number.isInteger(event.endMs) ||
      event.endMs <= event.timeMs || event.endMs > durationMs))
    return 'El combate debe terminar después de comenzar y dentro del video.';
  return '';
}

export function mergedRanges(events, durationMs) {
  const ranges = [];
  for (const event of events) {
    if (validateEvent(event, durationMs)) continue;
    const endMs = event.type === 'COMBATE' ? event.endMs : event.timeMs;
    const start = Math.max(0, event.timeMs - event.beforeMs);
    const end = Math.min(durationMs, endMs + event.afterMs);
    if (end > start) ranges.push({ startMs: start, endMs: end });
  }
  ranges.sort((a, b) => a.startMs - b.startMs);
  const merged = [];
  for (const range of ranges) {
    const last = merged.at(-1);
    if (!last || range.startMs > last.endMs + rules.mergeGapMilliseconds) {
      merged.push({ ...range });
    } else {
      last.endMs = Math.max(last.endMs, range.endMs);
    }
  }
  return merged;
}
