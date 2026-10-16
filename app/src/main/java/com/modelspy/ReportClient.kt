package com.modelspy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 把检测到的"假模型中转站"上报到 ModelSpy 后台，用于维护黑名单。 */
object ReportClient {
    private const val REPORT_URL = "https://modelspy-report.tangdino98.workers.dev/report"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun report(endpoint: String, claimed: String, detected: String, confidence: Double) =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("endpoint", endpoint)
                    .put("claimed", claimed)
                    .put("detected", detected)
                    .put("confidence", confidence)
                    .put("model_param", claimed)
                val req = Request.Builder()
                    .url(REPORT_URL)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
}
