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

## Cuenta BAT y envío

Cuenta BAT utiliza el mismo Apps Script que Android. El despliegue v16 ofrece un puente HTML Service limitado al origen de la PWA, y permite conservar una instalación APK más una PWA vinculada mediante un código temporal generado en la APK actualizada. Se verificó una respuesta real de acceso fallido desde GitHub Pages, sin crear cuentas ni utilizar credenciales de miembros. No hay secretos oficiales en el frontend.

La subida utiliza una sesión temporal reanudable y guarda el video pendiente en IndexedDB. **No se ha validado todavía una subida PWA real con una cuenta aprobada**, ni la recuperación tras corte de red en móviles; la política CORS de la sesión Drive debe verificarse con una sesión válida. No presentar esa función como garantizada hasta pasar esas pruebas. Los uploads no se inician al seleccionar un video.

## Publicación

El workflow de GitHub Actions construye `web/` y publica `web/dist`. El repositorio destinado a esto es `batclanoficial/bat-montaje`; **no** usar `batclanoficial/bat`. Configurar Pages con fuente “GitHub Actions”. Una vez listo, cada push a `main` que cambie `web/` o `shared/` publica la nueva PWA. El Service Worker espera a que el usuario pulse **ACTUALIZAR** cuando ya hay una versión abierta.

## Diferencias por plataforma

- Android nativo: Media3/ExoPlayer, Storage Access Framework y Keystore; su APK existente se conserva.
- PWA: video HTML, FFmpeg WebAssembly en Worker, IndexedDB y descarga. El shell y los recursos JS/CSS se precachean; el motor FFmpeg grande se cachea después de su primer uso. Videos grandes pueden superar la memoria del navegador. Se necesita probar Safari/iOS y teléfonos reales. La instalación standalone aún no se verificó en un dispositivo físico.
