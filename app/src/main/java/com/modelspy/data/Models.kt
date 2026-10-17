package com.modelspy.data

data class ProbeResult(
    val name: String,
    val prompt: String,
    val response: String?,
    val latencySeconds: Double?,
    val reasoningTokens: Int?,
    val error: String?,
)

data class ModelScanResult(
    val modelParam: String,
    val detected: String,
    val provider: String,
    val tier: String,
    val score: Int,
    val hasReasoning: Boolean,
    val avgLatency: Double?,
    val identity: String,
    val errors: Int,
    val probes: List<ProbeResult>,
    val top3: List<Pair<String, Double>>,
    val probability: Double = 0.0,
    val errorDetail: String = "",
)
