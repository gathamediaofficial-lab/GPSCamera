package com.gatha.gpscamera

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases update lookup. Set OWNER and REPOSITORY to the public GitHub repository
 * before publishing. Android always requires the user to approve APK installation.
 */
internal object GitHubUpdateChecker {
    // TODO: Replace after the GitHub repository is created.
    const val OWNER = "YOUR_GITHUB_USERNAME"
    const val REPOSITORY = "gps-camera"

    data class Result(val currentVersion: String, val latestVersion: String, val releaseUrl: String, val hasUpdate: Boolean)

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val current = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.9.0"
        } catch (_: Exception) { "1.9.0" }

        require(OWNER != "YOUR_GITHUB_USERNAME") { "GitHub repository is not configured yet." }
        val connection = (URL("https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GPS-Camera-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub returned HTTP $code. Check the repository and its published releases.")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val url = json.optString("html_url", "https://github.com/$OWNER/$REPOSITORY/releases")
            if (tag.isBlank()) throw IllegalStateException("Latest release has no version tag.")
            Result(current, tag, url, compareVersions(tag, current) > 0)
        } finally { connection.disconnect() }
    }

    fun openRelease(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }; val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
