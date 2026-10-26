package com.sukot.messenger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(
    val id: String,
    val peer: String,
    val outgoing: Boolean,
    val text: String,
    val ts: Long,
    val status: String
)

class Db(context: Context) : SQLiteOpenHelper(context, "sukot.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE msgs(" +
                "id TEXT PRIMARY KEY, " +
                "peer TEXT NOT NULL, " +
                "outgoing INTEGER NOT NULL, " +
                "text TEXT NOT NULL, " +
                "ts INTEGER NOT NULL, " +
                "status TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_peer_ts ON msgs(peer, ts)")
        db.execSQL(
            "CREATE TABLE contacts(" +
                "phone TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "ts INTEGER NOT NULL)"
        )
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS msgs")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    // ---------- پیام‌ها ----------
    fun insert(m: Msg) {
        val cv = ContentValues().apply {
            put("id", m.id)
            put("peer", m.peer)
            put("outgoing", if (m.outgoing) 1 else 0)
            put("text", m.text)
            put("ts", m.ts)
            put("status", m.status)
        }
        writableDatabase.insertWithOnConflict("msgs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateStatus(id: String, status: String) {
        writableDatabase.update(
            "msgs",
            ContentValues().apply { put("status", status) },
            "id=?", arrayOf(id)
        )
    }

    fun deletePeer(peer: String) {
        writableDatabase.delete("msgs", "peer=?", arrayOf(peer))
    }

    fun chatMessages(peer: String): List<Msg> {
        val out = ArrayList<Msg>()
        writableDatabase.rawQuery(
            "SELECT id,peer,outgoing,text,ts,status FROM msgs WHERE peer=? ORDER BY ts ASC",
            arrayOf(peer)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Msg(
                        c.getString(0), c.getString(1), c.getInt(2) == 1,
                        c.getString(3), c.getLong(4), c.getString(5)
                    )
                )
            }
        }
        return out
    }

    // ---------- مخاطبین ----------
    fun setContact(phone: String, name: String) {
        if (phone.isBlank()) return
        writableDatabase.insertWithOnConflict(
            "contacts", null,
            ContentValues().apply {
                put("phone", phone)
                put("name", name)
                put("ts", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun contactName(phone: String): String? {
        var name: String? = null
        readableDatabase.rawQuery(
            "SELECT name FROM contacts WHERE phone=?", arrayOf(phone)
        ).use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
        return name
    }

    fun allContacts(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT phone,name FROM contacts ORDER BY name", null).use { c ->
            while (c.moveToNext()) out.add(Pair(c.getString(0), c.getString(1)))
        }
        return out
    }

    // ---------- خوانده‌نشده ----------
    private fun readKey(peer: String) = "read_$peer"

    fun markRead(peer: String) {
        writableDatabase.insertWithOnConflict(
            "meta", null,
            ContentValues().apply {
                put("key", readKey(peer))
                put("value", System.currentTimeMillis().toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun lastRead(peer: String): Long {
        var v = 0L
        readableDatabase.rawQuery(
            "SELECT value FROM meta WHERE key=?", arrayOf(readKey(peer))
        ).use { c ->
            if (c.moveToFirst()) v = c.getString(0).toLongOrNull() ?: 0L
        }
        return v
    }

    fun unread(peer: String): Int {
        var n = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM msgs WHERE peer=? AND outgoing=0 AND ts>?",
            arrayOf(peer, lastRead(peer).toString())
        ).use { c ->
            if (c.moveToFirst()) n = c.getInt(0)
        }
        return n
    }

    // ---------- لیست گفتگوها ----------
    data class ChatRow(
        val peer: String,
        val name: String,
        val last: String,
        val ts: Long,
        val outgoing: Boolean,
        val unread: Int
    )

    fun chatList(): List<ChatRow> {
        val out = ArrayList<ChatRow>()
        writableDatabase.rawQuery(
            "SELECT m.peer, m.text, m.ts, m.outgoing FROM msgs m " +
                "JOIN (SELECT peer, MAX(ts) AS mts FROM msgs GROUP BY peer) x " +
                "ON x.peer = m.peer AND x.mts = m.ts " +
                "ORDER BY m.ts DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val peer = c.getString(0)
                val saved = contactName(peer)
                out.add(
                    ChatRow(
                        peer = peer,
                        name = saved ?: Util.formatPhone(peer),
                        last = c.getString(1),
                        ts = c.getLong(2),
                        outgoing = c.getInt(3) == 1,
                        unread = unread(peer)
                    )
                )
            }
        }
        return out
    }

    // ---------- همتاها (برای اشتراک حضور) ----------
    fun peers(): List<String> {
        val set = LinkedHashSet<String>()
        readableDatabase.rawQuery("SELECT DISTINCT peer FROM msgs", null).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        readableDatabase.rawQuery("SELECT phone FROM contacts", null).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set.toList()
    }
}
