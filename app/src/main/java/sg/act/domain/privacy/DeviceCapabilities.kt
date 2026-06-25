package sg.act.domain.privacy

import android.app.ActivityManager
import android.content.Context

/**
 * Reads the device's memory profile so the UI can recommend an on-device model
 * the phone can actually run — rather than shipping a one-size default that OOMs
 * on budget hardware.
 */
class DeviceCapabilities(context: Context) {

    enum class Suitability { RECOMMENDED, HEAVY, INSUFFICIENT }

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    val totalRamMb: Long = ActivityManager.MemoryInfo().also {
        activityManager.getMemoryInfo(it)
    }.totalMem / (1024 * 1024)

    val isLowRam: Boolean = activityManager.isLowRamDevice

    /**
     * Generation thread count, probed **once** at startup (CPU topology is fixed for
     * the device). Uses the **performance-core cluster**, not the total core count:
     * on a big.LITTLE CPU, adding the slow little cores as inference threads *hurts*
     * (every matmul barrier waits on the slowest thread) and starves the UI. Cores
     * are clustered by max frequency — the little cluster sits at a distinctly lower
     * `cpuinfo_max_freq`, so "performance cores" are those above the slowest cluster.
     * Falls back to ~half the cores when `/sys` is unreadable; floored at 2, capped.
     */
    val recommendedThreads: Int = computeRecommendedThreads()

    private fun computeRecommendedThreads(): Int {
        val total = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val freqs = (0 until total).mapNotNull { cpu ->
            runCatching {
                java.io.File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toLong()
            }.getOrNull()
        }
        val performance = if (freqs.size == total && freqs.isNotEmpty()) {
            val min = freqs.min()
            freqs.count { it > min } // cores above the slowest (little) cluster
        } else {
            0 // /sys unreadable
        }
        val threads = if (performance in 2..total) performance else (total + 1) / 2
        return threads.coerceIn(2, minOf(total, 8))
    }

    /**
     * Classify a model needing [minRamMb] against this device. A comfortable run
     * wants noticeable headroom over the model's working set, so "recommended"
     * requires ~1.6x the model's minimum.
     */
    fun rate(minRamMb: Int): Suitability = when {
        totalRamMb < minRamMb -> Suitability.INSUFFICIENT
        totalRamMb < minRamMb * 1.6 -> Suitability.HEAVY
        else -> Suitability.RECOMMENDED
    }

    /**
     * Context length to request for on-device inference, scaled to device memory.
     * Larger contexts cost RAM (the KV cache grows with n_ctx), so budget phones
     * get a smaller window. The native side further clamps this to the model's
     * trained context.
     */
    fun recommendedContextTokens(): Int = when {
        isLowRam || totalRamMb < 3_000 -> 2048
        totalRamMb < 6_000 -> 4096
        else -> 8192
    }

    /**
     * The largest context length the user may select on this device. Roughly 2x the
     * recommended size: enough headroom to be useful, while still bounding the KV
     * cache so a high preset can't OOM a budget phone. The native side additionally
     * clamps any request to the model's trained context.
     */
    fun maxAllowedContextTokens(): Int = when {
        isLowRam || totalRamMb < 3_000 -> 4096
        totalRamMb < 6_000 -> 8192
        else -> 16384
    }
}
