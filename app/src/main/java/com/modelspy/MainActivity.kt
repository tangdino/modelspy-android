package com.modelspy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.modelspy.data.ModelScanResult
import com.modelspy.fpd.FpdProbe
import com.modelspy.fpd.FpdRanker
import org.json.JSONObject
import com.modelspy.ui.AppStrings
import com.modelspy.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val fpdProbe = FpdProbe()
    private var ranker: FpdRanker? = null
    private var job: Job? = null

    private val endpoint = mutableStateOf("")
    private val apiKey = mutableStateOf("")
    private val concurrency = mutableStateOf(8)
    private val running = mutableStateOf(false)
    private val progress = mutableStateOf("")
    private val errorMessage = mutableStateOf<String?>(null)
    private val updateMessage = mutableStateOf<String?>(null)
    private val results = mutableStateListOf<ModelScanResult>()
    private val loading = mutableStateOf(true)
    private val themeMode = mutableStateOf("system")  // system / light / dark
    private val allModels = mutableStateListOf<String>()
    private val selectedModels = mutableStateListOf<String>()
    private val blacklist = mutableStateListOf<BlacklistEntry>()
    private val blacklistLoading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = when (themeMode.value) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                if (loading.value) {
                    LoadingScreen()
                } else {
                    MainScreen(
                    endpoint = endpoint.value,
                    apiKey = apiKey.value,
                    concurrency = concurrency.value,
                    running = running.value,
                    progress = progress.value,
                    results = results,
                    error = errorMessage.value,
                    allModels = allModels,
                    selectedModels = selectedModels,
                    onEndpointChange = { endpoint.value = it },
                    onApiKeyChange = { apiKey.value = it },
                    onConcurrencyChange = { concurrency.value = it },
                    onFetchModels = ::fetchModels,
                    onToggleModel = ::toggleModel,
                    onToggleAll = ::toggleAll,
                    onStart = ::startScan,
                    onCancel = { job?.cancel(); running.value = false; progress.value = AppStrings.get("done") },
                    onSelect = {},
                    onToggleLang = {
                        AppStrings.lang = if (AppStrings.lang == "zh") "en" else "zh"
                    },
                    onCheckUpdate = ::checkUpdate,
                    updateMessage = updateMessage.value,
                    onDismissUpdate = { updateMessage.value = null },
                    themeMode = themeMode.value,
                    blacklist = blacklist,
                    blacklistLoading = blacklistLoading.value,
                    onOpenBlacklist = ::loadBlacklist,
                    onToggleTheme = {
                        themeMode.value = when (themeMode.value) {
                            "system" -> "light"
                            "light" -> "dark"
                            else -> "system"
                        }
                    },
                    )
                }
            }
        }
        loadDetector()
    }

    private fun loadDetector() {
        lifecycleScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val json = assets.open("detector.json").bufferedReader().use { it.readText() }
                    FpdRanker(JSONObject(json))
                }
                ranker = r
            } catch (e: Exception) {
                errorMessage.value = "detector load failed: ${e.message}"
            } finally {
                loading.value = false
            }
        }
    }

    private fun fetchModels() {
        errorMessage.value = null
        progress.value = AppStrings.get("fetching")
        lifecycleScope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    com.modelspy.probe.ProbeEngine().listModels(endpoint.value, apiKey.value)
                }
                allModels.clear(); allModels.addAll(models)
                selectedModels.clear(); selectedModels.addAll(models)
                progress.value = "${AppStrings.get("found")} ${models.size} ${AppStrings.get("models")}"
            } catch (e: Exception) {
                errorMessage.value = e.message ?: e.toString()
                progress.value = ""
            }
        }
    }

    private fun toggleModel(m: String) {
        if (m in selectedModels) selectedModels.remove(m) else selectedModels.add(m)
    }

    private fun toggleAll() {
        if (selectedModels.size == allModels.size) selectedModels.clear()
        else { selectedModels.clear(); selectedModels.addAll(allModels) }
    }

    private fun loadBlacklist() {
        if (blacklistLoading.value) return
        blacklistLoading.value = true
        lifecycleScope.launch {
            try {
                val list = BlacklistClient.fetch()
                blacklist.clear(); blacklist.addAll(list)
            } catch (e: Exception) {
                errorMessage.value = AppStrings.get("blacklist_failed") + ": ${e.message}"
            } finally {
                blacklistLoading.value = false
            }
        }
    }

    private fun checkUpdate() {
        updateMessage.value = AppStrings.get("checking")
        lifecycleScope.launch {
            try {
                val info = UpdateChecker.check()
                updateMessage.value = when {
                    info == null -> AppStrings.get("update_failed")
                    UpdateChecker.isNewer(info.latestTag) ->
                        AppStrings.get("update_found") + ": ${info.latestTag}\n${AppStrings.get("download")}: ${info.htmlUrl}"
                    else -> AppStrings.get("up_to_date") + " (" + AppStrings.get("current_version") + " " +
                        UpdateChecker.CURRENT_VERSION + ")"
                }
            } catch (e: Exception) {
                updateMessage.value = AppStrings.get("update_failed") + ": ${e.message}"
            }
        }
    }

    private suspend fun reportFakes(models: List<String>) {
        // 假模型判定：置信度 >= 90% 且 声称型号与检测结果不同（归一化后仍不同）
        for (r in results) {
            if (r.probability < 0.90) continue
            val claimed = r.modelParam.lowercase()
            val detected = r.detected.lowercase()
            // 简单归一化：如果检测结果与声称的一致（或互为包含），视为真身
            val same = claimed.contains(detected) || detected.contains(claimed) ||
                       claimed.split("-").toSet() == detected.split("-").toSet()
            if (!same && r.detected != AppStrings.get("insufficient")) {
                ReportClient.report(endpoint.value, r.modelParam, r.detected, r.probability)
            }
        }
    }

    private fun startScan() {
        val models = if (selectedModels.isEmpty()) allModels.toList() else selectedModels.toList()
        if (models.isEmpty()) { errorMessage.value = AppStrings.get("need_select"); return }
        job?.cancel()
        results.clear()
        errorMessage.value = null
        running.value = true
        job = lifecycleScope.launch {
            try {
                val r = ranker ?: throw RuntimeException("detector not loaded")
                progress.value = "${AppStrings.get("found")} ${models.size} ${AppStrings.get("models")}"
                val sem = Semaphore(concurrency.value)
                var done = 0
                withContext(Dispatchers.Default) {
                    models.map { m ->
                        async {
                            sem.acquire()
                            try {
                                val texts = fpdProbe.collect(endpoint.value, apiKey.value, m, 3)
                                val res = if (texts.size >= 3) r.analyze(texts) else null
                                done += 1
                                withContext(Dispatchers.Main) {
                                    progress.value = "${AppStrings.get("progress")} $done/${models.size}"
                                    results.add(
                                        ModelScanResult(
                                            modelParam = m,
                                            detected = res?.model ?: AppStrings.get("insufficient"),
                                            provider = "",
                                            tier = "",
                                            score = (res?.score ?: 0.0).toInt(),
                                            hasReasoning = false,
                                            avgLatency = null,
                                            identity = "",
                                            errors = 3 - texts.size,
                                            probes = emptyList(),
                                            top3 = res?.top?.map { it.first to (it.second * 1000).toInt() } ?: emptyList(),
                                            probability = res?.probability ?: 0.0,
                                        )
                                    )
                                }
                            } finally { sem.release() }
                        }
                    }.awaitAll()
                }
                results.sortByDescending { it.probability }
                progress.value = "${AppStrings.get("done")}: ${models.size}"
                reportFakes(models)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: e.toString()
            } finally {
                running.value = false
            }
        }
    }
}


@Composable
fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(AppStrings.get("loading"), Modifier.padding(top = 16.dp))
    }
}
