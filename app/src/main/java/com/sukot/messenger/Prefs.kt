package com.sukot.messenger

import android.content.Context

object Prefs {
    private const val FILE = "sukot_prefs"

    fun get(context: Context, key: String, def: String = ""): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key, def) ?: def

    fun set(context: Context, key: String, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    fun isLoggedIn(context: Context): Boolean = get(context, "username").isNotBlank()

    fun myUsername(context: Context): String = get(context, "username").lowercase().trim()

    fun myName(context: Context): String = get(context, "display").ifBlank { myUsername(context) }
}
