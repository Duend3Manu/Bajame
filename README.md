# Bajame 🎬🔒

**Bajame** es una aplicación moderna para Android desarrollada con **Jetpack Compose** y **Material 3** que permite descargar videos, música e imágenes de redes sociales (YouTube, X/Twitter, Instagram, TikTok) de forma **100% privada, sin servidores intermediarios, sin cuentas y sin analíticas**.

---

## 🌟 Características Principales

- **Privacidad Extrema**: Todo ocurre en tu dispositivo. Las cookies y sesiones son temporales en RAM y se borran inmediatamente tras finalizar la tarea.
- **Bóveda Privada (Vault)**: Las descargas se almacenan en el almacenamiento privado de la app protegido con un archivo `.nomedia`, haciéndolas completamente invisibles para la galería del teléfono, WhatsApp, Telegram u otras aplicaciones.
- **Reproductor Interno Privado (Media3 ExoPlayer)**: Reproduce tus videos y audios descargados directamente dentro de la app sin generar caché en el sistema ni dejar registros en historiales externos. Incluye protección contra capturas de pantalla (`FLAG_SECURE`).
- **Navegador Incógnito Integrado (WebView)**: Para contenido protegido o privado (como Reels de Instagram o posts de X), puedes iniciar sesión de forma segura. La app nunca ve ni almacena tus contraseñas.
- **Selector Inteligente de Calidades**: Agrupa automáticamente las opciones en **Video + Audio** (unidas mediante `ffmpeg`), **Solo Video** y **Solo Audio (Conversión a MP3)** con cálculo de peso estimado (`≈`).
- **Bloqueo Biométrico**: Protege el acceso a la app mediante huella dactilar, reconocimiento facial o PIN del dispositivo.
- **Personalización Completa**: Tema automático, claro u oscuro y selector de paleta de colores HSV personalizado.

---

## 🛠️ Tecnologías y Arquitectura

- **Kotlin** & **Coroutines / StateFlow**
- **Jetpack Compose** & **Material 3**
- **Jetpack Media3 (ExoPlayer)** para reproducción multimedia local
- **youtubedl-android (`yt-dlp` + `ffmpeg`)** para la extracción y procesamiento de medios en el dispositivo
- **AndroidX Security Crypto** (`EncryptedSharedPreferences`) para sesiones opcionales cifradas

---

## 📥 Compilación y Ejecución

1. Clona el repositorio o descarga el código fuente:
   ```bash
   git clone https://github.com/Duend3Manu/Bajame.git
   ```
2. Abre la carpeta del proyecto en **Android Studio** (Iguana, Jellyfish, Ladybug, Meerkat o superior).
3. Asegúrate de contar con **Gradle 8.13** y **Java 17/21**.
4. Conecta un dispositivo físico o emulador Android (API 29+) y ejecuta la compilación (`Build` -> `Build Bundle(s) / APK(s)` o ejecuta `app:assembleDebug`).

---

## 🤝 Privacidad y Repositorio

- Consulta los detalles de privacidad en [PRIVACY.md](PRIVACY.md).
- Repositorio oficial en GitHub: [https://github.com/Duend3Manu/Bajame](https://github.com/Duend3Manu/Bajame).
