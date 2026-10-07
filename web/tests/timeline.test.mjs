import test from 'node:test';
import assert from 'node:assert/strict';
import { maskTimestamp, parseTimestamp, mergedRanges, validateEvent } from '../src/timeline.js';

test('máscara y validación del tiempo son equivalentes al editor Android', () => {
  assert.equal(maskTimestamp('4'), '0:04');
  assert.equal(maskTimestamp('224'), '2:24');
  assert.equal(maskTimestamp('444444'), '44:44:44');
  assert.equal(parseTimestamp('2:24'), 144000);
  assert.equal(parseTimestamp('1:60'), -1);
});

test('combate, márgenes y rangos cercanos se fusionan', () => {
  const events = [
    { type: 'COMBATE', timeMs: 10000, endMs: 16000, beforeMs: 7000, afterMs: 3000 },
    { type: 'KILL', timeMs: 20000, beforeMs: 1000, afterMs: 3000 }
  ];
  assert.deepEqual(mergedRanges(events, 60000), [{ startMs: 3000, endMs: 23000 }]);
});

test('otro requiere nombre y combate requiere fin', () => {
  assert.match(validateEvent({ type: 'OTRO', timeMs: 1000, beforeMs: 0, afterMs: 1, customName: '' }, 5000), /nombre/);
  assert.match(validateEvent({ type: 'COMBATE', timeMs: 2000, endMs: 1000, beforeMs: 0, afterMs: 1 }, 5000), /terminar/);
});
