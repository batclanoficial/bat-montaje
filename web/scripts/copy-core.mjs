import { copyFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
// Vite empaqueta @ffmpeg/ffmpeg como módulo Worker; importScripts no existe allí.
const source = resolve(root, 'node_modules/@ffmpeg/core/dist/esm');
const target = resolve(root, 'dist/vendor');
mkdirSync(target, { recursive: true });
for (const name of ['ffmpeg-core.js', 'ffmpeg-core.wasm']) {
  copyFileSync(resolve(source, name), resolve(target, name));
}
