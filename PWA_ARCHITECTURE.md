# BAT Montaje: arquitectura Android + PWA

## Estado inicial verificado (7 de octubre de 2026)

La APK actual es Java/Android nativo. La interfaz se construye en `AccountActivity`, `MainActivity`, `HistoryActivity` y `UploadActivity`; no existe un frontend web que pueda publicarse sin cambios en GitHub Pages. Media3/ExoPlayer reproduce y exporta los clips, Storage Access Framework maneja los archivos y Android Keystore protege la sesión. `MontageLogic` calcula eventos y rangos. El backend compartido ya es Google Apps Script, con Sheets y Drive privados. La comunicación Android → Apps Script funciona mediante `HttpURLConnection`.

El frontend estático de GitHub Pages **no puede reutilizar directamente** el reproductor/exportador, selector de archivos, Keystore ni la interfaz Java. Reescribir de golpe esas pantallas pondría en riesgo la APK probada. Por eso la migración debe ser incremental: conservar la APK nativa, extraer reglas y contratos de datos a un módulo compartido, crear un frontend PWA en este mismo árbol y migrar pantallas Android al frontend compartido cuando el puente de Media3/archivos/sesión esté probado. La excepción permanente serán los adaptadores de plataforma de archivos, video y almacenamiento protegido.

## Backend web y vinculación

Apps Script `ContentService` responde con redirección y no ofrece los encabezados CORS necesarios para que la PWA de GitHub Pages lea respuestas a sus POST JSON. No debe usarse `no-cors`, JSONP ni colocar credenciales oficiales en el navegador. Hace falta un puente web autenticado y comprobado antes de habilitar Cuenta BAT o Subir a BAT en PWA. La propuesta dentro del backend actual es una página HTML Service que se comunique de forma restringida con la PWA mediante `postMessage`, con origen exacto, nonce por solicitud y validación server-side. Debe probarse en Android Chrome y Safari/iOS antes del despliegue público.

La cuenta permitirá una instalación Android y **una** PWA vinculada, no un dispositivo ilimitado. La APK autenticada emitirá un código de vinculación aleatorio, breve y de un solo uso; la PWA lo presentará junto con las credenciales para asociar una instalación web generada aleatoriamente. El backend guardará hashes, validará rol/estado/cuota y permitirá a futuro desvincularla sin modificar la cuenta. No se usará fingerprinting.

## Fuente única y excepciones

El objetivo final es que editor, eventos, historial y cuenta utilicen componentes web compartidos dentro de la APK (WebView) y la PWA. Mientras se migra, la APK conserva sus pantallas nativas y la PWA se implementa en `web/`. Las reglas que se extraigan a `shared/` deben verificarse mediante pruebas de paridad. Esto es una transición; no debe afirmarse que la interfaz Java existente ya sea compartida.

## Riesgos que requieren validación real

- FFmpeg WebAssembly consume bastante memoria y puede no exportar videos largos en móviles con poca RAM. El exportador nativo Media3 se mantiene en Android.
- iOS/Safari no ofrece todas las APIs de Chrome Android; instalación, almacenamiento y exportación deben probarse en dispositivo real.
- El almacenamiento web es gestionado por el navegador y no ofrece la misma persistencia que un archivo guardado por Storage Access Framework. Se ofrecerá descarga local y se solicitará persistencia cuando exista.
- El endpoint Apps Script y la sesión resumable de Drive requieren pruebas de CORS reales desde el origen de GitHub Pages. Hasta pasar esas pruebas, no se debe mostrar una subida PWA como operativa.

## Pruebas de referencia ya pasadas

- `gradlew.bat assembleDebug --no-daemon`
- `node backend/tests/backend.test.cjs`
- `tests/MontageLogicTest.java` con JDK 17

El repositorio público `batclanoficial/bat` queda fuera de alcance. Si se publica este proyecto, excluir `backend/`, APKs, videos de prueba y configuraciones locales mediante `.gitignore`/auditoría de secretos. El workflow de GitHub Pages construirá solamente `web/` con base `/bat-montaje/`.
