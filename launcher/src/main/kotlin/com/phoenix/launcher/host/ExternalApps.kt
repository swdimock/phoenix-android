package com.phoenix.launcher.host

import android.content.Context
import android.content.Intent
import android.util.Log

/** An external app the launcher hands a mode over to (vendor radio, vendor BT phone, ...). */
interface ExternalApp {
    /** The intent that would be launched, or null if no matching app is installed. */
    fun resolve(): Intent?

    /** Starts the app. Returns false if it is not installed or refused to start. */
    fun launch(): Boolean
}

/**
 * Resolves [primaryPackage]'s launch intent, falling back to any `MAIN` activity in a package starting
 * with [fallbackPrefix] whose label contains one of [fallbackLabels] (case-insensitive).
 */
class VendorApp(
    context: Context,
    private val primaryPackage: () -> String,
    private val fallbackPrefix: String? = null,
    private val fallbackLabels: List<String> = emptyList(),
) : ExternalApp {

    constructor(context: Context, primaryPackage: String, fallbackPrefix: String?, fallbackLabels: List<String>) :
        this(context, { primaryPackage }, fallbackPrefix, fallbackLabels)

    private val context = context.applicationContext
    private val pm = context.packageManager

    override fun resolve(): Intent? = (pm.getLaunchIntentForPackage(primaryPackage()) ?: findFallback())
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun launch(): Boolean {
        val intent = resolve() ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "launch ${intent.component ?: intent.`package`} failed: $e")
            false
        }
    }

    private fun findFallback(): Intent? {
        val prefix = fallbackPrefix ?: return null
        if (fallbackLabels.isEmpty()) return null
        val match = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), 0).firstOrNull { ri ->
            val info = ri.activityInfo
            info.packageName.startsWith(prefix) && info.exported &&
                ri.loadLabel(pm).toString().let { label -> fallbackLabels.any { label.contains(it, ignoreCase = true) } }
        } ?: return null
        return Intent(Intent.ACTION_MAIN).setClassName(match.activityInfo.packageName, match.activityInfo.name)
    }

    private companion object {
        const val TAG = "VendorApp"
    }
}
