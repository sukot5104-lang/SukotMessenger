package com.sukot.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** خواندن خودکار کد تایید از پیامک (فقط وقتی صفحه کد باز است) */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppState.waitingCode) return

        val full = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .joinToString(" ") { it.displayMessageBody ?: "" }
        if (full.isBlank()) return

        val latin = Util.toLatinDigits(full)
        val m = Regex("(?:کد|code)[^0-9]{0,24}([0-9]{4,8})").find(latin)
            ?: Regex("\\b([0-9]{4,8})\\b").find(latin)
        val code = m?.groupValues?.get(1) ?: return

        context.sendBroadcast(
            Intent(Mqtt.ACTION_SMS_CODE)
                .setPackage(context.packageName)
                .putExtra("code", code)
        )
    }
}
