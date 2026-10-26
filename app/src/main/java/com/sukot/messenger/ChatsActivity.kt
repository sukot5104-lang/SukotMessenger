package com.sukot.messenger

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject

class ChatsActivity : AppCompatActivity() {

    private lateinit var adapter: ChatListAdapter
    private val db by lazy { Db(this) }
    private lateinit var tvState: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvAvatarMe: TextView
    private lateinit var tvMeInfo: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        ContextCompat.startForegroundService(this, Intent(this, MqttService::class.java))
        Mqtt.connect(this, Prefs.myPhone(this))

        tvAvatarMe = findViewById(R.id.tvAvatarMe)
        tvMeInfo = findViewById(R.id.tvMeInfo)
        findViewById<View>(R.id.barProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        tvState = findViewById(R.id.tvState)
        tvEmpty = findViewById(R.id.tvEmpty)

        val rv = findViewById<RecyclerView>(R.id.rvChats)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ChatListAdapter(
            onClick = { peer ->
                startActivity(Intent(this, ChatActivity::class.java).putExtra("peer", peer))
            },
            onLong = { peer -> confirmDelete(peer) }
        )
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { showAddDialog() }
        refreshProfileBar()
        refresh()
    }

    private fun refreshProfileBar() {
        val name = Prefs.myName(this)
        tvAvatarMe.text = name.take(1)
        tvAvatarMe.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Util.avatarColor(Prefs.myPhone(this)))
        tvMeInfo.text = getString(R.string.me_info, name, Util.formatPhone(Prefs.myPhone(this)))
    }

    private fun confirmDelete(peer: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_chat)
            .setMessage(R.string.del_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                db.deletePeer(peer)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_add, null)
        val etPhone = v.findViewById<TextInputEditText>(R.id.etPhone)
        val etName = v.findViewById<TextInputEditText>(R.id.etName)
        val tvMember = v.findViewById<TextView>(R.id.tvMember)

        etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val p = Util.normalizePhone(s?.toString() ?: "")
                tvMember.text = ""
                if (p.isNotBlank()) {
                    val server = Prefs.server(this@ChatsActivity)
                    if (server.isNotBlank()) {
                        Http.get("$server/api/users/exists?phone=$p") { ok, body ->
                            if (ok) runCatching {
                                val j = JSONObject(body)
                                val reg = j.optBoolean("registered")
                                tvMember.setText(if (reg) R.string.member_yes else R.string.member_no)
                                tvMember.setTextColor(
                                    if (reg) getColor(R.color.primary) else getColor(R.color.gray)
                                )
                            }
                        }
                    }
                }
            }
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_contact)
            .setView(v)
            .setPositiveButton(R.string.open_chat) { _, _ ->
                val p = Util.normalizePhone(etPhone.text.toString())
                if (p.isBlank()) {
                    Toast.makeText(this, R.string.err_phone, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) db.setContact(p, name)
                else if (db.contactName(p) == null) db.setContact(p, Util.formatPhone(p))
                Mqtt.subscribePresence(p)
                startActivity(Intent(this, ChatActivity::class.java).putExtra("peer", p))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        adapter.submit(db.chatList())
        tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        tvState.setText(
            when {
                Mqtt.connected -> R.string.state_online
                Prefs.myPhone(this).isNotBlank() -> R.string.state_connecting
                else -> R.string.state_offline
            }
        )
    }

    override fun onResume() {
        super.onResume()
        AppState.foreground = true
        val f = IntentFilter().apply {
            addAction(Mqtt.ACTION_MSG)
            addAction(Mqtt.ACTION_STATE)
            addAction(Mqtt.ACTION_PRESENCE)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshProfileBar()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        AppState.foreground = false
        runCatching { unregisterReceiver(receiver) }
    }
}
