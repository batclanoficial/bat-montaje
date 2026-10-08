# Estado de ampliación BAT Montaje APK

Actualizado: 2026-10-07. Cuenta propietaria: `bat.clan.oficial@gmail.com`.
La [hoja privada](https://docs.google.com/spreadsheets/d/1MXr85_BlpnHXOvXfpzaHGR5ieYCyfDZ5GaU9MkSv-8I/edit),
el [Apps Script](https://script.google.com/home/projects/1y_8YvFI2N0QET2JvmOSNuOCyOxkwIttYIzpLySP1lV8T-htWUfZRFTB_/edit)
y la [carpeta de Drive](https://drive.google.com/drive/folders/1k3xBe0APFVAGcdW2vYYvr8TUJkZcpr9O)
ya existen. La implementación web activa es la versión 17; se conservó su URL. La versión 16 añadió el puente de acceso para la PWA con origen restringido, sin publicar la hoja ni los secretos; la versión 17 permite autorizar una instalación PWA nueva mediante un código temporal enviado al correo, sin exigir Android.

| Etapa | Estado | Verificación pendiente |
| --- | --- | --- |
| A: inspección | Hecha; editor local y arquitectura preservados | Ninguna del inventario. |
| B: Sheet/Apps Script | Hoja privada y backend real configurados; versión 16 mantiene la asignación del menor ID libre desde 1 y el correo BAT renovado | Vigilar futuras publicaciones. |
| C: registro/email | Verificado con registro sintético, email recibido y estado PENDIENTE | Comprobar entrega con buzones de administradores reales. |
| D: aprobación/ID | Aprobación y rechazo reales comprobados; ID 11 asignado en prueba y liberado | Probar aprobaciones concurrentes reales. |
| E: sesión/validación | Activación y validación reales comprobadas; token revocado al cerrar prueba | Probar en teléfono físico y recuperación sin conexión. |
| F: roles/cuotas | Cambios de límite y estados SUSPENDED/BANEADO reflejados; límite alcanzado y dos reservas simultáneas comprobados contra Google real | Pruebas de carga más amplias. |
| G: Drive | Raíz y cuatro carpetas privadas creadas; IDs en Config | Ninguna de estructura. |
| H: subida resumable | MP4 sintético subido a Drive y verificado como NEW; cancelación y cuota comprobadas; sin OAuth oficial en APK | Pérdida de red y reanudación en teléfono real; archivos grandes. |
| I: historial | Pantallas y cliente compilados; fila NEW/REJECTED confirmada en Sheet | Verificar la visualización en teléfono con cuenta real. |
| J: pruebas | Backend simulado, cuota de fecha de Sheets corregida, subida real pequeña y dos solicitudes simultáneas verificadas | Teléfonos reales, códecs, red intermitente y carga mayor. |

El 30 de septiembre se corrigió el guardado cifrado de la solicitud en Android:
Keystore ahora genera el IV de AES-GCM. Se reprodujo el error de la APK anterior
en el emulador y se confirmó que la APK corregida guarda la solicitud y la
recupera tras reiniciar la app. La prueba se hizo sin conexión para no crear
otro registro de prueba en el backend; falta confirmar el envío desde un
teléfono físico con esta compilación.

Los registros sintéticos de las pruebas iniciales quedaron `REJECTED` con nota de auditoría; sus tokens
se revocaron y los hashes de aprobación se limpiaron. El ajuste obsoleto
`next_member_number` se retiró de Config al eliminar la antigua reserva 1–10.
El MP4 sintético de 3663 bytes fue movido manualmente de `Nuevos`
a `No Aprobados` y etiquetado `PRUEBA_TECNICA`; no se borró. La fila de envío
se marcó `REJECTED`. No se borraron filas ni archivos del usuario.
Las filas sintéticas de `Uploads` conservan su auditoría pero no su
`internal_id`, para que un futuro usuario real que reutilice ese ID no herede
historial ni cuota de estas pruebas.

La APK original `dist/BAT Montaje.apk` **no fue reemplazada**. La nueva
`dist/BAT Montaje - nube beta.apk` es una compilación de depuración para
validación interna, no una publicación estable. Ambas tienen la misma firma
debug; antes de distribución duradera hay que definir una clave de firma
privada y conservarla fuera del repositorio.

No están implementadas las funciones FUTURAS: preset oficial, administración
en la APK, publicación automática, limpieza automática o iOS.
