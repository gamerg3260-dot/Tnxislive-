package com.example.router

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val normalizedName: String
)

object GenericAppLauncher {
    private const val TAG = "GenericAppLauncher"

    // Comprehensive phonetic / colloquial transliterations and aliases (Hindi/Hinglish/English)
    private val appAliases = mapOf(
        // Social & Messaging
        "fb" to "facebook",
        "f b" to "facebook",
        "facebook" to "facebook",
        "फेसबुक" to "facebook",
        "facebook lite" to "facebook lite",
        "fb lite" to "facebook lite",
        "meta" to "facebook",
        "insta" to "instagram",
        "ig" to "instagram",
        "इंस्टा" to "instagram",
        "इंस्टाग्राम" to "instagram",
        "instagram" to "instagram",
        "whatsapp" to "whatsapp",
        "whats app" to "whatsapp",
        "व्हाट्सएप" to "whatsapp",
        "व्हाट्सऐप" to "whatsapp",
        "wp" to "whatsapp",
        "wa" to "whatsapp",
        "whatsapp business" to "whatsapp business",
        "telegram" to "telegram",
        "tg" to "telegram",
        "टेलीग्राम" to "telegram",
        "snapchat" to "snapchat",
        "snap" to "snapchat",
        "स्नैपचैट" to "snapchat",
        "twitter" to "x",
        "ट्विटर" to "x",
        "x" to "x",
        "threads" to "threads",
        "linkedin" to "linkedin",
        "लिंक्डइन" to "linkedin",

        // Video & Media
        "yt" to "youtube",
        "you tube" to "youtube",
        "यू ट्यूब" to "youtube",
        "यूट्यूब" to "youtube",
        "youtube" to "youtube",
        "yt music" to "youtube music",
        "youtube music" to "youtube music",
        "spotify" to "spotify",
        "स्पॉटिफ़ाई" to "spotify",
        "netflix" to "netflix",
        "नेटफ्लिक्स" to "netflix",
        "hotstar" to "disney+ hotstar",
        "डिज्नी हॉटस्टार" to "disney+ hotstar",
        "jiocinema" to "jio cinema",
        "जियो सिनेमा" to "jio cinema",
        "prime video" to "prime video",
        "amazon prime" to "prime video",

        // Google & Productivity
        "chrome" to "chrome",
        "क्रोम" to "chrome",
        "गूगल क्रोम" to "chrome",
        "google chrome" to "chrome",
        "browser" to "chrome",
        "ब्राउज़र" to "chrome",
        "google" to "google",
        "गूगल" to "google",
        "gmail" to "gmail",
        "जीमेल" to "gmail",
        "मेल" to "gmail",
        "email" to "gmail",
        "maps" to "maps",
        "गूगल मैप्स" to "maps",
        "मैप्स" to "maps",
        "नक्शा" to "maps",
        "google maps" to "maps",
        "drive" to "drive",
        "गूगल ड्राइव" to "drive",
        "photos" to "photos",
        "फोटोस" to "photos",
        "google photos" to "photos",
        "play store" to "play store",
        "प्ले स्टोर" to "play store",
        "playstore" to "play store",
        "store" to "play store",

        // Utilities & System
        "calculator" to "calculator",
        "कैलकुलेटर" to "calculator",
        "calc" to "calculator",
        "हिसाब" to "calculator",
        "clock" to "clock",
        "घड़ी" to "clock",
        "अलार्म" to "clock",
        "क्लॉक" to "clock",
        "calendar" to "calendar",
        "कैलेंडर" to "calendar",
        "camera" to "camera",
        "कैमरा" to "camera",
        "photo" to "camera",
        "gallery" to "gallery",
        "गैलरी" to "gallery",
        "तस्वीर" to "gallery",
        "settings" to "settings",
        "सेटिंग" to "settings",
        "सेटिंग्स" to "settings",
        "phone" to "phone",
        "dialer" to "phone",
        "फोन" to "phone",
        "डायलर" to "phone",
        "contacts" to "contacts",
        "कॉन्टैक्ट्स" to "contacts",
        "contacts list" to "contacts",
        "messages" to "messages",
        "संदेश" to "messages",
        "sms" to "messages",
        "files" to "files",
        "file manager" to "files",
        "फ़ाइलें" to "files",

        // Shopping & Payments
        "amazon" to "amazon",
        "अमेज़न" to "amazon",
        "flipkart" to "flipkart",
        "फ्लिपकार्ट" to "flipkart",
        "paytm" to "paytm",
        "पेटीएम" to "paytm",
        "phonepe" to "phonepe",
        "फोनपे" to "phonepe",
        "gpay" to "google pay",
        "google pay" to "google pay",
        "जीपे" to "google pay",
        "bhim" to "bhim",
        "zomato" to "zomato",
        "ज़ोमैटो" to "zomato",
        "swiggy" to "swiggy",
        "स्वीगी" to "swiggy",
        "uber" to "uber",
        "ऊबर" to "uber",
        "ola" to "ola",
        "ओला" to "ola",
        "truecaller" to "truecaller",
        "ट्रूकॉलर" to "truecaller"
    )

    // Direct well-known package name associations for 100% fail-safe resolution
    private val wellKnownPackages = mapOf(
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.android"),
        "facebook lite" to listOf("com.facebook.lite", "com.facebook.katana"),
        "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "whatsapp business" to listOf("com.whatsapp.w4b", "com.whatsapp"),
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "spotify" to listOf("com.spotify.music", "com.spotify.lite"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        "snapchat" to listOf("com.snapchat.android"),
        "x" to listOf("com.twitter.android", "com.twitter.android.lite"),
        "amazon" to listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"),
        "flipkart" to listOf("com.flipkart.android"),
        "paytm" to listOf("net.one97.paytm"),
        "phonepe" to listOf("com.phonepe.app"),
        "google pay" to listOf("com.google.android.apps.nbu.paisa.user"),
        "zomato" to listOf("com.application.zomato"),
        "swiggy" to listOf("in.swiggy.android"),
        "truecaller" to listOf("com.truecaller"),
        "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "settings" to listOf("com.android.settings")
    )

    @Volatile
    private var cachedInstalledApps: List<InstalledAppInfo>? = null

    /**
     * Invalidate cached app list and perform a fresh query.
     */
    fun invalidateCacheAndRefresh(context: Context): List<InstalledAppInfo> {
        cachedInstalledApps = null
        val freshList = getInstalledLaunchableApps(context, forceFresh = true)
        Log.i(TAG, "APP_LIST_REFRESHED: Total installed apps updated to ${freshList.size}")
        return freshList
    }

    /**
     * Retrieves all launchable installed apps on the device using PackageManager.
     */
    fun getInstalledLaunchableApps(context: Context, forceFresh: Boolean = false): List<InstalledAppInfo> {
        if (!forceFresh) {
            cachedInstalledApps?.let { return it }
        }

        val pm = context.packageManager
        val list = mutableListOf<InstalledAppInfo>()
        val seenPackages = mutableSetOf<String>()

        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo.packageName
                if (seenPackages.add(pkg)) {
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher activities", e)
        }

        // Fallback: Query all installed applications to capture everything
        try {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                val pkg = app.packageName
                if (!seenPackages.contains(pkg)) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        seenPackages.add(pkg)
                        val label = try {
                            pm.getApplicationLabel(app).toString()
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
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Secondary getInstalledApplications inspection", e)
        }

        Log.d(TAG, "[InstalledApps] Total launchable apps retrieved: ${list.size}")
        cachedInstalledApps = list
        return list
    }

    /**
     * Matches the user's spoken app name against installed apps using multi-tier fuzzy matching.
     * Returns the matched app info, or null if not found.
     */
    fun findBestAppMatch(spokenName: String, installedApps: List<InstalledAppInfo>): InstalledAppInfo? {
        val cleanSpoken = normalizeString(spokenName)
        if (cleanSpoken.isBlank()) return null

        Log.i(TAG, "APP_OPEN_SEARCH: searching installed apps for '$spokenName' (clean: '$cleanSpoken') across ${installedApps.size} apps")

        // 1. Check alias dictionary
        val aliasedName = appAliases[cleanSpoken] ?: cleanSpoken
        val target = normalizeString(aliasedName)

        // 2. Direct Well-Known Package Lookup
        val knownPkgList = wellKnownPackages[target] ?: wellKnownPackages[cleanSpoken]
        if (knownPkgList != null) {
            for (pkg in knownPkgList) {
                val found = installedApps.find { it.packageName.equals(pkg, ignoreCase = true) }
                if (found != null) {
                    Log.i(TAG, "APP_OPEN_MATCH: ${found.appName} (${found.packageName}) via WellKnownPackage")
                    return found
                }
            }
        }

        // 3. Exact Normalized Name Match
        val exactMatch = installedApps.find { it.normalizedName == target || it.normalizedName == cleanSpoken }
        if (exactMatch != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${exactMatch.appName} (${exactMatch.packageName}) via ExactName")
            return exactMatch
        }

        // 4. Starts-with / Prefix Match (e.g. "face" -> "Facebook", "insta" -> "Instagram")
        val prefixMatch = installedApps.find {
            it.normalizedName.startsWith(target) || target.startsWith(it.normalizedName)
        }
        if (prefixMatch != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${prefixMatch.appName} (${prefixMatch.packageName}) via Prefix")
            return prefixMatch
        }

        // 5. Contains Substring Match (e.g. "facebook" in "Facebook Lite", "tube" in "YouTube")
        val containsMatch = installedApps.find {
            it.normalizedName.contains(target) || (target.length >= 3 && target.contains(it.normalizedName))
        }
        if (containsMatch != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${containsMatch.appName} (${containsMatch.packageName}) via Substring")
            return containsMatch
        }

        // 6. Word-boundary / Token Match
        val targetTokens = target.split(" ").filter { it.length >= 2 }
        val tokenMatch = installedApps.find { app ->
            val appTokens = app.normalizedName.split(" ", "_", "-")
            targetTokens.any { t -> appTokens.any { at -> at == t || (t.length >= 3 && at.startsWith(t)) } }
        }
        if (tokenMatch != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${tokenMatch.appName} (${tokenMatch.packageName}) via TokenMatch")
            return tokenMatch
        }

        // 7. Package Name Substring Match (e.g. "katana" or "facebook" in "com.facebook.katana")
        val packageMatch = installedApps.find {
            it.packageName.lowercase().contains(target) || (target == "facebook" && it.packageName.contains("katana"))
        }
        if (packageMatch != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${packageMatch.appName} (${packageMatch.packageName}) via PackageSubstring")
            return packageMatch
        }

        // 8. Levenshtein Distance Fuzzy Matching
        var bestScore = Double.MAX_VALUE
        var bestApp: InstalledAppInfo? = null

        for (app in installedApps) {
            val dist = computeLevenshteinDistance(target, app.normalizedName)
            val maxLen = maxOf(target.length, app.normalizedName.length)
            val normalizedDist = dist.toDouble() / maxLen.coerceAtLeast(1)

            val threshold = if (maxLen <= 4) 0.25 else 0.40
            if (normalizedDist < threshold && normalizedDist < bestScore) {
                bestScore = normalizedDist
                bestApp = app
            }
        }

        if (bestApp != null) {
            Log.i(TAG, "APP_OPEN_MATCH: ${bestApp.appName} (${bestApp.packageName}) via LevenshteinScore($bestScore)")
        } else {
            Log.w(TAG, "APP_OPEN_MATCH: NOT-FOUND for '$spokenName' (target '$target') across ${installedApps.size} apps")
        }

        return bestApp
    }

    /**
     * Launches the matched app via Intent with clean task flags.
     */
    fun launchApp(context: Context, appInfo: InstalledAppInfo): Boolean {
        return try {
            val pm = context.packageManager
            var intent = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (intent == null) {
                // Fail-safe intent creation using main intent and package name
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    `setPackage`(appInfo.packageName)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            Log.i(TAG, "APP_OPEN_LAUNCH: success for package ${appInfo.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "APP_OPEN_LAUNCH: exception/error message - ${e.localizedMessage}", e)
            false
        }
    }

    fun normalizeString(input: String): String {
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
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

