package com.sukot.messenger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class NameActivity : AppCompatActivity() {

    private var phone = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_name)

        phone = intent.getStringExtra("phone") ?: Prefs.myPhone(this)
        if (phone.isBlank()) {
            finish()
            return
        }

        val tvPhone = findViewById<TextView>(R.id.tvNamePhone)
        tvPhone.text = Util.formatPhone(phone)

        val etName = findViewById<TextInputEditText>(R.id.etName)
        findViewById<MaterialButton>(R.id.btnFinish).setOnClickListener {
            val n = etName.text.toString().trim()
            if (n.isEmpty()) {
                etName.error = getString(R.string.err_name)
                return@setOnClickListener
            }
            Prefs.set(this, "phone", phone)
            Prefs.set(this, "display", n)
            ContextCompat_startService()
            startActivity(Intent(this, ChatsActivity::class.java))
            finishAffinity()
        }
    }

    private fun ContextCompat_startService() {
        androidx.core.content.ContextCompat.startForegroundService(
            this, Intent(this, MqttService::class.java)
        )
    }
}
