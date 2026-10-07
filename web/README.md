# BAT Montaje PWA

Frontend instalable en español para GitHub Pages bajo `/bat-montaje/`. Comparte el backend y las reglas declarativas en `../shared` con Android; la migración de la interfaz Android nativa a componentes web compartidos sigue pendiente (véase `../PWA_ARCHITECTURE.md`).

## Desarrollo

Con Node 24 y pnpm 11:

```text
pnpm install
pnpm test
pnpm dev
pnpm build
pnpm preview
```

El build incluye FFmpeg WebAssembly alojado en el mismo origen. El archivo fuente solo se lee tras selección explícita del usuario. La creación local descarga el MP4 y conserva una copia en IndexedDB si el navegador dispone de espacio. Los videos no se almacenan en `localStorage` ni se suben automáticamente.

## Funciones no disponibles aún en PWA

Cuenta BAT y Subir a BAT permanecen deshabilitados porque el backend actual de Apps Script no es legible mediante CORS desde GitHub Pages. No colocar tokens oficiales ni secretos en este frontend. La vinculación segura APK + una PWA y la subida reanudable necesitan backend y pruebas móviles de extremo a extremo antes de habilitarse.

## Publicación

El workflow de GitHub Actions construye `web/` y publica `web/dist`. El repositorio destinado a esto es `batclanoficial/bat-montaje`; **no** usar `batclanoficial/bat`. Configurar Pages con fuente “GitHub Actions”. Una vez listo, cada push a `main` que cambie `web/` o `shared/` publica la nueva PWA. El Service Worker espera a que el usuario pulse **ACTUALIZAR** cuando ya hay una versión abierta.

## Diferencias por plataforma

- Android nativo: Media3/ExoPlayer, Storage Access Framework y Keystore; su APK existente se conserva.
- PWA: video HTML, FFmpeg WebAssembly en Worker, IndexedDB y descarga. Videos grandes pueden superar la memoria del navegador. Se necesita probar Safari/iOS y teléfonos reales.
