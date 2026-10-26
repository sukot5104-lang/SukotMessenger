package com.sukot.messenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

object Mqtt {

    const val BROKER = "tcp://broker.emqx.io:1883"
    const val ACTION_MSG = "com.sukot.messenger.ACTION_MSG"
    const val ACTION_STATE = "com.sukot.messenger.ACTION_STATE"
    const val ACTION_PRESENCE = "com.sukot.messenger.ACTION_PRESENCE"
    const val ACTION_TYPING = "com.sukot.messenger.ACTION_TYPING"
    const val ACTION_SMS_CODE = "com.sukot.messenger.ACTION_SMS_CODE"
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_MSGS = "messages"

    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var appCtx: Context? = null
    @Volatile private var me: String = ""
    @Volatile private var retrying = false

    val connected: Boolean
        get() = client?.isConnected == true

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                ctx.getString(R.string.notif_channel_service),
                NotificationManager.IMPORTANCE_MIN
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MSGS,
                ctx.getString(R.string.notif_channel_msgs),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    fun connect(ctx: Context, phone: String) {
        val c = ctx.applicationContext
        ensureChannel(c)
        appCtx = c
        if (me == phone && client?.isConnected == true) return
        if (me != phone) {
            me = phone
            try { client?.disconnectForcibly(800) } catch (_: Exception) {}
            client = null
        }
        if (client == null) build(c)
    }

    fun shutdown() {
        try { publishPresence(false) } catch (_: Exception) {}
        try { client?.disconnect(2000) } catch (_: Exception) {}
        client = null
        me = ""
    }

    private fun scheduleRetry(ctx: Context) {
        if (retrying) return
        retrying = true
        Handler(Looper.getMainLooper()).postDelayed({
            retrying = false
            if (me.isNotBlank() && client?.isConnected != true) build(ctx)
        }, 15000)
    }

    private fun presenceOnlinePayload(): ByteArray {
        return JSONObject()
            .put("f", me)
            .put("st", "online")
            .put("ts", System.currentTimeMillis())
            .toString().toByteArray(Charsets.UTF_8)
    }

    private fun presenceOfflinePayload(): ByteArray {
        return JSONObject()
            .put("f", me)
            .put("st", "offline")
            .put("ts", System.currentTimeMillis())
            .toString().toByteArray(Charsets.UTF_8)
    }

    private fun build(ctx: Context) {
        try {
            try { client?.disconnectForcibly(500) } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
            client = null

            val c = MqttAsyncClient(BROKER, Crypto.clientIdFor(me), MemoryPersistence())
            c.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    try {
                        c.subscribe(Crypto.topicFor(me), 1)
                        subscribePeersPresence()
                        c.publish(
                            Crypto.presenceFor(me),
                            MqttMessage(presenceOnlinePayload()).apply { qos = 1; isRetained = true }
                        )
                    } catch (_: Exception) {}
                    broadcastState(ctx)
                }

                override fun connectionLost(cause: Throwable?) {
                    broadcastState(ctx)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == null || message == null) return
                    if (topic.startsWith("sukot/v1/p/")) {
                        onPresence(ctx, message.payload)
                    } else {
                        onBytes(ctx, message.payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val opts = MqttConnectOptions().apply {
                isCleanSession = false
                isAutomaticReconnect = true
                connectionTimeout = 15
                keepAliveInterval = 30
                // اگر ناخواسته قطع شد، بروکر «آفلاین» را برای ما ثبت می‌کند (آخرین بازدید)
                setWill(Crypto.presenceFor(me), presenceOfflinePayload(), 1, true)
            }

            client = c
            broadcastState(ctx)
            c.connect(opts).setActionCallback(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    broadcastState(ctx)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    broadcastState(ctx)
                    scheduleRetry(ctx)
                }
            })
        } catch (_: Exception) {
            scheduleRetry(ctx)
        }
    }

    fun subscribePresence(peer: String) {
        val c = client ?: return
        if (!c.isConnected || peer.isBlank()) return
        try { c.subscribe(Crypto.presenceFor(peer), 1) } catch (_: Exception) {}
    }

    private fun subscribePeersPresence() {
        val c = client ?: return
        val ctx = appCtx ?: return
        val peers = Db(ctx).peers()
        if (peers.isEmpty()) return
        val topics = peers.map { Crypto.presenceFor(it) }.toTypedArray()
        val qos = IntArray(topics.size) { 1 }
        try { c.subscribe(topics, qos) } catch (_: Exception) {}
    }

    fun publishPresence(online: Boolean) {
        val c = client ?: return
        if (!c.isConnected || me.isBlank()) return
        try {
            val payload = if (online) presenceOnlinePayload() else presenceOfflinePayload()
            c.publish(
                Crypto.presenceFor(me),
                MqttMessage(payload).apply { qos = 1; isRetained = true }
            )
        } catch (_: Exception) {}
    }

    fun send(ctx: Context, to: String, text: String) {
        val from = me
        if (from.isBlank() || to.isBlank()) return
        val db = Db(ctx)
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val inner = JSONObject()
            .put("t", to)
            .put("m", text)
            .put("ts", ts)
            .put("id", id)
            .put("n", Prefs.myName(ctx))
        val outer = JSONObject().put("f", from).put("d", Crypto.encrypt(inner.toString(), listOf(from, to)))
        db.insert(Msg(id, to, true, text, ts, "pending"))
        try {
            val m = MqttMessage(outer.toString().toByteArray(Charsets.UTF_8))
            m.qos = 1
            client?.publish(Crypto.topicFor(to), m)
            db.updateStatus(id, "sent")
        } catch (_: Exception) {
        }
        broadcastMsg(ctx, to)
    }

    fun sendTyping(to: String) {
        val from = me
        if (from.isBlank() || to.isBlank()) return
        try {
            val inner = JSONObject().put("typing", 1).put("ts", System.currentTimeMillis())
            val outer = JSONObject().put("f", from).put("d", Crypto.encrypt(inner.toString(), listOf(from, to)))
            val m = MqttMessage(outer.toString().toByteArray(Charsets.UTF_8))
            m.qos = 0
            client?.publish(Crypto.topicFor(to), m)
        } catch (_: Exception) {
        }
    }

    private fun onPresence(ctx: Context, raw: ByteArray) {
        try {
            val j = JSONObject(String(raw, Charsets.UTF_8))
            val from = j.optString("f")
            if (from.isBlank() || from == me) return
            ctx.sendBroadcast(
                Intent(ACTION_PRESENCE).setPackage(ctx.packageName)
                    .putExtra("peer", from)
                    .putExtra("st", j.optString("st"))
                    .putExtra("ts", j.optLong("ts", 0L))
            )
        } catch (_: Exception) {
        }
    }

    private fun onBytes(ctx: Context, raw: ByteArray) {
        try {
            val outer = JSONObject(String(raw, Charsets.UTF_8))
            val from = outer.optString("f")
            if (from.isBlank() || from == me) return
            val inner = JSONObject(Crypto.decrypt(outer.getString("d"), listOf(me, from)))

            if (inner.has("typing")) {
                ctx.sendBroadcast(Intent(ACTION_TYPING).setPackage(ctx.packageName).putExtra("peer", from))
                return
            }

            if (inner.has("ack")) {
                Db(ctx).updateStatus(inner.getString("ack"), "delivered")
                broadcastMsg(ctx, from)
                return
            }

            val to = inner.optString("t")
            if (to != me) return
            val id = inner.getString("id")
            val ts = inner.optLong("ts", System.currentTimeMillis())
            val text = inner.getString("m")
            val senderName = inner.optString("n")

            val db = Db(ctx)
            if (senderName.isNotBlank() && db.contactName(from) == null) {
                db.setContact(from, senderName)
            }
            db.insert(Msg(id, from, false, text, ts, "in"))
            notifyIfNeeded(ctx, from, senderName, text)
            broadcastMsg(ctx, from)

            try {
                val ackInner = JSONObject().put("ack", id).put("ts", System.currentTimeMillis())
                val ackOuter = JSONObject().put("f", me).put("d", Crypto.encrypt(ackInner.toString(), listOf(me, from)))
                val am = MqttMessage(ackOuter.toString().toByteArray(Charsets.UTF_8))
                am.qos = 1
                client?.publish(Crypto.topicFor(from), am)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun notifyIfNeeded(ctx: Context, peer: String, senderName: String, text: String) {
        if (AppState.foreground && AppState.openChat == peer) return
        val titleName = senderName.ifBlank { Db(ctx).contactName(peer) ?: Util.formatPhone(peer) }
        val i = Intent(ctx, ChatActivity::class.java).putExtra("peer", peer)
        val pi = PendingIntent.getActivity(
            ctx, peer.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_MSGS)
            .setSmallIcon(R.drawable.ic_stat_msg)
            .setContentTitle(ctx.getString(R.string.notif_title, titleName))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(peer.hashCode(), n)
        } catch (_: Exception) {
        }
    }

    private fun broadcastMsg(ctx: Context, peer: String) {
        ctx.sendBroadcast(Intent(ACTION_MSG).setPackage(ctx.packageName).putExtra("peer", peer))
    }

    private fun broadcastState(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_STATE).setPackage(ctx.packageName))
    }
}
