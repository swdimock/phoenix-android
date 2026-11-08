package com.phoenix.launcher.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SelfUpdater] against `https://api.github.com/repos/<owner>/<repo>/releases/latest`. The first `.apk`
 * asset of a release whose tag is newer than [currentVersion] is downloaded to the cache and opened with
 * the package installer (requires the user to allow installs from this app once).
 */
class GithubReleasesUpdater(
    context: Context,
    private val owner: String,
    private val repo: String,
    private val currentVersion: String,
) : SelfUpdater {

    private val context = context.applicationContext

    override val isEnabled: Boolean get() = owner.isNotBlank() && repo.isNotBlank()

    private val latestUrl get() = "https://api.github.com/repos/$owner/$repo/releases/latest"

    override suspend fun check(): UpdateCheck {
        if (!isEnabled) return UpdateCheck.Disabled
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(httpGet(latestUrl))
                val tag = json.getString("tag_name")
                if (!isNewerVersion(tag, currentVersion)) return@withContext UpdateCheck.UpToDate(tag)
                val assets = json.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0))
                    .map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?: return@withContext UpdateCheck.Error("release $tag has no .apk asset")
                UpdateCheck.Available(tag, apk.getString("browser_download_url"))
            } catch (t: Throwable) {
                UpdateCheck.Error(t.message ?: t.toString())
            }
        }
    }

    override suspend fun downloadAndInstall(update: UpdateCheck.Available): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "launcher-${update.tag}.apk")
            open(update.apkUrl).run {
                try {
                    inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                } finally {
                    disconnect()
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }
    }

    private fun httpGet(url: String): String = open(url).run {
        try {
            if (responseCode !in 200..299) error("HTTP $responseCode from $url")
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "phoenix-launcher/$currentVersion")
        }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
