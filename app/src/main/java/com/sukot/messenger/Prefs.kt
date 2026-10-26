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

    fun remove(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }

    fun isLoggedIn(context: Context): Boolean = get(context, "phone").isNotBlank()

    fun myPhone(context: Context): String = get(context, "phone")

    fun myName(context: Context): String = get(context, "display").ifBlank { "کاربر سوکوت" }

    fun server(context: Context): String = get(context, "server").trim().trimEnd('/')

    fun token(context: Context): String = get(context, "token")
}
