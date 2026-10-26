package com.sukot.messenger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.isLoggedIn(this)) {
            goNext()
            return
        }
        setContentView(R.layout.activity_main)

        val etUser = findViewById<TextInputEditText>(R.id.etUser)
        val etName = findViewById<TextInputEditText>(R.id.etName)

        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            val u = etUser.text.toString().trim().lowercase()
            val n = etName.text.toString().trim()
            if (!Regex("^[a-z0-9_]{3,20}$").matches(u)) {
                etUser.error = getString(R.string.err_username)
                return@setOnClickListener
            }
            if (n.isEmpty()) {
                etName.error = getString(R.string.err_name)
                return@setOnClickListener
            }
            Prefs.set(this, "username", u)
            Prefs.set(this, "display", n)
            goNext()
        }
    }

    private fun goNext() {
        startActivity(Intent(this, ChatsActivity::class.java))
        finish()
    }
}
