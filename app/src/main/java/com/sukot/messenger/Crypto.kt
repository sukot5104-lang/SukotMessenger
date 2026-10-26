package com.sukot.messenger

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    private const val SECRET = "SukotMessenger::v1::shared"

    fun topicFor(user: String): String {
        val u = user.lowercase().trim()
        val d = MessageDigest.getInstance("SHA-256")
            .digest("sukot-v1|user|$u|$SECRET".toByteArray(Charsets.UTF_8))
        return "sukot/v1/m/" + d.joinToString("") { "%02x".format(it) }.substring(0, 24)
    }

    fun clientIdFor(user: String): String {
        val u = user.lowercase().trim()
        val d = MessageDigest.getInstance("SHA-256")
            .digest("sukot-v1|cid|$u|$SECRET".toByteArray(Charsets.UTF_8))
        return "sukot-" + d.joinToString("") { "%02x".format(it) }.substring(0, 20)
    }

    private fun keyFor(users: List<String>): SecretKeySpec {
        val norm = users.map { it.lowercase().trim() }.sorted().joinToString("|")
        val d = MessageDigest.getInstance("SHA-256")
            .digest((norm + "||" + SECRET).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(d, "AES")
    }

    fun encrypt(text: String, users: List<String>): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keyFor(users))
        val iv = c.iv
        val enc = c.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + enc, Base64.NO_WRAP)
    }

    fun decrypt(data: String, users: List<String>): String {
        val raw = Base64.decode(data, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keyFor(users), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }
}
