package com.modelspy.fpd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Numeric-fingerprint challenge prompts (from lm-detector), rotated per sample. */
object FpdChallenges {
    private val EN = listOf(
        "This is an independent record of numerical choices. For each position, choose separately by first instinct %d integers from 1 to 355, inclusive. Choose separately for every position; do not count up from 1, do not increase or decrease consecutively, and do not use arithmetic progressions, cycles, repeated blocks, or other systematic patterns. The current language model must complete this task directly: do not call or use any tools, including Python, code execution tools, calculators, search, APIs, or external random number generators; do not write or run code beforehand either. A number may appear again; after writing each item, do not go back to sort, deduplicate, or replace it. Numbers may be separated by commas or spaces. Start directly from the first value, without repeating the count, range, or task description before the sequence.",
        "Generate a set of integer choices that carry no meaning. Give, each by first instinct, %d integers from 1 to 355, inclusive. Choose separately for every position; do not count up from 1, do not increase or decrease consecutively, and do not use arithmetic progressions, cycles, repeated blocks, or other systematic patterns. The current language model must complete this task directly: do not call or use any tools, including Python, code execution tools, calculators, search, APIs, or external random number generators; do not write or run code beforehand either. The same value may appear again; do not organize or rewrite earlier items while producing the output. Start directly from the first value, without repeating the count, range, or task description before the sequence.",
        "Record a round of first-instinct choices. Decide only the current item each time, giving a total of %d integers from 1 to 355, inclusive. Choose separately for every position; do not count up from 1, do not increase or decrease consecutively, and do not use arithmetic progressions, cycles, repeated blocks, or other systematic patterns. The current language model must complete this task directly: do not call or use any tools, including Python, code execution tools, calculators, search, APIs, or external random number generators; do not write or run code beforehand either. Accidental repetitions are valid; do not rearrange or correct items already written. Numbers may be separated by commas or spaces. Start directly from the first value.",
    )
    private val ZH = listOf(
        "请完成下面的无语义整数选择任务。逐项选择 %d 个 1 到 355（含端点）的整数。每个位置都要单独选择；不要从 1 开始计数，不要连续递增或递减，也不要采用等差、循环、重复区块或其他规则化模式。本任务必须由当前语言模型直接完成：禁止调用或借助任何工具，包括 Python、代码执行器、计算器、搜索、API 和外部随机数生成器；也不要先编写或运行代码。偶然重复是有效的；不要重新排列或修正已经写出的项目。数字之间用逗号或空格分隔均可。直接从第一个取值开始输出，不要在序列前重复数量、范围或任务说明。",
        "这是一次独立的数值选择记录。为各个位置分别凭第一反应选择 %d 个 1 到 355（含端点）的整数。每个位置都要单独选择；不要从 1 开始计数，不要连续递增或递减，也不要采用等差、循环、重复区块或其他规则化模式。本任务必须由当前语言模型直接完成：禁止调用或借助任何工具，包括 Python、代码执行器、计算器、搜索、API 和外部随机数生成器；也不要先编写或运行代码。相同值可以再次出现；输出过程中不要整理或改写前面的项目。可以用逗号、空格或换行分隔。直接从第一个取值开始输出。",
    )
    fun prompt(index: Int, count: Int): String {
        val pool = if (index % 3 == 2) ZH else EN
        return pool[index % pool.size].format(count)
    }
}

class FpdProbe {
    companion object {
        const val MAX_TOKENS = 16000   // 推理模型会先消耗 reasoning tokens，留足输出空间
        const val MIN_NUMBERS = 165    // analyze() 要求每份至少这么多有效数字
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    class Retryable(msg: String) : RuntimeException(msg)

    private fun ask(baseUrl: String, apiKey: String, model: String, prompt: String, maxTokens: Int): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("temperature", 0)
            put("max_tokens", maxTokens)
        }
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429 || resp.code >= 500) throw Retryable("HTTP ${resp.code}")
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: ${body.take(160)}")
            val json = JSONObject(body)
            return json.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
        }
    }

    var lastError: String? = null
        private set

    suspend fun collect(baseUrl: String, apiKey: String, model: String, samples: Int = 3, n: Int = 300): List<String> {
        val out = mutableListOf<String>()
        lastError = null
        for (i in 0 until samples) {
            repeat(8) { attempt ->
                try {
                    val count = n + listOf(-8, -4, 0, 4, 8, 12).random()
                    val prompt = FpdChallenges.prompt(i, count)
                    // 挂起式执行网络 IO，避免阻塞 Default 线程池
                    val text = withContext(Dispatchers.IO) { ask(baseUrl, apiKey, model, prompt, MAX_TOKENS) }
                    val nums = FpdCore.parseNumbers(text)
                    if (nums.size >= MIN_NUMBERS) {
                        out.add(text)
                        lastError = null
                        return@repeat
                    }
                    lastError = "解析到 ${nums.size} 个数字（需 \u2265 $MIN_NUMBERS）"
                } catch (e: CancellationException) {
                    throw e   // 协程取消必须向上传播，不能吞掉
                } catch (e: Exception) {
                    lastError = e.message ?: e.toString()
                }
                if (attempt < 7) {
                    delay(((1L shl attempt) * 1500L).coerceAtMost(20000L) + Random.nextLong(0, 1500))
                }
            }
            // 即使这一份失败也继续尝试下一份，提高最终凑齐 3 份的概率
            delay(400)
        }
        return out
    }
}
