package com.modelspy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BlacklistEntry(
    val endpoint: String,
    val detected: String,
    val claims: List<String>,
    val confidence: Double,
    val reports: Int,
    val lastSeen: String,
)

object BlacklistClient {
    private const val URL = "https://raw.githubusercontent.com/tangdino/modelspy-android/main/docs/blacklist.json"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): List<BlacklistEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(URL).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string() ?: return@use emptyList()
            val arr = JSONObject(body).optJSONArray("entries") ?: return@use emptyList()
            val out = mutableListOf<BlacklistEntry>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val claimsArr = e.optJSONArray("claims")
                val claims = mutableListOf<String>()
                if (claimsArr != null) {
                    for (j in 0 until claimsArr.length()) claims.add(claimsArr.optString(j))
                }
                out.add(BlacklistEntry(
                    endpoint = e.optString("endpoint"),
                    detected = e.optString("detected"),
                    claims = claims,
                    confidence = e.optDouble("confidence", 0.0),
                    reports = e.optInt("reports", 1),
                    lastSeen = e.optString("last_seen"),
                ))
            }
            out.sortedByDescending { it.reports }
        }
    }
}
