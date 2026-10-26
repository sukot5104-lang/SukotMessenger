package com.sukot.messenger

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

class CodeActivity : AppCompatActivity() {

    private var phone = ""
    private lateinit var etCode: EditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var tvResend: TextView
    private lateinit var tvError: TextView
    private var timer: CountDownTimer? = null

    private val smsCodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val code = intent?.getStringExtra("code") ?: return
            etCode.setText(code)
            verify()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code)

        phone = intent.getStringExtra("phone") ?: ""
        if (phone.isBlank()) {
            finish()
            return
        }
        AppState.waitingCode = true

        val tvPhone = findViewById<TextView>(R.id.tvCodePhone)
        tvPhone.text = Util.formatPhone(phone)

        etCode = findViewById(R.id.etCode)
        btnVerify = findViewById(R.id.btnVerify)
        tvResend = findViewById(R.id.tvResend)
        tvError = findViewById(R.id.tvError)

        val devCode = intent.getStringExtra("dev_code") ?: ""
        if (devCode.isNotBlank()) {
            etCode.setText(devCode)
            tvError.text = getString(R.string.dev_code_note)
            tvError.visibility = View.VISIBLE
        }

        btnVerify.setOnClickListener { verify() }
        tvResend.setOnClickListener { if (tvResend.isEnabled) requestAgain() }

        askSmsPermission()
        startCountdown()
    }

    private fun askSmsPermission() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 20)
        }
    }

    private fun startCountdown() {
        tvResend.isEnabled = false
        timer?.cancel()
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(ms: Long) {
                tvResend.text = getString(R.string.resend_in, ms / 1000)
            }

            override fun onFinish() {
                tvResend.isEnabled = true
                tvResend.text = getString(R.string.btn_resend)
            }
        }.start()
    }

    private fun requestAgain() {
        val server = Prefs.server(this)
        if (server.isBlank()) return
        tvError.visibility = View.GONE
        Http.postJson("$server/api/otp/request", JSONObject().put("phone", phone).toString()) { ok, body ->
            if (ok) {
                Toast.makeText(this, R.string.code_sent, Toast.LENGTH_SHORT).show()
                val j = runCatching { JSONObject(body) }.getOrNull()
                val devCode = j?.optString("dev_code", "") ?: ""
                if (devCode.isNotBlank()) {
                    etCode.setText(devCode)
                    tvError.text = getString(R.string.dev_code_note)
                    tvError.visibility = View.VISIBLE
                }
                startCountdown()
            } else {
                val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                tvError.text = err ?: getString(R.string.conn_error_short)
                tvError.visibility = View.VISIBLE
                startCountdown()
            }
        }
    }

    private fun verify() {
        val code = Util.toLatinDigits(etCode.text.toString()).replace(Regex("[^0-9]"), "")
        if (code.length < 4) {
            tvError.text = getString(R.string.err_code)
            tvError.visibility = View.VISIBLE
            return
        }
        val server = Prefs.server(this)
        if (server.isBlank()) return
        btnVerify.isEnabled = false
        tvError.visibility = View.GONE
        Http.postJson(
            "$server/api/otp/verify",
            JSONObject().put("phone", phone).put("code", code).toString()
        ) { ok, body ->
            btnVerify.isEnabled = true
            if (ok) {
                val j = runCatching { JSONObject(body) }.getOrNull() ?: JSONObject()
                Prefs.set(this, "phone", j.optString("phone", phone))
                Prefs.set(this, "token", j.optString("token", ""))
                startActivity(Intent(this, NameActivity::class.java).putExtra("phone", phone))
                finish()
            } else {
                val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                tvError.text = err ?: getString(R.string.conn_error_short)
                tvError.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(Mqtt.ACTION_SMS_CODE)
        ContextCompat.registerReceiver(this, smsCodeReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(smsCodeReceiver) }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppState.waitingCode = false
        timer?.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // اگر کاربر اجازه نداد، کد را دستی وارد می‌کند
    }
}
