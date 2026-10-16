package com.modelspy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(val latestTag: String, val htmlUrl: String, val apkUrl: String?)

object UpdateChecker {
    const val CURRENT_VERSION = "1.0"
    private const val API = "https://api.github.com/repos/tangdino/modelspy-android/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val j = JSONObject(body)
            val tag = j.optString("tag_name", "")
            val html = j.optString("html_url", "")
            val assets = j.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name", "").endsWith(".apk")) {
                        apk = a.optString("browser_download_url", null); break
                    }
                }
            }
            if (tag.isEmpty()) null else UpdateInfo(tag, html, apk)
        }
    }

    /** Semantic-version comparison; supports 1.0, 1.0.1, 1.2.3, optional 'v' prefix. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    fun isNewer(latest: String): Boolean = compareVersions(latest, CURRENT_VERSION) > 0
}
