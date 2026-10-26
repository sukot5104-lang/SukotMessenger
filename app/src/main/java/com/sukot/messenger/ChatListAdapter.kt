package com.sukot.messenger

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ChatListAdapter(
    val onClick: (String) -> Unit,
    val onLong: (String) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val items = ArrayList<Db.ChatRow>()

    fun submit(list: List<Db.ChatRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAvatar: TextView = v.findViewById(R.id.tvAvatar)
        val tvPeer: TextView = v.findViewById(R.id.tvPeer)
        val tvLast: TextView = v.findViewById(R.id.tvLast)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvBadge: TextView = v.findViewById(R.id.tvBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        val ctx = h.itemView.context
        h.tvPeer.text = r.name
        h.tvAvatar.text = r.name.take(1)
        h.tvAvatar.backgroundTintList = ColorStateList.valueOf(Util.avatarColor(r.peer))
        h.tvLast.text =
            (if (r.outgoing) ctx.getString(R.string.chat_you_prefix) else "") + r.last
        h.tvTime.text =
            if (Util.sameDay(r.ts, System.currentTimeMillis())) Util.timeFa(r.ts)
            else Util.shortDateFa(r.ts)
        if (r.unread > 0) {
            h.tvBadge.visibility = View.VISIBLE
            h.tvBadge.text = minOf(r.unread, 99).toString()
        } else {
            h.tvBadge.visibility = View.GONE
        }
        h.itemView.setOnClickListener { onClick(r.peer) }
        h.itemView.setOnLongClickListener { onLong(r.peer); true }
    }
}
