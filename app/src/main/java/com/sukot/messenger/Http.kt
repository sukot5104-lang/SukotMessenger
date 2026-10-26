package com.sukot.messenger

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object Http {

    private val main = Handler(Looper.getMainLooper())

    private fun readAll(c: HttpURLConnection): String {
        val stream = try {
            c.inputStream
        } catch (e: Exception) {
            c.errorStream
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    fun postJson(urlStr: String, body: String, cb: (Boolean, String) -> Unit) {
        Thread {
            var ok = false
            var resp = ""
            try {
                val c = URL(urlStr).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                resp = readAll(c)
                ok = c.responseCode in 200..299
                c.disconnect()
            } catch (e: Exception) {
                resp = e.message ?: "network error"
            }
            main.post { cb(ok, resp) }
        }.start()
    }

    fun get(urlStr: String, cb: (Boolean, String) -> Unit) {
        Thread {
            var ok = false
            var resp = ""
            try {
                val c = URL(urlStr).openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.connectTimeout = 8000
                c.readTimeout = 12000
                resp = readAll(c)
                ok = c.responseCode in 200..299
                c.disconnect()
            } catch (e: Exception) {
                resp = e.message ?: "network error"
            }
            main.post { cb(ok, resp) }
        }.start()
    }
}
