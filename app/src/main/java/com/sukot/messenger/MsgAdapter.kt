package com.sukot.messenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MsgAdapter : RecyclerView.Adapter<MsgAdapter.VH>() {

    private val items = ArrayList<Msg>()

    fun submit(list: List<Msg>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvText: TextView = v.findViewById(R.id.tvText)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
    }

    override fun getItemViewType(position: Int): Int = if (items[position].outgoing) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_msg_out else R.layout.item_msg_in
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.tvText.text = m.text
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.ts))
        h.tvMeta.text = if (m.outgoing) {
            when (m.status) {
                "pending" -> "$time ..."
                "sent" -> "$time ✓"
                else -> "$time ✓✓"
            }
        } else {
            time
        }
    }
}
