# SafeGrab (esqueleto)

Abrir la carpeta en Android Studio (genera el Gradle wrapper solo) y ejecutar en un dispositivo real.

## Flujo
1. Pegas (o compartes desde la red social) el enlace de UN video/imagen.
2. Intenta descargar sin sesión. Si el contenido exige login -> WebView limpio (tipo incógnito).
3. Tomas la sesión (cookies) solo en RAM -> yt-dlp descarga -> se borra el archivo temporal de cookies.
4. Diálogo: "Mantener" (cifrada con Keystore) o "Cerrar y borrar".

## Selector de calidades (v0.2)
- `yt-dlp --dump-json` (getInfo) lista los formatos; `FormatParser` los agrupa en Video+audio / Solo video / Solo audio con peso.
- Pesos: reales si el sitio los informa; si no, estimados por bitrate x duración (se marcan con ≈).
- YouTube-style (video y audio separados): se une con ffmpeg a mp4/mkv. X/Instagram/Facebook suelen venir ya completos.
- Descarga en servicio en primer plano (no WorkManager: guardaría cookies en disco). START_NOT_STICKY.

## Pendiente / ideas
- Verificar la última versión de youtubedl-android y actualizar yt-dlp desde la app (YoutubeDL.updateYoutubeDL).
- Imágenes: yt-dlp casi no baja fotos; hará falta un módulo aparte (og:image / URL directa).
- Si cambian los nombres de campos de VideoFormat en la librería, ajustar FormatParser.
- Ícono y tema propios.

## v0.3 — Actualizaciones y repositorio seguro
- `Updater.kt`: actualiza yt-dlp (1 vez al día, o tras un fallo) y avisa de nuevas versiones de la app leyendo GitHub Releases. Interruptor en la pantalla de inicio. Cambia `REPO` en `Updater.kt` por el nombre real del repo.
- Verifica los nombres `YoutubeDL.UpdateChannel` / `updateYoutubeDL` contra la versión de la librería que uses.
- `PRIVACY.md`, `SECURITY.md`, `.gitignore` y escaneo de secretos (gitleaks) listos para el repositorio.
- Pendiente: nueva interfaz (alias, barra inferior de 4 pestañas, colores personalizables, detección del portapapeles). Ver el plan.

## v1.0.0 — Interfaz completa
- Alias al inicio, barra inferior (Inicio / Imágenes / Videos / Música), ajustes con tema claro/oscuro/auto y paleta HSV para fondo, barra y botones.
- Bloqueo biométrico (Lock.kt), pantalla de ajustes con sesiones guardadas (caducan a 7 días) y botón "Borrar todo".
- Imágenes: enlace directo (X con name=orig) o navegador temporal que detecta imágenes grandes (ImagePicker.kt).
- Botón cancelar, mensajes de error en español, detección del portapapeles, WebView restringido por dominio y FLAG_SECURE.
- No incluido aún: selector de carpeta de destino, modo "tocar la imagen", rules.json firmado, R8/firma de release.
