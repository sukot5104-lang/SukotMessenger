package com.sukot.messenger

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class ChatsActivity : AppCompatActivity() {

    private lateinit var adapter: ChatListAdapter
    private val db by lazy { Db(this) }
    private lateinit var tvState: TextView
    private lateinit var tvEmpty: TextView

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
        Mqtt.connect(this, Prefs.myUsername(this))

        val tvMyId = findViewById<TextView>(R.id.tvMyId)
        tvMyId.text = getString(R.string.my_id, Prefs.myUsername(this))
        tvMyId.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("id", Prefs.myUsername(this)))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
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
        refresh()
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
        val et = v.findViewById<TextInputEditText>(R.id.etPeer)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_contact)
            .setView(v)
            .setPositiveButton(R.string.open_chat) { _, _ ->
                val p = et.text.toString().trim().lowercase()
                if (Regex("^[a-z0-9_]{3,20}$").matches(p)) {
                    startActivity(Intent(this, ChatActivity::class.java).putExtra("peer", p))
                } else {
                    Toast.makeText(this, R.string.err_peer, Toast.LENGTH_SHORT).show()
                }
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
                Prefs.myUsername(this).isNotBlank() -> R.string.state_connecting
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
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        AppState.foreground = false
        runCatching { unregisterReceiver(receiver) }
    }
}
