package com.modelspy.probe

import com.modelspy.data.ModelScanResult
import com.modelspy.data.ModelSignatures
import com.modelspy.data.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

data class ProbeDef(val name: String, val prompt: String)

object Probes {
    val ALL = listOf(
        ProbeDef("self_identity", "What model are you? Please state your exact model name and version."),
        ProbeDef("knowledge_cutoff", "What is the most recent event you have knowledge of? State your knowledge cutoff date in YYYY-MM format."),
        ProbeDef("strawberry_count", "How many letter 'r's are in the word 'strawberry'?"),
        ProbeDef("decimal_comparison", "Which is larger, 9.11 or 9.9? Answer with just the number."),
        ProbeDef("multi_step_math", "What is 17 * 23 + 89 - 14 * 3? Show your work step by step."),
        ProbeDef("logic_puzzle", "All roses are flowers. Some flowers fade quickly. Can we conclude that some roses fade quickly? Explain your reasoning."),
        ProbeDef("token_awareness", "How many tokens does the sentence 'The quick brown fox jumps over the lazy dog' contain? Estimate and explain."),
        ProbeDef("coding_task", "Write a Python one-liner that checks if a string is a palindrome. Return only the code, no explanation."),
    )
}

class ProbeEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun listModels(baseUrl: String, apiKey: String): List<String> {
        val url = baseUrl.trimEnd('/') + "/models"
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
            val body = resp.body?.string() ?: error("empty body")
            val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optString("id")
                if (!id.isNullOrBlank() && !out.contains(id)) out.add(id)
            }
            return out.sorted()
        }
    }

    private fun chat(baseUrl: String, apiKey: String, model: String, prompt: String, maxTokens: Int): Triple<String, Double, Int?> {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("temperature", 0)
            put("max_tokens", maxTokens)
        }
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val start = System.currentTimeMillis()
        client.newCall(req).execute().use { resp ->
            val latency = (System.currentTimeMillis() - start) / 1000.0
            val body = resp.body?.string() ?: ""
            if (resp.code == 429 || resp.code >= 500) throw Retryable("HTTP ${resp.code}")
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${body.take(160)}")
            val json = JSONObject(body)
            val text = json.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
            val usage = json.optJSONObject("usage")
            val reasoning = usage?.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens")
            return Triple(text.trim(), latency, if (reasoning == 0) null else reasoning)
        }
    }

    class Retryable(msg: String) : RuntimeException(msg)

    private suspend fun probeOnce(baseUrl: String, apiKey: String, model: String, def: ProbeDef, maxTokens: Int, retries: Int = 6): ProbeResult {
        var lastErr: String? = null
        repeat(retries) { attempt ->
            try {
                val (text, latency, reasoning) = withContext(Dispatchers.IO) {
                    chat(baseUrl, apiKey, model, def.prompt, maxTokens)
                }
                return ProbeResult(def.name, def.prompt, text, latency, reasoning, null)
            } catch (e: Exception) {
                lastErr = e.message ?: e.toString()
                val backoff = (2.0.pow(attempt) * 2).toLong().coerceAtMost(30L) * 1000
                delay(backoff + Random.nextLong(0, 2000))
            }
        }
        return ProbeResult(def.name, def.prompt, null, null, null, lastErr)
    }

    suspend fun scanModel(baseUrl: String, apiKey: String, model: String, concurrency: Int, maxTokens: Int): ModelScanResult {
        val sem = Semaphore(concurrency)
        val results = coroutineScopeAsync(sem, baseUrl, apiKey, model, concurrency, maxTokens)
        return summarize(model, results)
    }

    private suspend fun coroutineScopeAsync(
        sem: Semaphore, baseUrl: String, apiKey: String, model: String,
        concurrency: Int, maxTokens: Int
    ): List<ProbeResult> = withContext(Dispatchers.Default) {
        Probes.ALL.map { def ->
            async {
                sem.acquire()
                try { probeOnce(baseUrl, apiKey, model, def, maxTokens) }
                finally { sem.release() }
            }
        }.awaitAll()
    }

    private fun summarize(model: String, probes: List<ProbeResult>): ModelScanResult {
        val identity = probes.firstOrNull { it.name == "self_identity" }?.response?.lowercase() ?: ""
        val strawberry = probes.firstOrNull { it.name == "strawberry_count" }?.response
            ?.let { Regex("\\b3\\b").containsMatchIn(it) } ?: false
        val decimalRaw = probes.firstOrNull { it.name == "decimal_comparison" }?.response ?: ""
        val decimal = decimalRaw.contains("9.9") &&
            !decimalRaw.split("9.9").first().takeLast(5).contains("9.11")
        val hasReasoning = probes.any { (it.reasoningTokens ?: 0) > 0 }

        val scored = ModelSignatures.ALL.map { sig ->
            var score = 0
            if (sig.identityKeywords.any { identity.contains(it) }) score += 15
            if (sig.strawberryFail && !strawberry) score += 5
            else if (!sig.strawberryFail && strawberry) score += 5
            if (sig.decimalFail && !decimal) score += 5
            else if (!sig.decimalFail && decimal) score += 5
            if (sig.hasReasoningTokens == hasReasoning) score += 10
            else if (hasReasoning && !sig.hasReasoningTokens) score -= 10
            sig to score
        }.sortedByDescending { it.second }

        val top = scored.firstOrNull()
        val lat = probes.mapNotNull { it.latencySeconds }
        return ModelScanResult(
            modelParam = model,
            detected = top?.first?.name ?: "UNKNOWN",
            provider = top?.first?.provider ?: "?",
            tier = top?.first?.tier ?: "?",
            score = top?.second ?: 0,
            hasReasoning = hasReasoning,
            avgLatency = if (lat.isEmpty()) null else lat.average(),
            identity = probes.firstOrNull { it.name == "self_identity" }?.response?.replace("\n", " ") ?: "",
            errors = probes.count { it.error != null },
            probes = probes,
            top3 = scored.take(3).map { it.first.name to it.second.toDouble() },
        )
    }
}
