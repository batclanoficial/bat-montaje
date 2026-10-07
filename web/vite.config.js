import { defineConfig } from 'vite';

export default defineConfig({
  base: '/bat-montaje/',
  build: { outDir: 'dist', emptyOutDir: true },
  server: { port: 4173 }
});
