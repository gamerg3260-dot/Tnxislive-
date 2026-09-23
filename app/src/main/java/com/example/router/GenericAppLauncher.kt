package com.example.router

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val normalizedName: String
)

object GenericAppLauncher {
    private const val TAG = "GenericAppLauncher"

    // Common phonetic / colloquial transliterations or shortcuts (Hindi/Hinglish to English)
    private val appAliases = mapOf(
        "insta" to "instagram",
        "ig" to "instagram",
        "इंस्टा" to "instagram",
        "इंस्टाग्राम" to "instagram",
        "yt" to "youtube",
        "यू ट्यूब" to "youtube",
        "यूट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp",
        "व्हाट्सऐप" to "whatsapp",
        "wp" to "whatsapp",
        "wa" to "whatsapp",
        "fb" to "facebook",
        "फेसबुक" to "facebook",
        "क्रोम" to "chrome",
        "गूगल क्रोम" to "chrome",
        "गूगल मैप्स" to "maps",
        "मैप्स" to "maps",
        "नक्शा" to "maps",
        "स्पॉटिफ़ाई" to "spotify",
        "गाना" to "music",
        "म्यूजिक" to "music",
        "जीमेल" to "gmail",
        "मेल" to "gmail",
        "प्ले स्टोर" to "play store",
        "स्टोर" to "play store",
        "कैलकुलेटर" to "calculator",
        "हिसाब" to "calculator",
        "घड़ी" to "clock",
        "अलार्म" to "clock",
        "क्लॉक" to "clock",
        "मैसेज" to "messages",
        "संदेश" to "messages",
        "कैमरा" to "camera",
        "फोटो" to "camera",
        "गैलरी" to "gallery",
        "तस्वीर" to "gallery",
        "सेटिंग" to "settings",
        "सेटिंग्स" to "settings"
    )

    /**
     * Retrieves all launchable installed apps on the device using PackageManager.
     */
    fun getInstalledLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher activities", e)
            emptyList()
        }

        val list = mutableListOf<InstalledAppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            val label = try {
                resolveInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast(".")
            }

            list.add(
                InstalledAppInfo(
                    appName = label,
                    packageName = pkg,
                    normalizedName = normalizeString(label)
                )
            )
        }

        // Also ensure fallback querying installed applications if queryIntentActivities returned few
        if (list.size < 5) {
            try {
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in installed) {
                    if (seenPackages.contains(app.packageName)) continue
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        val label = pm.getApplicationLabel(app).toString()
                        seenPackages.add(app.packageName)
                        list.add(
                            InstalledAppInfo(
                                appName = label,
                                packageName = app.packageName,
                                normalizedName = normalizeString(label)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback querying installed applications failed", e)
            }
        }

        return list
    }

    /**
     * Matches the user's spoken app name against all installed apps using fuzzy matching.
     * Returns the best match or null if no confident match is found.
     */
    fun findBestAppMatch(spokenName: String, installedApps: List<InstalledAppInfo>): InstalledAppInfo? {
        val cleanSpoken = normalizeString(spokenName)
        if (cleanSpoken.isBlank()) return null

        // Check alias lookup
        val aliasedName = appAliases[cleanSpoken] ?: cleanSpoken
        val target = normalizeString(aliasedName)

        // 1. Exact Match on normalized app name
        val exactMatch = installedApps.find { it.normalizedName == target }
        if (exactMatch != null) return exactMatch

        // 2. Starts with / Prefix Match
        val prefixMatch = installedApps.find {
            it.normalizedName.startsWith(target) || target.startsWith(it.normalizedName)
        }
        if (prefixMatch != null) return prefixMatch

        // 3. Contains Substring Match (e.g. "insta" in "instagram", "tube" in "youtube")
        val containsMatch = installedApps.find {
            it.normalizedName.contains(target) || (target.length >= 3 && target.contains(it.normalizedName))
        }
        if (containsMatch != null) return containsMatch

        // 4. Word boundary match
        val wordMatch = installedApps.find { app ->
            val words = app.normalizedName.split(" ", "_", "-")
            words.any { w -> w == target || (target.length >= 3 && w.startsWith(target)) }
        }
        if (wordMatch != null) return wordMatch

        // 5. Package Name Match (e.g. "com.whatsapp")
        val packageMatch = installedApps.find {
            it.packageName.lowercase().contains(target)
        }
        if (packageMatch != null) return packageMatch

        // 6. Levenshtein / Edit Distance Fuzzy Match
        var bestScore = Double.MAX_VALUE
        var bestApp: InstalledAppInfo? = null

        for (app in installedApps) {
            val dist = computeLevenshteinDistance(target, app.normalizedName)
            val maxLen = maxOf(target.length, app.normalizedName.length)
            val normalizedDist = dist.toDouble() / maxLen.coerceAtLeast(1)

            // Allow up to ~35% difference if strings are reasonably long
            val threshold = if (maxLen <= 4) 0.25 else 0.40
            if (normalizedDist < threshold && normalizedDist < bestScore) {
                bestScore = normalizedDist
                bestApp = app
            }
        }

        return bestApp
    }

    /**
     * Launches the matched app via Intent without needing Gemini API.
     */
    fun launchApp(context: Context, appInfo: InstalledAppInfo): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package: ${appInfo.packageName}", e)
            false
        }
    }

    private fun normalizeString(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s]"), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
