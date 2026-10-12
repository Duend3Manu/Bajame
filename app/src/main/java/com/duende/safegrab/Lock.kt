package com.duende.safegrab

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Bloqueo con huella/rostro/PIN. Lo verifica Android: la app solo recibe "sí" o "no". */
object Lock {
    private fun auth() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun available(ctx: Context) =
        BiometricManager.from(ctx).canAuthenticate(auth()) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(act: FragmentActivity, onOk: () -> Unit, onFail: (String) -> Unit) {
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onFail(msg.toString())
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear SafeGrab")
            .setAllowedAuthenticators(auth())
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancelar")
        prompt.authenticate(info.build())
    }
}
