package com.duende.safegrab

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONTokener

data class Found(val src: String, val w: Int, val h: Int)

// Lee las imágenes grandes de la página (y la mayor versión de cada srcset). Sin depender de clases CSS.
private const val JS = """(function(){var o={};
function add(u,w,h){if(!u||u.indexOf('data:')===0)return;try{u=new URL(u,location.href).href}catch(e){return}
if(!o[u]||o[u].w*o[u].h<w*h)o[u]={src:u,w:w,h:h}}
document.querySelectorAll('img').forEach(function(i){var w=i.naturalWidth,h=i.naturalHeight;
if(i.srcset){i.srcset.split(',').forEach(function(p){var a=p.trim().split(/\s+/);var wd=parseInt(a[1])||0;
if(wd>w){add(a[0],wd,Math.round(wd*h/(w||1)))}})}
if(w>=300&&h>=300)add(i.currentSrc||i.src,w,h)});
var og=document.querySelector('meta[property="og:image"]');if(og)add(og.content,0,0);
return JSON.stringify(Object.keys(o).map(function(k){return o[k]}))})()"""

private fun parse(raw: String?): List<Found> = runCatching {
    val s = JSONTokener(raw ?: "null").nextValue() as? String ?: return@runCatching emptyList<Found>()
    val a = JSONArray(s)
    (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Found(o.getString("src"), o.optInt("w"), o.optInt("h"))
    }.sortedByDescending { it.w * it.h }
}.getOrDefault(emptyList())

@Composable
fun ImagePicker(host: String, url: String, onDownload: (List<String>) -> Unit, onClose: () -> Unit) {
    SecureWindow()
    var wv by remember { mutableStateOf<WebView?>(null) }
    var found by remember { mutableStateOf(emptyList<Found>()) }
    var detected by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var shown by remember { mutableStateOf(host) }
    DisposableEffect(Unit) { onDispose { Cookies.wipeWebView() } }   // modo incógnito: al salir se borra todo

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(
            "🔒 $shown · navegador temporal. Si hace falta, inicia sesión aquí; al salir se borra todo.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
        )
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                Cookies.wipeWebView()
                SafeWebView.create(ctx, host) { shown = it }.also { wv = it; it.loadUrl(url) }
            }
        )
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                wv?.evaluateJavascript(JS) { r -> found = parse(r); detected = true; picked = emptySet() }
            }) { Text("Detectar imágenes") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cerrar") }
        }
        if (detected && found.isEmpty())
            Text("No encontré imágenes grandes. Desplázate por la página y detecta de nuevo.", Modifier.padding(12.dp))
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(found) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { picked = if (f.src in picked) picked - f.src else picked + f.src }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = f.src in picked, onCheckedChange = null)
                    Spacer(Modifier.width(10.dp))
                    Text((if (f.w > 0) "${f.w}×${f.h}" else "vista previa") + " · " + Uri.parse(f.src).host, maxLines = 1)
                }
            }
        }
        Button(
            onClick = { onDownload(picked.toList()) }, enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) { Text("Descargar seleccionadas (${picked.size})") }
    }
}
