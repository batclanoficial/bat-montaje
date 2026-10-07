# BAT Montaje para Android

Aplicación Android vertical e independiente del proyecto Windows. Conserva el logo y los colores BAT, y está completamente en español. No incluye detección automática.

## Funciones

- Seleccionar un vídeo desde los archivos del teléfono y verlo con audio y controles debajo de la imagen.
- Añadir eventos manuales `KILL`, `CLUTCH`, `COMBATE` y `OTRO` con tiempos `M:SS`, `MM:SS` o `HH:MM:SS`. `COMBATE` utiliza inicio y fin; `OTRO` requiere un nombre personalizado. Al escribir solo números, los dos últimos son segundos: `224` → `2:24`.
- Ajustar segundos antes y después para todos los eventos nuevos, aplicar los valores a todos los existentes o editar cada evento por separado.
- Fusionar recortes solapados o separados por menos de 0,35 segundos, y exportar los clips en orden a un MP4. La app pide una ubicación de guardado al terminar.
- Abrir el MP4 guardado en un reproductor del dispositivo.
- Eliminar individualmente un montaje local desde Historial, con confirmación. Algunos archivos de versiones anteriores pueden requerir volver a seleccionarlos para obtener permiso de eliminación.

El teléfono no necesita Python, FFmpeg, FFprobe, Tesseract ni VLC. La reproducción y exportación usan AndroidX Media3. La codificación H.264/AAC depende de las capacidades multimedia del dispositivo.

## Ampliación de cuenta y nube beta

La APK beta añade registro, validación de cuenta, historial local/nube y
subidas resumables a Drive sin credenciales de Google en el cliente. El
servidor oficial y las carpetas privadas ya están configurados. Registro,
correo, aprobación, rechazo y cambios de estado/límite se probaron con Google
real. Una subida resumable de un MP4 sintético pequeño llegó a Drive y se
verificó; la subida desde un teléfono todavía requiere validación.
El guardado de la solicitud con Android Keystore se corrigió y se verificó en
emulador; la APK anterior fallaba al registrar en algunos dispositivos.
El editor local permanece disponible cuando el servicio BAT no responde al
abrir la aplicación. La pantalla principal lleva a HISTORIAL: cinco montajes
locales recientes (o búsqueda por nombre en todo el historial local) y cinco
envíos recientes. La cuenta permanece vinculada a un solo dispositivo y «Salir
de la cuenta» revoca la sesión. Consulta
[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) y
[backend/README.md](backend/README.md) antes de distribuirla. No se
implementaron ANALIZAR, administración dentro de la APK,
publicación automática ni el futuro BAT Official Preset.

## Instalación

El archivo `dist/BAT Montaje.apk` conserva la versión local distribuida
anteriormente. La compilación actual está en `dist/BAT Montaje - nube beta v1.3.apk`
y `app/build/outputs/apk/debug/app-debug.apk`. Todas usan la misma clave de depuración
de Android. Pueden instalarse manualmente en Android 6.0 (API 23) o posterior,
pero para publicación y actualizaciones mantenibles hace falta una clave de
firma propia y estable, guardada fuera del repositorio.

## Compilación

Requiere Android Studio/SDK con plataforma Android 36 y un JDK compatible con el plugin de Android. Desde esta carpeta:

```powershell
.\gradlew.bat assembleDebug
```

Salida de Gradle: `app/build/outputs/apk/debug/app-debug.apk`.

Para comprobar las reglas de tiempos y fusión de clips sin Android:

```powershell
javac -d build/test-classes app/src/main/java/com/batclan/montaje/MontageLogic.java tests/MontageLogicTest.java
java -cp build/test-classes com.batclan.montaje.MontageLogicTest
```

## Alcance de las pruebas

Probado en un emulador Android 16/API 36 con vídeo de muestra H.264/AAC: selección, reproducción con controles externos, COMBATE, OTRO con nombre obligatorio, edición de tipos, fusión de clips, exportación y guardado. Se eliminó el montaje de prueba desde Historial y se comprobó que otro montaje permaneció intacto. No se ha probado todavía en un teléfono físico ni con todos los formatos/códecs posibles de Rainbow Six Mobile.
