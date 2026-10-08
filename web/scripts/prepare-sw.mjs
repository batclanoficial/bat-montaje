import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dist = new URL('../dist/', import.meta.url);
const assetDir = new URL('assets/', dist);
const assets = (await readdir(assetDir))
  .filter(name => /\.(?:js|css)$/.test(name))
  .map(name => `/bat-montaje/assets/${name}`);
const sw = join(fileURLToPath(dist), 'sw.js');
const source = await readFile(sw, 'utf8');
const marker = 'const ASSETS = []; // BUILD_ASSETS';
if (!source.includes(marker)) throw new Error('Falta el marcador de recursos del Service Worker.');
await writeFile(sw, source.replace(marker, `const ASSETS = ${JSON.stringify(assets)};`));
