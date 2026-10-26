package com.sukot.messenger

import java.util.Calendar
import java.util.Locale

object Util {

    fun toLatinDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch in '۰'..'۹' -> sb.append(('0' + (ch - '۰')))
                ch in '٠'..'٩' -> sb.append(('0' + (ch - '٠')))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** 0912.../98912.../+98.../0098.../۹۱۲... → 989123456789 */
    fun normalizePhone(input: String): String {
        var d = toLatinDigits(input).filter { it.isDigit() }
        if (d.startsWith("0098")) d = d.substring(4)
        else if (d.startsWith("98") && d.length >= 12) d = d.substring(2)
        if (d.startsWith("0") && d.length == 11) d = "98" + d.substring(1)
        else if (d.length == 10 && d.startsWith("9")) d = "98$d"
        return if (Regex("^989\\d{9}$").matches(d)) d else ""
    }

    /** 989123456789 → 0912 345 6789 */
    fun formatPhone(p: String): String {
        if (!p.startsWith("98") || p.length != 12) return p
        val local = "0" + p.substring(2)
        return "${local.substring(0, 4)} ${local.substring(4, 7)} ${local.substring(7)}"
    }

    fun avatarColor(seed: String): Int {
        val palette = intArrayOf(
            0xFF00897B.toInt(), 0xFF3949AB.toInt(), 0xFFD81B60.toInt(), 0xFFF4511E.toInt(),
            0xFF6D4C41.toInt(), 0xFF5E35B1.toInt(), 0xFF00838F.toInt(), 0xFF2E7D32.toInt()
        )
        var h = 0
        for (c in seed) h = h * 31 + c.code
        return palette[((h % palette.size) + palette.size) % palette.size]
    }

    fun timeFa(ts: Long): String =
        android.icu.text.SimpleDateFormat("HH:mm", Locale("fa")).format(java.util.Date(ts))

    fun shortDateFa(ts: Long): String =
        android.icu.text.SimpleDateFormat("d MMM", Locale("fa")).format(java.util.Date(ts))

    fun dateTimeFa(ts: Long): String =
        android.icu.text.SimpleDateFormat("d MMMM yyyy، HH:mm", Locale("fa")).format(java.util.Date(ts))

    /** اگر نسبت به پیام قبلی روز جدیدی است، برچسب تاریخ برگردانده می‌شود */
    fun dateLabel(ts: Long, prevTs: Long?): String? {
        if (prevTs != null && sameDay(ts, prevTs)) return null
        return when {
            sameDay(ts, System.currentTimeMillis()) -> "امروز"
            sameDay(ts, System.currentTimeMillis() - 86400000L) -> "دیروز"
            else -> android.icu.text.SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa")).format(java.util.Date(ts))
        }
    }

    fun sameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
