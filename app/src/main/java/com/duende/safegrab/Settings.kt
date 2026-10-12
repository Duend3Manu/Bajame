package com.duende.safegrab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.atan2

@Composable
fun ColorWheelPicker(onColorSelected: (Color) -> Unit) {
    val rainbowColors = listOf(
        Color.Red, Color.Magenta, Color.Blue,
        Color.Cyan, Color.Green, Color.Yellow, Color.Red
    )
    var selectedColor by remember { mutableStateOf(Color.Red) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(selectedColor)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        val newColor = Color.hsv(angle, 1f, 1f)
                        selectedColor = newColor
                        onColorSelected(newColor)
                    }
                }
        ) {
            drawCircle(brush = Brush.sweepGradient(rainbowColors))
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    SecureWindow()
    val ctx = LocalContext.current
    val p = vm.prefs
    val sc = MaterialTheme.colorScheme

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val privateFile = File(ctx.filesDir, "custom_bg.jpg")
            ctx.contentResolver.openInputStream(it)?.use { input ->
                privateFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            p.setCustomBg(privateFile.absolutePath)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajustes y Personalización", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }

        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(p.themeMode == 0, { p.setTheme(0) }, { Text("Automático") })
            FilterChip(p.themeMode == 1, { p.setTheme(1) }, { Text("Claro") })
            FilterChip(p.themeMode == 2, { p.setTheme(2) }, { Text("Oscuro") })
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Fondo de Pantalla Personalizado", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Elegir imagen de galería")
            }
            if (p.customBgPath != null) {
                OutlinedButton(
                    onClick = { p.setCustomBg(null) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Quitar fondo")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Color de las letras", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { p.updateTextColor(android.graphics.Color.WHITE) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Blanco", color = Color.Black) }

            Button(
                onClick = { p.updateTextColor(android.graphics.Color.parseColor("#FFD700")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Amarillo", color = Color.Black) }

            Button(
                onClick = { p.updateTextColor(android.graphics.Color.BLACK) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Negro", color = Color.White) }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Selector de Color Principal (Círculo Cromático)", style = MaterialTheme.typography.titleSmall)
        ColorWheelPicker { newColor ->
            p.setColors(p.bgColor, p.barColor, newColor.toArgb())
        }
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
            colors = ButtonDefaults.buttonColors(containerColor = sc.error, contentColor = sc.onError),
            shape = RoundedCornerShape(12.dp)
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
        Text("Bajame ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}
