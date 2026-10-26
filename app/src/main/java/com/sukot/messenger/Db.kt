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

class Db(context: Context) : SQLiteOpenHelper(context, "sukot.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS msgs")
        onCreate(db)
    }

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

    data class ChatRow(val peer: String, val last: String, val ts: Long, val outgoing: Boolean)

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
                out.add(ChatRow(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3) == 1))
            }
        }
        return out
    }
}
