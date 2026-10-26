package com.sukot.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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

    private var presenceSt = ""
    private var presenceTs = 0L
    private var typingUntil = 0L
    private var lastTypingSent = 0L
    private val main = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Mqtt.ACTION_MSG -> refresh()
                Mqtt.ACTION_PRESENCE -> if (intent.getStringExtra("peer") == peer) {
                    presenceSt = intent.getStringExtra("st") ?: ""
                    presenceTs = intent.getLongExtra("ts", 0L)
                    updateSubtitle()
                }
                Mqtt.ACTION_TYPING -> if (intent.getStringExtra("peer") == peer) {
                    typingUntil = System.currentTimeMillis() + 4000
                    updateSubtitle()
                    main.postDelayed({ updateSubtitle() }, 4200)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        peer = (intent.getStringExtra("peer") ?: "").trim()
        if (peer.isBlank()) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = db.contactName(peer) ?: Util.formatPhone(peer)

        adapter = MsgAdapter()
        rv = findViewById(R.id.rv)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rv.layoutManager = lm
        rv.adapter = adapter
        tvEmpty = findViewById(R.id.tvEmpty)

        val et = findViewById<EditText>(R.id.etMsg)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val now = System.currentTimeMillis()
                if (!s.isNullOrEmpty() && now - lastTypingSent > 2500) {
                    lastTypingSent = now
                    Mqtt.sendTyping(peer)
                }
            }
        })

        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val t = et.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            et.setText("")
            Mqtt.send(this, peer, t)
            refresh()
        }
        refresh()
    }

    private fun updateSubtitle() {
        supportActionBar?.setSubtitle(
            when {
                System.currentTimeMillis() < typingUntil -> getString(R.string.typing)
                presenceSt == "online" -> getString(R.string.online)
                presenceSt == "offline" && presenceTs > 0 ->
                    getString(R.string.offline_at, Util.dateTimeFa(presenceTs))
                else -> null
            }
        )
    }

    private fun refresh() {
        adapter.submit(db.chatMessages(peer))
        tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        if (adapter.itemCount > 0) {
            rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
        }
        db.markRead(peer)
    }

    override fun onResume() {
        super.onResume()
        AppState.openChat = peer
        val f = IntentFilter().apply {
            addAction(Mqtt.ACTION_MSG)
            addAction(Mqtt.ACTION_STATE)
            addAction(Mqtt.ACTION_PRESENCE)
            addAction(Mqtt.ACTION_TYPING)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        Mqtt.subscribePresence(peer)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        AppState.openChat = null
        db.markRead(peer)
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
