package com.sukot.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class ChatActivity : AppCompatActivity() {

    private lateinit var peer: String
    private lateinit var adapter: MsgAdapter
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private val db by lazy { Db(this) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        peer = (intent.getStringExtra("peer") ?: "").trim().lowercase()
        if (peer.isBlank()) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = peer

        adapter = MsgAdapter()
        rv = findViewById(R.id.rv)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rv.layoutManager = lm
        rv.adapter = adapter
        tvEmpty = findViewById(R.id.tvEmpty)

        val et = findViewById<EditText>(R.id.etMsg)
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val t = et.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            et.setText("")
            Mqtt.send(this, peer, t)
            refresh()
        }
        refresh()
    }

    private fun refresh() {
        adapter.submit(db.chatMessages(peer))
        tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        if (adapter.itemCount > 0) {
            rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.openChat = peer
        val f = IntentFilter().apply {
            addAction(Mqtt.ACTION_MSG)
            addAction(Mqtt.ACTION_STATE)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        AppState.openChat = null
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
