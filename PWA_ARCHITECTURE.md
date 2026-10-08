# BAT Montaje: arquitectura Android + PWA

## Estado inicial verificado (7 de octubre de 2026)

La APK actual es Java/Android nativo. La interfaz se construye en `AccountActivity`, `MainActivity`, `HistoryActivity` y `UploadActivity`; no existe un frontend web que pueda publicarse sin cambios en GitHub Pages. Media3/ExoPlayer reproduce y exporta los clips, Storage Access Framework maneja los archivos y Android Keystore protege la sesión. `MontageLogic` calcula eventos y rangos. El backend compartido ya es Google Apps Script, con Sheets y Drive privados. La comunicación Android → Apps Script funciona mediante `HttpURLConnection`.

El frontend estático de GitHub Pages **no puede reutilizar directamente** el reproductor/exportador, selector de archivos, Keystore ni la interfaz Java. Reescribir de golpe esas pantallas pondría en riesgo la APK probada. Por eso la migración debe ser incremental: conservar la APK nativa, extraer reglas y contratos de datos a un módulo compartido, crear un frontend PWA en este mismo árbol y migrar pantallas Android al frontend compartido cuando el puente de Media3/archivos/sesión esté probado. La excepción permanente serán los adaptadores de plataforma de archivos, video y almacenamiento protegido.

## Backend web y vinculación

Apps Script `ContentService` responde con redirección y no ofrece los encabezados CORS necesarios para que la PWA de GitHub Pages lea respuestas a sus POST JSON. No se usa `no-cors`, JSONP ni credenciales oficiales en el navegador. El backend v16 incorporó una página HTML Service que se comunica con la PWA mediante `postMessage`, con origen exacto, nonce por solicitud y validación server-side. La versión 17 añade la autorización de una instalación web nueva por código de correo, sin APK. El login de una cuenta aprobada y el envío todavía requieren pruebas de extremo a extremo en teléfonos.

La PWA es una vía de acceso independiente: se puede registrar y utilizar desde iPhone sin haber instalado jamás la APK. Cada cuenta admite una instalación Android y **una** PWA vinculada, pero ninguna plataforma es requisito de la otra. Si una instalación web nueva accede a una cuenta existente, el backend primero comprueba la contraseña derivada y después envía al correo de esa cuenta un código temporal de un solo uso. Al introducirlo, la instalación PWA anterior queda revocada. El antiguo código emitido por la APK se conserva solo por compatibilidad, no como requisito. El backend guarda hashes, valida rol/estado/cuota y no usa fingerprinting.

## Fuente única y excepciones

El objetivo final es que editor, eventos, historial y cuenta utilicen componentes web compartidos dentro de la APK (WebView) y la PWA. Mientras se migra, la APK conserva sus pantallas nativas y la PWA se implementa en `web/`. Ambas versiones leen `shared/montage-rules.json` para los tipos de evento, márgenes iniciales y distancia de fusión. La APK incluye ese archivo como asset y la PWA lo incorpora en el build. Añadir un tipo de momento estándar en ese JSON lo hace aparecer en ambas interfaces tras recompilarlas; los tipos especiales que necesitan campos propios, como COMBATE y OTRO, siguen requiriendo lógica de interfaz. Los algoritmos de exportación y las pantallas aún son distintos; esto es una transición, no una interfaz completamente compartida.

## Riesgos que requieren validación real

- FFmpeg WebAssembly consume bastante memoria y puede no exportar videos largos en móviles con poca RAM. El exportador nativo Media3 se mantiene en Android.
- iOS/Safari no ofrece todas las APIs de Chrome Android; instalación, almacenamiento y exportación deben probarse en dispositivo real.
- El almacenamiento web es gestionado por el navegador y no ofrece la misma persistencia que un archivo guardado por Storage Access Framework. Se ofrecerá descarga local y se solicitará persistencia cuando exista.
- El puente de Apps Script respondió desde GitHub Pages, pero la sesión resumable de Drive requiere una prueba CORS con una sesión válida y una cuenta aprobada. Hasta pasarla, la subida PWA es beta y no debe describirse como validada.

## Pruebas de referencia ya pasadas

- `gradlew.bat assembleDebug` (asset compartido confirmado dentro del APK)
- `node backend/tests/backend.test.cjs`
- `tests/MontageLogicTest.java` con JDK 17

El repositorio público `batclanoficial/bat` queda fuera de alcance. `batclanoficial/bat-montaje` excluye `backend/`, APKs, videos de prueba y configuraciones locales mediante `.gitignore`/auditoría de secretos. El workflow de GitHub Pages construye solamente `web/` con base `/bat-montaje/`.
