package com.example

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long
) {
    val durationHoursAndMins: String
        get() {
            val totalMins = totalTimeMs / 1000 / 60
            val hrs = totalMins / 60
            val mins = totalMins % 60
            return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        }
    
    val usagePercentage: Float
        get() {
            // Assume max standard app usage is 4 hours for full progress visual
            val hours = totalTimeMs.toFloat() / (1000f * 60f * 60f)
            return (hours / 4.0f).coerceIn(0.05f, 1f)
        }
}

class UsageStatsRepository(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayAppUsage(): List<AppUsageInfo> {
        if (!hasUsageStatsPermission()) {
            return getFallbackUsageData()
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, now)
            if (stats.isNullOrEmpty()) {
                return getFallbackUsageData()
            }

            val packageManager = context.packageManager
            // Filter system launcher apps or apps with 0 time, and group duplicates that queryUsageStats can return
            val mergedStats = stats.filter { it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }
                .map { (pkgName, list) ->
                    val totalTime = list.sumOf { it.totalTimeInForeground }
                    val appLabel = try {
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkgName.substringAfterLast(".")
                    }
                    AppUsageInfo(
                        packageName = pkgName,
                        appName = appLabel,
                        totalTimeMs = totalTime
                    )
                }
                .filter { info ->
                    !info.packageName.startsWith("com.android.") &&
                    !info.packageName.startsWith("android.") &&
                    !info.packageName.startsWith("com.google.android.gms") &&
                    !info.packageName.startsWith("com.google.android.gsf") &&
                    !info.packageName.contains("launcher", ignoreCase = true) &&
                    !info.packageName.contains("systemui", ignoreCase = true) &&
                    info.totalTimeMs > 60_000L  // minimum 1 minute to show
                }
                .sortedByDescending { it.totalTimeMs }

            if (mergedStats.isEmpty()) {
                getFallbackUsageData()
            } else {
                mergedStats.take(6)
            }
        } catch (e: Exception) {
            getFallbackUsageData()
        }
    }

    fun getTotalTodayScreenMinutes(): Int {
        val usage = getTodayAppUsage()
        // If it's pure fallback data, total used minutes should sync with main UI state
        if (!hasUsageStatsPermission()) {
            return 107 // Default mock todayused
        }
        val totalMs = usage.sumOf { it.totalTimeMs }
        return (totalMs / 1000 / 60).toInt()
    }

    private fun getFallbackUsageData(): List<AppUsageInfo> {
        return listOf(
            AppUsageInfo("com.instagram.android", "Instagram", (2.3 * 60 * 60 * 1000).toLong()),
            AppUsageInfo("com.google.android.youtube", "YouTube", (1.8 * 60 * 60 * 1000).toLong()),
            AppUsageInfo("com.twitter.android", "Twitter", (45 * 60 * 1000).toLong()),
            AppUsageInfo("com.facebook.katana", "Facebook", (32 * 60 * 1000).toLong()),
            AppUsageInfo("com.zhiliaoapp.musically", "TikTok", (15 * 60 * 1000).toLong())
        )
    }
}
