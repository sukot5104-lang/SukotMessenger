package com.sukot.messenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject

class PhoneActivity : AppCompatActivity() {

    private lateinit var tvServer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.isLoggedIn(this)) {
            goChats()
            return
        }
        setContentView(R.layout.activity_phone)

        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        tvServer = findViewById(R.id.tvServer)
        updateServerLabel()

        tvServer.setOnClickListener { showServerDialog() }
        findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            val phone = Util.normalizePhone(etPhone.text.toString())
            if (phone.isBlank()) {
                etPhone.error = getString(R.string.err_phone)
                return@setOnClickListener
            }
            requestOtp(phone)
        }
        findViewById<TextView>(R.id.btnDemo).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.demo_login)
                .setMessage(R.string.demo_confirm)
                .setPositiveButton(R.string.btn_continue) { _, _ ->
                    val phone = Util.normalizePhone(etPhone.text.toString())
                    if (phone.isBlank()) {
                        etPhone.error = getString(R.string.err_phone)
                        return@setPositiveButton
                    }
                    startActivity(
                        Intent(this, NameActivity::class.java)
                            .putExtra("phone", phone)
                            .putExtra("demo", true)
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateServerLabel() {
        val s = Prefs.server(this)
        tvServer.text = if (s.isBlank()) getString(R.string.server_unset) else getString(R.string.server_set, s)
    }

    private fun showServerDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_server, null)
        val et = v.findViewById<TextInputEditText>(R.id.etServer)
        et.setText(Prefs.server(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_title)
            .setMessage(R.string.server_desc)
            .setView(v)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                Prefs.set(this, "server", et.text.toString().trim().trimEnd('/'))
                updateServerLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestOtp(phone: String) {
        val server = Prefs.server(this)
        if (server.isBlank()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.need_server_title)
                .setMessage(R.string.need_server_msg)
                .setPositiveButton(R.string.server_set_btn) { _, _ -> showServerDialog() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        setBusy(true)
        Http.postJson("$server/api/otp/request", JSONObject().put("phone", phone).toString()) { ok, body ->
            setBusy(false)
            if (ok) {
                val j = runCatching { JSONObject(body) }.getOrNull() ?: JSONObject()
                val devCode = j.optString("dev_code", "")
                startActivity(
                    Intent(this, CodeActivity::class.java)
                        .putExtra("phone", phone)
                        .putExtra("dev_code", devCode)
                )
            } else {
                val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                if (err.isNullOrBlank()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.conn_error_title)
                        .setMessage(R.string.conn_error_msg)
                        .setPositiveButton(R.string.demo_login) { _, _ ->
                            startActivity(
                                Intent(this, NameActivity::class.java)
                                    .putExtra("phone", phone)
                                    .putExtra("demo", true)
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setBusy(b: Boolean) {
        findViewById<View>(R.id.progress).visibility = if (b) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnContinue).isEnabled = !b
    }

    private fun goChats() {
        startActivity(Intent(this, ChatsActivity::class.java))
        finish()
    }
}
