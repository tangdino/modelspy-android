package com.modelspy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.modelspy.data.ModelScanResult
import com.modelspy.fpd.FpdProbe
import com.modelspy.fpd.FpdRanker
import org.json.JSONObject
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
    private val results = mutableStateListOf<ModelScanResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            val json = assets.open("detector.json").bufferedReader().use { it.readText() }
            ranker = FpdRanker(JSONObject(json))
        } catch (e: Exception) { errorMessage.value = "detector load failed: ${e.message}" }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(
                    endpoint = endpoint.value,
                    apiKey = apiKey.value,
                    concurrency = concurrency.value,
                    running = running.value,
                    progress = progress.value,
                    results = results,
                    error = errorMessage.value,
                    onEndpointChange = { endpoint.value = it },
                    onApiKeyChange = { apiKey.value = it },
                    onConcurrencyChange = { concurrency.value = it },
                    onStart = ::startScan,
                    onCancel = { job?.cancel(); running.value = false; progress.value = "已停止" },
                    onSelect = {},
                )
            }
        }
    }

    private fun startScan() {
        job?.cancel()
        results.clear()
        errorMessage.value = null
        running.value = true
        job = lifecycleScope.launch {
            try {
                val r = ranker ?: throw RuntimeException("detector not loaded")
                val models = withContext(Dispatchers.IO) {
                    com.modelspy.probe.ProbeEngine().listModels(endpoint.value, apiKey.value)
                }
                progress.value = "Found ${models.size} models — numeric fingerprinting (3 samples each)"
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
                                    progress.value = "progress $done/${models.size}"
                                    results.add(
                                        ModelScanResult(
                                            modelParam = m,
                                            detected = res?.model ?: "insufficient samples",
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
                progress.value = "done: ${models.size} models"
            } catch (e: Exception) {
                errorMessage.value = e.message ?: e.toString()
            } finally {
                running.value = false
            }
        }
    }
}
