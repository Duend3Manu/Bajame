package com.duende.safegrab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun ColorPickerRow(label: String, current: Int, default: Color, onChange: (Int) -> Unit) {
    val start = if (current != 0) Color(current) else default
    val key = current == 0
    val init = remember(key) { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) } }
    var h by remember(key) { mutableFloatStateOf(init[0]) }
    var s by remember(key) { mutableFloatStateOf(init[1]) }
    var v by remember(key) { mutableFloatStateOf(init[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(label)
        }
        Slider(h, { h = it; emit() }, valueRange = 0f..360f)   // tono
        Slider(s, { s = it; emit() })                          // intensidad
        Slider(v, { v = it; emit() })                          // brillo
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    SecureWindow()
    val ctx = LocalContext.current
    val p = vm.prefs
    val sc = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::closeSettings) { Text("Cerrar") }
        }

        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(p.themeMode == 0, { p.setTheme(0) }, { Text("Automático") })
            FilterChip(p.themeMode == 1, { p.setTheme(1) }, { Text("Claro") })
            FilterChip(p.themeMode == 2, { p.setTheme(2) }, { Text("Oscuro") })
        }
        ColorPickerRow("Color de fondo", p.bgColor, sc.background) { p.setColors(it, p.barColor, p.btnColor) }
        ColorPickerRow("Color de la barra", p.barColor, sc.surfaceContainer) { p.setColors(p.bgColor, it, p.btnColor) }
        ColorPickerRow("Color de los botones", p.btnColor, sc.primary) { p.setColors(p.bgColor, p.barColor, it) }
        TextButton(onClick = { p.resetColors() }) { Text("Restaurar colores por defecto") }

        HorizontalDivider()
        Text("Seguridad", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = p.lockEnabled, onCheckedChange = { vm.setLock(ctx, it) })
            Spacer(Modifier.width(10.dp))
            Text("Bloquear la app con huella, rostro o PIN", style = MaterialTheme.typography.bodySmall)
        }
        vm.lockMessage?.let { Text(it, color = sc.error, style = MaterialTheme.typography.bodySmall) }
        Text("Android verifica tu huella o rostro; la app nunca los recibe ni los guarda.", style = MaterialTheme.typography.bodySmall)

        Text("Sesiones guardadas (caducan a los 7 días)", style = MaterialTheme.typography.titleSmall)
        if (vm.sessions.isEmpty()) Text("Ninguna.", style = MaterialTheme.typography.bodySmall)
        vm.sessions.forEach { (host, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$host · ${DateFormat.getDateInstance().format(Date(t))}", Modifier.weight(1f))
                TextButton(onClick = { vm.deleteSession(host) }) { Text("Borrar") }
            }
        }
        Button(
            onClick = vm::panic,
            colors = ButtonDefaults.buttonColors(containerColor = sc.error, contentColor = sc.onError)
        ) { Text("Borrar todo (sesiones y archivos temporales)") }

        HorizontalDivider()
        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.autoUpdates, onCheckedChange = vm::setAuto)
            Spacer(Modifier.width(10.dp))
            Text("Buscar automáticamente (solo consulta GitHub, sin enviar datos tuyos)", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { vm.checkUpdates(true) }) { Text("Buscar actualizaciones ahora") }

        HorizontalDivider()
        Text(
            "Privacidad: todo queda en tu teléfono. Sin cuentas, sin correo, sin analíticas ni servidores. " +
            "Tu contraseña la escribes directo en el sitio y la app nunca la ve. Lee PRIVACY.md en el repositorio.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("SafeGrab ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}
