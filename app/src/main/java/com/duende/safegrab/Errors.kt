package com.duende.safegrab

object Errors {
    fun friendly(m: String): String = when {
        m.contains("Unsupported URL", true) -> "Enlace no soportado."
        m.contains("No video could be found", true) || m.contains("no video", true) ->
            "No encontré video en este enlace. Si es una foto, usa «Buscar imágenes»."
        m.contains("429") || m.contains("too many", true) -> "Demasiados intentos seguidos. Espera un rato e inténtalo de nuevo."
        m.contains("space", true) -> "Sin espacio en el teléfono."
        m.contains("cancel", true) -> "Descarga cancelada."
        m.contains("Unable to resolve", true) || m.contains("timed out", true) || m.contains("network", true) ->
            "Sin conexión a internet."
        m.contains("private", true) -> "Contenido privado: inicia sesión con una cuenta que pueda verlo."
        else -> m.take(200).ifBlank { "Error desconocido" }
    }
}
