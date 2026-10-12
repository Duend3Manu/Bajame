package com.duende.safegrab

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity

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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val s = vm.state
            when {
                vm.prefs.alias.isBlank() -> Onboarding(vm.prefs)
                vm.locked -> LockScreen(vm)
                s is UiState.Logging -> LoginScreen(s.host, onDone = { vm.loginDone(s.host, s.url, it) }, onCancel = vm::cancel)
                s is UiState.Choose -> Picker(vm, s.info)
                s is UiState.ImagePick -> ImagePicker(s.host, s.url, onDownload = vm::downloadImages, onClose = vm::cancel)
                vm.showSettings -> SettingsScreen(vm)
                else -> MainShell(vm, initialUrl)
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
        Text("Bienvenido a SafeGrab", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No necesitas cuenta, correo ni contraseña. Todo lo que hagas se queda en tu teléfono: " +
            "sin servidores, sin intermediarios, sin analíticas.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            name, { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("¿Cómo quieres que te llame?") }
        )
        Button(onClick = { prefs.updateAlias(name) }, enabled = name.isNotBlank()) { Text("Continuar") }
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
        Text("SafeGrab está bloqueado", style = MaterialTheme.typography.titleLarge)
        if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { ask() }) { Text("Desbloquear") }
    }
}

@Composable
fun MainShell(vm: MainViewModel, initialUrl: String) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("Inicio" to "🏠", "Imágenes" to "🖼️", "Videos" to "🎬", "Música" to "🎵")
    val bar = MaterialTheme.colorScheme.surfaceContainer
    val on = readable(bar)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            0 -> Home(vm, initialUrl, m)
            1 -> LibraryScreen(Kind.IMAGE, m)
            2 -> LibraryScreen(Kind.VIDEO, m)
            else -> LibraryScreen(Kind.AUDIO, m)
        }
    }
}

@Composable
fun Home(vm: MainViewModel, initialUrl: String, modifier: Modifier) {
    var url by remember { mutableStateOf(initialUrl) }
    val busy = vm.state is UiState.Downloading || vm.state is UiState.Analyzing
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current

    // Detecta un enlace copiado (solo cuando la app está al frente; Android lo exige). Nunca lo analiza solo.
    val focused = LocalWindowInfo.current.isWindowFocused
    var clip by remember { mutableStateOf<String?>(null) }
    var ignored by remember { mutableStateOf("") }
    LaunchedEffect(focused) {
        if (focused) {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            clip = Regex("https?://\\S+").find(t)?.value?.takeIf { it != url && it != ignored }
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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hola, ${vm.prefs.alias}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::openSettings) { Text("⚙️ Ajustes") }
        }
        Card {
            Text(
                "Modo privado: tu contraseña la escribes directo en el sitio, la app nunca la ve. " +
                "Sin servidores, sin intermediarios. La sesión es temporal y tú decides si se guarda o se borra.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
            )
        }
        clip?.let { c ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Enlace copiado: ${Cookies.hostOf(c) ?: c}")
                    Row {
                        TextButton(onClick = { url = c; clip = null; vm.start(c) }) { Text("Analizar") }
                        TextButton(onClick = { ignored = c; clip = null }) { Text("Ignorar") }
                    }
                }
            }
        }
        OutlinedTextField(
            url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Enlace de UN video, música o imagen") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start(url) }, enabled = !busy) { Text("Analizar enlace") }
            OutlinedButton(onClick = { vm.startImages(url) }, enabled = !busy) { Text("Buscar imágenes") }
        }

        when (val s = vm.state) {
            is UiState.Analyzing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Analizando calidades disponibles…")
            }
            is UiState.Downloading -> {
                LinearProgressIndicator(progress = s.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Text(s.line, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text("Puedes salir de la app: la descarga sigue en segundo plano.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::cancelDownload) { Text("Cancelar descarga") }
            }
            is UiState.NeedsLogin -> {
                Text(s.reason)
                Button(onClick = { vm.openLogin(s.host, s.url) }) { Text("Iniciar sesión (temporal)") }
            }
            is UiState.Done -> Text(s.message)
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
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(info.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2)
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.first, maxLines = 1) })
            }
        }
        val list = tabs[tab].second
        if (list.isEmpty()) Text("No hay opciones de este tipo para este enlace.", Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
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
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = vm::cancel, modifier = Modifier.padding(12.dp)) { Text("Cancelar") }
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
