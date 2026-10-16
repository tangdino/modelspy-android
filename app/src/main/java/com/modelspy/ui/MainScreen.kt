package com.modelspy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelspy.data.ModelScanResult
import com.modelspy.data.ProbeResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    endpoint: String,
    apiKey: String,
    concurrency: Int,
    running: Boolean,
    progress: String,
    results: List<ModelScanResult>,
    error: String?,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (ModelScanResult) -> Unit,
) {
    var showDetail by remember { mutableStateOf<ModelScanResult?>(null) }
    showDetail?.let { r ->
        DetailDialog(r) { showDetail = null }
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("API Model Spy") })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(value = endpoint, onValueChange = onEndpointChange,
                label = { Text("Endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("https://host/v1") })
            OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange,
                label = { Text("API Key") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("并发数: $concurrency")
                Text("建议 8 以避开网关限流", fontSize = 12.sp)
            }
            Slider(value = concurrency.toFloat(), onValueChange = { onConcurrencyChange(it.toInt()) },
                valueRange = 1f..24f, steps = 22, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(onClick = onStart, enabled = !running) { Text("开始全量扫描") }
                if (running) Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) { Text("停止") }
            }
            if (running) {
                CircularProgressIndicator(Modifier.padding(top = 12.dp))
                Text(progress, Modifier.padding(top = 8.dp), fontSize = 13.sp)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
                items(results) { r ->
                    ResultCard(r) { showDetail = r }
                }
            }
        }
    }
}

@Composable
fun ResultCard(r: ModelScanResult, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(r.modelParam, fontWeight = FontWeight.Bold)
            Text("真实: ${r.detected}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            if (r.probability > 0) Text("置信度: ${"%.2f".format(r.probability * 100)}%", fontSize = 12.sp)
            if (r.top3.isNotEmpty()) Text("候选: ${r.top3.joinToString("  ") { "${it.first} ${"%.3f".format(it.second / 1000.0)}" }}",
                fontSize = 11.sp, maxLines = 2)
            if (r.errors > 0) Text("样本不足: 缺 ${r.errors}/3", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DetailDialog(r: ModelScanResult, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) { }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("${r.modelParam} → ${r.detected}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Provider ${r.provider} · Tier ${r.tier} · Score ${r.score}", fontSize = 13.sp)
        androidx.compose.foundation.lazy.LazyColumn {
            items(r.probes) { p -> ProbeRow(p) }
        }
    }
}

@Composable
fun ProbeRow(p: ProbeResult) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (p.error != null) Text("ERROR: ${p.error}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            else {
                Text("延迟 ${p.latencySeconds}s · 思考 ${p.reasoningTokens ?: 0} tokens", fontSize = 11.sp)
                Text((p.response ?: "").take(500), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
