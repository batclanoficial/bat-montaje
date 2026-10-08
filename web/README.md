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

Cuenta BAT utiliza el mismo Apps Script que Android. La PWA permite registrarse desde iPhone sin APK. Para acceder a una cuenta existente desde una instalación web nueva, se usa el correo y la contraseña, seguidos de un código temporal enviado al correo de esa cuenta; no se requiere Android. Puede coexistir una instalación Android con una PWA. Vincular otra PWA revoca la sesión web anterior. El puente HTML Service está limitado al origen de la PWA y no hay secretos oficiales en el frontend. La entrega del código y el acceso real en iPhone requieren una prueba con una cuenta del usuario después del despliegue.

La subida utiliza una sesión temporal reanudable y guarda el video pendiente en IndexedDB. En una prueba real, el archivo llegó completo pero la PWA no recibió la confirmación final del navegador. Ahora consulta al backend antes de reintentar; este verifica el archivo existente y evita volver a transferirlo. **Queda pendiente validar de nuevo el flujo completo en un navegador del usuario y la recuperación tras corte de red en móviles.** Los uploads no se inician al seleccionar un video.

## Publicación

El workflow de GitHub Actions construye `web/` y publica `web/dist`. El repositorio destinado a esto es `batclanoficial/bat-montaje`; **no** usar `batclanoficial/bat`. Configurar Pages con fuente “GitHub Actions”. Una vez listo, cada push a `main` que cambie `web/` o `shared/` publica la nueva PWA. El Service Worker espera a que el usuario pulse **ACTUALIZAR** cuando ya hay una versión abierta.

## Diferencias por plataforma

- Android nativo: Media3/ExoPlayer, Storage Access Framework y Keystore; su APK existente se conserva.
- PWA: video HTML, FFmpeg WebAssembly en Worker, IndexedDB y descarga. El shell y los recursos JS/CSS se precachean; el motor FFmpeg grande se cachea después de su primer uso. Videos grandes pueden superar la memoria del navegador. Se necesita probar Safari/iOS y teléfonos reales. La instalación standalone aún no se verificó en un dispositivo físico.
