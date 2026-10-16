package com.modelspy.fpd

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Kotlin port of Ikaleio/lm-detector "shared ranker".
 * Numeric-distribution fingerprinting: models write ~300 integers in 1..355 and
 * the value distribution is matched against a reference bank. Because it reads
 * weight-level behaviour it is NOT fooled by injected identity prompts.
 */
object FpdCore {
    const val VALUE_MIN = 1
    const val VALUE_MAX = 355
    const val ALPHA = 0.5
    const val DIM = 355

    /** Longest digit run; alphabetic separators split runs; keep 1..355. */
    fun parseNumbers(text: String): List<Int> {
        val runs = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var prevEnd = 0
        Regex("\\d+").findAll(text).forEach { m ->
            val sep = text.substring(prevEnd, m.range.first)
            val v = m.value.toIntOrNull()
            if (current.isNotEmpty() && Regex("\\p{L}").containsMatchIn(sep)) {
                runs.add(current); current = mutableListOf()
            }
            if (v != null && v in VALUE_MIN..VALUE_MAX) current.add(v)
            prevEnd = m.range.last + 1
        }
        if (current.isNotEmpty()) runs.add(current)
        return runs.maxByOrNull { it.size } ?: emptyList()
    }

    fun countNumbers(nums: List<Int>): DoubleArray {
        val c = DoubleArray(DIM)
        for (n in nums) c[n - VALUE_MIN] += 1.0
        return c
    }

    fun hellingerFeature(counts: DoubleArray): DoubleArray {
        val total = counts.sum() + ALPHA * DIM
        return DoubleArray(DIM) { i -> sqrt((counts[i] + ALPHA) / total) }
    }

    fun orderedBlockFeature(nums: List<Int>): DoubleArray {
        val n = nums.size
        val base = n / 4; val rem = n % 4
        val pieces = mutableListOf<Double>()
        var start = 0
        for (i in 0 until 4) {
            val size = base + if (i < rem) 1 else 0
            val chunk = nums.subList(start, (start + size).coerceAtMost(n)); start += size
            val bins = DoubleArray(16) { 0.5 }
            for (v in chunk) bins[minOf(15, ((v - 1) / 355.0 * 16).toInt())] += 1.0
            val t = bins.sum()
            pieces.addAll(bins.map { sqrt(it / t) })
        }
        val last = DoubleArray(10) { 0.5 }
        for (v in nums) last[v % 10] += 1.0
        val lt = last.sum()
        pieces.addAll(last.map { sqrt(it / lt) })
        return pieces.toDoubleArray()
    }
}

object FpdMath {
    fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
    fun mean(a: DoubleArray): Double = a.sum() / a.size
    fun meanList(a: List<Double>): Double = if (a.isEmpty()) 0.0 else a.sum() / a.size
    fun unit(a: DoubleArray): DoubleArray {
        val n = maxOf(sqrt(dot(a, a)), 1e-12)
        return DoubleArray(a.size) { a[it] / n }
    }
    fun zscore(a: DoubleArray): DoubleArray {
        val m = mean(a)
        val v = maxOf(sqrt(mean(DoubleArray(a.size) { (a[it] - m) * (a[it] - m) })), 1e-12)
        return DoubleArray(a.size) { (a[it] - m) / v }
    }
    fun median(a: List<Double>): Double {
        val v = a.sorted(); val i = v.size / 2
        return if (v.size % 2 == 1) v[i] else (v[i - 1] + v[i]) / 2.0
    }
    fun colMean(m: List<DoubleArray>): DoubleArray {
        if (m.isEmpty()) return DoubleArray(0)
        val w = m[0].size
        return DoubleArray(w) { i -> meanList(m.map { it[i] }) }
    }
    fun subtractBasis(v: DoubleArray, basis: List<DoubleArray>): DoubleArray {
        val out = v.copyOf()
        for (b in basis) {
            val p = dot(out, b)
            for (i in out.indices) out[i] -= p * b[i]
        }
        return out
    }
    fun toArr(ja: JSONArray): DoubleArray = DoubleArray(ja.length()) { ja.optDouble(it, 0.0) }
}

class FpdRanker(private val art: JSONObject) {
    val modelIds: List<String> = art.getJSONArray("model_ids").let { a ->
        (0 until a.length()).map { a.getString(it) }
    }
    private val ranker = art.getJSONObject("ranker")
    private val blend = ranker.getJSONObject("blend")
    private val tau = art.optJSONObject("calibration")?.optDouble("tau", 4.782979057955927) ?: 4.782979057955927

    private fun preprocess(idx: Int, key: String): Pair<DoubleArray, DoubleArray> {
        val p = ranker.getJSONArray(key).getJSONObject(idx)
        return Pair(FpdMath.toArr(p.getJSONArray("mean")), FpdMath.toArr(p.getJSONArray("scale")))
    }

    private fun transform(b: List<DoubleArray>, key: String): DoubleArray {
        val out = mutableListOf<Double>()
        for (j in b.indices) {
            val (mean, scale) = preprocess(j, key)
            val u = FpdMath.unit(DoubleArray(b[j].size) { i -> (b[j][i] - mean[i]) / scale[i] })
            val w = sqrt(if (j == 0) 0.75 else 0.25)
            u.forEach { out.add(it * w) }
        }
        return out.toDoubleArray()
    }

    private fun blocks(n: List<Int>): List<DoubleArray> =
        listOf(FpdCore.hellingerFeature(FpdCore.countNumbers(n)), FpdCore.orderedBlockFeature(n))

    private fun centered(v: DoubleArray, fb: JSONObject): DoubleArray {
        val m = FpdMath.toArr(fb.getJSONArray("feature_mean"))
        val s = FpdMath.toArr(fb.getJSONArray("feature_scale"))
        return DoubleArray(v.size) { i -> (v[i] - m[i]) / s[i] }
    }

    private fun basisOf(fb: JSONObject): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        val nb = fb.optJSONArray("nuisance_basis") ?: return out
        for (i in 0 until nb.length()) out.add(FpdMath.toArr(nb.getJSONArray(i)))
        return out
    }

    private fun baseline(b: List<DoubleArray>): DoubleArray {
        val bank = ranker.getJSONObject("bank")
        val h = bank.getJSONObject("hellinger")
        val o = bank.getJSONObject("ordered_blocks")
        val hUnit = FpdMath.unit(FpdMath.subtractBasis(centered(b[0], h), basisOf(h)))
        val hC = h.getJSONArray("centroids")
        val marginal = (0 until hC.length()).map { FpdMath.dot(hUnit, FpdMath.toArr(hC.getJSONArray(it))) }
        val oC = centered(b[1], o)
        val raw = FpdMath.unit(oC)
        val projected = FpdMath.unit(FpdMath.subtractBasis(oC, basisOf(o)))
        val oCentroids = o.getJSONArray("centroids")
        val envArr = o.getJSONArray("environment_centroids")
        val templates = (0 until oCentroids.length()).map { i ->
            (0 until envArr.length()).maxOf { e ->
                FpdMath.dot(raw, FpdMath.toArr(envArr.getJSONArray(e).getJSONArray(i)))
            }
        }
        val nuisance = (0 until oCentroids.length()).map { FpdMath.dot(projected, FpdMath.toArr(oCentroids.getJSONArray(it))) }
        val tz = FpdMath.zscore(templates.toDoubleArray())
        val nz = FpdMath.zscore(nuisance.toDoubleArray())
        val ordered = FpdMath.zscore(DoubleArray(tz.size) { 0.5 * tz[it] + 0.5 * nz[it] })
        val mz = FpdMath.zscore(marginal.toDoubleArray())
        return DoubleArray(mz.size) { 0.5 * mz[it] + 0.5 * ordered[it] }
    }

    private fun nearest(x: DoubleArray, refs: List<DoubleArray>): Double {
        val xx = FpdMath.dot(x, x)
        val d = refs.map { r -> maxOf(0.0, xx + FpdMath.dot(r, r) - 2 * FpdMath.dot(x, r)) }.sorted()
        return FpdMath.meanList(d.take(7))
    }

    // ---- positional head: 4 blocks -> 751 dims (355+74+102+220) ----
    private val posWeights = listOf(0.6, 0.15, 0.125, 0.125)
    private val posCentres = (0 until 24).map { 1.0 + it * 354.0 / 23 }
    private val posLags = listOf(1, 2, 3, 5, 8, 13, 21, 34)

    private fun positionKernel(x: List<Int>): DoubleArray {
        val n = x.size
        val out = DoubleArray(34 * 3)
        for (position in x.indices) {
            val value = x[position].toDouble()
            val t = (position + 0.5) * 2 / n - 1
            val basis = doubleArrayOf(t, 0.5 * (3 * t * t - 1), 0.5 * (5 * t * t * t - 3 * t))
            val feats = ArrayList<Double>(34)
            for (c in posCentres) feats.add(kotlin.math.exp(-0.5 * ((value - c) / 20.0).let { it * it }))
            for (d in 0 until 10) feats.add(if (d == x[position] % 10) 1.0 else 0.0)
            for (k in feats.indices) for (j in 0..2) out[k * 3 + j] += feats[k] * basis[j] / n
        }
        return out
    }

    private fun centredTransitions(symbols: List<Int>, bins: Int): DoubleArray {
        val joint = DoubleArray(bins * bins) { 0.5 }
        val total = symbols.size - 1 + 0.5 * bins * bins
        for (i in 1 until symbols.size) joint[symbols[i - 1] * bins + symbols[i]] += 1.0
        for (i in joint.indices) joint[i] /= total
        val rows = DoubleArray(bins) { a -> (0 until bins).sumOf { b -> joint[a * bins + b] } }
        val cols = DoubleArray(bins) { b -> (0 until bins).sumOf { a -> joint[a * bins + b] } }
        return DoubleArray(joint.size) { i -> joint[i] - rows[i / bins] * cols[i % bins] }
    }

    private fun sequenceFeatures(x: List<Int>): DoubleArray {
        val n = x.size
        val m = FpdMath.meanList(x.map { it.toDouble() })
        val sd = maxOf(kotlin.math.sqrt(FpdMath.meanList(x.map { (it - m) * (it - m) })), 1.0)
        val s = x.map { (it - m) / sd }
        val out = ArrayList<Double>()
        for (lag in posLags) {
            val cnt = n - lag
            val d = (0 until cnt).map { x[it + lag] - x[it] }
            out.add(FpdMath.meanList((0 until cnt).map { s[it + lag] * s[it] }))
            out.add(FpdMath.meanList(d.map { kotlin.math.abs(it.toDouble()) }) / 355.0)
            out.add(d.count { it > 0 }.toDouble() / cnt)
            out.add(d.count { it == 0 }.toDouble() / cnt)
            out.add(d.count { kotlin.math.abs(it) <= 5 }.toDouble() / cnt)
            out.add(d.count { kotlin.math.abs(it) <= 20 }.toDouble() / cnt)
        }
        out.addAll(centredTransitions(x.map { minOf(7, ((it - 1) * 8 / 355)) }, 8).toList())
        out.addAll(centredTransitions(x.map { it % 10 }, 10).toList())
        val diff = (0 until n - 1).map { x[it + 1] - x[it] }
        val zStep = (0 until n - 1).map { kotlin.math.abs(s[it + 1] - s[it]) }
        out.add((0 until n - 2).count { (diff[it + 1] > 0) == (diff[it] > 0) }.toDouble() / (n - 2))
        out.add((0 until n - 2).count { diff[it + 1] == diff[it] }.toDouble() / (n - 2))
        out.add((0 until n - 1).count { x[it] % 10 == x[it + 1] % 10 }.toDouble() / (n - 1))
        out.add((0 until n - 1).count { zStep[it] > 1 }.toDouble() / (n - 1))
        out.add((0 until n - 1).count { zStep[it] > 2 }.toDouble() / (n - 1))
        val buckets = x.map { minOf(15, ((it - 1) * 16 / 355)) }
        for (width in listOf(4, 8, 16)) {
            var sum = 0.0
            var count = 0
            for (i in 0..(n - width)) { sum += buckets.subList(i, i + width).toSet().size.toDouble() / width; count++ }
            out.add(if (count > 0) sum / count else 0.0)
        }
        return out.toDoubleArray()
    }

    private fun positionalDecision(n: List<Int>): DoubleArray {
        val head = ranker.getJSONObject("positional")
        val params = head.getJSONArray("params")
        val weights = head.getJSONArray("weights")
        val bias = FpdMath.toArr(head.getJSONArray("bias"))
        val parts = listOf(
            FpdCore.hellingerFeature(FpdCore.countNumbers(n)),
            FpdCore.orderedBlockFeature(n),
            positionKernel(n),
            sequenceFeatures(n)
        )
        val x = ArrayList<Double>()
        for (j in parts.indices) {
            val p = params.getJSONObject(j)
            val m = FpdMath.toArr(p.getJSONArray("mean"))
            val sc = FpdMath.toArr(p.getJSONArray("scale"))
            val scaled = DoubleArray(parts[j].size) { i -> (parts[j][i] - m[i]) / sc[i] }
            val L = maxOf(kotlin.math.sqrt(FpdMath.dot(scaled, scaled)), 1e-12)
            scaled.forEach { x.add(it / L * kotlin.math.sqrt(posWeights[j])) }
        }
        return DoubleArray(weights.length()) { k ->
            val row = FpdMath.toArr(weights.getJSONArray(k))
            var sum = bias[k]
            for (i in row.indices) if (i < x.size) sum += row[i] * x[i]
            sum
        }
    }

    fun rank(parsed: List<List<Int>>): DoubleArray {
        val full = parsed.map { blocks(it) }
        val ax = full.map { transform(it, "full_params") }
        val headW = ranker.getJSONArray("lda_weights")
        val ldaBias = FpdMath.toArr(ranker.getJSONArray("lda_bias"))
        val lda = parsed.map { n ->
            val x = transform(blocks(n.take(128)), "head_params")
            FpdMath.zscore(DoubleArray(headW.length()) { i ->
                FpdMath.dot(FpdMath.toArr(headW.getJSONArray(i)), x) + ldaBias[i]
            })
        }
        val l = FpdMath.zscore(FpdMath.colMean(lda))
        val refsJa = ranker.getJSONArray("references")
        val near = FpdMath.zscore(DoubleArray(refsJa.length()) { k ->
            val refJa = refsJa.getJSONArray(k)
            val ref = (0 until refJa.length()).map { FpdMath.toArr(refJa.getJSONArray(it)) }
            FpdMath.median(ax.map { -nearest(it, ref) })
        })
        val base = FpdMath.zscore(FpdMath.colMean(full.map { baseline(it) }))
        val freq = FpdMath.zscore(DoubleArray(l.size) { 0.5 * l[it] + 0.25 * near[it] + 0.25 * base[it] })
        val pos = FpdMath.zscore(FpdMath.colMean(parsed.map { FpdMath.zscore(positionalDecision(it)) }))
        val f = blend.optDouble("frequency", 0.5)
        val p = blend.optDouble("positional", 0.5)
        return DoubleArray(freq.size) { f * freq[it] + p * pos[it] }
    }

    fun probabilities(ranking: DoubleArray): DoubleArray {
        val logits = DoubleArray(ranking.size) { tau * ranking[it] }
        val mx = logits.maxOrNull() ?: 0.0
        val w = logits.map { exp(it - mx) }
        val s = w.sum()
        return DoubleArray(w.size) { w[it] / s }
    }

    data class Result(val model: String, val probability: Double, val score: Double, val top: List<Pair<String, Double>>)

    fun analyze(texts: List<String>, expected: Int = 300): Result? {
        val min = maxOf(80, (expected * 0.55).toInt().let { if (expected * 0.55 > it) it + 1 else it })
        val parsed = texts.map { FpdCore.parseNumbers(it) }
        if (parsed.count { it.size >= min } < 3) return null
        val r = rank(parsed)
        val pr = probabilities(r)
        val order = (0 until r.size).sortedByDescending { r[it] }
        return Result(
            model = modelIds[order[0]],
            probability = pr[order[0]],
            score = r[order[0]],
            top = order.take(5).map { modelIds[it] to pr[it] }
        )
    }
}
