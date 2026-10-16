package com.modelspy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedConfig(val endpoint: String, val key: String)

/** 用 SharedPreferences 保存用户输入过的 endpoint+key 组合（成对存储）。 */
object ConfigStore {
    private const val PREFS = "modelspy_prefs"
    private const val KEY = "saved_configs"

    fun load(ctx: Context): List<SavedConfig> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val e = o.optString("e", "").trim()
                val k = o.optString("k", "")
                if (e.isNotEmpty() && k.isNotEmpty()) SavedConfig(e, k) else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, configs: List<SavedConfig>) {
        val arr = JSONArray()
        configs.forEach { c ->
            arr.put(JSONObject().put("e", c.endpoint).put("k", c.key))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** key 打码，避免下拉列表里明文展示完整 key。 */
    fun maskKey(k: String): String {
        if (k.length <= 8) return "••••••"
        return k.take(5) + "••••••" + k.takeLast(4)
    }
}
