package com.sukot.messenger

object AppState {
    @Volatile var foreground: Boolean = false
    @Volatile var openChat: String? = null
}
