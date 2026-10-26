package com.sukot.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvAvatar: TextView
    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvServer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvAvatar = findViewById(R.id.tvBigAvatar)
        tvName = findViewById(R.id.tvProfileName)
        tvPhone = findViewById(R.id.tvProfilePhone)
        tvServer = findViewById(R.id.tvProfileServer)

        refresh()

        findViewById<View>(R.id.btnEditName).setOnClickListener { showEditName() }
        findViewById<View>(R.id.rowPhone).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("phone", Util.formatPhone(Prefs.myPhone(this))))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.rowServer).setOnClickListener { showEditServer() }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val text = getString(
                R.string.share_card_text,
                Prefs.myName(this), Util.formatPhone(Prefs.myPhone(this))
            )
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, getString(R.string.btn_share_card)))
        }
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.btn_logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.btn_logout) { _, _ ->
                    Mqtt.shutdown()
                    stopService(Intent(this, MqttService::class.java))
                    Prefs.remove(this, "phone")
                    Prefs.remove(this, "display")
                    Prefs.remove(this, "token")
                    startActivity(Intent(this, PhoneActivity::class.java))
                    finishAffinity()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun refresh() {
        val name = Prefs.myName(this)
        val phone = Prefs.myPhone(this)
        tvName.text = name
        tvAvatar.text = name.take(1)
        tvAvatar.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Util.avatarColor(phone))
        tvPhone.text = Util.formatPhone(phone)
        val s = Prefs.server(this)
        tvServer.text = if (s.isBlank()) getString(R.string.server_unset) else s
    }

    private fun showEditName() {
        val v = layoutInflater.inflate(R.layout.dialog_name, null)
        val et = v.findViewById<TextInputEditText>(R.id.etNameSingle)
        et.setText(Prefs.myName(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_name)
            .setView(v)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    Prefs.set(this, "display", n)
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditServer() {
        val v = layoutInflater.inflate(R.layout.dialog_server, null)
        val et = v.findViewById<TextInputEditText>(R.id.etServer)
        et.setText(Prefs.server(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_title)
            .setMessage(R.string.server_desc)
            .setView(v)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                Prefs.set(this, "server", et.text.toString().trim().trimEnd('/'))
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
