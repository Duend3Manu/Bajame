package com.duende.safegrab

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Solo se usa si el usuario elige "Mantener sesión". Cifrado con Android Keystore, 100% local, caduca a los 7 días. */
class SessionStore(context: Context) {
    private val ttl = 7L * 24 * 3600_000
    private val prefs by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                context, "sessions",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.deleteSharedPreferences("sessions")
            EncryptedSharedPreferences.create(
                context, "sessions",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun save(host: String, cookies: String) =
        prefs.edit().putString("c:$host", cookies).putLong("t:$host", System.currentTimeMillis()).apply()

    fun get(host: String): String? {
        val t = prefs.getLong("t:$host", 0L)
        if (t == 0L) return null
        if (System.currentTimeMillis() - t > ttl) { clear(host); return null }
        return prefs.getString("c:$host", null)
    }

    fun clear(host: String) = prefs.edit().remove("c:$host").remove("t:$host").apply()
    fun clearAll() = prefs.edit().clear().apply()

    /** (sitio, fecha en que se guardó). Borra las caducadas. */
    fun list(): List<Pair<String, Long>> {
        val now = System.currentTimeMillis()
        return prefs.all.keys.filter { it.startsWith("t:") }.mapNotNull { k ->
            val host = k.removePrefix("t:")
            val t = prefs.getLong(k, 0L)
            if (now - t > ttl) { clear(host); null } else host to t
        }
    }
}
