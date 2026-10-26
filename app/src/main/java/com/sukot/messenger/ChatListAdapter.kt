package com.sukot.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.tvPeer.text = r.peer
        h.tvAvatar.text = r.peer.take(1).uppercase()
        h.tvLast.text = r.last
        h.tvTime.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(r.ts))
        h.itemView.setOnClickListener { onClick(r.peer) }
        h.itemView.setOnLongClickListener { onLong(r.peer); true }
    }
}
