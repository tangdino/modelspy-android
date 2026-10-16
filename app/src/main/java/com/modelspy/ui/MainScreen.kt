package com.modelspy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.modelspy.data.ModelScanResult
import com.modelspy.data.ProbeResult
import com.modelspy.BlacklistEntry
import com.modelspy.ConfigStore
import com.modelspy.SavedConfig
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete

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
    allModels: List<String>,
    selectedModels: List<String>,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onFetchModels: () -> Unit,
    onToggleModel: (String) -> Unit,
    onToggleAll: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (ModelScanResult) -> Unit,
    onToggleLang: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateMessage: String?,
    onDismissUpdate: () -> Unit,
    themeMode: String,
    onToggleTheme: () -> Unit,
    blacklist: List<BlacklistEntry>,
    blacklistLoading: Boolean,
    onOpenBlacklist: () -> Unit,
    savedConfigs: List<SavedConfig>,
    onSaveConfig: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onDeleteConfig: (SavedConfig) -> Unit,
) {
    var showDetail by remember { mutableStateOf<ModelScanResult?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showBlacklist by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showConfigMenu by remember { mutableStateOf(false) }

    showDetail?.let { r ->
        DetailDialog(r) { showDetail = null }
        return
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(AppStrings.get("about_title")) },
            text = { Text(AppStrings.get("about_body"), fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(AppStrings.get("ok")) } }
        )
    }
    if (showBlacklist) {
        AlertDialog(
            onDismissRequest = { showBlacklist = false },
            title = { Text(AppStrings.get("blacklist"), fontWeight = FontWeight.Bold) },
            text = {
                when {
                    blacklistLoading -> Text(AppStrings.get("blacklist_loading"), fontSize = 13.sp)
                    blacklist.isEmpty() -> Text(AppStrings.get("blacklist_empty"), fontSize = 13.sp)
                    else -> androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(blacklist) { e ->
                            BlacklistCard(e)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlacklist = false }) { Text(AppStrings.get("ok")) } }
        )
    }
    updateMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text(AppStrings.get("check_update")) },
            text = { Text(msg, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = onDismissUpdate) { Text(AppStrings.get("ok")) } }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(AppStrings.get("title")) },
            actions = {
                // 语言切换：短文本
                TextButton(onClick = onToggleLang) {
                    Text(if (AppStrings.lang == "zh") "EN" else "中")
                }
                // 主题切换：短文本（用图标符号缩短）
                TextButton(onClick = onToggleTheme) {
                    Text(when (themeMode) {
                        "light" -> "☀"
                        "dark" -> "☾"
                        else -> "◐"
                    })
                }
                // 更多菜单：收纳黑名单/关于/更新
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(AppStrings.get("blacklist")) },
                        onClick = { showMenu = false; showBlacklist = true; onOpenBlacklist() }
                    )
                    DropdownMenuItem(
                        text = { Text(AppStrings.get("about")) },
                        onClick = { showMenu = false; showAbout = true }
                    )
                    DropdownMenuItem(
                        text = { Text(AppStrings.get("check_update")) },
                        onClick = { showMenu = false; onCheckUpdate() }
                    )
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(value = endpoint, onValueChange = onEndpointChange,
                label = { Text(AppStrings.get("endpoint")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(AppStrings.get("endpoint_hint")) })
            OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange,
                label = { Text(AppStrings.get("apikey")) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showConfigMenu = true }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "saved")
                    }
                })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSaveConfig) { Text(AppStrings.get("save_config")) }
                if (savedConfigs.isNotEmpty()) {
                    TextButton(onClick = { showConfigMenu = true }) {
                        Text("${AppStrings.get("saved_configs")} (${savedConfigs.size})")
                    }
                }
            }
            DropdownMenu(expanded = showConfigMenu, onDismissRequest = { showConfigMenu = false }) {
                if (savedConfigs.isEmpty()) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(AppStrings.get("blacklist_empty"), fontSize = 13.sp) },
                        onClick = { showConfigMenu = false }
                    )
                } else {
                    savedConfigs.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelectConfig(c); showConfigMenu = false }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.endpoint, fontSize = 13.sp, maxLines = 1)
                                Text(ConfigStore.maskKey(c.key), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = { onDeleteConfig(c) }) {
                                Icon(Icons.Filled.Delete, contentDescription = AppStrings.get("delete"),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${AppStrings.get("concurrency")}: $concurrency")
                Text(AppStrings.get("concurrency_hint"), fontSize = 12.sp)
            }
            Slider(value = concurrency.toFloat(), onValueChange = { onConcurrencyChange(it.toInt()) },
                valueRange = 1f..24f, steps = 22, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFetchModels, enabled = !running) { Text(AppStrings.get("fetch_models")) }
                Button(onClick = onStart, enabled = !running) { Text(AppStrings.get("start")) }
                if (running) Button(onClick = onCancel) { Text(AppStrings.get("stop")) }
            }

            if (running) {
                CircularProgressIndicator(Modifier.padding(top = 12.dp))
                Text(progress, Modifier.padding(top = 8.dp), fontSize = 13.sp)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

            if (allModels.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(AppStrings.get("select_all"), fontWeight = FontWeight.Bold)
                    Text("${AppStrings.get("selected")}: ${selectedModels.size}/${allModels.size}", fontSize = 12.sp)
                    Checkbox(
                        checked = selectedModels.size == allModels.size && allModels.isNotEmpty(),
                        onCheckedChange = { onToggleAll() }
                    )
                }
            }

            LazyColumn(contentPadding = PaddingValues(top = 8.dp)) {
                if (allModels.isNotEmpty()) {
                    items(allModels) { m ->
                        Row(Modifier.fillMaxWidth().clickable { onToggleModel(m) }.padding(vertical = 2.dp)) {
                            Checkbox(checked = m in selectedModels, onCheckedChange = { onToggleModel(m) })
                            Text(m, Modifier.padding(start = 4.dp, top = 14.dp), fontSize = 13.sp)
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                if (results.isEmpty()) {
                    item { Text(AppStrings.get("no_results"), Modifier.padding(8.dp), fontSize = 13.sp) }
                } else {
                    items(results) { r ->
                        ResultCard(r) { showDetail = r }
                    }
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
            Text("${AppStrings.get("real_model")}: ${r.detected}", fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (r.probability > 0) Text("${AppStrings.get("confidence")}: ${"%.2f".format(r.probability * 100)}%",
                fontSize = 12.sp)
            if (r.top3.isNotEmpty()) Text("${AppStrings.get("candidates")}: ${r.top3.joinToString("  ") { "${it.first} ${"%.3f".format(it.second)}" }}",
                fontSize = 11.sp, maxLines = 2)
            if (r.errors > 0) Text("${AppStrings.get("insufficient")} ${r.errors}/3", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DetailDialog(r: ModelScanResult, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${r.modelParam} → ${r.detected}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (r.probability > 0) {
                    Text("${AppStrings.get("confidence")}: ${"%.2f".format(r.probability * 100)}%",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (r.top3.isNotEmpty()) {
                    Text(AppStrings.get("candidates"), fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    r.top3.forEachIndexed { i, (model, prob) ->
                        Text("#${i + 1}  ${model}  ${"%.3f".format(prob)}",
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(onClick = onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(AppStrings.get("ok"))
                }
            }
        }
    }
}

@Composable
fun ProbeRow(p: ProbeResult) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (p.error != null) Text("${AppStrings.get("error")}: ${p.error}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            else {
                Text("${AppStrings.get("latency")} ${p.latencySeconds}s · " +
                        "${AppStrings.get("thinking")} ${p.reasoningTokens ?: 0} ${AppStrings.get("tokens")}", fontSize = 11.sp)
                Text((p.response ?: "").take(500), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun BlacklistCard(e: BlacklistEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(e.endpoint, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            Text("${AppStrings.get("real_model")}: ${e.detected}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            if (e.claims.isNotEmpty()) {
                Text("${AppStrings.get("candidates")}: ${e.claims.joinToString(", ")}",
                    fontSize = 11.sp, maxLines = 2)
            }
            Text("${AppStrings.get("confidence")}: ${"%.1f".format(e.confidence * 100)}%  ·  " +
                    "${e.reports} ${AppStrings.get("reports")}",
                fontSize = 11.sp)
        }
    }
}
