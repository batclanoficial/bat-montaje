import { FFmpeg } from '@ffmpeg/ffmpeg';
import { mergedRanges } from './timeline.js';

let instance;

async function ffmpegInstance(onStatus) {
  if (instance) return instance;
  onStatus('Preparando el editor de video…');
  const ffmpeg = new FFmpeg();
  await ffmpeg.load({
    coreURL: `${import.meta.env.BASE_URL}vendor/ffmpeg-core.js`,
    wasmURL: `${import.meta.env.BASE_URL}vendor/ffmpeg-core.wasm`
  });
  instance = ffmpeg;
  return ffmpeg;
}

export async function renderMontage(file, events, durationMs, onStatus = () => {}) {
  const ranges = mergedRanges(events, durationMs);
  if (!ranges.length) throw new Error('Añade al menos un evento válido.');
  const ffmpeg = await ffmpegInstance(onStatus);
  const prefix = `bat_${crypto.randomUUID().replaceAll('-', '')}`;
  const input = `${prefix}_input`;
  const outputs = [];
  try {
    onStatus('Preparando el video…');
    await ffmpeg.writeFile(input, new Uint8Array(await file.arrayBuffer()));
    for (let i = 0; i < ranges.length; i++) {
      const range = ranges[i];
      const segment = `${prefix}_${i}.mp4`;
      outputs.push(segment);
      onStatus(`Creando clip ${i + 1} de ${ranges.length}…`);
      const start = (range.startMs / 1000).toFixed(3);
      const length = ((range.endMs - range.startMs) / 1000).toFixed(3);
      const result = await ffmpeg.exec([
        '-ss', start, '-i', input, '-t', length,
        '-map', '0:v:0', '-map', '0:a:0?',
        '-c:v', 'libx264', '-preset', 'ultrafast', '-pix_fmt', 'yuv420p',
        '-c:a', 'aac', '-movflags', '+faststart', segment
      ]);
      if (result !== 0) throw new Error('No se pudo crear uno de los clips.');
    }
    const list = `${prefix}_list.txt`;
    const finalName = `${prefix}_montaje.mp4`;
    await ffmpeg.writeFile(list, new TextEncoder().encode(outputs.map(name => `file '${name}'`).join('\n')));
    onStatus('Uniendo clips…');
    const exit = await ffmpeg.exec(['-f', 'concat', '-safe', '0', '-i', list,
      '-c', 'copy', '-movflags', '+faststart', finalName]);
    if (exit !== 0) throw new Error('No se pudieron unir los clips.');
    const data = await ffmpeg.readFile(finalName);
    await ffmpeg.deleteFile(finalName);
    await ffmpeg.deleteFile(list);
    return new Blob([data], { type: 'video/mp4' });
  } finally {
    for (const name of [input, ...outputs]) {
      try { await ffmpeg.deleteFile(name); } catch { /* archivo no creado */ }
    }
  }
}
