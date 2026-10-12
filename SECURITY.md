# Reglas del repositorio (para quien desarrolla)

Este repositorio nunca debe contener datos de ningún usuario, incluidos los de quien prueba la app.

- Nunca subir: llaves de firma (.jks/.keystore), cookies, capturas de pantalla con sesión, logs, volcados de errores ni archivos descargados en pruebas.
- Probar con enlaces **públicos** y una **cuenta secundaria**; nunca con la cuenta personal.
- No imprimir URLs, cookies ni credenciales en `Log.*`. Los mensajes de error van a la pantalla, no a archivos.
- Revisar `git diff` y `git status` antes de cada push. El `.gitignore` ya bloquea los casos comunes.
- Cada push pasa por un escaneo automático de secretos (gitleaks, ver `.github/workflows`).
- La llave de firma se guarda fuera del repositorio y con copia de respaldo: si se pierde, nadie podrá actualizar la app.
- Publicar el SHA-256 de cada APK en la release.
- Si algo sensible llega a subirse por error, cambia/revoca la credencial y limpia el historial; borrar el archivo en un commit nuevo no basta.
