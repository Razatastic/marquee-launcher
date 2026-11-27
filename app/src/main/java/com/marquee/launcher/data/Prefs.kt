package com.marquee.launcher.data

import android.content.Context

/** The Home row: which apps, in what order. Survives reboots and updates. */
object Prefs {
    private const val FILE = "marquee"
    private const val KEY_PINNED = "pinned"

    fun pinned(context: Context): List<String> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PINNED, null)
            ?: return AppRepo.defaultPinned
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setPinned(context: Context, pkgs: List<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED, pkgs.joinToString(","))
            .apply()
    }
}
