package com.duende.safegrab

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import coil.compose.rememberAsyncImagePainter
import java.io.File

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { Regex("https?://\\S+").find(it)?.value }.orEmpty()
        setContent { App(vm, shared) }
    }

    override fun onStop() { super.onStop(); vm.onBackgrounded() }
    override fun onStart() { super.onStart(); vm.onForegrounded() }
}

@Composable
fun App(vm: MainViewModel, initialUrl: String) {
    SafeGrabTheme(vm.prefs) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize()) {
                vm.prefs.customBgPath?.let { bgPath ->
                    val bgFile = File(bgPath)
                    if (bgFile.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(bgFile),
                            contentDescription = "Fondo personalizado",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    }
                }

                Surface(
                    Modifier.fillMaxSize(),
                    color = if (vm.prefs.customBgPath != null) Color.Transparent else MaterialTheme.colorScheme.background
                ) {
                    val s = vm.state
                    when {
                        vm.prefs.alias.isBlank() -> Onboarding(vm.prefs)
                        vm.locked -> LockScreen(vm)
                        s is UiState.Logging -> LoginScreen(s.host, onDone = { vm.loginDone(s.host, s.url, it) }, onCancel = vm::cancel)
                        s is UiState.Choose -> Picker(vm, s.info)
                        s is UiState.ImagePick -> ImagePicker(s.host, s.url, onDownload = vm::downloadImages, onClose = vm::cancel)
                        else -> MainShell(vm, initialUrl)
                    }
                }
            }
        }
    }
}

@Composable
fun Onboarding(prefs: Prefs) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Bienvenido a Bajame", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No necesitas cuenta, correo ni contraseña. Todo lo que hagas se queda en tu teléfono: " +
            "sin servidores, sin intermediarios, sin analíticas.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            name, { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            label = { Text("¿Cómo quieres que te llame?") }
        )
        Button(
            onClick = { prefs.updateAlias(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Continuar", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun LockScreen(vm: MainViewModel) {
    SecureWindow()
    val ctx = LocalContext.current as? FragmentActivity ?: return
    var msg by remember { mutableStateOf("") }
    fun ask() = Lock.prompt(ctx, onOk = vm::unlock, onFail = { msg = it })
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Text("Bajame está bloqueado", style = MaterialTheme.typography.titleLarge)
        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { ask() }, shape = RoundedCornerShape(16.dp)) { Text("Desbloquear") }
    }
}

@Composable
fun MainShell(vm: MainViewModel, initialUrl: String) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("Inicio" to "🏠", "Bóveda" to "🔒", "Ajustes" to "⚙️")
    val bar = MaterialTheme.colorScheme.surfaceContainer
    val on = readable(bar)
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = bar) {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = on, unselectedTextColor = on.copy(alpha = 0.7f),
                            indicatorColor = on.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> Home(vm, initialUrl, m, onOpenSettings = { tab = 2 })
            1 -> VaultScreen(m)
            else -> SettingsScreen(vm)
        }
    }
}

@Composable
fun Home(vm: MainViewModel, initialUrl: String, modifier: Modifier, onOpenSettings: () -> Unit) {
    var urlInput by remember { mutableStateOf(initialUrl) }
    val busy = vm.state is UiState.Downloading || vm.state is UiState.Analyzing
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    val focused = LocalWindowInfo.current.isWindowFocused
    var clip by remember { mutableStateOf<String?>(null) }
    var ignored by remember { mutableStateOf("") }
    LaunchedEffect(focused) {
        if (focused) {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            clip = Regex("https?://\\S+").find(t)?.value?.takeIf { it != urlInput && it != ignored }
        }
    }

    vm.appUpdate?.let { u ->
        AlertDialog(
            onDismissRequest = vm::dismissUpdate,
            title = { Text("Nueva versión ${u.version}") },
            text = { Text(u.notes.ifBlank { "Hay una nueva versión en GitHub." }) },
            confirmButton = { TextButton(onClick = { uri.openUri(u.pageUrl); vm.dismissUpdate() }) { Text("Ver en GitHub") } },
            dismissButton = { TextButton(onClick = vm::dismissUpdate) { Text("Más tarde") } }
        )
    }
    (vm.state as? UiState.AskKeep)?.let { s ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("¿Mantener tu sesión en ${s.host}?") },
            text = { Text("Puedes cerrarla ahora y se borra del teléfono, o guardarla cifrada solo aquí (caduca a los 7 días).") },
            confirmButton = { TextButton(onClick = { vm.keepSession(s.host) }) { Text("Mantener") } },
            dismissButton = { TextButton(onClick = { vm.closeSession(s.host) }) { Text("Cerrar y borrar") } }
        )
    }
    (vm.state as? UiState.AuthPrompt)?.let { s ->
        AlertDialog(
            onDismissRequest = vm::cancel,
            title = { Text("Inicio de sesión requerido") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { vm.openLogin(s.host, s.url) }) {
                    Text("Iniciar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancel) {
                    Text("No en este momento")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bajame",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Hola, ${vm.prefs.alias.ifBlank { "viajero" }} 👋",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pega un enlace de video, música o imagen",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        clip?.let { c ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("📋 Enlace detectado en portapapeles", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(Cookies.hostOf(c) ?: c, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { urlInput = c; clip = null; vm.start(c) }) { Text("Analizar") }
                        TextButton(onClick = { ignored = c; clip = null }) { Text("Ignorar") }
                    }
                }
            }
        }

        // --- Campo de Texto ---
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    clipboardManager.getText()?.text?.let { clipboardText ->
                        urlInput = clipboardText
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Pegar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- Botones de Acción ---
        Button(
            onClick = {
                if (urlInput.isNotBlank()) {
                    vm.start(urlInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = urlInput.isNotBlank() && !busy
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Analizar Enlace", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                if (urlInput.isNotBlank()) {
                    vm.startImages(urlInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = urlInput.isNotBlank() && !busy
        ) {
            Text(text = "Buscar Imágenes (Navegador Oculto)", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Estado actual ---
        when (val s = vm.state) {
            is UiState.Analyzing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("Analizando calidades disponibles…")
            }
            is UiState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text(s.line, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text("Puedes salir de la app: la descarga sigue en segundo plano.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = vm::cancelDownload) { Text("Cancelar descarga") }
            }
            is UiState.NeedsLogin -> {
                Text(s.reason, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { vm.openLogin(s.host, s.url) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Iniciar sesión (temporal)")
                }
            }
            is UiState.Done -> Text(s.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

/** Lista de calidades con el peso de cada opción. */
@Composable
fun Picker(vm: MainViewModel, info: MediaInfo) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Video + audio" to info.both,
        "Solo video" to info.videoOnly,
        "Solo audio" to info.audio
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Text(info.title, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2)
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.first, maxLines = 1) })
            }
        }
        val list = tabs[tab].second
        if (list.isEmpty()) Text("No hay opciones de este tipo para este enlace.", Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(list) { o ->
                ListItem(
                    headlineContent = { Text(o.label) },
                    trailingContent = { Text(o.sizeText) },
                    modifier = Modifier.clickable { vm.choose(o, info.title) }
                )
                HorizontalDivider()
            }
        }
        Text(
            "≈ indica un peso estimado. El MP3 se convierte en tu teléfono.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = vm::cancel,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) { Text("Cancelar") }
    }
}

@Composable
fun LoginScreen(host: String, onDone: (String?) -> Unit, onCancel: () -> Unit) {
    SecureWindow()
    var shown by remember { mutableStateOf(host) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(
            "🔒 $shown · Inicia sesión aquí. La app no ve ni guarda tu contraseña; solo toma la sesión temporal.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
        )
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                Cookies.wipeWebView()   // arranca limpio, como incógnito
                SafeWebView.create(ctx, host) { shown = it }.also { it.loadUrl(Cookies.loginUrl(host)) }
            }
        )
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onDone(android.webkit.CookieManager.getInstance().getCookie("https://$host")) }) {
                Text("Ya inicié sesión, continuar")
            }
        }
    }
}
