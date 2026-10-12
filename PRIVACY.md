# Privacidad de SafeGrab

SafeGrab funciona 100% en tu teléfono. No tiene servidor propio, cuentas, registro, correo ni analíticas.

## Qué se guarda (solo en tu teléfono)
- Tu alias (para saludarte) y tus preferencias de colores y tema.
- Lo que descargas, en la carpeta Descargas/SafeGrab.
- Una sesión de una red social **solo si tú eliges "Mantener"**, cifrada con el Keystore de Android y con caducidad.

## Qué NO se guarda nunca
- Tu contraseña: la escribes directo en el sitio de la red social; la app nunca la ve.
- Cookies temporales: viven en memoria durante la descarga y el archivo temporal se borra al terminar.
- Historial de enlaces, correos, identificadores ni datos de uso.

## Con quién se conecta la app
1. La red social desde la que descargas (ella ve tu IP y lo que pides, como al usar su sitio).
2. GitHub, solo para buscar versiones nuevas del motor y de la app. No se envía ningún dato tuyo. Se puede desactivar en la pantalla de inicio.

## Cómo comprobarlo
- El código es abierto: puedes leerlo y compilarlo tú mismo.
- Cada APK publicado trae su SHA-256 en la descripción de la release; compáralo antes de instalar.
- Puedes borrar todo cuando quieras con "Cerrar y borrar todas las sesiones" o desinstalando la app.

## Lo que no podemos controlar
Las redes sociales y GitHub tienen sus propias políticas. Un teléfono con root o con malware puede exponer cualquier app.

## Bloqueo con huella, rostro o PIN (opcional)
Lo verifica Android. SafeGrab solo recibe "sí" o "no": nunca ve, recibe ni guarda tu huella, tu rostro ni tu PIN.

## Navegador temporal de inicio de sesión
Es un WebView dentro de la app que solo abre los dominios de la red elegida, sin puentes de JavaScript y sin permitir capturas de pantalla. Al salir se borran sus cookies y datos.
